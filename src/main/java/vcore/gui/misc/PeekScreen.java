package vcore.gui.misc;

import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.text.Text;
import vcore.features.modules.render.Tooltips;

public class PeekScreen extends ShulkerBoxScreen {
   private static final ItemStack[] ITEMS = new ItemStack[27];

   public PeekScreen(ShulkerBoxScreenHandler handler, PlayerInventory inventory, Text title, Block block) {
      super(handler, inventory, title);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 2
         && this.focusedSlot != null
         && !this.focusedSlot.getStack().isEmpty()
         && this.client.player.playerScreenHandler.getCursorStack().isEmpty()) {
         ItemStack itemStack = this.focusedSlot.getStack();
         if (Tooltips.hasItems(itemStack) && Tooltips.middleClickOpen.getValue()) {
            Arrays.fill(ITEMS, ItemStack.EMPTY);
            ContainerComponent nbt = (ContainerComponent)itemStack.get(DataComponentTypes.CONTAINER);
            if (nbt != null) {
               List<ItemStack> list = nbt.stream().toList();

               for (int i = 0; i < list.size(); i++) {
                  ITEMS[i] = list.get(i);
               }
            }

            this.client
               .setScreen(
                  new PeekScreen(
                     new ShulkerBoxScreenHandler(0, this.client.player.getInventory(), new SimpleInventory(ITEMS)),
                     this.client.player.getInventory(),
                     this.focusedSlot.getStack().getName(),
                     ((BlockItem)this.focusedSlot.getStack().getItem()).getBlock()
                  )
               );
            return true;
         }
      }

      return false;
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return false;
   }
}
