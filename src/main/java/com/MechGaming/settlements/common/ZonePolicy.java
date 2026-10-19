package com.MechGaming.settlements.common;

import com.MechGaming.settlements.data.Zone;
import net.minecraft.util.math.BlockPos;
import java.util.BitSet;
import java.util.List;

/** Pure rectangle coverage calculation; later build zones override earlier ones. */
public final class ZonePolicy {
    private ZonePolicy() { }

    public static boolean allows(List<Zone> zones, boolean defaultAllowed, int dimension, BlockPos center, int radius) {
        if (radius < 0 || radius > 256) throw new IllegalArgumentException("Claim radius must be between 0 and 256");
        int width = radius * 2 + 1;
        long left = (long) center.getX() - radius, top = (long) center.getZ() - radius;
        BitSet[] rows = new BitSet[width];
        for (int row = 0; row < width; row++) {
            rows[row] = new BitSet(width);
            if (defaultAllowed) rows[row].set(0, width);
        }
        for (Zone zone : zones) {
            if (!"build".equals(zone.type) || zone.dimension != dimension) continue;
            int x1 = (int) Math.max(0, Math.min(width, (long) zone.minX - left));
            int x2 = (int) Math.max(-1, Math.min(width - 1, (long) zone.maxX - left));
            int z1 = (int) Math.max(0, Math.min(width, (long) zone.minZ - top));
            int z2 = (int) Math.max(-1, Math.min(width - 1, (long) zone.maxZ - top));
            if (x1 > x2 || z1 > z2) continue;
            for (int row = z1; row <= z2; row++) rows[row].set(x1, x2 + 1, zone.allowed);
        }
        for (BitSet row : rows) if (row.nextClearBit(0) < width) return false;
        return true;
    }
}
