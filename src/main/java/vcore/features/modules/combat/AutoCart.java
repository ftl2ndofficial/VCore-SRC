package vcore.features.modules.combat;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
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
import vcore.core.InputBlocker;
import vcore.core.Managers;
import vcore.core.manager.client.AsyncManager;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventFixVelocity;
import vcore.events.impl.EventKeyboardInput;
import vcore.events.impl.EventSync;
import vcore.events.impl.PacketEvent;
import vcore.events.impl.TotemPopEvent;
import vcore.features.modules.Module;
import vcore.injection.accesors.IInteractionManager;
import vcore.setting.Setting;
import vcore.setting.impl.Bind;
import vcore.setting.impl.SettingGroup;
import vcore.utility.player.InteractionUtility;
import vcore.utility.player.InventoryUtility;
import vcore.utility.player.MovementUtility;
import vcore.utility.player.PlayerUtility;
import vcore.utility.player.SearchInvResult;
import vcore.utility.render.Render2DEngine;
import vcore.utility.render.Render3DEngine;

public class AutoCart extends Module {
   private static final Color CROSSBOW_DEBUG_FILL = new Color(255, 0, 0, 100);
   private static final int HOTBAR_SLOT_SIZE = 20;
   private static final int HOTBAR_Y_OFFSET = 22;
   private static final int HOTBAR_LEFT_OFFSET = 91;
   private static final int TICK_LENGTH_MS = 50;
   private static final long CART_AURA_TARGET_MEMORY_MS = 1000L;
   private final Setting<AutoCart.Mode> mode = new Setting<>("Mode", AutoCart.Mode.Bow);
   private final Setting<Float> maxDistance = new Setting<>("Max Distance", 4.5F, 2.0F, 6.0F, v -> this.mode.is(AutoCart.Mode.Bow));
   private final Setting<Integer> startDelay = new Setting<>("Start Delay", 25, 0, 60, v -> this.mode.is(AutoCart.Mode.Bow));
   private final Setting<Bind> activeBind = new Setting<>("Active Bind", new Bind(-1, false, false), v -> this.mode.is(AutoCart.Mode.CrossBow));
   private final Setting<Integer> delay = new Setting<>("Delay", 25, 0, 100, v -> this.mode.is(AutoCart.Mode.Bow) || this.mode.is(AutoCart.Mode.CrossBow));
   private final Setting<Integer> cartAuraDelay = new Setting<>("Cart Aura Delay", 0, 0, 20, v -> this.isCartAuraEnabled());
   private final Setting<Integer> refillSlot = new Setting<>("Slot", 9, 1, 9, v -> this.isRefillSlotVisible());
   private final Setting<Boolean> swapBack = new Setting<>("Swap Back", true);
   private final Setting<Boolean> totemCheck = new Setting<>("Totem Check", true, v -> this.isCartAuraEnabled());
   private final Setting<Boolean> changeLook = new Setting<>("Change Look", false);
   private final Setting<Boolean> cartAura = new Setting<>("Cart Aura", false, v -> this.mode.is(AutoCart.Mode.CrossBow));
   private final Setting<AutoCart.ReFillMode> reFill = new Setting<>("ReFill", AutoCart.ReFillMode.None);
   private final Setting<SettingGroup> cartAuraTargets = new Setting<>(
      "Target", new SettingGroup(false, 0), v -> this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura.getValue()
   );
   private final Setting<Boolean> cartAuraTarget = new Setting<>("Aura Target", true, v -> this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura.getValue())
      .addToGroup(this.cartAuraTargets);
   private final Setting<Boolean> cartOtherPlayer = new Setting<>("Other Player", false, v -> this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura.getValue())
      .addToGroup(this.cartAuraTargets);
   private volatile float[] silentRotation = null;
   private volatile boolean rotating = false;
   private boolean crossBowPressed = false;
   private volatile boolean cartAuraExecuting = false;
   private boolean scheduledRefillSwap = false;
   private int scheduledRefillInventorySlot = -1;
   private int scheduledRefillHotbarSlot = -1;
   private long scheduledRefillAt = -1L;
   private UUID lastAuraTargetUuid = null;
   private int lastAuraTargetId = -1;
   private long lastAuraTargetAt = -1L;
   private static final String REFILL_BLOCK_OWNER = "autocart_refill";
   private static final long LEGIT_REFILL_DELAY_MS = 5L;

   public AutoCart() {
      super("AutoCart", "Automates TNT minecart combat setups.", Module.Category.COMBAT);
   }

   private boolean isCartAuraEnabled() {
      return this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura != null && this.cartAura.getValue();
   }

   private boolean isRefillSlotVisible() {
      return this.reFill != null && this.reFill.getValue() != AutoCart.ReFillMode.None;
   }

