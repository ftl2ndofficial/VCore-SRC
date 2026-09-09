package vcore.gui.clickui.impl;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import vcore.core.Managers;
import vcore.features.modules.render.HudEditor;
import vcore.gui.clickui.AbstractElement;
import vcore.gui.clickui.ModuleButton;
import vcore.gui.font.FontRenderers;
import vcore.setting.Setting;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.TextureStorage;
import vcore.utility.render.animation.AnimationUtility;

public class ModeElement extends AbstractElement {
   private static final float CHIP_TOP_MARGIN = 3.0F;
   private static final float CHIP_BOTTOM_MARGIN = 3.0F;
   private static final float CHIP_HORIZONTAL_PADDING = 1.75F;
   private static final float CHIP_VERTICAL_PADDING = 0.5F;
   private static final float CHIP_HORIZONTAL_GAP = 0.85F;
   private static final float CHIP_VERTICAL_GAP = 0.85F;
   private static final float CHIP_LEFT_PADDING = 6.0F;
   private static final float CHIP_RIGHT_PADDING = 8.0F;
   private static final float CHIP_MIN_WIDTH = 8.0F;
   private static final float CHIP_RADIUS = 2.0F;
   private static final float CHIP_OUTLINE_THICKNESS = 0.4F;
   private final ModuleButton owner;
   public Setting setting2;
   private boolean open;
   private double wheight;
   private String prevMode;
   private float animation;
   private float animation2;

   public ModeElement(ModuleButton owner, Setting setting) {
      super(setting);
      this.owner = owner;
      this.setting2 = setting;
      this.prevMode = setting.currentEnumName();
   }

   @Override
   public void init() {
      this.animation = this.open ? 0.0F : 1.0F;
      this.animation2 = 1.0F;
      this.prevMode = this.setting2.currentEnumName();
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.animation = AnimationUtility.fast(this.animation, this.open ? 0.0F : 1.0F, 15.0F);
      this.animation2 = AnimationUtility.fast(this.animation2, 1.0F, 10.0F);
      ModeElement.ModeChipLayout chipLayout = this.buildChipLayout();
      float tx = this.x + this.width - 11.0F;
      float ty = this.y + 7.5F;
      MatrixStack matrixStack = context.getMatrices();
      float thetaRotation = -180.0F * this.animation;
      matrixStack.push();
      matrixStack.translate(tx, ty, 0.0F);
      matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(thetaRotation));
      matrixStack.translate(-tx, -ty, 0.0F);
      matrixStack.translate(this.x + this.width - 14.0F, this.y + 4.5F, 0.0F);
      context.drawTexture(TextureStorage.guiArrow, 0, 0, 0.0F, 0.0F, 6, 6, 6, 6);
      matrixStack.translate(-(this.x + this.width - 14.0F), -this.y - 4.5F, 0.0F);
      matrixStack.pop();
      if (this.setting.group != null && !this.open) {
         this.drawGroupGuide(context, this.height);
      }

      FontRenderers.sf_medium_mini
         .drawString(matrixStack, this.setting2.getName(), this.getSettingNameX(), this.y + this.wheight / 2.0 - 3.0 + 3.0, Color.WHITE.getRGB());
      if (this.animation2 < 0.99 && !Objects.equals(this.setting2.currentEnumName(), this.prevMode)) {
         FontRenderers.sf_medium_mini
            .drawString(
               matrixStack,
               this.prevMode,
               (int)(this.x + this.width - 18.0F - FontRenderers.sf_medium_mini.getStringWidth(this.prevMode)),
               3.0 + (this.y + this.wheight / 2.0 - 3.0) - this.animation2 * 5.0F,
               Render2DEngine.applyOpacity(new Color(-1), this.animation2)
            );
         FontRenderers.sf_medium_mini
            .drawString(
               matrixStack,
               this.setting2.currentEnumName(),
               this.x + this.width - 18.0F - FontRenderers.sf_medium_mini.getStringWidth(this.setting2.currentEnumName()),
               3.0 + (this.y + this.wheight / 2.0 - 3.0) - this.animation2 * 5.0F + 5.0,
               Render2DEngine.applyOpacity(new Color(-1), 1.0F - this.animation2)
            );
      } else {
         FontRenderers.sf_medium_mini
            .drawString(
               matrixStack,
               this.setting2.currentEnumName(),
               this.x + this.width - 18.0F - FontRenderers.sf_medium_mini.getStringWidth(this.setting.currentEnumName()),
               3.0 + (this.y + this.wheight / 2.0 - 3.0),
               Color.WHITE.getRGB()
            );
      }

