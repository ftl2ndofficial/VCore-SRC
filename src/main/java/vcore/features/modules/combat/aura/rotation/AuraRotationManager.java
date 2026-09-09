package vcore.features.modules.combat.aura.rotation;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vcore.core.Managers;
import vcore.core.manager.client.ModuleManager;
import vcore.core.manager.player.PlayerManager;
import vcore.features.modules.combat.Aura;
import vcore.utility.player.PlayerUtility;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.Render3DEngine;

public final class AuraRotationManager {
   private final Aura aura;
   private final RotationModeHandler trackRotation = new Track();
   private final RotationModeHandler snapRotation = new Snap();
   private final RotationModeHandler grimRotation = new Grim();
   private final RotationModeHandler noneRotation = new None();
   private final ElytraRotation elytraRotation = new ElytraRotation();

   public AuraRotationManager(Aura aura) {
      this.aura = aura;
   }

   private RotationModeHandler getModeHandler() {
      if (this.shouldUseElytraRotation()) {
         return this.elytraRotation;
      }

      return switch ((Aura.Mode)this.aura.rotationMode.getValue()) {
         case Track -> this.trackRotation;
         case Grim -> this.grimRotation;
         case Snap -> this.snapRotation;
         case None -> this.noneRotation;
      };
   }

   public void rotate(boolean ready) {
      float previousYaw = this.aura.rotationYaw;
      float previousPitch = this.aura.rotationPitch;
      if (this.shouldUseElytraRotation()) {
         this.elytraRotation.rotate(this.aura, ready);
      } else {
         this.getModeHandler().rotate(this.aura, ready);
      }

      this.aura.captureRotationState(previousYaw, previousPitch);
   }

