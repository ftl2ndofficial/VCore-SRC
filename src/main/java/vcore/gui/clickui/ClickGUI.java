package vcore.gui.clickui;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.Color;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import vcore.core.Managers;
import vcore.core.manager.client.ModuleManager;
import vcore.features.hud.HudElement;
import vcore.features.modules.Module;
import vcore.features.modules.render.HudEditor;
import vcore.gui.clickui.impl.SearchBar;
import vcore.gui.clickui.impl.ThemeSelector;
import vcore.gui.font.FontRenderer;
import vcore.gui.font.FontRenderers;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.animation.advanced.Easing;

public class ClickGUI extends Screen {
   public static List<AbstractCategory> windows;
   public static boolean anyHovered;
   private float scrollY;
   public static boolean close = false;
   public static boolean imageDirection;
   private final SearchBar searchBar = new SearchBar();
   private final ThemeSelector themeSelector = new ThemeSelector();
   private float managerBtnX;
   private float managerBtnY;
   private float managerBtnSize;
   public static String currentDescription = "";
   public static boolean descriptionActive;
   private static final int OPEN_ANIMATION_MS = 460;
   private static final long MAX_OPEN_FRAME_TIME_MS = 22L;
   private static final float CATEGORY_GROUP_DELAY_STEP = 0.14F;
   private static final int PANEL_WIDTH = 125;
   private static final int PANEL_HEIGHT = 280;
   private static final int PANEL_MARGIN = 8;
   private static final float TOP_OFFSET = 90.0F;
   private static final float BOTTOM_OFFSET = 60.0F;
   private static final float HINT_MARGIN = 8.0F;
   private static final float HINT_LINE_GAP = 2.0F;
   private static final float HINT_OUTLINE_OFFSET = 0.45F;
   private float openProgress;
   private long lastOpenFrameTimeMs;
   private static ClickGUI INSTANCE = new ClickGUI();

   public ClickGUI() {
      super(Text.of("NewClickGUI"));
      windows = Lists.newArrayList();
      this.setInstance();
   }

