package vcore.features.modules.render;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vcore.Vcore;
import vcore.events.impl.EventAttack;
import vcore.events.impl.EventDeath;
import vcore.features.modules.Module;
import vcore.features.modules.combat.Aura;
import vcore.features.modules.render.frageffects.FragCharmSfx;
import vcore.features.modules.render.frageffects.FragDeathMemory;
import vcore.features.modules.render.frageffects.FragEffectMath;
import vcore.features.modules.render.frageffects.FragParticleRenderer;
import vcore.utility.Timer;
import vcore.utility.render.Render2DEngine;

public final class FragEffects extends Module {
   private static final Identifier VIGNETTE_TEXTURE = Identifier.of("vcore", "textures/effects/lightarroundscreen.png");
   private static final long EFFECT_MS_MAX = 2800L;
   private static final int COLOR_5 = -65536;
   private static final int COLOR_6 = -1;
   private final FragCharmSfx charmSfx;
   private final FragDeathMemory deathMemory;
   private final FragParticleRenderer particles;
   private final Timer linearTimer = new Timer();
   private float[] timerValues = new float[0];
   private boolean animate;
   private int ticksForEditTimer;

   public FragEffects() {
      super("FragEffects", "Relake frag kill feedback effects.", Module.Category.RENDER);
      this.charmSfx = FragCharmSfx.create();
      this.deathMemory = FragDeathMemory.create(950L, this::initWorldEffectOnUpdate, this::shouldRotateToKilled);
      this.particles = FragParticleRenderer.create();
      this.refillTimerValues(0.125F, 0.175F, 0.3F, 0.65F, 0.8F);
   }

   @Override
   public void onDisable() {
      this.clearRuntimeState();
   }

   @Override
   public void onLogout() {
      this.clearRuntimeState();
   }

   @Override
   public void onUpdate() {
      if (mc.player != null && mc.world != null) {
         this.updateTickTimerValues();
         this.updateTargetMemories();
         this.deathMemory.updateAutoMemories();
         this.deathMemory.removeAutoMemories();
         this.particles.updateParticlesList();
      } else {
         this.clearRuntimeState();
      }
   }

   @Override
   public void onRender3D(MatrixStack matrices) {
      if (mc.player != null && mc.world != null) {
         this.particles.renderParticles(matrices);
      }
   }

   @Override
   public void onRender2D(DrawContext context) {
      this.drawVignetteTextureIfEffectMoment(context);
      this.charmSfx.inRenderThreadUpdate();
   }

   @EventHandler
   private void onAttackEntity(EventAttack event) {
      if (event != null && event.getEntity() != null) {
         if (event.getEntity() instanceof LivingEntity living && living.isAlive()) {
            this.deathMemory.controllingAddingMemoryToEntity(living, this.mobDetect());
         }
      }
   }

   @EventHandler
   private void onDeathEntity(EventDeath event) {
      if (event != null && event.getPlayer() != null) {
         this.deathMemory.onContains(event.getPlayer());
      }
   }

   public float getMulCameraZoomValue() {
      if (this.isEnabled() && mc.options.getPerspective().isFirstPerson()) {
         float effectPC = Math.min(this.getEffectPC() * 3.0F, 1.0F);
         if (effectPC > 0.0F) {
            float input = (effectPC > 0.5F ? 1.0F - effectPC : effectPC) * 2.0F;
            float value = (float)(effectPC > 0.5F ? FragEffectMath.easeExpoIn(input) : FragEffectMath.easeBackInOut(input));
            return 1.0F + FragEffectMath.lerp(value, value * value, 0.3F) / 1.25F;
         }
      }

      return 1.0F;
   }

   private boolean isWasTargeted(LivingEntity living) {
      return living != null && living == Aura.target;
   }

   private boolean mobDetect() {
      return true;
   }

   private boolean shouldRotateToKilled() {
      return true;
   }

