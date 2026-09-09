package vcore.features.modules.render;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import vcore.core.Managers;
import vcore.features.modules.Module;
import vcore.setting.Setting;

public class CustomModel extends Module {
   private final Setting<CustomModel.Mode> mode = new Setting<>("Mode", CustomModel.Mode.Rabbit);
   private final Setting<Boolean> self = new Setting<>("Self", true);
   private final Setting<Boolean> friends = new Setting<>("Friends", true);
   private final Setting<Boolean> others = new Setting<>("Others", true);
   private static final float RABBIT_HEIGHT_FACTOR = 0.76F;
   private static final float VANILLA_HEIGHT_FACTOR = 0.96F;
   private static final float VANILLA_MODEL_HEIGHT = 16.0F;
   private static final float RABBIT_MODEL_HEIGHT = 12.666667F;
   private static final float RABBIT_VERTICAL_OFFSET = 0.7F;
   private static final Identifier RABBIT_TEXTURE = Identifier.of("vcore", "textures/custommodel/rabbit.png");
   private static final Identifier FREDDY_TEXTURE = Identifier.of("vcore", "textures/custommodel/freddy.png");
   private final CustomModel.RabbitModel rabbitModel = new CustomModel.RabbitModel();
   private final CustomModel.FreddyModel freddyModel = new CustomModel.FreddyModel();
   private final ModelPart rabbitCape = this.createCapePart(12.666667F);
   private AbstractClientPlayerEntity renderingPlayer;

   public CustomModel() {
      super("CustomModel", "Renders selected custom model variants.", Module.Category.RENDER);
   }

   public Identifier getTexture(AbstractClientPlayerEntity player, Identifier fallback) {
      if (!this.shouldApply(player)) {
         return fallback;
      }

      return switch ((CustomModel.Mode)this.mode.getValue()) {
         case Rabbit -> RABBIT_TEXTURE;
         case FreddyBear -> FREDDY_TEXTURE;
      };
   }

   public void beginPlayerRender(AbstractClientPlayerEntity player) {
      this.renderingPlayer = player;
   }

   public void endPlayerRender(AbstractClientPlayerEntity player) {
      if (this.renderingPlayer == player) {
         this.renderingPlayer = null;
      }
   }

   public AbstractClientPlayerEntity getRenderingPlayer() {
      return this.renderingPlayer;
   }

   public boolean renderCustomPlayerModel(
      AbstractClientPlayerEntity player,
      PlayerEntityModel<?> vanillaModel,
      MatrixStack matrices,
      VertexConsumer vertexConsumer,
      int light,
      int overlay,
      int color
   ) {
      if (!this.shouldApply(player)) {
         return false;
      }

      switch ((CustomModel.Mode)this.mode.getValue()) {
         case Rabbit:
            this.renderRabbit(vanillaModel, matrices, vertexConsumer, light, overlay, color);
            break;
         case FreddyBear:
            this.renderFreddy(vanillaModel, matrices, vertexConsumer, light, overlay, color);
      }

      return true;
   }

