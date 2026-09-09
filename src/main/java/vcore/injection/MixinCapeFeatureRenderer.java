package vcore.injection;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vcore.core.manager.client.ModuleManager;

@Mixin(CapeFeatureRenderer.class)
public class MixinCapeFeatureRenderer {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void renderCustomModelCape(
      MatrixStack matrixStack,
      VertexConsumerProvider vertexConsumerProvider,
      int i,
      AbstractClientPlayerEntity abstractClientPlayerEntity,
      float f,
      float g,
      float h,
      float j,
      float k,
      float l,
      CallbackInfo ci
   ) {
      if (ModuleManager.customModel != null
         && ModuleManager.customModel.isEnabled()
         && ModuleManager.customModel.renderCape(matrixStack, vertexConsumerProvider, i, abstractClientPlayerEntity, h)) {
         ci.cancel();
      }
   }

   @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 6)
   private float renderHook(
      float n,
      MatrixStack matrixStack,
      VertexConsumerProvider vertexConsumerProvider,
      int i,
      AbstractClientPlayerEntity abstractClientPlayerEntity,
      float f,
      float g,
      float h,
      float j,
      float k,
      float l
   ) {
      return MathHelper.lerp(h, abstractClientPlayerEntity.prevBodyYaw, abstractClientPlayerEntity.bodyYaw);
   }
}
