package vcore.features.modules.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPart.Cuboid;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vcore.events.impl.TotemPopEvent;
import vcore.features.modules.Module;
import vcore.features.modules.render.popeffect.PopEffectParticles;
import vcore.injection.accesors.IEntity;
import vcore.injection.accesors.IModelPart;
import vcore.setting.Setting;
import vcore.setting.impl.ColorSetting;
import vcore.utility.math.MathUtility;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.Render3DEngine;

public final class PopEffect extends Module {
   private static final int FULL_BRIGHT_LIGHT = 15728880;
   private static final double EFFECT_SCALER = 2.5;
   private static final int EFFECT_REVIVE_COUNT = 17;
   private static final int EFFECT_SPARK_COUNT = 99;
   private final Setting<PopEffect.Mode> mode = new Setting<>("Mode", PopEffect.Mode.ChamsOld);
   private final Setting<Integer> oldYSpeed = new Setting<>("YSpeed", 1, -10, 10, v -> this.isChamsOld());
   private final Setting<Integer> oldAlphaSpeed = new Setting<>("AlphaSpeed", 35, 1, 100, v -> this.isChamsOld());
   private final Setting<Float> oldRotationSpeed = new Setting<>("RotationSpeed", 0.0F, 0.0F, 6.0F, v -> this.isChamsOld());
   private final Setting<ColorSetting> oldColor = new Setting<>("Color", new ColorSetting(new Color(-1, true)), v -> this.isChamsOld());
   private final Setting<Integer> newLifeTime = new Setting<>("Fade Time", 25, 5, 100, v -> this.shouldShowNewFadeTime());
   private final Setting<Float> newDisperseSpeed = new Setting<>("Animation Speed", 1.5F, 0.0F, 10.0F, v -> this.shouldShowNewAnimationSetting()).step(0.5F);
   private final Setting<Float> newDisperseMaxDistance = new Setting<>("Animation Distance", 1.0F, 0.0F, 8.0F, v -> this.shouldShowNewAnimationSetting())
      .step(0.1F);
   private final Setting<Float> newWireframeThickness = new Setting<>("OutLine Thickness", 2.0F, 0.0F, 5.0F, v -> this.shouldShowNewOutlineSetting())
      .step(0.1F);
   private final Setting<Boolean> newFadeOut = new Setting<>("Fade", true, v -> this.isChamsNew());
   private final Setting<Boolean> newDisperse = new Setting<>("Animation", false, v -> this.isChamsNew());
   private final Setting<Boolean> newFilledModel = new Setting<>("Fill", true, v -> this.isChamsNew());
   private final Setting<Boolean> newWireframe = new Setting<>("OutLine", true, v -> this.isChamsNew());
   private final Setting<ColorSetting> newFilledColor = new Setting<>(
      "Fill Color", new ColorSetting(new Color(137, 0, 255, 150)), v -> this.isChamsNew() && this.newFilledModel.getValue()
   );
   private final Setting<ColorSetting> newWireframeColor = new Setting<>(
      "OutLine Color", new ColorSetting(new Color(100, 0, 255, 150)), v -> this.isChamsNew() && this.newWireframe.getValue()
   );
   private final Setting<Float> effectOpacity = new Setting<>("Opacity", 1.0F, 0.0F, 1.0F, v -> this.isEffect()).step(0.05F);
   private final CopyOnWriteArrayList<PopEffect.OldPerson> oldPopList = new CopyOnWriteArrayList<>();
   private final CopyOnWriteArrayList<PopEffect.NewPerson> newPopList = new CopyOnWriteArrayList<>();

   public PopEffect() {
      super("PopEffect", "Highlights totem pops.", Module.Category.RENDER);
   }

   @Override
   public void onUpdate() {
      this.oldPopList.forEach(person -> person.update(this.oldPopList));
      this.newPopList.forEach(person -> person.update(this.newPopList));
   }

   @Override
   public void onRender3D(MatrixStack stack) {
      switch ((PopEffect.Mode)this.mode.getValue()) {
         case ChamsOld:
            this.renderChamsOld(stack);
            break;
         case ChamsNew:
            this.renderChamsNew(stack);
         case Effect:
      }
   }

   @Override
   public void onDisable() {
      this.clearEffects();
   }

   @Override
   public void onLogout() {
      this.clearEffects();
   }

