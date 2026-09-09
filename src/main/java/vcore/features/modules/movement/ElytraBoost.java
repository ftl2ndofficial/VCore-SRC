package vcore.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventFireworkMotion;
import vcore.events.impl.EventMove;
import vcore.features.modules.Module;
import vcore.features.modules.combat.Aura;
import vcore.setting.Setting;

public final class ElytraBoost extends Module {
   private static final int[] YAW_VECTORS = new int[]{-45, 45, 135, -135};
   private static final int[] PITCH_VECTORS = new int[]{-45, 45};
   private final Setting<ElytraBoost.Mode> mode = new Setting<>("Mode", ElytraBoost.Mode.Normal);
   private final Setting<Boolean> smartSpeed = new Setting<>("SmartSpeed", false, v -> this.mode.is(ElytraBoost.Mode.Normal));
   private final Setting<Float> boost = new Setting<>("Boost", 1.6F, 1.5F, 2.0F, v -> this.mode.is(ElytraBoost.Mode.Normal) && !this.smartSpeed.getValue());
   private final Setting<Boolean> v1 = new Setting<>("V1", false, v -> this.mode.is(ElytraBoost.Mode.Normal) && this.smartSpeed.getValue());
   private final Setting<Boolean> v2 = new Setting<>("V2", false, v -> this.mode.is(ElytraBoost.Mode.Normal) && this.smartSpeed.getValue());
   private final Setting<Boolean> matrix = new Setting<>(
      "Matrix", false, v -> this.mode.is(ElytraBoost.Mode.Normal) && this.smartSpeed.getValue() && this.v1.getValue()
   );
   private final Setting<Boolean> untrusted = new Setting<>(
      "Untrusted", false, v -> this.mode.is(ElytraBoost.Mode.Normal) && this.smartSpeed.getValue() && this.v1.getValue() && this.matrix.getValue()
   );
   private final Setting<Float> fireworkSpeedMinGlobal = new Setting<>(
      "MinSpeedGlobal", 1.65F, 1.5F, 5.0F, v -> this.mode.is(ElytraBoost.Mode.Normal) && this.smartSpeed.getValue() && this.v2.getValue()
   );
   private final Setting<Float> fireworkSpeedMaxGlobal = new Setting<>(
      "MaxSpeedGlobal", 2.1F, 1.5F, 5.0F, v -> this.mode.is(ElytraBoost.Mode.Normal) && this.smartSpeed.getValue() && this.v2.getValue()
   );
   private final Setting<Float> fireworkSpeedMaxYaw = new Setting<>(
      "MaxSpeedYaw", 1.95F, 1.5F, 5.0F, v -> this.mode.is(ElytraBoost.Mode.Normal) && this.smartSpeed.getValue() && this.v2.getValue()
   );
   private final Setting<Float> fireworkSpeedMaxPitch = new Setting<>(
      "MaxSpeedPitch", 2.5F, 1.5F, 5.0F, v -> this.mode.is(ElytraBoost.Mode.Normal) && this.smartSpeed.getValue() && this.v2.getValue()
   );
   private final Setting<Float> yawSpeed = new Setting<>("Yaw Speed", 39.5F, 35.0F, 50.0F, v -> this.mode.is(ElytraBoost.Mode.Control));
   private final Setting<Float> pitchSpeed = new Setting<>("Pitch Speed", 45.0F, 35.0F, 60.0F, v -> this.mode.is(ElytraBoost.Mode.Control));

   public ElytraBoost() {
      super("ElytraBoost", "Boosts firework power while elytra flying.", Module.Category.MOVEMENT);
   }

   public boolean isControlModeSelected() {
      return this.mode.is(ElytraBoost.Mode.Control);
   }

   public boolean isControlModeActive() {
      return this.isEnabled() && this.isControlModeSelected();
   }

   public double getControlYawSpeedBps() {
      return this.yawSpeed.getValue().floatValue();
   }

   public double getControlPitchSpeedBps() {
      return this.pitchSpeed.getValue().floatValue();
   }

   @EventHandler
   public void onFireworkMotion(EventFireworkMotion event) {
      if (event != null && mc.player != null && this.mode.is(ElytraBoost.Mode.Normal)) {
         this.handleNormalFireworkMotion(event);
      }
   }

   @EventHandler(priority = -200)
   public void onMove(EventMove event) {
      if (event != null && this.mode.is(ElytraBoost.Mode.Control)) {
         this.handleControlMove(event);
      }
   }

   private void handleNormalFireworkMotion(EventFireworkMotion event) {
      boolean boosterWorking = this.shouldKeepBoosting();
      double speedXZ = this.boost.getValue().floatValue();
      double speedY = this.boost.getValue().floatValue();
      if (this.smartSpeed.getValue()) {
         if (this.v1.getValue() && this.v2.getValue()) {
            speedXZ = Math.max(this.getBoostV1(), this.getBoostV2());
         } else if (this.v1.getValue()) {
            speedXZ = this.getBoostV1();
         } else if (this.v2.getValue()) {
            speedXZ = this.getBoostV2();
         }

         speedY = 1.6F;
      }

      if (this.v2.getValue()) {
         speedXZ = Math.max(this.fireworkSpeedMinGlobal.getValue().floatValue(), speedXZ);
         speedXZ = Math.min(this.fireworkSpeedMaxGlobal.getValue().floatValue(), speedXZ);
      }

      event.setVector(new Vec3d(speedXZ, speedY, speedXZ));
      if (boosterWorking) {
         event.cancel();
      }
   }

