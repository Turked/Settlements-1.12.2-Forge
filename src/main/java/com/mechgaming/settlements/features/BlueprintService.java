package com.mechgaming.settlements.features;

import com.mechgaming.settlements.data.Settlement;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.*;

/** Bounded, server-side blueprints. Container NBT is never recorded or restored. */
final class BlueprintService {
    private static final Map<UUID, CachedView> VIEWS = new LinkedHashMap<UUID, CachedView>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, CachedView> entry) { return size() > 512; }
    };
    private static final class CachedView { long tick; int dimension; NBTTagCompound view; }
    private BlueprintService() { }

    static boolean handle(EntityPlayerMP p, String action, BlockPos pos, int radius, NBTTagCompound payload) {
        if (!action.equals("feature_blueprint_save") && !action.equals("feature_blueprint_preview") && !action.equals("feature_blueprint_repair")) return false;
        Settlement s = FeatureService.requireMember(p, payload);
        if (s == null) return true;
        FeatureWorldData data = FeatureWorldData.get(p.world);
        if (action.equals("feature_blueprint_save")) {
            if (!FeatureService.requireLeader(p, s)) return true;
            if (p.dimension != s.dimension || !s.contains(p.dimension, pos)) { FeatureService.tell(p, "Stand inside your settlement before saving its blueprint."); return true; }
            radius = radius <= 0 ? 8 : Math.min(31, radius);
            if ((long)(radius * 2 + 1) * (radius * 2 + 1) * (radius * 2 + 1) > FeatureService.maxBlueprintVolume) {
                FeatureService.tell(p, "Blueprint exceeds the configured " + FeatureService.maxBlueprintVolume + " block volume limit. Use a smaller radius."); return true;
            }
            NBTTagCompound blueprint = capture(p.getServerWorld(), s, pos, radius);
            if (blueprint == null) { FeatureService.tell(p, "The entire blueprint cube must be loaded, within world height and inside your claim."); return true; }
            for (int i = data.blueprints.tagCount() - 1; i >= 0; i--) if (s.id.equals(data.blueprints.getCompoundTagAt(i).getUniqueId("settlement"))) data.blueprints.removeTag(i);
            data.blueprints.appendTag(blueprint); data.markDirty();
            FeatureService.tell(p, "Saved " + blueprint.getTagList("blocks", 10).tagCount() + " ordinary blocks. Containers, doors, beds, fluids, gravity blocks and other multi-part blocks are excluded.");
        } else {
            NBTTagCompound blueprint = FeatureService.find(data.blueprints, "settlement", s.id);
            if (blueprint == null) { FeatureService.tell(p, "Your leader must save a blueprint first: /settlements blueprint save [radius]."); return true; }
            if (p.dimension != s.dimension) { FeatureService.tell(p, "Enter your settlement's dimension first."); return true; }
            if (action.equals("feature_blueprint_repair")) {
                if (!FeatureService.autoRepair) { FeatureService.tell(p, "Inventory repair is disabled by the server."); return true; }
                int count = repair(p.getServerWorld(), s, blueprint, p, 256);
                FeatureService.tell(p, "Repaired " + count + " missing blocks using your inventory. Occupied positions and containers are never replaced.");
            } else {
                Map<String, Integer> materials = materials(p.getServerWorld(), blueprint);
                FeatureService.tell(p, materials.isEmpty() ? "No missing blocks in loaded blueprint chunks." : "Missing materials: " + materials);
            }
        }
        VIEWS.remove(s.id); FeatureService.refresh(p); return true;
    }

    static NBTTagCompound capture(WorldServer world, Settlement s, BlockPos center, int radius) {
        BlockPos min = center.add(-radius, -radius, -radius), max = center.add(radius, radius, radius);
        if (min.getY() < 0 || max.getY() > 255 || !s.contains(s.dimension, min) || !s.contains(s.dimension, max)) return null;
        NBTTagCompound n = new NBTTagCompound(); n.setUniqueId("settlement", s.id); n.setInteger("dimension", s.dimension);
        n.setLong("center", center.toLong()); n.setInteger("radius", radius); NBTTagList blocks = new NBTTagList();
        for (BlockPos.MutableBlockPos cursor : BlockPos.getAllInBoxMutable(min, max)) {
            BlockPos pos = cursor.toImmutable(); if (!world.isBlockLoaded(pos)) return null;
            IBlockState state = world.getBlockState(pos); if (!safe(state)) continue;
            NBTTagCompound b = new NBTTagCompound(); b.setLong("pos", pos.toLong());
            b.setString("block", state.getBlock().getRegistryName().toString()); b.setInteger("meta", state.getBlock().getMetaFromState(state)); blocks.appendTag(b);
        }
        n.setTag("blocks", blocks); return n;
    }

    private static boolean safe(IBlockState state) {
        Block b = state.getBlock(); ResourceLocation id = b.getRegistryName();
        if (id == null || id.getResourceDomain().equals("settlements") || !state.isFullCube() || b.hasTileEntity(state)
                || b instanceof BlockFalling || b instanceof BlockLiquid || b instanceof BlockDoor || b instanceof BlockBed
                || b instanceof BlockPistonBase || b instanceof BlockPistonExtension || b instanceof BlockPistonMoving
                || b == Blocks.BEDROCK || b == Blocks.BARRIER || b == Blocks.COMMAND_BLOCK || b == Blocks.STRUCTURE_BLOCK) return false;
        Item item = Item.getItemFromBlock(b); return item != null && item != net.minecraft.init.Items.AIR;
    }

    static IBlockState state(NBTTagCompound b) {
        try {
            ResourceLocation id = new ResourceLocation(b.getString("block"));
            if (!Block.REGISTRY.containsKey(id)) return null;
            Block block = Block.REGISTRY.getObject(id); IBlockState state = block.getStateFromMeta(b.getInteger("meta")); return safe(state) ? state : null;
        } catch (RuntimeException e) { return null; }
    }

    /** freeRestore is reserved for a saved raid/war snapshot. Never fills occupied positions. */
    static int repair(WorldServer world, Settlement settlement, NBTTagCompound blueprint, EntityPlayerMP player, int limit) {
        int placed = 0; NBTTagList list = blueprint.getTagList("blocks", 10);
        for (int i = 0; i < list.tagCount() && placed < limit; i++) {
            NBTTagCompound b = list.getCompoundTagAt(i); BlockPos pos = BlockPos.fromLong(b.getLong("pos"));
            if (!settlement.contains(world.provider.getDimension(), pos) || !world.isBlockLoaded(pos) || !world.isAirBlock(pos)) continue;
            IBlockState state = state(b); if (state == null) continue;
            ItemStack cost = new ItemStack(Item.getItemFromBlock(state.getBlock()), 1, state.getBlock().damageDropped(state));
            int slot = player == null || player.isCreative() ? -1 : inventorySlot(player, cost);
            if (player != null && (!player.canPlayerEdit(pos, EnumFacing.UP, cost)
                    || player.getServer().isBlockProtected(world, pos, player)
                    || !world.mayPlace(state.getBlock(), pos, false, EnumFacing.UP, player)
                    || (!player.isCreative() && slot < 0))) continue;
            BlockSnapshot before = BlockSnapshot.getBlockSnapshot(world, pos);
            if (!world.setBlockState(pos, state, 3)) continue;
            if (player != null && ForgeEventFactory.onPlayerBlockPlace(player, before, EnumFacing.UP, EnumHand.MAIN_HAND).isCanceled()) {
                before.restore(true, false); continue;
            }
            if (slot >= 0) { player.inventory.getStackInSlot(slot).shrink(1); player.inventory.markDirty(); }
            placed++;
        }
        if (player != null && placed > 0) player.inventoryContainer.detectAndSendChanges();
        return placed;
    }

    private static int inventorySlot(EntityPlayerMP p, ItemStack cost) {
        for (int i = 0; i < p.inventory.mainInventory.size(); i++) {
            ItemStack stack = p.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == cost.getItem() && stack.getMetadata() == cost.getMetadata() && !stack.hasTagCompound()) return i;
        }
        return -1;
    }

    static Map<String, Integer> materials(WorldServer world, NBTTagCompound blueprint) {
        Map<String, Integer> map = new LinkedHashMap<>(); NBTTagList list = blueprint.getTagList("blocks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound b = list.getCompoundTagAt(i); BlockPos pos = BlockPos.fromLong(b.getLong("pos"));
            if (!world.isBlockLoaded(pos) || !world.isAirBlock(pos)) continue;
            IBlockState state = state(b); if (state == null) continue;
            String item = Item.getItemFromBlock(state.getBlock()).getRegistryName() + ":" + state.getBlock().damageDropped(state);
            map.put(item, map.getOrDefault(item, 0) + 1);
        }
        return map;
    }

    /** Freeze only blocks actually present when an event begins: stale blueprints cannot create free materials. */
    static NBTTagCompound eventSnapshot(WorldServer world, NBTTagCompound blueprint) {
        NBTTagCompound result = blueprint.copy(); NBTTagList blocks = new NBTTagList(), source = blueprint.getTagList("blocks", 10);
        result.setUniqueId("snapshot", UUID.randomUUID());
        for (int i = 0; i < source.tagCount(); i++) {
            NBTTagCompound b = source.getCompoundTagAt(i); BlockPos p = BlockPos.fromLong(b.getLong("pos"));
            if (!world.isBlockLoaded(p)) return null;
            IBlockState expected = state(b);
            if (expected != null && world.getBlockState(p).equals(expected)) blocks.appendTag(b.copy());
        }
        result.setTag("blocks", blocks); return result;
    }

    static void queueRestore(WorldServer world, Settlement s, NBTTagCompound blueprint) {
        NBTTagCompound pending = blueprint.copy(); pending.setUniqueId("settlement", s.id); pending.setInteger("dimension", s.dimension);
        FeatureWorldData data = FeatureWorldData.get(world); data.restorations.appendTag(pending); data.markDirty();
    }

    static void tick(MinecraftServer server) {
        FeatureWorldData data = FeatureWorldData.get(server.getWorld(0)); int budget = 256;
        for (int i = data.restorations.tagCount() - 1; i >= 0 && budget > 0; i--) {
            NBTTagCompound pending = data.restorations.getCompoundTagAt(i); WorldServer world = server.getWorld(pending.getInteger("dimension"));
            if (world == null) continue;
            Settlement s = FeatureService.settlement(world, pending.getUniqueId("settlement"));
            if (s == null) { data.restorations.removeTag(i); data.markDirty(); continue; }
            NBTTagList blocks = pending.getTagList("blocks", 10);
            boolean changed = false;
            for (int b = blocks.tagCount() - 1; b >= 0 && budget > 0; b--) {
                NBTTagCompound record = blocks.getCompoundTagAt(b); BlockPos pos = BlockPos.fromLong(record.getLong("pos"));
                if (!world.isBlockLoaded(pos)) continue;
                IBlockState expected = state(record);
                if (expected != null && world.isAirBlock(pos) && s.contains(s.dimension, pos)) world.setBlockState(pos, expected, 3);
                blocks.removeTag(b); budget--; data.markDirty(); changed = true; // Each position is restored at most once, including across restarts.
            }
            if (changed) FeatureEvents.invalidateSnapshot(pending);
            if (blocks.tagCount() == 0) { data.restorations.removeTag(i); data.markDirty(); }
        }
    }

    static void snapshot(EntityPlayerMP p, NBTTagCompound n, Settlement s) {
        NBTTagList missing = new NBTTagList(), materialList = new NBTTagList();
        NBTTagCompound blueprint = s == null ? null : FeatureService.find(FeatureWorldData.get(p.world).blueprints, "settlement", s.id);
        if (s != null) {
            CachedView cached = VIEWS.get(s.id);
            if (cached != null && cached.dimension == p.dimension && cached.tick == p.world.getTotalWorldTime() / 20) {
                for (String key : cached.view.getKeySet()) n.setTag(key, cached.view.getTag(key).copy());
                return;
            }
        }
        if (blueprint != null && p.dimension == s.dimension) {
            NBTTagList list = blueprint.getTagList("blocks", 10); Map<String, NBTTagCompound> materials = new LinkedHashMap<>();
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound b = list.getCompoundTagAt(i); BlockPos pos = BlockPos.fromLong(b.getLong("pos"));
                if (!p.world.isBlockLoaded(pos) || !p.world.isAirBlock(pos)) continue;
                IBlockState state = state(b); if (state == null) continue;
                if (missing.tagCount() < 256) { NBTTagCompound m = new NBTTagCompound(); m.setLong("pos", pos.toLong()); m.setInteger("state", Block.getStateId(state)); missing.appendTag(m); }
                String item = Item.getItemFromBlock(state.getBlock()).getRegistryName().toString(); int meta = state.getBlock().damageDropped(state); String key = item + ":" + meta;
                NBTTagCompound m = materials.get(key); if (m == null) { m = new NBTTagCompound(); m.setString("item", item); m.setInteger("meta", meta); materials.put(key, m); }
                m.setInteger("count", m.getInteger("count") + 1);
            }
            for (NBTTagCompound material : materials.values()) if (materialList.tagCount() < 256) materialList.appendTag(material);
            n.setInteger("blueprintRadius", blueprint.getInteger("radius"));
        }
        n.setBoolean("hasBlueprint", blueprint != null); n.setTag("blueprintMissing", missing); n.setTag("blueprintMaterials", materialList);
        if (s != null) {
            CachedView cached = new CachedView(); cached.tick = p.world.getTotalWorldTime() / 20; cached.dimension = p.dimension; cached.view = new NBTTagCompound();
            for (String key : Arrays.asList("hasBlueprint", "blueprintRadius", "blueprintMissing", "blueprintMaterials")) if (n.hasKey(key)) cached.view.setTag(key, n.getTag(key).copy());
            VIEWS.put(s.id, cached);
        }
    }
}
