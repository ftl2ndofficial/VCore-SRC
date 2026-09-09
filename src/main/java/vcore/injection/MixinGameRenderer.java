package vcore.injection;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vcore.Vcore;
import vcore.core.Managers;
import vcore.core.manager.client.ModuleManager;
import vcore.features.modules.Module;
import vcore.features.modules.player.NoEntityTrace;
import vcore.utility.math.FrameRateCounter;
import vcore.utility.render.BlockAnimationUtility;
import vcore.utility.render.Render3DEngine;
import vcore.utility.render.shaders.satin.impl.ReloadableShaderEffectManager;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
   @Shadow
   private float zoom;
   @Shadow
   private float zoomX;
   @Shadow
   private float zoomY;
   @Shadow
   private float viewDistance;
   @Unique
   private boolean vcore$renderingHand;

   @Shadow
   public abstract void tick();

   @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V", ordinal = 1, shift = Shift.BEFORE), method = "render")
   void postHudRenderHook(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
      FrameRateCounter.INSTANCE.recordFrame();
   }

   @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z", opcode = 180, ordinal = 0), method = "renderWorld")
   void render3dHook(RenderTickCounter tickCounter, CallbackInfo ci) {
      if (!Module.fullNullCheck()) {
         Camera camera = Module.mc.gameRenderer.getCamera();
         MatrixStack matrixStack = new MatrixStack();
         RenderSystem.getModelViewStack().pushMatrix().mul(matrixStack.peek().getPositionMatrix());
         matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
         matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
         RenderSystem.applyModelViewMatrix();
         Render3DEngine.lastProjMat.set(RenderSystem.getProjectionMatrix());
         Render3DEngine.lastModMat.set(RenderSystem.getModelViewMatrix());
         Render3DEngine.lastWorldSpaceMatrix.set(matrixStack.peek().getPositionMatrix());
         Managers.MODULE.onRender3D(matrixStack);
         BlockAnimationUtility.onRender(matrixStack);
         Render3DEngine.onRender3D(matrixStack);
         RenderSystem.getModelViewStack().popMatrix();
         RenderSystem.applyModelViewMatrix();
      }
   }

   @Redirect(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"))
   private float renderWorldHook(float delta, float first, float second) {
      return ModuleManager.noRender.isEnabled() && ModuleManager.noRender.nausea.getValue() ? 0.0F : MathHelper.lerp(delta, first, second);
   }

   @Inject(method = "loadPrograms", at = @At("RETURN"))
   private void loadSatinPrograms(ResourceFactory factory, CallbackInfo ci) {
      ReloadableShaderEffectManager.INSTANCE.reload(factory);
   }

   @Inject(
      method = "updateCrosshairTarget",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/GameRenderer;findCrosshairTarget(Lnet/minecraft/entity/Entity;DDF)Lnet/minecraft/util/hit/HitResult;"
      ),
      cancellable = true
   )
   private void onUpdateTargetedEntity(float tickDelta, CallbackInfo info) {
      if (!Module.fullNullCheck()) {
         if (ModuleManager.freeCam.isEnabled()) {
            Module.mc.getProfiler().pop();
            info.cancel();
            Module.mc.crosshairTarget = Managers.PLAYER
               .getRtxTarget(
                  ModuleManager.freeCam.getFakeYaw(),
                  ModuleManager.freeCam.getFakePitch(),
                  ModuleManager.freeCam.getFakeX(),
                  ModuleManager.freeCam.getFakeY(),
                  ModuleManager.freeCam.getFakeZ()
               );
         }
      }
   }

   @Inject(method = "findCrosshairTarget", at = @At("HEAD"), cancellable = true)
   private void findCrosshairTargetHook(
      Entity camera, double blockInteractionRange, double entityInteractionRange, float tickDelta, CallbackInfoReturnable<HitResult> cir
   ) {
      boolean disableEntityTrace = this.shouldDisableEntityTrace();
      if (disableEntityTrace) {
         double d = Math.max(blockInteractionRange, entityInteractionRange);
         Vec3d vec3d = camera.getCameraPosVec(tickDelta);
         HitResult hitResult = camera.raycast(d, tickDelta, false);
         cir.setReturnValue(this.ensureTargetInRangeCustom(hitResult, vec3d, blockInteractionRange));
      }
   }

   @Unique
   private boolean shouldDisableEntityTrace() {
      if (ModuleManager.noEntityTrace.isEnabled() && Module.mc.player != null) {
         Item mainItem = Module.mc.player.getMainHandStack().getItem();
         return mainItem == Items.COBWEB && NoEntityTrace.cobweb.getValue()
            || mainItem instanceof PickaxeItem && NoEntityTrace.pickaxe.getValue()
            || mainItem instanceof AxeItem && NoEntityTrace.axe.getValue();
      } else {
         return false;
      }
   }

   @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
   private void getFovHook(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
      float fragZoom = ModuleManager.fragEffects.getMulCameraZoomValue();
      if (fragZoom != 1.0F) {
         cir.setReturnValue((Double)cir.getReturnValue() / fragZoom);
      }
   }

   @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
   public void getBasicProjectionMatrixHook(double fov, CallbackInfoReturnable<Matrix4f> cir) {
      if (ModuleManager.aspectRatio.isEnabled() && !this.vcore$renderingHand) {
         MatrixStack matrixStack = new MatrixStack();
         matrixStack.peek().getPositionMatrix().identity();
         if (this.zoom != 1.0F) {
            matrixStack.translate(this.zoomX, -this.zoomY, 0.0F);
            matrixStack.scale(this.zoom, this.zoom, 1.0F);
         }

         float aspect = ModuleManager.aspectRatio.isEnabled()
            ? ModuleManager.aspectRatio.getRatioValue()
            : (float)Module.mc.getWindow().getFramebufferWidth() / Module.mc.getWindow().getFramebufferHeight();
         matrixStack.peek()
            .getPositionMatrix()
            .mul(new Matrix4f().setPerspective((float)(fov * (float) (Math.PI / 180.0)), aspect, 0.05F, this.viewDistance * 4.0F));
         cir.setReturnValue(matrixStack.peek().getPositionMatrix());
      }
   }

   @Inject(method = "renderHand", at = @At("HEAD"))
   private void renderHandHead(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
      this.vcore$renderingHand = true;
   }

   @Inject(method = "renderHand", at = @At("RETURN"))
   private void renderHandReturn(Camera camera, float tickDelta, Matrix4f matrix4f, CallbackInfo ci) {
      this.vcore$renderingHand = false;
   }

   @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
   private void bobViewHook(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
      if (!Module.fullNullCheck()) {
         if (ModuleManager.noRender.isEnabled() && ModuleManager.noRender.noBob.getValue()) {
            ci.cancel();
         } else {
            Vcore.core.bobView(matrices, tickDelta);
            ci.cancel();
         }
      }
   }

   @Unique
   private HitResult ensureTargetInRangeCustom(HitResult hitResult, Vec3d cameraPos, double interactionRange) {
      Vec3d vec3d = hitResult.getPos();
      if (!vec3d.isInRange(cameraPos, interactionRange)) {
         Vec3d vec3d2 = hitResult.getPos();
         Direction direction = Direction.getFacing(vec3d2.x - cameraPos.x, vec3d2.y - cameraPos.y, vec3d2.z - cameraPos.z);
         return BlockHitResult.createMissed(vec3d2, direction, BlockPos.ofFloored(vec3d2));
      } else {
         return hitResult;
      }
   }

   @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
   private void showFloatingItemHook(ItemStack floatingItem, CallbackInfo info) {
      if (ModuleManager.totemAnimation.isEnabled()) {
         ModuleManager.totemAnimation.showFloatingItem(floatingItem);
         info.cancel();
      }
   }

   @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
   private void renderFloatingItemHook(DrawContext context, float tickDelta, CallbackInfo ci) {
      if (ModuleManager.totemAnimation.isEnabled()) {
         ModuleManager.totemAnimation.renderFloatingItem(tickDelta);
         ci.cancel();
      }
   }

   @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
   private void tiltViewWhenHurtHook(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
      if (ModuleManager.noRender.isEnabled() && ModuleManager.noRender.hurtCam.getValue()) {
         ci.cancel();
      }
   }
}