   public boolean renderCape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player, float tickDelta) {
      if (!this.shouldRenderRabbitCape(player)) {
         return false;
      }

      SkinTextures skinTextures = player.getSkinTextures();
      Identifier capeTexture = skinTextures.capeTexture();
      if (capeTexture == null) {
         return false;
      }

      matrices.push();
      matrices.translate(0.0F, 0.0F, 0.125F);
      matrices.translate(0.0F, 0.7F, 0.0F);
      this.applyVanillaCapeRotation(matrices, player, tickDelta);
      VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutout(capeTexture));
      this.rabbitCape.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);
      matrices.pop();
      return true;
   }

   public boolean shouldHideArmorSlotRender(LivingEntity entity) {
      return entity instanceof AbstractClientPlayerEntity player && this.shouldApply(player);
   }

   public boolean shouldTransformHeldItem(LivingEntity entity) {
      return entity instanceof AbstractClientPlayerEntity player && this.shouldApply(player);
   }

   public void applyHeldItemRootTransform(MatrixStack matrices, LivingEntity entity) {
      if (this.shouldTransformHeldItem(entity)) {
         switch ((CustomModel.Mode)this.mode.getValue()) {
            case Rabbit:
               matrices.translate(0.0, 0.3, 0.0);
               matrices.scale(0.8F, 0.8F, 0.8F);
               break;
            case FreddyBear:
               matrices.translate(0.07, -0.3, 0.06);
               matrices.scale(0.79F, 0.9F, 1.15F);
         }
      }
   }

   private void renderRabbit(PlayerEntityModel<?> vanillaModel, MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color) {
      this.copyRotation(this.rabbitModel.head, vanillaModel.head);
      this.copyRotation(this.rabbitModel.leftArm, vanillaModel.leftArm);
      this.copyRotation(this.rabbitModel.rightArm, vanillaModel.rightArm);
      this.copyRotation(this.rabbitModel.leftLeg, vanillaModel.leftLeg);
      this.copyRotation(this.rabbitModel.rightLeg, vanillaModel.rightLeg);
      matrices.push();
      this.rabbitModel.root.render(matrices, vertexConsumer, light, overlay, color);
      matrices.pop();
   }

   private void renderFreddy(PlayerEntityModel<?> vanillaModel, MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, int color) {
      this.copyRotation(this.freddyModel.head, vanillaModel.head);
      this.copyRotation(this.freddyModel.leftArm, vanillaModel.leftArm);
      this.copyRotation(this.freddyModel.rightArm, vanillaModel.rightArm);
      this.copyRotation(this.freddyModel.leftLeg, vanillaModel.leftLeg);
      this.copyRotation(this.freddyModel.rightLeg, vanillaModel.rightLeg);
      matrices.push();
      matrices.scale(0.75F, 0.65F, 0.75F);
      matrices.translate(0.0, 0.85F, 0.0);
      this.freddyModel.root.render(matrices, vertexConsumer, light, overlay, color);
      matrices.pop();
   }

   private void copyRotation(ModelPart target, ModelPart source) {
      target.pitch = source.pitch;
      target.yaw = source.yaw;
      target.roll = source.roll;
   }

   private boolean shouldRenderRabbitCape(AbstractClientPlayerEntity player) {
      if (this.mode.getValue() != CustomModel.Mode.Rabbit || !this.shouldApply(player)) {
         return false;
      } else {
         return !player.isInvisible() && player.isPartVisible(PlayerModelPart.CAPE) ? !player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) : false;
      }
   }

   private boolean shouldApply(AbstractClientPlayerEntity player) {
      if (fullNullCheck() || player == null || !this.isEnabled()) {
         return false;
      } else if (player == mc.player) {
         return this.self.getValue();
      } else {
         return Managers.FRIEND.isFriend(player) ? this.friends.getValue() : this.others.getValue();
      }
   }

   private void applyVanillaCapeRotation(MatrixStack matrices, AbstractClientPlayerEntity player, float tickDelta) {
      double capeX = MathHelper.lerp(tickDelta, player.prevCapeX, player.capeX) - MathHelper.lerp(tickDelta, player.prevX, player.getX());
      double capeY = MathHelper.lerp(tickDelta, player.prevCapeY, player.capeY) - MathHelper.lerp(tickDelta, player.prevY, player.getY());
      double capeZ = MathHelper.lerp(tickDelta, player.prevCapeZ, player.capeZ) - MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
      float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
      double sinYaw = MathHelper.sin(bodyYaw * (float) (Math.PI / 180.0));
      double negCosYaw = -MathHelper.cos(bodyYaw * (float) (Math.PI / 180.0));
      float verticalSwing = (float)capeY * 10.0F;
      verticalSwing = MathHelper.clamp(verticalSwing, -6.0F, 32.0F);
      float backwardSwing = (float)(capeX * sinYaw + capeZ * negCosYaw) * 100.0F;
      backwardSwing = MathHelper.clamp(backwardSwing, 0.0F, 150.0F);
      float sideSwing = (float)(capeX * negCosYaw - capeZ * sinYaw) * 100.0F;
      sideSwing = MathHelper.clamp(sideSwing, -20.0F, 20.0F);
      if (backwardSwing < 0.0F) {
         backwardSwing = 0.0F;
      }

      float stride = MathHelper.lerp(tickDelta, player.prevStrideDistance, player.strideDistance);
      verticalSwing += MathHelper.sin(MathHelper.lerp(tickDelta, player.prevHorizontalSpeed, player.horizontalSpeed) * 6.0F) * 32.0F * stride;
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0F + backwardSwing / 2.0F + verticalSwing));
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sideSwing / 2.0F));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - sideSwing / 2.0F));
   }

   private ModelPart createCapePart(float height) {
      ModelData modelData = new ModelData();
      modelData.getRoot()
         .addChild(
            "cape", ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, 0.0F, -1.0F, 10.0F, height, 1.0F, Dilation.NONE), ModelTransform.pivot(0.0F, 0.0F, 0.0F)
         );
      return TexturedModelData.of(modelData, 64, 32).createModel().getChild("cape");
   }

   private static ModelPartBuilder box(int textureX, int textureY, float x, float y, float z, float width, float height, float depth) {
      return ModelPartBuilder.create().uv(textureX, textureY).cuboid(x, y, z, width, height, depth, Dilation.NONE);
   }

   private static final class FreddyModel {
      private final ModelPart root = createRoot();
      private final ModelPart head = this.root.getChild("fredhead");
      private final ModelPart leftArm = this.root.getChild("armLeft");
      private final ModelPart rightArm = this.root.getChild("armRight");
      private final ModelPart leftLeg = this.root.getChild("legLeft");
      private final ModelPart rightLeg = this.root.getChild("legRight");

      private static ModelPart createRoot() {
         ModelData modelData = new ModelData();
         ModelPartData root = modelData.getRoot();
         ModelPartData fredbody = root.addChild(
            "fredbody", CustomModel.box(0, 0, -1.0F, -14.0F, -1.0F, 2.0F, 24.0F, 2.0F), ModelTransform.pivot(0.0F, -9.0F, 0.0F)
         );
         fredbody.addChild(
            "torso", CustomModel.box(8, 0, -6.0F, -9.0F, -4.0F, 12.0F, 18.0F, 8.0F), ModelTransform.of(0.0F, 0.0F, 0.0F, (float) (Math.PI / 180.0), 0.0F, 0.0F)
         );
         fredbody.addChild("crotch", CustomModel.box(56, 0, -5.5F, 0.0F, -3.5F, 11.0F, 3.0F, 7.0F), ModelTransform.pivot(0.0F, 9.5F, 0.0F));
         ModelPartData armRight = fredbody.addChild(
            "armRight",
            CustomModel.box(48, 0, -1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F),
            ModelTransform.of(-6.5F, -8.0F, 0.0F, 0.0F, 0.0F, (float) (Math.PI / 12))
         );
         armRight.addChild("armRightpad", CustomModel.box(70, 10, -2.5F, 0.0F, -2.5F, 5.0F, 9.0F, 5.0F), ModelTransform.pivot(0.0F, 0.5F, 0.0F));
         ModelPartData armRight2 = armRight.addChild(
            "armRight2",
            CustomModel.box(90, 20, -1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
            ModelTransform.of(0.0F, 9.6F, 0.0F, (float) (-Math.PI / 18), 0.0F, 0.0F)
         );
         armRight2.addChild("armRightpad2", CustomModel.box(0, 26, -2.5F, 0.0F, -2.5F, 5.0F, 7.0F, 5.0F), ModelTransform.pivot(0.0F, 0.5F, 0.0F));
         armRight2.addChild(
            "handRight", CustomModel.box(20, 26, -2.0F, 0.0F, -2.5F, 4.0F, 4.0F, 5.0F), ModelTransform.of(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, -0.05235988F)
         );
         ModelPartData armLeft = fredbody.addChild(
            "armLeft",
            CustomModel.box(62, 10, -1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F),
            ModelTransform.of(6.5F, -8.0F, 0.0F, 0.0F, 0.0F, (float) (-Math.PI / 12))
         );
         armLeft.addChild("armLeftpad", CustomModel.box(38, 54, -2.5F, 0.0F, -2.5F, 5.0F, 9.0F, 5.0F), ModelTransform.pivot(0.0F, 0.5F, 0.0F));
         ModelPartData armLeft2 = armLeft.addChild(
            "armLeft2", CustomModel.box(90, 48, -1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), ModelTransform.of(0.0F, 9.6F, 0.0F, (float) (-Math.PI / 18), 0.0F, 0.0F)
         );
         armLeft2.addChild("armLeftpad2", CustomModel.box(0, 58, -2.5F, 0.0F, -2.5F, 5.0F, 7.0F, 5.0F), ModelTransform.pivot(0.0F, 0.5F, 0.0F));
         armLeft2.addChild(
            "handLeft", CustomModel.box(58, 56, -1.0F, 0.0F, -2.5F, 4.0F, 4.0F, 5.0F), ModelTransform.of(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.05235988F)
         );
         ModelPartData legRight = fredbody.addChild(
            "legRight", CustomModel.box(90, 8, -1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), ModelTransform.pivot(-3.3F, 12.5F, 0.0F)
         );
         legRight.addChild("legRightpad", CustomModel.box(73, 33, -3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F), ModelTransform.pivot(0.0F, 0.5F, 0.0F));
         ModelPartData legRight2 = legRight.addChild(
            "legRight2", CustomModel.box(20, 35, -1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), ModelTransform.of(0.0F, 9.6F, 0.0F, 0.034906585F, 0.0F, 0.0F)
         );
         legRight2.addChild("legRightpad2", CustomModel.box(0, 39, -2.5F, 0.0F, -3.0F, 5.0F, 7.0F, 6.0F), ModelTransform.pivot(0.0F, 0.5F, 0.0F));
         legRight2.addChild(
            "footRight", CustomModel.box(22, 39, -2.5F, 0.0F, -6.0F, 5.0F, 3.0F, 8.0F), ModelTransform.of(0.0F, 8.0F, 0.0F, -0.034906585F, 0.0F, 0.0F)
         );
         ModelPartData legLeft = fredbody.addChild(
            "legLeft", CustomModel.box(54, 10, -1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F), ModelTransform.pivot(3.3F, 12.5F, 0.0F)
         );
         legLeft.addChild("legLeftpad", CustomModel.box(48, 39, -3.0F, 0.0F, -3.0F, 6.0F, 9.0F, 6.0F), ModelTransform.pivot(0.0F, 0.5F, 0.0F));
         ModelPartData legLeft2 = legLeft.addChild(
            "legLeft2", CustomModel.box(72, 48, -1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F), ModelTransform.of(0.0F, 9.6F, 0.0F, 0.034906585F, 0.0F, 0.0F)
         );
         legLeft2.addChild("legLeftpad2", CustomModel.box(16, 50, -2.5F, 0.0F, -3.0F, 5.0F, 7.0F, 6.0F), ModelTransform.pivot(0.0F, 0.5F, 0.0F));
         legLeft2.addChild(
            "footLeft", CustomModel.box(72, 50, -2.5F, 0.0F, -6.0F, 5.0F, 3.0F, 8.0F), ModelTransform.of(0.0F, 8.0F, 0.0F, -0.034906585F, 0.0F, 0.0F)
         );
         ModelPartData fredhead = fredbody.addChild(
            "fredhead", CustomModel.box(39, 22, -5.5F, -8.0F, -4.5F, 11.0F, 8.0F, 9.0F), ModelTransform.pivot(0.0F, -13.0F, -0.5F)
         );
         fredhead.addChild("frednose", CustomModel.box(17, 67, -4.0F, -2.0F, -3.0F, 8.0F, 4.0F, 3.0F), ModelTransform.pivot(0.0F, -2.0F, -4.5F));
         fredhead.addChild("jaw", CustomModel.box(49, 65, -5.0F, 0.0F, -4.5F, 10.0F, 3.0F, 9.0F), ModelTransform.of(0.0F, 0.5F, 0.0F, 0.08726646F, 0.0F, 0.0F));
         ModelPartData earRight = fredhead.addChild(
            "earRight",
            CustomModel.box(8, 0, -1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
            ModelTransform.of(-4.5F, -5.5F, 0.0F, 0.05235988F, 0.0F, (float) (-Math.PI / 3))
         );
         earRight.addChild("earRightpad", CustomModel.box(85, 0, -2.0F, -5.0F, -1.0F, 4.0F, 4.0F, 2.0F), ModelTransform.pivot(0.0F, -1.0F, 0.0F));
         ModelPartData earLeft = fredhead.addChild(
            "earLeft",
            CustomModel.box(40, 0, -1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F),
            ModelTransform.of(4.5F, -5.5F, 0.0F, 0.05235988F, 0.0F, (float) (Math.PI / 3))
         );
         earLeft.addChild("earRightpad_1", CustomModel.box(40, 39, -2.0F, -5.0F, -1.0F, 4.0F, 4.0F, 2.0F), ModelTransform.pivot(0.0F, -1.0F, 0.0F));
         ModelPartData hat = fredhead.addChild(
            "hat", CustomModel.box(70, 24, -3.0F, -0.5F, -3.0F, 6.0F, 1.0F, 6.0F), ModelTransform.of(0.0F, -8.4F, 0.0F, (float) (-Math.PI / 180.0), 0.0F, 0.0F)
         );
         hat.addChild(
            "hat2", CustomModel.box(78, 61, -2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F), ModelTransform.of(0.0F, 0.1F, 0.0F, (float) (-Math.PI / 180.0), 0.0F, 0.0F)
         );
         return TexturedModelData.of(modelData, 100, 80).createModel().getChild("fredbody");
      }
   }

   private enum Mode {
      Rabbit("Rabbit"),
      FreddyBear("Freddy Bear");

      private final String name;

      Mode(String name) {
         this.name = name;
      }

      @Override
      public String toString() {
         return this.name;
      }
   }

   private static final class RabbitModel {
      private final ModelPart root = createRoot();
      private final ModelPart head = this.root.getChild("rabbitHead");
      private final ModelPart leftArm = this.root.getChild("rabbitLarm");
      private final ModelPart rightArm = this.root.getChild("rabbitRarm");
      private final ModelPart leftLeg = this.root.getChild("rabbitLleg");
      private final ModelPart rightLeg = this.root.getChild("rabbitRleg");

      private static ModelPart createRoot() {
         ModelData modelData = new ModelData();
         ModelPartData root = modelData.getRoot();
         ModelPartData rabbitBone = root.addChild(
            "rabbitBone", CustomModel.box(28, 45, -5.0F, -13.0F, -5.0F, 10.0F, 11.0F, 8.0F), ModelTransform.pivot(0.0F, 24.0F, 0.0F)
         );
         rabbitBone.addChild("rabbitRleg", CustomModel.box(0, 0, -2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F), ModelTransform.pivot(-3.0F, -2.0F, -1.0F));
         rabbitBone.addChild(
            "rabbitLarm", CustomModel.box(0, 0, 0.0F, 0.0F, -2.0F, 2.0F, 8.0F, 4.0F), ModelTransform.of(5.0F, -13.0F, -1.0F, 0.0F, 0.0F, -0.0873F)
         );
         rabbitBone.addChild(
            "rabbitRarm", CustomModel.box(0, 0, -2.0F, 0.0F, -2.0F, 2.0F, 8.0F, 4.0F), ModelTransform.of(-5.0F, -13.0F, -1.0F, 0.0F, 0.0F, 0.0873F)
         );
         rabbitBone.addChild("rabbitLleg", CustomModel.box(0, 0, -2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F), ModelTransform.pivot(3.0F, -2.0F, -1.0F));
         rabbitBone.addChild(
            "rabbitHead",
            ModelPartBuilder.create()
               .uv(0, 0)
               .cuboid(-3.0F, 0.0F, -4.0F, 6.0F, 1.0F, 6.0F, Dilation.NONE)
               .uv(56, 0)
               .cuboid(-5.0F, -9.0F, -5.0F, 2.0F, 3.0F, 2.0F, Dilation.NONE)
               .uv(56, 0)
               .cuboid(3.0F, -9.0F, -5.0F, 2.0F, 3.0F, 2.0F, Dilation.NONE)
               .uv(0, 45)
               .cuboid(-4.0F, -11.0F, -4.0F, 8.0F, 11.0F, 8.0F, Dilation.NONE)
               .uv(46, 0)
               .cuboid(1.0F, -20.0F, 0.0F, 3.0F, 9.0F, 1.0F, Dilation.NONE)
               .uv(46, 0)
               .cuboid(-4.0F, -20.0F, 0.0F, 3.0F, 9.0F, 1.0F, Dilation.NONE),
            ModelTransform.pivot(0.0F, -14.0F, -1.0F)
         );
         return TexturedModelData.of(modelData, 64, 64).createModel().getChild("rabbitBone");
      }
   }
}
