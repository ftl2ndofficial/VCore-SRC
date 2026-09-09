package vcore.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventMove;
import vcore.features.modules.Module;
import vcore.features.modules.combat.Aura;
import vcore.features.modules.combat.ElytraTarget;
import vcore.features.modules.misc.ClientSettings;
import vcore.setting.Setting;
import vcore.utility.Timer;
import vcore.utility.player.InventoryUtility;
import vcore.utility.player.SearchInvResult;

public class ElytraMotion extends Module {
   private final Setting<ElytraMotion.Mode> mode = new Setting<>("Mode", ElytraMotion.Mode.Freeze, v -> this.canUseMotionModeSetting());
   public final Setting<Float> attackDistance = new Setting<>("Freeze Distance", 3.0F, 0.1F, 5.0F, v -> !this.isSafeMotionSettingEnabled());
   private final Setting<Float> safeRange = new Setting<>("Safe Range", 3.0F, 0.5F, 8.0F, v -> this.isSafeRangeSettingVisible()).step(0.1F);
   public final Setting<Boolean> fallCheck = new Setting<>("Fall Check", true, v -> !this.isSafeMotionSettingEnabled());
   private final Setting<Boolean> autoFirework = new Setting<>("Auto Firework", false);
   private final Setting<Boolean> bypass = new Setting<>("Matrix Bypass", false, v -> !this.isSafeMotionSettingEnabled());
   private static final long SAFE_MOTION_ATTACK_INTERVAL_MS = 550L;
   private static final double SAFE_MOTION_REACH_EPSILON = 0.8;
   private static final double SAFE_MOTION_ATTACK_RANGE_BUFFER = 0.8;
   private static final double SAFE_MOTION_ATTACK_STEP = 0.05;
   private static final double SAFE_MOTION_ATTACK_RANGE_MARGIN = 1.0;
   private static final double SAFE_MOTION_YAW_STEP = 10.0;
   private static final double SAFE_MOTION_DANGEROUS_YAW = 55.0;
   private static final double SAFE_MOTION_VERTICAL_FACTOR = Math.tan(Math.toRadians(10.0));
   public boolean freeze;
   private final Timer timer = new Timer();
   private ElytraMotion.SafeMotionPhase safeMotionPhase = ElytraMotion.SafeMotionPhase.Attack;
   private boolean safeMotionWaitingAtSafe;
   private long safeMotionSafeUntil;
   private ElytraMotion.SafeMotionPath safeMotionLockedPath;
   private Vec3d safeMotionAttackDirection;
   private int safeMotionAttackTargetId = -1;
   private int safeMotionTargetId = -1;

   public ElytraMotion() {
      super("ElytraMotion", "Helps stabilize elytra targeting movement.", Module.Category.MOVEMENT);
   }

   @Override
   public void onUpdate() {
      if (mc.player != null) {
         if (this.shouldPauseForPeakAssist()) {
            this.freeze = false;
         } else if (!mc.player.isFallFlying()) {
            this.freeze = false;
         } else {
            LivingEntity target = Aura.target instanceof LivingEntity living ? living : null;
            ElytraTarget elytraTarget = ModuleManager.elytraTarget;
            if (this.shouldYieldToElytraTargetPredict(target)) {
               this.freeze = false;
               this.resetSafeMotion();
            } else {
               if (this.isSafeMotionActive(target)) {
                  this.freeze = false;
               } else {
                  this.resetSafeMotionIfUnavailable(target);
                  boolean shouldFreeze = this.check(target, elytraTarget);
                  boolean falling = this.isPlayerActuallyFalling();
                  this.freeze = shouldFreeze && (!this.fallCheck.getValue() || this.freeze || falling);
                  if (this.freeze) {
                     if (this.bypass.getValue() && this.timer.passedMs(500L)) {
                        this.useFirework();
                        this.timer.reset();
                     }

                     return;
                  }
               }

               if (this.autoFirework.getValue() && this.canRunAutoFirework(target) && this.timer.passedMs(500L)) {
                  this.useFirework();
                  this.timer.reset();
               }
            }
         }
      }
   }

