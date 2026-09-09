package vcore.utility.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL30;

public class MSAAFramebuffer extends Framebuffer {
   public static final int MAX_SAMPLES = GL30.glGetInteger(36183);
   private static final Map<Integer, MSAAFramebuffer> INSTANCES = new HashMap<>();
   private final int samples;
   private int rboColor;
   private int rboDepth;

   public MSAAFramebuffer(int samples) {
      super(true);
      this.samples = samples;
      this.setClearColor(1.0F, 1.0F, 1.0F, 0.0F);
   }

   public static MSAAFramebuffer getInstance(int samples) {
      return INSTANCES.computeIfAbsent(samples, x -> new MSAAFramebuffer(samples));
   }

   public static void use(boolean fancy, Runnable drawAction) {
      use(Math.min(fancy ? 16 : 4, MAX_SAMPLES), MinecraftClient.getInstance().getFramebuffer(), drawAction);
   }

   public static void use(int samples, @NotNull Framebuffer mainBuffer, @NotNull Runnable drawAction) {
      RenderSystem.assertOnRenderThreadOrInit();
      MSAAFramebuffer msaaBuffer = getInstance(samples);
      msaaBuffer.resize(mainBuffer.textureWidth, mainBuffer.textureHeight, false);
      GlStateManager._glBindFramebuffer(36008, mainBuffer.fbo);
      GlStateManager._glBindFramebuffer(36009, msaaBuffer.fbo);
      GlStateManager._glBlitFrameBuffer(
         0, 0, msaaBuffer.textureWidth, msaaBuffer.textureHeight, 0, 0, msaaBuffer.textureWidth, msaaBuffer.textureHeight, 16384, 9729
      );
      msaaBuffer.beginWrite(true);
      drawAction.run();
      msaaBuffer.endWrite();
      GlStateManager._glBindFramebuffer(36008, msaaBuffer.fbo);
      GlStateManager._glBindFramebuffer(36009, mainBuffer.fbo);
      GlStateManager._glBlitFrameBuffer(
         0, 0, msaaBuffer.textureWidth, msaaBuffer.textureHeight, 0, 0, msaaBuffer.textureWidth, msaaBuffer.textureHeight, 16384, 9729
      );
      msaaBuffer.clear(false);
      mainBuffer.beginWrite(false);
   }

   public void resize(int width, int height, boolean getError) {
      if (this.textureWidth != width || this.textureHeight != height) {
         super.resize(width, height, getError);
      }
   }

   public void initFbo(int width, int height, boolean getError) {
      RenderSystem.assertOnRenderThreadOrInit();
      this.viewportWidth = width;
      this.viewportHeight = height;
      this.textureWidth = width;
      this.textureHeight = height;
      this.fbo = GlStateManager.glGenFramebuffers();
      GlStateManager._glBindFramebuffer(36160, this.fbo);
      this.rboColor = GlStateManager.glGenRenderbuffers();
      GlStateManager._glBindRenderbuffer(36161, this.rboColor);
      GL30.glRenderbufferStorageMultisample(36161, this.samples, 32856, width, height);
      GlStateManager._glBindRenderbuffer(36161, 0);
      this.rboDepth = GlStateManager.glGenRenderbuffers();
      GlStateManager._glBindRenderbuffer(36161, this.rboDepth);
      GL30.glRenderbufferStorageMultisample(36161, this.samples, 6402, width, height);
      GlStateManager._glBindRenderbuffer(36161, 0);
      GL30.glFramebufferRenderbuffer(36160, 36064, 36161, this.rboColor);
      GL30.glFramebufferRenderbuffer(36160, 36096, 36161, this.rboDepth);
      this.colorAttachment = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
      this.depthAttachment = MinecraftClient.getInstance().getFramebuffer().getDepthAttachment();
      this.checkFramebufferStatus();
      this.clear(getError);
      this.endRead();
   }

   public void delete() {
      RenderSystem.assertOnRenderThreadOrInit();
      this.endRead();
      this.endWrite();
      if (this.fbo > -1) {
         GlStateManager._glBindFramebuffer(36160, 0);
         GlStateManager._glDeleteFramebuffers(this.fbo);
         this.fbo = -1;
      }

      if (this.rboColor > -1) {
         GlStateManager._glDeleteRenderbuffers(this.rboColor);
         this.rboColor = -1;
      }

      if (this.rboDepth > -1) {
         GlStateManager._glDeleteRenderbuffers(this.rboDepth);
         this.rboDepth = -1;
      }

      this.colorAttachment = -1;
      this.depthAttachment = -1;
      this.textureWidth = -1;
      this.textureHeight = -1;
   }
}
