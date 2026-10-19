package com.MechGaming.settlements.features;

import com.MechGaming.settlements.config.SettlementConfig;
import com.MechGaming.settlements.data.Settlement;
import com.MechGaming.settlements.data.SettlementData;
import net.minecraft.block.BlockBed;
import net.minecraft.init.Blocks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Two leader consent, explicit participant opt-in, frozen rules and time-bounded wars. */
final class WarService {
    private WarService() { }

    static boolean handle(EntityPlayerMP p, String action, int value, String text, NBTTagCompound payload) {
        if (!Arrays.asList("feature_war_offer", "feature_war_accept", "feature_war_decline", "feature_war_join", "feature_war_leave", "feature_war_start", "feature_war_surrender", "feature_war_status").contains(action)) return false;
        Settlement own = FeatureService.requireMember(p, payload); if (own == null) return true;
        FeatureWorldData data = FeatureWorldData.get(p.world); NBTTagCompound war = findWar(data, own.id);
        if (action.equals("feature_war_status")) {
            FeatureService.tell(p, war == null ? "No settlement war is proposed or active." : summary(p, own, war)); return true;
        }
        if (!SettlementConfig.warsWithoutRuin) { FeatureService.tell(p, "Wars are disabled. Enable warsWithoutRuin in the server configuration first."); return true; }
        if (action.equals("feature_war_offer")) {
            if (!FeatureService.requireLeader(p, own)) return true;
            if (hasWar(p.world, own.id) || FeatureService.find(data.raids, "settlement", own.id) != null) { FeatureService.tell(p, "Finish the existing war or raid first."); return true; }
            Settlement other = null;
            for (Settlement s : SettlementData.get(p.world).settlements) if (s.name.equalsIgnoreCase(text.trim()) || s.id.toString().equalsIgnoreCase(text.trim())) { other = s; break; }
            if (other == null || other.owner.equals(own.owner)) { FeatureService.tell(p, "Enter the exact name of a settlement with a different leader."); return true; }
            if (other.dimension != own.dimension || hasWar(p.world, other.id) || FeatureService.find(data.raids, "settlement", other.id) != null) { FeatureService.tell(p, "The target must be in the same dimension and free of wars or raids."); return true; }
            NBTTagCompound aBlueprint = FeatureService.find(data.blueprints, "settlement", own.id), bBlueprint = FeatureService.find(data.blueprints, "settlement", other.id);
            if (aBlueprint == null || bBlueprint == null) { FeatureService.tell(p, "Both leaders must save a blueprint before a war can be proposed. Restoration covers these explicit blueprint areas."); return true; }
            String condition = payload != null && payload.hasKey("condition", 8) ? payload.getString("condition") : "lives";
            if (!condition.equals("lives") && !condition.equals("beds")) { FeatureService.tell(p, "Supported win conditions: lives or beds."); return true; }
            war = new NBTTagCompound(); war.setUniqueId("id", UUID.randomUUID()); war.setUniqueId("a", own.id); war.setUniqueId("b", other.id);
            war.setUniqueId("ownerA", own.owner); war.setUniqueId("ownerB", other.owner);
            war.setBoolean("annex", payload != null && payload.getBoolean("annex"));
            if (!EscrowService.terms(war, payload, p)) return true;
            war.setInteger("dimension", own.dimension); war.setString("status", "offered"); war.setInteger("minutes", Math.max(1, Math.min(120, value <= 0 ? 10 : value)));
            int lives = payload != null && payload.hasKey("lives", 3) ? Math.max(1, Math.min(100, payload.getInteger("lives"))) : 3;
            war.setString("condition", condition); war.setInteger("livesA", lives); war.setInteger("livesB", lives);
            war.setLong("expires", p.getServer().getWorld(0).getTotalWorldTime() + 24000); war.setTag("playersA", new NBTTagList()); war.setTag("playersB", new NBTTagList());
            addParticipant(war, "playersA", p.getUniqueID()); data.wars.appendTag(war); data.markDirty();
            String stakes = stakeDescription(war);
            FeatureService.announce(p.getServer(), own, "War terms proposed to " + other.name + ": " + war.getInteger("minutes") + " minutes, " + condition + (condition.equals("lives") ? ", " + lives + " shared lives each." : ".") + stakes);
            FeatureService.announce(p.getServer(), other, own.name + " proposes a war: " + war.getInteger("minutes") + " minutes, " + condition + (condition.equals("lives") ? ", " + lives + " shared lives each." : ".") + stakes + " Leader: /settlements war accept or decline. Members join only after acceptance.");
        } else {
            if (war == null) { FeatureService.tell(p, "No settlement war is proposed or active."); return true; }
            String status = war.getString("status"); String side = own.id.equals(war.getUniqueId("a")) ? "A" : "B";
            if (action.equals("feature_war_accept")) {
                if (!FeatureService.requireLeader(p, own)) return true;
                if (!side.equals("B") || !status.equals("offered")) { FeatureService.tell(p, "Only the receiving leader can accept a pending offer."); return true; }
                war.setString("status", "preparing"); addParticipant(war, "playersB", p.getUniqueID()); data.markDirty();
                announceBoth(p.getServer(), war, "War terms accepted. Members may /settlements war join or leave. Each leader must /settlements war start after reviewing the participant list.");
            } else if (action.equals("feature_war_decline")) {
                if (!FeatureService.requireLeader(p, own)) return true;
                if (status.equals("active")) FeatureService.tell(p, "Use war surrender during an active war.");
                else finish(p.getServer(), data, war, null, "War proposal cancelled.");
            } else if (action.equals("feature_war_join") || action.equals("feature_war_leave")) {
                if (!status.equals("preparing")) { FeatureService.tell(p, "Participants can change only after acceptance and before both leaders start."); return true; }
                if (action.endsWith("join")) {
                    removeParticipant(war, "playersA", p.getUniqueID()); removeParticipant(war, "playersB", p.getUniqueID());
                    addParticipant(war, "players" + side, p.getUniqueID());
                }
                else removeParticipant(war, "players" + side, p.getUniqueID());
                war.setBoolean("readyA", false); war.setBoolean("readyB", false); data.markDirty();
                FeatureService.tell(p, action.endsWith("join") ? "You opted into the war. Both leaders must confirm the changed roster." : "You opted out; war participants cannot damage you through PvP. Server ghost protection can also protect you from other damage while inside the active war claims.");
            } else if (action.equals("feature_war_start")) {
                if (!FeatureService.requireLeader(p, own)) return true;
                if (!status.equals("preparing")) { FeatureService.tell(p, "The other leader must accept the terms first."); return true; }
                if (war.getTagList("playersA", 10).tagCount() == 0 || war.getTagList("playersB", 10).tagCount() == 0) { FeatureService.tell(p, "Both sides need at least one participant."); return true; }
                if (!allParticipantsOnline(p.getServer(), war)) { FeatureService.tell(p, "Every opted-in participant must be online in the war dimension, belong to the agreed side, and appear on only one roster. Cancel/re-offer if the membership changed."); return true; }
                war.setBoolean("ready" + side, true); data.markDirty();
                if (war.getBoolean("readyA") && war.getBoolean("readyB")) begin(p.getServer(), data, war);
                else FeatureService.tell(p, "Your side is ready. Waiting for the other leader to confirm this roster.");
            } else if (action.equals("feature_war_surrender")) {
                if (!FeatureService.requireLeader(p, own)) return true;
                finish(p.getServer(), data, war, status.equals("active") ? side.equals("A") ? "B" : "A" : null, own.name + (status.equals("active") ? " surrendered." : " cancelled the agreement before combat."));
            }
        }
        FeatureService.refresh(p); return true;
    }

