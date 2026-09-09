package vcore.injection;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import vcore.core.manager.client.ModuleManager;
import vcore.features.modules.Module;
import vcore.features.modules.combat.Aura;
import vcore.features.modules.misc.FakePlayer;
import vcore.utility.interfaces.IEntityLiving;
import vcore.utility.interfaces.IOtherClientPlayerEntity;

@Mixin(OtherClientPlayerEntity.class)
public class MixinOtherClientPlayerEntity extends AbstractClientPlayerEntity implements IOtherClientPlayerEntity {
   @Unique
   private double backUpX;
   @Unique
   private double backUpY;
   @Unique
   private double backUpZ;

   public MixinOtherClientPlayerEntity(ClientWorld world, GameProfile profile) {
      super(world, profile);
   }

   @Override
   public void resolve(Aura.Resolver mode) {
      if ((Object)this == FakePlayer.fakePlayer) {
         this.backUpY = -999.0;
      } else {
         this.backUpX = this.getX();
         this.backUpY = this.getY();
         this.backUpZ = this.getZ();
         if (mode == Aura.Resolver.BackTrack) {
            double minDst = 999.0;
            Aura.Position bestPos = null;

            for (Aura.Position p : ((IEntityLiving)(Object)this).getPositionHistory()) {
               double dst = Module.mc.player.squaredDistanceTo(p.getX(), p.getY(), p.getZ());
               if (dst < minDst) {
                  minDst = dst;
                  bestPos = p;
               }
            }

            if (bestPos != null) {
               this.setPosition(bestPos.getX(), bestPos.getY(), bestPos.getZ());
               if ((Object)Aura.target == (Object)this) {
                  ModuleManager.aura.resolvedBox = this.getBoundingBox();
               }
            }
         } else {
            Vec3d from = new Vec3d(((IEntityLiving)(Object)this).getPrevServerX(), ((IEntityLiving)(Object)this).getPrevServerY(), ((IEntityLiving)(Object)this).getPrevServerZ());
            Vec3d to = new Vec3d(this.serverX, this.serverY, this.serverZ);
            if (mode == Aura.Resolver.Advantage) {
               if (Module.mc.player.squaredDistanceTo(from) > Module.mc.player.squaredDistanceTo(to)) {
                  this.setPosition(to.x, to.y, to.z);
               } else {
                  this.setPosition(from.x, from.y, from.z);
               }
            }

            if ((Object)Aura.target == (Object)this) {
               ModuleManager.aura.resolvedBox = this.getBoundingBox();
            }
         }
      }
   }

   @Override
   public void releaseResolver() {
      if (this.backUpY != -999.0) {
         this.setPosition(this.backUpX, this.backUpY, this.backUpZ);
         this.backUpY = -999.0;
      }
   }
}
