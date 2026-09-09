package vcore.injection;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vcore.core.manager.client.ModuleManager;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
   protected MixinTitleScreen(Text title) {
      super(title);
   }

   @Inject(method = "init", at = @At("RETURN"))
   public void postInitHook(CallbackInfo ci) {
      if (ModuleManager.clickGui.getBind().getKey() == -1) {
         ModuleManager.clickGui.setBind(InputUtil.fromTranslationKey("key.keyboard.right.shift").getCode(), false, false);
      }
   }
}
