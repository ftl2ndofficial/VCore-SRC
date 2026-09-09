package vcore.features.modules.render.frageffects;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vcore.features.modules.Module;
import vcore.utility.Timer;
import vcore.utility.render.Render3DEngine;

public final class FragParticleRenderer {
   private final List<FragParticleRenderer.FragParticle> particles = new ArrayList<>();
   private final Random random = new Random();

   private FragParticleRenderer() {
   }

   public static FragParticleRenderer create() {
      return new FragParticleRenderer();
   }

   public void spawnParticlesVoid(Vec3d mainPos, int lifeTimeMin, int lifeTimeMax, float maxFlight, int count, float gravity) {
      float maxXZMotion = maxFlight / (lifeTimeMin / 50.0F) * 1.12F;
      float maxYMotion = maxFlight / (lifeTimeMin / 50.0F) * 0.31F;

      for (int i = 0; i < count; i++) {
         this.particles
            .add(
               new FragParticleRenderer.FragParticle(
                  mainPos, FragEffectMath.lerp(lifeTimeMin, lifeTimeMax, this.random.nextFloat()), maxXZMotion, maxYMotion, gravity
               )
            );
      }
   }

   public void updateParticlesList() {
      if (!this.particles.isEmpty()) {
         this.particles.removeIf(FragParticleRenderer.FragParticle::removeIf);

         for (FragParticleRenderer.FragParticle particle : this.particles) {
            particle.updateMotion();
         }
      }
   }

   public void renderParticles(MatrixStack matrices) {
      if (!this.particles.isEmpty() && Module.mc.gameRenderer != null) {
         List<FragParticleRenderer.ColoredPoint>[] pointsBySize = this.createPointBuckets(160);
         float tickDelta = Render3DEngine.getTickDelta();
         Vec3d cameraPos = Module.mc.gameRenderer.getCamera().getPos();

         for (FragParticleRenderer.FragParticle particle : this.particles) {
            Vec3d renderPos = particle.getRenderPos(tickDelta).subtract(cameraPos);
            pointsBySize[particle.getScaledBeginInt(160)]
               .add(new FragParticleRenderer.ColoredPoint(renderPos.x, renderPos.y, renderPos.z, particle.getColor()));
         }

         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(false);
         RenderSystem.setShader(GameRenderer::getPositionColorProgram);
         Matrix4f matrix = matrices.peek().getPositionMatrix();
         float pointScale = 0.25F;

         for (List<FragParticleRenderer.ColoredPoint> points : pointsBySize) {
            if (!points.isEmpty()) {
               BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
               float radius = pointScale * 0.003F;

               for (FragParticleRenderer.ColoredPoint point : points) {
                  this.putPointQuad(buffer, matrix, point, radius);
               }

               BufferRenderer.drawWithGlobalProgram(buffer.end());
            }

            pointScale += 0.25F;
         }

         RenderSystem.depthMask(true);
         RenderSystem.disableBlend();
      }
   }

   public void clear() {
      this.particles.clear();
   }

   private List<FragParticleRenderer.ColoredPoint>[] createPointBuckets(int count) {
      List<FragParticleRenderer.ColoredPoint>[] buckets = new ArrayList[count];

      for (int i = 0; i < buckets.length; i++) {
         buckets[i] = new ArrayList<>();
      }

      return buckets;
   }

   private void putPointQuad(BufferBuilder buffer, Matrix4f matrix, FragParticleRenderer.ColoredPoint point, float radius) {
      float x = (float)point.x;
      float y = (float)point.y;
      float z = (float)point.z;
      buffer.vertex(matrix, x - radius, y + radius, z).color(point.color);
      buffer.vertex(matrix, x + radius, y + radius, z).color(point.color);
      buffer.vertex(matrix, x + radius, y - radius, z).color(point.color);
      buffer.vertex(matrix, x - radius, y - radius, z).color(point.color);
   }

   private record ColoredPoint(double x, double y, double z, int color) {
   }

   private static final class FragParticle {
      private static final Random RANDOM = new Random();
      private Vec3d pos;
      private Vec3d prevPos;
      private Vec3d motion;
      private final int maxTime;
      private final float gravity;
      private final Timer stopWatch = new Timer();
      private final int color = Color.HSBtoRGB(0.0F, 0.0F, (float)FragEffectMath.easeExpoInOut(RANDOM.nextFloat()));

      private FragParticle(Vec3d spawnPos, int maxTime, float motionXZSpeedMax, float motionYSpeedMax, float gravity) {
         this.pos = spawnPos;
         this.prevPos = spawnPos;
         this.maxTime = maxTime;
         this.motion = new Vec3d(
            FragEffectMath.lerp(-motionXZSpeedMax, motionXZSpeedMax, RANDOM.nextFloat()),
            FragEffectMath.lerp(-motionYSpeedMax / 4.0F, motionYSpeedMax, RANDOM.nextFloat()),
            FragEffectMath.lerp(-motionXZSpeedMax, motionXZSpeedMax, RANDOM.nextFloat())
         );
         this.gravity = gravity;
      }

      private void updateMotion() {
         this.prevPos = this.pos;
         this.pos = this.pos.add(this.motion);
         this.motion = new Vec3d(this.motion.x * 0.99F, (this.motion.y - this.gravity) * 0.994F, this.motion.z * 0.99F);
      }

      private Vec3d getRenderPos(float partialTicks) {
         return this.prevPos
            .add((this.pos.x - this.prevPos.x) * partialTicks, (this.pos.y - this.prevPos.y) * partialTicks, (this.pos.z - this.prevPos.z) * partialTicks);
      }

      private boolean removeIf() {
         return this.stopWatch.passedMs(this.maxTime);
      }

      private int getColor() {
         return FragEffectMath.multAlpha(this.color, 1.0F - Math.min((float)this.stopWatch.getPassedTimeMs() / this.maxTime, 1.0F));
      }

      private int getScaledBeginInt(int maxInt) {
         return Math.floorMod(this.hashCode(), maxInt);
      }
   }
}
