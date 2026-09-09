package vcore.features.modules.render.popeffect;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class PopEffectParticles {
   public static final SimpleParticleType REVIVE = FabricParticleTypes.simple();
   public static final SimpleParticleType REVIVE_SPARK = FabricParticleTypes.simple();

   private PopEffectParticles() {
   }

   public static void register() {
      Registry.register(Registries.PARTICLE_TYPE, Identifier.of("vcore", "revive"), REVIVE);
      Registry.register(Registries.PARTICLE_TYPE, Identifier.of("vcore", "revive_spark"), REVIVE_SPARK);
      ParticleFactoryRegistry.getInstance().register(REVIVE, PopEffectReviveParticle.Factory::new);
      ParticleFactoryRegistry.getInstance().register(REVIVE_SPARK, PopEffectReviveSparkParticle.Factory::new);
   }
}
