package vcore.features.cmd.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import vcore.features.cmd.Command;
import vcore.features.modules.misc.ClientSettings;

public class VClipCommand extends Command {
   public VClipCommand() {
      super("vclip", "Clip up or down by distance.");
   }

   @Override
   public void executeBuild(@NotNull LiteralArgumentBuilder<CommandSource> builder) {
      builder.then(literal("down").executes(context -> {
         float y = 0.0F;

         for (int i = 1; i < 255; i++) {
            if (mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos()).add(0, -i, 0)) == Blocks.AIR.getDefaultState()) {
               y = -i - 1;
               break;
            }

            if (mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos()).add(0, -i, 0)) == Blocks.BEDROCK.getDefaultState()) {
               sendMessage(Formatting.RED + "There's nowhere to clip!");
               return 1;
            }
         }

         this.clip(y);
         return 1;
      }));
      builder.then(literal("up").executes(context -> {
         float y = 0.0F;

         for (int i = 4; i < 255; i++) {
            if (mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos()).add(0, i, 0)) == Blocks.AIR.getDefaultState()) {
               y = i + 1;
               break;
            }
         }

         this.clip(y);
         return 1;
      }));
      builder.then(arg("count", DoubleArgumentType.doubleArg()).executes(context -> {
         double count = (Double)context.getArgument("count", Double.class);

         try {
            sendMessage(Formatting.GREEN + "Clipping by " + count + " blocks");
            this.clip(count);
         } catch (Exception var5) {
         }

         return 1;
      }));
      builder.executes(context -> {
         sendMessage("Try .vclip <number>");
         return 1;
      });
   }

   private void clip(double b) {
      if (ClientSettings.clipMode.getValue() == ClientSettings.ClipMode.Matrix) {
         for (int i = 0; i < 10; i++) {
            mc.player.networkHandler.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false));
         }

         for (int i = 0; i < 10; i++) {
            mc.player.networkHandler.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + b, mc.player.getZ(), false));
         }
      } else {
         mc.player.networkHandler.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY() + b, mc.player.getZ(), false));
      }

      mc.player.setPosition(mc.player.getX(), mc.player.getY() + b, mc.player.getZ());
   }
}
