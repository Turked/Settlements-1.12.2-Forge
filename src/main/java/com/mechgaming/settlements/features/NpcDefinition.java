package com.mechgaming.settlements.features;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public Gson schema for config/settlements/npcs/*.json, generated example by DataGen. */
public final class NpcDefinition {
    public String id = "settler";
    public String name = "Settler";
    public int minRoomVolume = 8;
    public int maxRoomVolume = 512;
    public Map<String, Integer> requiredBlocks = new LinkedHashMap<>();
    public List<Trade> trades = new ArrayList<>();
    public List<StructureBlock> structureBlocks = new ArrayList<>();

    public static final class Trade {
        public String buy = "minecraft:emerald", sell = "minecraft:bread";
        public int buyCount = 1, sellCount = 3, buyMeta = 0, sellMeta = 0, uses = 12;
    }

    /** Optional block additions relative to the room marker; only empty positions are filled. */
    public static final class StructureBlock {
        public int x, y, z, meta;
        public String block;
    }
}
