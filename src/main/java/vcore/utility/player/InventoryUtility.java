package vcore.utility.player;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import org.jetbrains.annotations.NotNull;
import vcore.core.Managers;
import vcore.core.manager.client.ModuleManager;
import vcore.features.modules.Module;
import vcore.injection.accesors.IInteractionManager;

public final class InventoryUtility {
   private static int cachedSlot = -1;

   public static int getItemCount(Item item) {
      if (Module.mc.player == null) {
         return 0;
      }

      int counter = 0;

      for (int i = 0; i <= 44; i++) {
         ItemStack itemStack = Module.mc.player.getInventory().getStack(i);
         if (itemStack.getItem() == item) {
            counter += itemStack.getCount();
         }
      }

      return counter;
   }

   public static SearchInvResult getAxe() {
      if (Module.mc.player == null) {
         return SearchInvResult.notFound();
      }

      int slot = -1;
      float f = 1.0F;

      for (int b1 = 9; b1 < 45; b1++) {
         ItemStack itemStack = Module.mc.player.getInventory().getStack(b1 >= 36 ? b1 - 36 : b1);
         if (itemStack != null && itemStack.getItem() instanceof AxeItem axe) {
            float f1 = ((Integer)axe.getComponents().get(DataComponentTypes.MAX_DAMAGE)).intValue();
            f1 += EnchantmentHelper.getLevel(
               (RegistryEntry)Module.mc.world.getRegistryManager().get(Enchantments.SHARPNESS.getRegistryRef()).getEntry(Enchantments.SHARPNESS).get(),
               itemStack
            );
            if (f1 > f) {
               f = f1;
               slot = b1;
            }
         }
      }

      if (slot >= 36) {
         slot -= 36;
      }

      return slot == -1 ? SearchInvResult.notFound() : new SearchInvResult(slot, true, Module.mc.player.getInventory().getStack(slot));
   }

   public static SearchInvResult getSwordHotBar() {
      if (Module.mc.player == null) {
         return SearchInvResult.notFound();
      }

      int slot = -1;
      float f = 1.0F;

      for (int b1 = 0; b1 < 9; b1++) {
         ItemStack itemStack = Module.mc.player.getInventory().getStack(b1);
         if (itemStack != null && itemStack.getItem() instanceof SwordItem sword) {
            float f1 = ((Integer)sword.getComponents().get(DataComponentTypes.MAX_DAMAGE)).intValue();
            f1 += EnchantmentHelper.getLevel(
               (RegistryEntry)Module.mc.world.getRegistryManager().get(Enchantments.SHARPNESS.getRegistryRef()).getEntry(Enchantments.SHARPNESS).get(),
               itemStack
            );
            if (f1 > f) {
               f = f1;
               slot = b1;
            }
         }
      }

      return slot == -1 ? SearchInvResult.notFound() : new SearchInvResult(slot, true, Module.mc.player.getInventory().getStack(slot));
   }

   public static int getElytra() {
      for (ItemStack stack : Module.mc.player.getInventory().armor) {
         if (stack.getItem() == Items.ELYTRA && stack.getDamage() < 430) {
            return -2;
         }
      }

      int slot = -1;

      for (int i = 0; i < 36; i++) {
         ItemStack s = Module.mc.player.getInventory().getStack(i);
         if (s.getItem() == Items.ELYTRA && s.getDamage() < 430) {
            slot = i;
            break;
         }
      }

      if (slot < 9 && slot != -1) {
         slot += 36;
      }

      return slot;
   }

   public static SearchInvResult findInHotBar(InventoryUtility.Searcher searcher) {
      if (Module.mc.player != null) {
         for (int i = 0; i < 9; i++) {
            ItemStack stack = Module.mc.player.getInventory().getStack(i);
            if (searcher.isValid(stack)) {
               return new SearchInvResult(i, true, stack);
            }
         }
      }

      return SearchInvResult.notFound();
   }

   public static SearchInvResult findItemInHotBar(List<Item> items) {
      return findInHotBar(stack -> items.contains(stack.getItem()));
   }

   public static SearchInvResult findItemInHotBar(Item... items) {
      return findItemInHotBar(Arrays.asList(items));
   }

   public static SearchInvResult findInInventory(InventoryUtility.Searcher searcher) {
      if (Module.mc.player != null) {
         for (int i = 36; i >= 0; i--) {
            ItemStack stack = Module.mc.player.getInventory().getStack(i);
            if (searcher.isValid(stack)) {
               if (i < 9) {
                  i += 36;
               }

               return new SearchInvResult(i, true, stack);
            }
         }
      }

      return SearchInvResult.notFound();
   }

