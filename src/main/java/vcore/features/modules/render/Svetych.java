package vcore.features.modules.render;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vcore.features.modules.Module;
import vcore.setting.Setting;
import vcore.utility.Timer;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.Render3DEngine;
import vcore.utility.render.TextureStorage;

public class Svetych extends Module {
   private final Setting<Integer> cubeCount = new Setting<>("Cube count", 100, 50, 300);
   private final Setting<Float> maxAlpha = new Setting<>("Max Alpha", 1.0F, 0.1F, 1.0F);
   private static final float CUBE_SIZE = 0.26F;
   private static final long SPAWN_DELAY_MS = 200L;
   private final List<Svetych.Particle> particles = new ArrayList<>();
   private final Timer spawnTimer = new Timer();

   public Svetych() {
      super("Svetych", "Floating cubes with physics and outlines.", Module.Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.particles.clear();
   }

   @Override
   public void onLogout() {
      this.particles.clear();
   }

   @Override
   public void onUpdate() {
      if (mc.player != null && mc.world != null) {
         if (this.particles.size() < this.cubeCount.getValue() && this.spawnTimer.passedMs(200L)) {
            this.particles.add(new Svetych.Particle(mc.player.getPos(), mc.player.getHeight()));
            this.spawnTimer.reset();
         }
      } else {
         this.particles.clear();
      }
   }

   @Override
   public void onRender3D(MatrixStack stack) {
      if (mc.player != null && mc.world != null && !this.particles.isEmpty()) {
         long now = System.currentTimeMillis();
         Camera camera = mc.gameRenderer.getCamera();
         Vec3d cameraPos = camera.getPos();
         float rotation = (float)(now % 9000L) / 9000.0F * 360.0F;
         Color baseColor = HudEditor.getColor((int)(now / 12L));
         this.particles.removeIf(particlex -> particlex.shouldRemove(now));

         for (Svetych.Particle particle : this.particles) {
            particle.update(now);
         }

         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(false);
         RenderSystem.disableCull();
         float alphaLimit = this.maxAlpha.getValue();
         this.renderCubes(stack, cameraPos, rotation, baseColor, now, alphaLimit);
         this.renderCubeLines(stack, cameraPos, rotation, baseColor, now, alphaLimit);
         this.renderGlow(camera, baseColor, now, alphaLimit);
         RenderSystem.enableCull();
         RenderSystem.depthMask(true);
         RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.disableBlend();
      }
   }

   private void renderCubes(MatrixStack stack, Vec3d cameraPos, float rotation, Color baseColor, long now, float alphaLimit) {
      RenderSystem.setShader(GameRenderer::getPositionColorProgram);
      BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);

      for (Svetych.Particle particle : this.particles) {
         particle.renderCube(stack, buffer, cameraPos, rotation, baseColor, now, alphaLimit);
      }

      Render2DEngine.endBuilding(buffer);
   }

   private void renderCubeLines(MatrixStack stack, Vec3d cameraPos, float rotation, Color baseColor, long now, float alphaLimit) {
      RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
      RenderSystem.lineWidth(1.0F);
      BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.LINES, VertexFormats.LINES);

      for (Svetych.Particle particle : this.particles) {
         particle.renderLines(stack, buffer, cameraPos, rotation, baseColor, now, alphaLimit);
      }

