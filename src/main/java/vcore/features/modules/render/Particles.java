package vcore.features.modules.render;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap.Type;
import org.joml.Matrix4f;
import vcore.events.impl.EventAttack;
import vcore.features.modules.Module;
import vcore.setting.Setting;
import vcore.setting.impl.ColorSetting;
import vcore.setting.impl.SettingGroup;
import vcore.utility.math.MathUtility;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.TextureStorage;

public class Particles extends Module {
   private static final int HIT_SPAWN_COUNT = 35;
   private static final int PEARL_SPAWN_COUNT = 4;
   private static final int WORLD_SPAWN_COUNT = 7;
   private static final int PEARL_PARTICLE_LIMIT = 650;
   private static final int WORLD_PARTICLE_LIMIT = 450;
   private static final long HIT_LIFESPAN_MS = 2000L;
   private static final long PEARL_LIFESPAN_MS = 2000L;
   private static final long WORLD_LIFESPAN_MS = 4000L;
   private static final long HIT_FADE_IN_MS = 400L;
   private static final long PEARL_FADE_IN_MS = 700L;
   private static final long WORLD_FADE_IN_MS = 1500L;
   private static final long HIT_FADE_OUT_START_MS = 600L;
   private static final long PEARL_FADE_OUT_START_MS = 1200L;
   private static final long WORLD_FADE_OUT_START_MS = 2200L;
   private static final long FADE_ANIMATION_MS = 400L;
   private static final double PARTICLE_SPEED_MULTIPLIER = 0.2;
   private static final float SPAWN_START_SCALE = 0.55F;
   private static final float BLOOM_INNER_SCALE = 0.5F;
   private static final float BLOOM_INNER_START = 0.72F;
   private static final float BLOOM_INNER_ALPHA = 0.55F;
   private final Setting<Boolean> hit = new Setting<>("Hit", true);
   private final Setting<Boolean> pearl = new Setting<>("Pearl", true);
   private final Setting<Boolean> world = new Setting<>("World", true);
   private final Setting<SettingGroup> hitSetting = new Setting<>("Hit Setting", new SettingGroup(false, 0), v -> this.hit.getValue());
   private final Setting<Particles.ParticleMode> hitMode = new Setting<>("Mode", Particles.ParticleMode.Genshin).addToGroup(this.hitSetting);
   private final Setting<Float> hitScale = new Setting<>("Scale", 0.5F, 0.1F, 2.0F).addToGroup(this.hitSetting).step(0.1F);
   private final Setting<Boolean> hitSyncColor = new Setting<>("SyncColor", true).addToGroup(this.hitSetting);
   private final Setting<ColorSetting> hitColor = new Setting<>(
         "Color", new ColorSetting(new Color(255, 255, 255, 255).getRGB()), v -> !this.hitSyncColor.getValue()
      )
      .addToGroup(this.hitSetting);
   private final Setting<SettingGroup> pearlSetting = new Setting<>("Pearl Setting", new SettingGroup(false, 0), v -> this.pearl.getValue());
   private final Setting<Particles.ParticleMode> pearlMode = new Setting<>("Mode", Particles.ParticleMode.Rhombus).addToGroup(this.pearlSetting);
   private final Setting<Float> pearlScale = new Setting<>("Scale", 0.5F, 0.1F, 2.0F).addToGroup(this.pearlSetting).step(0.1F);
   private final Setting<Boolean> pearlSyncColor = new Setting<>("SyncColor", true).addToGroup(this.pearlSetting);
   private final Setting<ColorSetting> pearlColor = new Setting<>(
         "Color", new ColorSetting(new Color(255, 255, 255, 255).getRGB()), v -> !this.pearlSyncColor.getValue()
      )
      .addToGroup(this.pearlSetting);
   private final Setting<SettingGroup> worldSetting = new Setting<>("World Setting", new SettingGroup(false, 0), v -> this.world.getValue());
   private final Setting<Particles.ParticleMode> worldMode = new Setting<>("Mode", Particles.ParticleMode.Star).addToGroup(this.worldSetting);
   private final Setting<Float> worldScale = new Setting<>("Scale", 0.5F, 0.1F, 2.0F).addToGroup(this.worldSetting).step(0.1F);
   private final Setting<Boolean> worldSyncColor = new Setting<>("SyncColor", true).addToGroup(this.worldSetting);
   private final Setting<ColorSetting> worldColor = new Setting<>(
         "Color", new ColorSetting(new Color(255, 255, 255, 255).getRGB()), v -> !this.worldSyncColor.getValue()
      )
      .addToGroup(this.worldSetting);
   private final List<Particles.Particle> hitParticles = new ArrayList<>();
   private final List<Particles.Particle> pearlParticles = new ArrayList<>();
   private final List<Particles.Particle> worldParticles = new ArrayList<>();
   private long lastRenderTime = System.nanoTime();

