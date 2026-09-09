package vcore.features.modules.movement;

import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import vcore.events.impl.EventKeyboardInput;
import vcore.features.modules.Module;
import vcore.setting.Setting;
import vcore.setting.impl.SettingGroup;

public class NoSlow extends Module {
   public final Setting<NoSlow.Mode> mode = new Setting<>("Mode", NoSlow.Mode.Normal);
   private final Setting<Boolean> mainHand = new Setting<>("MainHand", true);
   private final Setting<SettingGroup> selection = new Setting<>("Selection", new SettingGroup(false, 0));
   private final Setting<Boolean> food = new Setting<>("Food", true).addToGroup(this.selection);
   private final Setting<Boolean> projectiles = new Setting<>("Projectiles", false).addToGroup(this.selection);
   private final Setting<Boolean> shield = new Setting<>("Shield", true).addToGroup(this.selection);
   public final Setting<Boolean> soulSand = new Setting<>("SoulSand", false).addToGroup(this.selection);
   public final Setting<Boolean> honey = new Setting<>("Honey", false).addToGroup(this.selection);
   public final Setting<Boolean> slime = new Setting<>("Slime", false).addToGroup(this.selection);
   public final Setting<Boolean> ice = new Setting<>("Ice", false).addToGroup(this.selection);
   public final Setting<Boolean> sweetBerryBush = new Setting<>("SweetBerryBush", false).addToGroup(this.selection);
   public final Setting<Boolean> sneak = new Setting<>("Sneak", false).addToGroup(this.selection);
   public final Setting<Boolean> crawl = new Setting<>("Crawl", false).addToGroup(this.selection);
   private int grimTickCounter;
   private boolean grimTickReady;

   public NoSlow() {
      super("NoSlow", "No slowdown when using items.", Module.Category.MOVEMENT);
   }

   @Override
   public List<Setting<?>> getSettings() {
      List<Setting<?>> settings = super.getSettings();
      settings.removeIf(setting -> setting != this.mode && !"Enabled".equals(setting.getName()) && !"Keybind".equals(setting.getName()));
      return settings;
   }

   @Override
   public void onUpdate() {
      if (mc.player != null) {
         this.updateGrimTickState();
         if (this.isUsingSlowItem()) {
            switch ((NoSlow.Mode)this.mode.getValue()) {
               case Grim:
                  if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                     this.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 8 + 1));
                     this.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot % 7 + 2));
                     this.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                  } else if (this.mainHand.getValue() && (mc.player.getItemUseTime() <= 3 || mc.player.age % 2 == 0)) {
                     this.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
                  }
               case GrimTick:
            }
         }
      }
   }

   @EventHandler
   public void onKeyboardInput(EventKeyboardInput e) {
      if (e != null && mc.player != null) {
         if (this.mode.getValue() == NoSlow.Mode.Matrix && mc.player.isUsingItem() && !mc.player.isFallFlying()) {
            mc.player.input.movementForward *= 5.0F;
            mc.player.input.movementSideways *= 5.0F;
            float multiplier = 1.0F;
            if (mc.player.isOnGround()) {
               if (mc.player.input.movementForward != 0.0F && mc.player.input.movementSideways != 0.0F) {
                  mc.player.input.movementForward *= 0.35F;
                  mc.player.input.movementSideways *= 0.35F;
               } else {
                  mc.player.input.movementForward *= 0.5F;
                  mc.player.input.movementSideways *= 0.5F;
               }
            } else if (mc.player.input.movementForward != 0.0F && mc.player.input.movementSideways != 0.0F) {
               multiplier = 0.47F;
            } else {
               multiplier = 0.67F;
            }

            mc.player.input.movementForward *= multiplier;
            mc.player.input.movementSideways *= multiplier;
         }
      }
   }

   public boolean canNoSlow() {
      return mc.player == null
         ? true
         : this.mode.getValue() != NoSlow.Mode.Matrix
            && this.consumeGrimTickWindow()
            && this.allowsActiveItem()
            && this.allowsActiveHand()
            && !this.isGrimNewBlocked();
   }

   private boolean isGrimNewBlocked() {
      return mc.player != null
         && this.mode.getValue() == NoSlow.Mode.Grim
         && mc.player.getActiveHand() == Hand.MAIN_HAND
         && (mc.player.getOffHandStack().getComponents().contains(DataComponentTypes.FOOD) || mc.player.getOffHandStack().getItem() == Items.SHIELD);
   }

   private void updateGrimTickState() {
      if (this.mode.getValue() == NoSlow.Mode.GrimTick && this.isUsingSlowItem()) {
         if (this.grimTickCounter < 20) {
            this.grimTickCounter++;
         }

         this.grimTickReady = this.grimTickCounter > 1;
      } else {
         this.grimTickCounter = 0;
         this.grimTickReady = false;
      }
   }

   private boolean consumeGrimTickWindow() {
      if (this.mode.getValue() != NoSlow.Mode.GrimTick) {
         return true;
      }

      if (!this.grimTickReady) {
         return false;
      }

      this.grimTickReady = false;
      this.grimTickCounter = 0;
      return true;
   }

   private boolean allowsActiveItem() {
      if (!this.food.getValue() && mc.player.getActiveItem().getComponents().contains(DataComponentTypes.FOOD)) {
         return false;
      } else {
         return !this.shield.getValue() && mc.player.getActiveItem().getItem() == Items.SHIELD
            ? false
            : this.projectiles.getValue()
               || mc.player.getActiveItem().getItem() != Items.CROSSBOW
                  && mc.player.getActiveItem().getItem() != Items.BOW
                  && mc.player.getActiveItem().getItem() != Items.TRIDENT;
      }
   }

   private boolean allowsActiveHand() {
      return this.mainHand.getValue() || mc.player.getActiveHand() != Hand.MAIN_HAND;
   }

   private boolean isUsingSlowItem() {
      return mc.player != null && mc.player.isUsingItem() && !mc.player.isRiding() && !mc.player.isFallFlying();
   }

   public enum Mode {
      Grim,
      GrimTick,
      Matrix,
      Normal;
   }
}