    private static void begin(MinecraftServer server, FeatureWorldData data, NBTTagCompound war) {
        Settlement a = FeatureService.settlement(server.getWorld(0), war.getUniqueId("a")), b = FeatureService.settlement(server.getWorld(0), war.getUniqueId("b"));
        WorldServer world = server.getWorld(war.getInteger("dimension")); if (a == null || b == null || world == null) return;
        if (war.getString("condition").equals("beds")) {
            NBTTagList bedsA = beds(world, a), bedsB = beds(world, b);
            if (bedsA == null || bedsB == null || bedsA.tagCount() == 0 || bedsB.tagCount() == 0) { announceBoth(server, war, "Bed objectives require loaded claims of radius 33 or less with 1..256 vanilla beds per side. Ready confirmations were reset."); war.setBoolean("readyA", false); war.setBoolean("readyB", false); return; }
            war.setTag("bedsA", bedsA); war.setTag("bedsB", bedsB);
        }
        NBTTagCompound blueprintA = FeatureService.find(data.blueprints, "settlement", a.id), blueprintB = FeatureService.find(data.blueprints, "settlement", b.id);
        if (blueprintA == null || blueprintB == null) { announceBoth(server, war, "A required saved blueprint was removed. War cannot start."); return; }
        NBTTagCompound frozenA = BlueprintService.eventSnapshot(world, blueprintA), frozenB = BlueprintService.eventSnapshot(world, blueprintB);
        if (frozenA == null || frozenB == null) { announceBoth(server, war, "Load both saved blueprint areas before starting the war."); war.setBoolean("readyA", false); war.setBoolean("readyB", false); return; }
        war.setTag("restoreA", frozenA); war.setTag("restoreB", frozenB);
        if (!EscrowService.collect(server, war)) { announceBoth(server, war, "Both survival leaders must be online and have the agreed untagged wager items. Neither inventory was changed; ready confirmations were reset."); war.setBoolean("readyA", false); war.setBoolean("readyB", false); data.markDirty(); return; }
        war.setString("status", "active"); war.setLong("ends", server.getWorld(0).getTotalWorldTime() + war.getInteger("minutes") * 1200L); data.markDirty();
        announceBoth(server, war, "War started. Only the agreed participants can attack the other side or modify its claim. Nonparticipants retain PvP protection" + (SettlementConfig.warGhostProtection ? " and take no damage while inside the active war claim areas." : ". Monsters and environmental damage are unaffected."));
    }

