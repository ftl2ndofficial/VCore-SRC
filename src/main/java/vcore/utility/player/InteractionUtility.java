package vcore.utility.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vcore.features.modules.Module;
import vcore.injection.accesors.IClientWorldMixin;
import vcore.utility.world.ExplosionUtility;

public final class InteractionUtility {
   private static final List<Block> SHIFT_BLOCKS = Arrays.asList(
      Blocks.ENDER_CHEST,
      Blocks.CHEST,
      Blocks.TRAPPED_CHEST,
      Blocks.CRAFTING_TABLE,
      Blocks.BIRCH_TRAPDOOR,
      Blocks.BAMBOO_TRAPDOOR,
      Blocks.DARK_OAK_TRAPDOOR,
      Blocks.CHERRY_TRAPDOOR,
      Blocks.ANVIL,
      Blocks.BREWING_STAND,
      Blocks.HOPPER,
      Blocks.DROPPER,
      Blocks.DISPENSER,
      Blocks.ACACIA_TRAPDOOR,
      Blocks.ENCHANTING_TABLE,
      Blocks.WHITE_SHULKER_BOX,
      Blocks.ORANGE_SHULKER_BOX,
      Blocks.MAGENTA_SHULKER_BOX,
      Blocks.LIGHT_BLUE_SHULKER_BOX,
      Blocks.YELLOW_SHULKER_BOX,
      Blocks.LIME_SHULKER_BOX,
      Blocks.PINK_SHULKER_BOX,
      Blocks.GRAY_SHULKER_BOX,
      Blocks.CYAN_SHULKER_BOX,
      Blocks.PURPLE_SHULKER_BOX,
      Blocks.BLUE_SHULKER_BOX,
      Blocks.BROWN_SHULKER_BOX,
      Blocks.GREEN_SHULKER_BOX,
      Blocks.RED_SHULKER_BOX,
      Blocks.BLACK_SHULKER_BOX
   );
   public static Map<BlockPos, Long> awaiting = new HashMap<>();

   public static boolean canSee(Vec3d vec) {
      return canSee(vec, vec);
   }

   public static boolean canSee(Entity entity) {
      Vec3d entityEyes = getEyesPos(entity);
      Vec3d entityPos = entity.getPos();
      return canSee(entityEyes, entityPos);
   }

   public static boolean canSee(Vec3d entityEyes, Vec3d entityPos) {
      if (Module.mc.player != null && Module.mc.world != null) {
         Vec3d playerEyes = getEyesPos(Module.mc.player);
         if (ExplosionUtility.raycast(playerEyes, entityEyes, false) == Type.MISS) {
            return true;
         } else {
            return playerEyes.getY() > entityPos.getY() ? ExplosionUtility.raycast(playerEyes, entityEyes, false) == Type.MISS : false;
         }
      } else {
         return false;
      }
   }

   public static Vec3d getEyesPos(@NotNull Entity entity) {
      return entity.getPos().add(0.0, entity.getEyeHeight(entity.getPose()), 0.0);
   }

   public static float @NotNull [] calculateAngle(Vec3d to) {
      return calculateAngle(getEyesPos(Module.mc.player), to);
   }

