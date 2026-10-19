package com.mechgaming.settlements.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** A dimension-scoped square claim, extending through the world's full height. */
public final class Settlement {
    public UUID id = UUID.randomUUID(), owner;
    public String name = "Settlement";
    public int dimension, radius = 32;
    public BlockPos flag = BlockPos.ORIGIN;
    public boolean accepting = true;
    public long created;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final Map<UUID, String> requests = new LinkedHashMap<>(), memberNames = new LinkedHashMap<>();

    public boolean contains(int dim, BlockPos pos) {
        return dimension == dim && Math.abs((long) pos.getX() - flag.getX()) <= radius
                && Math.abs((long) pos.getZ() - flag.getZ()) <= radius;
    }
    public NBTTagCompound write() {
        NBTTagCompound n = new NBTTagCompound();
        n.setUniqueId("id", id); n.setUniqueId("owner", owner); n.setString("name", name);
        n.setInteger("dimension", dimension); n.setLong("flag", flag.toLong()); n.setInteger("radius", radius);
        n.setBoolean("accepting", accepting); n.setLong("created", created);
        Map<UUID,String> allMembers=new LinkedHashMap<>(memberNames);for(UUID member:members)allMembers.putIfAbsent(member,"Player");allMembers.putIfAbsent(owner,"Owner");
        n.setTag("members", writeNames(allMembers)); n.setTag("requests", writeNames(requests));
        return n;
    }
    public static Settlement read(NBTTagCompound n) {
        if(!n.hasUniqueId("id")||!n.hasUniqueId("owner"))throw new IllegalArgumentException("Settlement is missing its identity or owner");
        Settlement s = new Settlement(); s.id=n.getUniqueId("id"); s.owner=n.getUniqueId("owner");
        s.name=n.getString("name"); s.dimension=n.getInteger("dimension"); s.flag=BlockPos.fromLong(n.getLong("flag"));
        s.radius=Math.max(1, Math.min(256,n.getInteger("radius"))); s.accepting=n.getBoolean("accepting"); s.created=n.getLong("created");
        readNames(n.getTagList("members",10),s.memberNames); s.members.addAll(s.memberNames.keySet());
        s.members.add(s.owner); s.memberNames.putIfAbsent(s.owner,"Owner");
        readNames(n.getTagList("requests",10),s.requests); return s;
    }
    public static NBTTagList writeNames(Map<UUID,String> names) {
        NBTTagList a=new NBTTagList(); names.forEach((id,name)->{NBTTagCompound n=new NBTTagCompound();n.setUniqueId("id",id);n.setString("name",name);a.appendTag(n);});return a;
    }
    public static void readNames(NBTTagList list,Map<UUID,String> names) {
        for(int i=0;i<list.tagCount() && i<1024;i++){NBTTagCompound n=list.getCompoundTagAt(i); if(n.hasUniqueId("id"))names.put(n.getUniqueId("id"),n.getString("name"));}
    }
}