    static void tick(MinecraftServer server) {
        FeatureWorldData data = FeatureWorldData.get(server.getWorld(0)); long now = server.getWorld(0).getTotalWorldTime();
        for (int i = data.wars.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound war = data.wars.getCompoundTagAt(i); Settlement a = FeatureService.settlement(server.getWorld(0), war.getUniqueId("a")), b = FeatureService.settlement(server.getWorld(0), war.getUniqueId("b"));
            if (!SettlementConfig.warsWithoutRuin || a == null || b == null) { finish(server, data, war, null, "War cancelled because its settlement or server setting changed."); continue; }
            if (!war.getString("status").equals("active")) { if (now > war.getLong("expires")) finish(server, data, war, null, "War agreement expired before both sides started."); continue; }
            if (war.getString("condition").equals("beds")) {
                WorldServer world = server.getWorld(war.getInteger("dimension"));
                if (world != null) {
                    boolean lostA = noBeds(world, war.getTagList("bedsA", 10)), lostB = noBeds(world, war.getTagList("bedsB", 10));
                    if (lostA || lostB) { finish(server, data, war, lostA == lostB ? null : lostA ? "B" : "A", "Agreed bed objective completed."); continue; }
                }
            }
            if (now >= war.getLong("ends")) {
                int livesA = war.getInteger("livesA"), livesB = war.getInteger("livesB");
                finish(server, data, war, war.getString("condition").equals("lives") && livesA != livesB ? livesA > livesB ? "A" : "B" : null, "Agreed war period ended.");
            }
        }
    }

    static void died(EntityPlayerMP p) {
        FeatureWorldData data = FeatureWorldData.get(p.world);
        for (int i = 0; i < data.wars.tagCount(); i++) {
            NBTTagCompound war = data.wars.getCompoundTagAt(i);
            if (!war.getString("status").equals("active") || !war.getString("condition").equals("lives")) continue;
            String side = participant(war, p.getUniqueID()); if (side == null) continue;
            int remaining = Math.max(0, war.getInteger("lives" + side) - 1); war.setInteger("lives" + side, remaining); data.markDirty();
            announceBoth(p.getServer(), war, p.getName() + " died. Side " + side + " has " + remaining + " shared lives remaining.");
            if (remaining == 0) finish(p.getServer(), data, war, side.equals("A") ? "B" : "A", "Agreed lives objective completed.");
            return;
        }
    }

    static boolean canModify(EntityPlayerMP p, Settlement target) {
        if (!SettlementConfig.warsWithoutRuin) return false;
        NBTTagCompound war = findWar(FeatureWorldData.get(p.world), target.id);
        if (war == null || !war.getString("status").equals("active")) return false;
        String side = participant(war, p.getUniqueID());
        return side != null && memberOf(FeatureService.settlement(p.world, war.getUniqueId(side.toLowerCase(Locale.ROOT))), p.getUniqueID()) && !target.id.equals(war.getUniqueId(side.toLowerCase(Locale.ROOT)));
    }