   private void runOnClientThreadAndWait(Runnable action) {
      if (mc.isOnThread()) {
         action.run();
      } else {
         CountDownLatch latch = new CountDownLatch(1);
         AtomicReference<RuntimeException> errorRef = new AtomicReference<>();
         mc.execute(() -> {
            try {
               action.run();
            } catch (RuntimeException error) {
               errorRef.set(error);
            } finally {
               latch.countDown();
            }
         });

         try {
            latch.await();
         } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
         }

         RuntimeException error = errorRef.get();
         if (error != null) {
            throw error;
         }
      }
   }

   private <T> T callOnClientThreadAndWait(Supplier<T> supplier) {
      AtomicReference<T> resultRef = new AtomicReference<>();
      this.runOnClientThreadAndWait(() -> resultRef.set(supplier.get()));
      return resultRef.get();
   }

   @Override
   public void onEnable() {
      this.resetState();
   }

   @Override
   public void onDisable() {
      this.resetState();
   }

   private void resetState() {
      this.silentRotation = null;
      this.rotating = false;
      this.crossBowPressed = false;
      if (this.cartAuraExecuting) {
         ModuleManager.aura.externalPause = false;
      }

      this.cartAuraExecuting = false;
      this.lastAuraTargetUuid = null;
      this.lastAuraTargetId = -1;
      this.lastAuraTargetAt = -1L;
      this.clearRefillState();
   }

   @EventHandler
   public void onSync(EventSync e) {
      if (!fullNullCheck()) {
         if (this.rotating && this.silentRotation != null) {
            mc.player.setYaw(this.silentRotation[0]);
            mc.player.setPitch(this.silentRotation[1]);
         }
      }
   }

   @EventHandler(priority = -200)
   public void onPlayerMove(EventFixVelocity event) {
      float[] rotation = this.getAutoCartMoveFixRotation();
      if (rotation != null) {
         event.setVelocity(this.fixMovement(rotation[0], event.getMovementInput(), event.getSpeed()));
      }
   }

   @EventHandler(priority = -200)
   public void onKeyboardInput(EventKeyboardInput event) {
      float[] rotation = this.getAutoCartMoveFixRotation();
      if (rotation != null) {
         float moveForward = mc.player.input.movementForward;
         float moveSideways = mc.player.input.movementSideways;
         float delta = (mc.player.getYaw() - rotation[0]) * (float) (Math.PI / 180.0);
         float cos = MathHelper.cos(delta);
         float sin = MathHelper.sin(delta);
         mc.player.input.movementSideways = Math.round(moveSideways * cos - moveForward * sin);
         mc.player.input.movementForward = Math.round(moveForward * cos + moveSideways * sin);
      }
   }

   @EventHandler
   public void onPacketSendPost(PacketEvent.@NotNull SendPost event) {
      if (!fullNullCheck() && this.mode.is(AutoCart.Mode.Bow)) {
         if (event.getPacket() instanceof PlayerActionC2SPacket action
            && action.getAction() == Action.RELEASE_USE_ITEM
            && mc.player.getMainHandStack().getItem() == Items.BOW) {
            this.executeBowMode();
         }
      }
   }

   @EventHandler(priority = 200)
   public void onPacketReceive(PacketEvent.@NotNull Receive event) {
      if (!fullNullCheck() && this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura.getValue()) {
         if (event.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35 && packet.getEntity(mc.world) instanceof PlayerEntity player
            )
          {
            this.handleCartAuraPop(player);
         }
      }
   }

   @Override
   public void onUpdate() {
      if (!fullNullCheck()) {
         this.handleScheduledRefill();
         this.handleRefill();
         if (this.mode.is(AutoCart.Mode.CrossBow)) {
            this.updateCartAuraTargetMemory();
            boolean pressed = this.isKeyPressed(this.activeBind);
            if (pressed && !this.crossBowPressed) {
               this.crossBowPressed = true;
               this.executeCrossBowMode();
            }

            if (!pressed) {
               this.crossBowPressed = false;
            }
         }
      }
   }

   @Override
   public void onRender2D(DrawContext context) {
      if (!fullNullCheck() && this.mode.is(AutoCart.Mode.CrossBow) && !mc.options.hudHidden) {
         if (this.hasCrossbowDebugBaseRequirements()) {
            this.renderCrossbowDebug(context);
         }
      }
   }

   private void handleRefill() {
      if (this.reFill.is(AutoCart.ReFillMode.None)) {
         this.clearRefillState();
      } else if (!this.scheduledRefillSwap && mc.currentScreen == null && mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
         int targetHotbarSlot = this.refillSlot.getValue() - 1;
         if (mc.player.getInventory().getStack(targetHotbarSlot).getItem() != Items.TNT_MINECART) {
            SearchInvResult cartResult = InventoryUtility.findItemInInventory(Items.TNT_MINECART);
            if (cartResult.found()) {
               if (this.reFill.is(AutoCart.ReFillMode.Legit)) {
                  this.handleLegitRefill(cartResult.slot(), targetHotbarSlot);
               } else {
                  this.performRefillSwap(cartResult.slot(), targetHotbarSlot);
               }
            }
         }
      }
   }

   private void handleLegitRefill(int inventorySlot, int hotbarSlot) {
      if (MovementUtility.isMoving()) {
         InputBlocker.block("autocart_refill");
         this.scheduleRefillSwap(inventorySlot, hotbarSlot, 5L);
      } else {
         this.performRefillSwap(inventorySlot, hotbarSlot);
      }
   }

   private void scheduleRefillSwap(int inventorySlot, int hotbarSlot, long delayMs) {
      if (inventorySlot == -1) {
         this.clearRefillState();
      } else {
         this.scheduledRefillSwap = true;
         this.scheduledRefillInventorySlot = inventorySlot;
         this.scheduledRefillHotbarSlot = hotbarSlot;
         this.scheduledRefillAt = System.currentTimeMillis() + delayMs;
      }
   }

   private void handleScheduledRefill() {
      if (this.scheduledRefillSwap && System.currentTimeMillis() >= this.scheduledRefillAt) {
         try {
            this.performRefillSwap(this.scheduledRefillInventorySlot, this.scheduledRefillHotbarSlot);
         } finally {
            this.clearRefillState();
         }
      }
   }

   private void performRefillSwap(int inventorySlot, int hotbarSlot) {
      if (inventorySlot != -1 && hotbarSlot >= 0 && hotbarSlot <= 8) {
         if (mc.currentScreen == null && mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            if (mc.player.getInventory().getStack(hotbarSlot).getItem() != Items.TNT_MINECART) {
               clickSlot(inventorySlot, hotbarSlot, SlotActionType.SWAP);
            }
         }
      }
   }

   private void clearRefillState() {
      this.scheduledRefillSwap = false;
      this.scheduledRefillInventorySlot = -1;
      this.scheduledRefillHotbarSlot = -1;
      this.scheduledRefillAt = -1L;
      InputBlocker.unblock("autocart_refill");
   }

   @EventHandler
   public void onTotemPop(@NotNull TotemPopEvent event) {
      this.handleCartAuraPop(event.getEntity());
   }

   private void handleCartAuraPop(PlayerEntity popTarget) {
      if (!fullNullCheck()) {
         if (this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura.getValue() && !this.cartAuraExecuting) {
            if (popTarget != null && popTarget != mc.player) {
               this.updateCartAuraTargetMemory();
               boolean isAuraTarget = this.cartAuraTarget.getValue() && this.isRememberedAuraTarget(popTarget);
               boolean isOtherPlayer = false;
               if (this.cartOtherPlayer.getValue()) {
                  isOtherPlayer = !Managers.FRIEND.isFriend(popTarget) && mc.player.getPos().distanceTo(popTarget.getPos()) <= 6.0;
               }

               if (isAuraTarget || isOtherPlayer) {
                  mc.execute(() -> {
                     if (!fullNullCheck() && !this.cartAuraExecuting && !this.isDisabled()) {
                        if (this.mode.is(AutoCart.Mode.CrossBow) && this.cartAura.getValue()) {
                           if (!popTarget.isRemoved()) {
                              if (!this.totemCheck.getValue() || !this.isHoldingTotem(popTarget)) {
                                 if (this.hasBasicCartAuraResources()) {
                                    this.executeCartAura(popTarget);
                                 }
                              }
                           }
                        }
                     }
                  });
               }
            }
         }
      }
   }

   private void executeCartAura(PlayerEntity target) {
      this.cartAuraExecuting = true;
      int delayMs = this.delay.getValue();
      int startDelayMs = this.cartAuraDelay.getValue() * 50;
      Managers.ASYNC.run(() -> {
         try {
            if (startDelayMs > 0) {
               AsyncManager.sleep(startDelayMs);
            }

            if (fullNullCheck() || this.isDisabled() || !this.mode.is(AutoCart.Mode.CrossBow) || !this.cartAura.getValue()) {
               return;
            }

            int[] prevSlotRef = new int[]{-1};
            AutoCart.CartAuraPlan plan = this.callOnClientThreadAndWait(() -> {
               if (this.totemCheck.getValue() && this.isHoldingTotem(target)) {
                  return null;
               }

               AutoCart.CartAuraPlan createdPlan = this.createCartAuraPlan(target);
               if (mc.player != null) {
                  prevSlotRef[0] = mc.player.getInventory().selectedSlot;
               }

               return createdPlan;
            });
            if (plan != null) {
               BlockPos basePos = plan.basePos();
               int prevSlot = prevSlotRef[0];
               ModuleManager.aura.externalPause = true;
               Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);
               this.runOnClientThreadAndWait(() -> this.applyRotation(InteractionUtility.calculateAngle(placeVec)));
               AsyncManager.sleep(delayMs);
               if (!plan.railExists()) {
                  this.runOnClientThreadAndWait(() -> {
                     if (!this.isRailBlock(mc.world.getBlockState(basePos.up()).getBlock())) {
                        this.selectHotbarLegit(plan.railResult().slot());
                        this.placeRailOn(basePos);
                     }
                  });
                  AsyncManager.sleep(delayMs);
               }

               this.runOnClientThreadAndWait(() -> {
                  this.selectHotbarLegit(plan.cartResult().slot());
                  this.placeMinecartOn(basePos);
               });
               AsyncManager.sleep(delayMs);
               if (!plan.hasFlame()) {
                  BlockPos firePos = this.findFirePosition(basePos);
                  if (firePos != null) {
                     this.runOnClientThreadAndWait(() -> {
                        Vec3d fireVec = new Vec3d(firePos.getX() + 0.5, firePos.getY() + 1.0, firePos.getZ() + 0.5);
                        this.applyRotation(InteractionUtility.calculateAngle(fireVec));
                        this.selectHotbarLegit(plan.flintResult().slot());
                        BlockHitResult fireHit = new BlockHitResult(fireVec, Direction.UP, firePos, false);
                        this.interactWithBlock(fireHit);
                     });
                     AsyncManager.sleep(delayMs);
                  }
               }

               this.runOnClientThreadAndWait(() -> {
                  Vec3d minecartCenter = new Vec3d(basePos.getX() + 0.5, basePos.getY() + 1.5, basePos.getZ() + 0.5);
                  float[] shootAngle = InteractionUtility.calculateAngle(minecartCenter);
                  this.applyRotation(shootAngle);
                  this.selectHotbarLegit(plan.crossbowResult().slot());
                  this.interactWithItem();
                  mc.player.swingHand(Hand.MAIN_HAND);
               });
               AsyncManager.sleep(delayMs);
               this.runOnClientThreadAndWait(() -> {
                  if (this.swapBack.getValue()) {
                     this.selectHotbarLegit(prevSlot);
                  }

                  this.endRotation();
               });
               return;
            }
         } finally {
            ModuleManager.aura.externalPause = false;
            this.cartAuraExecuting = false;
         }
      });
   }

   private void updateCartAuraTargetMemory() {
      if (this.isCartAuraEnabled() && this.cartAuraTarget.getValue()) {
         if (Aura.target instanceof PlayerEntity player && player != mc.player && !player.isRemoved()) {
            this.lastAuraTargetUuid = player.getUuid();
            this.lastAuraTargetId = player.getId();
            this.lastAuraTargetAt = System.currentTimeMillis();
         }
      }
   }

   private boolean isRememberedAuraTarget(PlayerEntity player) {
      if (player == null) {
         return false;
      } else if (this.isSameAuraTarget(Aura.target, player)) {
         return true;
      } else if (ModuleManager.aura.isOn() && this.isSameAuraTarget(ModuleManager.aura.findTarget(), player)) {
         return true;
      } else {
         return this.lastAuraTargetUuid != null && System.currentTimeMillis() - this.lastAuraTargetAt <= 1000L
            ? player.getUuid().equals(this.lastAuraTargetUuid) || player.getId() == this.lastAuraTargetId
            : false;
      }
   }

   private boolean isSameAuraTarget(Entity auraTarget, PlayerEntity player) {
      return !(auraTarget instanceof PlayerEntity auraPlayer)
         ? false
         : auraPlayer == player || auraPlayer.getId() == player.getId() || auraPlayer.getUuid().equals(player.getUuid());
   }

   private void executeBowMode() {
      if (!fullNullCheck()) {
         SearchInvResult bowResult = InventoryUtility.findItemInHotBar(Items.BOW);
         SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
         if (bowResult.found() && cartResult.found()) {
            BlockPos targetPos = this.calcBowTrajectory(mc.player.getYaw());
            if (targetPos != null) {
               BlockPos basePos = this.getCartBasePos(targetPos);
               boolean railExists = this.isRailBlock(mc.world.getBlockState(basePos.up()).getBlock());
               if (railExists || this.findRailInHotBar().found()) {
                  float distSq = PlayerUtility.squaredDistanceFromEyes(basePos.up().toCenterPos());
                  float maxDistSq = this.maxDistance.getValue() * this.maxDistance.getValue();
                  float safeDistSq = 4.0F;
                  if (!(distSq > maxDistSq) && !(distSq < safeDistSq)) {
                     Managers.ASYNC.run(() -> this.executeBowPlacement(targetPos), this.startDelay.getValue().intValue());
                  }
               }
            }
         }
      }
   }

   private void executeBowPlacement(@NotNull BlockPos targetPos) {
      if (!fullNullCheck() && this.mode.is(AutoCart.Mode.Bow)) {
         BlockPos basePos = this.getCartBasePos(targetPos);
         boolean railExists = this.isRailBlock(mc.world.getBlockState(basePos.up()).getBlock());
         SearchInvResult railResult = this.findRailInHotBar();
         SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
         if (cartResult.found()) {
            if (railExists || railResult.found()) {
               int prevSlot = mc.player.getInventory().selectedSlot;
               int delayMs = this.delay.getValue();
               Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);
               mc.executeSync(() -> this.applyRotation(InteractionUtility.calculateAngle(placeVec)));
               AsyncManager.sleep(delayMs);
               if (!railExists) {
                  mc.executeSync(() -> {
                     if (!this.isRailBlock(mc.world.getBlockState(basePos.up()).getBlock())) {
                        this.selectHotbarLegit(railResult.slot());
                        this.placeRailOn(basePos);
                     }
                  });
                  AsyncManager.sleep(delayMs);
               }

               mc.executeSync(() -> {
                  this.selectHotbarLegit(cartResult.slot());
                  this.placeMinecartOn(basePos);
               });
               AsyncManager.sleep(delayMs);
               mc.executeSync(() -> {
                  if (this.swapBack.getValue()) {
                     this.selectHotbarLegit(prevSlot);
                  }

                  this.endRotation();
               });
            }
         }
      }
   }

   @NotNull
   private BlockPos getCartBasePos(@NotNull BlockPos targetPos) {
      BlockState targetState = mc.world.getBlockState(targetPos);
      return !this.isRailBlock(targetState.getBlock()) && !targetState.isReplaceable() ? targetPos : targetPos.down();
   }

   private boolean hasBasicCartAuraResources() {
      SearchInvResult crossbowResult = this.findLoadedCrossbowInHotBar();
      SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
      if (crossbowResult.found() && cartResult.found()) {
         boolean hasFlame = this.hasFlameEnchant(mc.player.getInventory().getStack(crossbowResult.slot()));
         return hasFlame || InventoryUtility.findItemInHotBar(Items.FLINT_AND_STEEL).found();
      } else {
         return false;
      }
   }

   @Nullable
   private AutoCart.CartAuraPlan createCartAuraPlan(PlayerEntity target) {
      if (!this.isValidCartAuraTarget(target)) {
         return null;
      }

      BlockPos basePos = this.findCartAuraPosition(target);
      if (basePos == null) {
         return null;
      }

      SearchInvResult crossbowResult = this.findLoadedCrossbowInHotBar();
      SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
      if (crossbowResult.found() && cartResult.found()) {
         boolean hasFlame = this.hasFlameEnchant(mc.player.getInventory().getStack(crossbowResult.slot()));
         SearchInvResult flintResult = InventoryUtility.findItemInHotBar(Items.FLINT_AND_STEEL);
         if (!hasFlame && !flintResult.found()) {
            return null;
         }

         boolean railExists = this.isRailBlock(mc.world.getBlockState(basePos.up()).getBlock());
         SearchInvResult railResult = this.findRailInHotBar();
         return !railExists && !railResult.found()
            ? null
            : new AutoCart.CartAuraPlan(basePos, crossbowResult, railResult, cartResult, flintResult, hasFlame, railExists);
      } else {
         return null;
      }
   }

   private boolean isValidCartAuraTarget(PlayerEntity target) {
      return target != null && target != mc.player && !target.isRemoved();
   }

   private boolean isHoldingTotem(PlayerEntity player) {
      return player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING || player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;
   }

   private void executeCrossBowMode() {
      if (!fullNullCheck()) {
         SearchInvResult crossbowResult = this.findLoadedCrossbowInHotBar();
         SearchInvResult railResult = this.findRailInHotBar();
         SearchInvResult cartResult = InventoryUtility.findItemInHotBar(Items.TNT_MINECART);
         if (crossbowResult.found() && railResult.found() && cartResult.found()) {
            boolean hasFlame = this.hasFlameEnchant(mc.player.getInventory().getStack(crossbowResult.slot()));
            SearchInvResult flintResult = InventoryUtility.findItemInHotBar(Items.FLINT_AND_STEEL);
            if (hasFlame || flintResult.found()) {
               BlockHitResult rayResult = this.rayTraceFromEyes(4.5);
               if (rayResult != null && rayResult.getType() == Type.BLOCK) {
                  BlockPos hitPos = rayResult.getBlockPos();
                  if (!(PlayerUtility.squaredDistanceFromEyes(hitPos.toCenterPos()) > 20.25F)) {
                     BlockPos basePos;
                     if (mc.world.getBlockState(hitPos).isReplaceable()) {
                        basePos = hitPos.down();
                     } else {
                        basePos = hitPos;
                     }

                     BlockPos fireBlockPos = null;
                     if (!hasFlame) {
                        fireBlockPos = this.findFirePosition(basePos);
                        if (fireBlockPos == null) {
                           return;
                        }
                     }

                     BlockPos finalFireBlockPos = fireBlockPos;
                     int prevSlot = mc.player.getInventory().selectedSlot;
                     int delayMs = this.delay.getValue();
                     Managers.ASYNC.run(() -> {
                        Vec3d placeVec = new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5);
                        mc.executeSync(() -> this.applyRotation(InteractionUtility.calculateAngle(placeVec)));
                        AsyncManager.sleep(delayMs);
                        mc.executeSync(() -> {
                           if (!this.isRailBlock(mc.world.getBlockState(basePos.up()).getBlock())) {
                              this.selectHotbarLegit(railResult.slot());
                              this.placeRailOn(basePos);
                           }
                        });
                        AsyncManager.sleep(delayMs);
                        mc.executeSync(() -> {
                           this.selectHotbarLegit(cartResult.slot());
                           this.placeMinecartOn(basePos);
                        });
                        AsyncManager.sleep(delayMs);
                        if (!hasFlame && finalFireBlockPos != null) {
                           mc.executeSync(() -> {
                              Vec3d fireVec = new Vec3d(finalFireBlockPos.getX() + 0.5, finalFireBlockPos.getY() + 1.0, finalFireBlockPos.getZ() + 0.5);
                              this.applyRotation(InteractionUtility.calculateAngle(fireVec));
                              this.selectHotbarLegit(flintResult.slot());
                              BlockHitResult fireHit = new BlockHitResult(fireVec, Direction.UP, finalFireBlockPos, false);
                              this.interactWithBlock(fireHit);
                           });
                           AsyncManager.sleep(delayMs);
                        }

                        mc.executeSync(() -> {
                           Vec3d minecartCenter = new Vec3d(basePos.getX() + 0.5, basePos.getY() + 1.5, basePos.getZ() + 0.5);
                           float[] shootAngle = InteractionUtility.calculateAngle(minecartCenter);
                           this.applyRotation(shootAngle);
                           this.selectHotbarLegit(crossbowResult.slot());
                           this.interactWithItem();
                           mc.player.swingHand(Hand.MAIN_HAND);
                        });
                        AsyncManager.sleep(delayMs);
                        mc.executeSync(() -> {
                           if (this.swapBack.getValue()) {
                              this.selectHotbarLegit(prevSlot);
                           }

                           this.endRotation();
                        });
                     });
                  }
               }
            }
         }
      }
   }

   private void applyRotation(float[] angle) {
      if (this.changeLook.getValue()) {
         mc.player.setYaw(angle[0]);
         mc.player.setPitch(angle[1]);
      } else {
         this.silentRotation = angle;
         this.rotating = true;
      }
   }

   private void endRotation() {
      this.silentRotation = null;
      this.rotating = false;
   }

   public boolean isAutoCartMoveFixActive() {
      return this.getAutoCartMoveFixRotation() != null;
   }

   @Nullable
   private float[] getAutoCartMoveFixRotation() {
      float[] rotation = this.silentRotation;
      return this.isOn() && !fullNullCheck() && !this.changeLook.getValue() && this.rotating && rotation != null && !mc.player.isRiding() ? rotation : null;
   }

   private Vec3d fixMovement(float yaw, Vec3d movementInput, float speed) {
      double lengthSquared = movementInput.lengthSquared();
      if (lengthSquared < 1.0E-7) {
         return Vec3d.ZERO;
      }

      Vec3d movement = (lengthSquared > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
      float sin = MathHelper.sin(yaw * (float) (Math.PI / 180.0));
      float cos = MathHelper.cos(yaw * (float) (Math.PI / 180.0));
      return new Vec3d(movement.x * cos - movement.z * sin, movement.y, movement.z * cos + movement.x * sin);
   }

   private void selectHotbarLegit(int slot) {
      if (mc.player != null && mc.interactionManager != null && slot >= 0 && slot <= 8) {
         mc.player.getInventory().selectedSlot = slot;
         ((IInteractionManager)mc.interactionManager).syncSlot();
      }
   }

   private void runWithInteractionRotation(@Nullable float[] angle, Runnable action) {
      if (mc.player != null) {
         if (!this.changeLook.getValue() && angle != null) {
            float prevYaw = mc.player.getYaw();
            float prevPitch = mc.player.getPitch();
            mc.player.setYaw(angle[0]);
            mc.player.setPitch(angle[1]);

            try {
               action.run();
            } finally {
               mc.player.setYaw(prevYaw);
               mc.player.setPitch(prevPitch);
            }
         } else {
            action.run();
         }
      }
   }

   private void interactWithItem() {
      if (mc.player != null && mc.interactionManager != null) {
         this.runWithInteractionRotation(this.silentRotation, () -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND));
      }
   }

   private void placeRailOn(BlockPos basePos) {
      if (mc.world != null && mc.player != null && mc.interactionManager != null) {
         BlockHitResult hitResult = new BlockHitResult(new Vec3d(basePos.getX() + 0.5, basePos.up().getY(), basePos.getZ() + 0.5), Direction.UP, basePos, false);
         this.interactWithBlock(hitResult);
      }
   }

   private void placeMinecartOn(BlockPos basePos) {
      if (mc.world != null && mc.player != null && mc.interactionManager != null) {
         BlockHitResult hitResult = new BlockHitResult(
            new Vec3d(basePos.getX() + 0.5, basePos.up().getY() + 0.125, basePos.getZ() + 0.5), Direction.UP, basePos.up(), false
         );
         this.interactWithBlock(hitResult);
      }
   }

   private void interactWithBlock(BlockHitResult hitResult) {
      this.runWithInteractionRotation(this.silentRotation, () -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult));
      mc.player.swingHand(Hand.MAIN_HAND);
   }

   @Nullable
   private BlockPos calcBowTrajectory(float yaw) {
      if (mc.player != null && mc.world != null) {
         double x = Render2DEngine.interpolate(mc.player.prevX, mc.player.getX(), Render3DEngine.getTickDelta());
         double y = Render2DEngine.interpolate(mc.player.prevY, mc.player.getY(), Render3DEngine.getTickDelta());
         double z = Render2DEngine.interpolate(mc.player.prevZ, mc.player.getZ(), Render3DEngine.getTickDelta());
         y += mc.player.getEyeHeight(mc.player.getPose()) - 0.1000000014901161;
         float pitch = mc.player.getPitch();
         double motionX = -MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
         double motionY = -MathHelper.sin(pitch / 180.0F * (float) Math.PI);
         double motionZ = MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI);
         float power = mc.player.getItemUseTime() / 20.0F;
         power = (power * power + power * 2.0F) / 3.0F;
         if (power > 1.0F) {
            power = 1.0F;
         }

         if (power < 0.1F) {
            return null;
         }

         float dist = MathHelper.sqrt((float)(motionX * motionX + motionY * motionY + motionZ * motionZ));
         motionX /= dist;
         motionY /= dist;
         motionZ /= dist;
         float pow = power * 3.0F;
         motionX *= pow;
         motionY *= pow;
         motionZ *= pow;
         if (!mc.player.isOnGround()) {
            motionY += mc.player.getVelocity().getY();
         }

         for (int i = 0; i < 300; i++) {
            Vec3d lastPos = new Vec3d(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;
            motionX *= 0.99;
            motionY *= 0.99;
            motionZ *= 0.99;
            motionY -= 0.05F;

            for (Entity ent : mc.world.getEntities()) {
               if (!(ent instanceof ArrowEntity)
                  && !ent.equals(mc.player)
                  && ent.getBoundingBox().intersects(new Box(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3))) {
                  return null;
               }
            }

            Vec3d pos = new Vec3d(x, y, z);
            BlockHitResult bhr = mc.world.raycast(new RaycastContext(lastPos, pos, ShapeType.OUTLINE, FluidHandling.NONE, mc.player));
            if (bhr != null && bhr.getType() == Type.BLOCK) {
               return bhr.getBlockPos();
            }

            if (y <= -65.0) {
               break;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   private BlockPos findCartAuraPosition(Entity target) {
      if (mc.player != null && mc.world != null) {
         Vec3d playerEyes = InteractionUtility.getEyesPos(mc.player);
         Box targetBox = target.getBoundingBox();
         Vec3d targetFeet = new Vec3d(target.getX(), targetBox.minY, target.getZ());
         BlockPos targetBlockPos = target.getBlockPos();
         int r = 4;
         BlockPos bestPos = null;
         double bestDist = Double.MAX_VALUE;

         for (int x = targetBlockPos.getX() - r; x <= targetBlockPos.getX() + r; x++) {
            for (int z = targetBlockPos.getZ() - r; z <= targetBlockPos.getZ() + r; z++) {
               for (int y = targetBlockPos.getY() - r; y <= targetBlockPos.getY(); y++) {
                  BlockPos bp = new BlockPos(x, y, z);
                  if (mc.world.getBlockState(bp).isSolid() && !(bp.getY() + 1 > targetBox.minY)) {
                     BlockState aboveState = mc.world.getBlockState(bp.up());
                     if (aboveState.isReplaceable() || this.isRailBlock(aboveState.getBlock())) {
                        Vec3d surfacePos = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
                        if (!(PlayerUtility.squaredDistanceFromEyes(surfacePos) > 20.25F)) {
                           BlockHitResult blockCheck = mc.world
                              .raycast(new RaycastContext(playerEyes, surfacePos, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
                           if ((blockCheck == null || blockCheck.getType() != Type.BLOCK || blockCheck.getBlockPos().equals(bp))
                              && this.isPathClearOfPlayers(playerEyes, surfacePos, target)) {
                              Vec3d cartCenter = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5);
                              BlockHitResult damageCheck = mc.world
                                 .raycast(new RaycastContext(cartCenter, targetFeet, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
                              if (damageCheck == null || damageCheck.getType() != Type.BLOCK) {
                                 double distToTarget = cartCenter.squaredDistanceTo(targetFeet);
                                 if (distToTarget < bestDist) {
                                    bestDist = distToTarget;
                                    bestPos = bp;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return bestPos;
      } else {
         return null;
      }
   }

   private boolean isPathClearOfPlayers(Vec3d from, Vec3d to, Entity exclude) {
      if (mc.world == null) {
         return true;
      }

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player != mc.player && player != exclude && player.getBoundingBox().raycast(from, to).isPresent()) {
            return false;
         }
      }

      return true;
   }

   private SearchInvResult findRailInHotBar() {
      return InventoryUtility.findItemInHotBar(Items.RAIL, Items.ACTIVATOR_RAIL, Items.DETECTOR_RAIL, Items.POWERED_RAIL);
   }

   private void renderCrossbowDebug(DrawContext context) {
      int hotbarLeft = mc.getWindow().getScaledWidth() / 2 - 91;
      int hotbarTop = mc.getWindow().getScaledHeight() - 22;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = mc.player.getInventory().getStack(slot);
         if (this.isCrossbowDebugCandidate(stack)) {
            float slotX = hotbarLeft + slot * 20 + 1;
            float slotY = hotbarTop + 1;
            Render2DEngine.drawRect(context.getMatrices(), slotX, slotY, 20.0F, 20.0F, CROSSBOW_DEBUG_FILL);
         }
      }
   }

   private boolean hasCrossbowDebugBaseRequirements() {
      if (this.findRailInHotBar().found() && InventoryUtility.findItemInHotBar(Items.TNT_MINECART).found()) {
         if (!this.hasArrowAmmoInInventory()) {
            return false;
         }

         for (int slot = 0; slot < 9; slot++) {
            if (this.isCrossbowDebugCandidate(mc.player.getInventory().getStack(slot))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isCrossbowDebugCandidate(ItemStack stack) {
      return !this.isUnloadedCrossbow(stack) ? false : this.hasFlameEnchant(stack) || InventoryUtility.findItemInHotBar(Items.FLINT_AND_STEEL).found();
   }

   private boolean hasArrowAmmoInInventory() {
      for (ItemStack stack : mc.player.getInventory().main) {
         if (this.isArrowStack(stack)) {
            return true;
         }
      }

      for (ItemStack stack : mc.player.getInventory().offHand) {
         if (this.isArrowStack(stack)) {
            return true;
         }
      }

      return false;
   }

   private boolean isArrowStack(ItemStack stack) {
      return !stack.isEmpty() && stack.getItem() instanceof ArrowItem;
   }

   private boolean isUnloadedCrossbow(ItemStack stack) {
      return stack.getItem() == Items.CROSSBOW && !this.isCrossbowCharged(stack);
   }

   private boolean isCrossbowCharged(ItemStack stack) {
      return stack.getItem() == Items.CROSSBOW
         && stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null
         && !((ChargedProjectilesComponent)stack.get(DataComponentTypes.CHARGED_PROJECTILES)).isEmpty();
   }

   private SearchInvResult findLoadedCrossbowInHotBar() {
      return InventoryUtility.findInHotBar(this::isCrossbowCharged);
   }

   private boolean isRailBlock(Block block) {
      return block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL || block == Blocks.ACTIVATOR_RAIL;
   }

   private boolean hasFlameEnchant(ItemStack stack) {
      return mc.world == null
         ? false
         : EnchantmentHelper.getLevel(
               (RegistryEntry)mc.world.getRegistryManager().get(Enchantments.FLAME.getRegistryRef()).getEntry(Enchantments.FLAME).get(), stack
            )
            > 0;
   }

   @Nullable
   private BlockHitResult rayTraceFromEyes(double maxRange) {
      if (mc.player != null && mc.world != null) {
         Vec3d eyePos = mc.player.getEyePos();
         Vec3d lookVec = mc.player.getRotationVec(1.0F);
         Vec3d endPos = eyePos.add(lookVec.multiply(maxRange));
         return mc.world.raycast(new RaycastContext(eyePos, endPos, ShapeType.OUTLINE, FluidHandling.NONE, mc.player));
      } else {
         return null;
      }
   }

   @Nullable
   private BlockPos findFirePosition(BlockPos targetPos) {
      if (mc.player != null && mc.world != null) {
         Vec3d eyePos = mc.player.getEyePos();
         Vec3d minecartPos = new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 1.5, targetPos.getZ() + 0.5);
         Vec3d dir = minecartPos.subtract(eyePos).normalize();
         Direction[] horizontals = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
         BlockPos bestPos = null;
         double bestScore = Double.MAX_VALUE;

         for (Direction d : horizontals) {
            BlockPos candidate = targetPos.offset(d);
            if (mc.world.getBlockState(candidate).isSolid() && mc.world.isAir(candidate.up())) {
               Vec3d fireCenter = new Vec3d(candidate.getX() + 0.5, candidate.getY() + 1.0, candidate.getZ() + 0.5);
               Vec3d eyeToFire = fireCenter.subtract(eyePos);
               double t = eyeToFire.dotProduct(dir);
               if (!(t <= 0.0)) {
                  double eyeToMinecart = minecartPos.subtract(eyePos).dotProduct(dir);
                  if (!(t >= eyeToMinecart)) {
                     Vec3d closest = eyePos.add(dir.multiply(t));
                     double distToLine = closest.distanceTo(fireCenter);
                     if (distToLine < bestScore) {
                        bestScore = distToLine;
                        bestPos = candidate;
                     }
                  }
               }
            }
         }

         if (bestPos == null) {
            for (Direction d : horizontals) {
               BlockPos adj = targetPos.offset(d);
               if (mc.world.getBlockState(adj).isSolid() && mc.world.isAir(adj.up())) {
                  return adj;
               }
            }
         }

         return bestPos;
      } else {
         return null;
      }
   }

   @Override
   public String getDisplayInfo() {
      return this.mode.getValue().name();
   }

   private record CartAuraPlan(
      BlockPos basePos,
      SearchInvResult crossbowResult,
      SearchInvResult railResult,
      SearchInvResult cartResult,
      SearchInvResult flintResult,
      boolean hasFlame,
      boolean railExists
   ) {
   }

   public enum Mode {
      Bow,
      CrossBow;
   }

   public enum ReFillMode {
      None,
      Normal,
      Legit;
   }
}
