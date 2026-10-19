package com.mechgaming.settlements.features;

import com.mechgaming.settlements.data.Settlement;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.util.*;

/** Pure consent-roster checks, independent of loaded worlds and online player objects. */
final class WarPolicy {
    private WarPolicy() { }
    static boolean validRosters(NBTTagCompound war, Settlement a, Settlement b) {
        if (a == null || b == null || a.id.equals(b.id) || a.owner.equals(b.owner)) return false;
        if (!a.owner.equals(war.getUniqueId("ownerA")) || !b.owner.equals(war.getUniqueId("ownerB"))) return false;
        Set<UUID> seen = new HashSet<>();
        for (String side : Arrays.asList("A", "B")) {
            Settlement s = side.equals("A") ? a : b; NBTTagList list = war.getTagList("players" + side, 10);
            if (list.tagCount() == 0 || list.tagCount() > 128) return false;
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound participant = list.getCompoundTagAt(i);
                if (!participant.hasUniqueId("id")) return false;
                UUID id = participant.getUniqueId("id");
                if (!seen.add(id) || (!s.owner.equals(id) && !s.members.contains(id))) return false;
            }
        }
        return true;
    }
}
