package vcore.features.modules.misc;

import java.awt.Color;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vcore.core.manager.client.ModuleManager;
import vcore.events.impl.EventSetBlockState;
import vcore.events.impl.EventSetting;
import vcore.features.modules.Module;
import vcore.setting.Setting;
import vcore.setting.impl.SettingGroup;
import vcore.utility.Timer;
import vcore.utility.render.Render3DEngine;

public class XRay extends Module {
   private final Setting<XRay.RenderMode> renderMode = new Setting<>("Render Mode", XRay.RenderMode.WallHack);
   private final Setting<SettingGroup> oreSelect = new Setting<>("Ore Select", new SettingGroup(true, 0));
   private final Setting<Boolean> coal = new Setting<>("Coal", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> copper = new Setting<>("Copper", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> iron = new Setting<>("Iron", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> gold = new Setting<>("Gold", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> redstone = new Setting<>("Redstone", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> lapis = new Setting<>("Lapis", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> quartz = new Setting<>("Quartz", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> emerald = new Setting<>("Emerald", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> diamond = new Setting<>("Diamond", false).addToGroup(this.oreSelect);
   private final Setting<Boolean> netherite = new Setting<>("Netherite", false).addToGroup(this.oreSelect);
   private static final int NORMAL_CHUNK_RANGE = 4;
   private final Timer scanTimer = new Timer();
   private final Map<Long, EnumMap<XRay.OreType, Set<BlockPos>>> normalChunks = new ConcurrentHashMap<>();

   public XRay() {
      super("XRay", "See ores through blocks.", Module.Category.MISC);
   }

   @Override
   public void onEnable() {
      this.resetCaches();
      this.reloadWorldRenderer();
   }

   @Override
   public void onDisable() {
      this.resetCaches();
      this.reloadWorldRenderer();
   }

   @Override
   public void onLogin() {
      this.resetCaches();
   }

   @Override
   public void onLogout() {
      this.resetCaches();
   }

   @Override
   public void onUpdate() {
      if (!fullNullCheck() && this.scanTimer.every(500L)) {
         if (this.renderMode.is(XRay.RenderMode.ESP)) {
            this.scanNormalChunks();
         }
      }
   }

   @Override
   public void onRender3D(MatrixStack stack) {
      if (!fullNullCheck() && !this.shouldUseWallHack() && !this.normalChunks.isEmpty()) {
         ChunkPos center = mc.player.getChunkPos();

         for (int chunkX = center.x - 4; chunkX <= center.x + 4; chunkX++) {
            for (int chunkZ = center.z - 4; chunkZ <= center.z + 4; chunkZ++) {
               this.renderNormalChunk(this.normalChunks.get(ChunkPos.toLong(chunkX, chunkZ)));
            }
         }
      }
   }

   @EventHandler
   public void onSettingChange(EventSetting event) {
      if (event.getSetting().getModule() == this) {
         this.resetCaches();
         if (event.getSetting() == this.renderMode || event.getSetting().group == this.oreSelect) {
            this.reloadWorldRenderer();
         }
      }
   }

   @EventHandler
   public void onBlockChanged(EventSetBlockState event) {
      if (this.isEnabled()) {
         BlockState state = event.getState();
         if (this.renderMode.is(XRay.RenderMode.ESP) && state != null) {
            this.updateNormalCache(event.getPos(), state.getBlock());
         }
      }
   }

   public boolean shouldUseWallHack() {
      return this.isEnabled() && this.renderMode.is(XRay.RenderMode.WallHack);
   }

   public boolean isSelectedOre(Block block) {
      XRay.OreType type = XRay.OreType.fromBlock(block);
      return type != null && this.isOreEnabled(type);
   }

   public static boolean isCheckableOre(Block block) {
      return ModuleManager.xray != null && ModuleManager.xray.isSelectedOre(block);
   }

   private void scanNormalChunks() {
      ChunkPos center = mc.player.getChunkPos();

      for (int chunkX = center.x - 4; chunkX <= center.x + 4; chunkX++) {
         for (int chunkZ = center.z - 4; chunkZ <= center.z + 4; chunkZ++) {
            long key = ChunkPos.toLong(chunkX, chunkZ);
            if (!this.normalChunks.containsKey(key)) {
               WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
               if (chunk != null) {
                  this.normalChunks.put(key, this.scanNormalChunk(chunk));
               }
            }
         }
      }
   }

   private EnumMap<XRay.OreType, Set<BlockPos>> scanNormalChunk(Chunk chunk) {
      EnumMap<XRay.OreType, Set<BlockPos>> result = new EnumMap<>(XRay.OreType.class);
      ChunkPos chunkPos = chunk.getPos();
      int startX = chunkPos.getStartX();
      int startZ = chunkPos.getStartZ();

      for (int y = mc.world.getBottomY(); y < mc.world.getTopY(); y++) {
         for (int x = startX; x <= chunkPos.getEndX(); x++) {
            for (int z = startZ; z <= chunkPos.getEndZ(); z++) {
               BlockPos pos = new BlockPos(x, y, z);
               XRay.OreType type = XRay.OreType.fromBlock(chunk.getBlockState(pos).getBlock());
               if (type != null && this.isOreEnabled(type)) {
                  result.computeIfAbsent(type, ignored -> new HashSet<>()).add(pos);
               }
            }
         }
      }

      return result;
   }

   private void updateNormalCache(BlockPos pos, Block block) {
      long chunkKey = ChunkPos.toLong(pos);
      EnumMap<XRay.OreType, Set<BlockPos>> chunk = this.normalChunks.computeIfAbsent(chunkKey, ignored -> new EnumMap<>(XRay.OreType.class));
      chunk.values().forEach(positions -> positions.remove(pos));
      XRay.OreType type = XRay.OreType.fromBlock(block);
      if (type != null && this.isOreEnabled(type)) {
         chunk.computeIfAbsent(type, ignored -> new HashSet<>()).add(pos.toImmutable());
      }
   }

   private void renderNormalChunk(@Nullable EnumMap<XRay.OreType, Set<BlockPos>> chunk) {
      if (chunk != null && !chunk.isEmpty()) {
         for (Entry<XRay.OreType, Set<BlockPos>> entry : chunk.entrySet()) {
            if (this.isOreEnabled(entry.getKey())) {
               for (BlockPos pos : entry.getValue()) {
                  this.drawEsp(pos, entry.getKey().color);
               }
            }
         }
      }
   }

   private void drawEsp(BlockPos pos, Color color) {
      Render3DEngine.OUTLINE_QUEUE.add(new Render3DEngine.OutlineAction(new Box(pos), color, 2.0F));
   }

   private void reloadWorldRenderer() {
      if (mc.worldRenderer != null) {
         mc.worldRenderer.reload();
      }
   }

   private void resetCaches() {
      this.normalChunks.clear();
   }

   private boolean isOreEnabled(@NotNull XRay.OreType type) {
      return switch (type) {
         case COAL -> this.coal.getValue();
         case COPPER -> this.copper.getValue();
         case IRON -> this.iron.getValue();
         case GOLD -> this.gold.getValue();
         case REDSTONE -> this.redstone.getValue();
         case LAPIS -> this.lapis.getValue();
         case QUARTZ -> this.quartz.getValue();
         case EMERALD -> this.emerald.getValue();
         case DIAMOND -> this.diamond.getValue();
         case NETHERITE -> this.netherite.getValue();
      };
   }

   private enum OreType {
      COAL(new Color(47, 44, 54, 220), Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
      COPPER(new Color(239, 151, 0, 220), Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
      IRON(new Color(236, 173, 119, 220), Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
      GOLD(new Color(247, 229, 30, 220), Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
      REDSTONE(new Color(245, 7, 23, 220), Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
      LAPIS(new Color(38, 97, 156, 220), Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
      QUARTZ(new Color(205, 205, 205, 220), Blocks.NETHER_QUARTZ_ORE),
      EMERALD(new Color(27, 209, 45, 220), Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
      DIAMOND(new Color(33, 244, 255, 220), Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
      NETHERITE(new Color(209, 27, 245, 220), Blocks.ANCIENT_DEBRIS);

      private final Color color;
      private final Set<Block> blocks;

      OreType(Color color, Block... blocks) {
         this.color = color;
         this.blocks = Set.of(blocks);
      }

      @Nullable
      private static XRay.OreType fromBlock(Block block) {
         for (XRay.OreType type : values()) {
            if (type.blocks.contains(block)) {
               return type;
            }
         }

         return null;
      }
   }

   private enum RenderMode {
      WallHack,
      ESP;
   }
}
