package com.MechGaming.settlements.features;

import com.MechGaming.settlements.Settlements;
import com.MechGaming.settlements.data.Settlement;
import com.MechGaming.settlements.data.SettlementData;
import com.MechGaming.settlements.config.SettlementConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.UUID;

/** Server-authoritative entry point shared by commands and the settlement menu. */
public final class FeatureService {
    static int maxNpcs = 8, maxBlueprintVolume = 32768, raidWaves = 3, raidMobsPerWave = 4;
    static int raidRewardEmeralds = 4, raidIntervalMinutes = 0;
    static boolean enableNpcs = true, autoRepair = true, mobDestruction, mobPlacement, allowNpcStructures;
    static String[] raidEntities = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider"};
    private static File definitionConfigDir;
    private FeatureService() { }

    public static void load(File configDir) {
        definitionConfigDir = configDir;
        Configuration c = new Configuration(new File(configDir, "settlements-features.cfg"));
        c.load();
        allowNpcStructures = c.getBoolean("allowNpcStructures", "npcs", false, "Allow administrator-authored NPC definitions to place their structureBlocks into empty room positions.");
        raidIntervalMinutes = c.getInt("raidIntervalMinutes", "raids", 0, 0, 1440, "0 means manual raids only. Positive intervals enable automatic raids while a member is present.");
        raidEntities = c.getStringList("entities", "raids", raidEntities, "Installed entity registry IDs. Invalid or non-living IDs are skipped; modded entities remain optional.");
        c.save();
        syncConfig();
    }

    /** Item and block registries, including optional pack mods, are complete by post-init. */
    public static void postInit() { if (definitionConfigDir != null) NpcService.load(definitionConfigDir); }

    public static boolean handle(EntityPlayerMP player, String action, BlockPos pos, int value, String text, NBTTagCompound payload) {
        syncConfig();
        if (!action.startsWith("feature_")) return false;
        if (action.startsWith("feature_room_")) return NpcService.handle(player, action, pos, text, payload);
        if (action.startsWith("feature_blueprint_")) return BlueprintService.handle(player, action, pos, value, payload);
        if (action.startsWith("feature_raid_")) return RaidService.handle(player, action, payload);
        if (action.startsWith("feature_war_")) return WarService.handle(player, action, value, text, payload);
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCounter() % 20 != 0) return;
        syncConfig();
        NpcService.tick(server);
        RaidService.tick(server);
        WarService.tick(server);
        BlueprintService.tick(server);
        BedRestorationService.tick(server);
        EscrowService.tick(server);
    }

    public static NBTTagCompound snapshot(EntityPlayerMP player) {
        return snapshot(player, "");
    }

    public static NBTTagCompound snapshot(EntityPlayerMP player, String selectedSettlement) {
        syncConfig();
        NBTTagCompound selection = new NBTTagCompound(); selection.setString("settlement", selectedSettlement == null ? "" : selectedSettlement);
        Settlement s = memberSettlement(player, selection);
        NBTTagCompound n = new NBTTagCompound();
        NpcService.snapshot(player, n, s);
        BlueprintService.snapshot(player, n, s);
        RaidService.snapshot(player, n, s);
        WarService.snapshot(player, n, s);
        return n;
    }

    public static boolean canAttack(EntityPlayerMP player, Settlement target) {
        return WarService.canModify(player, target);
    }
    public static boolean hasWar(World world, UUID settlement) { return WarService.hasWar(world, settlement); }

    private static void syncConfig() {
        enableNpcs = SettlementConfig.enableNpcs; maxNpcs = SettlementConfig.maxNpcs;
        maxBlueprintVolume = SettlementConfig.maxBlueprintVolume; autoRepair = SettlementConfig.autoRepair;
        raidWaves = SettlementConfig.raidWaves; raidMobsPerWave = SettlementConfig.raidMobsPerWave;
        raidRewardEmeralds = SettlementConfig.raidRewardEmeralds;
        mobDestruction = SettlementConfig.mobDestruction; mobPlacement = SettlementConfig.mobPlacement;
    }

    static Settlement memberSettlement(EntityPlayerMP player) {
        for (Settlement s : SettlementData.get(player.world).settlements)
            if (s.contains(player.dimension, player.getPosition()) && (s.owner.equals(player.getUniqueID()) || s.members.contains(player.getUniqueID()))) return s;
        for (Settlement s : SettlementData.get(player.world).settlements)
            if (s.owner.equals(player.getUniqueID()) || s.members.contains(player.getUniqueID())) return s;
        return null;
    }
    static Settlement memberSettlement(EntityPlayerMP player, NBTTagCompound payload) {
        if (payload == null || !payload.hasKey("settlement", 8) || payload.getString("settlement").isEmpty()) return memberSettlement(player);
        try {
            UUID id = UUID.fromString(payload.getString("settlement")); Settlement s = settlement(player.world, id);
            return s != null && (s.owner.equals(player.getUniqueID()) || s.members.contains(player.getUniqueID())) ? s : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    static Settlement settlement(World world, UUID id) {
        for (Settlement s : SettlementData.get(world).settlements) if (s.id.equals(id)) return s;
        return null;
    }
    static Settlement requireMember(EntityPlayerMP player) {
        Settlement s = memberSettlement(player);
        if (s == null) tell(player, "Join or establish a settlement first.");
        return s;
    }
    static Settlement requireMember(EntityPlayerMP player, NBTTagCompound payload) {
        Settlement s = memberSettlement(player, payload); if (s == null) tell(player, "Select a settlement you belong to first."); return s;
    }
    static boolean requireLeader(EntityPlayerMP p, Settlement s) {
        if (s != null && s.owner.equals(p.getUniqueID())) return true;
        tell(p, "Only your settlement leader can do that."); return false;
    }
    static void tell(EntityPlayerMP player, String text) { player.sendMessage(new TextComponentString("[Settlements] " + text)); }
    static void announce(MinecraftServer server, Settlement s, String text) {
        for (EntityPlayerMP p : server.getPlayerList().getPlayers())
            if (s.members.contains(p.getUniqueID()) || s.owner.equals(p.getUniqueID())) tell(p, text);
    }
    static NBTTagCompound find(net.minecraft.nbt.NBTTagList list, String key, UUID id) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound n = list.getCompoundTagAt(i);
            if (n.hasUniqueId(key) && id.equals(n.getUniqueId(key))) return n;
        }
        return null;
    }
    static void refresh(EntityPlayerMP p) { Settlements.sendState(p); }
}
