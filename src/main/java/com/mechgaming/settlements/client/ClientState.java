package com.mechgaming.settlements.client;

import com.mechgaming.settlements.Settlements;
import com.mechgaming.settlements.network.ClientAction;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The server's read-only view. Planner slots never replace the player's inventory. */
@SideOnly(Side.CLIENT)
public final class ClientState {
    public static final ClientState INSTANCE = new ClientState();
    private NBTTagCompound state = new NBTTagCompound();
    public boolean planner;
    public boolean boundaries;
    public int slot;
    public String activeOwnedId = "";
    public String activeSettlementId = "";
    public long highlightUntil;
    int inventoryHash;
    int dimension;
    Object world;

    private ClientState() { }

    public void accept(NBTTagCompound value) {
        state = value.copy();
        Minecraft mc = Minecraft.getMinecraft();
        String open = state.getString("openGui");
        if (state.hasKey("selectedSettlement", 8)) {
            activeSettlementId = state.getString("selectedSettlement");
            NBTTagCompound selected = byId(entries(), activeSettlementId);
            if (selected.getBoolean("owned")) activeOwnedId = activeSettlementId;
        }
        if ("settlement".equals(open) || "context".equals(open)) {
            planner = false;
            mc.displayGuiScreen(SettlementScreen.home(null, state.hasKey("selectedSettlement", 8)
                    ? byId(entries(), activeSettlementId) : new NBTTagCompound()));
        } else if ("config".equals(open) && operator()) {
            planner = false;
            mc.displayGuiScreen(SettlementScreen.config(null));
        } else if ("zone".equals(open) && operator()) {
            planner = false;
            NBTTagCompound zone = byId(zones(), state.getString("selectedZone"));
            if (!zone.hasNoTags()) mc.displayGuiScreen(SettlementScreen.zone(null, zone));
        }
    }

    public NBTTagCompound snapshot() { return state; }
    public NBTTagCompound config() { return state.getCompoundTag("config"); }
    public NBTTagCompound features() { return state.getCompoundTag("features"); }
    public boolean operator() { return state.getBoolean("operator"); }
    public NBTTagList entries() { return state.getTagList("entries", 10); }
    public NBTTagList zones() { return state.getTagList("zones", 10); }

    public NBTTagCompound owned() {
        NBTTagCompound active = byId(entries(), activeOwnedId);
        if (!active.hasNoTags() && active.getBoolean("owned") && !active.getBoolean("proposal")) return active;
        return byId(entries(), state.getString("owned"));
    }

    public NBTTagCompound affiliation() {
        NBTTagCompound selected = byId(entries(), activeSettlementId);
        if (!selected.getBoolean("proposal") && (selected.getBoolean("owned") || selected.getBoolean("member"))) return selected;
        NBTTagCompound owned = owned();
        if (!owned.hasNoTags()) return owned;
        NBTTagList entries = entries();
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            if (entry.getBoolean("owned")) return entry;
        }
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            if (entry.getBoolean("member") || entry.getBoolean("owned")) return entry;
        }
        return new NBTTagCompound();
    }

    public NBTTagCompound entryAt(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return new NBTTagCompound();
        NBTTagList entries = entries();
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound e = entries.getCompoundTagAt(i);
            if (e.getInteger("dimension") == mc.player.dimension
                    && Math.abs((long) pos.getX() - e.getInteger("x")) <= e.getInteger("radius")
                    && Math.abs((long) pos.getZ() - e.getInteger("z")) <= e.getInteger("radius")) return e;
        }
        return new NBTTagCompound();
    }

    public NBTTagCompound zoneAt(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return new NBTTagCompound();
        NBTTagList zones = zones();
        // Newer zones take precedence, matching the server's editing order.
        for (int i = zones.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound z = zones.getCompoundTagAt(i);
            if (z.getInteger("dimension") == mc.player.dimension
                    && pos.getX() >= z.getInteger("minX") && pos.getX() <= z.getInteger("maxX")
                    && pos.getZ() >= z.getInteger("minZ") && pos.getZ() <= z.getInteger("maxZ")) return z;
        }
        return new NBTTagCompound();
    }

    public static NBTTagCompound byId(NBTTagList list, String id) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.getString("id").equals(id)) return entry;
        }
        return new NBTTagCompound();
    }

    public void send(String action, BlockPos pos, int value, String text, NBTTagCompound payload) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() != null) {
            NBTTagCompound parameters = payload == null ? new NBTTagCompound() : payload.copy();
            if (action.startsWith("feature_") || "sync".equals(action) || "accept".equals(action) || "deny".equals(action)
                    || "accepting".equals(action) || "disband".equals(action)) {
                NBTTagCompound selected = action.startsWith("feature_") || "sync".equals(action) ? affiliation() : owned();
                if (!selected.hasNoTags() && !selected.getBoolean("proposal") && !parameters.hasKey("settlement", 8)) parameters.setString("settlement", selected.getString("id"));
            }
            if ("join".equals(action) && mc.world != null) highlightUntil = mc.world.getTotalWorldTime() + 30;
            Settlements.NETWORK.sendToServer(new ClientAction(action, pos == null ? BlockPos.ORIGIN : pos,
                    value, text == null ? "" : text, parameters));
        }
    }

    public void send(String action, String text) { send(action, BlockPos.ORIGIN, 0, text, null); }

    public void clear() {
        state = new NBTTagCompound();
        planner = false;
        activeOwnedId = "";
        activeSettlementId = "";
        highlightUntil = 0;
        world = null;
    }
}
