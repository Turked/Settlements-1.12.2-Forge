package com.MechGaming.settlements.client;

import com.MechGaming.settlements.Settlements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public final class ClientEvents {
    public static final KeyBinding PLANNER = new KeyBinding("key.settlements.planner", Keyboard.KEY_K, "key.categories.settlements");
    public static final KeyBinding BOUNDARIES = new KeyBinding("key.settlements.boundaries", Keyboard.KEY_J, "key.categories.settlements");
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ClientState state = ClientState.INSTANCE;
    private int ticks;
    private int suppressedDrop = Integer.MIN_VALUE;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void key(InputEvent.KeyInputEvent event) {
        if (mc.player == null) return;
        int code = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        if (!Keyboard.getEventKeyState()) {
            if (code == suppressedDrop) suppressedDrop = Integer.MIN_VALUE;
            return;
        }
        if (code == suppressedDrop) { clear(mc.gameSettings.keyBindDrop); return; }
        if (Keyboard.isRepeatEvent()) return;
        if (mc.currentScreen != null) return;
        if (BOUNDARIES.isActiveAndMatches(code)) {
            state.boundaries = !state.boundaries;
            if (state.boundaries) state.send("sync", "");
            mc.player.sendStatusMessage(new TextComponentString("Settlement boundaries: " + (state.boundaries ? "shown" : "hidden")), true);
        }
        if (PLANNER.isActiveAndMatches(code)) {
            NBTTagCompound here = state.entryAt(mc.player.getPosition());
            if (!here.hasNoTags() && !here.getBoolean("proposal")) {
                state.planner = false;
                state.activeSettlementId = here.getString("id");
                if (here.getBoolean("owned")) state.activeOwnedId = here.getString("id");
                state.send("sync", "");
                mc.displayGuiScreen(SettlementScreen.home(null, here));
            } else if (state.planner) {
                state.planner = false;
            } else {
                state.planner = true;
                state.slot = 0;
                state.inventoryHash = inventoryHash();
                state.dimension = mc.player.dimension;
                state.world = mc.world;
                state.send("sync", "");
                suppressPlannerActions();
            }
            return;
        }
        if (!state.planner) return;
        if (code == mc.gameSettings.keyBindDrop.getKeyCode()) {
            clear(mc.gameSettings.keyBindDrop);
            suppressedDrop = code;
            state.planner = false;
        } else if (code == Keyboard.KEY_ESCAPE || code == mc.gameSettings.keyBindInventory.getKeyCode()
                || code == mc.gameSettings.keyBindChat.getKeyCode() || code == mc.gameSettings.keyBindCommand.getKeyCode()) {
            state.planner = false;
        } else {
            for (int i = 0; i < mc.gameSettings.keyBindsHotbar.length; i++) {
                KeyBinding binding = mc.gameSettings.keyBindsHotbar[i];
                if (binding.isActiveAndMatches(code)) {
                    if (i < 2) {
                        state.slot = i;
                        clear(binding);
                    } else state.planner = false;
                }
            }
        }
        if (state.planner && code == mc.gameSettings.keyBindSwapHands.getKeyCode()) {
            // Inventory actions release the planner and retain their normal vanilla behavior.
            state.planner = false;
        }
        if (state.planner) suppressPlannerActions();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void mouse(MouseEvent event) {
        if (!state.planner || mc.player == null || mc.currentScreen != null) return;
        if (event.getDwheel() != 0) {
            state.slot = 1 - state.slot;
            event.setCanceled(true);
        }
        if (event.getButton() == 0 || event.getButton() == 1) {
            event.setCanceled(true);
            clear(mc.gameSettings.keyBindAttack);
            clear(mc.gameSettings.keyBindUseItem);
            if (event.getButton() == 1 && event.isButtonstate()) interact();
        } else if (event.getButton() >= 2) {
            // Custom mouse bindings may manipulate inventory; release the planner first.
            state.planner = false;
        }
    }

    private void interact() {
        if (mc.player == null || mc.player.isDead) return;
        RayTraceResult hit = mc.objectMouseOver;
        BlockPos target = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? hit.getBlockPos() : mc.player.getPosition();
        if (state.slot == 0) {
            if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
                mc.player.sendStatusMessage(new TextComponentString("Look at the ground to propose a settlement."), true);
                return;
            }
            target = target.offset(hit.sideHit);
            state.planner = false;
            mc.displayGuiScreen(SettlementScreen.propose(target));
        } else {
            NBTTagCompound entry = state.entryAt(target);
            if (entry.hasNoTags()) entry = state.entryAt(mc.player.getPosition());
            if (entry.hasNoTags()) {
                mc.player.sendStatusMessage(new TextComponentString("Stand in or look at a settlement boundary to support or request entry."), true);
            } else if (entry.getBoolean("proposal")) {
                state.send("support", entry.getString("id"));
            } else {
                state.send("join", entry.getString("id"));
            }
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.player == null || mc.world == null) {
            if (state.world != null) state.clear();
            suppressedDrop = Integer.MIN_VALUE;
            return;
        }
        if (state.world != mc.world || state.dimension != mc.player.dimension) {
            state.planner = false;
            state.highlightUntil = 0;
            state.world = mc.world;
            state.dimension = mc.player.dimension;
            state.send("sync", "");
        }
        if (++ticks % 100 == 0) state.send("sync", "");
        if (state.planner && (mc.player.isDead || mc.player.getHealth() <= 0 || mc.player.isSpectator()
                || mc.currentScreen != null || state.inventoryHash != inventoryHash())) state.planner = false;
        if (state.planner) {
            suppressPlannerActions();
        }
    }

    @SubscribeEvent
    public void gui(GuiOpenEvent event) { if (event.getGui() != null) state.planner = false; }

    @SubscribeEvent
    public void developerAirClick(PlayerInteractEvent.LeftClickEmpty event) {
        if (state.planner || mc.player == null) return;
        ItemStack held = mc.player.getHeldItemMainhand();
        Item tool = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Settlements.MODID, "dev_tool"));
        if (held.getItem() != tool) return;
        int current = held.hasTagCompound() ? held.getTagCompound().getInteger("zoneMode") : 0;
        state.send("zone_mode", mc.player.getPosition(), mc.player.isSneaking() ? 2 : current == 0 ? 1 : 0, "", null);
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { mc.addScheduledTask(state::clear); }

    private int inventoryHash() {
        int result = mc.player.inventory.currentItem;
        for (int i = 0; i < mc.player.inventory.getSizeInventory(); i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            result = 31 * result + (stack.isEmpty() ? 0 : Item.getIdFromItem(stack.getItem()));
            result = 31 * result + stack.getCount();
            result = 31 * result + stack.getMetadata();
            result = 31 * result + (stack.hasTagCompound() ? stack.getTagCompound().hashCode() : 0);
        }
        return result;
    }

    private static void clear(KeyBinding binding) {
        KeyBinding.setKeyBindState(binding.getKeyCode(), false);
        while (binding.isPressed()) { /* Drain vanilla's queued action as well as its held state. */ }
    }

    private void suppressPlannerActions() {
        clear(mc.gameSettings.keyBindAttack);
        clear(mc.gameSettings.keyBindUseItem);
        clear(mc.gameSettings.keyBindPickBlock);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hideVanilla(RenderGameOverlayEvent.Pre event) {
        if (!state.planner) return;
        switch (event.getType()) {
            case HOTBAR: case HEALTH: case ARMOR: case FOOD: case EXPERIENCE: case HEALTHMOUNT: case JUMPBAR:
                event.setCanceled(true);
                break;
            default: break;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void plannerHand(RenderSpecificHandEvent event) {
        if (!state.planner || mc.player == null) return;
        event.setCanceled(true);
        if (event.getHand() != EnumHand.MAIN_HAND) return;
        Item flag = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Settlements.MODID, "settlement_flag"));
        Item placeholder = state.slot == 0 ? flag == null ? Items.PAPER : flag : Items.LEATHER;
        // This overload renders a supplied stack directly; it does not re-fire the hand event
        // or change the inventory/equipped stack used by the game and other mods.
        mc.getItemRenderer().renderItemInFirstPerson(mc.player, event.getPartialTicks(), event.getInterpolatedPitch(),
                EnumHand.MAIN_HAND, 0, new ItemStack(placeholder), 0);
    }

    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || mc.player == null || mc.gameSettings.hideGUI) return;
        ScaledResolution res = event.getResolution();
        if (state.boundaries && !state.planner) {
            String line = "Settlements: green allowed | red restricted | gray claims | gold proposals";
            mc.fontRenderer.drawStringWithShadow(line, 5, 5, 0xDDDDDD);
        }
        if (!state.planner) return;
        int x = res.getScaledWidth() / 2;
        int y = res.getScaledHeight() - 29;
        Gui.drawRect(x - 30, y - 3, x + 30, y + 23, 0xCC101418);
        Gui.drawRect(x - 28 + state.slot * 28, y - 2, x - 3 + state.slot * 28, y + 22, 0xCCB4A66B);
        Item flag = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Settlements.MODID, "settlement_flag"));
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(flag == null ? Items.PAPER : flag), x - 24, y + 2);
        // Vanilla glove-like leather is a replaceable visual placeholder for the raised hand.
        mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(Items.LEATHER), x + 4, y + 2);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        drawCentered(state.slot == 0 ? "1: Settlement flag" : "2: Raise hand", x, y - 15, 0xFFFFFF);
        NBTTagCompound entry = state.entryAt(mc.player.getPosition());
        if (state.slot == 0) {
            int flags = 0;
            for (int i = 0; i < mc.player.inventory.getSizeInventory(); i++) {
                ItemStack stack = mc.player.inventory.getStackInSlot(i);
                if (stack.getItem() == flag) flags += stack.getCount();
            }
            int required = state.snapshot().getInteger("requiredFlags");
            int supporters = entry.getBoolean("proposal") ? entry.getInteger("players") : 1;
            int people = state.snapshot().getInteger("requiredPlayers");
            drawCentered("Flags " + flags + "/" + required + "    Willing players " + supporters + "/" + people + " minimum", x, y - 29,
                    flags >= required && supporters >= people ? 0x85E68A : 0xF3CD83);
        } else if (!entry.hasNoTags()) {
            drawCentered(entry.getString("name") + " - " + (entry.getBoolean("proposal") ? "proposed: " + entry.getInteger("players") + " willing"
                    : entry.getBoolean("accepting") ? "accepting requests" : "closed to requests"), x, y - 29,
                    entry.getBoolean("member") || entry.getBoolean("owned") ? 0x85E68A : 0xDDDDDD);
        } else drawCentered("Look at a boundary or stand inside it to support / request entry", x, y - 29, 0xDDDDDD);
        drawCentered("Right click: " + (state.slot == 0 ? "propose" : "support / join") + "  |  1 / 2 or wheel: slot  |  "
                + PLANNER.getDisplayName() + (entry.hasNoTags() || entry.getBoolean("proposal") ? ": exit" : ": settlement menu"), x, y - 42, 0xBBBBBB);
        if (state.boundaries) drawCentered("Green allowed / red restricted / gray claims / gold proposals; unzoned land "
                + (state.snapshot().getBoolean("defaultAllowed") ? "allowed" : "restricted"), x, y - 55, 0xBBBBBB);
        GlStateManager.enableDepth();
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawCentered(String text, int x, int y, int color) {
        mc.fontRenderer.drawStringWithShadow(text, x - mc.fontRenderer.getStringWidth(text) / 2F, y, color);
    }
}
