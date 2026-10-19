package com.mechgaming.settlements.data;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.util.*;

public final class Proposal {
    public UUID id=UUID.randomUUID(),owner;
    public String name="Settlement";
    public int dimension,radius;
    public BlockPos flag=BlockPos.ORIGIN;
    public long expires;
    public final Map<UUID,String> supporters=new LinkedHashMap<>();
    public boolean contains(int dim,BlockPos p){return dim==dimension&&Math.abs((long)p.getX()-flag.getX())<=radius&&Math.abs((long)p.getZ()-flag.getZ())<=radius;}
    public NBTTagCompound write(){NBTTagCompound n=new NBTTagCompound();n.setUniqueId("id",id);n.setUniqueId("owner",owner);n.setString("name",name);n.setInteger("dimension",dimension);n.setInteger("radius",radius);n.setLong("flag",flag.toLong());n.setLong("expires",expires);n.setTag("supporters",Settlement.writeNames(supporters));return n;}
    public static Proposal read(NBTTagCompound n){if(!n.hasUniqueId("id")||!n.hasUniqueId("owner"))throw new IllegalArgumentException("Proposal is missing identity or owner");Proposal p=new Proposal();p.id=n.getUniqueId("id");p.owner=n.getUniqueId("owner");p.name=n.getString("name");p.dimension=n.getInteger("dimension");p.radius=Math.max(1,Math.min(256,n.getInteger("radius")));p.flag=BlockPos.fromLong(n.getLong("flag"));p.expires=n.getLong("expires");Settlement.readNames(n.getTagList("supporters",10),p.supporters);return p;}
}
