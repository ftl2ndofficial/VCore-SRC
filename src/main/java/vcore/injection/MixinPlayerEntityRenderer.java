package vcore.injection;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vcore.core.manager.client.ModuleManager;

@Mixin(PlayerEntityRenderer.class)
public class MixinPlayerEntityRenderer {
   @Inject(
      method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
      at = @At("HEAD")
   )
   private void beginCustomModelRender(
      AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci
   ) {
      if (ModuleManager.customModel != null) {
         ModuleManager.customModel.beginPlayerRender(player);
      }
   }

   @Inject(
      method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
      at = @At("RETURN")
   )
   private void endCustomModelRender(
      AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci
   ) {
      if (ModuleManager.customModel != null) {
         ModuleManager.customModel.endPlayerRender(player);
      }
   }

   @Inject(
      method = "getTexture(Lnet/minecraft/client/network/AbstractClientPlayerEntity;)Lnet/minecraft/util/Identifier;",
      at = @At("RETURN"),
      cancellable = true
   )
   private void getCustomModelTexture(AbstractClientPlayerEntity player, CallbackInfoReturnable<Identifier> cir) {
      if (ModuleManager.customModel != null && ModuleManager.customModel.isEnabled()) {
         cir.setReturnValue(ModuleManager.customModel.getTexture(player, (Identifier)cir.getReturnValue()));
      }
   }
}