   @EventHandler
   public void onMove(EventMove event) {
      if (this.shouldPauseForPeakAssist()) {
         this.freeze = false;
      } else {
         LivingEntity target = Aura.target instanceof LivingEntity living ? living : null;
         if (this.shouldYieldToElytraTargetPredict(target)) {
            this.freeze = false;
            this.resetSafeMotion();
         } else if (this.isSafeMotionActive(target)) {
            this.freeze = false;
         } else if (this.freeze) {
            event.cancel();
            event.setX(0.0);
            event.setY(0.0);
            event.setZ(0.0);
         }
      }
   }

   public boolean check(LivingEntity target, ElytraTarget elytraTarget) {
      if (target != null && mc.player != null && mc.player.isFallFlying()) {
         if (getBps(target) > 13.5) {
            return false;
         }

         boolean canTarget = elytraTarget != null && elytraTarget.shouldTarget(target);
         return !canTarget && target.distanceTo(mc.player) < this.attackDistance.getValue();
      } else {
         return false;
      }
   }

   private boolean canRunAutoFirework(LivingEntity target) {
      if (target == null) {
         return false;
      } else {
         return ModuleManager.elytraTarget != null && ModuleManager.elytraTarget.isEnabled() ? this.isTargetWithinElytraMotionBps(target) : true;
      }
   }

   public void markAuraPostAttack(boolean attacked) {
      if (attacked && Aura.target instanceof LivingEntity livingTarget && this.isSafeMotionActive(livingTarget)) {
         if (this.safeMotionPhase != ElytraMotion.SafeMotionPhase.Safe) {
            ElytraMotion.SafeMotionPath path = this.buildSafeMotionPath(livingTarget);
            if (path == null) {
               this.resetSafeMotion();
            } else {
               this.keepSafeMotionPath(livingTarget, path);
               this.safeMotionPhase = ElytraMotion.SafeMotionPhase.Safe;
               this.safeMotionWaitingAtSafe = false;
               this.safeMotionSafeUntil = 0L;
            }
         }
      }
   }

   public Vec3d getSafeMotionControlPoint(LivingEntity target) {
      if (!this.isSafeMotionActive(target)) {
         this.resetSafeMotion();
         return null;
      } else {
         ElytraMotion.SafeMotionPath path = this.buildSafeMotionPath(target);
         if (path == null) {
            this.resetSafeMotion();
            return null;
         } else if (this.safeMotionPhase == ElytraMotion.SafeMotionPhase.Safe) {
            this.keepSafeMotionPath(target, path);
            return this.updateSafeMotionSafePhase(path);
         } else {
            return path.attackPoint();
         }
      }
   }

   public boolean isSafeMotionRunning() {
      LivingEntity target = Aura.target instanceof LivingEntity living ? living : null;
      return this.isSafeMotionActive(target);
   }

   public boolean isElytraTargetPredictActive() {
      LivingEntity target = Aura.target instanceof LivingEntity living ? living : null;
      return this.shouldYieldToElytraTargetPredict(target);
   }

   private boolean canUseMotionModeSetting() {
      return ModuleManager.elytraBoost != null && ModuleManager.elytraBoost.isControlModeSelected();
   }

   private boolean isSafeRangeSettingVisible() {
      return this.canUseMotionModeSetting() && this.isMotionModeSelected();
   }

   private boolean isFreezeModeSelected() {
      return !this.canUseMotionModeSetting() || this.mode.is(ElytraMotion.Mode.Freeze);
   }

   private boolean isMotionModeSelected() {
      return this.canUseMotionModeSetting() && this.mode.is(ElytraMotion.Mode.Motion);
   }

   private boolean canUseSafeMotionSetting() {
      return ModuleManager.elytraBoost != null && ModuleManager.elytraBoost.isControlModeActive();
   }

   private boolean isSafeMotionSettingEnabled() {
      return this.isMotionModeSelected() && this.canUseSafeMotionSetting();
   }

   private boolean isSafeMotionActive(LivingEntity target) {
      return this.isSafeMotionSettingEnabled()
         && !this.shouldYieldToElytraTargetPredict(target)
         && mc.player != null
         && mc.world != null
         && mc.player.isFallFlying()
         && target != null
         && this.isTargetWithinElytraMotionBps(target)
         && !target.isRemoved();
   }

   private boolean isTargetWithinElytraMotionBps(LivingEntity target) {
      return target != null && getBps(target) <= 13.5;
   }

   private boolean shouldYieldToElytraTargetPredict(LivingEntity target) {
      return ModuleManager.elytraTarget != null && ModuleManager.elytraTarget.isEnabled() && target != null && ModuleManager.elytraTarget.shouldTarget(target);
   }

