package com.mechgaming.settlements.registry;
import com.mechgaming.settlements.block.SettlementFlagBlock;
import com.mechgaming.settlements.item.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class RegistryHandler {
    public static final Block SETTLEMENT_FLAG=new SettlementFlagBlock();
    public static final Item FLAG_ITEM=new FlagItem(SETTLEMENT_FLAG),DEV_TOOL=new DevToolItem();
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event){event.getRegistry().register(SETTLEMENT_FLAG);}
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event){event.getRegistry().registerAll(FLAG_ITEM,DEV_TOOL);}
}
