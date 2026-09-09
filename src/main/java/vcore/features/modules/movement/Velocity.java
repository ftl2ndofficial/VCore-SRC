package vcore.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.PacketEvent;
import vcore.features.modules.Module;
import vcore.injection.accesors.IClientPlayerEntity;
import vcore.injection.accesors.IExplosionS2CPacket;
import vcore.injection.accesors.ISPacketEntityVelocity;
import vcore.setting.Setting;
import vcore.utility.player.MovementUtility;

public class Velocity extends Module {
   public Setting<Boolean> onlyAura = new Setting<>("OnlyDuringAura", false);
   public Setting<Boolean> pauseInWater = new Setting<>("PauseInLiquids", false);
   public Setting<Boolean> explosions = new Setting<>("Explosions", true);
   public Setting<Boolean> cc = new Setting<>("PauseOnFlag", false);
   public Setting<Boolean> fire = new Setting<>("PauseOnFire", false);
   private final Setting<Velocity.modeEn> mode = new Setting<>("Mode", Velocity.modeEn.Cancel);
   public Setting<Float> vertical = new Setting<>("Vertical", 0.0F, 0.0F, 100.0F, v -> this.mode.getValue() == Velocity.modeEn.Custom);
   private final Setting<Velocity.jumpModeEn> jumpMode = new Setting<>("JumpMode", Velocity.jumpModeEn.Jump, v -> this.mode.getValue() == Velocity.modeEn.Jump);
   public Setting<Float> horizontal = new Setting<>(
      "Horizontal", 0.0F, 0.0F, 100.0F, v -> this.mode.getValue() == Velocity.modeEn.Custom || this.mode.getValue() == Velocity.modeEn.Jump
   );
   public Setting<Float> motion = new Setting<>("Motion", 0.42F, 0.4F, 0.5F, v -> this.mode.getValue() == Velocity.modeEn.Jump);
   public Setting<Boolean> fail = new Setting<>("SmartFail", true, v -> this.mode.getValue() == Velocity.modeEn.Jump);
   public Setting<Float> failRate = new Setting<>("FailRate", 0.3F, 0.0F, 1.0F, v -> this.mode.getValue() == Velocity.modeEn.Jump && this.fail.getValue());
   public Setting<Float> jumpRate = new Setting<>("FailJumpRate", 0.25F, 0.0F, 1.0F, v -> this.mode.getValue() == Velocity.modeEn.Jump && this.fail.getValue());
   private boolean doJump;
   private boolean failJump;
   private boolean skip;
   private boolean flag;
   private int grimTicks;
   private int ccCooldown;

   public Velocity() {
      super("Velocity", "Prevents knockback.", Module.Category.MOVEMENT);
   }

   @EventHandler
   public void onPacketReceive(PacketEvent.Receive e) {
      if (!fullNullCheck()) {
         if (mc.player == null || !mc.player.isTouchingWater() && !mc.player.isSubmergedInWater() && !mc.player.isInLava() || !this.pauseInWater.getValue()) {
            if (mc.player == null || !mc.player.isOnFire() || !this.fire.getValue() || mc.player.hurtTime <= 0) {
               if (this.ccCooldown > 0) {
                  this.ccCooldown--;
               } else {
                  if (e.getPacket() instanceof EntityVelocityUpdateS2CPacket pac
                     && pac.getEntityId() == mc.player.getId()
                     && (!this.onlyAura.getValue() || ModuleManager.aura.isEnabled())) {
                     switch ((Velocity.modeEn)this.mode.getValue()) {
                        case Matrix:
                           if (!this.flag) {
                              e.cancel();
                              this.flag = true;
                           } else {
                              this.flag = false;
                              ((ISPacketEntityVelocity)pac).setMotionX((int)(pac.getVelocityX() * -0.1));
                              ((ISPacketEntityVelocity)pac).setMotionZ((int)(pac.getVelocityZ() * -0.1));
                           }
                           break;
                        case Cancel:
                           e.cancel();
                           break;
                        case Sunrise:
                           e.cancel();
                           this.sendPacket(new PositionAndOnGround(mc.player.getX(), -999.0, mc.player.getZ(), true));
                           break;
                        case Custom:
                           ((ISPacketEntityVelocity)pac).setMotionX((int)((float)pac.getVelocityX() * this.horizontal.getValue() / 100.0F));
                           ((ISPacketEntityVelocity)pac).setMotionY((int)((float)pac.getVelocityY() * this.vertical.getValue() / 100.0F));
                           ((ISPacketEntityVelocity)pac).setMotionZ((int)((float)pac.getVelocityZ() * this.horizontal.getValue() / 100.0F));
                           break;
                        case Redirect:
                           double vX = Math.abs(pac.getVelocityX());
                           double vZ = Math.abs(pac.getVelocityZ());
                           double[] motion = MovementUtility.forward(vX + vZ);
                           ((ISPacketEntityVelocity)pac).setMotionX((int)motion[0]);
                           ((ISPacketEntityVelocity)pac).setMotionY(0);
                           ((ISPacketEntityVelocity)pac).setMotionZ((int)motion[1]);
                           break;
                        case OldGrim:
                           e.cancel();
                           this.grimTicks = 6;
                           break;
                        case Jump:
                           ((ISPacketEntityVelocity)pac).setMotionX((int)((float)pac.getVelocityX() * this.horizontal.getValue() / 100.0F));
                           ((ISPacketEntityVelocity)pac).setMotionZ((int)((float)pac.getVelocityZ() * this.horizontal.getValue() / 100.0F));
                           break;
                        case GrimNew:
                           e.cancel();
                           this.flag = true;
                     }
                  }

                  if (e.getPacket() instanceof ExplosionS2CPacket explosion && this.explosions.getValue()) {
                     switch ((Velocity.modeEn)this.mode.getValue()) {
                        case Cancel:
                           ((IExplosionS2CPacket)explosion).setMotionX(0.0F);
                           ((IExplosionS2CPacket)explosion).setMotionY(0.0F);
                           ((IExplosionS2CPacket)explosion).setMotionZ(0.0F);
                           break;
                        case Custom:
                           ((IExplosionS2CPacket)explosion).setMotionX(((IExplosionS2CPacket)explosion).getMotionX() * this.horizontal.getValue() / 100.0F);
                           ((IExplosionS2CPacket)explosion).setMotionZ(((IExplosionS2CPacket)explosion).getMotionZ() * this.horizontal.getValue() / 100.0F);
                           ((IExplosionS2CPacket)explosion).setMotionY(((IExplosionS2CPacket)explosion).getMotionY() * this.vertical.getValue() / 100.0F);
                           break;
                        case GrimNew:
                           ((IExplosionS2CPacket)explosion).setMotionX(0.0F);
                           ((IExplosionS2CPacket)explosion).setMotionY(0.0F);
                           ((IExplosionS2CPacket)explosion).setMotionZ(0.0F);
                           this.flag = true;
                     }
                  }

                  if (this.mode.getValue() == Velocity.modeEn.OldGrim && e.getPacket() instanceof CommonPingS2CPacket && this.grimTicks > 0) {
                     e.cancel();
                     this.grimTicks--;
                  }

                  if (e.getPacket() instanceof PlayerPositionLookS2CPacket && (this.cc.getValue() || this.mode.getValue() == Velocity.modeEn.GrimNew)) {
                     this.ccCooldown = 5;
                  }
               }
            }
         }
      }
   }

