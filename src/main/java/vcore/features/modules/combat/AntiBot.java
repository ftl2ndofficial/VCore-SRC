package vcore.features.modules.combat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventEntityRemoved;
import vcore.events.impl.EventSync;
import vcore.features.modules.Module;
import vcore.features.modules.misc.FakePlayer;
import vcore.setting.Setting;

public final class AntiBot extends Module {
   public final Setting<AntiBot.Mode> mode = new Setting<>("Mode", AntiBot.Mode.Matrix);
   public final Setting<Boolean> enabledRemove = new Setting<>("Remove", true);
   public final Setting<Boolean> onlyAura = new Setting<>("OnlyAura", false);
   private static final Set<Integer> botEntityIds = ConcurrentHashMap.newKeySet();

   public AntiBot() {
      super("AntiBot", "Removes matrix bots.", Module.Category.COMBAT);
   }

   @Override
   public void onEnable() {
      super.onEnable();
      botEntityIds.clear();
   }

   @Override
   public void onDisable() {
      super.onDisable();
      botEntityIds.clear();
   }

   @EventHandler
   public void onSync(EventSync event) {
      if (mc.world != null && this.mode.is(AntiBot.Mode.Matrix)) {
         if (!this.onlyAura.getValue() || ModuleManager.aura.isEnabled()) {
            this.scanMatrixPlayers();
            if (this.enabledRemove.getValue() && !botEntityIds.isEmpty()) {
               this.removeDetectedBots();
            }
         }
      }
   }

   @EventHandler
   public void onEntityRemoved(EventEntityRemoved event) {
      if (event != null && event.getEntity() != null) {
         if (event.getEntity() instanceof PlayerEntity player) {
            botEntityIds.remove(player.getId());
         }
      }
   }

   private void scanMatrixPlayers() {
      for (PlayerEntity player : List.copyOf(mc.world.getPlayers())) {
         if (this.canCheckPlayer(player)) {
            if (isConfirmedMatrixBot(player)) {
               this.addBot(player);
            } else {
               this.clearBot(player);
            }
         }
      }
   }

   private boolean canCheckPlayer(PlayerEntity player) {
      return player != null
         && player != mc.player
         && player instanceof OtherClientPlayerEntity
         && player.isAlive()
         && (FakePlayer.fakePlayer == null || player != FakePlayer.fakePlayer);
   }

   private static boolean isConfirmedMatrixBot(PlayerEntity player) {
      return hasMatrixArmorFingerprint(player);
   }

   private static boolean hasMatrixArmorFingerprint(PlayerEntity player) {
      return hasRenderedArmorItem(player) && player.getArmor() == 0;
   }

   private static boolean hasRenderedArmorItem(PlayerEntity player) {
      for (ItemStack stack : player.getInventory().armor) {
         if (isArmorItem(stack)) {
            return true;
         }
      }

      return isArmorItem(player.getEquippedStack(EquipmentSlot.HEAD))
         || isArmorItem(player.getEquippedStack(EquipmentSlot.CHEST))
         || isArmorItem(player.getEquippedStack(EquipmentSlot.LEGS))
         || isArmorItem(player.getEquippedStack(EquipmentSlot.FEET));
   }

   private static boolean isArmorItem(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.getItem() instanceof ArmorItem;
   }

   private void removeDetectedBots() {
      for (PlayerEntity player : List.copyOf(mc.world.getPlayers())) {
         if (botEntityIds.contains(player.getId()) && (FakePlayer.fakePlayer == null || player != FakePlayer.fakePlayer)) {
            if (!isConfirmedMatrixBot(player)) {
               this.clearBot(player);
            } else {
               mc.world.removeEntity(player.getId(), RemovalReason.KILLED);
            }
         }
      }
   }

   private void addBot(PlayerEntity player) {
      if (botEntityIds.add(player.getId())) {
         this.sendMessage(player.getName().getString() + " is a bot (Matrix)!");
      }
   }

   private void clearBot(PlayerEntity player) {
      botEntityIds.remove(player.getId());
   }

   public static boolean isBot(Entity entity) {
      if (entity instanceof PlayerEntity player) {
         return FakePlayer.fakePlayer != null && entity == FakePlayer.fakePlayer ? false : botEntityIds.contains(player.getId());
      } else {
         return false;
      }
   }

   @Override
   public String getDisplayInfo() {
      return String.valueOf(botEntityIds.size());
   }

   public enum Mode {
      Matrix;
   }
}