   private boolean shouldKeepBoosting() {
      if (Aura.target instanceof LivingEntity livingTarget && ModuleManager.elytraTarget.shouldTarget(livingTarget)) {
         Vec3d targetVec = ModuleManager.elytraTarget.getTargetVec(livingTarget, true);
         if (targetVec == null) {
            return true;
         }

         double attackDistance = Math.max(0.0, ModuleManager.elytraTarget.getPrediction(livingTarget) - 0.5);
         double stopDistance = attackDistance * 0.7;
         double currentDistance = mc.player.getPos().distanceTo(targetVec);
         return currentDistance > stopDistance && !mc.player.getBoundingBox().contains(targetVec);
      } else {
         return true;
      }
   }

   private void handleControlMove(EventMove event) {
      if (mc.player != null && mc.world != null && mc.player.isFallFlying()) {
         if (Aura.target instanceof LivingEntity livingTarget) {
            if (!this.shouldRespectElytraMotionFreeze()) {
               Vec3d safeMotionPoint = this.getSafeMotionPoint(livingTarget);
               Vec3d delta = safeMotionPoint != null ? this.getDeltaToPoint(safeMotionPoint) : this.getControlDelta(livingTarget);
               Vec3d velocity = this.getControlVelocity(livingTarget, delta);
               event.setX(velocity.x);
               event.setY(velocity.y);
               event.setZ(velocity.z);
               mc.player.setVelocity(velocity);
               event.cancel();
            }
         }
      }
   }

   private boolean shouldRespectElytraMotionFreeze() {
      return ModuleManager.elytraMotion != null
         && ModuleManager.elytraMotion.isEnabled()
         && ModuleManager.elytraMotion.freeze
         && !ModuleManager.elytraMotion.isSafeMotionRunning()
         && !ModuleManager.elytraMotion.isElytraTargetPredictActive();
   }

   private Vec3d getSafeMotionPoint(LivingEntity livingTarget) {
      return ModuleManager.elytraMotion != null && ModuleManager.elytraMotion.isEnabled()
         ? ModuleManager.elytraMotion.getSafeMotionControlPoint(livingTarget)
         : null;
   }

   private Vec3d getDeltaToPoint(Vec3d point) {
      return point.subtract(mc.player.getBoundingBox().getCenter());
   }

   private Vec3d getControlDelta(LivingEntity livingTarget) {
      return ModuleManager.elytraTarget.shouldTarget(livingTarget) ? this.getDeltaToPredictedHitbox(livingTarget) : this.getDeltaToTargetHitbox(livingTarget);
   }

   private Vec3d getDeltaToPredictedHitbox(LivingEntity livingTarget) {
      Box playerBox = mc.player.getBoundingBox();
      Box predictedBox = this.getPredictedHitbox(livingTarget);
      return predictedBox.getCenter().subtract(playerBox.getCenter());
   }

   private Vec3d getDeltaToTargetHitbox(LivingEntity livingTarget) {
      Box playerBox = mc.player.getBoundingBox();
      Box targetBox = livingTarget.getBoundingBox();
      return targetBox.getCenter().subtract(playerBox.getCenter());
   }

   private Box getPredictedHitbox(LivingEntity livingTarget) {
      Vec3d predictionOffset = livingTarget.getVelocity().multiply(ModuleManager.elytraTarget.getPrediction(livingTarget));
      return livingTarget.getBoundingBox().offset(predictionOffset);
   }

   private Vec3d getControlVelocity(LivingEntity livingTarget, Vec3d delta) {
      Vec3d followVelocity = livingTarget.getVelocity();
      Vec3d desiredVelocity = followVelocity.add(delta);
      return this.limitControlVelocity(desiredVelocity);
   }

   private Vec3d limitControlVelocity(Vec3d velocity) {
      double horizontalLimit = this.yawSpeed.getValue().floatValue() / 20.0;
      double verticalLimit = this.pitchSpeed.getValue().floatValue() / 20.0;
      double horizontalLength = Math.hypot(velocity.x, velocity.z);
      double x = velocity.x;
      double z = velocity.z;
      if (horizontalLength > horizontalLimit && horizontalLength > 1.0E-6) {
         double ratio = horizontalLimit / horizontalLength;
         x *= ratio;
         z *= ratio;
      }

      double y = MathHelper.clamp(velocity.y, -verticalLimit, verticalLimit);
      return new Vec3d(x, y, z);
   }

