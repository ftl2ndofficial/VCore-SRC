package vcore.features.modules.render.popeffect;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vcore.core.manager.client.ModuleManager;

@Environment(EnvType.CLIENT)
public final class PopEffectReviveSparkParticle extends SpriteBillboardParticle {
   private LivingEntity target;
   private Vec3d targetDirection;

   private PopEffectReviveSparkParticle(
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
      this.maxAge = this.random.nextBetween(30, 48);
      this.alpha = getEffectOpacity();
      this.scale = 0.1F;
      this.velocityX = this.random.nextBetween(-10, 10) / 25.0F;
      this.velocityY = this.random.nextBetween(-10, 10) / 25.0F;
      this.velocityZ = this.random.nextBetween(-10, 10) / 25.0F;
      this.velocityMultiplier = 1.0F;
      this.collidesWithWorld = true;
      this.setSpriteForAge(spriteProvider);
      if (clientWorld.getEntityById((int)velocityX) instanceof LivingEntity livingEntity) {
         this.target = livingEntity;
      }

      if (this.random.nextBoolean()) {
         this.setColor(0.0F, 1.0F, 0.0F);
      } else {
         this.setColor(1.0F, 1.0F, 0.0F);
      }
   }

   public void tick() {
      super.tick();
      if (this.target != null) {
         this.targetDirection = new Vec3d(
               this.target.getX() - this.x,
               this.target.getY() + this.target.getDimensions(this.target.getPose()).height() / 2.0F - this.y,
               this.target.getZ() - this.z
            )
            .normalize();
      }

      if (this.age >= this.maxAge / 5.0F && this.age < this.maxAge / 4.0F) {
         this.velocityMultiplier = 0.0F;
      }

      if (this.target != null && this.age >= this.maxAge / 4.0F && this.target.getPos().distanceTo(new Vec3d(this.x, this.y, this.z)) < 20.0) {
         this.alpha = MathHelper.clamp(1.0F - (this.age - this.maxAge / 1.5F) / 20.0F, 0.0F, 1.0F) * getEffectOpacity();
         this.velocityMultiplier = 1.0F;
         this.velocityX = this.targetDirection.x * ((this.age - 15) * 0.05F);
         this.velocityY = this.targetDirection.y * ((this.age - 15) * 0.05F);
         this.velocityZ = this.targetDirection.z * ((this.age - 15) * 0.05F);
         if (this.target.getPos().distanceTo(new Vec3d(this.x, this.y, this.z)) < 2.0) {
            this.alpha = 0.0F;
            this.dead = true;
         }
      }
   }

   @NotNull
   public ParticleTextureSheet getType() {
      return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
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
         return new PopEffectReviveSparkParticle(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
