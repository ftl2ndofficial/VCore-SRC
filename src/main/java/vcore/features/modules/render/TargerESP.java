package vcore.features.modules.render;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vcore.features.modules.Module;
import vcore.features.modules.combat.Aura;
import vcore.features.modules.combat.HitBox;
import vcore.setting.Setting;
import vcore.utility.render.GhostRenderer3D;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.Render3DEngine;
import vcore.utility.render.TextureStorage;
import vcore.utility.render.animation.AnimationUtility;
import vcore.utility.render.animation.advanced.Animation;
import vcore.utility.render.animation.advanced.Easing;
import vcore.utility.render.animation.advanced.InfinityAnimation;

public class TargerESP extends Module {
   private static final int PARTICLE_LIMIT = 3;
   private static final float PARTICLE_SIZE = 0.28F;
   private static final float GHOST_V1_SPEED = 0.62F;
   private static final float IMAGE_MIN_DISTANCE_SCALE = 0.25F;
   private static final float IMAGE_MAX_DISTANCE_SCALE = 5.5F;
   private static final float IMAGE_RADIUS = 0.5F;
   private static final int IMAGE_COLOR_OFFSET = 255;
   private final Setting<TargerESP.Mode> mode = new Setting<>("Mode", TargerESP.Mode.Image);
   private final Setting<Boolean> distanceEffect = new Setting<>("Distance Effect", false, v -> this.mode.is(TargerESP.Mode.Image));
   private final Setting<TargerESP.GhostType> ghostType = new Setting<>("Ghost Type", TargerESP.GhostType.Type1, v -> this.mode.is(TargerESP.Mode.Ghost));
   private final Animation imageAnim = new Animation().setEasing(Easing.TARGETESP_EASE_OUT_BACK).setSpeed(350).setSize(1.0F).setForward(false);
   private LivingEntity imageTarget;
   private float imageHurtProgress;
   private final Animation circleAlphaAnim = new Animation().setEasing(Easing.EASE_OUT_QUAD).setSpeed(350).setSize(1.0F).setForward(false);
   private LivingEntity circleTarget;
   private float circleHurtProgress;
   private final Animation ghostV1Anim = new Animation().setEasing(Easing.EASE_OUT_QUAD).setSpeed(400).setSize(1.0F).setForward(false);
   private LivingEntity ghostV1Target;
   private final Animation spiritsAlphaAnim = new Animation().setEasing(Easing.EASE_OUT_QUAD).setSpeed(350).setSize(1.0F).setForward(false);
   private LivingEntity spiritsTarget;
   private float spiritsHurtProgress;
   private long currentTimeSpirits;
   private float spiritsAnimation;
   private final TargerESP.TargetEspRenderer renderer = new TargerESP.TargetEspRenderer(() -> 3, () -> 0.28F, () -> TextureStorage.firefly);

