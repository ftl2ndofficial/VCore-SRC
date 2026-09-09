package vcore.events.impl;

import net.minecraft.world.chunk.WorldChunk;
import vcore.events.Event;

public class EventChunkData extends Event {
   private final WorldChunk chunk;

   public EventChunkData(WorldChunk chunk) {
      this.chunk = chunk;
   }

   public WorldChunk getChunk() {
      return this.chunk;
   }
}