   public double getBoostV2() {
      LivingEntity target = Aura.target instanceof LivingEntity living ? living : null;
      float lastYaw = target != null ? ModuleManager.aura.rotationYaw : mc.player.getYaw();
      float lastPitch = target != null ? ModuleManager.aura.rotationPitch : mc.player.getPitch();
      if (Math.abs(lastPitch) > 55.0F) {
         return 1.55;
      }

      double yawRad = Math.toRadians(lastYaw);
      double pitchRad = Math.toRadians(lastPitch);
      double sinYaw = Math.sin(yawRad);
      double cosYaw = Math.cos(yawRad);
      double cosPitch = Math.cos(pitchRad);
      if (cosPitch < 1.0E-6) {
         return 1.55;
      }

      double m = Math.max(Math.abs(sinYaw), Math.abs(cosYaw));
      double pitchContrib = 1.0 / cosPitch - 1.0;
      double yawContrib = 1.0 / m - 1.0;
      double a = 0.15;
      double b = 1.45;
      double desiredYawMaxBoost = this.fireworkSpeedMaxYaw.getValue().floatValue();
      double desiredPitchMaxBoost = this.fireworkSpeedMaxPitch.getValue().floatValue();
      double yawMaxContrib = (desiredYawMaxBoost - a) / b - 1.0;
      double pitchMaxContrib = (desiredPitchMaxBoost - a) / b - 1.0;
      pitchContrib = Math.min(pitchContrib, pitchMaxContrib);
      yawContrib = Math.min(yawContrib, yawMaxContrib);
      double inv = 1.0 + pitchContrib + yawContrib;
      double a2 = 0.15;
      double b2 = 1.45;
      return a2 + b2 * inv;
   }

   public double getBoostV1() {
      LivingEntity target = Aura.target instanceof LivingEntity living ? living : null;
      float lastYaw = target != null ? ModuleManager.aura.rotationYaw : mc.player.getYaw();
      float lastPitch = target != null ? ModuleManager.aura.rotationPitch : mc.player.getPitch();
      if (Math.abs(lastPitch) > 55.0F) {
         return 1.55;
      }

      float boostYaw = this.adjustBoostForYaw(lastYaw);
      double boostPitch = this.adjustBoostForPitch(lastYaw, lastPitch);
      double boostValue = boostYaw + (boostPitch - 1.6F);
      boostValue = Math.max(1.6, boostValue);
      return this.matrix.getValue() ? Math.min(boostValue, 2.1) : boostValue;
   }

   private float adjustBoostForYaw(float lastYaw) {
      int closestYawIndex = findClosestVector(lastYaw, YAW_VECTORS);
      if (closestYawIndex == -1) {
         return 1.6F;
      }

      float yawDistance = Math.abs(MathHelper.wrapDegrees(lastYaw) - YAW_VECTORS[closestYawIndex]);
      float maxBoost = 2.2F;
      float minBoostValue = 1.6F;
      float maxDistance = 12.0F;
      float variableSpeedSmart = 0.0F;
      if (yawDistance <= maxDistance) {
         float ratio = yawDistance / maxDistance;
         variableSpeedSmart = maxBoost - (maxBoost - minBoostValue) * ratio;
      }

      float variableSpeed = getVariableSpeed(yawDistance);
      float finalSpeed = Math.max(variableSpeedSmart, variableSpeed);
      float max = this.untrusted.getValue() ? 1.95F : 1.8F;
      return this.matrix.getValue() ? Math.min(finalSpeed, max) : finalSpeed;
   }

   private static float getVariableSpeed(float yawDistance) {
      float[] thresholds = new float[]{4.0F, 8.0F, 11.0F, 15.0F, 21.0F, 28.0F};
      float[] speeds = new float[]{2.2F, 2.1F, 2.0F, 1.9F, 1.8F, 1.7F, 1.6F};
      int level = 0;

      while (level < thresholds.length && yawDistance >= thresholds[level]) {
         level++;
      }

      return speeds[level];
   }

   private double adjustBoostForPitch(float lastYaw, float lastPitch) {
      int closestYawIndex = findClosestVector(lastPitch, PITCH_VECTORS);
      if (closestYawIndex == -1) {
         return 1.6F;
      }

      int closestYawIndex1 = findClosestVector(lastYaw, YAW_VECTORS);
      float yawDistance1 = Math.abs(MathHelper.wrapDegrees(lastYaw) - YAW_VECTORS[closestYawIndex1]);
      float yawDistance = Math.abs(MathHelper.wrapDegrees(lastPitch) - PITCH_VECTORS[closestYawIndex]);
      float maxBoost = getVariableSpeed(yawDistance);
      float minBoostValue = 1.6F;
      float maxDistance = 45.0F;
      float variableSpeedSmart = 0.0F;
      if (yawDistance <= maxDistance) {
         float ratio = yawDistance / maxDistance;
         variableSpeedSmart = maxBoost - (maxBoost - minBoostValue) * ratio;
      }

      return variableSpeedSmart;
   }

   private static int findClosestVector(float angle, int[] vectors) {
      int minDistIndex = -1;
      float minDist = Float.MAX_VALUE;

      for (int i = 0; i < vectors.length; i++) {
         float dist = Math.abs(MathHelper.wrapDegrees(angle) - vectors[i]);
         if (dist < minDist) {
            minDist = dist;
            minDistIndex = i;
         }
      }

      return minDistIndex;
   }

   public enum Mode {
      Normal,
      Control;
   }
}