   public TargerESP() {
      super("TargetESP", "Target visualizer.", Module.Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.resetAll();
   }

   @Override
   public void onRender3D(MatrixStack stack) {
      if (fullNullCheck()) {
         this.resetAll();
      } else {
         LivingEntity target = this.resolveTarget();
         if (this.mode.is(TargerESP.Mode.Image)) {
            this.renderer.reset();
            this.resetCircle(target);
            this.resetGhostV1();
            this.resetSpirits();
            this.renderImage(stack, target);
         } else {
            this.resetImage(target);
            if (this.mode.is(TargerESP.Mode.Circle)) {
               this.renderer.reset();
               this.resetGhostV1();
               this.resetSpirits();
               this.renderCircle(stack, target);
            } else {
               this.resetCircle(target);
               switch ((TargerESP.GhostType)this.ghostType.getValue()) {
                  case Type1:
                     this.resetGhostV1();
                     this.resetSpirits();
                     if (target != null) {
                        this.renderer.render(target);
                     }
                     break;
                  case Type2:
                     this.renderer.reset();
                     this.resetGhostV1();
                     this.renderSpirits(stack, target);
                     break;
                  case Type3:
                     this.renderer.reset();
                     this.resetSpirits();
                     this.renderGhostV1(target);
               }
            }
         }
      }
   }

   private LivingEntity resolveTarget() {
      return Aura.target instanceof LivingEntity living && !living.isRemoved() ? living : null;
   }

   private void renderImage(MatrixStack stack, LivingEntity target) {
      if (target != null && target != this.imageTarget) {
         this.imageTarget = target;
         this.imageHurtProgress = 0.0F;
      }

      this.imageAnim.setForward(target != null);
      if (this.imageTarget != null) {
         if (!this.imageTarget.isRemoved() && !this.imageAnim.finished(false)) {
            Camera camera = mc.gameRenderer.getCamera();
            float tickDelta = Render3DEngine.getTickDelta();
            Vec3d pos = new Vec3d(
               Render2DEngine.interpolate(this.imageTarget.prevX, this.imageTarget.getX(), tickDelta),
               Render2DEngine.interpolate(this.imageTarget.prevY, this.imageTarget.getY(), tickDelta),
               Render2DEngine.interpolate(this.imageTarget.prevZ, this.imageTarget.getZ(), tickDelta)
            );
            Vec3d imagePos = pos.add(0.0, this.imageTarget.getHeight() / 1.75F, 0.0);
            float anim = MathHelper.clamp(this.imageAnim.get(), 0.0F, 1.0F);
            this.imageHurtProgress = this.smoothImageHurtProgress(this.getHurtFactor(this.imageTarget));
            Color drawColor = blend(HudEditor.getColor(255), new Color(200, 70, 70), this.imageHurtProgress);
            int alpha = MathHelper.clamp(Math.round(255.0F * anim), 0, 255);
            float rotate = (float)((Math.sin(System.currentTimeMillis() / 900.0) + 1.0) * 360.0);
            float size = (1.7F - 0.9F * anim + (0.35F - 0.35F * this.imageHurtProgress)) * this.getImageDistanceScale(camera.getPos(), imagePos);
            stack.push();
            stack.translate(imagePos.x - camera.getPos().x, imagePos.y - camera.getPos().y, imagePos.z - camera.getPos().z);
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));
            stack.scale(size, size, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, TextureStorage.targetImage);
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            drawQuad(buffer, stack.peek().getPositionMatrix(), 0.5F, withAlpha(drawColor, alpha));
            Render2DEngine.endBuilding(buffer);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            stack.pop();
         } else {
            this.imageTarget = null;
         }
      }
   }

   private void renderCircle(MatrixStack stack, LivingEntity target) {
      if (target != null && target != this.circleTarget) {
         this.circleTarget = target;
         this.circleHurtProgress = 0.0F;
      }

      this.circleAlphaAnim.setForward(target != null);
      if (this.circleTarget != null) {
         if (!this.circleTarget.isRemoved() && !this.circleAlphaAnim.finished(false)) {
            float alphaProgress = MathHelper.clamp(this.circleAlphaAnim.get(), 0.0F, 1.0F);
            this.circleHurtProgress = AnimationUtility.fast(this.circleHurtProgress, this.getHurtFactor(this.circleTarget), 14.0F);
            Camera camera = mc.gameRenderer.getCamera();
            float tickDelta = Render3DEngine.getTickDelta();
            double x = Render2DEngine.interpolate(this.circleTarget.prevX, this.circleTarget.getX(), tickDelta) - camera.getPos().x;
            double y = Render2DEngine.interpolate(this.circleTarget.prevY, this.circleTarget.getY(), tickDelta) - camera.getPos().y;
            double z = Render2DEngine.interpolate(this.circleTarget.prevZ, this.circleTarget.getZ(), tickDelta) - camera.getPos().z;
            float height = this.circleTarget.getHeight();
            double width = Math.max(0.05, this.circleTarget.getWidth() - 0.2F * this.circleHurtProgress);
            double duration = 1800.0;
            double elapsed = System.currentTimeMillis() % duration;
            boolean side = elapsed > duration / 2.0;
            double progress = elapsed / (duration / 2.0);
            progress = side ? progress - 1.0 : 1.0 - progress;
            progress = easeInOutQuad(progress);
            double eased = height / 1.25F * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1.0 : 1.0);
            stack.push();
            stack.translate(x, y, z);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            Matrix4f matrix = stack.peek().getPositionMatrix();
            BufferBuilder strip = Tessellator.getInstance().begin(DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

            for (int i = 0; i <= 360; i += 5) {
               double rad = Math.toRadians(i);
               float xPos = (float)(Math.cos(rad) * width);
               float zPos = (float)(Math.sin(rad) * width);
               Color color = this.getCircleColor(i, alphaProgress);
               int bottomAlpha = MathHelper.clamp(Math.round(180.0F * alphaProgress), 0, 255);
               strip.vertex(matrix, xPos, (float)(height * progress), zPos).color(withAlpha(color, bottomAlpha).getRGB());
               strip.vertex(matrix, xPos, (float)(height * progress + eased), zPos).color(withAlpha(color, 0).getRGB());
            }

            Render2DEngine.endBuilding(strip);
            BufferBuilder line = Tessellator.getInstance().begin(DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

            for (int i = 0; i <= 360; i += 5) {
               double rad = Math.toRadians(i);
               float xPos = (float)(Math.cos(rad) * width);
               float zPos = (float)(Math.sin(rad) * width);
               Color color = this.getCircleColor(i, alphaProgress);
               int lineAlpha = MathHelper.clamp(Math.round(255.0F * alphaProgress), 0, 255);
               line.vertex(matrix, xPos, (float)(height * progress), zPos).color(withAlpha(color, lineAlpha).getRGB());
            }

            Render2DEngine.endBuilding(line);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            stack.pop();
         } else {
            this.circleTarget = null;
            this.circleHurtProgress = 0.0F;
         }
      }
   }

   private void renderGhostV1(Entity target) {
      LivingEntity livingTarget = target instanceof LivingEntity living && !living.isRemoved() ? living : null;
      if (livingTarget != null) {
         this.ghostV1Target = livingTarget;
      }

      this.ghostV1Anim.setForward(livingTarget != null);
      if (this.ghostV1Target != null) {
         if (!this.ghostV1Target.isRemoved() && !this.ghostV1Anim.finished(false)) {
            float anim = this.ghostV1Anim.get();
            float red = MathHelper.clamp((this.ghostV1Target.hurtTime - Render3DEngine.getTickDelta()) / 20.0F, 0.0F, 1.0F);
            Render3DEngine.renderGhosts(this.ghostV1Target, anim, red, 0.62F);
         } else {
            this.ghostV1Target = null;
         }
      }
   }

   private void renderSpirits(MatrixStack stack, LivingEntity target) {
      if (target != null && target != this.spiritsTarget) {
         this.spiritsTarget = target;
         this.spiritsHurtProgress = 0.0F;
         this.resetSpiritsMotion();
      }

      this.spiritsAlphaAnim.setForward(target != null);
      if (this.spiritsTarget != null) {
         if (!this.spiritsTarget.isRemoved() && !this.spiritsAlphaAnim.finished(false)) {
            float alphaProgress = MathHelper.clamp(this.spiritsAlphaAnim.get(), 0.0F, 1.0F);
            long currentTime = System.currentTimeMillis();
            if (this.currentTimeSpirits == 0L) {
               this.currentTimeSpirits = currentTime;
            }

            long timeDiff = currentTime - this.currentTimeSpirits;
            if (timeDiff > 0L) {
               this.spiritsAnimation += (float)(5L * timeDiff) / 900.0F;
            }

            this.currentTimeSpirits = currentTime;
            Camera camera = mc.gameRenderer.getCamera();
            float tickDelta = Render3DEngine.getTickDelta();
            Vec3d pos = new Vec3d(
               Render2DEngine.interpolate(this.spiritsTarget.prevX, this.spiritsTarget.getX(), tickDelta),
               Render2DEngine.interpolate(this.spiritsTarget.prevY, this.spiritsTarget.getY(), tickDelta),
               Render2DEngine.interpolate(this.spiritsTarget.prevZ, this.spiritsTarget.getZ(), tickDelta)
            );
            Vec3d cameraPos = camera.getPos();
            double x = pos.x - cameraPos.x;
            double y = pos.y - cameraPos.y;
            double z = pos.z - cameraPos.z;
            this.spiritsHurtProgress = AnimationUtility.fast(this.spiritsHurtProgress, this.getHurtFactor(this.spiritsTarget), 14.0F);
            Color baseColor = withAlpha(blend(HudEditor.getColor(0), new Color(200, 70, 70), this.spiritsHurtProgress), Math.round(255.0F * alphaProgress));
            double orbitRadius = this.spiritsTarget.getWidth() * 0.78F + 0.06F;
            double heightScale = this.spiritsTarget.getHeight() / 2.0F;
            double baseYOffset = 0.5 * heightScale;
            double verticalSwing = 0.3F * heightScale;
            double ringYOffsetStep = 0.2F * heightScale;
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, TextureStorage.glow);
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            int step = 3;
            int particlesPerRing = 12;
            int ringCount = 3 * step;
            stack.push();

            for (int i = 0; i < ringCount; i += step) {
               for (int j = 0; j < particlesPerRing; j++) {
                  float phase = this.spiritsAnimation + j * 0.1F;
                  int squaredIndex = (int)Math.pow(i, 2.0);
                  double particleX = x + orbitRadius * Math.sin(phase + squaredIndex);
                  double particleY = y + baseYOffset + verticalSwing * Math.sin(this.spiritsAnimation + j * 0.2F) + ringYOffsetStep * i;
                  double particleZ = z + orbitRadius * Math.cos(phase - squaredIndex);
                  float scale = 0.005F + j / 2000.0F;
                  stack.push();
                  stack.translate(particleX, particleY, particleZ);
                  stack.scale(scale, scale, scale);
                  stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                  stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                  drawLargeGlowQuad(buffer, stack.peek().getPositionMatrix(), baseColor);
                  stack.pop();
               }
            }

            stack.pop();
            Render2DEngine.endBuilding(buffer);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         } else {
            this.spiritsTarget = null;
            this.spiritsHurtProgress = 0.0F;
            this.resetSpiritsMotion();
         }
      }
   }

   private void resetImage(LivingEntity currentTarget) {
      this.imageAnim.setForward(false);
      if (currentTarget != this.imageTarget && this.imageAnim.finished(false)) {
         this.imageTarget = null;
         this.imageHurtProgress = 0.0F;
      }
   }

   private void resetCircle(LivingEntity currentTarget) {
      this.circleAlphaAnim.setForward(false);
      if (currentTarget != this.circleTarget && this.circleAlphaAnim.finished(false)) {
         this.circleTarget = null;
         this.circleHurtProgress = 0.0F;
      }
   }

   private void resetGhostV1() {
      this.ghostV1Target = null;
      this.ghostV1Anim.setForward(false);
      this.ghostV1Anim.reset();
   }

   private void resetSpiritsMotion() {
      this.currentTimeSpirits = 0L;
      this.spiritsAnimation = 0.0F;
   }

   private void resetSpirits() {
      this.spiritsTarget = null;
      this.spiritsHurtProgress = 0.0F;
      this.spiritsAlphaAnim.setForward(false);
      this.spiritsAlphaAnim.reset();
      this.resetSpiritsMotion();
   }

   private void resetAll() {
      this.renderer.reset();
      this.imageTarget = null;
      this.imageHurtProgress = 0.0F;
      this.imageAnim.setForward(false);
      this.imageAnim.reset();
      this.circleTarget = null;
      this.circleHurtProgress = 0.0F;
      this.circleAlphaAnim.setForward(false);
      this.circleAlphaAnim.reset();
      this.resetGhostV1();
      this.resetSpirits();
   }

   private float getHurtFactor(LivingEntity target) {
      float hurtTicks = Math.max(0.0F, target.hurtTime - Render3DEngine.getTickDelta());
      return MathHelper.clamp((float)Math.sin(hurtTicks * (Math.PI / 20)), 0.0F, 1.0F);
   }

   private float smoothImageHurtProgress(float targetProgress) {
      float smoothed = AnimationUtility.fast(this.imageHurtProgress, targetProgress, 14.0F);
      return smoothed < 0.001F && targetProgress <= 0.001F ? 0.0F : smoothed;
   }

   private float getImageDistanceScale(Vec3d cameraPos, Vec3d targetPos) {
      if (!this.distanceEffect.getValue()) {
         return 1.0F;
      }

      float distance = (float)cameraPos.distanceTo(targetPos);
      float progress = (MathHelper.clamp(distance, 1.0F, 16.0F) - 1.0F) / 15.0F;
      return MathHelper.lerp(progress, 0.25F, 5.5F);
   }

   private Color getCircleColor(int offset, float alphaProgress) {
      Color syncedColor = multiplyBrightness(HudEditor.getColor(offset * 4), 0.75F);
      Color hurtColor = new Color(200, 70, 70, MathHelper.clamp(Math.round(255.0F * alphaProgress), 0, 255));
      return blend(syncedColor, hurtColor, this.circleHurtProgress);
   }

   private static double easeInOutQuad(double progress) {
      return progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
   }

   private static Color multiplyBrightness(Color color, float factor) {
      return new Color(
         MathHelper.clamp(Math.round(color.getRed() * factor), 0, 255),
         MathHelper.clamp(Math.round(color.getGreen() * factor), 0, 255),
         MathHelper.clamp(Math.round(color.getBlue() * factor), 0, 255),
         color.getAlpha()
      );
   }

   private static void drawQuad(BufferBuilder buffer, Matrix4f matrix, float halfSize, Color color) {
      int argb = color.getRGB();
      buffer.vertex(matrix, -halfSize, halfSize, 0.0F).texture(0.0F, 1.0F).color(argb);
      buffer.vertex(matrix, halfSize, halfSize, 0.0F).texture(1.0F, 1.0F).color(argb);
      buffer.vertex(matrix, halfSize, -halfSize, 0.0F).texture(1.0F, 0.0F).color(argb);
      buffer.vertex(matrix, -halfSize, -halfSize, 0.0F).texture(0.0F, 0.0F).color(argb);
   }

   private static void drawLargeGlowQuad(BufferBuilder buffer, Matrix4f matrix, Color color) {
      int argb = color.getRGB();
      int min = -25;
      int size = 50;
      buffer.vertex(matrix, min, min + size, 0.0F).texture(0.0F, 1.0F).color(argb);
      buffer.vertex(matrix, min + size, min + size, 0.0F).texture(1.0F, 1.0F).color(argb);
      buffer.vertex(matrix, min + size, min, 0.0F).texture(1.0F, 0.0F).color(argb);
      buffer.vertex(matrix, min, min, 0.0F).texture(0.0F, 0.0F).color(argb);
   }

   private static Color blend(Color first, Color second, float progress) {
      float p = MathHelper.clamp(progress, 0.0F, 1.0F);
      int r = MathHelper.clamp(Math.round(first.getRed() + (second.getRed() - first.getRed()) * p), 0, 255);
      int g = MathHelper.clamp(Math.round(first.getGreen() + (second.getGreen() - first.getGreen()) * p), 0, 255);
      int b = MathHelper.clamp(Math.round(first.getBlue() + (second.getBlue() - first.getBlue()) * p), 0, 255);
      int a = MathHelper.clamp(Math.round(first.getAlpha() + (second.getAlpha() - first.getAlpha()) * p), 0, 255);
      return new Color(r, g, b, a);
   }

   private static Color withAlpha(Color color, int alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), MathHelper.clamp(alpha, 0, 255));
   }

   public enum GhostType {
      Type1("Type 1"),
      Type2("Type 2"),
      Type3("Type 3");

      private final String displayName;

      GhostType(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   public enum Mode {
      Image("Image"),
      Circle("Circle"),
      Ghost("Ghost");

      private final String displayName;

      Mode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   public static class TargetEspRenderer {
      private final Supplier<Integer> particleLimit;
      private final Supplier<Float> particleSize;
      private final Supplier<Identifier> textureSupplier;
      private final InfinityAnimation moving = new InfinityAnimation();
      private final Animation targetEspAnim = new Animation().setEasing(Easing.TARGETESP_EASE_OUT_BACK).setSpeed(300).setSize(1.0F).setForward(false);
      private final List<GhostRenderer3D> particles = new ArrayList<>();
      private LivingEntity currentTarget;
      private Vec3d smoothedTargetPos;

      public TargetEspRenderer(Supplier<Integer> particleLimit, Supplier<Float> particleSize, Supplier<Identifier> textureSupplier) {
         this.particleLimit = particleLimit;
         this.particleSize = particleSize;
         this.textureSupplier = textureSupplier;
      }

      public void render(LivingEntity target) {
         if (target != null && !target.isRemoved()) {
            Vec3d orbitCenter = this.smoothTargetPosition(target);
            this.spawnParticleIfNeeded(orbitCenter);
            float fpsFactor = 500.0F / Math.max(Module.mc.getCurrentFps(), 5);
            this.moving.animate(this.moving.get() + 20.0F, 55);
            this.targetEspAnim.setForward(target.hurtTime > 7);
            float movementValue = this.moving.get();
            float animationFactor = this.targetEspAnim.get();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, this.textureSupplier.get());
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            Camera camera = Module.mc.gameRenderer.getCamera();

            for (int index = 0; index < this.particles.size(); index++) {
               GhostRenderer3D particle = this.particles.get(index);
               this.updateParticle(particle, index, fpsFactor, target, orbitCenter, movementValue, animationFactor);
               particle.render(buffer, camera);
            }

            Render2DEngine.endBuilding(buffer);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
         } else {
            this.reset();
         }
      }

      private Vec3d smoothTargetPosition(LivingEntity target) {
         float tickDelta = Render3DEngine.getTickDelta();
         Vec3d rawPos = new Vec3d(
            Render2DEngine.interpolate(target.prevX, target.getX(), tickDelta),
            Render2DEngine.interpolate(target.prevY, target.getY(), tickDelta),
            Render2DEngine.interpolate(target.prevZ, target.getZ(), tickDelta)
         );
         if (this.smoothedTargetPos == null) {
            this.currentTarget = target;
            this.smoothedTargetPos = rawPos;
            return this.smoothedTargetPos;
         } else if (this.currentTarget != target) {
            this.currentTarget = target;
            this.smoothedTargetPos = rawPos;
            return this.smoothedTargetPos;
         } else {
            double distance = this.smoothedTargetPos.distanceTo(rawPos);
            if (distance < 0.008) {
               return this.smoothedTargetPos;
            } else if (distance > 1.2) {
               this.smoothedTargetPos = rawPos;
               return this.smoothedTargetPos;
            } else {
               double baseFactor = AnimationUtility.deltaTime() * 34.0F;
               double distanceBoost = distance * 1.15;
               double factor = MathHelper.clamp(baseFactor + distanceBoost, 0.38, 0.92);
               this.smoothedTargetPos = this.smoothedTargetPos.lerp(rawPos, factor);
               return this.smoothedTargetPos;
            }
         }
      }

      private void spawnParticleIfNeeded(Vec3d orbitCenter) {
         int desired = Math.max(1, this.particleLimit.get());
         if (this.particles.size() < desired) {
            this.particles.add(new GhostRenderer3D(orbitCenter, Vec3d.ZERO, this.particleSize.get()));
         }

         while (this.particles.size() > desired) {
            this.particles.removeLast();
         }
      }

      private void updateParticle(
         GhostRenderer3D particle, int index, float fpsFactor, LivingEntity target, Vec3d orbitCenter, float movementValue, float animationFactor
      ) {
         int segments = Math.max(1, this.particleLimit.get());
         float angleOffset = index * 360.0F / segments;
         float currentAngle = movementValue + angleOffset;
         double rad = Math.toRadians(currentAngle);
         double baseRadius = this.getOrbitRadius(target, this.particleSize.get());
         double dynamicRadius = baseRadius - baseRadius * animationFactor;
         double offsetX = Math.sin(rad) * dynamicRadius;
         double offsetZ = Math.cos(rad) * dynamicRadius;
         double verticalSwing = Math.sin(Math.toRadians(movementValue / (index + 1.0F))) * this.getVerticalAmplitude(target);
         Vec3d desiredPos = orbitCenter.add(offsetX, this.getVerticalCenter(target) + verticalSwing, offsetZ);
         double mul = 0.05F * fpsFactor;
         Vec3d motion = desiredPos.subtract(particle.getPosition()).multiply(mul, mul, mul);
         particle.setMotion(motion);
         particle.tick();
      }

      private double getOrbitRadius(LivingEntity target, float particleSize) {
         Box box = HitBox.getBaseBoundingBox(target);
         double hitboxWidth = Math.max(box.getLengthX(), box.getLengthZ());
         return hitboxWidth * 0.5 + particleSize * 0.25;
      }

      private double getVerticalCenter(LivingEntity target) {
         return this.getVerticalAmplitude(target) - 0.7;
      }

      private double getVerticalAmplitude(LivingEntity target) {
         return HitBox.getBaseBoundingBox(target).getLengthY() * 0.5;
      }

      public void reset() {
         this.particles.clear();
         this.currentTarget = null;
         this.smoothedTargetPos = null;
         this.moving.getAnimation().reset();
         this.targetEspAnim.reset();
      }
   }
}
