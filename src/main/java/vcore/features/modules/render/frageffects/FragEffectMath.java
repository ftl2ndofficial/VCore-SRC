package vcore.features.modules.render.frageffects;

public final class FragEffectMath {
   private FragEffectMath() {
   }

   public static float clamp(float value, float min, float max) {
      return Math.min(max, Math.max(value, min));
   }

   public static float lerp(float a, float b, float progress) {
      return a + progress * (b - a);
   }

   public static int lerp(int a, int b, float progress) {
      return a + (int)(progress * (b - a));
   }

   public static double easeQuintOut(double value) {
      return 1.0 - Math.pow(1.0 - value, 5.0);
   }

   public static double easeQuartInOut(double value) {
      return value < 0.5 ? 8.0 * Math.pow(value, 4.0) : 1.0 - Math.pow(-2.0 * value + 2.0, 4.0) / 2.0;
   }

   public static double easeExpoIn(double value) {
      return value != 0.0 ? Math.pow(2.0, 10.0 * value - 10.0) : value;
   }

   public static double easeExpoInOut(double value) {
      if (value != 0.0 && value != 1.0) {
         return value < 0.5 ? Math.pow(2.0, 20.0 * value - 10.0) / 2.0 : (2.0 - Math.pow(2.0, -20.0 * value + 10.0)) / 2.0;
      } else {
         return value;
      }
   }

   public static double easeBackInOut(double value) {
      return value < 0.5
         ? Math.pow(2.0 * value, 2.0) * (7.189819 * value - 2.5949095) / 2.0
         : (Math.pow(2.0 * value - 2.0, 2.0) * (3.5949095 * (value * 2.0 - 2.0) + 2.5949095) + 2.0) / 2.0;
   }

   public static int getColor(int red, int green, int blue, int alpha) {
      return clampColor(alpha) << 24 | clampColor(red) << 16 | clampColor(green) << 8 | clampColor(blue);
   }

   public static int multAlpha(int color, float percent01) {
      return getColor(red(color), green(color), blue(color), Math.round(alpha(color) * percent01));
   }

   public static int multDark(int color, float brightness) {
      return getColor(Math.round(red(color) * brightness), Math.round(green(color) * brightness), Math.round(blue(color) * brightness), alpha(color));
   }

   public static int getOverallColorFrom(int color1, int color2, float percentTo2) {
      return getColor(
         lerp(red(color1), red(color2), percentTo2),
         lerp(green(color1), green(color2), percentTo2),
         lerp(blue(color1), blue(color2), percentTo2),
         lerp(alpha(color1), alpha(color2), percentTo2)
      );
   }

   public static float[] getRGBAf(int color) {
      return new float[]{red(color) / 255.0F, green(color) / 255.0F, blue(color) / 255.0F, alpha(color) / 255.0F};
   }

   private static int red(int color) {
      return color >> 16 & 0xFF;
   }

   private static int green(int color) {
      return color >> 8 & 0xFF;
   }

   private static int blue(int color) {
      return color & 0xFF;
   }

   private static int alpha(int color) {
      return color >> 24 & 0xFF;
   }

   private static int clampColor(int value) {
      return Math.min(255, Math.max(0, value));
   }
}
