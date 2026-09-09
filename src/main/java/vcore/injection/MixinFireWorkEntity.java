package vcore.injection;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vcore.Vcore;
import vcore.core.Managers;
import vcore.core.manager.IManager;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventFireworkMotion;
import vcore.features.modules.combat.Aura;

@Mixin(FireworkRocketEntity.class)
public class MixinFireWorkEntity {
   @Shadow
   private LivingEntity shooter;

   private Vec3d getRotationVectorForBoost(LivingEntity entity) {
      if (IManager.mc.player != null
         && entity == IManager.mc.player
         && IManager.mc.player.isFallFlying()
         && ModuleManager.aura.isEnabled()
         && Aura.target != null) {
         boolean shouldUseAuraRotation = ModuleManager.aura.rotationMode.not(Aura.Mode.None);
         if (Aura.target instanceof LivingEntity livingTarget && ModuleManager.elytraTarget.shouldTarget(livingTarget)) {
            shouldUseAuraRotation = true;
         }

         return shouldUseAuraRotation
            ? Managers.PLAYER.getRotationVector(ModuleManager.aura.rotationPitch, ModuleManager.aura.rotationYaw)
            : entity.getRotationVector();
      } else {
         return entity.getRotationVector();
      }
   }

   @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
   private Vec3d tickHook(LivingEntity instance) {
      return this.getRotationVectorForBoost(instance);
   }

   @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
   private void tickSetVelocityHook(LivingEntity entity, Vec3d velocity) {
      if (entity != null) {
         if (!entity.isFallFlying()) {
            entity.setVelocity(velocity);
         } else {
            Vec3d direction = this.getRotationVectorForBoost(entity);
            Vec3d motion = entity.getVelocity();
            Vec3d multiplier = new Vec3d(1.5, 1.5, 1.5);
            if (entity == IManager.mc.player) {
               EventFireworkMotion event = new EventFireworkMotion(entity, (FireworkRocketEntity)(Object)this, new Vec3d(1.6, 1.6, 1.6));
               Vcore.EVENT_BUS.post(event);
               if (event.isCancelled()) {
                  multiplier = event.getVector();
                  if (multiplier.equals(Vec3d.ZERO)) {
                     return;
                  }
               }
            }

            Vec3d newMotion = motion.add(
               direction.x * 0.1 + (direction.x * multiplier.x - motion.x) * 0.5,
               direction.y * 0.1 + (direction.y * multiplier.y - motion.y) * 0.5,
               direction.z * 0.1 + (direction.z * multiplier.z - motion.z) * 0.5
            );
            entity.setVelocity(newMotion);
         }
      }
   }
}
