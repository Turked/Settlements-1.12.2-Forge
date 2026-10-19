package com.mechgaming.settlements.features;

import com.google.gson.Gson;
import com.mechgaming.settlements.Settlements;
import com.mechgaming.settlements.data.Settlement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.WorldServer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Small bounded room scans, vanilla villagers and administrator-provided trade definitions. */
final class NpcService {
    private static final Map<String, NpcDefinition> DEFINITIONS = new LinkedHashMap<>();
    private NpcService() { }

    static void load(File configDir) {
        DEFINITIONS.clear();
        File folder = new File(configDir, "settlements/npcs");
        if (!folder.isDirectory() && !folder.mkdirs()) { Settlements.LOGGER.error("Cannot create NPC definition directory {}", folder); return; }
        File example = new File(folder, "settler.json");
        if (!example.exists()) {
            try (InputStream in = NpcService.class.getResourceAsStream("/settlements/default_npc.json")) {
                if (in != null) Files.copy(in, example.toPath());
            } catch (IOException e) { Settlements.LOGGER.error("Cannot install generated NPC example", e); }
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        Arrays.sort(files);
        for (int i = 0; i < files.length && i < 128; i++) {
            try (Reader reader = new InputStreamReader(new FileInputStream(files[i]), StandardCharsets.UTF_8)) {
                if (files[i].length() > 1048576) throw new IllegalArgumentException("NPC file exceeds 1 MiB");
                NpcDefinition d = new Gson().fromJson(reader, NpcDefinition.class);
                if (d == null || d.id == null || !d.id.matches("[a-z0-9_]{1,48}") || d.requiredBlocks == null || d.trades == null)
                    throw new IllegalArgumentException("Missing/invalid NPC id, requiredBlocks or trades");
                d.minRoomVolume = Math.max(1, Math.min(512, d.minRoomVolume));
                d.maxRoomVolume = Math.max(d.minRoomVolume, Math.min(2048, d.maxRoomVolume));
                if (d.requiredBlocks.size() > 64 || d.trades.size() > 64 || (d.structureBlocks != null && d.structureBlocks.size() > 256))
                    throw new IllegalArgumentException("NPC definition exceeds size limits");
                for (Map.Entry<String, Integer> requirement : d.requiredBlocks.entrySet()) {
                    if (!Block.REGISTRY.containsKey(new ResourceLocation(requirement.getKey()))) throw new IllegalArgumentException("Unknown required block " + requirement.getKey());
                    if (requirement.getValue() == null || requirement.getValue() < 1 || requirement.getValue() > 512) throw new IllegalArgumentException("Required block counts must be in 1..512");
                }
                for (NpcDefinition.Trade t : d.trades) {
                    if (t == null || t.buy == null || t.sell == null || !Item.REGISTRY.containsKey(new ResourceLocation(t.buy)) || !Item.REGISTRY.containsKey(new ResourceLocation(t.sell)))
                        throw new IllegalArgumentException("Trade contains an unknown item registry id");
                }
                if (d.structureBlocks != null) for (NpcDefinition.StructureBlock b : d.structureBlocks) {
                    if (b == null || b.block == null || !Block.REGISTRY.containsKey(new ResourceLocation(b.block))) throw new IllegalArgumentException("Structure contains an unknown block registry id");
                }
                DEFINITIONS.put(d.id, d);
            } catch (Exception e) { Settlements.LOGGER.error("Ignoring NPC definition {}: {}", files[i], e.getMessage()); }
        }
    }

    static boolean handle(EntityPlayerMP p, String action, BlockPos pos, String text, NBTTagCompound payload) {
        if (!action.equals("feature_room_add") && !action.equals("feature_room_remove")) return false;
        Settlement s = FeatureService.requireMember(p, payload);
        if (!FeatureService.requireLeader(p, s)) return true;
        if (p.dimension != s.dimension || !s.contains(p.dimension, pos)) { FeatureService.tell(p, "Stand inside your settlement's room."); return true; }
        FeatureWorldData data = FeatureWorldData.get(p.world);
        if (action.equals("feature_room_remove")) {
            for (int i = data.rooms.tagCount() - 1; i >= 0; i--) {
                NBTTagCompound room = data.rooms.getCompoundTagAt(i);
                if (!s.id.equals(room.getUniqueId("settlement"))) continue;
                NpcDefinition definition = DEFINITIONS.get(room.getString("definition")); BlockPos marker = BlockPos.fromLong(room.getLong("pos"));
                boolean inside = marker.equals(pos) || (definition != null && scan(p.getServerWorld(), s, marker, definition).interior.contains(pos));
                if (inside) {
                    removeNpc(p.getServerWorld(), room); data.rooms.removeTag(i); data.markDirty();
                    FeatureService.tell(p, "Room removed. Its Settlements NPC has moved out."); FeatureService.refresh(p); return true;
                }
            }
            FeatureService.tell(p, "Stand inside the registered room or at its marker to remove it."); return true;
        }
        if (!FeatureService.enableNpcs) { FeatureService.tell(p, "NPC move-ins are disabled by the server."); return true; }
        String id = text == null || text.trim().isEmpty() ? "settler" : text.trim();
        NpcDefinition d = DEFINITIONS.get(id);
        if (d == null) { FeatureService.tell(p, "Unknown NPC definition. Available: " + DEFINITIONS.keySet()); return true; }
        int count = 0;
        for (int i = 0; i < data.rooms.tagCount(); i++) if (s.id.equals(data.rooms.getCompoundTagAt(i).getUniqueId("settlement"))) count++;
        if (count >= FeatureService.maxNpcs) { FeatureService.tell(p, "Settlement room limit reached."); return true; }
        RoomScan scan = scan(p.getServerWorld(), s, pos, d);
        if (!scan.valid) { FeatureService.tell(p, scan.error); return true; }
        for (int i = 0; i < data.rooms.tagCount(); i++) {
            NBTTagCompound r = data.rooms.getCompoundTagAt(i);
            if (r.getInteger("dimension") == p.dimension && scan.interior.contains(BlockPos.fromLong(r.getLong("pos")))) {
                FeatureService.tell(p, "This room already has an NPC marker."); return true;
            }
        }
        NBTTagCompound room = new NBTTagCompound();
        room.setUniqueId("id", UUID.randomUUID()); room.setUniqueId("settlement", s.id);
        room.setInteger("dimension", p.dimension); room.setLong("pos", pos.toLong()); room.setString("definition", id);
        room.setLong("nextMoveIn", p.world.getTotalWorldTime()); data.rooms.appendTag(room); data.markDirty();
        FeatureService.tell(p, "Valid room registered for " + d.name + ". Move-in is checked every 20 seconds."); FeatureService.refresh(p); return true;
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCounter() % 400 != 0) return;
        FeatureWorldData data = FeatureWorldData.get(server.getWorld(0));
        for (int i = data.rooms.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound room = data.rooms.getCompoundTagAt(i);
            WorldServer world = server.getWorld(room.getInteger("dimension"));
            if (world == null || !world.isBlockLoaded(BlockPos.fromLong(room.getLong("pos")))) continue;
            Settlement s = FeatureService.settlement(world, room.getUniqueId("settlement"));
            NpcDefinition d = DEFINITIONS.get(room.getString("definition"));
            if (s == null || d == null) { removeNpc(world, room); data.rooms.removeTag(i); data.markDirty(); continue; }
            RoomScan scan = scan(world, s, BlockPos.fromLong(room.getLong("pos")), d);
            Entity entity = room.hasUniqueId("npc") ? world.getEntityFromUuid(room.getUniqueId("npc")) : null;
            if (!FeatureService.enableNpcs || !scan.valid) {
                if (entity != null) { entity.setDead(); room.removeTag("npcMost"); room.removeTag("npcLeast"); data.markDirty(); }
                continue;
            }
            if (entity == null && world.getTotalWorldTime() >= room.getLong("nextMoveIn")) {
                // An unloaded NPC must never be duplicated: its home is always inside this loaded room.
                // The villager could have wandered into an unloaded chunk. Only a real death
                // event clears its UUID, so checking a room never duplicates an existing NPC.
                if (room.hasUniqueId("npc")) continue;
                EntityVillager villager = new EntitySettler(world);
                NBTTagCompound n = new NBTTagCompound(); villager.writeToNBT(n);
                MerchantRecipeList recipes = new MerchantRecipeList();
                for (NpcDefinition.Trade t : d.trades) {
                    Item buy = Item.REGISTRY.getObject(new ResourceLocation(t.buy)), sell = Item.REGISTRY.getObject(new ResourceLocation(t.sell));
                    if (buy != null && sell != null && t.buyCount > 0 && t.sellCount > 0)
                        recipes.add(new MerchantRecipe(new ItemStack(buy, Math.min(64, t.buyCount), Math.max(0, t.buyMeta)), ItemStack.EMPTY,
                                new ItemStack(sell, Math.min(64, t.sellCount), Math.max(0, t.sellMeta)), 0, Math.max(1, Math.min(10000, t.uses))));
                }
                n.setTag("Offers", recipes.getRecipiesAsTags()); villager.readFromNBT(n);
                villager.setPosition(scan.spawn.getX() + .5, scan.spawn.getY(), scan.spawn.getZ() + .5);
                villager.setCustomNameTag(d.name == null ? d.id : d.name.substring(0, Math.min(64, d.name.length())));
                villager.enablePersistence(); villager.setHomePosAndDistance(scan.spawn, 6);
                villager.getEntityData().setBoolean("SettlementsNpc", true);
                villager.getEntityData().setUniqueId("SettlementId", s.id); villager.getEntityData().setUniqueId("RoomId", room.getUniqueId("id"));
                room.setUniqueId("npc", villager.getUniqueID());
                if (world.spawnEntity(villager)) placeStructure(world, s, scan, d);
                else { room.removeTag("npcMost"); room.removeTag("npcLeast"); }
                data.markDirty();
            }
        }
    }

