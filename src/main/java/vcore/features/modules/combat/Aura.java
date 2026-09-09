package vcore.features.modules.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import vcore.Vcore;
import vcore.core.Core;
import vcore.core.Managers;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventSync;
import vcore.events.impl.PacketEvent;
import vcore.events.impl.PlayerUpdateEvent;
import vcore.features.modules.Module;
import vcore.features.modules.combat.aura.rotation.AuraRotationManager;
import vcore.features.modules.movement.AutoSprint;
import vcore.features.modules.render.HudEditor;
import vcore.gui.notification.Notification;
import vcore.injection.accesors.ILivingEntity;
import vcore.setting.Setting;
import vcore.setting.impl.SettingGroup;
import vcore.utility.Timer;
import vcore.utility.interfaces.IOtherClientPlayerEntity;
import vcore.utility.math.MathUtility;
import vcore.utility.player.InteractionUtility;
import vcore.utility.player.InventoryUtility;
import vcore.utility.player.PlayerUtility;
import vcore.utility.render.Render3DEngine;

public class Aura extends Module {
   private final AuraRotationManager rotationManager;
   public final Setting<Float> attackRange = new Setting<>("Range", 3.1F, 1.0F, 6.0F);
   public final Setting<Float> aimRange = new Setting<>("AimRange", 2.0F, 0.0F, 6.0F);
   public final Setting<Float> elytraAttackRange = new Setting<>("ElytraRange", 0.5F, 0.0F, 6.0F);
   public final Setting<Integer> elytraAimRange = new Setting<>("ElytraAimRange", 32, 0, 64);
   public final Setting<Integer> fov = new Setting<>("FOV", 180, 1, 180);
   public final Setting<Boolean> igoneWall = new Setting<>("IgoneWall", true);
   public final Setting<Boolean> onlyWeapon = new Setting<>("OnlyWeapon", false);
   public final Setting<Aura.Timing> timing = new Setting<>("Timing", Aura.Timing.NEW);
   public final Setting<SettingGroup> attackSetting = new Setting<>("Attack Settings", new SettingGroup(false, 0));
   public final Setting<Integer> minCPS = new Setting<>("MinCPS", 17, 1, 20, v -> this.timing.is(Aura.Timing.OLD)).addToGroup(this.attackSetting);
   public final Setting<Integer> maxCPS = new Setting<>("MaxCPS", 20, 1, 20, v -> this.timing.is(Aura.Timing.OLD)).addToGroup(this.attackSetting);
   public final Setting<Boolean> smartCrit = new Setting<>("SmartCrit", true, v -> this.timing.is(Aura.Timing.NEW)).addToGroup(this.attackSetting);
   public final Setting<Boolean> onlySpace = new Setting<>("OnlyCrit", false, v -> this.timing.is(Aura.Timing.NEW) && this.smartCrit.getValue())
      .addToGroup(this.attackSetting);
   public final Setting<Boolean> shieldBreaker = new Setting<>("ShieldBreaker", true).addToGroup(this.attackSetting);
   public final Setting<Boolean> unpressShield = new Setting<>("UnpressShield", true, v -> this.timing.is(Aura.Timing.NEW)).addToGroup(this.attackSetting);
   public final Setting<Boolean> pauseWhileEating = new Setting<>("PauseWhileEating", false).addToGroup(this.attackSetting);
   public final Setting<Boolean> randomHitDelay = new Setting<>("RandomHitDelay", false, v -> this.timing.is(Aura.Timing.NEW)).addToGroup(this.attackSetting);
   public final Setting<Boolean> tpsSync = new Setting<>("TPSSync", false, v -> this.timing.is(Aura.Timing.NEW)).addToGroup(this.attackSetting);
   public final Setting<SettingGroup> rotationGroup = new Setting<>("Rotation Settings", new SettingGroup(false, 0));
   public final Setting<Aura.Mode> rotationMode = new Setting<>("Mode", Aura.Mode.Grim).addToGroup(this.rotationGroup);
   public final Setting<Integer> snapTicks = new Setting<>("SnapTicks", 3, 1, 10, v -> this.rotationMode.getValue() == Aura.Mode.Snap)
      .addToGroup(this.rotationGroup);
   public final Setting<Aura.AccelerateOnHit> accelerateOnHit = new Setting<>(
         "AccelerateOnHit", Aura.AccelerateOnHit.Both, v -> this.rotationMode.getValue() == Aura.Mode.Track
      )
      .addToGroup(this.rotationGroup);
   public final Setting<Integer> minYawStep = new Setting<>("MinYawStep", 20, 1, 180, v -> this.rotationMode.getValue() == Aura.Mode.Track)
      .addToGroup(this.rotationGroup);
   public final Setting<Integer> maxYawStep = new Setting<>("MaxYawStep", 60, 1, 180, v -> this.rotationMode.getValue() == Aura.Mode.Track)
      .addToGroup(this.rotationGroup);
   public final Setting<Float> aimedPitchStep = new Setting<>("AimedPitchStep", 1.5F, 0.0F, 90.0F, v -> this.rotationMode.getValue() == Aura.Mode.Track)
      .addToGroup(this.rotationGroup);
   public final Setting<Float> maxPitchStep = new Setting<>("MaxPitchStep", 16.0F, 1.0F, 90.0F, v -> this.rotationMode.getValue() == Aura.Mode.Track)
      .addToGroup(this.rotationGroup);
   public final Setting<Float> pitchAccelerate = new Setting<>("PitchAccelerate", 1.65F, 1.0F, 10.0F, v -> this.rotationMode.getValue() == Aura.Mode.Track)
      .addToGroup(this.rotationGroup);
   public final Setting<Boolean> clientLook = new Setting<>("ClientLook", false).addToGroup(this.rotationGroup);
   public final Setting<Boolean> lockTarget = new Setting<>("LockTarget", true).addToGroup(this.rotationGroup);
   public final Setting<Boolean> rayTrace = new Setting<>("RayTrace", true).addToGroup(this.rotationGroup);
   public final Setting<Aura.Resolver> resolver = new Setting<>("Resolver", Aura.Resolver.Off).addToGroup(this.rotationGroup);
   public final Setting<Integer> backTicks = new Setting<>("BackTicks", 4, 1, 20, v -> this.resolver.is(Aura.Resolver.BackTrack))
      .addToGroup(this.rotationGroup);
   public final Setting<Boolean> resolverVisualisation = new Setting<>("ResolverVisualisation", false, v -> !this.resolver.is(Aura.Resolver.Off))
      .addToGroup(this.rotationGroup);
   public final Setting<Aura.SprintMode> sprintMode = new Setting<>("Sprint", Aura.SprintMode.HVH, v -> this.timing.is(Aura.Timing.NEW));
   public final Setting<Aura.Sort> sort = new Setting<>("Sort", Aura.Sort.Distance);
   public final Setting<SettingGroup> targets = new Setting<>("Targets", new SettingGroup(false, 0));
   public final Setting<Boolean> Players = new Setting<>("Players", false).addToGroup(this.targets);
   public final Setting<Boolean> Mobs = new Setting<>("Mobs", false).addToGroup(this.targets);
   public final Setting<Boolean> Animals = new Setting<>("Animals", false).addToGroup(this.targets);
   public final Setting<Boolean> Villagers = new Setting<>("Villagers", false).addToGroup(this.targets);
   public final Setting<Boolean> Slimes = new Setting<>("Slimes", false).addToGroup(this.targets);
   public final Setting<Boolean> hostiles = new Setting<>("Hostiles", false).addToGroup(this.targets);
   public final Setting<Boolean> onlyAngry = new Setting<>("OnlyAngryHostiles", false, v -> this.hostiles.getValue()).addToGroup(this.targets);
   public final Setting<Boolean> Projectiles = new Setting<>("Projectiles", false).addToGroup(this.targets);
   public final Setting<Boolean> ignoreInvisible = new Setting<>("IgnoreInvisibleEntities", false).addToGroup(this.targets);
   public final Setting<Boolean> ignoreNamed = new Setting<>("IgnoreNamed", false).addToGroup(this.targets);
   public final Setting<Boolean> ignoreTeam = new Setting<>("IgnoreTeam", false).addToGroup(this.targets);
   public final Setting<Boolean> ignoreCreative = new Setting<>("IgnoreCreative", false).addToGroup(this.targets);
   public final Setting<Boolean> ignoreNaked = new Setting<>("IgnoreNaked", false).addToGroup(this.targets);
   public final Setting<Boolean> ignoreShield = new Setting<>("AttackShieldingEntities", false).addToGroup(this.targets);
   public static volatile Entity target;
   public float rotationYaw;
   public float rotationPitch;
   public float pitchAcceleration = 1.0F;
   private float previousRenderRotationYaw;
   private float previousRenderRotationPitch;
   private float renderRotationYaw;
   private float renderRotationPitch;
   public Vec3d rotationPoint = Vec3d.ZERO;
   public Vec3d rotationMotion = Vec3d.ZERO;
   private int hitTicks;
   public int trackticks;
   public boolean lookingAtHitbox;
   private boolean wasConsuming;
   private boolean wasHoldingWeapon;
   private boolean pendingElytraTargetAttack;
   private final Timer delayTimer = new Timer();
   private final Timer pauseTimer = new Timer();
   public volatile boolean externalPause = false;
   public Box resolvedBox;

