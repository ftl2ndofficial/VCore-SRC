package vcore.injection;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vcore.Vcore;
import vcore.core.Managers;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventChunkData;
import vcore.features.modules.Module;
import vcore.features.modules.misc.UnHook;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {
   @Inject(method = "onEntityStatus", at = @At("HEAD"))
   private void hookPopEffectTotemStatus(@NotNull EntityStatusS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      NetworkThreadUtils.forceMainThread(packet, (ClientPlayNetworkHandler)(Object)this, client);
      if (client.world != null && packet.getStatus() == 35) {
         Entity entity = packet.getEntity(client.world);
         if (entity != null) {
            ModuleManager.popEffect.addEffect(entity);
         }
      }
   }

   @Inject(
      method = "onEntityStatus",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/packet/s2c/play/EntityStatusS2CPacket;getEntity(Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"
      )
   )
   private void hookPopEffectChamsNewStatus(@NotNull EntityStatusS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world != null && packet.getStatus() == 35) {
         if (packet.getEntity(client.world) instanceof PlayerEntity player) {
            ModuleManager.popEffect.addChamsNewFromStatus(player);
         }
      }
   }

   @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
   private void sendChatMessageHook(@NotNull String message, CallbackInfo ci) {
      if (message.equals(String.valueOf(ModuleManager.unHook.code)) && UnHook.isActive()) {
         ModuleManager.unHook.disable();
      }

      if (!Module.fullNullCheck()) {
         if (message.startsWith(Managers.COMMAND.getPrefix())) {
            try {
               Managers.COMMAND.getDispatcher().execute(message.substring(Managers.COMMAND.getPrefix().length()), Managers.COMMAND.getSource());
            } catch (CommandSyntaxException var4) {
            }

            ci.cancel();
         }
      }
   }

   @Inject(method = "onChunkData", at = @At("TAIL"))
   private void hookChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world != null) {
         WorldChunk chunk = client.world.getChunkManager().getWorldChunk(packet.getChunkX(), packet.getChunkZ(), false);
         if (chunk != null) {
            Vcore.EVENT_BUS.post(new EventChunkData(chunk));
         }
      }
   }
}
