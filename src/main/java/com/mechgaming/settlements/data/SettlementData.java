package com.mechgaming.settlements.data;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import java.util.*;

public final class SettlementData extends WorldSavedData {
    public static final String KEY="settlements_world";
    public final List<Settlement> settlements=new ArrayList<>();
    public final List<Proposal> proposals=new ArrayList<>();
    public final List<Zone> zones=new ArrayList<>();
    /** Deferred decay entries, persisted so a restart cannot bypass zone rules. */
    public final List<NBTTagCompound> decay=new ArrayList<>();
    public SettlementData(){super(KEY);}
    public SettlementData(String name){super(name);}
    public static SettlementData get(World world){
        if(world.isRemote)throw new IllegalStateException("Settlement data is server authoritative");
        World overworld=world.getMinecraftServer().getWorld(0);MapStorage storage=overworld.getMapStorage();
        SettlementData d=(SettlementData)storage.getOrLoadData(SettlementData.class,KEY);
        if(d==null){d=new SettlementData();storage.setData(KEY,d);d.markDirty();}return d;
    }
    @Override public void readFromNBT(NBTTagCompound n){
        settlements.clear();proposals.clear();zones.clear();decay.clear();
        NBTTagList a=n.getTagList("settlements",10);for(int i=0;i<a.tagCount()&&i<2048;i++)try{settlements.add(Settlement.read(a.getCompoundTagAt(i)));}catch(IllegalArgumentException ex){warn("settlement",i,ex);}
        a=n.getTagList("proposals",10);for(int i=0;i<a.tagCount()&&i<2048;i++)try{proposals.add(Proposal.read(a.getCompoundTagAt(i)));}catch(IllegalArgumentException ex){warn("proposal",i,ex);}
        a=n.getTagList("zones",10);for(int i=0;i<a.tagCount()&&i<2048;i++)try{zones.add(Zone.read(a.getCompoundTagAt(i)));}catch(IllegalArgumentException ex){warn("zone",i,ex);}
        a=n.getTagList("decay",10);for(int i=0;i<a.tagCount()&&i<8192;i++)decay.add(a.getCompoundTagAt(i));
    }
    private static void warn(String kind,int index,Exception error){org.apache.logging.log4j.LogManager.getLogger("Settlements").warn("Skipped malformed {} record {}: {}",kind,index,error.getMessage());}
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){
        NBTTagList a=new NBTTagList();settlements.forEach(s->a.appendTag(s.write()));n.setTag("settlements",a);
        NBTTagList b=new NBTTagList();proposals.forEach(s->b.appendTag(s.write()));n.setTag("proposals",b);
        NBTTagList c=new NBTTagList();zones.forEach(s->c.appendTag(s.write()));n.setTag("zones",c);NBTTagList e=new NBTTagList();decay.forEach(e::appendTag);n.setTag("decay",e);n.setInteger("version",1);return n;
    }
}
