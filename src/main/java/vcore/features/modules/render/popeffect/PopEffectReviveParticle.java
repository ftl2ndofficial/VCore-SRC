package vcore.features.modules.render.popeffect;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vcore.core.manager.client.ModuleManager;

@Environment(EnvType.CLIENT)
public final class PopEffectReviveParticle extends SpriteBillboardParticle {
   private final SpriteProvider spriteProvider;
   private final double scaler;
   private final double rotX;
   private final double rotZ;
   private double rotY;
   private LivingEntity target;
   private Quaternionf quaternion = new Quaternionf(0.0F, -0.7F, 0.7F, 0.0F);

   private PopEffectReviveParticle(
      @NotNull ClientWorld clientWorld,
      double x,
      double y,
      double z,
      @NotNull SpriteProvider spriteProvider,
      double velocityX,
      double velocityY,
      double velocityZ
   ) {
      super(clientWorld, x, y, z, velocityX, velocityY, velocityZ);
      this.maxAge = 20;
      this.alpha = 0.0F;
      this.scale = 0.2F;
      this.scaler = velocityX;
      this.velocityMultiplier = 0.0F;
      this.rotX = this.random.nextBetween(-180, 180);
      this.rotY = this.random.nextBetween(-180, 180);
      this.rotZ = this.random.nextBetween(-180, 180);
      if (clientWorld.getEntityById((int)velocityY) instanceof LivingEntity livingEntity) {
         this.target = livingEntity;
      }

      if (this.random.nextBoolean()) {
         this.setColor(1.0F, 1.0F, 0.0F);
      } else {
         this.setColor(0.0F, 1.0F, 0.0F);
      }

      this.spriteProvider = spriteProvider;
      this.setSprite(this.spriteProvider.getSprite(this.age, this.maxAge));
   }

   public void buildGeometry(@NotNull VertexConsumer vertexConsumer, @NotNull Camera camera, float tickDelta) {
      Vec3d cameraPos = camera.getPos();
      float x = (float)(MathHelper.lerp(tickDelta, this.prevPosX, this.x) - cameraPos.x);
      float y = (float)(MathHelper.lerp(tickDelta, this.prevPosY, this.y) - cameraPos.y);
      float z = (float)(MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - cameraPos.z);
      Vector3f[] vertices = new Vector3f[]{
         new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)
      };
      Vector3f[] bottomVertices = new Vector3f[]{
         new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, -1.0F, 0.0F)
      };
      float size = this.getSize(tickDelta);

      for (int i = 0; i < 4; i++) {
         Vector3f vertex = vertices[i];
         vertex.rotate(this.quaternion);
         vertex.mul(size);
         vertex.add(x, y, z);
         Vector3f bottomVertex = bottomVertices[i];
         bottomVertex.rotate(this.quaternion);
         bottomVertex.mul(size);
         bottomVertex.add(x, y - 0.1F, z);
      }

      float minU = this.getMinU();
      float maxU = this.getMaxU();
      float minV = this.getMinV();
      float maxV = this.getMaxV();
      int light = this.getBrightness(tickDelta);
      this.putVertex(vertexConsumer, vertices[0], maxU, maxV, light);
      this.putVertex(vertexConsumer, vertices[1], maxU, minV, light);
      this.putVertex(vertexConsumer, vertices[2], minU, minV, light);
      this.putVertex(vertexConsumer, vertices[3], minU, maxV, light);
      this.putVertex(vertexConsumer, vertices[3], minU, maxV, light);
      this.putVertex(vertexConsumer, vertices[2], minU, minV, light);
      this.putVertex(vertexConsumer, vertices[1], maxU, minV, light);
      this.putVertex(vertexConsumer, vertices[0], maxU, maxV, light);
   }

   public void tick() {
      super.tick();
      this.setSprite(this.spriteProvider.getSprite(this.age, this.maxAge));
      if (this.target != null) {
         this.setPos(this.target.getX(), this.target.getY() + this.target.getDimensions(this.target.getPose()).height() / 2.0F, this.target.getZ());
      }

      float opacity = getEffectOpacity();
      if (!(opacity <= 0.0F)) {
         this.rotY += 20.0;
         this.quaternion = euler(0.0F, 0.0F, (float)this.rotY);
         this.quaternion = euler((float)this.rotZ, (float)this.rotX, (float)(-this.rotZ)).mul(this.quaternion);
         this.scale = this.alpha / opacity * (float)this.scaler;
         this.alpha = MathHelper.clamp((float)Math.sqrt(Math.sin((double)this.age / this.maxAge * Math.PI)) / 1.2F, 0.0F, 1.0F) * opacity;
      }
   }

   @NotNull
   public ParticleTextureSheet getType() {
      return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
   }

   private void putVertex(@NotNull VertexConsumer buffer, @NotNull Vector3f vertex, float u, float v, int light) {
      buffer.vertex(vertex.x(), vertex.y(), vertex.z()).texture(u, v).color(this.red, this.green, this.blue, this.alpha).light(light);
   }

   @NotNull
   private static Quaternionf euler(float x, float y, float z) {
      Quaternionf qx = new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), x);
      Quaternionf qy = new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), y);
      Quaternionf qz = new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 0.0F, 1.0F), z);
      return qx.mul(qy).mul(qz);
   }

   private static float getEffectOpacity() {
      return ModuleManager.popEffect.getEffectOpacity();
   }

   @Environment(EnvType.CLIENT)
   public static final class Factory implements ParticleFactory<SimpleParticleType> {
      private final SpriteProvider spriteProvider;

      public Factory(@NotNull SpriteProvider spriteProvider) {
         this.spriteProvider = spriteProvider;
      }

      @Nullable
      public Particle createParticle(
         SimpleParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ
      ) {
         return new PopEffectReviveParticle(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