   public Particles() {
      super("Particles", "Hit, pearl and world particle effects.", Module.Category.RENDER);
   }

   @Override
   public void onDisable() {
      this.clearParticles();
   }

   @Override
   public void onLogout() {
      this.clearParticles();
   }

   @Override
   public void onUpdate() {
      if (mc.player != null && mc.world != null) {
         this.removeExpiredParticles(this.hitParticles, 2000L);
         this.removeExpiredParticles(this.pearlParticles, 2000L);
         this.removeExpiredParticles(this.worldParticles, 4000L);
         this.updatePearlParticles();
         this.updateWorldParticles();
      } else {
         this.clearParticles();
      }
   }

   @EventHandler
   public void onAttack(EventAttack event) {
      if (this.hit.getValue() && event != null && !event.isPre() && mc.player != null) {
         Entity target = event.getEntity();
         if (target != null) {
            if (target instanceof LivingEntity living) {
               if (living.getHealth() <= 0.0F) {
                  return;
               }

               if (living.hurtTime > 1) {
                  return;
               }
            }

            for (int i = 0; i < 35; i++) {
               Vec3d position = new Vec3d(target.getX(), target.getY() + MathUtility.random(0.0, target.getHeight()), target.getZ())
                  .add(0.0, getParticleRadius(this.hitScale.getValue()), 0.0);
               this.hitParticles
                  .add(
                     new Particles.Particle(
                        this.hitMode.getValue().texture,
                        position,
                        new Vec3d(MathUtility.random(-6.0, 6.0), MathUtility.random(-6.0, 6.0), MathUtility.random(-6.0, 6.0)),
                        true,
                        this.hitParticles.size(),
                        getParticleRadius(this.hitScale.getValue())
                     )
                  );
            }
         }
      }
   }

   @Override
   public void onRender3D(MatrixStack stack) {
      if (mc.player != null && mc.world != null) {
         double deltaTime = this.getRenderDeltaTime();
         if (this.hit.getValue() && !this.hitParticles.isEmpty()) {
            this.renderParticles(stack, this.hitParticles, deltaTime, this.hitSyncColor.getValue(), this.hitColor.getValue().getColorObject(), 400L, 600L);
         }

         if (this.pearl.getValue() && !this.pearlParticles.isEmpty()) {
            this.renderParticles(
               stack, this.pearlParticles, deltaTime, this.pearlSyncColor.getValue(), this.pearlColor.getValue().getColorObject(), 700L, 1200L
            );
         }

         if (this.world.getValue() && !this.worldParticles.isEmpty()) {
            this.renderParticles(
               stack, this.worldParticles, deltaTime, this.worldSyncColor.getValue(), this.worldColor.getValue().getColorObject(), 1500L, 2200L
            );
         }
      }
   }

   private void removeExpiredParticles(List<Particles.Particle> particles, long lifespanMs) {
      long now = System.currentTimeMillis();
      particles.removeIf(particle -> particle.isExpired(now, lifespanMs));
   }

