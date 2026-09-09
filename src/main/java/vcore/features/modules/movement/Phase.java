package vcore.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventCollision;
import vcore.events.impl.EventPostSync;
import vcore.events.impl.EventSync;
import vcore.features.modules.Module;
import vcore.setting.Setting;
import vcore.utility.player.InteractionUtility;
import vcore.utility.player.InventoryUtility;
import vcore.utility.player.MovementUtility;

public class Phase extends Module {
   private final Setting<Phase.Mode> mode = new Setting<>("Mode", Phase.Mode.Pearl);
   private final Setting<Boolean> onlyOnGround = new Setting<>("OnlyOnGround", false, v -> this.mode.is(Phase.Mode.Pearl));
   private final Setting<Boolean> autoDisable = new Setting<>("AutoDisable", true, v -> this.mode.getValue() == Phase.Mode.Pearl);
   private final Setting<Integer> afterPearl = new Setting<>("PearlTimeout", 0, 0, 60, v -> this.mode.getValue() == Phase.Mode.Pearl);
   private final Setting<Float> pitch = new Setting<>("Pitch", 85.0F, 0.0F, 90.0F, v -> this.mode.getValue() == Phase.Mode.Pearl);
   public int clipTimer;
   public int afterPearlTime;

   public Phase() {
      super("Phase", "Walk through walls.", Module.Category.MOVEMENT);
   }

   @EventHandler
   public void onCollide(EventCollision e) {
      if (!fullNullCheck()) {
         if (this.afterPearlTime > 0) {
            BlockPos playerPos = BlockPos.ofFloored(mc.player.getPos());
            if (!e.getPos().equals(playerPos.down()) || mc.options.sneakKey.isPressed()) {
               e.setState(Blocks.AIR.getDefaultState());
            }
         }
      }
   }

   @Override
   public void onEnable() {
      this.afterPearlTime = 0;
      this.clipTimer = 0;
      if (mc.player.isOnGround() && this.mode.is(Phase.Mode.CCClip)) {
         double[] diagonalOffset = MovementUtility.forwardWithoutStrafe(0.44);
         boolean diagonal = mc.player.getYaw() % 90.0F > 35.0F && mc.player.getYaw() % 90.0F < 55.0F;
         this.sendPacket(new ClientCommandC2SPacket(mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_SPRINTING));
         if (diagonal) {
            double[] directionVec = MovementUtility.forwardWithoutStrafe(0.51);
            int height = mc.world
                  .raycast(
                     new RaycastContext(
                        mc.player.getEyePos(),
                        mc.player.getEyePos().add(diagonalOffset[0], 0.0, diagonalOffset[1]),
                        ShapeType.COLLIDER,
                        FluidHandling.NONE,
                        mc.player
                     )
                  )
                  .getType()
                  .equals(Type.MISS)
               ? 1
               : 2;
            mc.player.setPosition(mc.player.getX() + directionVec[0], mc.player.getY() + height, mc.player.getZ() + directionVec[1]);
            this.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
            height = mc.world.isAir(BlockPos.ofFloored(mc.player.getPos().add(diagonalOffset[0], -2.0, diagonalOffset[1]))) ? 2 : 1;
            mc.player.setPosition(mc.player.getX() + directionVec[0], mc.player.getY() - height, mc.player.getZ() + directionVec[1]);
            this.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
            this.disable("diagonal");
         } else {
            double[] directionVec = MovementUtility.forwardWithoutStrafe(0.57);
            int height = mc.world
                  .raycast(
                     new RaycastContext(
                        mc.player.getEyePos(),
                        mc.player.getEyePos().add(diagonalOffset[0], 0.0, diagonalOffset[1]),
                        ShapeType.COLLIDER,
                        FluidHandling.NONE,
                        mc.player
                     )
                  )
                  .getType()
                  .equals(Type.MISS)
               ? 1
               : 2;
            mc.player.setPosition(mc.player.getX() + directionVec[0], mc.player.getY() + height, mc.player.getZ() + directionVec[1]);
            this.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
            mc.player.setPosition(mc.player.getX() + directionVec[0], mc.player.getY(), mc.player.getZ() + directionVec[1]);
            this.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
            height = mc.world.isAir(BlockPos.ofFloored(mc.player.getPos().add(diagonalOffset[0], -2.0, diagonalOffset[1]))) ? 2 : 1;
            mc.player.setPosition(mc.player.getX() + directionVec[0], mc.player.getY() - height, mc.player.getZ() + directionVec[1]);
            this.sendPacket(new PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true));
            this.disable("normal");
         }
      }
   }

   @EventHandler
   public void onSync(EventSync e) {
      if (!fullNullCheck()) {
         if (this.clipTimer > 0) {
            this.clipTimer--;
         }

         if (this.afterPearlTime > 0) {
            this.afterPearlTime--;
         }

         if (this.mode.getValue() == Phase.Mode.Pearl
            && (mc.player.isOnGround() || !this.onlyOnGround.getValue())
            && mc.player.horizontalCollision
            && !this.playerInsideBlock()
            && this.clipTimer <= 0
            && mc.player.age > 60) {
            if (mc.options.sneakKey.isPressed()) {
               return;
            }

            double[] dir = MovementUtility.forward(0.5);
            BlockPos block = BlockPos.ofFloored(mc.player.getX() + dir[0], mc.player.getY(), mc.player.getZ() + dir[1]);
            float[] angle = InteractionUtility.calculateAngle(block.toCenterPos());
            int epSlot = this.findEPSlot();
            if (epSlot != -1) {
               ModuleManager.aura.pause();
               mc.player.setYaw(angle[0]);
               mc.player.setPitch(this.pitch.getValue());
            }
         }
      }
   }

   @EventHandler
   public void onPostSync(EventPostSync e) {
      if (this.mode.getValue() == Phase.Mode.Pearl
         && (mc.player.isOnGround() || !this.onlyOnGround.getValue())
         && mc.player.horizontalCollision
         && !this.playerInsideBlock()
         && this.clipTimer <= 0
         && mc.player.age > 60) {
         if (mc.options.sneakKey.isPressed()) {
            return;
         }

         int epSlot = this.findEPSlot();
         int prevItem = mc.player.getInventory().selectedSlot;
         if (epSlot != -1) {
            InventoryUtility.switchTo(epSlot);
            this.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, mc.player.getYaw(), mc.player.getPitch()));
            this.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            InventoryUtility.switchTo(prevItem);
            if (this.autoDisable.getValue()) {
               this.disable();
            }
         }

         this.clipTimer = 20;
         this.afterPearlTime = this.afterPearl.getValue();
      }
   }

   private int findEPSlot() {
      int epSlot = -1;
      if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
         epSlot = mc.player.getInventory().selectedSlot;
      }

      if (epSlot == -1) {
         for (int l = 0; l < 9; l++) {
            if (mc.player.getInventory().getStack(l).getItem() == Items.ENDER_PEARL) {
               epSlot = l;
               break;
            }
         }
      }

      return epSlot;
   }

   public boolean playerInsideBlock() {
      BlockPos pos = BlockPos.ofFloored(mc.player.getPos());
      BlockState state = mc.world.getBlockState(pos);
      return !state.isAir() && state.isFullCube(mc.world, pos);
   }

   private enum Mode {
      Pearl,
      CCClip;
   }
}