   @Override
   public void onUpdate() {
      if (mc.player == null || !mc.player.isTouchingWater() && !mc.player.isSubmergedInWater() || !this.pauseInWater.getValue()) {
         switch ((Velocity.modeEn)this.mode.getValue()) {
            case Matrix:
               if (mc.player.hurtTime > 0 && !mc.player.isOnGround()) {
                  double var3 = mc.player.getYaw() * (float) (Math.PI / 180.0);
                  double var5 = Math.sqrt(mc.player.getVelocity().x * mc.player.getVelocity().x + mc.player.getVelocity().z * mc.player.getVelocity().z);
                  mc.player.setVelocity(-Math.sin(var3) * var5, mc.player.getVelocity().y, Math.cos(var3) * var5);
                  mc.player.setSprinting(mc.player.age % 2 != 0);
               }
               break;
            case Jump:
               if ((this.failJump || mc.player.hurtTime > 6) && mc.player.isOnGround()) {
                  if (this.failJump) {
                     this.failJump = false;
                  }

                  if (!this.doJump) {
                     this.skip = true;
                  }

                  if (Math.random() <= this.failRate.getValue().floatValue() && this.fail.getValue()) {
                     if (Math.random() <= this.jumpRate.getValue().floatValue()) {
                        this.doJump = true;
                        this.failJump = true;
                     } else {
                        this.doJump = false;
                        this.failJump = false;
                     }
                  } else {
                     this.doJump = true;
                     this.failJump = false;
                  }

                  if (this.skip) {
                     this.skip = false;
                     return;
                  }

                  switch ((Velocity.jumpModeEn)this.jumpMode.getValue()) {
                     case Motion:
                        mc.player.setVelocity(mc.player.getVelocity().getX(), this.motion.getValue().floatValue(), mc.player.getVelocity().getZ());
                        break;
                     case Jump:
                        mc.player.jump();
                        break;
                     case Both:
                        mc.player.jump();
                        mc.player.setVelocity(mc.player.getVelocity().getX(), this.motion.getValue().floatValue(), mc.player.getVelocity().getZ());
                  }
               }
               break;
            case GrimNew:
               if (this.flag) {
                  if (this.ccCooldown <= 0) {
                     this.sendPacket(
                        new Full(
                           mc.player.getX(),
                           mc.player.getY(),
                           mc.player.getZ(),
                           ((IClientPlayerEntity)mc.player).getLastYaw(),
                           ((IClientPlayerEntity)mc.player).getLastPitch(),
                           mc.player.isOnGround()
                        )
                     );
                     this.sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, BlockPos.ofFloored(mc.player.getPos()), Direction.DOWN));
                  }

                  this.flag = false;
               }
         }

         if (this.grimTicks > 0) {
            this.grimTicks--;
         }
      }
   }

   private boolean isValidMotion(double motion, double min, double max) {
      return Math.abs(motion) > min && Math.abs(motion) < max;
   }

   @Override
   public void onEnable() {
      this.grimTicks = 0;
   }

   public enum jumpModeEn {
      Motion,
      Jump,
      Both;
   }

   public enum modeEn {
      Matrix,
      Cancel,
      Sunrise,
      Custom,
      Redirect,
      OldGrim,
      Jump,
      GrimNew;
   }
}
