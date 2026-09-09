package vcore.features.cmd.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import vcore.features.cmd.Command;

public class HClipCommand extends Command {
   public HClipCommand() {
      super("hclip", "Clip horizontally in facing direction.");
   }

   @Override
   public void executeBuild(@NotNull LiteralArgumentBuilder<CommandSource> builder) {
      builder.then(literal("s").executes(context -> {
         double x = -(MathHelper.sin(mc.player.getYaw() * (float) (Math.PI / 180.0)) * 0.8);
         double z = MathHelper.cos(mc.player.getYaw() * (float) (Math.PI / 180.0)) * 0.8;

         for (int i = 0; i < 10; i++) {
            mc.player.networkHandler.sendPacket(new PositionAndOnGround(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z, false));
         }

         mc.player.setPosition(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z);
         return 1;
      }));
      builder.then(
         arg("count", DoubleArgumentType.doubleArg())
            .executes(
               context -> {
                  double speed = (Double)context.getArgument("count", Double.class);

                  try {
                     sendMessage(Formatting.GREEN + "Clipping by " + speed + " blocks.");
                     mc.player
                        .setPosition(
                           mc.player.getX() - MathHelper.sin(mc.player.getYaw() * (float) (Math.PI / 180.0)) * speed,
                           mc.player.getY(),
                           mc.player.getZ() + MathHelper.cos(mc.player.getYaw() * (float) (Math.PI / 180.0)) * speed
                        );
                  } catch (Exception var4) {
                  }

                  return 1;
               }
            )
      );
      builder.executes(context -> {
         sendMessage("Try .hclip <number>, .hclip s");
         return 1;
      });
   }
}
