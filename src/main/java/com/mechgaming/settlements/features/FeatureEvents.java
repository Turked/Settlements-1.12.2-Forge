package com.mechgaming.settlements.features;

import com.mechgaming.settlements.data.Settlement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Feature hooks are server-only; damage checks include indirect projectile sources. */
public final class FeatureEvents {
    private static final Map<UUID, BitSet> SNAPSHOT_BLOCKS = new LinkedHashMap<UUID, BitSet>(32, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, BitSet> entry) { return size() > 32; }
    };
    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && server.getWorld(0) != null) FeatureService.tick(server);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attack(LivingAttackEvent event) {
        if (event.getEntity().world.isRemote || !(event.getEntityLiving() instanceof EntityPlayerMP)) return;
        if (WarService.ghostProtected((EntityPlayerMP) event.getEntityLiving())) { event.setCanceled(true); return; }
        Entity source = event.getSource().getTrueSource();
        if (source instanceof EntityPlayerMP && WarService.blocksPvP((EntityPlayerMP) source, (EntityPlayerMP) event.getEntityLiving())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent event) {
        if (event.getEntity().world.isRemote) return;
        NpcService.npcDied(event.getEntity()); RaidService.mobDied(event.getEntity());
        if (event.getEntityLiving() instanceof EntityPlayerMP) WarService.died((EntityPlayerMP) event.getEntityLiving());
    }

    @SubscribeEvent
    public void entityLoaded(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || event.getWorld().getMinecraftServer() == null) return;
        Entity entity = event.getEntity(); NBTTagCompound tag = entity.getEntityData();
        if (entity instanceof EntityItem) {
            EntityItem item = (EntityItem) entity;
            // Vanilla beds bypass HarvestDropsEvent. Preserve player-thrown items, suppress
            // unowned block drops at objective positions that will be restored for free.
            if (item.getItem().getItem() == Items.BED && item.getThrower() == null && BedRestorationService.tracked(event.getWorld(), item.getPosition())) event.setCanceled(true);
            return;
        }
        if (!tag.getBoolean("SettlementsNpc") && !tag.hasUniqueId("SettlementsRaid")) return;
        FeatureWorldData data = FeatureWorldData.get(event.getWorld());
        if (tag.getBoolean("SettlementsNpc")) {
            NBTTagCompound room = FeatureService.find(data.rooms, "id", tag.getUniqueId("RoomId"));
            if (room == null || !room.hasUniqueId("npc") || !entity.getUniqueID().equals(room.getUniqueId("npc"))) event.setCanceled(true);
        }
        if (tag.hasUniqueId("SettlementsRaid") && FeatureService.find(data.raids, "id", tag.getUniqueId("SettlementsRaid")) == null) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void breakXp(BlockEvent.BreakEvent event) {
        if (!event.getWorld().isRemote && restoredBlock(event.getWorld(), event.getPos())) event.setExpToDrop(0);
        if (!event.getWorld().isRemote) BedRestorationService.breakBed(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void harvest(BlockEvent.HarvestDropsEvent event) {
        if (event.getWorld().isRemote) return;
        // Blocks scheduled for free post-event restoration cannot also yield materials.
        // Container contents are not in blueprints and are therefore never suppressed here.
        if (restoredBlock(event.getWorld(), event.getPos())) event.getDrops().clear();
    }

    private static boolean restoredBlock(World world, BlockPos pos) {
        FeatureWorldData data = FeatureWorldData.get(world);
        for (int i = 0; i < data.raids.tagCount(); i++) {
            NBTTagCompound r = data.raids.getCompoundTagAt(i);
            if (r.getInteger("dimension") == world.provider.getDimension() && r.hasKey("restore", 10) && contains(r.getCompoundTag("restore"), pos)) return true;
        }
        for (int i = 0; i < data.wars.tagCount(); i++) {
            NBTTagCompound w = data.wars.getCompoundTagAt(i);
            if (!w.getString("status").equals("active") || w.getInteger("dimension") != world.provider.getDimension()) continue;
            if (contains(w.getCompoundTag("restoreA"), pos) || contains(w.getCompoundTag("restoreB"), pos)) return true;
        }
        for (int i = 0; i < data.restorations.tagCount(); i++) {
            NBTTagCompound pending = data.restorations.getCompoundTagAt(i);
            if (pending.getInteger("dimension") == world.provider.getDimension() && contains(pending, pos)) return true;
        }
        return false;
    }
    private static boolean contains(NBTTagCompound blueprint, BlockPos pos) {
        BlockPos center = BlockPos.fromLong(blueprint.getLong("center")); int radius = blueprint.getInteger("radius");
        if (Math.abs(center.getX() - pos.getX()) > radius || Math.abs(center.getY() - pos.getY()) > radius || Math.abs(center.getZ() - pos.getZ()) > radius) return false;
        NBTTagList blocks = blueprint.getTagList("blocks", 10);
        if (blueprint.hasUniqueId("snapshot") && radius >= 0 && radius <= 31) {
            UUID id = blueprint.getUniqueId("snapshot"); BitSet positions = SNAPSHOT_BLOCKS.get(id);
            if (positions == null) {
                positions = new BitSet();
                for (int i = 0; i < blocks.tagCount(); i++) { BlockPos p = BlockPos.fromLong(blocks.getCompoundTagAt(i).getLong("pos")); positions.set(index(p, center, radius)); }
                SNAPSHOT_BLOCKS.put(id, positions);
            }
            return positions.get(index(pos, center, radius));
        }
        for (int i = 0; i < blocks.tagCount(); i++) if (blocks.getCompoundTagAt(i).getLong("pos") == pos.toLong()) return true;
        return false;
    }
    private static int index(BlockPos p, BlockPos center, int radius) {
        int edge = radius * 2 + 1;
        return ((p.getY() - center.getY() + radius) * edge + p.getZ() - center.getZ() + radius) * edge + p.getX() - center.getX() + radius;
    }
    static void invalidateSnapshot(NBTTagCompound snapshot) { if (snapshot.hasUniqueId("snapshot")) SNAPSHOT_BLOCKS.remove(snapshot.getUniqueId("snapshot")); }
}
