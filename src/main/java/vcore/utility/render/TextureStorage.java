package vcore.utility.render;

import net.minecraft.util.Identifier;

public final class TextureStorage {
   public static final Identifier star = id("textures/effects/particle_star.png");
   public static final Identifier particleBloom = id("textures/effects/particle_firefly.png");
   public static final Identifier particleStar = star;
   public static final Identifier particleSnowflake = id("textures/effects/particle_snowflake.png");
   public static final Identifier particleHeart = id("textures/effects/particle_heart.png");
   public static final Identifier particleGenshin = id("textures/effects/particle_genshin.png");
   public static final Identifier particleRhombus = id("textures/effects/particle_rhombus.png");
   public static final Identifier firefly = id("textures/effects/firefly.png");
   public static final Identifier arrow = id("textures/effects/triangle.png");
   public static final Identifier bubble = id("textures/effects/hit_bubble.png");
   public static final Identifier defaultCircle = id("textures/effects/circle.png");
   public static final Identifier container = id("textures/ui/container.png");
   public static final Identifier guiArrow = id("textures/ui/arrow.png");
   public static final Identifier setting = id("textures/ui/settings.png");
   public static final Identifier brokenShield = id("textures/ui/broken_shield.png");
   public static final Identifier miniLogo = id("textures/ui/mini_logo.png");
   public static final Identifier playerIcon = id("textures/ui/player.png");
   public static final Identifier speedometerIcon = id("textures/ui/speedometer.png");
   public static final Identifier lagIcon = id("textures/ui/lag.png");
   public static final Identifier fpsIcon = id("textures/ui/fps.png");
   public static final Identifier pingIcon = id("textures/ui/ping.png");
   public static final Identifier tpsIcon = id("textures/ui/tps.png");
   public static final Identifier coordsIcon = id("textures/ui/coords.png");
   public static final Identifier themeHudIcon = id("textures/ui/theme_hud.png");
   public static final Identifier starCape = id("textures/cosmetics/cape.png");
   public static final Identifier dashBloom = id("textures/effects/dashbloom.png");
   public static final Identifier dashBloomSample = id("textures/effects/dashbloomsample.png");
   public static final Identifier glow = id("textures/effects/glow.png");
   public static final Identifier targetImage = id("textures/effects/target.png");

   private TextureStorage() {
   }

   private static Identifier id(String path) {
      return Identifier.of("vcore", path);
   }
}