   public Aura() {
      super("Aura", "Auto attacks entities.", Module.Category.COMBAT);
      this.rotationManager = new AuraRotationManager(this);
   }

   public float getRange() {
      return mc.player != null && mc.player.isFallFlying() ? this.elytraAttackRange.getValue() : this.attackRange.getValue();
   }

   public float getWallRange() {
      return !this.igoneWall.getValue() ? 0.0F : this.getRange() + this.getAimRange();
   }

   public float getAimRange() {
      return mc.player != null && mc.player.isFallFlying()
         ? ModuleManager.eMaceHelper.getElytraAimRangeOverride(this.elytraAimRange.getValue().floatValue())
         : this.aimRange.getValue();
   }

   public void auraLogic() {
      if (!this.hasWeapon()) {
         target = null;
         this.pendingElytraTargetAttack = false;
      } else {
         this.handleKill();
         this.updateTarget();
         if (target == null) {
            this.pendingElytraTargetAttack = false;
         } else {
            this.pendingElytraTargetAttack = false;
            boolean readyForCrit = this.autoCrit();
            boolean useElytraTargetAttack = target instanceof LivingEntity livingTarget && ModuleManager.elytraTarget.shouldTarget(livingTarget);
            this.calcRotations(readyForCrit);
            boolean readyForAttack = readyForCrit && (useElytraTargetAttack || this.lookingAtHitbox || this.skipRayTraceCheck());
            if (readyForAttack) {
               if (target instanceof LivingEntity livingTarget && ModuleManager.elytraTarget.shouldTarget(livingTarget)) {
                  if (!this.canAttackElytraTarget(livingTarget)) {
                     return;
                  }

                  this.pendingElytraTargetAttack = true;
                  return;
               }

               if (!this.isTargetInAttackRange(target)) {
                  return;
               }

               if (this.shieldBreaker()) {
                  return;
               }

               boolean[] playerState = this.preAttack();
               boolean attacked = false;
               if (!(target instanceof PlayerEntity pl && pl.isUsingItem() && pl.getOffHandStack().getItem() == Items.SHIELD && !this.ignoreShield.getValue())) {
                  this.attack();
                  attacked = true;
               }

               this.postAttack(playerState[0], playerState[1], attacked);
            }
         }
      }
   }