   public static SearchInvResult findItemInInventory(List<Item> items) {
      return findInInventory(stack -> items.contains(stack.getItem()));
   }

   public static SearchInvResult findItemInInventory(Item... items) {
      return findItemInInventory(Arrays.asList(items));
   }

   public static SearchInvResult findBlockInHotBar(@NotNull List<Block> blocks) {
      return findItemInHotBar(blocks.stream().<Item>map(Block::asItem).toList());
   }

   public static SearchInvResult findBlockInHotBar(Block... blocks) {
      return findItemInHotBar(Arrays.stream(blocks).<Item>map(Block::asItem).toList());
   }

   public static SearchInvResult findBlockInInventory(@NotNull List<Block> blocks) {
      return findItemInInventory(blocks.stream().<Item>map(Block::asItem).toList());
   }

   public static SearchInvResult findBlockInInventory(Block... blocks) {
      return findItemInInventory(Arrays.stream(blocks).<Item>map(Block::asItem).toList());
   }

   public static void saveSlot() {
      cachedSlot = Module.mc.player.getInventory().selectedSlot;
   }

   public static void returnSlot() {
      if (cachedSlot != -1) {
         switchTo(cachedSlot);
      }

      cachedSlot = -1;
   }

   public static void saveAndSwitchTo(int slot) {
      saveSlot();
      if (Module.mc.player != null && Module.mc.getNetworkHandler() != null) {
         if (Module.mc.player.getInventory().selectedSlot != slot || Managers.PLAYER.serverSideSlot != slot) {
            Module.mc.player.getInventory().selectedSlot = slot;
            ((IInteractionManager)Module.mc.interactionManager).syncSlot();
         }
      }
   }

   public static void switchTo(int slot) {
      if (Module.mc.player != null && Module.mc.getNetworkHandler() != null) {
         if (Module.mc.player.getInventory().selectedSlot != slot || Managers.PLAYER.serverSideSlot != slot) {
            Module.mc.player.getInventory().selectedSlot = slot;
            ((IInteractionManager)Module.mc.interactionManager).syncSlot();
         }
      }
   }

   public static void switchToSilent(int slot) {
      if (Module.mc.player != null && Module.mc.getNetworkHandler() != null) {
         Module.mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
      }
   }

   public static SearchInvResult getAntiWeaknessItem() {
      if (Module.mc.player == null) {
         return SearchInvResult.notFound();
      }

      Item mainHand = Module.mc.player.getMainHandStack().getItem();
      return !(mainHand instanceof SwordItem) && !(mainHand instanceof PickaxeItem) && !(mainHand instanceof AxeItem) && !(mainHand instanceof ShovelItem)
         ? findInHotBar(
            itemStack -> itemStack.getItem() instanceof SwordItem
               || itemStack.getItem() instanceof PickaxeItem
               || itemStack.getItem() instanceof AxeItem
               || itemStack.getItem() instanceof ShovelItem
         )
         : new SearchInvResult(Module.mc.player.getInventory().selectedSlot, true, Module.mc.player.getMainHandStack());
   }

   public static float getHitDamage(@NotNull ItemStack weapon, PlayerEntity ent) {
      if (Module.mc.player == null) {
         return 0.0F;
      }

      float baseDamage = 1.0F;
      if (weapon.getItem() instanceof SwordItem swordItem) {
         baseDamage = 7.0F;
      }

      if (weapon.getItem() instanceof AxeItem axeItem) {
         baseDamage = 9.0F;
      }

      if (Module.mc.player.fallDistance > 0.0F || ModuleManager.criticals.isEnabled()) {
         baseDamage += baseDamage / 2.0F;
      }

      if (Module.mc.player.hasStatusEffect(StatusEffects.STRENGTH)) {
         int strength = Objects.requireNonNull(Module.mc.player.getStatusEffect(StatusEffects.STRENGTH)).getAmplifier() + 1;
         baseDamage += 3 * strength;
      }

      return DamageUtil.getDamageLeft(
         ent,
         baseDamage,
         Module.mc.world.getDamageSources().generic(),
         ent.getArmor(),
         (float)ent.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).getValue()
      );
   }

   public interface Searcher {
      boolean isValid(ItemStack var1);
   }
}