   public static ClickGUI getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ClickGUI();
      }

      imageDirection = true;
      return INSTANCE;
   }

   public static ClickGUI getClickGui() {
      return getInstance();
   }

   public static void closeModuleSettingsExcept(ModuleButton openButton) {
      if (windows != null) {
         for (AbstractCategory window : windows) {
            if (window instanceof Category category) {
               category.closeModuleSettingsExcept(openButton);
            }
         }
      }
   }

   private void setInstance() {
      INSTANCE = this;
   }

   public void onDisplayed() {
      this.prepareOpen();
   }

   protected void init() {
      this.prepareWindows();
      this.layoutWindowsAtRest();
      this.syncWindowState();
   }

   public boolean shouldPause() {
      return false;
   }

   public void tick() {
      windows.forEach(AbstractCategory::tick);
      this.searchBar.tick();
      this.themeSelector.tick();
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.updateOpenAnimation();
      if (ModuleManager.clickGui.blur.getValue()) {
         this.applyBlur(delta);
      }

      anyHovered = false;
      descriptionActive = false;
      currentDescription = "";
      if (Module.fullNullCheck()) {
         this.renderBackground(context, mouseX, mouseY, delta);
      }

      List<Module.Category> visibleCategories = Lists.newArrayList(Managers.MODULE.getCategories());
      int totalWidth = visibleCategories.size() * 133 - 8;
      int startX = (Module.mc.getWindow().getScaledWidth() - totalWidth) / 2;
      int startY = (Module.mc.getWindow().getScaledHeight() - 280) / 2;
      float anim = this.getOpenProgress();
      float rawAnim = this.getRawOpenProgress();
      int i = 0;

      for (AbstractCategory w : windows) {
         float targetX = startX + i * 133;
         float targetY = startY;
         float progress = this.getCategoryOpenProgress(rawAnim, w.getName());
         float invProgress = 1.0F - progress;
         float offsetY = -90.0F * invProgress;
         w.setX(targetX);
         w.setY(targetY + offsetY);
         i++;
      }

      for (AbstractCategory window : windows) {
         if (this.scrollY != 0.0F) {
            window.setModuleOffset(this.scrollY, mouseX, mouseY);
         }
      }

      this.scrollY = 0.0F;

      for (AbstractCategory w : windows) {
         float progress = this.getCategoryOpenProgress(rawAnim, w.getName());
         if (!(progress <= 0.0F)) {
            float alpha = MathHelper.clamp(progress, 0.0F, 1.0F);
            Render2DEngine.setGlobalAlpha(alpha);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            w.render(context, mouseX, mouseY, delta);
            Render2DEngine.setGlobalAlpha(1.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         }
      }

      Render2DEngine.setGlobalAlpha(anim);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, anim);
      this.renderBottomBar(context, mouseX, mouseY, delta, anim);
      Render2DEngine.setGlobalAlpha(1.0F);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      this.renderInteractionHints(context.getMatrices());
      boolean fullyOpen = anim >= 0.999F && imageDirection;
      if (fullyOpen && descriptionActive && !Objects.equals(currentDescription, "")) {
         float paddingX = 8.0F;
         float paddingY = 4.0F;
         float textWidth = FontRenderers.sf_medium.getStringWidth(currentDescription);
         float textHeight = FontRenderers.sf_medium.getFontHeight(currentDescription);
         float descWidth = Math.max(60.0F, textWidth + paddingX * 2.0F);
         float descHeight = textHeight + paddingY * 2.0F;
         float descY = startY - descHeight - 10.0F;
         float descX = (Module.mc.getWindow().getScaledWidth() - descWidth) / 2.0F;
         float textY = descY + (descHeight - textHeight) / 2.0F + 3.0F;
         Color bg = new Color(25, 25, 28, 200);
         Render2DEngine.drawClickGuiRound(context.getMatrices(), descX, descY, descWidth, descHeight, descHeight / 2.0F, bg);
         FontRenderers.sf_medium.drawCenteredString(context.getMatrices(), currentDescription, descX + descWidth / 2.0F, textY, Color.WHITE.getRGB());
      }

      if (!HudElement.anyHovered && !anyHovered && GLFW.glfwGetPlatform() != 393219) {
         GLFW.glfwSetCursor(Module.mc.getWindow().getHandle(), GLFW.glfwCreateStandardCursor(221185));
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scrollY += (int)(verticalAmount * 15.0);
      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (Render2DEngine.isHovered((float)mouseX, (float)mouseY, this.managerBtnX, this.managerBtnY, this.managerBtnSize, this.managerBtnSize) && button == 0) {
         Module.mc.setScreen(new ManagerScreen(this));
         return true;
      } else {
         this.searchBar.mouseClicked((int)mouseX, (int)mouseY, button);
         this.themeSelector.mouseClicked((int)mouseX, (int)mouseY, button);
         windows.forEach(w -> {
            w.mouseClicked((int)mouseX, (int)mouseY, button);
            windows.forEach(w1 -> {
               if (w.dragging && w != w1) {
                  w1.dragging = false;
               }
            });
         });
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.searchBar.mouseReleased((int)mouseX, (int)mouseY, button);
      this.themeSelector.mouseReleased((int)mouseX, (int)mouseY, button);
      windows.forEach(w -> w.mouseReleased((int)mouseX, (int)mouseY, button));
      return super.mouseReleased(mouseX, mouseY, button);
   }

   private void renderBottomBar(DrawContext context, int mouseX, int mouseY, float delta, float anim) {
      float searchWidth = Math.min(180.0F, Module.mc.getWindow().getScaledWidth() - 40.0F);
      float searchHeight = 20.0F;
      float searchMarginBottom = 10.0F;
      float themeButtonSize = 16.0F;
      float gap = 5.0F;
      float themeHeight = 16.0F;
      float themeMarginBottom = 40.0F;
      float searchX = (Module.mc.getWindow().getScaledWidth() - searchWidth) / 2.0F;
      float searchY = (Module.mc.getWindow().getScaledHeight() + 280) / 2.0F + searchMarginBottom;
      float buttonX = searchX + searchWidth + gap;
      float buttonY = searchY + (searchHeight - themeButtonSize) / 2.0F;
      float themeWidth = searchWidth;
      float paletteX = (Module.mc.getWindow().getScaledWidth() - themeWidth) / 2.0F;
      float paletteY = (Module.mc.getWindow().getScaledHeight() + 280) / 2.0F + themeMarginBottom;
      float bottomOffset = 60.0F * (1.0F - anim);
      searchY += bottomOffset;
      buttonY += bottomOffset;
      paletteY += bottomOffset;
      this.searchBar.setX(searchX);
      this.searchBar.setY(searchY);
      this.searchBar.setWidth(searchWidth);
      this.searchBar.setHeight(searchHeight);
      this.searchBar.render(context, mouseX, mouseY, delta);
      this.themeSelector.setLayout(buttonX, buttonY, themeButtonSize, paletteX, themeWidth, paletteY, themeHeight);
      this.themeSelector.render(context, mouseX, mouseY, delta);
      this.managerBtnX = buttonX + themeButtonSize + gap;
      this.managerBtnY = buttonY;
      this.managerBtnSize = themeButtonSize;
      boolean managerHovered = Render2DEngine.isHovered(mouseX, mouseY, this.managerBtnX, this.managerBtnY, this.managerBtnSize, this.managerBtnSize);
      int hudAlpha = Math.round(255.0F * HudEditor.getAlpha());
      Color btnBg = Render2DEngine.injectAlpha(new Color(25, 25, 28), hudAlpha);
      Render2DEngine.drawClickGuiRound(
         context.getMatrices(), this.managerBtnX, this.managerBtnY, this.managerBtnSize, this.managerBtnSize, this.managerBtnSize / 4.0F, btnBg
      );
      Color iconCol = Render2DEngine.injectAlpha(Color.WHITE, hudAlpha);
      FontRenderers.sf_bold_mini
         .drawString(
            context.getMatrices(),
            "...",
            this.managerBtnX + this.managerBtnSize / 2.0F - FontRenderers.sf_bold_mini.getStringWidth("...") / 2.0F,
            this.managerBtnY + this.managerBtnSize / 2.0F - FontRenderers.sf_bold_mini.getFontHeight("...") / 2.0F + 1.0F,
            iconCol.getRGB()
         );
      if (managerHovered) {
         anyHovered = true;
      }

      if (this.themeSelector.shouldRenderPalette()) {
         this.themeSelector.renderPalette(context, mouseX, mouseY, delta, paletteX, themeWidth, paletteY, themeHeight);
      }
   }

   private void renderInteractionHints(MatrixStack matrices) {
      if (Module.mc.getWindow() != null) {
         FontRenderer font = FontRenderers.sf_bold_mini;
         List<String> hints = this.getInteractionHints(font);
         float lineHeight = font.getFontHeight("A") + 2.0F;
         float startY = Module.mc.getWindow().getScaledHeight() - 8.0F - lineHeight * hints.size();
         float startX = 8.0F;

         for (int i = 0; i < hints.size(); i++) {
            this.drawOutlinedHintText(matrices, font, hints.get(i), startX, startY + i * lineHeight);
         }
      }
   }

   private List<String> getInteractionHints(FontRenderer font) {
      return List.of(
            "Left Click: Enable/Disable modules",
            "Right Click: Open setting",
            "Mid Click: Change bind",
            "Del + Left Click: Reset modules",
            "Ctrl + F: Search modules"
         )
         .stream()
         .sorted((left, right) -> Float.compare(font.getStringWidth(left), font.getStringWidth(right)))
         .toList();
   }

   private void drawOutlinedHintText(MatrixStack matrices, FontRenderer font, String text, float x, float y) {
      int outlineColor = Render2DEngine.injectAlpha(Color.BLACK, 255).getRGB();
      int fillColor = Render2DEngine.injectAlpha(Color.WHITE, 255).getRGB();
      font.drawString(matrices, text, x - 0.45F, y, outlineColor);
      font.drawString(matrices, text, x + 0.45F, y, outlineColor);
      font.drawString(matrices, text, x, y - 0.45F, outlineColor);
      font.drawString(matrices, text, x, y + 0.45F, outlineColor);
      font.drawString(matrices, text, x, y, fillColor);
   }

   public boolean charTyped(char chr, int modifiers) {
      this.searchBar.charTyped(chr, modifiers);
      windows.forEach(w -> w.charTyped(chr, modifiers));
      return true;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      this.searchBar.keyTyped(keyCode);
      windows.forEach(w -> w.keyTyped(keyCode));
      if (keyCode == 256) {
         imageDirection = false;
         Module.mc.setScreen(null);
         return true;
      } else {
         return false;
      }
   }

   private float getOpenProgress() {
      return MathHelper.clamp((float)Easing.EASE_OUT_CIRC.apply(this.openProgress), 0.0F, 1.0F);
   }

   private float getRawOpenProgress() {
      return MathHelper.clamp(this.openProgress, 0.0F, 1.0F);
   }

   private void resetOpenAnimation() {
      this.openProgress = 0.0F;
      this.lastOpenFrameTimeMs = System.currentTimeMillis();
   }

   private void updateOpenAnimation() {
      long now = System.currentTimeMillis();
      long elapsed = Math.min(Math.max(0L, now - this.lastOpenFrameTimeMs), 22L);
      this.lastOpenFrameTimeMs = now;
      if (!(this.openProgress >= 1.0F)) {
         this.openProgress = MathHelper.clamp(this.openProgress + (float)elapsed / 460.0F, 0.0F, 1.0F);
      }
   }

   private void prepareOpen() {
      SearchBar.resetState();
      this.prepareWindows();
      this.layoutWindowsAtRest();
      this.syncWindowState();
      this.resetOpenAnimation();
      imageDirection = true;
   }

   private void prepareWindows() {
      List<Module.Category> visibleCategories = Lists.newArrayList(Managers.MODULE.getCategories());
      if (this.shouldRebuildWindows(visibleCategories)) {
         windows.clear();

         for (Module.Category category : visibleCategories) {
            Category window = new Category(category, Managers.MODULE.getModulesByCategory(category), 0.0F, 0.0F, 125.0F, 20.0F);
            window.setOpen(true);
            windows.add(window);
         }
      }
   }

   private boolean shouldRebuildWindows(List<Module.Category> visibleCategories) {
      if (windows.size() != visibleCategories.size()) {
         return true;
      }

      for (int i = 0; i < visibleCategories.size(); i++) {
         if (!Objects.equals(windows.get(i).getName(), visibleCategories.get(i).getName())) {
            return true;
         }
      }

      return false;
   }

   private void layoutWindowsAtRest() {
      List<Module.Category> visibleCategories = Lists.newArrayList(Managers.MODULE.getCategories());
      int totalWidth = visibleCategories.size() * 133 - 8;
      int startX = (Module.mc.getWindow().getScaledWidth() - totalWidth) / 2;
      int startY = (Module.mc.getWindow().getScaledHeight() - 280) / 2;
      int i = 0;

      for (AbstractCategory window : windows) {
         window.setX(startX + i * 133);
         window.setY(startY);
         window.setHeight(280.0F);
         i++;
      }
   }

   private void syncWindowState() {
      windows.forEach(AbstractCategory::init);
      windows.forEach(w -> {
         if (w instanceof Category category) {
            category.snapButtonOffsets();
         }
      });
   }

   private float getCategoryOpenProgress(float progress, String name) {
      float delay = this.getCategoryGroupDelay(name);
      if (progress <= delay) {
         return 0.0F;
      }

      float delayedProgress = MathHelper.clamp((progress - delay) / (1.0F - delay), 0.0F, 1.0F);
      return MathHelper.clamp((float)Easing.EASE_OUT_CIRC.apply(delayedProgress), 0.0F, 1.0F);
   }

   private float getCategoryGroupDelay(String name) {
      if (name == null) {
         return 0.0F;
      } else if ("Movement".equalsIgnoreCase(name) || "Player".equalsIgnoreCase(name)) {
         return 0.14F;
      } else {
         return !"Combat".equalsIgnoreCase(name) && !"Misc".equalsIgnoreCase(name) ? 0.0F : 0.28F;
      }
   }

   public static void requestDescription(String description) {
      currentDescription = description;
      descriptionActive = true;
   }
}
