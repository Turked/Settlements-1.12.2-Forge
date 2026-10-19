package com.mechgaming.settlements.verification;

import com.mechgaming.settlements.data.Proposal;
import com.mechgaming.settlements.data.Settlement;
import com.mechgaming.settlements.data.SettlementData;
import com.mechgaming.settlements.data.Zone;
import com.mechgaming.settlements.network.ClientAction;
import com.mechgaming.settlements.features.FeatureWorldData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/** Executable regression checks using the actual Minecraft 1.12 NBT and wire codecs. */
public final class StateVerification {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Settlement original = new Settlement();
        original.owner = UUID.randomUUID();
        original.members.add(original.owner);
        original.memberNames.put(original.owner, "MechGaming");
        UUID member = UUID.randomUUID(), request = UUID.randomUUID();
        original.members.add(member);
        original.memberNames.put(member, "Member");
        original.requests.put(request, "Applicant");
        original.name = "Settlements – Persistence";
        original.dimension = -1;
        original.flag = new BlockPos(-100, 70, 200);
        original.radius = 32;
        original.created = 12345L;
        check(original.contains(-1, new BlockPos(-132, 0, 232)), "Inclusive claim edges");
        check(original.contains(-1, new BlockPos(-68, 255, 168)), "Claims cover full height");
        check(!original.contains(0, original.flag), "Dimensions are isolated");
        check(!original.contains(-1, new BlockPos(-133, 70, 200)), "Outside edge rejected");
        check(!original.contains(-1, new BlockPos(29999999, 70, 200)), "Distant coordinates rejected");
        Settlement restored = Settlement.read(roundTrip(original.write()));
        check(restored.id.equals(original.id) && restored.owner.equals(original.owner), "Claim identity survives NBT compression");
        check(restored.memberNames.equals(original.memberNames) && restored.members.equals(original.members), "Membership survives reload");
        check(restored.requests.equals(original.requests), "Inbox survives reload");
        check(restored.flag.equals(original.flag) && restored.name.equals(original.name) && restored.created == original.created, "Claim details survive reload");

        Zone zone = new Zone();
        zone.dimension = -1; zone.minX = -200; zone.maxX = -50; zone.minZ = 100; zone.maxZ = 300;
        zone.type = "spawn"; zone.allowed = false; zone.preventBuild = true; zone.decay = true;
        zone.blacklist.add("minecraft:tnt");
        Zone loadedZone = Zone.read(roundTrip(zone.write()));
        check(loadedZone.contains(-1, new BlockPos(-200, 255, 100)), "Inclusive zone corners");
        check(!loadedZone.contains(0, original.flag), "Zone dimensions are isolated");
        check(loadedZone.blacklist.contains("minecraft:tnt") && loadedZone.decay && loadedZone.preventBuild, "Zone policies persist");
        check(loadedZone.type.equals("spawn") && !loadedZone.allowed, "Spawn policy persists");

        Proposal proposal = new Proposal();
        proposal.owner = original.owner; proposal.flag = original.flag; proposal.dimension = -1; proposal.radius = 32;
        proposal.expires = 55555; proposal.supporters.put(original.owner, "MechGaming");
        Proposal loadedProposal = Proposal.read(roundTrip(proposal.write()));
        check(loadedProposal.supporters.equals(proposal.supporters) && loadedProposal.expires == proposal.expires, "Proposal support and expiry persist");

        SettlementData data = new SettlementData();
        data.settlements.add(original); data.zones.add(zone); data.proposals.add(proposal);
        SettlementData loadedData = new SettlementData();
        NBTTagCompound saved = roundTrip(data.writeToNBT(new NBTTagCompound()));
        loadedData.readFromNBT(saved); loadedData.readFromNBT(saved);
        check(loadedData.settlements.size() == 1 && loadedData.zones.size() == 1 && loadedData.proposals.size() == 1, "Reload does not duplicate world records");
        check(loadedData.settlements.get(0).requests.containsKey(request), "Whole-world save retains inbox");
        NBTTagCompound malformed = original.write(); malformed.setInteger("radius", Integer.MAX_VALUE);
        check(Settlement.read(malformed).radius <= 256, "Persisted radius is bounded");
        malformed.setInteger("radius", -1);
        check(Settlement.read(malformed).radius >= 1, "Persisted radius stays positive");

        NBTTagCompound payload = new NBTTagCompound(); payload.setBoolean("confirm", true);
        ClientAction action = new ClientAction("disband", original.flag, 17, original.id.toString(), payload);
        ByteBuf bytes = Unpooled.buffer();
        action.toBytes(bytes);
        ClientAction decoded = new ClientAction(); decoded.fromBytes(bytes); bytes.release();
        check(decoded.action.equals("disband") && decoded.pos.equals(original.flag) && decoded.value == 17, "Packet action/position round trip");
        check(decoded.text.equals(original.id.toString()) && decoded.payload.getBoolean("confirm"), "Packet target and confirmation round trip");
        bytes = Unpooled.buffer();
        new ClientAction("sync", BlockPos.ORIGIN, 0, repeat('x', 257), payload).toBytes(bytes);
        boolean rejected = false;
        try { new ClientAction().fromBytes(bytes); } catch (IllegalArgumentException expected) { rejected = true; } finally { bytes.release(); }
        check(rejected, "Oversized packet text rejected");
        bytes = Unpooled.buffer(20000); bytes.writeZero(20000); rejected = false;
        try { new ClientAction().fromBytes(bytes); } catch (IllegalArgumentException expected) { rejected = true; } finally { bytes.release(); }
        check(rejected, "Oversized packet frame rejected before decoding");
        FeatureWorldData features = new FeatureWorldData();
        NBTTagCompound record = new NBTTagCompound(); record.setString("marker", "retained");
        features.rooms.appendTag(record.copy()); features.blueprints.appendTag(record.copy());
        features.raids.appendTag(record.copy()); features.wars.appendTag(record.copy());
        features.restorations.appendTag(record.copy()); features.payouts.appendTag(record.copy());
        features.bedRestorations.appendTag(record.copy());
        FeatureWorldData restoredFeatures = new FeatureWorldData();
        restoredFeatures.readFromNBT(roundTrip(features.writeToNBT(new NBTTagCompound())));
        check(restoredFeatures.rooms.tagCount() == 1 && restoredFeatures.blueprints.tagCount() == 1, "Rooms and blueprints persist");
        check(restoredFeatures.raids.tagCount() == 1 && restoredFeatures.wars.tagCount() == 1, "Event records persist");
        check(restoredFeatures.restorations.tagCount() == 1 && restoredFeatures.payouts.tagCount() == 1, "Pending restoration and offline payouts persist");
        check(restoredFeatures.bedRestorations.tagCount() == 1, "Pending vanilla bed restoration persists");
        PolicyVerification.run();
        com.mechgaming.settlements.features.FeatureVerification.run();
        ResourceVerification.run();
        System.out.println("Passed " + checks + " settlement state and packet checks.");
    }
    private static NBTTagCompound roundTrip(NBTTagCompound data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(data, out);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(out.toByteArray()));
    }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static String repeat(char c, int count) {
        char[] text = new char[count]; java.util.Arrays.fill(text, c); return new String(text);
    }
}
