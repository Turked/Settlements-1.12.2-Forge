package com.MechGaming.settlements.features;

import com.MechGaming.settlements.config.SettlementConfig;
import com.MechGaming.settlements.data.Settlement;
import com.MechGaming.settlements.data.SettlementData;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Optional tagged raids; ordinary RLCraft mob AI is never replaced globally. */
final class RaidService {
    private RaidService() { }

    static boolean handle(EntityPlayerMP p, String action, NBTTagCompound payload) {
        if (!action.equals("feature_raid_start") && !action.equals("feature_raid_stop")) return false;
        Settlement s = FeatureService.requireMember(p, payload);
        if (!FeatureService.requireLeader(p, s)) return true;
        FeatureWorldData data = FeatureWorldData.get(p.world); NBTTagCompound existing = FeatureService.find(data.raids, "settlement", s.id);
        if (action.equals("feature_raid_stop")) {
            if (existing != null) finish(p.getServer(), data, existing, false, "Raid stopped by the settlement leader.");
            else FeatureService.tell(p, "No raid is active.");
        } else if (!SettlementConfig.mobsAttackSettlement) FeatureService.tell(p, "Enable mobsAttackSettlement in the server configuration first.");
        else if (existing != null) FeatureService.tell(p, "Your settlement already has an active raid.");
        else if (p.dimension != s.dimension || !s.contains(p.dimension, p.getPosition())) FeatureService.tell(p, "Stand inside your settlement to start its raid.");
        else if (WarService.hasWar(p.world, s.id)) FeatureService.tell(p, "Finish the settlement war before starting a raid.");
        else start(p.getServer(), s);
        FeatureService.refresh(p); return true;
    }

    private static void start(MinecraftServer server, Settlement s) {
        WorldServer world = server.getWorld(s.dimension); if (world == null) return;
        FeatureWorldData data = FeatureWorldData.get(world); NBTTagCompound raid = new NBTTagCompound();
        raid.setUniqueId("id", UUID.randomUUID()); raid.setUniqueId("settlement", s.id); raid.setInteger("dimension", s.dimension);
        raid.setInteger("wave", 0); raid.setLong("expires", world.getTotalWorldTime() + 24000); raid.setLong("nextWave", world.getTotalWorldTime() + 100);
        raid.setTag("mobs", new NBTTagList()); raid.setTag("placed", new NBTTagList());
        NBTTagCompound blueprint = FeatureService.find(data.blueprints, "settlement", s.id);
        if (SettlementConfig.raidsWithoutRuin && blueprint == null) { FeatureService.announce(server, s, "Save a blueprint before starting a raid with raidsWithoutRuin enabled."); return; }
        if (SettlementConfig.raidsWithoutRuin) {
            NBTTagCompound frozen = BlueprintService.eventSnapshot(world, blueprint);
            if (frozen == null) { FeatureService.announce(server, s, "Load the saved blueprint area before starting a raid."); return; }
            raid.setTag("restore", frozen);
        }
        data.raids.appendTag(raid); data.markDirty(); FeatureService.announce(server, s, "A settlement raid begins in 5 seconds. Defeat " + FeatureService.raidWaves + " waves while protecting your base.");
    }

    static void tick(MinecraftServer server) {
        FeatureWorldData data = FeatureWorldData.get(server.getWorld(0));
        if (SettlementConfig.mobsAttackSettlement && FeatureService.raidIntervalMinutes > 0 && server.getTickCounter() % (FeatureService.raidIntervalMinutes * 1200) == 0) {
            for (Settlement s : SettlementData.get(server.getWorld(0)).settlements) {
                if (FeatureService.find(data.raids, "settlement", s.id) == null && !WarService.hasWar(server.getWorld(0), s.id) && presentMember(server, s) != null) start(server, s);
            }
        }
        for (int i = data.raids.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound raid = data.raids.getCompoundTagAt(i); WorldServer world = server.getWorld(raid.getInteger("dimension"));
            Settlement s = FeatureService.settlement(server.getWorld(0), raid.getUniqueId("settlement"));
            if (world == null || s == null || !SettlementConfig.mobsAttackSettlement) { finish(server, data, raid, false, "Raid ended."); continue; }
            if (world.getTotalWorldTime() > raid.getLong("expires")) { finish(server, data, raid, false, "Raid timed out; no reward was awarded."); continue; }
            NBTTagList mobs = raid.getTagList("mobs", 10);
            for (int m = 0; m < mobs.tagCount(); m++) {
                Entity entity = world.getEntityFromUuid(mobs.getCompoundTagAt(m).getUniqueId("id"));
                if (entity instanceof EntityLiving) directMob(world, s, raid, (EntityLiving) entity, server);
            }
            if (mobs.tagCount() == 0 && world.getTotalWorldTime() >= raid.getLong("nextWave")) {
                if (raid.getInteger("wave") >= FeatureService.raidWaves) { finish(server, data, raid, true, "Raid defeated!"); continue; }
                EntityPlayerMP defender = presentMember(server, s);
                if (defender == null) continue; // No forced chunk loads or attacks on offline settlements.
                int spawned = spawnWave(world, s, raid, defender);
                if (spawned == 0) { raid.setLong("nextWave", world.getTotalWorldTime() + 200); FeatureService.announce(server, s, "No safe loaded spawn positions are available in the configured raid ring. Retrying in 10 seconds."); }
                else { raid.setInteger("wave", raid.getInteger("wave") + 1); raid.setLong("nextWave", world.getTotalWorldTime() + 100); FeatureService.announce(server, s, "Raid wave " + raid.getInteger("wave") + "/" + FeatureService.raidWaves + ": " + spawned + " enemies."); }
                data.markDirty();
            }
        }
    }