   private void updateTargetMemories() {
      for (Entity entity : mc.world.getEntities()) {
         if (entity instanceof LivingEntity living && this.isWasTargeted(living)) {
            this.deathMemory.controllingAddingMemoryToEntity(living, this.mobDetect());
         }
      }
   }

   private void initWorldEffectOnUpdate() {
      this.charmSfx.sfxPulseDistantRand(0L);
      this.charmSfx.sfxKnockMain(75L);
      this.charmSfx.sfxSparksBlockHit(150L);
      this.charmSfx.sfxEchoMain(225L);
      Vcore.FRAG_EFFECT_TIMER = 0.07F;
      this.restartEffect();
   }

   private void restartEffect() {
      this.animate = true;
      this.linearTimer.reset();
      this.ticksForEditTimer = 0;
   }

   private float getEffectPC() {
      if (!this.animate) {
         return 0.0F;
      }

      float value = Math.min((float)this.linearTimer.getPassedTimeMs() / 2800.0F, 1.0F);
      if (value >= 1.0F) {
         this.animate = false;
         Vcore.FRAG_EFFECT_TIMER = 1.0F;
      }

      return value;
   }

   private void refillTimerValues(float... values) {
      this.timerValues = values;
   }

   private void updateTickTimerValues() {
      if (!this.animate) {
         Vcore.FRAG_EFFECT_TIMER = 1.0F;
      } else {
         this.ticksForEditTimer++;
         if (this.ticksForEditTimer < this.timerValues.length) {
            Vcore.FRAG_EFFECT_TIMER = this.timerValues[this.ticksForEditTimer];
         } else if (this.linearTimer.getPassedTimeMs() > 300L) {
            Vcore.FRAG_EFFECT_TIMER = 1.0F;
         }
      }
   }

   private void drawVignetteTextureIfEffectMoment(DrawContext context) {
      float effectPC = this.getEffectPC();
      if (effectPC != 0.0F) {
         float alphaPC = (float)FragEffectMath.easeQuintOut(effectPC);
         float scalePC = Math.min((alphaPC > 0.5F ? 1.0F - alphaPC : alphaPC) * 4.25F, 1.0F);
         float width = mc.getWindow().getScaledWidth();
         float height = mc.getWindow().getScaledHeight();
         float extPCX = width / 2.0F * (1.0F - scalePC);
         float extPCY = height / 2.0F * (1.0F - scalePC);
         float x = -extPCX;
         float y = -extPCY;
         float x2 = width + extPCX;
         float y2 = height + extPCY;
         int color = FragEffectMath.multAlpha(FragEffectMath.getOverallColorFrom(-65536, -1, effectPC), alphaPC);
         Color renderColor = new Color(color, true);
         RenderSystem.enableBlend();
         RenderSystem.blendFuncSeparate(SrcFactor.SRC_ALPHA, DstFactor.ONE, SrcFactor.ZERO, DstFactor.ONE);
         RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
         RenderSystem.setShaderTexture(0, VIGNETTE_TEXTURE);
         BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
         Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

         for (int i = 0; i < 4; i++) {
            buffer.vertex(matrix, x, y2, 0.0F).texture(0.0F, 1.0F).color(renderColor.getRGB());
            buffer.vertex(matrix, x2, y2, 0.0F).texture(1.0F, 1.0F).color(renderColor.getRGB());
            buffer.vertex(matrix, x2, y, 0.0F).texture(1.0F, 0.0F).color(renderColor.getRGB());
            buffer.vertex(matrix, x, y, 0.0F).texture(0.0F, 0.0F).color(renderColor.getRGB());
         }

         Render2DEngine.endBuilding(buffer);
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
   }

   private void clearRuntimeState() {
      this.animate = false;
      this.ticksForEditTimer = 0;
      Vcore.FRAG_EFFECT_TIMER = 1.0F;
      this.deathMemory.clear();
      this.particles.clear();
      this.charmSfx.clear();
   }
}
