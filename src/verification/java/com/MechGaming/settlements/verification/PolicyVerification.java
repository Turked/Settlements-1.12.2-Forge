package com.MechGaming.settlements.verification;

import com.MechGaming.settlements.common.ZonePolicy;
import com.MechGaming.settlements.config.SettlementConfig;
import com.MechGaming.settlements.data.Settlement;
import com.MechGaming.settlements.data.SettlementData;
import com.MechGaming.settlements.data.Zone;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public final class PolicyVerification {
    private static int checks;
    private PolicyVerification() { }

    public static void run() {
        checks = 0;
        BlockPos center = BlockPos.ORIGIN;
        check(ZonePolicy.allows(Collections.emptyList(), true, 0, center, 32), "No zones inherit allow default");
        check(!ZonePolicy.allows(Collections.emptyList(), false, 0, center, 32), "No zones inherit restrict default");
        Zone restrictedIsland = zone(0, 0, 0, 0, false);
        Zone allowedIsland = zone(0, 0, 0, 0, true);
        check(!ZonePolicy.allows(Collections.singletonList(restrictedIsland), true, 0, center, 32), "Restricted interior island invalidates claim despite allowed corners");
        check(ZonePolicy.allows(Arrays.asList(restrictedIsland, allowedIsland), true, 0, center, 32), "Later allow restores previously restricted island");
        check(!ZonePolicy.allows(Arrays.asList(allowedIsland, restrictedIsland), true, 0, center, 32), "Reversed priority leaves island restricted");
        Zone edge = zone(32, 32, -32, 32, false);
        check(!ZonePolicy.allows(Collections.singletonList(edge), true, 0, center, 32), "Touching inclusive claim edge is restricted");
        edge.minX = 33; edge.maxX = 40;
        check(ZonePolicy.allows(Collections.singletonList(edge), true, 0, center, 32), "Zone immediately outside claim does not overlap");
        restrictedIsland.dimension = -1;
        check(ZonePolicy.allows(Collections.singletonList(restrictedIsland), true, 0, center, 32), "Other dimension has no influence");
        restrictedIsland.dimension = 0; restrictedIsland.type = "spawn";
        check(ZonePolicy.allows(Collections.singletonList(restrictedIsland), true, 0, center, 32), "Spawn zones do not change build permission");
        Zone partial = zone(-32, 31, -32, 32, true);
        check(!ZonePolicy.allows(Collections.singletonList(partial), false, 0, center, 32), "Partially allowed claim remains restricted");
        partial.maxX = 32;
        check(ZonePolicy.allows(Collections.singletonList(partial), false, 0, center, 32), "Full explicit allow satisfies restricted default");
        Zone distant = zone(Integer.MAX_VALUE - 2, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE + 2, false);
        check(ZonePolicy.allows(Collections.singletonList(distant), true, 0, center, 32), "Extreme zone coordinates clip without integer overflow");
        check(ZonePolicy.allows(Collections.emptyList(), true, 0, new BlockPos(-29999980, 80, -29999980), 256), "Maximum persisted radius remains bounded at distant coordinates");
        boolean rejected = false;
        try { ZonePolicy.allows(Collections.emptyList(), true, 0, center, Integer.MAX_VALUE); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Unbounded radius rejected before allocating coverage");
        rejected = false;
        try { ZonePolicy.allows(Collections.emptyList(), true, 0, center, -1); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Negative radius rejected before allocating coverage");

        Settlement s = new Settlement(); s.owner = UUID.randomUUID(); UUID unnamedMember = UUID.randomUUID(); s.members.add(unnamedMember);
        check(Settlement.read(s.write()).members.contains(unnamedMember), "Membership without a display name survives persistence");
        NBTTagCompound malformed = new NBTTagCompound(), persisted = new NBTTagCompound(); NBTTagList list = new NBTTagList(); list.appendTag(malformed); list.appendTag(s.write()); persisted.setTag("settlements", list);
        SettlementData restored = new SettlementData(); restored.readFromNBT(persisted);
        check(restored.settlements.size() == 1 && restored.settlements.get(0).id.equals(s.id), "Malformed identity record does not prevent loading valid claims");
        int originalRadius = SettlementConfig.radius; NBTTagCompound invalidConfig = new NBTTagCompound(); invalidConfig.setInteger("radius", 4); invalidConfig.setString("blacklist", "invalid registry name"); rejected = false;
        try { SettlementConfig.apply(invalidConfig); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected && SettlementConfig.radius == originalRadius, "Invalid blacklist rejects configuration before any live mutation");
        System.out.println("Passed " + checks + " settlement zone policy and invalid-state checks.");
    }
    private static Zone zone(int minX, int maxX, int minZ, int maxZ, boolean allow) { Zone z = new Zone(); z.minX = minX; z.maxX = maxX; z.minZ = minZ; z.maxZ = maxZ; z.allowed = allow; return z; }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