    private static int spawnWave(WorldServer world, Settlement s, NBTTagCompound raid, EntityPlayerMP defender) {
        if (FeatureService.raidEntities.length == 0) return 0;
        NBTTagList mobs = raid.getTagList("mobs", 10); Random random = world.rand;
        for (int tries = 0; tries < FeatureService.raidMobsPerWave * 32 && mobs.tagCount() < FeatureService.raidMobsPerWave; tries++) {
            int min = s.radius + Math.max(1, SettlementConfig.spawnMinDistance), max = s.radius + Math.max(SettlementConfig.spawnMinDistance + 1, SettlementConfig.spawnMaxDistance);
            int x = s.flag.getX() + random.nextInt(max * 2 + 1) - max, z = s.flag.getZ() + random.nextInt(max * 2 + 1) - max;
            if (Math.max(Math.abs(x - s.flag.getX()), Math.abs(z - s.flag.getZ())) < min || !world.isBlockLoaded(new BlockPos(x, 64, z))) continue;
            BlockPos p = world.getHeight(new BlockPos(x, 0, z));
            if (p.getY() < 1 || p.getY() > 253 || !world.isAirBlock(p) || !world.isAirBlock(p.up()) || !world.getBlockState(p.down()).isFullCube()) continue;
            Entity entity;
            try { entity = EntityList.createEntityByIDFromName(new ResourceLocation(FeatureService.raidEntities[random.nextInt(FeatureService.raidEntities.length)]), world); }
            catch (RuntimeException e) { continue; }
            if (!(entity instanceof EntityLiving) || !(entity instanceof IMob)) continue;
            EntityLiving mob = (EntityLiving) entity; mob.setLocationAndAngles(x + .5, p.getY(), z + .5, random.nextFloat() * 360, 0);
            if (!world.checkNoEntityCollision(mob.getEntityBoundingBox()) || world.containsAnyLiquid(mob.getEntityBoundingBox())) continue;
            mob.onInitialSpawn(world.getDifficultyForLocation(p), null); mob.enablePersistence(); mob.setAttackTarget(defender);
            mob.getEntityData().setUniqueId("SettlementsRaid", raid.getUniqueId("id"));
            if (world.spawnEntity(mob)) { NBTTagCompound id = new NBTTagCompound(); id.setUniqueId("id", mob.getUniqueID()); mobs.appendTag(id); }
        }
        return mobs.tagCount();
    }

