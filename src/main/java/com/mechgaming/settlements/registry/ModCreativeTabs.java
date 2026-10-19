package com.mechgaming.settlements.registry;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public final class ModCreativeTabs {
    public static final CreativeTabs SETTLEMENTS = new CreativeTabs("settlements") {
        @Override public ItemStack getTabIconItem() { return new ItemStack(RegistryHandler.FLAG_ITEM); }
    };
    private ModCreativeTabs() { }
}