   public static float @NotNull [] calculateAngle(@NotNull Vec3d from, @NotNull Vec3d to) {
      double difX = to.x - from.x;
      double difY = (to.y - from.y) * -1.0;
      double difZ = to.z - from.z;
      double dist = MathHelper.sqrt((float)(difX * difX + difZ * difZ));
      float yD = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
      float pD = (float)MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist))), -90.0, 90.0);
      return new float[]{yD, pD};
   }

   public static boolean placeBlock(
      BlockPos bp,
      InteractionUtility.Rotate rotate,
      InteractionUtility.Interact interact,
      InteractionUtility.PlaceMode mode,
      int slot,
      boolean returnSlot,
      boolean ignoreEntities
   ) {
      int prevItem = Module.mc.player.getInventory().selectedSlot;
      if (slot != -1) {
         InventoryUtility.switchTo(slot);
         boolean result = placeBlock(bp, rotate, interact, mode, ignoreEntities);
         if (returnSlot) {
            InventoryUtility.switchTo(prevItem);
         }

         return result;
      } else {
         return false;
      }
   }

   public static boolean placeBlock(
      BlockPos bp,
      InteractionUtility.Rotate rotate,
      InteractionUtility.Interact interact,
      InteractionUtility.PlaceMode mode,
      @NotNull SearchInvResult invResult,
      boolean returnSlot,
      boolean ignoreEntities
   ) {
      int prevItem = Module.mc.player.getInventory().selectedSlot;
      invResult.switchTo();
      boolean result = placeBlock(bp, rotate, interact, mode, ignoreEntities);
      if (returnSlot) {
         InventoryUtility.switchTo(prevItem);
      }

      return result;
   }

   public static boolean placeBlock(
      BlockPos bp, InteractionUtility.Rotate rotate, InteractionUtility.Interact interact, InteractionUtility.PlaceMode mode, boolean ignoreEntities
   ) {
      BlockHitResult result = getPlaceResult(bp, interact, ignoreEntities);
      if (result != null && Module.mc.world != null && Module.mc.interactionManager != null && Module.mc.player != null) {
         boolean sprint = Module.mc.player.isSprinting();
         boolean sneak = needSneak(Module.mc.world.getBlockState(result.getBlockPos()).getBlock()) && !Module.mc.player.isSneaking();
         if (sprint) {
            Module.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(Module.mc.player, Mode.STOP_SPRINTING));
         }

         if (sneak) {
            Module.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(Module.mc.player, Mode.PRESS_SHIFT_KEY));
         }

         float[] angle = calculateAngle(result.getPos());
         switch (rotate) {
            case None:
            default:
               break;
            case Default:
               Module.mc.player.networkHandler.sendPacket(new LookAndOnGround(angle[0], angle[1], Module.mc.player.isOnGround()));
               break;
            case Grim:
               Module.mc
                  .player
                  .networkHandler
                  .sendPacket(
                     new Full(Module.mc.player.getX(), Module.mc.player.getY(), Module.mc.player.getZ(), angle[0], angle[1], Module.mc.player.isOnGround())
                  );
         }

         if (mode == InteractionUtility.PlaceMode.Normal) {
            Module.mc.interactionManager.interactBlock(Module.mc.player, Hand.MAIN_HAND, result);
         }

         if (mode == InteractionUtility.PlaceMode.Packet) {
            sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, result, id));
         }

         awaiting.put(bp, System.currentTimeMillis());
         if (rotate == InteractionUtility.Rotate.Grim) {
            Module.mc
               .player
               .networkHandler
               .sendPacket(
                  new Full(
                     Module.mc.player.getX(),
                     Module.mc.player.getY(),
                     Module.mc.player.getZ(),
                     Module.mc.player.getYaw(),
                     Module.mc.player.getPitch(),
                     Module.mc.player.isOnGround()
                  )
               );
         }

         if (sneak) {
            Module.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(Module.mc.player, Mode.RELEASE_SHIFT_KEY));
         }

         if (sprint) {
            Module.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(Module.mc.player, Mode.START_SPRINTING));
         }

         Module.mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
         return true;
      } else {
         return false;
      }
   }

   public static boolean canPlaceBlock(@NotNull BlockPos bp, InteractionUtility.Interact interact, boolean ignoreEntities) {
      return awaiting.containsKey(bp) ? false : getPlaceResult(bp, interact, ignoreEntities) != null;
   }

   public static float @Nullable [] getPlaceAngle(@NotNull BlockPos bp, InteractionUtility.Interact interact, boolean ignoreEntities) {
      BlockHitResult result = getPlaceResult(bp, interact, ignoreEntities);
      return result != null ? calculateAngle(result.getPos()) : null;
   }

   public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
      if (Module.mc.getNetworkHandler() != null && Module.mc.world != null) {
         PendingUpdateManager pendingUpdateManager = ((IClientWorldMixin)Module.mc.world).getPendingUpdateManager().incrementSequence();

         try {
            int i = pendingUpdateManager.getSequence();
            Module.mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
         } catch (Throwable var5) {
            if (pendingUpdateManager != null) {
               try {
                  pendingUpdateManager.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (pendingUpdateManager != null) {
            pendingUpdateManager.close();
         }
      }
   }

   @Nullable
   public static BlockHitResult getPlaceResult(@NotNull BlockPos bp, InteractionUtility.Interact interact, boolean ignoreEntities) {
      if (!ignoreEntities) {
         for (Entity entity : Module.mc.world.getNonSpectatingEntities(Entity.class, new Box(bp))) {
            if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrbEntity)) {
               return null;
            }
         }
      }

      if (!Module.mc.world.getBlockState(bp).isReplaceable()) {
         return null;
      }

      if (interact == InteractionUtility.Interact.AirPlace) {
         return ExplosionUtility.rayCastBlock(
            new RaycastContext(getEyesPos(Module.mc.player), bp.toCenterPos(), ShapeType.COLLIDER, FluidHandling.NONE, Module.mc.player), bp
         );
      }

      ArrayList<InteractionUtility.BlockPosWithFacing> supports = getSupportBlocks(bp);
      Iterator var9 = supports.iterator();

      InteractionUtility.BlockPosWithFacing support;
      List<Direction> dirs;
      do {
         if (!var9.hasNext()) {
            return null;
         }

         support = (InteractionUtility.BlockPosWithFacing)var9.next();
         if (interact == InteractionUtility.Interact.Vanilla) {
            break;
         }

         dirs = getStrictDirections(bp);
         if (dirs.isEmpty()) {
            return null;
         }
      } while (!dirs.contains(support.facing));

      BlockHitResult result = null;
      if (interact == InteractionUtility.Interact.Legit) {
         Vec3d p = getVisibleDirectionPoint(support.facing, support.position, 0.0F, 6.0F);
         if (p != null) {
            return new BlockHitResult(p, support.facing, support.position, false);
         }
      } else {
         Vec3d directionVec = new Vec3d(
            support.position.getX() + 0.5 + support.facing.getVector().getX() * 0.5,
            support.position.getY() + 0.5 + support.facing.getVector().getY() * 0.5,
            support.position.getZ() + 0.5 + support.facing.getVector().getZ() * 0.5
         );
         result = new BlockHitResult(directionVec, support.facing, support.position, false);
      }

      return result;
   }

   @NotNull
   public static ArrayList<InteractionUtility.BlockPosWithFacing> getSupportBlocks(@NotNull BlockPos bp) {
      ArrayList<InteractionUtility.BlockPosWithFacing> list = new ArrayList<>();
      if (Module.mc.world.getBlockState(bp.add(0, -1, 0)).isSolid() || awaiting.containsKey(bp.add(0, -1, 0))) {
         list.add(new InteractionUtility.BlockPosWithFacing(bp.add(0, -1, 0), Direction.UP));
      }

      if (Module.mc.world.getBlockState(bp.add(0, 1, 0)).isSolid() || awaiting.containsKey(bp.add(0, 1, 0))) {
         list.add(new InteractionUtility.BlockPosWithFacing(bp.add(0, 1, 0), Direction.DOWN));
      }

      if (Module.mc.world.getBlockState(bp.add(-1, 0, 0)).isSolid() || awaiting.containsKey(bp.add(-1, 0, 0))) {
         list.add(new InteractionUtility.BlockPosWithFacing(bp.add(-1, 0, 0), Direction.EAST));
      }

      if (Module.mc.world.getBlockState(bp.add(1, 0, 0)).isSolid() || awaiting.containsKey(bp.add(1, 0, 0))) {
         list.add(new InteractionUtility.BlockPosWithFacing(bp.add(1, 0, 0), Direction.WEST));
      }

      if (Module.mc.world.getBlockState(bp.add(0, 0, 1)).isSolid() || awaiting.containsKey(bp.add(0, 0, 1))) {
         list.add(new InteractionUtility.BlockPosWithFacing(bp.add(0, 0, 1), Direction.NORTH));
      }

      if (Module.mc.world.getBlockState(bp.add(0, 0, -1)).isSolid() || awaiting.containsKey(bp.add(0, 0, -1))) {
         list.add(new InteractionUtility.BlockPosWithFacing(bp.add(0, 0, -1), Direction.SOUTH));
      }

      return list;
   }

   @Nullable
   public static InteractionUtility.BlockPosWithFacing checkNearBlocks(@NotNull BlockPos blockPos) {
      if (Module.mc.world.getBlockState(blockPos.add(0, -1, 0)).isSolid()) {
         return new InteractionUtility.BlockPosWithFacing(blockPos.add(0, -1, 0), Direction.UP);
      } else if (Module.mc.world.getBlockState(blockPos.add(-1, 0, 0)).isSolid()) {
         return new InteractionUtility.BlockPosWithFacing(blockPos.add(-1, 0, 0), Direction.EAST);
      } else if (Module.mc.world.getBlockState(blockPos.add(1, 0, 0)).isSolid()) {
         return new InteractionUtility.BlockPosWithFacing(blockPos.add(1, 0, 0), Direction.WEST);
      } else if (Module.mc.world.getBlockState(blockPos.add(0, 0, 1)).isSolid()) {
         return new InteractionUtility.BlockPosWithFacing(blockPos.add(0, 0, 1), Direction.NORTH);
      } else {
         return Module.mc.world.getBlockState(blockPos.add(0, 0, -1)).isSolid()
            ? new InteractionUtility.BlockPosWithFacing(blockPos.add(0, 0, -1), Direction.SOUTH)
            : null;
      }
   }

   public static float squaredDistanceFromEyes(@NotNull Vec3d vec) {
      double d0 = vec.x - Module.mc.player.getX();
      double d1 = vec.z - Module.mc.player.getZ();
      double d2 = vec.y - (Module.mc.player.getY() + Module.mc.player.getEyeHeight(Module.mc.player.getPose()));
      return (float)(d0 * d0 + d1 * d1 + d2 * d2);
   }

   public static float squaredDistanceFromEyes2d(@NotNull Vec3d vec) {
      double d0 = vec.x - Module.mc.player.getX();
      double d1 = vec.z - Module.mc.player.getZ();
      return (float)(d0 * d0 + d1 * d1);
   }

   @NotNull
   public static List<Direction> getStrictDirections(@NotNull BlockPos bp) {
      List<Direction> visibleSides = new ArrayList<>();
      Vec3d positionVector = bp.toCenterPos();
      double westDelta = getEyesPos(Module.mc.player).x - positionVector.add(0.5, 0.0, 0.0).x;
      double eastDelta = getEyesPos(Module.mc.player).x - positionVector.add(-0.5, 0.0, 0.0).x;
      double northDelta = getEyesPos(Module.mc.player).z - positionVector.add(0.0, 0.0, 0.5).z;
      double southDelta = getEyesPos(Module.mc.player).z - positionVector.add(0.0, 0.0, -0.5).z;
      double upDelta = getEyesPos(Module.mc.player).y - positionVector.add(0.0, 0.5, 0.0).y;
      double downDelta = getEyesPos(Module.mc.player).y - positionVector.add(0.0, -0.5, 0.0).y;
      if (westDelta > 0.0 && isSolid(bp.west())) {
         visibleSides.add(Direction.EAST);
      }

      if (westDelta < 0.0 && isSolid(bp.east())) {
         visibleSides.add(Direction.WEST);
      }

      if (eastDelta < 0.0 && isSolid(bp.east())) {
         visibleSides.add(Direction.WEST);
      }

      if (eastDelta > 0.0 && isSolid(bp.west())) {
         visibleSides.add(Direction.EAST);
      }

      if (northDelta > 0.0 && isSolid(bp.north())) {
         visibleSides.add(Direction.SOUTH);
      }

      if (northDelta < 0.0 && isSolid(bp.south())) {
         visibleSides.add(Direction.NORTH);
      }

      if (southDelta < 0.0 && isSolid(bp.south())) {
         visibleSides.add(Direction.NORTH);
      }

      if (southDelta > 0.0 && isSolid(bp.north())) {
         visibleSides.add(Direction.SOUTH);
      }

      if (upDelta > 0.0 && isSolid(bp.down())) {
         visibleSides.add(Direction.UP);
      }

      if (upDelta < 0.0 && isSolid(bp.up())) {
         visibleSides.add(Direction.DOWN);
      }

      if (downDelta < 0.0 && isSolid(bp.up())) {
         visibleSides.add(Direction.DOWN);
      }

      if (downDelta > 0.0 && isSolid(bp.down())) {
         visibleSides.add(Direction.UP);
      }

      return visibleSides;
   }

   public static boolean isSolid(BlockPos bp) {
      return Module.mc.world.getBlockState(bp).isSolid() || awaiting.containsKey(bp);
   }

   @NotNull
   public static List<Direction> getStrictBlockDirections(@NotNull BlockPos bp) {
      List<Direction> visibleSides = new ArrayList<>();
      Vec3d pV = bp.toCenterPos();
      double westDelta = getEyesPos(Module.mc.player).x - pV.add(0.5, 0.0, 0.0).x;
      double eastDelta = getEyesPos(Module.mc.player).x - pV.add(-0.5, 0.0, 0.0).x;
      double northDelta = getEyesPos(Module.mc.player).z - pV.add(0.0, 0.0, 0.5).z;
      double southDelta = getEyesPos(Module.mc.player).z - pV.add(0.0, 0.0, -0.5).z;
      double upDelta = getEyesPos(Module.mc.player).y - pV.add(0.0, 0.5, 0.0).y;
      double downDelta = getEyesPos(Module.mc.player).y - pV.add(0.0, -0.5, 0.0).y;
      if (westDelta > 0.0 && Module.mc.world.getBlockState(bp.east()).isReplaceable()) {
         visibleSides.add(Direction.EAST);
      }

      if (eastDelta < 0.0 && Module.mc.world.getBlockState(bp.west()).isReplaceable()) {
         visibleSides.add(Direction.WEST);
      }

      if (northDelta > 0.0 && Module.mc.world.getBlockState(bp.south()).isReplaceable()) {
         visibleSides.add(Direction.SOUTH);
      }

      if (southDelta < 0.0 && Module.mc.world.getBlockState(bp.north()).isReplaceable()) {
         visibleSides.add(Direction.NORTH);
      }

      if (upDelta > 0.0 && Module.mc.world.getBlockState(bp.up()).isReplaceable()) {
         visibleSides.add(Direction.UP);
      }

      if (downDelta < 0.0 && Module.mc.world.getBlockState(bp.down()).isReplaceable()) {
         visibleSides.add(Direction.DOWN);
      }

      return visibleSides;
   }

   @Nullable
   public static InteractionUtility.BreakData getBreakData(BlockPos bp, InteractionUtility.Interact interact) {
      if (interact == InteractionUtility.Interact.Vanilla) {
         return new InteractionUtility.BreakData(Direction.UP, bp.toCenterPos().add(0.0, 0.5, 0.0));
      }

      if (interact == InteractionUtility.Interact.Strict) {
         float bestDistance = 999.0F;
         Direction bestDirection = Direction.UP;
         Vec3d bestVector = null;

         for (Direction dir : Direction.values()) {
            Vec3d directionVec = new Vec3d(
               bp.getX() + 0.5 + dir.getVector().getX() * 0.5, bp.getY() + 0.5 + dir.getVector().getY() * 0.5, bp.getZ() + 0.5 + dir.getVector().getZ() * 0.5
            );
            float distance = squaredDistanceFromEyes(directionVec);
            if (bestDistance > distance) {
               bestDirection = dir;
               bestVector = directionVec;
               bestDistance = distance;
            }
         }

         return bestVector == null ? null : new InteractionUtility.BreakData(bestDirection, bestVector);
      } else {
         if (interact != InteractionUtility.Interact.Legit) {
            return null;
         }

         float bestDistance = 999.0F;
         InteractionUtility.BreakData bestData = null;

         for (float x = 0.0F; x <= 1.0F; x += 0.2F) {
            for (float y = 0.0F; y <= 1.0F; y += 0.2F) {
               for (float z = 0.0F; z <= 1.0F; z += 0.2F) {
                  Vec3d point = new Vec3d(bp.getX() + x, bp.getY() + y, bp.getZ() + z);
                  BlockHitResult wallCheck = Module.mc
                     .world
                     .raycast(new RaycastContext(getEyesPos(Module.mc.player), point, ShapeType.COLLIDER, FluidHandling.NONE, Module.mc.player));
                  if (wallCheck == null || wallCheck.getType() != Type.BLOCK || wallCheck.getBlockPos().equals(bp)) {
                     BlockHitResult result = ExplosionUtility.rayCastBlock(
                        new RaycastContext(getEyesPos(Module.mc.player), point, ShapeType.COLLIDER, FluidHandling.NONE, Module.mc.player), bp
                     );
                     if (squaredDistanceFromEyes(point) < bestDistance && result != null && result.getType() == Type.BLOCK) {
                        bestData = new InteractionUtility.BreakData(result.getSide(), result.getPos());
                     }
                  }
               }
            }
         }

         if (bestData == null) {
            return null;
         } else {
            return bestData.vector != null && bestData.dir != null ? bestData : null;
         }
      }
   }

   @Nullable
   public static Vec3d getVisibleDirectionPoint(@NotNull Direction dir, @NotNull BlockPos bp, float wallRange, float range) {
      Box brutBox = getDirectionBox(dir);
      if (brutBox.maxX - brutBox.minX == 0.0) {
         for (double y = brutBox.minY; y < brutBox.maxY; y += 0.1F) {
            for (double z = brutBox.minZ; z < brutBox.maxZ; z += 0.1F) {
               Vec3d point = new Vec3d(bp.getX() + brutBox.minX, bp.getY() + y, bp.getZ() + z);
               if (!shouldSkipPoint(point, bp, dir, wallRange, range)) {
                  return point;
               }
            }
         }
      }

      if (brutBox.maxY - brutBox.minY == 0.0) {
         for (double x = brutBox.minX; x < brutBox.maxX; x += 0.1F) {
            for (double z = brutBox.minZ; z < brutBox.maxZ; z += 0.1F) {
               Vec3d point = new Vec3d(bp.getX() + x, bp.getY() + brutBox.minY, bp.getZ() + z);
               if (!shouldSkipPoint(point, bp, dir, wallRange, range)) {
                  return point;
               }
            }
         }
      }

      if (brutBox.maxZ - brutBox.minZ == 0.0) {
         for (double x = brutBox.minX; x < brutBox.maxX; x += 0.1F) {
            for (double y = brutBox.minY; y < brutBox.maxY; y += 0.1F) {
               Vec3d point = new Vec3d(bp.getX() + x, bp.getY() + y, bp.getZ() + brutBox.minZ);
               if (!shouldSkipPoint(point, bp, dir, wallRange, range)) {
                  return point;
               }
            }
         }
      }

      return null;
   }

   @NotNull
   private static Box getDirectionBox(Direction dir) {
      return switch (dir) {
         case UP -> new Box(0.15F, 1.0, 0.15F, 0.85F, 1.0, 0.85F);
         case DOWN -> new Box(0.15F, 0.0, 0.15F, 0.85F, 0.0, 0.85F);
         case EAST -> new Box(1.0, 0.15F, 0.15F, 1.0, 0.85F, 0.85F);
         case WEST -> new Box(0.0, 0.15F, 0.15F, 0.0, 0.85F, 0.85F);
         case NORTH -> new Box(0.15F, 0.15F, 0.0, 0.85F, 0.85F, 0.0);
         case SOUTH -> new Box(0.15F, 0.15F, 1.0, 0.85F, 0.85F, 1.0);
         default -> throw new MatchException(null, null);
      };
   }

   private static boolean shouldSkipPoint(Vec3d point, BlockPos bp, Direction dir, float wallRange, float range) {
      RaycastContext context = new RaycastContext(getEyesPos(Module.mc.player), point, ShapeType.COLLIDER, FluidHandling.NONE, Module.mc.player);
      BlockHitResult result = Module.mc.world.raycast(context);
      float dst = squaredDistanceFromEyes(point);
      return result != null && result.getType() == Type.BLOCK && !result.getBlockPos().equals(bp) && dst > wallRange * wallRange ? true : dst > range * range;
   }

   public static boolean needSneak(Block in) {
      return SHIFT_BLOCKS.contains(in);
   }

   public static void lookAt(BlockPos bp) {
      if (bp != null) {
         float[] angle = calculateAngle(bp.toCenterPos());
         Module.mc.player.setYaw(angle[0]);
         Module.mc.player.setPitch(angle[1]);
      }
   }

   public static boolean isVecInFOV(Vec3d pos, Integer fov) {
      double deltaX = pos.getX() - Module.mc.player.getX();
      double deltaZ = pos.getZ() - Module.mc.player.getZ();
      float yawDelta = MathHelper.wrapDegrees(
         (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0) - MathHelper.wrapDegrees(Module.mc.player.getYaw())
      );
      return Math.abs(yawDelta) <= fov.intValue();
   }

   public record BlockPosWithFacing(BlockPos position, Direction facing) {
   }

   public record BreakData(Direction dir, Vec3d vector) {
   }

   public enum Interact {
      Vanilla,
      Strict,
      Legit,
      AirPlace;
   }

   public enum PlaceMode {
      Packet,
      Normal;
   }

   public enum Rotate {
      None,
      Default,
      Grim;
   }
}
