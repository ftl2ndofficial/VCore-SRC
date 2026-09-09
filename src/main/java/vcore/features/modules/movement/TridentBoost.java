package vcore.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.MovementType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vcore.events.impl.UseTridentEvent;
import vcore.features.modules.Module;
import vcore.setting.Setting;

public class TridentBoost extends Module {
   private final Setting<TridentBoost.Mode> mode = new Setting<>("Mode", TridentBoost.Mode.Motion);
   private final Setting<Float> factor = new Setting<>("Factor", 1.0F, 0.1F, 20.0F);
   public final Setting<Integer> cooldown = new Setting<>("Cooldown", 10, 0, 20);
   public final Setting<Boolean> anyWeather = new Setting<>("AnyWeather", true);

   public TridentBoost() {
      super("TridentBoost", "Boost with trident.", Module.Category.MOVEMENT);
   }

   @EventHandler
   public void onUseTrident(UseTridentEvent e) {
      if (mc.player.getItemUseTime() >= this.cooldown.getValue()) {
         float j = EnchantmentHelper.getTridentSpinAttackStrength(mc.player.getActiveItem(), mc.player);
         if ((this.anyWeather.getValue() || mc.player.isTouchingWaterOrRain()) && j > 0.0F) {
            float f = mc.player.getYaw();
            float g = mc.player.getPitch();
            float speedX = -MathHelper.sin(f * (float) (Math.PI / 180.0)) * MathHelper.cos(g * (float) (Math.PI / 180.0));
            float speedY = -MathHelper.sin(g * (float) (Math.PI / 180.0));
            float speedZ = MathHelper.cos(f * (float) (Math.PI / 180.0)) * MathHelper.cos(g * (float) (Math.PI / 180.0));
            float plannedSpeed = MathHelper.sqrt(speedX * speedX + speedY * speedY + speedZ * speedZ);
            float n = this.mode.is(TridentBoost.Mode.Factor) ? this.factor.getValue() * 3.0F * ((1.0F + j) / 4.0F) : this.factor.getValue();
            speedX *= n / plannedSpeed;
            speedY *= n / plannedSpeed;
            speedZ *= n / plannedSpeed;
            mc.player.addVelocity(speedX, speedY, speedZ);
            mc.player.useRiptide(20, 8.0F, mc.player.getActiveItem());
            if (mc.player.isOnGround()) {
               mc.player.move(MovementType.SELF, new Vec3d(0.0, 1.1999999F, 0.0));
            }

            RegistryEntry<SoundEvent> registryEntry = EnchantmentHelper.getEffect(mc.player.getActiveItem(), EnchantmentEffectComponentTypes.TRIDENT_SOUND)
               .orElse(SoundEvents.ITEM_TRIDENT_THROW);
            mc.world.playSoundFromEntity(null, mc.player, (SoundEvent)registryEntry.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
         }
      }

      e.cancel();
   }

   private enum Mode {
      Motion,
      Factor;
   }
}
