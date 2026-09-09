package vcore.injection;

import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vcore.core.manager.client.ModuleManager;

@Mixin(FlowableFluid.class)
public abstract class MixinFlowableFluid {
   @Shadow
   protected abstract boolean isFlowBlocked(BlockView var1, BlockPos var2, Direction var3);

   @Inject(method = "getVelocity", at = @At("HEAD"), cancellable = true)
   private void getVelocityHook(BlockView world, BlockPos pos, FluidState state, CallbackInfoReturnable<Vec3d> cir) {
      if (ModuleManager.noPush.isEnabled() && ModuleManager.noPush.water.getValue()) {
         double d = 0.0;
         double e = 0.0;
         Mutable mutable = new Mutable();
         Vec3d vec3d = new Vec3d(d, 0.0, e);
         if ((Boolean)state.get(FlowableFluid.FALLING)) {
            for (Direction direction2 : Type.HORIZONTAL) {
               mutable.set(pos, direction2);
               if (this.isFlowBlocked(world, mutable, direction2) || this.isFlowBlocked(world, mutable.up(), direction2)) {
                  vec3d = vec3d.normalize().add(0.0, -6.0, 0.0);
                  break;
               }
            }
         }

         cir.setReturnValue(vec3d.normalize());
      }
   }
}
