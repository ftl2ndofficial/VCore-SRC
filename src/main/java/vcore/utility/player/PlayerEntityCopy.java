package vcore.utility.player;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity.RemovalReason;
import vcore.features.modules.Module;

public class PlayerEntityCopy extends OtherClientPlayerEntity {
   public PlayerEntityCopy() {
      super(Objects.requireNonNull(Module.mc.world), Objects.requireNonNull(Module.mc.player).getGameProfile());
      this.copyFrom(Module.mc.player);
      this.getPlayerListEntry();
      this.dataTracker.set(PLAYER_MODEL_PARTS, (Byte)Module.mc.player.getDataTracker().get(PLAYER_MODEL_PARTS));
      this.setUuid(UUID.randomUUID());
   }

   public void spawn() {
      if (Module.mc.world != null) {
         this.unsetRemoved();
         Module.mc.world.addEntity(this);
      }
   }

   public void deSpawn() {
      if (Module.mc.world != null) {
         Module.mc.world.removeEntity(this.getId(), RemovalReason.DISCARDED);
      }
   }
}
