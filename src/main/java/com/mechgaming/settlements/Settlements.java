package com.mechgaming.settlements;

import com.mechgaming.settlements.command.SettlementCommand;
import com.mechgaming.settlements.common.CommonProxy;
import com.mechgaming.settlements.common.CoreEvents;
import com.mechgaming.settlements.common.SettlementService;
import com.mechgaming.settlements.config.SettlementConfig;
import com.mechgaming.settlements.features.FeatureEvents;
import com.mechgaming.settlements.features.FeatureService;
import com.mechgaming.settlements.features.EntitySettler;
import com.mechgaming.settlements.network.ClientAction;
import com.mechgaming.settlements.network.StateMessage;
import com.mechgaming.settlements.registry.RegistryHandler;
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
    @SidedProxy(clientSide = "com.mechgaming.settlements.client.ClientProxy",
                serverSide = "com.mechgaming.settlements.common.CommonProxy")
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