    private static void placeStructure(WorldServer world, Settlement s, RoomScan scan, NpcDefinition d) {
        if (!FeatureService.allowNpcStructures || d.structureBlocks == null) return;
        for (NpcDefinition.StructureBlock b : d.structureBlocks) {
            if (Math.abs(b.x) > 8 || Math.abs(b.y) > 8 || Math.abs(b.z) > 8 || b.block == null) continue;
            BlockPos p = scan.spawn.add(b.x, b.y, b.z);
            Block block = Block.REGISTRY.getObject(new ResourceLocation(b.block));
            if (block == null || !s.contains(s.dimension, p) || !world.isBlockLoaded(p) || !world.isAirBlock(p)) continue;
            IBlockState state = block.getStateFromMeta(Math.max(0, Math.min(15, b.meta)));
            if (!block.hasTileEntity(state) && scan.interior.contains(p)) world.setBlockState(p, state, 3);
        }
    }

    static void npcDied(Entity entity) {
        if (!entity.getEntityData().getBoolean("SettlementsNpc")) return;
        FeatureWorldData data = FeatureWorldData.get(entity.world);
        NBTTagCompound room = FeatureService.find(data.rooms, "id", entity.getEntityData().getUniqueId("RoomId"));
        if (room != null) { room.removeTag("npcMost"); room.removeTag("npcLeast"); room.setLong("nextMoveIn", entity.world.getTotalWorldTime() + 6000); data.markDirty(); }
    }

