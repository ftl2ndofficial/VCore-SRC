package vcore.features.modules.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import vcore.core.Managers;
import vcore.features.modules.Module;
import vcore.setting.Setting;
import vcore.setting.impl.ColorSetting;
import vcore.setting.impl.SettingGroup;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.Render3DEngine;

public class StorageEsp extends Module {
   public final Setting<Boolean> outline = new Setting<>("Outline", false);
   public final Setting<Boolean> fill = new Setting<>("Fill", true);
   public final Setting<Boolean> tracers = new Setting<>("Tracers", false);
   public final Setting<SettingGroup> storage = new Setting<>("Storage", new SettingGroup(false, 0));
   public final Setting<Boolean> chest = new Setting<>("Chest", true).addToGroup(this.storage);
   public final Setting<Boolean> trappedChest = new Setting<>("Trapped Chest", false).addToGroup(this.storage);
   public final Setting<Boolean> dispenser = new Setting<>("Dispenser", false).addToGroup(this.storage);
   public final Setting<Boolean> shulker = new Setting<>("Shulker", true).addToGroup(this.storage);
   public final Setting<Boolean> echest = new Setting<>("Ender Chest", true).addToGroup(this.storage);
   public final Setting<Boolean> furnace = new Setting<>("Furnace", false).addToGroup(this.storage);
   public final Setting<Boolean> hopper = new Setting<>("Hopper", false).addToGroup(this.storage);
   public final Setting<Boolean> barrels = new Setting<>("Barrel", true).addToGroup(this.storage);
   public final Setting<Boolean> cart = new Setting<>("Minecart", false).addToGroup(this.storage);
   public final Setting<Boolean> frame = new Setting<>("ItemFrame", false).addToGroup(this.storage);
   public final Setting<Boolean> spawner = new Setting<>("Spawner", true).addToGroup(this.storage);
   public final Setting<SettingGroup> color = new Setting<>("Color", new SettingGroup(false, 0));
   private final Setting<ColorSetting> chestColor = new Setting<>("ChestColor", new ColorSetting(new Color(-1711300608, true))).addToGroup(this.color);
   private final Setting<ColorSetting> trappedChestColor = new Setting<>("TrappedChestColor", new ColorSetting(new Color(-1711341568, true)))
      .addToGroup(this.color);
   private final Setting<ColorSetting> shulkColor = new Setting<>("ShulkerColor", new ColorSetting(new Color(-1711300608, true))).addToGroup(this.color);
   private final Setting<ColorSetting> echestColor = new Setting<>("EChestColor", new ColorSetting(new Color(-1720188673, true))).addToGroup(this.color);
   private final Setting<ColorSetting> frameColor = new Setting<>("FrameColor", new ColorSetting(new Color(-1718842228, true))).addToGroup(this.color);
   private final Setting<ColorSetting> shulkerframeColor = new Setting<>("ShulkFrameColor", new ColorSetting(new Color(-1718842228, true)))
      .addToGroup(this.color);
   private final Setting<ColorSetting> furnaceColor = new Setting<>("FurnaceColor", new ColorSetting(new Color(-1718842228, true))).addToGroup(this.color);
   private final Setting<ColorSetting> hopperColor = new Setting<>("HopperColor", new ColorSetting(new Color(-1718842228, true))).addToGroup(this.color);
   private final Setting<ColorSetting> dispenserColor = new Setting<>("DispenserColor", new ColorSetting(new Color(-1718842228, true))).addToGroup(this.color);
   private final Setting<ColorSetting> barrelColor = new Setting<>("BarrelColor", new ColorSetting(new Color(-1711300608, true))).addToGroup(this.color);
   private final Setting<ColorSetting> minecartColor = new Setting<>("MinecartColor", new ColorSetting(new Color(-1711300608, true))).addToGroup(this.color);
   private final Setting<ColorSetting> spawnerColor = new Setting<>("SpawnerColor", new ColorSetting(new Color(-1727987713, true))).addToGroup(this.color);

   public StorageEsp() {
      super("StorageEsp", "Highlights storage blocks.", Module.Category.RENDER);
   }