   private boolean hasWeapon() {
      Item handItem = mc.player.getMainHandStack().getItem();
      return !this.onlyWeapon.getValue()
         ? true
         : handItem instanceof SwordItem || handItem instanceof AxeItem || handItem instanceof TridentItem || handItem instanceof MaceItem;
   }

   private boolean skipRayTraceCheck() {
      return this.rotationManager.skipRayTraceCheck();
   }

   public void attack() {
      Criticals.cancelCrit = true;
      ModuleManager.criticals.doCrit();
      if (ModuleManager.maceSwap.isEnabled()) {
         MaceSwap.calledFromAura = true;
         ModuleManager.maceSwap.silentSwapToMace();
      }

      ModuleManager.autoSprint.markAuraHitCooldown();
      mc.interactionManager.attackEntity(mc.player, target);
      Criticals.cancelCrit = false;
      mc.player.swingHand(Hand.MAIN_HAND);
      this.hitTicks = this.getHitTicks();
   }

   private void runPendingElytraTargetAttack() {
      if (this.pendingElytraTargetAttack) {
         this.pendingElytraTargetAttack = false;
         if (this.hasWeapon()) {
            if (target instanceof LivingEntity livingTarget && ModuleManager.elytraTarget.shouldTarget(livingTarget)) {
               if (this.canAttackElytraTarget(livingTarget)) {
                  if (!this.shieldBreaker()) {
                     boolean[] playerState = this.preAttack();
                     boolean attacked = false;
                     if (!(
                        target instanceof PlayerEntity pl
                           && pl.isUsingItem()
                           && pl.getOffHandStack().getItem() == Items.SHIELD
                           && !this.ignoreShield.getValue()
                     )) {
                        this.attack();
                        attacked = true;
                     }

                     this.postAttack(playerState[0], playerState[1], attacked);
                  }
               }
            }
         }
      }
   }