   private void updatePearlParticles() {
      if (!this.pearl.getValue()) {
         this.pearlParticles.clear();
      } else if (this.pearlParticles.size() < 650) {
         for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EnderPearlEntity && !entity.isOnGround() && !(entity.getVelocity().lengthSquared() <= 1.0E-4)) {
               Vec3d position = entity.getPos();
               float particleSize = getParticleRadius(this.pearlScale.getValue());

               for (int i = 0; i < 4 && this.pearlParticles.size() < 650; i++) {
                  this.pearlParticles
                     .add(
                        new Particles.Particle(
                           this.pearlMode.getValue().texture,
                           position.add(MathUtility.random(-0.2, 0.2), MathUtility.random(-0.2, 0.2), MathUtility.random(-0.2, 0.2))
                              .add(0.0, particleSize, 0.0),
                           new Vec3d(MathUtility.random(-1.0, 1.0), MathUtility.random(-0.3, 0.3), MathUtility.random(-1.0, 1.0)),
                           true,
                           this.pearlParticles.size(),
                           particleSize
                        )
                     );
               }
            }
         }
      }
   }

   private void updateWorldParticles() {
      if (!this.world.getValue()) {
         this.worldParticles.clear();
      } else if (this.worldParticles.size() < 450) {
         int radius = 12;
         float particleSize = getParticleRadius(this.worldScale.getValue());

         for (int i = 0; i < 7 && this.worldParticles.size() < 450; i++) {
            Vec3d base = mc.player.getPos().add(MathUtility.random(-radius, radius), 0.0, MathUtility.random(-radius, radius));
            BlockPos topPos = mc.world.getTopPosition(Type.MOTION_BLOCKING, BlockPos.ofFloored(base));
            Vec3d spawnPos = new Vec3d(
               topPos.getX() + MathUtility.random(0.0, 1.0),
               mc.player.getY() + MathUtility.random(mc.player.getHeight(), radius),
               topPos.getZ() + MathUtility.random(0.0, 1.0)
            );

            while (!mc.world.isAir(BlockPos.ofFloored(spawnPos)) && spawnPos.y < mc.world.getTopY()) {
               spawnPos = spawnPos.add(0.0, 1.0, 0.0);
            }

            this.worldParticles
               .add(
                  new Particles.Particle(
                     this.worldMode.getValue().texture,
                     spawnPos.add(0.0, particleSize, 0.0),
                     new Vec3d(
                        mc.player.getVelocity().x + MathUtility.random(-2.0, 2.0),
                        MathUtility.random(-0.2, 0.2),
                        mc.player.getVelocity().z + MathUtility.random(-2.0, 2.0)
                     ),
                     true,
                     this.worldParticles.size(),
                     particleSize
                  )
               );
         }
      }
   }

   private void renderParticles(
      MatrixStack stack, List<Particles.Particle> particles, double deltaTime, boolean syncColor, Color fixedColor, long fadeInMs, long fadeOutStartMs
   ) {
      if (mc.gameRenderer != null) {
         Camera camera = mc.gameRenderer.getCamera();
         stack.push();

         for (Particles.ParticleTexture texture : Particles.ParticleTexture.values()) {
            if (this.hasTexture(particles, texture)) {
               BufferBuilder buffer = startQuads(texture.identifier);

               for (Particles.Particle particle : particles) {
                  if (particle.texture == texture) {
                     particle.update(deltaTime);
                     particle.render(stack, buffer, camera, texture, syncColor, fixedColor, fadeInMs, fadeOutStartMs);
                  }
               }

               endQuads(buffer);
            }
         }

         stack.pop();
      }
   }

   private void clearParticles() {
      this.hitParticles.clear();
      this.pearlParticles.clear();
      this.worldParticles.clear();
   }

   private boolean hasTexture(List<Particles.Particle> particles, Particles.ParticleTexture texture) {
      for (Particles.Particle particle : particles) {
         if (particle.texture == texture) {
            return true;
         }
      }

      return false;
   }

   private static BufferBuilder startQuads(Identifier texture) {
      RenderSystem.enableBlend();
      applyLightningBlend();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
      RenderSystem.setShaderTexture(0, texture);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      return Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
   }

   private static void applyLightningBlend() {
      RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
   }

   private static void endQuads(BufferBuilder bufferBuilder) {
      Render2DEngine.endBuilding(bufferBuilder);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.depthMask(true);
      RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE_MINUS_SRC_ALPHA);
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   private double getRenderDeltaTime() {
      long now = System.nanoTime();
      double delta = (now - this.lastRenderTime) / 1.0E9;
      this.lastRenderTime = now;
      return MathUtility.clamp(delta, 0.0, 0.05);
   }

   private static void putCenteredTexturedQuad(BufferBuilder bufferBuilder, Matrix4f matrix, float radius, int color) {
      bufferBuilder.vertex(matrix, -radius, radius, 0.0F).texture(0.0F, 1.0F).color(color);
      bufferBuilder.vertex(matrix, radius, radius, 0.0F).texture(1.0F, 1.0F).color(color);
      bufferBuilder.vertex(matrix, radius, -radius, 0.0F).texture(1.0F, 0.0F).color(color);
      bufferBuilder.vertex(matrix, -radius, -radius, 0.0F).texture(0.0F, 0.0F).color(color);
   }

   private static float getParticleRadius(float scale) {
      return 0.05F + scale * 0.2F;
   }

   private static float randomSteppedAngle() {
      return Math.round(MathUtility.random(0.0F, 360.0F) / 15.0F) * 15.0F;
   }

   private static float easeOutQuad(float progress) {
      return 1.0F - (1.0F - progress) * (1.0F - progress);
   }

   private static float smoothStep(float progress) {
      float clamped = MathUtility.clamp(progress, 0.0F, 1.0F);
      return clamped * clamped * (3.0F - 2.0F * clamped);
   }

   private static Color getBetaSyncedColor(int offset) {
      Color base = HudEditor.getColor(0);
      Color dark = scaleColor(base, 0.5F);
      int angle = (int)((System.currentTimeMillis() / 10L + offset) % 360L);
      angle = angle >= 180 ? 360 - angle : angle;
      return lerpColor(base, dark, angle / 180.0F);
   }

   private static Color applyAlpha(Color color, float alphaProgress) {
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampAlpha(Math.round(color.getAlpha() * alphaProgress)));
   }

   private static Color scaleColor(Color color, float factor) {
      return new Color(
         Math.max(0, Math.min(255, Math.round(color.getRed() * factor))),
         Math.max(0, Math.min(255, Math.round(color.getGreen() * factor))),
         Math.max(0, Math.min(255, Math.round(color.getBlue() * factor))),
         color.getAlpha()
      );
   }

   private static Color lerpColor(Color start, Color end, float progress) {
      float clamped = MathUtility.clamp(progress, 0.0F, 1.0F);
      return new Color(
         Math.round(start.getRed() + (end.getRed() - start.getRed()) * clamped),
         Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * clamped),
         Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * clamped),
         Math.round(start.getAlpha() + (end.getAlpha() - start.getAlpha()) * clamped)
      );
   }

   private static int clampAlpha(int alpha) {
      return Math.max(0, Math.min(255, alpha));
   }

   private class Particle {
      private final Particles.ParticleTexture texture;
      private Vec3d position;
      private Vec3d velocity;
      private final boolean physics;
      private final int colorOffset;
      private final float rotationAngle;
      private final float size;
      private final long spawnTime;

      private Particle(Particles.ParticleTexture texture, Vec3d position, Vec3d velocity, boolean physics, int colorOffset, float size) {
         this.texture = texture;
         this.position = position;
         this.velocity = velocity.multiply(0.05);
         this.physics = physics;
         this.colorOffset = colorOffset;
         this.rotationAngle = Particles.randomSteppedAngle();
         this.size = size;
         this.spawnTime = System.currentTimeMillis();
      }

      private void update(double deltaTime) {
         this.updatePhysics(deltaTime);
         double deltaMultiplier = deltaTime * 60.0 * 0.2;
         this.position = this.position.add(this.velocity.x * deltaMultiplier, this.velocity.y * deltaMultiplier, this.velocity.z * deltaMultiplier);
      }

      private void updatePhysics(double deltaTime) {
         if (this.physics && Module.mc.world != null) {
            double velocitySq = this.velocity.lengthSquared();
            if (velocitySq > 1.0E-4) {
               if (this.posBlock(this.position.x, this.position.y, this.position.z + this.velocity.z)) {
                  this.velocity = new Vec3d(this.velocity.x * 1.35, this.velocity.y * 1.35, this.velocity.z * -1.1);
               }

               if (this.posBlock(this.position.x, this.position.y + this.velocity.y, this.position.z)) {
                  this.velocity = new Vec3d(this.velocity.x * 1.35, this.velocity.y * -1.1, this.velocity.z * 1.35);
               }

               if (this.posBlock(this.position.x + this.velocity.x, this.position.y, this.position.z)) {
                  this.velocity = new Vec3d(this.velocity.x * -1.1, this.velocity.y * 1.35, this.velocity.z * 1.35);
               }
            }

            double friction = Math.pow(0.999, deltaTime * 60.0);
            this.velocity = this.velocity.multiply(friction).subtract(0.0, 2.0E-5, 0.0);
         }
      }

      private void render(
         MatrixStack stack,
         BufferBuilder buffer,
         Camera camera,
         Particles.ParticleTexture texture,
         boolean syncColor,
         Color fixedColor,
         long fadeInMs,
         long fadeOutStartMs
      ) {
         long elapsed = System.currentTimeMillis() - this.spawnTime;
         float spawnProgress = this.getSpawnProgress(elapsed, fadeInMs);
         float alphaProgress = this.getAlphaProgress(elapsed, fadeInMs, fadeOutStartMs);
         if (!(alphaProgress <= 0.0F)) {
            Color baseColor = syncColor ? Particles.getBetaSyncedColor(this.colorOffset * 100) : fixedColor;
            if (baseColor == null) {
               baseColor = Color.WHITE;
            }

            Color renderColor = Particles.applyAlpha(baseColor, alphaProgress);
            Vec3d cameraPos = camera.getPos();
            float rotation = texture == Particles.ParticleTexture.Heart ? this.rotationAngle + 180.0F : this.rotationAngle;
            stack.push();
            stack.translate(this.position.x - cameraPos.x, this.position.y - cameraPos.y, this.position.z - cameraPos.z);
            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
            Matrix4f matrix = stack.peek().getPositionMatrix();
            float revealScale = 0.55F + 0.45F * Particles.smoothStep(spawnProgress);
            float renderSize = this.size * revealScale;
            Particles.putCenteredTexturedQuad(buffer, matrix, renderSize, renderColor.getRGB());
            float innerProgress = MathUtility.clamp((spawnProgress - 0.72F) / 0.27999997F, 0.0F, 1.0F);
            if (texture == Particles.ParticleTexture.Bloom && innerProgress > 0.0F) {
               Color innerColor = Particles.applyAlpha(baseColor, alphaProgress * Particles.smoothStep(innerProgress) * 0.55F);
               Particles.putCenteredTexturedQuad(buffer, matrix, renderSize * 0.5F, innerColor.getRGB());
            }

            stack.pop();
         }
      }

      private float getSpawnProgress(long elapsed, long fadeInMs) {
         return MathUtility.clamp((float)elapsed / (float)fadeInMs, 0.0F, 1.0F);
      }

      private float getAlphaProgress(long elapsed, long fadeInMs, long fadeOutStartMs) {
         if (elapsed < fadeInMs) {
            return Particles.smoothStep(this.getSpawnProgress(elapsed, fadeInMs));
         } else {
            long fadeOutElapsed = elapsed - fadeOutStartMs;
            if (fadeOutElapsed > 0L) {
               float progress = Particles.easeOutQuad(MathUtility.clamp((float)fadeOutElapsed / 400.0F, 0.0F, 1.0F));
               return MathUtility.clamp(1.0F - progress, 0.0F, 1.0F);
            } else {
               return 1.0F;
            }
         }
      }

      private boolean isExpired(long now, long lifespanMs) {
         return now - this.spawnTime >= lifespanMs;
      }

      private boolean posBlock(double x, double y, double z) {
         if (Module.mc.world == null) {
            return false;
         }

         Block block = Module.mc.world.getBlockState(BlockPos.ofFloored(x, y, z)).getBlock();
         return !(block instanceof AirBlock) && block != Blocks.WATER && block != Blocks.LAVA;
      }
   }

   private enum ParticleMode {
      Bloom(Particles.ParticleTexture.Bloom),
      Star(Particles.ParticleTexture.Star),
      Snow(Particles.ParticleTexture.Snow),
      Heart(Particles.ParticleTexture.Heart),
      Genshin(Particles.ParticleTexture.Genshin),
      Rhombus(Particles.ParticleTexture.Rhombus);

      private final Particles.ParticleTexture texture;

      ParticleMode(Particles.ParticleTexture texture) {
         this.texture = texture;
      }
   }

   private enum ParticleTexture {
      Bloom(TextureStorage.particleBloom),
      Star(TextureStorage.particleStar),
      Snow(TextureStorage.particleSnowflake),
      Heart(TextureStorage.particleHeart),
      Genshin(TextureStorage.particleGenshin),
      Rhombus(TextureStorage.particleRhombus);

      private final Identifier identifier;

      ParticleTexture(Identifier identifier) {
         this.identifier = identifier;
      }
   }
}