    private static void removeNpc(WorldServer world, NBTTagCompound room) {
        if (room.hasUniqueId("npc")) { Entity entity = world.getEntityFromUuid(room.getUniqueId("npc")); if (entity != null) entity.setDead(); }
    }

    static RoomScan scan(WorldServer world, Settlement s, BlockPos start, NpcDefinition d) {
        RoomScan result = new RoomScan();
        if (!world.isBlockLoaded(start) || !interior(world.getBlockState(start))) return result.fail("Stand in the empty interior of the room.");
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(); Set<BlockPos> boundary = new HashSet<>(); todo.add(start); result.interior.add(start);
        while (!todo.isEmpty()) {
            BlockPos p = todo.removeFirst();
            if (!s.contains(world.provider.getDimension(), p) || p.getY() < 1 || p.getY() > 253 || !world.isBlockLoaded(p)) return result.fail("Room must be enclosed, loaded and completely inside the settlement.");
            if (result.interior.size() > d.maxRoomVolume) return result.fail("Room is open or exceeds " + d.maxRoomVolume + " interior blocks.");
            if (world.getBlockState(p).getMaterial().isReplaceable() && world.isAirBlock(p.up()) && world.getBlockState(p.down()).isSideSolid(world, p.down(), EnumFacing.UP)) result.spawn = p;
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos next = p.offset(face);
                if (!world.isBlockLoaded(next)) return result.fail("Room crosses unloaded chunks.");
                IBlockState state = world.getBlockState(next);
                if (interior(state)) { if (result.interior.add(next)) todo.add(next); }
                else boundary.add(next);
            }
        }
        if (result.interior.size() < d.minRoomVolume || result.spawn == null) return result.fail("Room needs at least " + d.minRoomVolume + " interior blocks and clear standing space on a solid floor.");
        Map<String, Integer> counts = new HashMap<>();
        for (BlockPos p : boundary) { ResourceLocation id = world.getBlockState(p).getBlock().getRegistryName(); if (id != null) counts.put(id.toString(), counts.getOrDefault(id.toString(), 0) + 1); }
        for (Map.Entry<String, Integer> need : d.requiredBlocks.entrySet())
            if (need.getValue() != null && counts.getOrDefault(need.getKey(), 0) < Math.max(0, need.getValue())) return result.fail("Room needs " + need.getValue() + " of " + need.getKey() + ". In 1.12.2 the default storage block is a chest.");
        result.valid = true; return result;
    }
    private static boolean interior(IBlockState state) {
        return !(state.getBlock() instanceof BlockDoor) && !state.getMaterial().blocksMovement() && !state.getMaterial().isLiquid();
    }

    static void snapshot(EntityPlayerMP p, NBTTagCompound n, Settlement s) {
        NBTTagList rooms = new NBTTagList();
        if (s != null) {
            NBTTagList source = FeatureWorldData.get(p.world).rooms;
            for (int i = 0; i < source.tagCount(); i++) {
                NBTTagCompound r = source.getCompoundTagAt(i);
                if (s.id.equals(r.getUniqueId("settlement"))) { NBTTagCompound copy = r.copy(); copy.removeTag("npcMost"); copy.removeTag("npcLeast"); rooms.appendTag(copy); }
            }
        }
        n.setTag("rooms", rooms); n.setInteger("maxNpcs", FeatureService.maxNpcs);
    }

    static final class RoomScan {
        boolean valid; String error; BlockPos spawn;
        final Set<BlockPos> interior = new LinkedHashSet<>();
        RoomScan fail(String text) { error = text; return this; }
    }
}