      Render2DEngine.endBuilding(buffer);
   }

   private void renderGlow(Camera camera, Color baseColor, long now, float alphaLimit) {
      this.renderGlowLayer(camera, baseColor, now, TextureStorage.dashBloom, 1.56F, 80, alphaLimit);
      this.renderGlowLayer(camera, baseColor, now, TextureStorage.dashBloomSample, 0.52F, 140, alphaLimit);
   }

   private void renderGlowLayer(Camera camera, Color baseColor, long now, Identifier texture, float size, int glowAlpha, float alphaLimit) {
      RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
      RenderSystem.setShaderTexture(0, texture);
      BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

      for (Svetych.Particle particle : this.particles) {
         particle.renderGlow(buffer, camera, baseColor, now, size, glowAlpha, alphaLimit);
      }

      Render2DEngine.endBuilding(buffer);
   }

   private static void drawCube(BufferBuilder buffer, Matrix4f matrix, Color color, float size) {
      float h = size / 2.0F;
      int argb = color.getRGB();
      buffer.vertex(matrix, -h, h, -h).color(argb);
      buffer.vertex(matrix, -h, h, h).color(argb);
      buffer.vertex(matrix, h, h, h).color(argb);
      buffer.vertex(matrix, h, h, -h).color(argb);
      buffer.vertex(matrix, -h, -h, -h).color(argb);
      buffer.vertex(matrix, h, -h, -h).color(argb);
      buffer.vertex(matrix, h, -h, h).color(argb);
      buffer.vertex(matrix, -h, -h, h).color(argb);
      buffer.vertex(matrix, -h, h, h).color(argb);
      buffer.vertex(matrix, -h, -h, h).color(argb);
      buffer.vertex(matrix, h, -h, h).color(argb);
      buffer.vertex(matrix, h, h, h).color(argb);
      buffer.vertex(matrix, -h, h, -h).color(argb);
      buffer.vertex(matrix, h, h, -h).color(argb);
      buffer.vertex(matrix, h, -h, -h).color(argb);
      buffer.vertex(matrix, -h, -h, -h).color(argb);
      buffer.vertex(matrix, -h, h, -h).color(argb);
      buffer.vertex(matrix, -h, -h, -h).color(argb);
      buffer.vertex(matrix, -h, -h, h).color(argb);
      buffer.vertex(matrix, -h, h, h).color(argb);
      buffer.vertex(matrix, h, h, -h).color(argb);
      buffer.vertex(matrix, h, h, h).color(argb);
      buffer.vertex(matrix, h, -h, h).color(argb);
      buffer.vertex(matrix, h, -h, -h).color(argb);
   }

   private static void drawLines(MatrixStack matrices, BufferBuilder buffer, Color color, float size) {
      float h = size / 2.0F;
      Render3DEngine.vertexLine(matrices, buffer, -h, -h, -h, h, -h, -h, color);
      Render3DEngine.vertexLine(matrices, buffer, h, -h, -h, h, -h, h, color);
      Render3DEngine.vertexLine(matrices, buffer, h, -h, h, -h, -h, h, color);
      Render3DEngine.vertexLine(matrices, buffer, -h, -h, h, -h, -h, -h, color);
      Render3DEngine.vertexLine(matrices, buffer, -h, h, -h, h, h, -h, color);
      Render3DEngine.vertexLine(matrices, buffer, h, h, -h, h, h, h, color);
      Render3DEngine.vertexLine(matrices, buffer, h, h, h, -h, h, h, color);
      Render3DEngine.vertexLine(matrices, buffer, -h, h, h, -h, h, -h, color);
      Render3DEngine.vertexLine(matrices, buffer, -h, -h, -h, -h, h, -h, color);
      Render3DEngine.vertexLine(matrices, buffer, h, -h, -h, h, h, -h, color);
      Render3DEngine.vertexLine(matrices, buffer, h, -h, h, h, h, h, color);
      Render3DEngine.vertexLine(matrices, buffer, -h, -h, h, -h, h, h, color);
   }

   private static void drawTexturedQuad(BufferBuilder buffer, Matrix4f matrix, float size, Color color) {
      int argb = color.getRGB();
      buffer.vertex(matrix, -size, size, 0.0F).texture(0.0F, 1.0F).color(argb);
      buffer.vertex(matrix, size, size, 0.0F).texture(1.0F, 1.0F).color(argb);
      buffer.vertex(matrix, size, -size, 0.0F).texture(1.0F, 0.0F).color(argb);
      buffer.vertex(matrix, -size, -size, 0.0F).texture(0.0F, 0.0F).color(argb);
   }

   private static Color withAlpha(Color color, int alpha) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
   }

   private static float easeInOutQuad(float value) {
      float t = Math.max(0.0F, Math.min(1.0F, value));
      return t < 0.5F ? 2.0F * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 2.0) / 2.0F;
   }

   private static class Particle {
      private static final long FADE_IN_MS = 1200L;
      private static final long LIFE_MS = 7000L;
      private static final long FADE_OUT_MS = 1200L;
      private double x;
      private double y;
      private double z;
      private double motionX;
      private double motionY;
      private double motionZ;
      private final long start = System.currentTimeMillis();
      private final float phase = (float)(Math.random() * 100.0);

      private Particle(Vec3d pos, float height) {
         double radius = 2.0 + Math.random() * 3.0;
         double angle = Math.random() * Math.PI * 2.0;
         this.x = pos.x + Math.cos(angle) * radius;
         this.z = pos.z + Math.sin(angle) * radius;
         this.y = pos.y + 2.0 + Math.random() * (height + 2.0);
         this.motionX = (Math.random() - 0.5) * 0.06;
         this.motionY = (Math.random() - 0.5) * 0.06;
         this.motionZ = (Math.random() - 0.5) * 0.06;
      }

      private void update(long now) {
         if (Module.mc.world != null) {
            if (this.isHit(this.x + this.motionX, this.y, this.z)) {
               this.motionX *= -0.8;
            } else {
               this.x = this.x + this.motionX;
            }

            if (this.isHit(this.x, this.y + this.motionY, this.z)) {
               this.motionY *= -0.8;
            } else {
               this.y = this.y + this.motionY;
            }

            if (this.isHit(this.x, this.y, this.z + this.motionZ)) {
               this.motionZ *= -0.8;
            } else {
               this.z = this.z + this.motionZ;
            }

            this.motionX *= 0.99;
            this.motionY *= 0.99;
            this.motionZ *= 0.99;
         }
      }

      private void renderCube(MatrixStack matrices, BufferBuilder buffer, Vec3d cameraPos, float rotation, Color baseColor, long now, float alphaLimit) {
         float alpha = this.getAlpha(now);
         if (!(alpha <= 0.0F)) {
            matrices.push();
            matrices.translate(this.x - cameraPos.x, this.y - cameraPos.y, this.z - cameraPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation + this.phase));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation * 0.5F));
            Svetych.drawCube(buffer, matrices.peek().getPositionMatrix(), Svetych.withAlpha(baseColor, Math.round(255.0F * alpha * 0.2F * alphaLimit)), 0.26F);
            matrices.pop();
         }
      }

      private void renderLines(MatrixStack matrices, BufferBuilder buffer, Vec3d cameraPos, float rotation, Color baseColor, long now, float alphaLimit) {
         float alpha = this.getAlpha(now);
         if (!(alpha <= 0.0F)) {
            matrices.push();
            matrices.translate(this.x - cameraPos.x, this.y - cameraPos.y, this.z - cameraPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation + this.phase));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation * 0.5F));
            Svetych.drawLines(matrices, buffer, Svetych.withAlpha(baseColor, Math.round(255.0F * alpha * 0.4F * alphaLimit)), 0.26F);
            matrices.pop();
         }
      }

      private void renderGlow(BufferBuilder buffer, Camera camera, Color baseColor, long now, float size, int glowAlpha, float alphaLimit) {
         float alpha = this.getAlpha(now);
         if (!(alpha <= 0.0F)) {
            MatrixStack matrices = new MatrixStack();
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
            matrices.translate(this.x - camera.getPos().x, this.y - camera.getPos().y, this.z - camera.getPos().z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            Svetych.drawTexturedQuad(
               buffer, matrices.peek().getPositionMatrix(), size, Svetych.withAlpha(baseColor, Math.round(glowAlpha * alpha * alphaLimit))
            );
         }
      }

      private boolean shouldRemove(long now) {
         return now - this.start > 8200L;
      }

      private float getAlpha(long now) {
         long age = now - this.start;
         if (age < 1200L) {
            return Svetych.easeInOutQuad((float)age / 1200.0F);
         } else {
            return age > 7000L ? 1.0F - Svetych.easeInOutQuad((float)(age - 7000L) / 1200.0F) : 1.0F;
         }
      }

      private boolean isHit(double px, double py, double pz) {
         if (Module.mc.world == null) {
            return false;
         }

         BlockPos pos = BlockPos.ofFloored(px, py, pz);
         return Module.mc.world.getBlockState(pos).isFullCube(Module.mc.world, pos);
      }
   }
}