    static boolean blocksPvP(EntityPlayerMP attacker, EntityPlayerMP victim) {
        FeatureWorldData data = FeatureWorldData.get(attacker.world);
        for (int i = 0; i < data.wars.tagCount(); i++) {
            NBTTagCompound war = data.wars.getCompoundTagAt(i); if (!war.getString("status").equals("active")) continue;
            String a = participant(war, attacker.getUniqueID()), v = participant(war, victim.getUniqueID());
            if (a == null && v == null) continue;
            // No friendly fire or PvP against an opted-out member. This does not grant PvE immunity.
            if (a != null && v != null && !a.equals(v)) return false;
            Settlement sa = FeatureService.settlement(attacker.world, war.getUniqueId("a")), sb = FeatureService.settlement(attacker.world, war.getUniqueId("b"));
            if (memberOf(sa, attacker.getUniqueID()) || memberOf(sb, attacker.getUniqueID()) || memberOf(sa, victim.getUniqueID()) || memberOf(sb, victim.getUniqueID())) return true;
        }
        return false;
    }

    static boolean isParticipantAt(EntityPlayerMP player, BlockPos pos) {
        FeatureWorldData data = FeatureWorldData.get(player.world);
        for (int i = 0; i < data.wars.tagCount(); i++) {
            NBTTagCompound w = data.wars.getCompoundTagAt(i);
            if (!w.getString("status").equals("active") || participant(w, player.getUniqueID()) == null) continue;
            Settlement a = FeatureService.settlement(player.world, w.getUniqueId("a")), b = FeatureService.settlement(player.world, w.getUniqueId("b"));
            if ((a != null && a.contains(player.dimension, pos)) || (b != null && b.contains(player.dimension, pos))) return true;
        }
        return false;
    }

    static boolean ghostProtected(EntityPlayerMP player) {
        if (!SettlementConfig.warsWithoutRuin || !SettlementConfig.warGhostProtection) return false;
        FeatureWorldData data = FeatureWorldData.get(player.world);
        for (int i = 0; i < data.wars.tagCount(); i++) {
            NBTTagCompound war = data.wars.getCompoundTagAt(i);
            if (!war.getString("status").equals("active") || participant(war, player.getUniqueID()) != null) continue;
            Settlement a = FeatureService.settlement(player.world, war.getUniqueId("a")), b = FeatureService.settlement(player.world, war.getUniqueId("b"));
            if ((memberOf(a, player.getUniqueID()) && a.contains(player.dimension, player.getPosition()))
                    || (memberOf(b, player.getUniqueID()) && b.contains(player.dimension, player.getPosition()))) return true;
        }
        return false;
    }

    private static boolean memberOf(Settlement s, UUID player) { return s != null && (s.owner.equals(player) || s.members.contains(player)); }
    static boolean hasWar(World world, UUID settlement) {
        Settlement target = FeatureService.settlement(world, settlement); if (target == null) return false;
        FeatureWorldData data = FeatureWorldData.get(world);
        for (int i = 0; i < data.wars.tagCount(); i++) {
            NBTTagCompound w = data.wars.getCompoundTagAt(i);
            Settlement a = FeatureService.settlement(world, w.getUniqueId("a")), b = FeatureService.settlement(world, w.getUniqueId("b"));
            if ((a != null && a.owner.equals(target.owner)) || (b != null && b.owner.equals(target.owner))) return true;
        }
        return false;
    }
    private static NBTTagCompound findWar(FeatureWorldData data, UUID settlement) {
        for (int i = 0; i < data.wars.tagCount(); i++) { NBTTagCompound w = data.wars.getCompoundTagAt(i); if (settlement.equals(w.getUniqueId("a")) || settlement.equals(w.getUniqueId("b"))) return w; } return null;
    }
    private static String participant(NBTTagCompound war, UUID id) {
        for (String side : Arrays.asList("A", "B")) { NBTTagList list = war.getTagList("players" + side, 10); for (int i = 0; i < list.tagCount(); i++) if (id.equals(list.getCompoundTagAt(i).getUniqueId("id"))) return side; } return null;
    }
    private static void addParticipant(NBTTagCompound war, String key, UUID id) {
        NBTTagList list = war.getTagList(key, 10); for (int i = 0; i < list.tagCount(); i++) if (id.equals(list.getCompoundTagAt(i).getUniqueId("id"))) return;
        if (list.tagCount() >= 128) return; NBTTagCompound n = new NBTTagCompound(); n.setUniqueId("id", id); list.appendTag(n);
    }
    private static void removeParticipant(NBTTagCompound war, String key, UUID id) { NBTTagList list = war.getTagList(key, 10); for (int i = list.tagCount() - 1; i >= 0; i--) if (id.equals(list.getCompoundTagAt(i).getUniqueId("id"))) list.removeTag(i); }
    private static boolean allParticipantsOnline(MinecraftServer server, NBTTagCompound war) {
        Settlement a = FeatureService.settlement(server.getWorld(0), war.getUniqueId("a")), b = FeatureService.settlement(server.getWorld(0), war.getUniqueId("b"));
        if (!WarPolicy.validRosters(war, a, b)) return false;
        Set<UUID> seen = new HashSet<>();
        for (String side : Arrays.asList("A", "B")) {
            Settlement s = FeatureService.settlement(server.getWorld(0), war.getUniqueId(side.toLowerCase(Locale.ROOT)));
            NBTTagList list = war.getTagList("players" + side, 10);
            for (int i = 0; i < list.tagCount(); i++) {
                UUID id = list.getCompoundTagAt(i).getUniqueId("id"); EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                if (!seen.add(id) || p == null || p.dimension != war.getInteger("dimension") || !memberOf(s, id)) return false;
            }
        }
        return true;
    }

