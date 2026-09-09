package vcore.features.modules.combat.aura.rotation;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vcore.core.manager.client.ModuleManager;
import vcore.features.modules.combat.Aura;
import vcore.utility.math.MathUtility;

public class ElytraRotation implements RotationModeHandler {
   @Override
   public void rotate(Aura aura, boolean ready) {
      if (Aura.target instanceof LivingEntity livingTarget && Aura.mc.player != null) {
         float[] rotations = this.getRotations(aura, livingTarget);
         if (rotations != null) {
            float[] correctedRotation = this.correctRotation(aura, this.randomRotate(rotations));
            if (!Float.isNaN(correctedRotation[0]) && !Float.isNaN(correctedRotation[1])) {
               aura.rotationYaw = correctedRotation[0];
               aura.rotationPitch = correctedRotation[1];
            }

            ModuleManager.moveFix.fixRotation = aura.rotationYaw;
            aura.lookingAtHitbox = aura.canAttackElytraTarget(livingTarget);
         }
      }
   }

   private float[] getRotations(Aura aura, LivingEntity entity) {
      Vec3d playerPos = Aura.mc.player.getEyePos();
      Vec3d entityPos = ModuleManager.elytraTarget.getTargetVec(entity, this.shouldApplyPrediction());
      return entityPos == null ? null : this.calculateDiff(playerPos, entityPos);
   }

   private boolean shouldApplyPrediction() {
      return ModuleManager.elytraTarget.shouldAimAtPrediction();
   }

   private float[] calculateDiff(Vec3d from, Vec3d to) {
      Vec3d diff = to.subtract(from);
      double distanceXZ = Math.hypot(diff.x, diff.z);
      double yawRad = Math.atan2(diff.z, diff.x);
      float yaw = (float)Math.toDegrees(yawRad) - 90.0F;
      double pitchRad = Math.atan2(diff.y, distanceXZ);
      float pitch = (float)(-Math.toDegrees(pitchRad));
      return new float[]{yaw, pitch};
   }

   private float[] randomRotate(float[] currentRotation) {
      return new float[]{currentRotation[0] + MathUtility.random(-1.0F, 1.0F), currentRotation[1] + MathUtility.random(-1.0F, 1.0F)};
   }

   private float[] correctRotation(Aura aura, float[] rotationVector) {
      float yaw = MathHelper.wrapDegrees(rotationVector[0]);
      float pitch = MathHelper.clamp(rotationVector[1], -90.0F, 90.0F);
      float gcd = this.getGcd(aura);
      return new float[]{yaw - yaw % gcd, pitch - pitch % gcd};
   }

   private float getGcd(Aura aura) {
      double sensitivity = (Double)Aura.mc.options.getMouseSensitivity().getValue();
      double value = sensitivity * 0.6 + 0.2;
      return (float)(Math.pow(value, 1.5) * 0.8 * 0.15);
   }
}
