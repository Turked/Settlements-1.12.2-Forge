package com.mechgaming.settlements.data;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Later matching zones override earlier zones. Coordinates are inclusive. */
public final class Zone {
    public UUID id=UUID.randomUUID();
    public String name="Zone",type="build";
    public int dimension,minX,maxX,minZ,maxZ;
    public boolean allowed,preventBuild,decay;
    public final Set<String> blacklist=new LinkedHashSet<>();
    public boolean contains(int dim,BlockPos p){return dimension==dim&&p.getX()>=minX&&p.getX()<=maxX&&p.getZ()>=minZ&&p.getZ()<=maxZ;}
    public NBTTagCompound write(){NBTTagCompound n=new NBTTagCompound();n.setUniqueId("id",id);n.setString("name",name);n.setString("type",type);n.setInteger("dimension",dimension);n.setInteger("minX",minX);n.setInteger("maxX",maxX);n.setInteger("minZ",minZ);n.setInteger("maxZ",maxZ);n.setBoolean("allowed",allowed);n.setBoolean("preventBuild",preventBuild);n.setBoolean("decay",decay);n.setString("blacklist",String.join(",",blacklist));return n;}
    public static Zone read(NBTTagCompound n){if(!n.hasUniqueId("id"))throw new IllegalArgumentException("Zone is missing identity");Zone z=new Zone();z.id=n.getUniqueId("id");z.name=n.getString("name");z.type="spawn".equals(n.getString("type"))?"spawn":"build";z.dimension=n.getInteger("dimension");z.minX=n.getInteger("minX");z.maxX=n.getInteger("maxX");z.minZ=n.getInteger("minZ");z.maxZ=n.getInteger("maxZ");if(z.minX>z.maxX||z.minZ>z.maxZ)throw new IllegalArgumentException("Zone has inverted bounds");z.allowed=n.getBoolean("allowed");z.preventBuild=n.getBoolean("preventBuild");z.decay=n.getBoolean("decay");for(String s:n.getString("blacklist").split(","))if(!s.trim().isEmpty())z.blacklist.add(s.trim());return z;}
}
