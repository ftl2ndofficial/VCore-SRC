package vcore.features.modules.render.frageffects;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vcore.core.Managers;
import vcore.injection.accesors.IClientPlayerEntity;
import vcore.utility.Timer;

public final class FragDeathMemory {
   private final long maxTimeMemory;
   private final Runnable memoryDeathTrigger;
   private final BooleanSupplier rotateToKilled;
   private final List<FragDeathMemory.EntityDeathMemory> deathMemories = new ArrayList<>();
   private LivingEntity lastKilledEntity;

   private FragDeathMemory(long maxTimeMemory, Runnable memoryDeathTrigger, BooleanSupplier rotateToKilled) {
      this.maxTimeMemory = maxTimeMemory;
      this.memoryDeathTrigger = memoryDeathTrigger;
      this.rotateToKilled = rotateToKilled;
   }

   public static FragDeathMemory create(long maxTimeMemory, Runnable memoryDeathTrigger, BooleanSupplier rotateToKilled) {
      return new FragDeathMemory(maxTimeMemory, memoryDeathTrigger, rotateToKilled);
   }

   public LivingEntity getLastKilledEntity() {
      return this.lastKilledEntity;
   }

   public void controllingAddingMemoryToEntity(LivingEntity baseTo, boolean mobDetect) {
      if (baseTo != null && !(baseTo instanceof ClientPlayerEntity) && baseTo.age >= 2 && baseTo.isAlive()) {
         FragDeathMemory.EntityDeathMemory searchedMemory = null;

         for (FragDeathMemory.EntityDeathMemory memory : this.deathMemories) {
            if (memory.isValidMemory() && memory.base.getId() == baseTo.getId()) {
               searchedMemory = memory;
               break;
            }
         }

         if (searchedMemory != null) {
            searchedMemory.resetMemory(baseTo, this.maxTimeMemory);
         } else if ((mobDetect || baseTo instanceof PlayerEntity) && this.deathMemories.isEmpty()) {
            this.deathMemories.add(new FragDeathMemory.EntityDeathMemory(baseTo, this.maxTimeMemory, this.memoryDeathTrigger));
         }
      }
   }

   public void removeAutoMemories() {
      this.deathMemories.removeIf(FragDeathMemory.EntityDeathMemory::isNotValidMemory);
   }

   public void updateAutoMemories() {
      for (FragDeathMemory.EntityDeathMemory memory : new ArrayList<>(this.deathMemories)) {
         memory.updateMemoryTrigger();
      }
   }

   public void onContains(LivingEntity living) {
      if (living != null) {
         Iterator<FragDeathMemory.EntityDeathMemory> iterator = this.deathMemories.iterator();

         while (iterator.hasNext()) {
            FragDeathMemory.EntityDeathMemory memory = iterator.next();
            if (memory.isValidMemory() && memory.base.getId() == living.getId()) {
               this.lastKilledEntity = memory.base;
               this.memoryDeathTrigger.run();
               this.rotateCameraToKilled(memory.base);
               iterator.remove();
               break;
            }
         }
      }
   }

   public void clear() {
      this.deathMemories.clear();
      this.lastKilledEntity = null;
   }

   private void rotateCameraToKilled(LivingEntity living) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.rotateToKilled.getAsBoolean() && living != null && mc.player != null) {
         float[] rotation = this.getRotationTo(mc.player.getEyePos(), living.getEyePos());
         this.applyClientRotation(rotation[0], rotation[1]);
      }
   }

   private float[] getRotationTo(Vec3d from, Vec3d to) {
      double diffX = to.x - from.x;
      double diffY = to.y - from.y;
      double diffZ = to.z - from.z;
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      float yaw = MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
      float pitch = MathHelper.clamp((float)(-Math.toDegrees(Math.atan2(diffY, diffXZ))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }

   private void applyClientRotation(float yaw, float pitch) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         mc.player.setYaw(yaw);
         mc.player.setPitch(pitch);
         mc.player.prevYaw = yaw;
         mc.player.prevPitch = pitch;
         mc.player.setHeadYaw(yaw);
         mc.player.setBodyYaw(yaw);
         IClientPlayerEntity playerAccessor = (IClientPlayerEntity)mc.player;
         playerAccessor.setLastYaw(yaw);
         playerAccessor.setLastPitch(pitch);
         Managers.PLAYER.yaw = yaw;
         Managers.PLAYER.pitch = pitch;
         Managers.PLAYER.lastYaw = yaw;
         Managers.PLAYER.lastPitch = pitch;
      }
   }

   private final class EntityDeathMemory {
      private final Timer startTime = new Timer();
      private final Runnable onTrigger;
      private LivingEntity base;
      private long maxTime;
      private boolean hasReseted;

      private EntityDeathMemory(LivingEntity base, long maxTime, Runnable onTrigger) {
         this.base = base;
         this.maxTime = maxTime;
         this.onTrigger = onTrigger;
      }

      private void resetMemory(LivingEntity base, long maxTime) {
         if (this.base == null || this.base.getHealth() != 0.0F) {
            this.base = base;
            this.maxTime = maxTime;
            this.startTime.reset();
         }
      }

      private boolean isValidMemory() {
         return this.base != null && this.maxTime != 0L && !this.startTime.passedMs(this.maxTime);
      }

      private boolean isNotValidMemory() {
         return !this.isValidMemory();
      }

      private void updateMemoryTrigger() {
         if (this.isValidMemory() && this.base.getHealth() == 0.0F && !this.hasReseted) {
            FragDeathMemory.this.lastKilledEntity = this.base;
            this.onTrigger.run();
            FragDeathMemory.this.rotateCameraToKilled(this.base);
            this.hasReseted = true;
            this.maxTime = 300L;
            this.startTime.reset();
         }
      }
   }
}
