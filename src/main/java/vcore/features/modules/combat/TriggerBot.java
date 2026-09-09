package vcore.features.modules.combat;

import java.util.Random;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import vcore.core.Managers;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.PlayerUpdateEvent;
import vcore.features.modules.Module;
import vcore.setting.Setting;
import vcore.setting.impl.BooleanSettingGroup;

public final class TriggerBot extends Module {
   public final Setting<Float> attackRange = new Setting<>("Range", 3.0F, 1.0F, 7.0F);
   public final Setting<BooleanSettingGroup> smartCrit = new Setting<>("SmartCrit", new BooleanSettingGroup(true));
   public final Setting<Boolean> onlySpace = new Setting<>("OnlyCrit", false).addToGroup(this.smartCrit);
   public final Setting<Boolean> autoJump = new Setting<>("AutoJump", false).addToGroup(this.smartCrit);
   public final Setting<Boolean> ignoreWalls = new Setting<>("IgnoreWalls", false);
   public final Setting<Boolean> pauseEating = new Setting<>("PauseWhileEating", false);
   public final Setting<Integer> minDelay = new Setting<>("RandomDelayMin", 2, 0, 20);
   public final Setting<Integer> maxDelay = new Setting<>("RandomDelayMax", 13, 0, 20);
   private int delay;
   private final Random random = new Random();

   public TriggerBot() {
      super("TriggerBot", "Auto attacks what you look at.", Module.Category.COMBAT);
   }

   @EventHandler
   public void onAttack(PlayerUpdateEvent e) {
      if (!mc.player.isUsingItem() || !this.pauseEating.getValue()) {
         if (!mc.options.jumpKey.isPressed() && mc.player.isOnGround() && this.autoJump.getValue()) {
            mc.player.jump();
         }

         if (!this.autoCrit() && this.delay > 0) {
            this.delay--;
         } else {
            Entity ent = Managers.PLAYER.getRtxTarget(mc.player.getYaw(), mc.player.getPitch(), this.attackRange.getValue(), this.ignoreWalls.getValue());
            if (ent != null && !Managers.FRIEND.isFriend(ent.getName().getString())) {
               mc.interactionManager.attackEntity(mc.player, ent);
               mc.player.swingHand(Hand.MAIN_HAND);
               this.delay = this.random.nextInt(this.minDelay.getValue(), this.maxDelay.getValue() + 1);
            }
         }
      }
   }

   private boolean autoCrit() {
      boolean reasonForSkipCrit = !this.smartCrit.getValue().isEnabled()
         || mc.player.getAbilities().flying
         || mc.player.isFallFlying()
         || ModuleManager.elytraPlus.isEnabled()
         || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
         || mc.player.isHoldingOntoLadder()
         || mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos())).getBlock() == Blocks.COBWEB;
      if (mc.player.fallDistance > 1.0F && mc.player.fallDistance < 1.14) {
         return false;
      } else if (ModuleManager.aura.getAttackCooldown() < (mc.player.isOnGround() ? 1.0F : 0.9F)) {
         return false;
      } else {
         boolean mergeWithTargetStrafe = !ModuleManager.targetStrafe.isEnabled() || !ModuleManager.targetStrafe.jump.getValue();
         boolean mergeWithSpeed = !ModuleManager.speed.isEnabled() || mc.player.isOnGround();
         if (!mc.options.jumpKey.isPressed() && mergeWithTargetStrafe && mergeWithSpeed && !this.onlySpace.getValue() && !this.autoJump.getValue()) {
            return true;
         } else if (mc.player.isInLava()) {
            return true;
         } else if (!mc.options.jumpKey.isPressed() && ModuleManager.aura.isAboveWater()) {
            return true;
         } else {
            return reasonForSkipCrit ? true : !mc.player.isOnGround() && mc.player.fallDistance > 0.0F;
         }
      }
   }
}