   private void resetSafeMotionIfUnavailable(LivingEntity target) {
      if (!this.isSafeMotionActive(target)) {
         this.resetSafeMotion();
      }
   }

   private ElytraMotion.SafeMotionPath buildSafeMotionPath(LivingEntity target) {
      if (mc.player != null && mc.world != null && target != null) {
         Box targetBox = target.getBoundingBox();
         Vec3d targetEye = target.getEyePos();
         Vec3d playerCenter = mc.player.getBoundingBox().getCenter();
         double elytraRange = Math.max(0.0, ModuleManager.aura.elytraAttackRange.getValue().floatValue());
         double safeAttackRange = elytraRange - 1.0 - 0.8;
         double safeHorizontalDistance = Math.max(this.safeRange.getValue().floatValue(), 0.1);
         if (elytraRange <= 0.0) {
            return null;
         }

         if (safeAttackRange <= 0.0) {
            return null;
         }

         ElytraMotion.SafeMotionPath lockedPath = this.buildLockedSafeMotionPath(target);
         if (lockedPath != null) {
            return lockedPath;
         }

         ElytraMotion.SafeMotionPath approachPath = this.buildAttackApproachPath(target, targetEye, targetBox, safeAttackRange, safeHorizontalDistance);
         if (approachPath != null) {
            return approachPath;
         }

         Vec3d targetLookDirection = this.getTargetLookDirection(target);
         ElytraMotion.SafeMotionPath bestPath = null;
         ElytraMotion.SafeMotionPath bestDangerousPath = null;
         double bestScore = Double.MAX_VALUE;
         double bestDangerousScore = Double.MAX_VALUE;

         for (double yaw = -180.0; yaw <= 180.0; yaw += 10.0) {
            Vec3d direction = this.getHorizontalDirection(Math.toRadians(yaw));
            Vec3d safePoint = this.getSafeMotionPoint(targetEye, direction, safeHorizontalDistance);
            Vec3d attackPoint = this.findAttackPoint(targetEye, direction, targetBox, safeAttackRange, safeHorizontalDistance);
            if (attackPoint != null
               && this.isSafeMotionLineClear(targetEye, safePoint)
               && this.isPlayerBoxClearAt(attackPoint)
               && this.isPlayerBoxClearAt(safePoint)) {
               ElytraMotion.SafeMotionPath path = new ElytraMotion.SafeMotionPath(attackPoint, safePoint, direction);
               double score = this.getSafeMotionCandidateScore(path, playerCenter);
               if (this.isDirectionDangerous(direction, targetLookDirection)) {
                  if (score < bestDangerousScore) {
                     bestDangerousScore = score;
                     bestDangerousPath = path;
                  }
               } else if (score < bestScore) {
                  bestScore = score;
                  bestPath = path;
               }
            }
         }

         ElytraMotion.SafeMotionPath selectedPath = bestPath != null ? bestPath : bestDangerousPath;
         if (selectedPath != null) {
            this.keepSafeMotionAttackDirection(target, selectedPath);
            return selectedPath;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private Vec3d updateSafeMotionSafePhase(ElytraMotion.SafeMotionPath path) {
      long now = System.currentTimeMillis();
      if (this.safeMotionWaitingAtSafe && !this.hasPlayerReachedSafePoint(path, 1.6)) {
         this.safeMotionWaitingAtSafe = false;
         this.safeMotionSafeUntil = 0L;
      }

      if (!this.safeMotionWaitingAtSafe && this.hasPlayerReachedSafePoint(path, 0.8)) {
         this.safeMotionWaitingAtSafe = true;
         this.safeMotionSafeUntil = now + this.getSafeMotionSafeDelay(path);
      }

      if (this.safeMotionWaitingAtSafe && now >= this.safeMotionSafeUntil) {
         this.safeMotionPhase = ElytraMotion.SafeMotionPhase.Attack;
         this.safeMotionWaitingAtSafe = false;
         this.safeMotionSafeUntil = 0L;
         this.clearSafeMotionPath();
         return path.attackPoint();
      } else {
         return path.safePoint();
      }
   }

   private long getSafeMotionSafeDelay(ElytraMotion.SafeMotionPath path) {
      long travelMs = this.getSafeMotionTravelMs(path.attackPoint(), path.safePoint());
      return Math.max(0L, 550L - travelMs * 2L);
   }

   private long getSafeMotionTravelMs(Vec3d from, Vec3d to) {
      Vec3d delta = to.subtract(from);
      double horizontalDistance = Math.hypot(delta.x, delta.z);
      double verticalDistance = Math.abs(delta.y);
      double yawSpeedBps = ModuleManager.elytraBoost != null ? ModuleManager.elytraBoost.getControlYawSpeedBps() : 39.5;
      double pitchSpeedBps = ModuleManager.elytraBoost != null ? ModuleManager.elytraBoost.getControlPitchSpeedBps() : 45.0;
      double horizontalSeconds = horizontalDistance / Math.max(1.0, yawSpeedBps);
      double verticalSeconds = verticalDistance / Math.max(1.0, pitchSpeedBps);
      return Math.round(Math.max(horizontalSeconds, verticalSeconds) * 1000.0);
   }

   private double getSafeMotionCandidateScore(ElytraMotion.SafeMotionPath path, Vec3d playerCenter) {
      Vec3d currentGoal = this.safeMotionPhase == ElytraMotion.SafeMotionPhase.Safe ? path.safePoint() : path.attackPoint();
      return currentGoal.squaredDistanceTo(playerCenter);
   }

   private Vec3d getHorizontalDirection(double yaw) {
      return new Vec3d(Math.cos(yaw), 0.0, Math.sin(yaw)).normalize();
   }

   private Vec3d getSafeMotionPoint(Vec3d targetEye, Vec3d horizontalDirection, double horizontalDistance) {
      Vec3d horizontalOffset = horizontalDirection.multiply(horizontalDistance);
      return targetEye.add(horizontalOffset.x, horizontalDistance * SAFE_MOTION_VERTICAL_FACTOR, horizontalOffset.z);
   }

   private ElytraMotion.SafeMotionPath buildAttackApproachPath(
      LivingEntity target, Vec3d targetEye, Box targetBox, double safeAttackRange, double safeHorizontalDistance
   ) {
      if (this.safeMotionPhase == ElytraMotion.SafeMotionPhase.Attack
         && this.safeMotionAttackDirection != null
         && this.safeMotionAttackTargetId == target.getId()) {
         ElytraMotion.SafeMotionPath path = this.buildPathForDirection(
            targetEye, targetBox, this.safeMotionAttackDirection, safeAttackRange, safeHorizontalDistance
         );
         if (path == null) {
            this.clearSafeMotionAttackDirection();
         }

         return path;
      } else {
         return null;
      }
   }

   private ElytraMotion.SafeMotionPath buildLockedSafeMotionPath(LivingEntity target) {
      if (this.safeMotionPhase != ElytraMotion.SafeMotionPhase.Safe || this.safeMotionLockedPath == null || this.safeMotionTargetId != target.getId()) {
         return null;
      } else if (!this.isPlayerBoxClearAt(this.safeMotionLockedPath.safePoint())) {
         this.clearSafeMotionPath();
         return null;
      } else {
         return this.safeMotionLockedPath;
      }
   }

   private ElytraMotion.SafeMotionPath buildPathForDirection(
      Vec3d targetEye, Box targetBox, Vec3d direction, double safeAttackRange, double safeHorizontalDistance
   ) {
      Vec3d safePoint = this.getSafeMotionPoint(targetEye, direction, safeHorizontalDistance);
      Vec3d attackPoint = this.findAttackPoint(targetEye, direction, targetBox, safeAttackRange, safeHorizontalDistance);
      if (attackPoint == null || !this.isSafeMotionLineClear(targetEye, safePoint)) {
         return null;
      } else {
         return this.isPlayerBoxClearAt(attackPoint) && this.isPlayerBoxClearAt(safePoint)
            ? new ElytraMotion.SafeMotionPath(attackPoint, safePoint, direction)
            : null;
      }
   }

   private void keepSafeMotionPath(LivingEntity target, ElytraMotion.SafeMotionPath path) {
      if (target != null && path != null) {
         this.safeMotionLockedPath = path;
         this.safeMotionTargetId = target.getId();
      } else {
         this.clearSafeMotionPath();
      }
   }

   private void keepSafeMotionAttackDirection(LivingEntity target, ElytraMotion.SafeMotionPath path) {
      if (target != null && path != null) {
         this.safeMotionAttackDirection = path.direction();
         this.safeMotionAttackTargetId = target.getId();
      } else {
         this.clearSafeMotionAttackDirection();
      }
   }

   private void clearSafeMotionPath() {
      this.safeMotionLockedPath = null;
      this.safeMotionTargetId = -1;
      this.clearSafeMotionAttackDirection();
   }

   private void clearSafeMotionAttackDirection() {
      this.safeMotionAttackDirection = null;
      this.safeMotionAttackTargetId = -1;
   }

   private Vec3d getTargetLookDirection(LivingEntity target) {
      Vec3d look = target.getRotationVec(1.0F);
      Vec3d horizontalLook = new Vec3d(look.x, 0.0, look.z);
      return horizontalLook.lengthSquared() <= 1.0E-8 ? null : horizontalLook.normalize();
   }

   private boolean isDirectionDangerous(Vec3d direction, Vec3d targetLookDirection) {
      if (targetLookDirection == null) {
         return false;
      }

      double dot = Math.max(-1.0, Math.min(1.0, direction.dotProduct(targetLookDirection)));
      return Math.toDegrees(Math.acos(dot)) <= 55.0;
   }

   private boolean isPlayerBoxClearAt(Vec3d point) {
      Vec3d currentCenter = mc.player.getBoundingBox().getCenter();
      Box movedBox = mc.player.getBoundingBox().offset(point.subtract(currentCenter));
      return !mc.world.getBlockCollisions(mc.player, movedBox).iterator().hasNext();
   }

   private Vec3d findAttackPoint(Vec3d targetEye, Vec3d horizontalDirection, Box targetBox, double safeAttackRange, double safeHorizontalDistance) {
      double maxAttackDistance = Math.max(0.05, safeHorizontalDistance - 0.05);

      for (double distance = maxAttackDistance; distance >= 0.05; distance -= 0.05) {
         Vec3d attackPoint = this.getSafeMotionPoint(targetEye, horizontalDirection, distance);
         if (this.isAttackPointInRange(attackPoint, targetBox, safeAttackRange)) {
            return attackPoint;
         }
      }

      return null;
   }

   private boolean isAttackPointInRange(Vec3d attackPoint, Box targetBox, double elytraRange) {
      double eyeOffset = mc.player.getEyeY() - mc.player.getBoundingBox().getCenter().y;
      Vec3d simulatedEye = attackPoint.add(0.0, eyeOffset, 0.0);
      return this.squaredDistanceToBox(simulatedEye, targetBox) <= elytraRange * elytraRange;
   }

   private boolean isSafeMotionLineClear(Vec3d targetEye, Vec3d safePoint) {
      RaycastContext context = new RaycastContext(targetEye, safePoint, ShapeType.COLLIDER, FluidHandling.NONE, mc.player);
      return mc.world.raycast(context).getType() == Type.MISS;
   }

   private double squaredDistanceToBox(Vec3d point, Box box) {
      double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
      double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
      double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
      return dx * dx + dy * dy + dz * dz;
   }

   private boolean isPlayerNear(Vec3d point, double epsilon) {
      return mc.player.getBoundingBox().getCenter().squaredDistanceTo(point) <= epsilon * epsilon;
   }

   private boolean hasPlayerReachedSafePoint(ElytraMotion.SafeMotionPath path, double epsilon) {
      if (path != null && mc.player != null) {
         if (this.isPlayerNear(path.safePoint(), epsilon)) {
            return true;
         }

         Vec3d travel = path.safePoint().subtract(path.attackPoint());
         if (travel.lengthSquared() <= 1.0E-8) {
            return false;
         }

         Vec3d travelDirection = travel.normalize();
         Vec3d playerCenter = mc.player.getBoundingBox().getCenter();
         Vec3d fromSafe = playerCenter.subtract(path.safePoint());
         double passedDistance = fromSafe.dotProduct(travelDirection);
         if (passedDistance < 0.0) {
            return false;
         }

         Vec3d projected = path.safePoint().add(travelDirection.multiply(passedDistance));
         double lateralLimit = epsilon * 1.5;
         return playerCenter.squaredDistanceTo(projected) <= lateralLimit * lateralLimit;
      } else {
         return false;
      }
   }

   private void resetSafeMotion() {
      this.safeMotionPhase = ElytraMotion.SafeMotionPhase.Attack;
      this.safeMotionWaitingAtSafe = false;
      this.safeMotionSafeUntil = 0L;
      this.clearSafeMotionPath();
   }

   private boolean isPlayerActuallyFalling() {
      double deltaY = mc.player.getY() - mc.player.prevY;
      double velocityY = mc.player.getVelocity().y;
      return !mc.player.isOnGround() && (deltaY < -1.0E-4 || velocityY < -1.0E-4);
   }

   @Override
   public void onDisable() {
      this.freeze = false;
      this.resetSafeMotion();
      super.onDisable();
   }

   private boolean shouldPauseForPeakAssist() {
      return ModuleManager.eMaceHelper != null && ModuleManager.eMaceHelper.isEnabled() && ModuleManager.eMaceHelper.isPeakAssistActive();
   }

   private void useFirework() {
      if (mc.player != null && mc.interactionManager != null) {
         if (!mc.player.getItemCooldownManager().isCoolingDown(Items.FIREWORK_ROCKET)) {
            if (ClientSettings.fireWorkMode.is(ClientSettings.FireWorkMode.OffHand)) {
               this.useFireworkOffhand();
            } else {
               this.useFireworkMainHand();
            }
         }
      }
   }

   private void useFireworkMainHand() {
      if (mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET) {
         this.useFireworkOffhandPacket();
      } else {
         SearchInvResult fireWorkResult = InventoryUtility.findItemInInventory(Items.FIREWORK_ROCKET);
         if (fireWorkResult.found()) {
            int currentSlot = mc.player.getInventory().selectedSlot;
            int hotbarSlot = currentSlot % 8 + 1;
            int itemSlot = fireWorkResult.slot();
            if (mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND) {
               this.useFireworkWithOffhandSwap(itemSlot);
            } else if (itemSlot >= 36) {
               this.useFireworkFromHotbar(itemSlot - 36, currentSlot);
            } else {
               mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, itemSlot, hotbarSlot, SlotActionType.SWAP, mc.player);
               this.useFireworkFromHotbar(hotbarSlot, currentSlot);
               mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, itemSlot, hotbarSlot, SlotActionType.SWAP, mc.player);
            }
         }
      }
   }

   private void useFireworkOffhand() {
      if (mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET) {
         this.useFireworkOffhandPacket();
      } else {
         SearchInvResult fireWorkResult = InventoryUtility.findItemInInventory(Items.FIREWORK_ROCKET);
         if (fireWorkResult.found()) {
            if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
               int slotIndex = convertFireworkSlot(fireWorkResult.slot());
               mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 40, SlotActionType.SWAP, mc.player);
               this.useFireworkOffhandPacket();
               mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 40, SlotActionType.SWAP, mc.player);
            }
         }
      }
   }

   private void useFireworkFromHotbar(int fireworkSlot, int returnSlot) {
      InventoryUtility.switchToSilent(fireworkSlot);
      float[] rotation = this.getUseRotation();
      this.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, rotation[0], rotation[1]));
      InventoryUtility.switchToSilent(returnSlot);
   }

   private void useFireworkWithOffhandSwap(int itemSlot) {
      int slotIndex = convertFireworkSlot(itemSlot);
      mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 40, SlotActionType.SWAP, mc.player);
      this.useFireworkOffhandPacket();
      mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 40, SlotActionType.SWAP, mc.player);
   }

   private void useFireworkOffhandPacket() {
      float[] rotation = this.getUseRotation();
      this.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, rotation[0], rotation[1]));
   }

   private static int convertFireworkSlot(int slotIndex) {
      return slotIndex < 9 ? 36 + slotIndex : slotIndex;
   }

   private float[] getUseRotation() {
      float yaw = mc.player.getYaw();
      float pitch = mc.player.getPitch();
      if (ModuleManager.aura.isEnabled() && Aura.target != null) {
         yaw = ModuleManager.aura.rotationYaw;
         pitch = ModuleManager.aura.rotationPitch;
      }

      return new float[]{yaw, pitch};
   }

   private static double getBps(LivingEntity entity) {
      if (entity == null) {
         return 0.0;
      }

      double dx = entity.getX() - entity.prevX;
      double dy = entity.getY() - entity.prevY;
      double dz = entity.getZ() - entity.prevZ;
      return Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
   }

   public enum Mode {
      Freeze,
      Motion;
   }

   private record SafeMotionPath(Vec3d attackPoint, Vec3d safePoint, Vec3d direction) {
   }

   private enum SafeMotionPhase {
      Attack,
      Safe;
   }
}