   public boolean shouldHideVanillaTotemParticle() {
      return this.isEnabled() && this.isEffect();
   }

   public float getEffectOpacity() {
      return this.effectOpacity.getValue();
   }

   @EventHandler
   private void onTotemPop(@NotNull TotemPopEvent event) {
      if (mc.world != null && event.getEntity() != null) {
         switch ((PopEffect.Mode)this.mode.getValue()) {
            case ChamsOld:
               this.addChamsOld(event.getEntity());
            case ChamsNew:
            case Effect:
         }
      }
   }

   private void addChamsOld(@NotNull PlayerEntity source) {
      if (!source.equals(mc.player)) {
         this.oldPopList.add(new PopEffect.OldPerson(this.createCapturedPlayer(source)));
      }
   }

   private void addChamsNew(@NotNull PlayerEntity source) {
      if (!source.equals(mc.player)) {
         this.newPopList.add(new PopEffect.NewPerson(source));
      }
   }

   public void addChamsNewFromStatus(@NotNull PlayerEntity source) {
      if (this.isEnabled() && this.isChamsNew()) {
         this.addChamsNew(source);
      }
   }

   public void addEffect(@NotNull Entity source) {
      if (this.isEnabled() && this.isEffect()) {
         double centerY = source.getY() + source.getDimensions(source.getPose()).height() / 2.0F;

         for (int i = 1; i < 18; i++) {
            source.getWorld().addParticle(PopEffectParticles.REVIVE, source.getX(), centerY, source.getZ(), 2.5, source.getId(), 0.0);
         }

         for (int i = 1; i < 100; i++) {
            source.getWorld().addParticle(PopEffectParticles.REVIVE_SPARK, source.getX(), centerY, source.getZ(), source.getId(), 0.0, 0.0);
         }
      }
   }

   private void renderChamsOld(@NotNull MatrixStack matrices) {
      RenderSystem.enableBlend();
      RenderSystem.disableDepthTest();
      RenderSystem.defaultBlendFunc();
      this.oldPopList.forEach(person -> this.renderOldEntity(matrices, person.player, person.modelPlayer, person.getAlpha()));
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
   }

   private void renderChamsNew(@NotNull MatrixStack matrices) {
      if (!this.newPopList.isEmpty()) {
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableDepthTest();
         float tickDelta = Render3DEngine.getTickDelta();
         this.newPopList.forEach(person -> this.renderChamsNewPerson(matrices, person, tickDelta));
         RenderSystem.enableCull();
         RenderSystem.enableDepthTest();
         RenderSystem.disableBlend();
      }
   }

