package vcore.features.modules.render.frageffects;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.LineEvent.Type;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import vcore.utility.Timer;

public final class FragCharmSfx {
   private static final String RESOURCE_PATH = "/assets/vcore/sounds/";
   private static final String FOLDER = "charmsfxpack/";
   private static final String FORMAT = ".wav";
   private final List<FragCharmSfx.TimedRunnableRunner> timedRuns = new ArrayList<>();
   private final Random random = new Random();
   private AudioFormat previousFormat;
   private Info lastData;

   private FragCharmSfx() {
   }

   public static FragCharmSfx create() {
      return new FragCharmSfx();
   }

   public void sfxPulseDistantRand(long timeWait) {
      this.silentPlay("charmsfxpack/pulsecharmdistant" + this.random.nextInt(5) + ".wav", 1.0F, timeWait);
   }

   public void sfxKnockMain(long timeWait) {
      this.silentPlay("charmsfxpack/knockcharmmain.wav", 1.0F, timeWait);
   }

   public void sfxEchoMain(long timeWait) {
      this.silentPlay("charmsfxpack/echocharmmain.wav", 0.6F, timeWait);
   }

   public void sfxSparksBlockHit(long timeWait) {
      this.silentPlay("charmsfxpack/sparkscollisionneared.wav", 0.2F, timeWait);
   }

   public void inRenderThreadUpdate() {
      if (!this.timedRuns.isEmpty()) {
         for (FragCharmSfx.TimedRunnableRunner runner : this.timedRuns) {
            runner.update();
         }

         this.timedRuns.removeIf(FragCharmSfx.TimedRunnableRunner::removeIf);
      }
   }

   public void clear() {
      this.timedRuns.clear();
   }

   private void silentPlay(String filePostLoc, float volume, long timeWait) {
      this.timedRuns.add(new FragCharmSfx.TimedRunnableRunner(() -> this.playSoundInstant(filePostLoc, volume * this.getMinecraftMasterPC()), timeWait));
   }

   private float getMinecraftMasterPC() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc.options.getSoundVolume(SoundCategory.MASTER) / 1.3F;
   }

   private void playSoundInstant(String location, float volume) {
      if (System.getProperty("os.name").startsWith("Windows") && !(volume <= 0.0F)) {
         AudioInputStream stream = this.getAudioInputStreamAsResource("/assets/vcore/sounds/" + location);
         if (stream != null) {
            Clip clip = this.createClip(stream);
            if (clip == null) {
               this.closeQuietly(stream);
            } else {
               try {
                  clip.open(stream);
                  this.setClipVolume(clip, volume);
                  clip.addLineListener(event -> {
                     if (event.getType() == Type.STOP) {
                        clip.close();
                        this.closeQuietly(stream);
                     }
                  });
                  clip.start();
               } catch (LineUnavailableException | IOException exception) {
                  this.closeQuietly(stream);
               }
            }
         }
      }
   }

   private Clip createClip(AudioInputStream stream) {
      AudioFormat format = stream.getFormat();
      if (!Objects.equals(this.previousFormat, format)) {
         this.lastData = new Info(Clip.class, format);
         this.previousFormat = format;
      }

      try {
         return (Clip)AudioSystem.getLine(this.lastData);
      } catch (LineUnavailableException exception) {
         return null;
      }
   }

   private void setClipVolume(Clip clip, float volume) {
      if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
         FloatControl volumeControl = (FloatControl)clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
         float clamped = FragEffectMath.clamp(volume, 1.0E-4F, 1.0F);
         volumeControl.setValue((float)(Math.log(clamped) / Math.log(10.0) * 20.0));
      }
   }

   private AudioInputStream getAudioInputStreamAsResource(String path) {
      InputStream resourceStream = FragCharmSfx.class.getResourceAsStream(path);
      if (resourceStream == null) {
         return null;
      }

      try {
         return AudioSystem.getAudioInputStream(new BufferedInputStream(resourceStream));
      } catch (UnsupportedAudioFileException | IOException exception) {
         return null;
      }
   }

   private void closeQuietly(AudioInputStream stream) {
      try {
         stream.close();
      } catch (IOException var3) {
      }
   }

   private static final class TimedRunnableRunner {
      private final Timer timer = new Timer();
      private final long removePost;
      private Runnable runnable;

      private TimedRunnableRunner(Runnable runnable, long removePost) {
         this.runnable = runnable;
         this.removePost = removePost;
      }

      private void update() {
         if (this.timer.passedMs(this.removePost) && this.runnable != null) {
            this.runnable.run();
            this.runnable = null;
         }
      }

      private boolean removeIf() {
         return this.runnable == null || this.timer.passedMs(this.removePost);
      }
   }
}
