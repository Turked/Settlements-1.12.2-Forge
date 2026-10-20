package com.MechGaming.settlements.client;

import com.MechGaming.settlements.Settlements;
import com.MechGaming.settlements.common.CommonProxy;
import com.MechGaming.settlements.features.EntitySettler;
import com.MechGaming.settlements.client.texture.FlagFabricSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.RenderVillager;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntitySettler.class, RenderVillager::new);
        ClientRegistry.registerKeyBinding(ClientEvents.PLANNER);
        ClientRegistry.registerKeyBinding(ClientEvents.BOUNDARIES);
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
        MinecraftForge.EVENT_BUS.register(new BoundaryRenderer());
    }

    @Override
    public void init() { }

    @Override
    public void acceptState(NBTTagCompound state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.isCallingFromMinecraftThread()) ClientState.INSTANCE.accept(state);
        else mc.addScheduledTask(() -> ClientState.INSTANCE.accept(state));
    }

    @Override
    public void openConfig() {
        if (!ClientState.INSTANCE.operator()) ClientState.INSTANCE.send("sync", "");
        ClientState.INSTANCE.planner = false;
        Minecraft.getMinecraft().displayGuiScreen(SettlementScreen.config(null));
    }

    @Override
    public void openZone() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        ClientState.INSTANCE.planner = false;
        NBTTagCompound zone = ClientState.INSTANCE.zoneAt(mc.player.getPosition());
        mc.displayGuiScreen(zone.hasNoTags() ? SettlementScreen.zones(null) : SettlementScreen.zone(null, zone));
    }

    @Mod.EventBusSubscriber(modid = Settlements.MODID, value = Side.CLIENT)
    public static final class Models {
        @SubscribeEvent
        public static void registerTextures(TextureStitchEvent.Pre event) {
            if (event.getMap() != Minecraft.getMinecraft().getTextureMapBlocks()) return;
            event.getMap().setTextureEntry(new FlagFabricSprite());
        }

        @SubscribeEvent
        public static void register(ModelRegistryEvent event) {
            registerItem("settlement_flag");
            registerItem("dev_tool");
        }

        private static void registerItem(String path) {
            ResourceLocation id = new ResourceLocation(Settlements.MODID, path);
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item != null) ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(id, "inventory"));
        }
    }
}
