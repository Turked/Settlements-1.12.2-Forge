package com.mechgaming.settlements.features;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** World-local feature records. Never stores container contents or player inventories. */
public final class FeatureWorldData extends WorldSavedData {
    private static final String KEY = "settlements_features";
    public NBTTagList rooms = new NBTTagList();
    public NBTTagList blueprints = new NBTTagList();
    public NBTTagList raids = new NBTTagList();
    public NBTTagList wars = new NBTTagList();
    public NBTTagList restorations = new NBTTagList();
    public NBTTagList payouts = new NBTTagList();
    public NBTTagList bedRestorations = new NBTTagList();

    public FeatureWorldData() { super(KEY); }
    public FeatureWorldData(String name) { super(name); }

    public static FeatureWorldData get(World world) {
        World primary = world.getMinecraftServer().getWorld(0);
        MapStorage storage = primary.getMapStorage();
        FeatureWorldData data = (FeatureWorldData) storage.getOrLoadData(FeatureWorldData.class, KEY);
        if (data == null) {
            data = new FeatureWorldData();
            storage.setData(KEY, data);
            data.markDirty();
        }
        return data;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        rooms = tag.getTagList("rooms", 10);
        blueprints = tag.getTagList("blueprints", 10);
        raids = tag.getTagList("raids", 10);
        wars = tag.getTagList("wars", 10);
        restorations = tag.getTagList("restorations", 10);
        payouts = tag.getTagList("payouts", 10);
        bedRestorations = tag.getTagList("bedRestorations", 10);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setInteger("version", 1);
        tag.setTag("rooms", rooms);
        tag.setTag("blueprints", blueprints);
        tag.setTag("raids", raids);
        tag.setTag("wars", wars);
        tag.setTag("restorations", restorations);
        tag.setTag("payouts", payouts);
        tag.setTag("bedRestorations", bedRestorations);
        return tag;
    }
}