   @Override
   public void onRender3D(MatrixStack stack) {
      if (!mc.options.hudHidden) {
         for (BlockEntity blockEntity : getBlockEntities()) {
            Color color = this.getColor(blockEntity);
            if (color != null) {
               if (blockEntity instanceof ChestBlockEntity) {
                  BlockState state = blockEntity.getWorld().getBlockState(blockEntity.getPos());
                  ChestType chestType = (ChestType)state.get(ChestBlock.CHEST_TYPE);
                  int originalAlpha = color.getAlpha();
                  Color drawColor = color;
                  if (chestType != ChestType.SINGLE) {
                     drawColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(1, originalAlpha / 2 + 17));
                  }

                  Box chestBox;
                  if (chestType != ChestType.SINGLE) {
                     Direction facing = (Direction)state.get(ChestBlock.FACING);
                     BlockPos pos1 = blockEntity.getPos();
                     BlockPos pos2 = pos1.offset(chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise());
                     double minX = Math.min(pos1.getX(), pos2.getX()) + 0.06;
                     double minY = Math.min(pos1.getY(), pos2.getY());
                     double minZ = Math.min(pos1.getZ(), pos2.getZ()) + 0.06;
                     double maxX = Math.max(pos1.getX(), pos2.getX()) + 0.94;
                     double maxY = Math.max(pos1.getY(), pos2.getY()) - 0.125 + 1.0;
                     double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 0.94;
                     chestBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);
                  } else {
                     chestBox = new Box(
                        blockEntity.getPos().getX() + 0.06,
                        blockEntity.getPos().getY(),
                        blockEntity.getPos().getZ() + 0.06,
                        blockEntity.getPos().getX() + 0.94,
                        blockEntity.getPos().getY() - 0.125 + 1.0,
                        blockEntity.getPos().getZ() + 0.94
                     );
                  }

                  if (this.fill.getValue()) {
                     Render3DEngine.drawFilledBox(stack, chestBox, drawColor);
                  }

                  if (this.outline.getValue()) {
                     Render3DEngine.drawBoxOutline(chestBox, Render2DEngine.injectAlpha(drawColor, 255), 1.0F);
                  }
               } else {
                  Box chestbox = new Box(
                     blockEntity.getPos().getX() + 0.06,
                     blockEntity.getPos().getY(),
                     blockEntity.getPos().getZ() + 0.06,
                     blockEntity.getPos().getX() + 0.94,
                     blockEntity.getPos().getY() - 0.125 + 1.0,
                     blockEntity.getPos().getZ() + 0.94
                  );
                  if (this.fill.getValue()) {
                     if (blockEntity instanceof EnderChestBlockEntity) {
                        Render3DEngine.drawFilledBox(stack, chestbox, color);
                     } else {
                        Render3DEngine.drawFilledBox(stack, new Box(blockEntity.getPos()), color);
                     }
                  }

                  if (this.outline.getValue()) {
                     if (blockEntity instanceof EnderChestBlockEntity) {
                        Render3DEngine.drawBoxOutline(chestbox, Render2DEngine.injectAlpha(color, 255), 1.0F);
                     } else {
                        Render3DEngine.drawBoxOutline(new Box(blockEntity.getPos()), Render2DEngine.injectAlpha(color, 255), 1.0F);
                     }
                  }
               }
            }
         }

         for (Entity ent : Managers.ASYNC.getAsyncEntities()) {
            if (ent instanceof ItemFrameEntity iframe && this.frame.getValue()) {
               Color frameColor1 = this.frameColor.getValue().getColorObject();
               if (iframe.getHeldItemStack().getItem() instanceof BlockItem bitem && bitem.getBlock() instanceof ShulkerBoxBlock) {
                  frameColor1 = this.shulkerframeColor.getValue().getColorObject();
               }

               if (this.fill.getValue()) {
                  Render3DEngine.drawFilledBox(stack, iframe.getBoundingBox(), frameColor1);
               }

               if (this.outline.getValue()) {
                  Render3DEngine.drawBoxOutline(iframe.getBoundingBox(), Render2DEngine.injectAlpha(frameColor1, 255), 1.0F);
               }
            }

            if (ent instanceof ChestMinecartEntity mcart && this.cart.getValue()) {
               if (this.fill.getValue()) {
                  Render3DEngine.drawFilledBox(stack, mcart.getBoundingBox(), this.minecartColor.getValue().getColorObject());
               }

               if (this.outline.getValue()) {
                  Render3DEngine.drawBoxOutline(mcart.getBoundingBox(), Render2DEngine.injectAlpha(this.minecartColor.getValue().getColorObject(), 255), 1.0F);
               }
            }
         }