   private boolean @NotNull [] preAttack() {
      boolean blocking = mc.player.isUsingItem() && mc.player.getActiveItem().getItem().getUseAction(mc.player.getActiveItem()) == UseAction.BLOCK;
      if (blocking && this.timing.is(Aura.Timing.NEW) && this.unpressShield.getValue()) {
         this.sendPacket(new PlayerActionC2SPacket(Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
      }

      boolean sprint = Core.serverSprint;
      if (this.timing.is(Aura.Timing.NEW) && this.sprintMode.getValue() == Aura.SprintMode.HVH && sprint) {
         this.disableSprint();
      }

      return new boolean[]{blocking, sprint};
   }

   public void postAttack(boolean block, boolean sprint, boolean attacked) {
      if (this.timing.is(Aura.Timing.NEW) && this.sprintMode.getValue() == Aura.SprintMode.HVH && sprint) {
         this.enableSprint();
      }

      if (block && this.timing.is(Aura.Timing.NEW) && this.unpressShield.getValue()) {
         this.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, this.rotationYaw, this.rotationPitch));
      }

      AutoSprint.markAuraPostAttack();
      EMaceHelper.markAuraPostAttack();
      MaceSwap.markAuraPostAttack(attacked);
      ModuleManager.elytraMotion.markAuraPostAttack(attacked);
   }

   private void disableSprint() {
      mc.player.setSprinting(false);
      mc.options.sprintKey.setPressed(false);
      this.sendPacket(new ClientCommandC2SPacket(mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.STOP_SPRINTING));
   }

   private void enableSprint() {
      mc.player.setSprinting(true);
      mc.options.sprintKey.setPressed(true);
      this.sendPacket(new ClientCommandC2SPacket(mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_SPRINTING));
   }