    private static NBTTagList beds(WorldServer world, Settlement s) {
        // Explicit loaded bounded claim scan; no chunk forcing and no whole-world search.
        long volume = (s.radius * 2L + 1) * (s.radius * 2L + 1) * 256L;
        if (volume > 1200000) return null;
        NBTTagList beds = new NBTTagList();
        for (int x = s.flag.getX() - s.radius; x <= s.flag.getX() + s.radius; x++) for (int z = s.flag.getZ() - s.radius; z <= s.flag.getZ() + s.radius; z++) {
            if (!world.isBlockLoaded(new BlockPos(x, 64, z))) return null;
            for (int y = 0; y < 256; y++) {
                BlockPos p = new BlockPos(x, y, z); net.minecraft.block.state.IBlockState state = world.getBlockState(p);
                if (state.getBlock() == Blocks.BED) {
                    BlockPos other = p.offset(state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD ? state.getValue(BlockBed.FACING).getOpposite() : state.getValue(BlockBed.FACING));
                    if (!s.contains(s.dimension, other) || !world.isBlockLoaded(other) || world.getBlockState(other).getBlock() != Blocks.BED) return null;
                    NBTTagCompound bed = new NBTTagCompound(); BedRestorationService.describe(world, p, bed); beds.appendTag(bed); if (beds.tagCount() > 512) return null;
                }
            }
        }
        return beds;
    }
    private static boolean noBeds(WorldServer world, NBTTagList beds) {
        for (int i = 0; i < beds.tagCount(); i++) { BlockPos p = BlockPos.fromLong(beds.getCompoundTagAt(i).getLong("pos")); if (!world.isBlockLoaded(p) || world.getBlockState(p).getBlock() instanceof BlockBed) return false; } return true;
    }

