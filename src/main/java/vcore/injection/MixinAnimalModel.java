package vcore.injection;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vcore.core.manager.client.ModuleManager;

@Mixin(AnimalModel.class)
public class MixinAnimalModel {
   @Inject(
      method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void renderCustomPlayerModel(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color, CallbackInfo ci) {
      if ((Object)this instanceof PlayerEntityModel<?> playerModel) {
         if (ModuleManager.customModel != null && ModuleManager.customModel.isEnabled()) {
            AbstractClientPlayerEntity player = ModuleManager.customModel.getRenderingPlayer();
            if (player != null) {
               if (ModuleManager.customModel.renderCustomPlayerModel(player, playerModel, matrices, vertexConsumer, light, overlay, color)) {
                  ci.cancel();
               }
            }
         }
      }
   }
}