   private void renderChamsNewPerson(@NotNull MatrixStack matrices, @NotNull PopEffect.NewPerson person, float tickDelta) {
      float displacement = person.getDisplacement(tickDelta);
      float alpha = person.getAlpha(tickDelta, displacement);
      if (!(alpha <= 0.0F)) {
         if (this.newFilledModel.getValue()) {
            RenderSystem.enableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            this.renderNewFilledModel(matrices, buffer, person, alpha, displacement);
            Render2DEngine.endBuilding(buffer);
         }

         if (this.newWireframe.getValue()) {
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
            RenderSystem.lineWidth(this.newWireframeThickness.getValue());
            BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.LINES, VertexFormats.LINES);
            this.renderNewWireframe(matrices, buffer, person, alpha, displacement);
            Render2DEngine.endBuilding(buffer);
            RenderSystem.lineWidth(1.0F);
         }
      }
   }

   private void renderOldEntity(@NotNull MatrixStack matrices, @NotNull PlayerEntity entity, @NotNull PlayerEntityModel<PlayerEntity> model, int alpha) {
      setOuterLayerVisible(model, false);
      double x = entity.getX() - mc.getEntityRenderDispatcher().camera.getPos().getX();
      double y = entity.getY() - mc.getEntityRenderDispatcher().camera.getPos().getY();
      double z = entity.getZ() - mc.getEntityRenderDispatcher().camera.getPos().getZ();
      ((IEntity)entity).setPos(entity.getPos().add(0.0, this.oldYSpeed.getValue().intValue() / 50.0, 0.0));
      matrices.push();
      matrices.translate((float)x, (float)y, (float)z);
      float yRotYaw = alpha / 255.0F * 360.0F * this.oldRotationSpeed.getValue();
      yRotYaw = yRotYaw == 0.0F
         ? 0.0F
         : Render2DEngine.interpolateFloat(
            yRotYaw, yRotYaw - this.oldAlphaSpeed.getValue().intValue() / 255.0F * 360.0F * this.oldRotationSpeed.getValue(), Render3DEngine.getTickDelta()
         );
      matrices.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtility.rad(180.0F - entity.bodyYaw + yRotYaw)));
      preparePlayerScale(matrices);
      model.animateModel(entity, entity.limbAnimator.getPos(), entity.limbAnimator.getSpeed(), Render3DEngine.getTickDelta());
      float limbSpeed = Math.min(entity.limbAnimator.getSpeed(), 1.0F);
      model.setAngles(entity, entity.limbAnimator.getPos(), limbSpeed, entity.age, entity.headYaw - entity.bodyYaw, entity.getPitch());
      RenderSystem.setShader(GameRenderer::getPositionProgram);
      BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION);
      RenderSystem.setShaderColor(
         this.oldColor.getValue().getGlRed(), this.oldColor.getValue().getGlGreen(), this.oldColor.getValue().getGlBlue(), alpha / 255.0F
      );
      model.render(matrices, buffer, 15728880, OverlayTexture.DEFAULT_UV);
      Render2DEngine.endBuilding(buffer);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      matrices.pop();
   }

   private void renderNewFilledModel(
      @NotNull MatrixStack matrices, @NotNull BufferBuilder buffer, @NotNull PopEffect.NewPerson person, float alpha, float displacement
   ) {
      int color = this.withAlpha(this.newFilledColor.getValue(), alpha);
      this.setupChamsNewPlayerTransform(matrices, person);
      setOuterLayerVisible(person.modelPlayer, false);
      this.applyChamsNewFlyingTransforms(matrices, person);

      for (PopEffect.BodyPart part : PopEffect.BodyPart.values()) {
         Vec3d partDisplacement = this.getPartDisplacement(person.uuid, part, displacement);
         matrices.push();
         matrices.translate(partDisplacement.getX(), partDisplacement.getY(), partDisplacement.getZ());
         this.renderModelPart(person.modelPlayer, part, matrices, buffer, color);
         matrices.pop();
      }

      matrices.pop();
   }

   private void renderNewWireframe(
      @NotNull MatrixStack matrices, @NotNull BufferBuilder buffer, @NotNull PopEffect.NewPerson person, float alpha, float displacement
   ) {
      int color = this.withAlpha(this.newWireframeColor.getValue(), alpha);
      this.setupChamsNewPlayerTransform(matrices, person);
      setOuterLayerVisible(person.modelPlayer, false);
      this.applyChamsNewFlyingTransforms(matrices, person);

      for (PopEffect.BodyPart part : PopEffect.BodyPart.values()) {
         Vec3d partDisplacement = this.getPartDisplacement(person.uuid, part, displacement);
         matrices.push();
         matrices.translate(partDisplacement.getX(), partDisplacement.getY(), partDisplacement.getZ());
         this.renderWireframePart(this.getModelPart(person.modelPlayer, part), matrices, buffer, color);
         matrices.pop();
      }

      matrices.pop();
   }

   @NotNull
   private PlayerEntity createCapturedPlayer(@NotNull PlayerEntity source) {
      PlayerEntity captured = new PlayerEntity(mc.world, BlockPos.ORIGIN, source.bodyYaw, new GameProfile(source.getUuid(), source.getName().getString())) {
         public boolean isSpectator() {
            return false;
         }

         public boolean isCreative() {
            return false;
         }
      };
      captured.copyPositionAndRotation(source);
      captured.bodyYaw = source.bodyYaw;
      captured.prevBodyYaw = source.prevBodyYaw;
      captured.headYaw = source.headYaw;
      captured.prevHeadYaw = source.prevHeadYaw;
      captured.handSwingProgress = source.handSwingProgress;
      captured.handSwingTicks = source.handSwingTicks;
      captured.setSneaking(source.isSneaking());
      captured.setPose(source.getPose());
      captured.limbAnimator.setSpeed(source.limbAnimator.getSpeed());
      captured.limbAnimator.pos = source.limbAnimator.getPos();
      return captured;
   }

   @NotNull
   private PlayerEntity createChamsNewCapturedPlayer(@NotNull PlayerEntity source) {
      PlayerEntity captured = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), "Captured Player")) {
         public boolean isSpectator() {
            return false;
         }

         public boolean isCreative() {
            return false;
         }

         public boolean isPushable() {
            return false;
         }

         public boolean isCollidable() {
            return false;
         }

         public boolean collidesWith(Entity other) {
            return false;
         }
      };
      captured.copyPositionAndRotation(source);
      captured.prevX = source.prevX;
      captured.prevY = source.prevY;
      captured.prevZ = source.prevZ;
      captured.lastRenderX = source.lastRenderX;
      captured.lastRenderY = source.lastRenderY;
      captured.lastRenderZ = source.lastRenderZ;
      captured.prevYaw = source.prevYaw;
      captured.prevPitch = source.prevPitch;
      captured.bodyYaw = source.bodyYaw;
      captured.prevBodyYaw = source.prevBodyYaw;
      captured.headYaw = source.headYaw;
      captured.prevHeadYaw = source.prevHeadYaw;
      captured.handSwingProgress = source.handSwingProgress;
      captured.lastHandSwingProgress = source.lastHandSwingProgress;
      captured.handSwingTicks = source.handSwingTicks;
      captured.handSwinging = source.handSwinging;
      captured.setSneaking(source.isSneaking());
      captured.setPose(source.getPose());
      captured.limbAnimator.setSpeed(source.limbAnimator.getSpeed());
      captured.limbAnimator.pos = source.limbAnimator.getPos();
      return captured;
   }

   private void setupCapturedPlayerTransform(@NotNull MatrixStack matrices, @NotNull PlayerEntity entity) {
      double x = entity.getX() - mc.getEntityRenderDispatcher().camera.getPos().getX();
      double y = entity.getY() - mc.getEntityRenderDispatcher().camera.getPos().getY();
      double z = entity.getZ() - mc.getEntityRenderDispatcher().camera.getPos().getZ();
      matrices.push();
      matrices.translate((float)x, (float)y, (float)z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtility.rad(180.0F - entity.bodyYaw)));
      preparePlayerScale(matrices);
   }

   private void setupChamsNewPlayerTransform(@NotNull MatrixStack matrices, @NotNull PopEffect.NewPerson person) {
      double x = person.player.getX() - mc.getEntityRenderDispatcher().camera.getPos().getX();
      double y = person.player.getY() - mc.getEntityRenderDispatcher().camera.getPos().getY();
      double z = person.player.getZ() - mc.getEntityRenderDispatcher().camera.getPos().getZ();
      matrices.push();
      matrices.translate((float)x, (float)y, (float)z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - person.player.bodyYaw));
      prepareChamsNewPlayerScale(matrices);
   }

   private void applyChamsNewFlyingTransforms(@NotNull MatrixStack matrices, @NotNull PopEffect.NewPerson person) {
      if (!person.usingRiptide) {
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(person.leaningPitch * (90.0F - person.statePitch)));
      }
   }

   private static void preparePlayerScale(@NotNull MatrixStack matrices) {
      matrices.scale(-1.0F, -1.0F, 1.0F);
      matrices.scale(1.6F, 1.8F, 1.6F);
      matrices.translate(0.0F, -1.501F, 0.0F);
   }

   private static void prepareChamsNewPlayerScale(@NotNull MatrixStack matrices) {
      matrices.scale(-1.0F, -1.0F, 1.0F);
      matrices.scale(0.8F, 0.9F, 0.8F);
      matrices.translate(0.0F, -1.5F, 0.0F);
   }

   private static void setOuterLayerVisible(@NotNull PlayerEntityModel<PlayerEntity> model, boolean visible) {
      model.leftPants.visible = visible;
      model.rightPants.visible = visible;
      model.leftSleeve.visible = visible;
      model.rightSleeve.visible = visible;
      model.jacket.visible = visible;
      model.hat.visible = visible;
   }

   private static void copyModelPartTransform(@NotNull ModelPart target, @NotNull ModelPart source) {
      target.pitch = source.pitch;
      target.yaw = source.yaw;
      target.roll = source.roll;
      target.pivotX = source.pivotX;
      target.pivotY = source.pivotY;
      target.pivotZ = source.pivotZ;
      target.xScale = source.xScale;
      target.yScale = source.yScale;
      target.zScale = source.zScale;
   }

   private void renderModelPart(
      @NotNull PlayerEntityModel<PlayerEntity> model, @NotNull PopEffect.BodyPart part, @NotNull MatrixStack matrices, @NotNull BufferBuilder buffer, int color
   ) {
      switch (part) {
         case HEAD:
            model.head.render(matrices, buffer, 15728880, OverlayTexture.DEFAULT_UV, color);
            break;
         case BODY:
            model.body.render(matrices, buffer, 15728880, OverlayTexture.DEFAULT_UV, color);
            break;
         case LEFT_ARM:
            model.leftArm.render(matrices, buffer, 15728880, OverlayTexture.DEFAULT_UV, color);
            break;
         case RIGHT_ARM:
            model.rightArm.render(matrices, buffer, 15728880, OverlayTexture.DEFAULT_UV, color);
            break;
         case LEFT_LEG:
            model.leftLeg.render(matrices, buffer, 15728880, OverlayTexture.DEFAULT_UV, color);
            break;
         case RIGHT_LEG:
            model.rightLeg.render(matrices, buffer, 15728880, OverlayTexture.DEFAULT_UV, color);
      }
   }

   @NotNull
   private ModelPart getModelPart(@NotNull PlayerEntityModel<PlayerEntity> model, @NotNull PopEffect.BodyPart part) {
      return switch (part) {
         case HEAD -> model.head;
         case BODY -> model.body;
         case LEFT_ARM -> model.leftArm;
         case RIGHT_ARM -> model.rightArm;
         case LEFT_LEG -> model.leftLeg;
         case RIGHT_LEG -> model.rightLeg;
      };
   }

   private void renderWireframePart(@NotNull ModelPart part, @NotNull MatrixStack matrices, @NotNull BufferBuilder buffer, int color) {
      matrices.push();
      part.rotate(matrices);
      Matrix4f matrix = matrices.peek().getPositionMatrix();

      for (Cuboid cuboid : ((IModelPart)(Object)part).vcore$getCuboids()) {
         this.renderCuboidWireframe(matrix, buffer, cuboid, color);
      }

      matrices.pop();
   }

   private void renderCuboidWireframe(@NotNull Matrix4f matrix, @NotNull BufferBuilder buffer, @NotNull Cuboid cuboid, int color) {
      float x1 = cuboid.minX / 16.0F;
      float y1 = cuboid.minY / 16.0F;
      float z1 = cuboid.minZ / 16.0F;
      float x2 = cuboid.maxX / 16.0F;
      float y2 = cuboid.maxY / 16.0F;
      float z2 = cuboid.maxZ / 16.0F;
      this.vertexLine(matrix, buffer, x1, y1, z1, x2, y1, z1, color);
      this.vertexLine(matrix, buffer, x2, y1, z1, x2, y2, z1, color);
      this.vertexLine(matrix, buffer, x2, y2, z1, x1, y2, z1, color);
      this.vertexLine(matrix, buffer, x1, y2, z1, x1, y1, z1, color);
      this.vertexLine(matrix, buffer, x1, y1, z2, x2, y1, z2, color);
      this.vertexLine(matrix, buffer, x2, y1, z2, x2, y2, z2, color);
      this.vertexLine(matrix, buffer, x2, y2, z2, x1, y2, z2, color);
      this.vertexLine(matrix, buffer, x1, y2, z2, x1, y1, z2, color);
      this.vertexLine(matrix, buffer, x1, y1, z1, x1, y1, z2, color);
      this.vertexLine(matrix, buffer, x2, y1, z1, x2, y1, z2, color);
      this.vertexLine(matrix, buffer, x2, y2, z1, x2, y2, z2, color);
      this.vertexLine(matrix, buffer, x1, y2, z1, x1, y2, z2, color);
   }

   private void vertexLine(@NotNull Matrix4f matrix, @NotNull BufferBuilder buffer, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
      Vector3f from = new Vector3f(x1, y1, z1);
      Vector3f to = new Vector3f(x2, y2, z2);
      Vector3f normal = new Vector3f(to).sub(from).normalize();
      int alpha = color >> 24 & 0xFF;
      int red = color >> 16 & 0xFF;
      int green = color >> 8 & 0xFF;
      int blue = color & 0xFF;
      buffer.vertex(matrix, from.x, from.y, from.z).color(red, green, blue, alpha).normal(normal.x, normal.y, normal.z);
      buffer.vertex(matrix, to.x, to.y, to.z).color(red, green, blue, alpha).normal(normal.x, normal.y, normal.z);
   }

   @NotNull
   private Vec3d getPartDisplacement(@NotNull UUID uuid, @NotNull PopEffect.BodyPart part, float amount) {
      if (this.newDisperse.getValue() && !(amount <= 0.0F)) {
         long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ part.ordinal();
         Random random = Random.create(seed);

         Vec3d base = switch (part) {
            case HEAD -> new Vec3d(0.0, -1.0, 0.0);
            case BODY -> Vec3d.ZERO;
            case LEFT_ARM -> new Vec3d(1.0, 0.0, 0.0);
            case RIGHT_ARM -> new Vec3d(-1.0, 0.0, 0.0);
            case LEFT_LEG -> new Vec3d(0.3, 1.0, 0.0);
            case RIGHT_LEG -> new Vec3d(-0.3, 1.0, 0.0);
         };
         Vec3d noise = new Vec3d((random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
         return base.add(noise).normalize().multiply(amount);
      } else {
         return Vec3d.ZERO;
      }
   }

   @NotNull
   private PlayerEntityModel<PlayerEntity> createPlayerModel() {
      PlayerEntityModel<PlayerEntity> model = new PlayerEntityModel(
         new Context(
               mc.getEntityRenderDispatcher(),
               mc.getItemRenderer(),
               mc.getBlockRenderManager(),
               mc.getEntityRenderDispatcher().getHeldItemRenderer(),
               mc.getResourceManager(),
               mc.getEntityModelLoader(),
               mc.textRenderer
            )
            .getPart(EntityModelLayers.PLAYER),
         false
      );
      setOuterLayerVisible(model, false);
      return model;
   }

   private int withAlpha(@NotNull ColorSetting setting, float alphaMultiplier) {
      int color = setting.getRawColor();
      int alpha = MathHelper.clamp((int)((color >> 24 & 0xFF) * alphaMultiplier), 0, 255);
      return alpha << 24 | color & 16777215;
   }

   private void clearEffects() {
      this.oldPopList.clear();
      this.newPopList.clear();
   }

   private boolean shouldShowNewFadeTime() {
      return this.isChamsNew() && this.newFadeOut != null && this.newFadeOut.getValue();
   }

   private boolean shouldShowNewAnimationSetting() {
      return this.isChamsNew() && this.newDisperse != null && this.newDisperse.getValue();
   }

   private boolean shouldShowNewOutlineSetting() {
      return this.isChamsNew() && this.newWireframe != null && this.newWireframe.getValue();
   }

   private boolean isChamsOld() {
      return this.mode.is(PopEffect.Mode.ChamsOld);
   }

   private boolean isChamsNew() {
      return this.mode.is(PopEffect.Mode.ChamsNew);
   }

   private boolean isEffect() {
      return this.mode.is(PopEffect.Mode.Effect);
   }

   private enum BodyPart {
      HEAD,
      BODY,
      LEFT_ARM,
      RIGHT_ARM,
      LEFT_LEG,
      RIGHT_LEG;
   }

   private enum Mode {
      ChamsOld("Chams Old"),
      ChamsNew("Chams New"),
      Effect("Effect");

      private final String displayName;

      Mode(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String toString() {
         return this.displayName;
      }
   }

   private final class NewPerson {
      private final PlayerEntity player;
      private final PlayerEntityModel<PlayerEntity> modelPlayer;
      private final UUID uuid;
      private final float statePitch;
      private final float leaningPitch;
      private final boolean usingRiptide;
      private int age;

      private NewPerson(@NotNull PlayerEntity source) {
         this.player = PopEffect.this.createChamsNewCapturedPlayer(source);
         this.modelPlayer = PopEffect.this.createPlayerModel();
         this.uuid = this.player.getUuid();
         this.statePitch = source.getPitch();
         this.leaningPitch = source.getLeaningPitch(0.0F);
         this.usingRiptide = source.isUsingRiptide();
         this.age = 0;
         this.captureModelPose(source);
      }

      private void captureModelPose(@NotNull PlayerEntity source) {
         if (Module.mc.getEntityRenderDispatcher().getRenderer(source) instanceof LivingEntityRenderer<?, ?> renderer
            && renderer.getModel() instanceof PlayerEntityModel<?> originalModel) {
            PopEffect.copyModelPartTransform(this.modelPlayer.head, originalModel.head);
            PopEffect.copyModelPartTransform(this.modelPlayer.hat, originalModel.hat);
            PopEffect.copyModelPartTransform(this.modelPlayer.body, originalModel.body);
            PopEffect.copyModelPartTransform(this.modelPlayer.leftArm, originalModel.leftArm);
            PopEffect.copyModelPartTransform(this.modelPlayer.rightArm, originalModel.rightArm);
            PopEffect.copyModelPartTransform(this.modelPlayer.leftLeg, originalModel.leftLeg);
            PopEffect.copyModelPartTransform(this.modelPlayer.rightLeg, originalModel.rightLeg);
            PopEffect.setOuterLayerVisible(this.modelPlayer, false);
         } else {
            float limbSpeed = Math.min(this.player.limbAnimator.getSpeed(), 1.0F);
            this.modelPlayer.animateModel(this.player, this.player.limbAnimator.getPos(), limbSpeed, Render3DEngine.getTickDelta());
            this.modelPlayer
               .setAngles(
                  this.player, this.player.limbAnimator.getPos(), limbSpeed, this.player.age, this.player.headYaw - this.player.bodyYaw, this.player.getPitch()
               );
            PopEffect.setOuterLayerVisible(this.modelPlayer, false);
         }
      }

      private void update(@NotNull CopyOnWriteArrayList<PopEffect.NewPerson> arrayList) {
         this.age++;
         if (this.age >= PopEffect.this.newLifeTime.getValue()) {
            arrayList.remove(this);
            this.player.remove(RemovalReason.DISCARDED);
         }
      }

      private float getAlpha(float tickDelta, float displacement) {
         if (!PopEffect.this.newFadeOut.getValue()) {
            return 1.0F;
         }

         float elapsed = Math.max(0.0F, this.age - 1 + tickDelta);
         float fadeStart = PopEffect.this.newLifeTime.getValue().intValue() * 0.8F;
         float lifeAlpha = elapsed >= fadeStart ? 1.0F - (elapsed - fadeStart) / (PopEffect.this.newLifeTime.getValue().intValue() - fadeStart) : 1.0F;
         float maxDistance = PopEffect.this.newDisperseMaxDistance.getValue();
         float disperseAlpha = PopEffect.this.newDisperse.getValue() && maxDistance > 0.0F ? 1.0F - displacement / maxDistance : 1.0F;
         return MathHelper.clamp(lifeAlpha * disperseAlpha, 0.0F, 1.0F);
      }

      private float getDisplacement(float tickDelta) {
         if (!PopEffect.this.newDisperse.getValue()) {
            return 0.0F;
         }

         float elapsed = Math.max(0.0F, this.age - 1 + tickDelta);
         float speed = MathHelper.clamp(PopEffect.this.newDisperseSpeed.getValue(), 1.0F, 10.0F);
         float multiplier = (float)Math.pow(speed / 10.0F, 2.0) * 2.0F;
         return Math.min(elapsed * multiplier, PopEffect.this.newDisperseMaxDistance.getValue());
      }
   }

   private final class OldPerson {
      private final PlayerEntity player;
      private final PlayerEntityModel<PlayerEntity> modelPlayer;
      private int alpha;

      private OldPerson(@NotNull PlayerEntity player) {
         this.player = player;
         this.modelPlayer = PopEffect.this.createPlayerModel();
         this.modelPlayer.getHead().scale(new Vector3f(-0.3F, -0.3F, -0.3F));
         this.alpha = PopEffect.this.oldColor.getValue().getAlpha();
      }

      private void update(@NotNull CopyOnWriteArrayList<PopEffect.OldPerson> arrayList) {
         if (this.alpha <= 0) {
            arrayList.remove(this);
            this.player.kill();
            this.player.remove(RemovalReason.KILLED);
            this.player.onRemoved();
         } else {
            this.alpha = this.alpha - PopEffect.this.oldAlphaSpeed.getValue();
         }
      }

      private int getAlpha() {
         return MathUtility.clamp(this.alpha, 0, 255);
      }
   }
}
