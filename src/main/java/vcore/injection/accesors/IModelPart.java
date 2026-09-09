package vcore.injection.accesors;

import java.util.List;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPart.Cuboid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface IModelPart {
   @Accessor("cuboids")
   List<Cuboid> vcore$getCuboids();
}
