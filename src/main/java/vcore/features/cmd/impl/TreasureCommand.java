package vcore.features.cmd.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import vcore.features.cmd.Command;

public class TreasureCommand extends Command {
   public TreasureCommand() {
      super("gettreasure", "Read treasure map coordinates.");
   }

   @Override
   public void executeBuild(LiteralArgumentBuilder<CommandSource> builder) {
      builder.executes(context -> {
         if (mc.player.getMainHandStack().getItem().toString().equals("filled_map")) {
            Record nbt = (Record)mc.player.getMainHandStack().getOrDefault(DataComponentTypes.MAP_DECORATIONS, PotionContentsComponent.DEFAULT);
            if (nbt == null) {
               return 1;
            }

            StringBuilder result = new StringBuilder();
            String rawNbt = nbt.toString();

            for (int i = rawNbt.indexOf("x="); i < rawNbt.indexOf(", rotation") - 2; i++) {
               result.append(rawNbt.charAt(i));
            }

            sendMessage("Found! Coords: " + result);
         } else {
            sendMessage("Take a map into your hand!");
         }

         return 1;
      });
   }
}
