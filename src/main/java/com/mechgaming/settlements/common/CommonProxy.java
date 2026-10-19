package com.mechgaming.settlements.common;

import net.minecraft.nbt.NBTTagCompound;

/** Dedicated-server-safe entry points. Client implementations live behind @SidedProxy. */
public class CommonProxy {
    public void preInit() { }
    public void init() { }
    public void acceptState(NBTTagCompound data) { }
    public void openConfig() { }
    public void openZone() { }
}