         if (this.tracers.getValue()) {
            double x1 = mc.player.prevX + (mc.player.getX() - mc.player.prevX) * Render3DEngine.getTickDelta();
            double y1 = mc.player.getEyeHeight(mc.player.getPose()) + mc.player.prevY + (mc.player.getY() - mc.player.prevY) * Render3DEngine.getTickDelta();
            double z1 = mc.player.prevZ + (mc.player.getZ() - mc.player.prevZ) * Render3DEngine.getTickDelta();
            Vec3d start = new Vec3d(0.0, 0.0, 75.0)
               .rotateX(-((float)Math.toRadians(mc.gameRenderer.getCamera().getPitch())))
               .rotateY(-((float)Math.toRadians(mc.gameRenderer.getCamera().getYaw())))
               .add(x1, y1, z1);

            for (BlockEntity blockEntity : getBlockEntities()) {
               Color color = this.getColor(blockEntity);
               if (color != null) {
                  if (blockEntity instanceof ChestBlockEntity) {
                     BlockState state = blockEntity.getWorld().getBlockState(blockEntity.getPos());
                     ChestType chestType = (ChestType)state.get(ChestBlock.CHEST_TYPE);
                     Direction facing = (Direction)state.get(ChestBlock.FACING);
                     BlockPos pos1 = blockEntity.getPos();
                     if (chestType != ChestType.SINGLE) {
                        BlockPos pos2 = pos1.offset(chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise());
                        double midX = (pos1.getX() + pos2.getX()) / 2.0 + 0.5;
                        double midY = (pos1.getY() + pos2.getY()) / 2.0 + 0.5;
                        double midZ = (pos1.getZ() + pos2.getZ()) / 2.0 + 0.5;
                        Render3DEngine.drawLineDebug(start, new Vec3d(midX, midY, midZ), color);
                        continue;
                     }
                  }

                  double x = blockEntity.getPos().getX() + 0.5;
                  double y = blockEntity.getPos().getY() + 0.5;
                  double z = blockEntity.getPos().getZ() + 0.5;
                  Render3DEngine.drawLineDebug(start, new Vec3d(x, y, z), color);
               }
            }

            for (Entity ent : Managers.ASYNC.getAsyncEntities()) {
               if (ent instanceof ItemFrameEntity iframe && this.frame.getValue()) {
                  Box box = iframe.getBoundingBox();
                  double x = (box.minX + box.maxX) / 2.0;
                  double y = (box.minY + box.maxY) / 2.0;
                  double z = (box.minZ + box.maxZ) / 2.0;
                  Color frameColor1 = this.frameColor.getValue().getColorObject();
                  if (iframe.getHeldItemStack().getItem() instanceof BlockItem bitem && bitem.getBlock() instanceof ShulkerBoxBlock) {
                     frameColor1 = this.shulkerframeColor.getValue().getColorObject();
                  }

                  Render3DEngine.drawLineDebug(start, new Vec3d(x, y, z), frameColor1);
               }

               if (ent instanceof ChestMinecartEntity mcart && this.cart.getValue()) {
                  Box box = mcart.getBoundingBox();
                  double x = (box.minX + box.maxX) / 2.0;
                  double y = (box.minY + box.maxY) / 2.0;
                  double z = (box.minZ + box.maxZ) / 2.0;
                  Render3DEngine.drawLineDebug(start, new Vec3d(x, y, z), this.minecartColor.getValue().getColorObject());
               }
            }
         }
      }
   }

   @Nullable
   private Color getColor(BlockEntity bEnt) {
      Color color = null;
      if (bEnt instanceof TrappedChestBlockEntity && this.trappedChest.getValue()) {
         color = this.trappedChestColor.getValue().getColorObject();
      } else if (bEnt instanceof ChestBlockEntity && this.chest.getValue() && bEnt.getType() != BlockEntityType.TRAPPED_CHEST) {
         color = this.chestColor.getValue().getColorObject();
      } else if (bEnt instanceof EnderChestBlockEntity && this.echest.getValue()) {
         color = this.echestColor.getValue().getColorObject();
      } else if (bEnt instanceof BarrelBlockEntity && this.barrels.getValue()) {
         color = this.barrelColor.getValue().getColorObject();
      } else if (bEnt instanceof ShulkerBoxBlockEntity && this.shulker.getValue()) {
         color = this.shulkColor.getValue().getColorObject();
      } else if (bEnt instanceof AbstractFurnaceBlockEntity && this.furnace.getValue()) {
         color = this.furnaceColor.getValue().getColorObject();
      } else if (bEnt instanceof DispenserBlockEntity && this.dispenser.getValue()) {
         color = this.dispenserColor.getValue().getColorObject();
      } else if (bEnt instanceof HopperBlockEntity && this.hopper.getValue()) {
         color = this.hopperColor.getValue().getColorObject();
      } else if (bEnt instanceof MobSpawnerBlockEntity && this.spawner.getValue()) {
         color = this.spawnerColor.getValue().getColorObject();
      }

      return color;
   }

   public static List<BlockEntity> getBlockEntities() {
      List<BlockEntity> list = new ArrayList<>();

      for (WorldChunk chunk : getLoadedChunks()) {
         list.addAll(chunk.getBlockEntities().values());
      }

      return list;
   }

   public static List<WorldChunk> getLoadedChunks() {
      List<WorldChunk> chunks = new ArrayList<>();
      int viewDist = (Integer)mc.options.getViewDistance().getValue();

      for (int x = -viewDist; x <= viewDist; x++) {
         for (int z = -viewDist; z <= viewDist; z++) {
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk((int)mc.player.getX() / 16 + x, (int)mc.player.getZ() / 16 + z);
            if (chunk != null) {
               chunks.add(chunk);
            }
         }
      }

      return chunks;
   }
}
