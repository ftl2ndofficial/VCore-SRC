package vcore.features.modules.player;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.entry.RegistryEntry;
import vcore.core.manager.client.ModuleManager;
import vcore.features.modules.Module;
import vcore.injection.accesors.IClientPlayerEntity;
import vcore.setting.Setting;

public class PerfectDelay extends Module {
   private final Setting<PerfectDelay.HorseJump> horse = new Setting<>("Horse", PerfectDelay.HorseJump.Legit);
   private final Setting<Boolean> bow = new Setting<>("Bow", true);
   private final Setting<Boolean> crossbow = new Setting<>("Crossbow", true);
   private final Setting<Boolean> trident = new Setting<>("Trident", true);

   public PerfectDelay() {
      super("PerfectDelay", "Perfect timing delays.", Module.Category.PLAYER);
   }

   private float getEnchantLevel(ItemStack stack) {
      return EnchantmentHelper.getLevel(
         (RegistryEntry)mc.world.getRegistryManager().get(Enchantments.PROTECTION.getRegistryRef()).getEntry(Enchantments.QUICK_CHARGE).get(), stack
      );
   }

   @Override
   public void onUpdate() {
      if (mc.player.getActiveItem().getItem() instanceof TridentItem
         && this.trident.getValue()
         && mc.player.getItemUseTime() > (ModuleManager.tridentBoost.isEnabled() ? ModuleManager.tridentBoost.cooldown.getValue() : 9)) {
         mc.interactionManager.stopUsingItem(mc.player);
      }

      if (mc.player.getActiveItem().getItem() instanceof CrossbowItem
         && this.crossbow.getValue()
         && mc.player.getItemUseTime() >= 25.0 - 0.25 * this.getEnchantLevel(mc.player.getActiveItem()) * 20.0) {
         mc.interactionManager.stopUsingItem(mc.player);
      }

      if (mc.player.getActiveItem().getItem() instanceof BowItem && this.bow.getValue() && mc.player.getItemUseTime() > 19) {
         mc.interactionManager.stopUsingItem(mc.player);
      }

      if (mc.player.getControllingVehicle() != null && mc.player.getControllingVehicle() instanceof HorseEntity && this.horse.is(PerfectDelay.HorseJump.Rage)) {
         ((IClientPlayerEntity)mc.player).setMountJumpStrength(1.0F);
      }

      if (mc.player.getControllingVehicle() != null
         && mc.player.getControllingVehicle() instanceof HorseEntity
         && this.horse.is(PerfectDelay.HorseJump.Legit)
         && mc.player.getMountJumpStrength() >= 1.0F) {
         mc.options.jumpKey.setPressed(false);
      }
   }

   private enum HorseJump {
      Legit,
      Rage,
      Off;
   }
}