   public boolean skipRayTraceCheck() {
      return this.aura.rotationMode.getValue() == Aura.Mode.None
         || !this.aura.rayTrace.getValue()
         || this.shouldBypassRayTraceForElytraDuel()
         || this.aura.rotationMode.is(Aura.Mode.Snap)
            && (
               this.aura.snapTicks.getValue() <= 1
                  || Aura.mc
                     .world
                     .getBlockCollisions(Aura.mc.player, Aura.mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, 1.0, 0.0))
                     .iterator()
                     .hasNext()
            );
   }

   private boolean shouldBypassRayTraceForElytraDuel() {
      return Aura.mc.player != null && Aura.mc.player.isFallFlying() && Aura.target instanceof LivingEntity livingTarget && livingTarget.isFallFlying();
   }

   public void onRender3D() {
      if (this.aura.clientLook.getValue() && (this.aura.rotationMode.getValue() != Aura.Mode.None || this.shouldUseElytraRotation())) {
         Aura.mc.player.setYaw((float)Render2DEngine.interpolate(Aura.mc.player.prevYaw, this.aura.getRenderRotationYaw(), Render3DEngine.getTickDelta()));
         Aura.mc
            .player
            .setPitch((float)Render2DEngine.interpolate(Aura.mc.player.prevPitch, this.aura.getRenderRotationPitch(), Render3DEngine.getTickDelta()));
      }
   }

   public boolean canAttackElytraTarget(LivingEntity livingTarget) {
      if (Aura.mc.player == null || livingTarget == null) {
         return false;
      }

      if (!ModuleManager.elytraTarget.shouldTarget(livingTarget)) {
         Vec3d eyePos = Aura.mc.player.getEyePos();
         double rangeSq = this.aura.getRange() * this.aura.getRange();
         return this.isBoxInAttackRange(eyePos, livingTarget.getBoundingBox(), rangeSq);
      }

      Vec3d targetVec = this.getElytraAttackVec(livingTarget);
      if (targetVec == null) {
         return false;
      }

      double maxDistance = ModuleManager.elytraTarget.getPrediction(livingTarget) - 0.5;
      return Aura.mc.player.getPos().distanceTo(targetVec) <= maxDistance || Aura.mc.player.getBoundingBox().contains(targetVec);
   }

   private boolean shouldUseElytraRotation() {
      return Aura.target instanceof LivingEntity livingTarget ? ModuleManager.elytraTarget.shouldUseElytraTargetRotation(livingTarget) : false;
   }

   private Vec3d getElytraAttackVec(LivingEntity livingTarget) {
      return ModuleManager.elytraTarget.getTargetVec(livingTarget, true);
   }

   private boolean isBoxInAttackRange(Vec3d eyePos, Box box, double rangeSq) {
      return eyePos.squaredDistanceTo(this.getClosestPoint(eyePos, box)) <= rangeSq;
   }

   private Vec3d getClosestPoint(Vec3d eyePos, Box box) {
      return new Vec3d(
         MathHelper.clamp(eyePos.x, box.minX, box.maxX), MathHelper.clamp(eyePos.y, box.minY, box.maxY), MathHelper.clamp(eyePos.z, box.minZ, box.maxZ)
      );
   }

   public boolean isWallsBypassYawOffset() {
      return ModuleManager.wallsBypass.isEnabled() && ModuleManager.wallsBypass.isYawOffset();
   }

   public boolean isWallsBypassPeekHigh() {
      return ModuleManager.wallsBypass.isEnabled() && ModuleManager.wallsBypass.isPeekHigh();
   }

   public float getSquaredRotateDistance() {
      float dst = this.aura.getRange();
      boolean needsAimRange = this.aura.rotationMode.getValue() == Aura.Mode.Track
         || this.aura.rotationMode.getValue() == Aura.Mode.Grim
         || this.shouldUseElytraSearchRange();
      if (needsAimRange) {
         dst += this.aura.getAimRange();
      }

      if ((Aura.mc.player.isFallFlying() || ModuleManager.elytraPlus.isEnabled()) && Aura.target != null) {
         dst += 4.0F;
      }

      if (!needsAimRange) {
         dst = this.aura.getRange();
      }

      return dst * dst;
   }

   public boolean isInRange(Entity target) {
      if (target instanceof LivingEntity livingTarget && ModuleManager.elytraTarget.shouldTarget(livingTarget)) {
         Vec3d targetVec = ModuleManager.elytraTarget.getTargetVec(livingTarget, true);
         return targetVec != null && PlayerUtility.squaredDistanceFromEyes(targetVec) <= this.getSquaredRotateDistance();
      } else {
         float sqRotate = this.getSquaredRotateDistance();
         Box bb = target.getBoundingBox();
         Vec3d eye = Aura.mc.player.getEyePos();
         double cx = MathHelper.clamp(eye.x, bb.minX, bb.maxX);
         double cy = MathHelper.clamp(eye.y, bb.minY, bb.maxY);
         double cz = MathHelper.clamp(eye.z, bb.minZ, bb.maxZ);
         double dx = cx - eye.x;
         double dy = cy - eye.y;
         double dz = cz - eye.z;
         if (dx * dx + dy * dy + dz * dz > sqRotate) {
            return false;
         }

         if (PlayerUtility.squaredDistanceFromEyes(target.getPos().add(0.0, target.getEyeHeight(target.getPose()), 0.0)) > sqRotate + 4.0F) {
            return false;
         }

         float halfBox = (float)(bb.getLengthX() / 2.0);
         float lenY = (float)bb.getLengthY();
         double tx = target.getX();
         double ty = target.getY();
         double tz = target.getZ();
         float rangeDist = (float)Math.sqrt(sqRotate);
         float wallRange = this.aura.getWallRange();
         boolean useRayTrace = this.aura.rayTrace.getValue();

         for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.15F) {
            for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.15F) {
               for (float y1 = 0.05F; y1 <= lenY; y1 += 0.25F) {
                  Vec3d point = new Vec3d(tx + x1, ty + y1, tz + z1);
                  if (!(PlayerUtility.squaredDistanceFromEyes(point) > sqRotate)) {
                     float[] rotation = PlayerManager.calcAngle(point);
                     if (Managers.PLAYER.checkRtx(rotation[0], rotation[1], rangeDist, wallRange, useRayTrace)) {
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   private boolean shouldUseElytraSearchRange() {
      return Aura.mc.player != null && Aura.mc.player.isFallFlying() && ModuleManager.elytraTarget.isEnabled();
   }
}