    private static void finish(MinecraftServer server, FeatureWorldData data, NBTTagCompound war, String winner, String reason) {
        announceBoth(server, war, reason + (winner == null ? " Result: draw." : " Winner: " + sideName(server, war, winner) + ".") + " Saved blueprint areas will be repaired without restoring container inventories." + (war.getInteger("stakeCount") > 0 ? " Agreed inventory wagers are paid to the winning leader or refunded on a draw." : "") + (war.getBoolean("annex") && winner != null ? " The agreed annexation combines both groups under the winning leader while retaining their separate claim areas." : " Settlement ownership is unchanged."));
        EscrowService.finish(server, war, winner);
        WorldServer world = server.getWorld(war.getInteger("dimension"));
        if (world != null && war.getString("status").equals("active")) for (String side : Arrays.asList("A", "B")) { Settlement s = FeatureService.settlement(world, war.getUniqueId(side.toLowerCase(Locale.ROOT))); if (s != null && war.hasKey("restore" + side, 10)) BlueprintService.queueRestore(world, s, war.getCompoundTag("restore" + side)); }
        if (world != null && war.getString("status").equals("active")) for (String side : Arrays.asList("A", "B")) { Settlement s = FeatureService.settlement(world, war.getUniqueId(side.toLowerCase(Locale.ROOT))); if (s != null && war.hasKey("beds" + side, 9)) BedRestorationService.queue(world, s, war.getTagList("beds" + side, 10)); }
        if (winner != null && war.getBoolean("annex") && war.getString("status").equals("active")) annex(server, war, winner);
        for (int i = data.wars.tagCount() - 1; i >= 0; i--) if (war.getUniqueId("id").equals(data.wars.getCompoundTagAt(i).getUniqueId("id"))) data.wars.removeTag(i); data.markDirty();
    }
    private static String sideName(MinecraftServer server, NBTTagCompound war, String side) { Settlement s = FeatureService.settlement(server.getWorld(0), war.getUniqueId(side.toLowerCase(Locale.ROOT))); return s == null ? side : s.name; }
    private static void announceBoth(MinecraftServer server, NBTTagCompound war, String text) { for (String side : Arrays.asList("a", "b")) { Settlement s = FeatureService.settlement(server.getWorld(0), war.getUniqueId(side)); if (s != null) FeatureService.announce(server, s, text); } }
    private static void annex(MinecraftServer server, NBTTagCompound war, String winner) {
        UUID ownerA = war.getUniqueId("ownerA"), ownerB = war.getUniqueId("ownerB"), winningOwner = war.getUniqueId("owner" + winner);
        Set<UUID> members = new LinkedHashSet<>(); Map<UUID, String> names = new LinkedHashMap<>();
        for (Settlement s : SettlementData.get(server.getWorld(0)).settlements) if (s.owner.equals(ownerA) || s.owner.equals(ownerB)) { members.addAll(s.members); members.add(s.owner); names.putAll(s.memberNames); }
        for (Settlement s : SettlementData.get(server.getWorld(0)).settlements) if (s.owner.equals(ownerA) || s.owner.equals(ownerB)) { s.owner = winningOwner; s.members.clear(); s.members.addAll(members); s.memberNames.clear(); s.memberNames.putAll(names); }
        SettlementData.get(server.getWorld(0)).markDirty();
    }
    private static String stakeDescription(NBTTagCompound war) { return (war.getInteger("stakeCount") > 0 ? " Each leader stakes " + war.getInteger("stakeCount") + " " + war.getString("stakeItem") + ":" + war.getInteger("stakeMeta") + "; winner takes both stakes, draw refunds." : " No wager.") + (war.getBoolean("annex") ? " Annexation enabled: loser group's claims and members join the winning leader." : " No annexation."); }
    private static String summary(EntityPlayerMP p, Settlement own, NBTTagCompound war) { return "War " + war.getString("status") + "; " + war.getString("condition") + "; lives " + war.getInteger("livesA") + "/" + war.getInteger("livesB") + "; participants " + war.getTagList("playersA", 10).tagCount() + "/" + war.getTagList("playersB", 10).tagCount() + "; " + (participant(war, p.getUniqueID()) == null ? "you are not participating." : "you opted in.") + stakeDescription(war); }

    static void snapshot(EntityPlayerMP p, NBTTagCompound n, Settlement s) {
        if (s == null) return; NBTTagCompound war = findWar(FeatureWorldData.get(p.world), s.id); if (war == null) return;
        NBTTagCompound w = new NBTTagCompound(); String side = s.id.equals(war.getUniqueId("a")) ? "A" : "B";
        w.setString("status", war.getString("status")); w.setString("condition", war.getString("condition")); w.setString("side", side); w.setString("other", sideName(p.getServer(), war, side.equals("A") ? "B" : "A"));
        w.setInteger("minutes", war.getInteger("minutes")); w.setInteger("livesA", war.getInteger("livesA")); w.setInteger("livesB", war.getInteger("livesB")); w.setInteger("participants", war.getTagList("playersA", 10).tagCount() + war.getTagList("playersB", 10).tagCount()); w.setBoolean("joined", participant(war, p.getUniqueID()) != null); w.setBoolean("readyA", war.getBoolean("readyA")); w.setBoolean("readyB", war.getBoolean("readyB")); w.setInteger("remainingSeconds", (int)Math.max(0, (war.getLong("ends") - p.getServer().getWorld(0).getTotalWorldTime()) / 20)); n.setTag("war", w);
        w.setString("stakeItem", war.getString("stakeItem")); w.setInteger("stakeMeta", war.getInteger("stakeMeta")); w.setInteger("stakeCount", war.getInteger("stakeCount"));
        w.setBoolean("annex", war.getBoolean("annex"));
    }
}
