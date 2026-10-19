package com.MechGaming.settlements.features;

import com.MechGaming.settlements.data.Settlement;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.io.*;
import java.util.UUID;

/** Regression checks for signed war sides and restart-safe deferred event state. */
public final class FeatureVerification {
    private FeatureVerification() { }
    public static void run() throws Exception {
        Settlement a = side(), b = side(); UUID member = UUID.randomUUID(); a.members.add(member);
        NBTTagCompound war = new NBTTagCompound(); war.setUniqueId("ownerA", a.owner); war.setUniqueId("ownerB", b.owner);
        war.setTag("playersA", players(a.owner, member)); war.setTag("playersB", players(b.owner));
        require(WarPolicy.validRosters(war, a, b), "Different owners and correct member rosters are valid");
        war.setTag("playersA", players(a.owner, a.owner));
        require(!WarPolicy.validRosters(war, a, b), "A duplicate on one side is rejected");
        b.members.add(member); war.setTag("playersA", players(a.owner, member)); war.setTag("playersB", players(b.owner, member));
        require(!WarPolicy.validRosters(war, a, b), "One player cannot consent on both sides");
        war.setTag("playersB", players(b.owner)); a.members.remove(member);
        require(!WarPolicy.validRosters(war, a, b), "Leaving the agreed side invalidates an old signature");
        a.members.add(member); UUID previousOwner = b.owner; b.owner = UUID.randomUUID();
        require(!WarPolicy.validRosters(war, a, b), "An ownership change invalidates the prior leader agreement"); b.owner = previousOwner;
        require(!WarPolicy.validRosters(war, a, null), "Missing claim cannot begin combat");
        war.setTag("playersA", new NBTTagList()); require(!WarPolicy.validRosters(war, a, b), "An empty side cannot begin combat");

        FeatureWorldData data = new FeatureWorldData();
        NBTTagCompound bed = new NBTTagCompound(); bed.setUniqueId("settlement", a.id); bed.setLong("head", -999); bed.setLong("foot", -1000); bed.setInteger("headMeta", 11); bed.setInteger("footMeta", 3); bed.setInteger("color", 5); bed.setInteger("dimension", -1); data.bedRestorations.appendTag(bed);
        NBTTagCompound payout = new NBTTagCompound(); payout.setUniqueId("owner", a.owner); NBTTagCompound stack = new NBTTagCompound(); stack.setString("id", "minecraft:emerald"); stack.setByte("Count", (byte)12); stack.setShort("Damage", (short)0); payout.setTag("stack", stack); data.payouts.appendTag(payout);
        NBTTagCompound restoration = new NBTTagCompound(); restoration.setUniqueId("snapshot", UUID.randomUUID()); restoration.setUniqueId("settlement", b.id); data.restorations.appendTag(restoration);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(data.writeToNBT(new NBTTagCompound()), bytes);
        FeatureWorldData restored = new FeatureWorldData(); restored.readFromNBT(CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray())));
        require(restored.bedRestorations.getCompoundTagAt(0).equals(bed), "Bed orientation, color, owner and dimension survive compressed saves");
        require(restored.payouts.getCompoundTagAt(0).equals(payout), "Offline wager destination and remaining stack survive compressed saves");
        require(restored.restorations.getCompoundTagAt(0).equals(restoration), "Pending one-time restoration identity survives compressed saves");
        restored.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        require(restored.bedRestorations.tagCount() == 1 && restored.payouts.tagCount() == 1, "Repeated reload does not duplicate bed repairs or payouts");
        System.out.println("Passed 11 feature consent and deferred-state checks.");
    }
    private static Settlement side() { Settlement s = new Settlement(); s.owner = UUID.randomUUID(); s.members.add(s.owner); return s; }
    private static NBTTagList players(UUID... ids) { NBTTagList list = new NBTTagList(); for (UUID id : ids) { NBTTagCompound n = new NBTTagCompound(); n.setUniqueId("id", id); list.appendTag(n); } return list; }
    private static void require(boolean value, String description) { if (!value) throw new AssertionError(description); }
}