      if (this.open) {
         Color selectedColor = HudEditor.getColor(0);
         Color outlineColor = this.getChipOutlineColor();

         for (ModeElement.ModeChip chip : chipLayout.chips) {
            this.drawChipOutline(context, chip, outlineColor);
            float textX = chip.x + (chip.width - FontRenderers.sf_medium_mini.getStringWidth(chip.mode)) / 2.0F;
            float textY = chip.y + (chip.height - FontRenderers.sf_medium_mini.getFontHeight(chip.mode)) / 2.0F + 2.9F;
            FontRenderers.sf_medium_mini
               .drawString(
                  matrixStack,
                  chip.mode,
                  textX,
                  textY,
                  this.setting2.currentEnumName().equalsIgnoreCase(chip.mode) ? selectedColor.getRGB() : Color.WHITE.getRGB()
               );
         }
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int button) {
      if (Render2DEngine.isHovered(mouseX, mouseY, this.x, this.y, this.width, this.wheight)) {
         if (button == 0) {
            this.prevMode = this.setting2.currentEnumName();
            this.animation2 = 0.0F;
            this.setting2.increaseEnum();
            Managers.SOUND.playBoolean();
         } else {
            boolean opening = !this.open;
            if (opening) {
               this.owner.closeSiblingSettingPanels(this.setting2);
            }

            this.open = opening;
            if (this.open) {
               Managers.SOUND.playSwipeIn();
            } else {
               Managers.SOUND.playSwipeOut();
            }
         }
      }

      if (this.open && button == 0) {
         for (ModeElement.ModeChip chip : this.buildChipLayout().chips) {
            if (Render2DEngine.isHovered(mouseX, mouseY, chip.x, chip.y, chip.width, chip.height)) {
               this.prevMode = this.setting2.currentEnumName();
               this.animation2 = 0.0F;
               this.setting2.setEnumByNumber(chip.index);
               Managers.SOUND.playBoolean();
               break;
            }
         }
      }

      super.mouseClicked(mouseX, mouseY, button);
   }

   public float getExpandedHeight() {
      return this.open ? (float)this.wheight + this.buildChipLayout().contentHeight : (float)this.wheight;
   }

   public void setWHeight(double height) {
      this.wheight = height;
   }

   public boolean isOpen() {
      return this.open;
   }

   public void setOpen(boolean open) {
      this.open = open;
   }

   private ModeElement.ModeChipLayout buildChipLayout() {
      List<ModeElement.ModeChip> chips = new ArrayList<>();
      if (!this.open) {
         return new ModeElement.ModeChipLayout(chips, 0.0F);
      }

      float startX = this.x + 6.0F + this.getGroupIndent();
      float maxRight = this.x + this.width - 8.0F;
      float chipHeight = Math.max(8.5F, FontRenderers.sf_medium_mini.getFontHeight("A") + 1.0F);
      float currentX = startX;
      float currentY = this.y + (float)this.wheight + 3.0F;
      float maxBottom = currentY;
      String[] modes = this.setting2.getModes();

      for (int i = 0; i < modes.length; i++) {
         String mode = modes[i];
         float chipWidth = Math.max(8.0F, FontRenderers.sf_medium_mini.getStringWidth(mode) + 3.5F);
         if (currentX + chipWidth > maxRight && currentX > startX) {
            currentX = startX;
            currentY += chipHeight + 0.85F;
         }

         chips.add(new ModeElement.ModeChip(i, mode, currentX, currentY, chipWidth, chipHeight));
         currentX += chipWidth + 0.85F;
         maxBottom = currentY + chipHeight;
      }

      float contentHeight = chips.isEmpty() ? 0.0F : maxBottom - (this.y + (float)this.wheight) + 3.0F;
      return new ModeElement.ModeChipLayout(chips, contentHeight);
   }

   private void drawChipOutline(DrawContext context, ModeElement.ModeChip chip, Color outlineColor) {
      Render2DEngine.drawCheckbox(context.getMatrices(), chip.x, chip.y, chip.width, chip.height, 2.0F, 0.4F, 0.0F, outlineColor);
   }

   private Color getChipOutlineColor() {
      int alpha = HudEditor.plateColor.getValue().getAlpha();
      return new Color(255, 255, 255, alpha);
   }

   private static final class ModeChip {
      private final int index;
      private final String mode;
      private final float x;
      private final float y;
      private final float width;
      private final float height;

      private ModeChip(int index, String mode, float x, float y, float width, float height) {
         this.index = index;
         this.mode = mode;
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }
   }

   private static final class ModeChipLayout {
      private final List<ModeElement.ModeChip> chips;
      private final float contentHeight;

      private ModeChipLayout(List<ModeElement.ModeChip> chips, float contentHeight) {
         this.chips = chips;
         this.contentHeight = contentHeight;
      }
   }
}
