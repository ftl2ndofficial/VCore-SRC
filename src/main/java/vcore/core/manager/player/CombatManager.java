package vcore.core.manager.player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vcore.Vcore;
import vcore.core.Managers;
import vcore.core.manager.IManager;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventPostTick;
import vcore.events.impl.PacketEvent;
import vcore.events.impl.TotemPopEvent;
import vcore.features.modules.Module;
import vcore.features.modules.combat.AntiBot;

public class CombatManager implements IManager {
   public HashMap<String, Integer> popList = new HashMap<>();

   @EventHandler
   public void onPacketReceive(PacketEvent.Receive event) {
      if (!Module.fullNullCheck()) {
         if (event.getPacket() instanceof EntityStatusS2CPacket pac && pac.getStatus() == 35) {
            Entity ent = pac.getEntity(mc.world);
            if (!(ent instanceof PlayerEntity)) {
               return;
            }

            if (this.popList == null) {
               this.popList = new HashMap<>();
            }

            if (this.popList.get(ent.getName().getString()) == null) {
               this.popList.put(ent.getName().getString(), 1);
            } else if (this.popList.get(ent.getName().getString()) != null) {
               this.popList.put(ent.getName().getString(), this.popList.get(ent.getName().getString()) + 1);
            }

            Vcore.EVENT_BUS.post(new TotemPopEvent((PlayerEntity)ent, this.popList.get(ent.getName().getString())));
         }
      }
   }

   @EventHandler
   public void onPostTick(EventPostTick event) {
      if (!Module.fullNullCheck()) {
         for (PlayerEntity player : mc.world.getPlayers()) {
            if ((!ModuleManager.antiBot.isEnabled() || ModuleManager.antiBot.mode.getValue() != AntiBot.Mode.Matrix || !AntiBot.isBot(player))
               && player.getHealth() <= 0.0F
               && this.popList.containsKey(player.getName().getString())) {
               this.popList.remove(player.getName().getString(), this.popList.get(player.getName().getString()));
            }
         }
      }
   }

   public int getPops(@NotNull PlayerEntity entity) {
      return this.popList.get(entity.getName().getString()) == null ? 0 : this.popList.get(entity.getName().getString());
   }

   public List<PlayerEntity> getTargets(float range) {
      return mc.world
         .getPlayers()
         .stream()
         .filter(e -> !e.isDead())
         .filter(entityPlayer -> !Managers.FRIEND.isFriend(entityPlayer.getName().getString()))
         .filter(entityPlayer -> entityPlayer != mc.player)
         .filter(entityPlayer -> mc.player.squaredDistanceTo(entityPlayer) < range * range)
         .sorted(Comparator.comparing(e -> mc.player.squaredDistanceTo(e)))
         .collect(Collectors.toList());
   }

   @Nullable
   public PlayerEntity getTarget(float range, @NotNull CombatManager.TargetBy targetBy) {
      PlayerEntity target = null;
      switch (targetBy) {
         case Distance:
            target = this.getNearestTarget(range);
            break;
         case FOV:
            target = this.getTargetByFOV(range);
            break;
         case Health:
            target = this.getTargetByHealth(range);
      }

      return target;
   }

   @Nullable
   public PlayerEntity getNearestTarget(float range) {
      return this.getTargets(range).stream().min(Comparator.comparing(t -> mc.player.distanceTo(t))).orElse(null);
   }

   public PlayerEntity getTargetByHealth(float range) {
      return this.getTargets(range).stream().min(Comparator.comparing(t -> t.getHealth() + t.getAbsorptionAmount())).orElse(null);
   }

   public PlayerEntity getTargetByFOV(float range) {
      return this.getTargets(range).stream().min(Comparator.comparing(this::getFOVAngle)).orElse(null);
   }

   public PlayerEntity getTargetByFOV(float range, float fov) {
      return this.getTargets(range)
         .stream()
         .filter(entityPlayer -> this.getFOVAngle(entityPlayer) < fov)
         .min(Comparator.comparing(this::getFOVAngle))
         .orElse(null);
   }

   @Nullable
   public PlayerEntity getTarget(float range, @NotNull CombatManager.TargetBy targetBy, @NotNull Predicate<PlayerEntity> predicate) {
      PlayerEntity target = null;
      switch (targetBy) {
         case Distance:
            target = this.getNearestTarget(range, predicate);
            break;
         case FOV:
            target = this.getTargetByFOV(range, predicate);
            break;
         case Health:
            target = this.getTargetByHealth(range, predicate);
      }

      return target;
   }

   @Nullable
   public PlayerEntity getNearestTarget(float range, Predicate<PlayerEntity> predicate) {
      return this.getTargets(range).stream().filter(predicate).min(Comparator.comparing(t -> mc.player.distanceTo(t))).orElse(null);
   }

   public PlayerEntity getTargetByHealth(float range, Predicate<PlayerEntity> predicate) {
      return this.getTargets(range).stream().filter(predicate).min(Comparator.comparing(t -> t.getHealth() + t.getAbsorptionAmount())).orElse(null);
   }

   public PlayerEntity getTargetByFOV(float range, Predicate<PlayerEntity> predicate) {
      return this.getTargets(range).stream().filter(predicate).min(Comparator.comparing(this::getFOVAngle)).orElse(null);
   }

   private float getFOVAngle(@NotNull LivingEntity e) {
      float yaw = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(e.getZ() - mc.player.getZ(), e.getX() - mc.player.getX())) - 90.0);
      return Math.abs(yaw - MathHelper.wrapDegrees(mc.player.getYaw()));
   }

   public enum TargetBy {
      Distance,
      FOV,
      Health;
   }
}
