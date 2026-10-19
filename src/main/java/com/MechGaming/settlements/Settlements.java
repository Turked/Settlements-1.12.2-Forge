package com.MechGaming.settlements;

import com.MechGaming.settlements.command.SettlementCommand;
import com.MechGaming.settlements.common.CommonProxy;
import com.MechGaming.settlements.common.CoreEvents;
import com.MechGaming.settlements.common.SettlementService;
import com.MechGaming.settlements.config.SettlementConfig;
import com.MechGaming.settlements.features.FeatureEvents;
import com.MechGaming.settlements.features.FeatureService;
import com.MechGaming.settlements.features.EntitySettler;
import com.MechGaming.settlements.network.ClientAction;
import com.MechGaming.settlements.network.StateMessage;
import com.MechGaming.settlements.registry.RegistryHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Settlements.MODID, name = Settlements.NAME, version = Settlements.VERSION,
     acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:forge@[14.23.5.2860,)")
public class Settlements {
    public static final String MODID = "settlements";
    public static final String NAME = "Settlements";
    public static final String VERSION = "0.1.0";
    public static final String AUTHOR = "MechGaming";
    public static Logger LOGGER = LogManager.getLogger(NAME);
    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @Mod.Instance(MODID) public static Settlements INSTANCE;
    @SidedProxy(clientSide = "com.MechGaming.settlements.client.ClientProxy",
                serverSide = "com.MechGaming.settlements.common.CommonProxy")
    public static CommonProxy PROXY;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        SettlementConfig.load(event.getSuggestedConfigurationFile());
        FeatureService.load(event.getModConfigurationDirectory());
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "settler"), EntitySettler.class,
                "settler", 0, INSTANCE, 64, 3, true);
        NETWORK.registerMessage(ClientAction.Handler.class, ClientAction.class, 0, Side.SERVER);
        NETWORK.registerMessage(StateMessage.Handler.class, StateMessage.class, 1, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(RegistryHandler.class);
        MinecraftForge.EVENT_BUS.register(new CoreEvents());
        MinecraftForge.EVENT_BUS.register(new FeatureEvents());
        PROXY.preInit();
    }

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        PROXY.init();
        LOGGER.info("Settlements {} by {}: server-authoritative claims; optional systems controlled by settlements.cfg", VERSION, AUTHOR);
    }

    @Mod.EventHandler public void postInit(FMLPostInitializationEvent event) {
        FeatureService.postInit();
    }

    @Mod.EventHandler public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new SettlementCommand());
    }

    public static void sendState(EntityPlayerMP player) {
        NETWORK.sendTo(new StateMessage(SettlementService.snapshot(player)), player);
    }
}