   public void resolvePlayers() {
      if (this.resolver.not(Aura.Resolver.Off)) {
         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player instanceof OtherClientPlayerEntity && !this.shouldBypassResolver(player)) {
               ((IOtherClientPlayerEntity)player).resolve(this.resolver.getValue());
            }
         }
      }
   }

   public void restorePlayers() {
      if (this.resolver.not(Aura.Resolver.Off)) {
         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player instanceof OtherClientPlayerEntity) {
               ((IOtherClientPlayerEntity)player).releaseResolver();
            }
         }
      }
   }

   private boolean shouldBypassResolver(PlayerEntity player) {
      return ModuleManager.elytraTarget.shouldTarget(player);
   }

   public void handleKill() {
      if (target instanceof LivingEntity && (((LivingEntity)target).getHealth() <= 0.0F || ((LivingEntity)target).isDead())) {
         Managers.NOTIFICATION.publicity("Aura", "Đã hạ mục tiêu thành công!", 3, Notification.Type.SUCCESS);
      }
   }

   private int getHitTicks() {
      return this.timing.is(Aura.Timing.OLD)
         ? 1 + (int)(20.0F / MathUtility.random(this.minCPS.getValue().intValue(), this.maxCPS.getValue().intValue()))
         : (this.shouldRandomizeDelay() ? (int)MathUtility.random(10.0F, 12.0F) : 10);
   }

   @EventHandler
   public void onUpdate(PlayerUpdateEvent e) {
      if (e != null) {
         if (this.externalPause) {
            if (this.hitTicks > 0) {
               this.hitTicks--;
            }
         } else if (this.pauseWhileEating.getValue() && this.isConsumingItem()) {
            this.wasConsuming = true;
            if (this.hitTicks > 0) {
               this.hitTicks--;
            }
         } else {
            if (this.wasConsuming) {
               this.wasConsuming = false;
               this.hitTicks = 0;
            }

            if (this.onlyWeapon.getValue()) {
               boolean hasWeaponNow = this.hasWeapon();
               if (hasWeaponNow && !this.wasHoldingWeapon) {
                  this.hitTicks = 0;
               }

               this.wasHoldingWeapon = hasWeaponNow;
            }

            this.resolvePlayers();
            this.auraLogic();
            this.restorePlayers();
            this.hitTicks--;
         }
      }
   }

   @EventHandler
   public void onSync(EventSync e) {
      if (e != null) {
         boolean pauseRotation = this.pauseWhileEating.getValue() && this.isConsumingItem() || this.externalPause;
         boolean canHandleTarget = target != null && !pauseRotation && this.hasWeapon();
         boolean shouldApplyRotation = canHandleTarget && this.rotationMode.getValue() != Aura.Mode.None;
         if (shouldApplyRotation) {
            mc.player.setYaw(this.rotationYaw);
            mc.player.setPitch(this.rotationPitch);
         } else {
            this.lookingAtHitbox = false;
            this.rotationYaw = mc.player.getYaw();
            this.rotationPitch = mc.player.getPitch();
            this.resetRenderRotation(this.rotationYaw, this.rotationPitch);
         }

         if (canHandleTarget && this.pendingElytraTargetAttack) {
            e.addPostAction(this::runPendingElytraTargetAttack);
         } else if (!canHandleTarget) {
            this.pendingElytraTargetAttack = false;
         }

         if (this.timing.is(Aura.Timing.OLD) && this.minCPS.getValue() > this.maxCPS.getValue()) {
            this.minCPS.setValue(this.maxCPS.getValue());
         }
      }
   }

   @EventHandler
   public void onPacketSend(PacketEvent.@NotNull Send e) {
      if (e.getPacket() instanceof PlayerInteractEntityC2SPacket pie && Criticals.getInteractType(pie) != Criticals.InteractType.ATTACK && target != null) {
         e.cancel();
      }
   }

   @EventHandler
   public void onPacketReceive(PacketEvent.@NotNull Receive e) {
      if (e.getPacket() instanceof EntityStatusS2CPacket status
         && status.getStatus() == 30
         && status.getEntity(mc.world) != null
         && target != null
         && status.getEntity(mc.world) == target) {
         Managers.NOTIFICATION.publicity("Aura", "Đã phá khiên của " + target.getName().getString(), 2, Notification.Type.SUCCESS);
      }
   }

   @Override
   public void onEnable() {
      target = null;
      this.lookingAtHitbox = false;
      this.rotationPoint = Vec3d.ZERO;
      this.rotationMotion = Vec3d.ZERO;
      this.rotationYaw = mc.player.getYaw();
      this.rotationPitch = mc.player.getPitch();
      this.resetRenderRotation(this.rotationYaw, this.rotationPitch);
      this.delayTimer.reset();
   }

   private boolean autoCrit() {
      if (this.hitTicks > 0) {
         return false;
      } else if (this.timing.is(Aura.Timing.OLD)) {
         return true;
      } else {
         boolean reasonForSkipCrit = !this.smartCrit.getValue()
            || mc.player.getAbilities().flying
            || mc.player.isFallFlying()
            || ModuleManager.elytraPlus.isEnabled()
            || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
            || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
            || Managers.PLAYER.isInWeb();
         if (this.getAttackCooldown() < 0.9F) {
            return false;
         } else if (ModuleManager.criticals.isEnabled() && ModuleManager.criticals.mode.is(Criticals.Mode.Grim)) {
            return true;
         } else {
            boolean mergeWithTargetStrafe = !ModuleManager.targetStrafe.isEnabled() || !ModuleManager.targetStrafe.jump.getValue();
            boolean mergeWithSpeed = !ModuleManager.speed.isEnabled() || mc.player.isOnGround();
            if (!mc.options.jumpKey.isPressed() && mergeWithTargetStrafe && mergeWithSpeed && !this.onlySpace.getValue()) {
               return true;
            } else if (mc.player.isInLava() || mc.player.isSubmergedInWater()) {
               return true;
            } else if (!mc.options.jumpKey.isPressed() && this.isAboveWater()) {
               return true;
            } else if (mc.player.fallDistance > 1.0F && mc.player.fallDistance < 1.14) {
               return false;
            } else {
               return reasonForSkipCrit
                  ? true
                  : !mc.player.isOnGround() && mc.player.fallDistance > (this.shouldRandomizeFallDistance() ? MathUtility.random(0.15F, 0.7F) : 0.0F);
            }
         }
      }
   }

   private boolean shieldBreaker() {
      int axeSlot = InventoryUtility.getAxe().slot();
      if (axeSlot == -1) {
         return false;
      }

      if (!this.shieldBreaker.getValue()) {
         return false;
      }

      if (!(target instanceof PlayerEntity)) {
         return false;
      }

      if (!((PlayerEntity)target).isUsingItem()) {
         return false;
      }

      if (((PlayerEntity)target).getOffHandStack().getItem() != Items.SHIELD && ((PlayerEntity)target).getMainHandStack().getItem() != Items.SHIELD) {
         return false;
      }

      if (axeSlot >= 9) {
         mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
         this.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
         mc.interactionManager.attackEntity(mc.player, target);
         mc.player.swingHand(Hand.MAIN_HAND);
         this.tryMaceSwapAttack();
         mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
         this.sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
      } else {
         this.sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
         mc.interactionManager.attackEntity(mc.player, target);
         mc.player.swingHand(Hand.MAIN_HAND);
         this.tryMaceSwapAttack();
         this.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
      }

      this.hitTicks = 10;
      return true;
   }

   private void tryMaceSwapAttack() {
      if (ModuleManager.maceSwap.isEnabled() && ModuleManager.maceSwap.isMaceStun()) {
         MaceSwap.calledFromAura = true;
         ModuleManager.maceSwap.silentSwapToMace();
         mc.interactionManager.attackEntity(mc.player, target);
         mc.player.swingHand(Hand.MAIN_HAND);
      }
   }

   public boolean isAboveWater() {
      return mc.player.isSubmergedInWater() || mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos().add(0.0, -0.4, 0.0))).getBlock() == Blocks.WATER;
   }

   public float getAttackCooldownProgressPerTick() {
      return (float)(
         1.0
            / mc.player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED)
            * (20.0 * Vcore.TICK_TIMER * (this.timing.is(Aura.Timing.NEW) && this.tpsSync.getValue() ? Managers.SERVER.getTPSFactor() : 1.0F))
      );
   }

   public float getAttackCooldown() {
      return MathHelper.clamp((((ILivingEntity)mc.player).getLastAttackedTicks() + 0.5F) / this.getAttackCooldownProgressPerTick(), 0.0F, 1.0F);
   }

   private void updateTarget() {
      boolean lockedTracking = target != null && this.lockTarget.getValue() && this.sort.getValue() != Aura.Sort.FOV;
      if (lockedTracking) {
         if (!this.skipEntity(target)) {
            if (this.Projectiles.getValue()) {
               Entity projectile = this.findIncomingProjectile();
               if (projectile != null) {
                  target = projectile;
               }
            }

            return;
         }

         target = null;
      }

      Entity candidat = this.findTarget();
      if (target == null) {
         target = candidat;
      } else {
         if (this.sort.getValue() == Aura.Sort.FOV || !this.lockTarget.getValue()) {
            target = candidat;
         }

         if (candidat instanceof ProjectileEntity) {
            target = candidat;
         }

         if (this.skipEntity(target)) {
            target = null;
         }
      }
   }

   private Entity findIncomingProjectile() {
      double rotateDist = Math.sqrt(this.getSquaredRotateDistance());
      double half = rotateDist + 8.0;
      Vec3d eye = mc.player.getEyePos();
      Box box = new Box(eye.x - half, eye.y - half, eye.z - half, eye.x + half, eye.y + half, eye.z + half);

      for (Entity ent : mc.world.getOtherEntities(mc.player, box)) {
         if ((ent instanceof ShulkerBulletEntity || ent instanceof FireballEntity) && ent.isAlive() && this.isInRange(ent)) {
            return ent;
         }
      }

      return null;
   }

   public boolean canAttackElytraTarget(LivingEntity livingTarget) {
      return this.rotationManager.canAttackElytraTarget(livingTarget);
   }

   private boolean isTargetInAttackRange(Entity targetEntity) {
      if (targetEntity != null && mc.player != null) {
         double maxDistanceSq = this.getRange() * this.getRange();
         Vec3d eyePos = mc.player.getEyePos();
         Box box = targetEntity.getBoundingBox();
         Vec3d closest = new Vec3d(
            MathHelper.clamp(eyePos.x, box.minX, box.maxX), MathHelper.clamp(eyePos.y, box.minY, box.maxY), MathHelper.clamp(eyePos.z, box.minZ, box.maxZ)
         );
         return eyePos.squaredDistanceTo(closest) <= maxDistanceSq;
      } else {
         return false;
      }
   }

   private void calcRotations(boolean ready) {
      this.rotationManager.rotate(ready);
   }

   @Override
   public void onRender3D(MatrixStack stack) {
      if (this.hasWeapon() && target != null) {
         this.rotationManager.onRender3D();
         boolean shouldRenderResolverVis = this.resolverVisualisation.getValue();
         if (shouldRenderResolverVis && this.resolvedBox != null) {
            Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(this.resolvedBox, HudEditor.getColor(0), 1.0F));
         }
      }
   }

   @Override
   public void onDisable() {
      target = null;
      this.lookingAtHitbox = false;
      this.pendingElytraTargetAttack = false;
      if (mc.player != null) {
         this.rotationYaw = mc.player.getYaw();
         this.rotationPitch = mc.player.getPitch();
         this.resetRenderRotation(this.rotationYaw, this.rotationPitch);
      }
   }

   public void captureRotationState(float previousYaw, float previousPitch) {
      if (!Float.isNaN(previousYaw) && !Float.isNaN(previousPitch) && !Float.isNaN(this.rotationYaw) && !Float.isNaN(this.rotationPitch)) {
         this.previousRenderRotationYaw = previousYaw;
         this.previousRenderRotationPitch = previousPitch;
         this.renderRotationYaw = this.rotationYaw;
         this.renderRotationPitch = this.rotationPitch;
      } else {
         this.resetRenderRotation(this.rotationYaw, this.rotationPitch);
      }
   }

   public float getRenderRotationYaw() {
      return this.interpolateRotation(this.previousRenderRotationYaw, this.renderRotationYaw);
   }

   public float getRenderRotationPitch() {
      return MathHelper.clamp(this.interpolateRotation(this.previousRenderRotationPitch, this.renderRotationPitch), -90.0F, 90.0F);
   }

   private void resetRenderRotation(float yaw, float pitch) {
      this.previousRenderRotationYaw = yaw;
      this.previousRenderRotationPitch = pitch;
      this.renderRotationYaw = yaw;
      this.renderRotationPitch = pitch;
   }

   private float interpolateRotation(float from, float to) {
      return from + MathHelper.wrapDegrees(to - from) * Render3DEngine.getTickDelta();
   }

   public boolean isLookingAtHitbox() {
      return this.lookingAtHitbox;
   }

   public boolean isWallsBypassYawOffset() {
      return this.rotationManager.isWallsBypassYawOffset();
   }

   public boolean isWallsBypassPeekHigh() {
      return this.rotationManager.isWallsBypassPeekHigh();
   }

   public float getSquaredRotateDistance() {
      return this.rotationManager.getSquaredRotateDistance();
   }

   public boolean isInRange(Entity target) {
      return this.rotationManager.isInRange(target);
   }

   public Entity findTarget() {
      if (mc.world != null && mc.player != null) {
         double rotateDist = Math.sqrt(this.getSquaredRotateDistance());
         double half = rotateDist + 16.0;
         Vec3d eye = mc.player.getEyePos();
         Box searchBox = new Box(eye.x - half, eye.y - half, eye.z - half, eye.x + half, eye.y + half, eye.z + half);
         boolean projectilesEnabled = this.Projectiles.getValue();
         List<LivingEntity> candidates = new ArrayList<>();

         for (Entity ent : mc.world.getOtherEntities(mc.player, searchBox)) {
            if (projectilesEnabled && (ent instanceof ShulkerBulletEntity || ent instanceof FireballEntity) && ent.isAlive() && this.isInRange(ent)) {
               return ent;
            }

            if (!this.skipEntity(ent) && ent instanceof LivingEntity living) {
               candidates.add(living);
            }
         }

         if (candidates.isEmpty()) {
            return null;
         }

         if (candidates.size() == 1) {
            return (Entity)candidates.get(0);
         }

         return switch ((Aura.Sort)this.sort.getValue()) {
            case Distance -> (LivingEntity)findMin(candidates, e -> mc.player.squaredDistanceTo(e.getPos()));
            case Durability -> (LivingEntity)findMin(candidates, Aura::computeDurabilityScore);
            case Health -> (LivingEntity)findMin(candidates, e -> e.getHealth() + e.getAbsorptionAmount());
            case FOV -> (LivingEntity)findMin(candidates, this::getFOVAngle);
         };
      } else {
         return null;
      }
   }

   private static <T> T findMin(List<T> list, ToDoubleFunction<T> keyFn) {
      T best = null;
      double bestKey = Double.POSITIVE_INFINITY;
      int i = 0;

      for (int n = list.size(); i < n; i++) {
         T item = list.get(i);
         double key = keyFn.applyAsDouble(item);
         if (key < bestKey) {
            bestKey = key;
            best = item;
         }
      }

      return best;
   }

   private static double computeDurabilityScore(LivingEntity e) {
      float v = 0.0F;

      for (ItemStack armor : e.getArmorItems()) {
         if (armor != null && !armor.isEmpty()) {
            int max = armor.getMaxDamage();
            if (max > 0) {
               v += (float)(max - armor.getDamage()) / max;
            }
         }
      }

      return v;
   }

   private boolean skipEntity(Entity entity) {
      if (this.isBullet(entity)) {
         return false;
      } else if (!(entity instanceof LivingEntity ent)) {
         return true;
      } else {
         if (ent.isDead() || !entity.isAlive()) {
            return true;
         }

         if (entity instanceof ArmorStandEntity) {
            return true;
         }

         if (entity instanceof CatEntity) {
            return true;
         }

         if (ModuleManager.eMaceHelper.shouldSkipAuraTarget(entity)) {
            return true;
         }

         if (this.skipNotSelected(entity)) {
            return true;
         }

         if (!InteractionUtility.isVecInFOV(ent.getPos(), this.fov.getValue())) {
            return true;
         }

         if (entity instanceof PlayerEntity player) {
            if (ModuleManager.antiBot.isEnabled() && AntiBot.isBot(entity)) {
               return true;
            }

            if (player == mc.player || Managers.FRIEND.isFriend(player)) {
               return true;
            }

            if (player.isCreative() && this.ignoreCreative.getValue()) {
               return true;
            }

            if (player.getArmor() == 0 && this.ignoreNaked.getValue()) {
               return true;
            }

            if (player.isInvisible() && this.ignoreInvisible.getValue()) {
               return true;
            }

            if (player.getTeamColorValue() == mc.player.getTeamColorValue() && this.ignoreTeam.getValue() && mc.player.getTeamColorValue() != 16777215) {
               return true;
            }
         }

         return !this.isInRange(entity) || entity.hasCustomName() && this.ignoreNamed.getValue();
      }
   }

   private boolean isBullet(Entity entity) {
      return (entity instanceof ShulkerBulletEntity || entity instanceof FireballEntity)
         && entity.isAlive()
         && PlayerUtility.squaredDistanceFromEyes(entity.getPos()) < this.getSquaredRotateDistance()
         && this.Projectiles.getValue();
   }

   private boolean skipNotSelected(Entity entity) {
      if (entity instanceof SlimeEntity && !this.Slimes.getValue()) {
         return true;
      }

      if (entity instanceof HostileEntity he) {
         if (!this.hostiles.getValue()) {
            return true;
         }

         if (this.onlyAngry.getValue()) {
            return !he.isAngryAt(mc.player);
         }
      }

      if (entity instanceof PlayerEntity && !this.Players.getValue()) {
         return true;
      } else if (entity instanceof VillagerEntity && !this.Villagers.getValue()) {
         return true;
      } else {
         return entity instanceof MobEntity && !this.Mobs.getValue() ? true : entity instanceof AnimalEntity && !this.Animals.getValue();
      }
   }

   private float getFOVAngle(@NotNull LivingEntity e) {
      double difX = e.getX() - mc.player.getX();
      double difZ = e.getZ() - mc.player.getZ();
      float yaw = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
      return (float)Math.abs(yaw - MathHelper.wrapDegrees(mc.player.getYaw()));
   }

   public void pause() {
      this.pauseTimer.reset();
   }

   private boolean isConsumingItem() {
      if (mc.player != null && mc.player.isUsingItem()) {
         UseAction useAction = mc.player.getActiveItem().getUseAction();
         return useAction == UseAction.EAT || useAction == UseAction.DRINK;
      } else {
         return false;
      }
   }

   private boolean shouldRandomizeDelay() {
      return this.timing.is(Aura.Timing.NEW)
         && this.randomHitDelay.getValue()
         && (mc.player.isOnGround() || mc.player.fallDistance < 0.12F || mc.player.isSwimming() || mc.player.isFallFlying());
   }

   private boolean shouldRandomizeFallDistance() {
      return this.timing.is(Aura.Timing.NEW) && this.randomHitDelay.getValue() && !this.shouldRandomizeDelay();
   }

   public enum AccelerateOnHit {
      Off,
      Yaw,
      Pitch,
      Both;
   }

   public enum Mode {
      Track,
      Grim,
      Snap,
      None;
   }

   public static class Position {
      private final double x;
      private final double y;
      private final double z;
      private int ticks;

      public Position(double x, double y, double z) {
         this.x = x;
         this.y = y;
         this.z = z;
      }

      public boolean shouldRemove() {
         return this.ticks++ > ModuleManager.aura.backTicks.getValue();
      }

      public double getX() {
         return this.x;
      }

      public double getY() {
         return this.y;
      }

      public double getZ() {
         return this.z;
      }
   }

   public enum Resolver {
      Off,
      Advantage,
      BackTrack;
   }

   public enum Sort {
      Distance,
      Durability,
      Health,
      FOV;
   }

   public enum SprintMode {
      HVH,
      Legit,
      LegitExtra,
      None;
   }

   public enum Timing {
      OLD,
      NEW;

      @Override
      public String toString() {
         return this == OLD ? "1.8" : "1.9";
      }
   }
}
