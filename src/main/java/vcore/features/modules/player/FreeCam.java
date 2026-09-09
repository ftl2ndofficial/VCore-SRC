package vcore.features.modules.player;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import vcore.events.impl.EventAttack;
import vcore.events.impl.EventKeyboardInput;
import vcore.events.impl.EventMouse;
import vcore.events.impl.EventMove;
import vcore.events.impl.EventSync;
import vcore.events.impl.PacketEvent;
import vcore.features.modules.Module;
import vcore.setting.Setting;
import vcore.utility.player.MovementUtility;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.Render3DEngine;

public class FreeCam extends Module {
   private final Setting<Float> speed = new Setting<>("HSpeed", 0.75F, 0.1F, 3.0F);
   private final Setting<Float> hspeed = new Setting<>("VSpeed", 0.5F, 0.1F, 3.0F);
   private final Setting<Boolean> freeze = new Setting<>("Freeze", false);
   public final Setting<Boolean> track = new Setting<>("Track", false);
   private float fakeYaw;
   private float fakePitch;
   private float prevFakeYaw;
   private float prevFakePitch;
   private float prevScroll;
   private double fakeX;
   private double fakeY;
   private double fakeZ;
   private double prevFakeX;
   private double prevFakeY;
   private double prevFakeZ;
   public LivingEntity trackEntity;

   public FreeCam() {
      super("FreeCam", "Camera detached from player.", Module.Category.RENDER);
   }

   @Override
   public void onEnable() {
      mc.chunkCullingEnabled = false;
      this.trackEntity = null;
      this.fakePitch = mc.player.getPitch();
      this.fakeYaw = mc.player.getYaw();
      this.prevFakePitch = this.fakePitch;
      this.prevFakeYaw = this.fakeYaw;
      this.fakeX = mc.player.getX();
      this.fakeY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
      this.fakeZ = mc.player.getZ();
      this.prevFakeX = mc.player.getX();
      this.prevFakeY = mc.player.getY();
      this.prevFakeZ = mc.player.getZ();
   }

   @EventHandler
   public void onAttack(EventAttack e) {
      if (!e.isPre() && e.getEntity() instanceof LivingEntity entity && this.track.getValue()) {
         this.trackEntity = entity;
      }
   }

   @Override
   public void onDisable() {
      if (!fullNullCheck()) {
         mc.chunkCullingEnabled = true;
      }
   }

   @EventHandler(priority = 100)
   public void onSync(EventSync e) {
      this.prevFakeYaw = this.fakeYaw;
      this.prevFakePitch = this.fakePitch;
      if (this.isKeyPressed(256) || this.isKeyPressed(340) || this.isKeyPressed(344)) {
         this.trackEntity = null;
      }

      if (this.trackEntity != null) {
         this.fakeYaw = this.trackEntity.getYaw();
         this.fakePitch = this.trackEntity.getPitch();
         this.prevFakeX = this.fakeX;
         this.prevFakeY = this.fakeY;
         this.prevFakeZ = this.fakeZ;
         this.fakeX = this.trackEntity.getX();
         this.fakeY = this.trackEntity.getY() + this.trackEntity.getEyeHeight(this.trackEntity.getPose());
         this.fakeZ = this.trackEntity.getZ();
      } else {
         this.fakeYaw = mc.player.getYaw();
         this.fakePitch = mc.player.getPitch();
      }
   }

   @EventHandler
   public void onKeyboardInput(EventKeyboardInput e) {
      if (mc.player != null) {
         if (this.trackEntity == null) {
            double[] motion = MovementUtility.forward(this.speed.getValue().floatValue());
            this.prevFakeX = this.fakeX;
            this.prevFakeY = this.fakeY;
            this.prevFakeZ = this.fakeZ;
            this.fakeX = this.fakeX + motion[0];
            this.fakeZ = this.fakeZ + motion[1];
            if (mc.options.jumpKey.isPressed()) {
               this.fakeY = this.fakeY + this.hspeed.getValue().floatValue();
            }

            if (mc.options.sneakKey.isPressed()) {
               this.fakeY = this.fakeY - this.hspeed.getValue().floatValue();
            }
         }

         mc.player.input.movementForward = 0.0F;
         mc.player.input.movementSideways = 0.0F;
         mc.player.input.jumping = false;
         mc.player.input.sneaking = false;
      }
   }

   @EventHandler(priority = -100)
   public void onMove(EventMove e) {
      if (this.freeze.getValue()) {
         e.setX(0.0);
         e.setY(0.0);
         e.setZ(0.0);
         e.cancel();
      }
   }

   @EventHandler
   public void onPacketSend(PacketEvent.Send e) {
      if (this.freeze.getValue() && e.getPacket() instanceof PlayerMoveC2SPacket) {
         e.cancel();
      }
   }

   @EventHandler
   public void onScroll(EventMouse e) {
      if (e.getAction() == 2) {
         if (e.getButton() > 0) {
            this.speed.setValue(this.speed.getValue() + 0.05F);
         } else {
            this.speed.setValue(this.speed.getValue() - 0.05F);
         }

         this.prevScroll = e.getButton();
      }
   }

   public float getFakeYaw() {
      return (float)Render2DEngine.interpolate(this.prevFakeYaw, this.fakeYaw, Render3DEngine.getTickDelta());
   }

   public float getFakePitch() {
      return (float)Render2DEngine.interpolate(this.prevFakePitch, this.fakePitch, Render3DEngine.getTickDelta());
   }

   public double getFakeX() {
      return Render2DEngine.interpolate(this.prevFakeX, this.fakeX, Render3DEngine.getTickDelta());
   }

   public double getFakeY() {
      return Render2DEngine.interpolate(this.prevFakeY, this.fakeY, Render3DEngine.getTickDelta());
   }

   public double getFakeZ() {
      return Render2DEngine.interpolate(this.prevFakeZ, this.fakeZ, Render3DEngine.getTickDelta());
   }
}