    private static void directMob(WorldServer world, Settlement s, NBTTagCompound raid, EntityLiving mob, MinecraftServer server) {
        EntityPlayerMP defender = presentMember(server, s); if (defender == null) return;
        mob.setAttackTarget(defender); mob.getNavigator().tryMoveToEntityLiving(defender, 1.0D);
        if (!world.getGameRules().getBoolean("mobGriefing")) return;
        if (FeatureService.mobDestruction && mob.collidedHorizontally) {
            BlockPos forward = mob.getPosition().offset(mob.getHorizontalFacing());
            for (BlockPos pos : Arrays.asList(forward.up(), forward)) {
                if (!s.contains(s.dimension, pos) || !world.isBlockLoaded(pos)) continue;
                IBlockState state = world.getBlockState(pos); ResourceLocation id = state.getBlock().getRegistryName();
                if (state.getBlock().hasTileEntity(state) || id == null || id.getResourceDomain().equals("settlements") || state.getBlockHardness(world, pos) < 0 || state.getBlock() == Blocks.AIR) continue;
                world.destroyBlock(pos, false); break; // No drops: non-destructive restoration cannot duplicate materials.
            }
        }
        if (FeatureService.mobPlacement && mob.collidedHorizontally && defender.posY > mob.posY + 1) {
            BlockPos pos = mob.getPosition().offset(mob.getHorizontalFacing());
            if (s.contains(s.dimension, pos) && world.isBlockLoaded(pos) && world.isAirBlock(pos) && raid.getTagList("placed", 10).tagCount() < 256) {
                if (world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3)) { mob.getJumpHelper().setJumping(); NBTTagCompound b = new NBTTagCompound(); b.setLong("pos", pos.toLong()); raid.getTagList("placed", 10).appendTag(b); FeatureWorldData.get(world).markDirty(); }
            }
        }
    }

    static void mobDied(Entity entity) {
        if (!entity.getEntityData().hasUniqueId("SettlementsRaid")) return;
        FeatureWorldData data = FeatureWorldData.get(entity.world); NBTTagCompound raid = FeatureService.find(data.raids, "id", entity.getEntityData().getUniqueId("SettlementsRaid"));
        if (raid == null) return; NBTTagList mobs = raid.getTagList("mobs", 10);
        for (int i = mobs.tagCount() - 1; i >= 0; i--) if (entity.getUniqueID().equals(mobs.getCompoundTagAt(i).getUniqueId("id"))) mobs.removeTag(i);
        data.markDirty();
    }

    private static void finish(MinecraftServer server, FeatureWorldData data, NBTTagCompound raid, boolean victory, String message) {
        WorldServer world = server.getWorld(raid.getInteger("dimension")); Settlement s = FeatureService.settlement(server.getWorld(0), raid.getUniqueId("settlement"));
        if (world != null) {
            NBTTagList mobs = raid.getTagList("mobs", 10);
            for (int i = 0; i < mobs.tagCount(); i++) { Entity entity = world.getEntityFromUuid(mobs.getCompoundTagAt(i).getUniqueId("id")); if (entity != null) entity.setDead(); }
            NBTTagList placed = raid.getTagList("placed", 10);
            for (int i = 0; i < placed.tagCount(); i++) { BlockPos pos = BlockPos.fromLong(placed.getCompoundTagAt(i).getLong("pos")); if (world.isBlockLoaded(pos) && world.getBlockState(pos).getBlock() == Blocks.COBBLESTONE) world.setBlockToAir(pos); }
            if (s != null && raid.hasKey("restore", 10)) BlueprintService.queueRestore(world, s, raid.getCompoundTag("restore"));
            if (victory && s != null) { EntityPlayerMP reward = presentMember(server, s); if (reward != null && !reward.isCreative() && FeatureService.raidRewardEmeralds > 0) { ItemStack stack = new ItemStack(Items.EMERALD, FeatureService.raidRewardEmeralds); if (!reward.inventory.addItemStackToInventory(stack)) reward.dropItem(stack, false); } }
        }
        for (int i = data.raids.tagCount() - 1; i >= 0; i--) if (raid.getUniqueId("id").equals(data.raids.getCompoundTagAt(i).getUniqueId("id"))) data.raids.removeTag(i);
        data.markDirty(); if (s != null) FeatureService.announce(server, s, message + (raid.hasKey("restore", 10) ? " Ordinary blocks present in the saved blueprint area before the raid will be restored as chunks load." : ""));
    }

    private static EntityPlayerMP presentMember(MinecraftServer server, Settlement s) {
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) if (!p.isSpectator() && s.contains(p.dimension, p.getPosition()) && (s.members.contains(p.getUniqueID()) || s.owner.equals(p.getUniqueID()))) return p;
        return null;
    }

    static void snapshot(EntityPlayerMP p, NBTTagCompound n, Settlement s) {
        if (s == null) return;
        NBTTagCompound raid = FeatureService.find(FeatureWorldData.get(p.world).raids, "settlement", s.id);
        if (raid != null) { NBTTagCompound r = new NBTTagCompound(); r.setInteger("wave", raid.getInteger("wave")); r.setInteger("waves", FeatureService.raidWaves); r.setInteger("remaining", raid.getTagList("mobs", 10).tagCount()); n.setTag("raid", r); }
    }
}
