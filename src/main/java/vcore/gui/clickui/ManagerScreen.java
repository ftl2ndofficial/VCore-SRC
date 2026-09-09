package vcore.gui.clickui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import vcore.core.manager.client.ModuleManager;
import vcore.features.modules.Module;
import vcore.gui.clickui.impl.ManagerPanel;

public class ManagerScreen extends Screen {
   private final Screen parent;
   private final ManagerPanel panel;

   public ManagerScreen(Screen parent) {
      super(Text.of("ManagerScreen"));
      this.parent = parent;
      this.panel = new ManagerPanel();
      this.panel.setOpen(true);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      if (ModuleManager.clickGui.blur.getValue()) {
         this.applyBlur(delta);
      }

      if (Module.fullNullCheck()) {
         this.renderBackground(context, mouseX, mouseY, delta);
      }

      this.panel.setSize(400.0F, 250.0F);
      this.panel.render(context, mouseX, mouseY, delta);
   }

   public boolean shouldPause() {
      return false;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return this.panel.mouseClicked((int)mouseX, (int)mouseY, button) ? true : super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.panel.mouseScrolled(mouseX, mouseY, verticalAmount);
      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.panel.keyPressed(keyCode)) {
         return true;
      } else if (keyCode == 256) {
         this.close();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean charTyped(char chr, int modifiers) {
      return this.panel.charTyped(chr) ? true : super.charTyped(chr, modifiers);
   }

   public void close() {
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }
}
