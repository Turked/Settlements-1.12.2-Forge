package com.mechgaming.settlements.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small vanilla GUI with server-authoritative actions; no inventory/container is opened. */
@SideOnly(Side.CLIENT)
public final class SettlementScreen extends GuiScreen {
    private enum Page { HOME, INBOX, PROPOSE, CONFIRM, LEAVE, CONFIG, ZONES, ZONE, FEATURES, BLUEPRINT, WAR, WAR_OFFER, ROOM }
    private static final String[] CONFIG_KEYS = { "radius", "requiredFlags", "requiredPlayers", "defaultAllowed", "protectClaims",
            "preventBuildOutsideZones", "decayOutsideZones", "advancedZoneSettings", "mobsAttackSettlement", "requireMobZone",
            "raidsWithoutRuin", "warsWithoutRuin", "spawnMinDistance", "spawnMaxDistance", "mobDestruction", "mobPlacement", "autoRepair",
            "enableNpcs", "maxNpcs", "maxBlueprintVolume", "raidWaves", "raidMobsPerWave", "raidRewardEmeralds",
            "firstSettlementDistance", "decaySeconds", "blacklist", "warGhostProtection" };
    private static final String[] CONFIG_LABELS = { "Settlement radius", "Flags required", "Willing players required", "Unzoned land allowed",
            "Protect member claims", "Prevent building in restricted land", "Decay in restricted land", "Advanced per-zone settings",
            "Mobs attack settlements", "Require a mob spawning zone", "Raids without ruin", "Wars without ruin", "Minimum mob spawn distance",
            "Maximum mob spawn distance", "Raid mobs may destroy blocks", "Raid mobs may place blocks", "Repair using inventory materials",
            "Enable room-based NPCs", "Maximum NPCs per settlement", "Maximum blueprint volume", "Raid waves", "Mobs per raid wave", "Raid reward emeralds",
            "Distance from first settlement (0 off)", "Restricted block decay delay (seconds)", "Restricted block blacklist (IDs)",
            "Protect opted-out members in their war claim" };
    private final ClientState state = ClientState.INSTANCE;
    private final GuiScreen parent;
    private final Page page;
    private final NBTTagCompound selected;
    private final BlockPos position;
    private final Map<Integer, Runnable> actions = new LinkedHashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Boolean> toggles = new LinkedHashMap<>();
    private final Map<String, GuiTextField> fields = new LinkedHashMap<>();
    private final List<String> lines = new ArrayList<>();
    private int index;
    private int left;
    private int top;
    private int rows;
    private int nextId;
    private float panelScale;
    private String status = "";
    private NBTTagCompound lastSnapshot;

    private SettlementScreen(GuiScreen parent, Page page, NBTTagCompound selected, BlockPos position) {
        this.parent = parent;
        this.page = page;
        this.selected = selected == null ? new NBTTagCompound() : selected.copy();
        this.position = position == null ? BlockPos.ORIGIN : position;
        if (page == Page.CONFIG) {
            NBTTagCompound config = state.config();
            for (String key : CONFIG_KEYS) {
                if (isText(key)) values.put(key, config.getString(key));
                else if (isNumber(key)) values.put(key, Integer.toString(config.getInteger(key)));
                else toggles.put(key, config.getBoolean(key));
            }
        } else if (page == Page.ZONE) {
            for (String key : new String[] { "allowed", "preventBuild", "decay" }) toggles.put(key, this.selected.getBoolean(key));
            values.put("blacklist", this.selected.getString("blacklist"));
        }
    }

    public static SettlementScreen home(GuiScreen parent) { return new SettlementScreen(parent, Page.HOME, null, null); }
    public static SettlementScreen home(GuiScreen parent, NBTTagCompound context) { return new SettlementScreen(parent, Page.HOME, context, null); }
    public static SettlementScreen config(GuiScreen parent) { return new SettlementScreen(parent, Page.CONFIG, null, null); }
    public static SettlementScreen zones(GuiScreen parent) { return new SettlementScreen(parent, Page.ZONES, null, null); }
    public static SettlementScreen zone(GuiScreen parent, NBTTagCompound zone) { return new SettlementScreen(parent, Page.ZONE, zone, null); }
    public static SettlementScreen propose(BlockPos pos) { return new SettlementScreen(null, Page.PROPOSE, null, pos); }

    @Override
    public void initGui() {
        rememberFields();
        buttonList.clear();
        actions.clear();
        fields.clear();
        lines.clear();
        nextId = 1;
        panelScale = Math.min(1F, (width - 20) / 410F);
        left = width / 2 - Math.round(205 * panelScale);
        top = 43;
        rows = Math.max(2, Math.min(7, (height - 135) / 25));
        lastSnapshot = state.snapshot();
        Keyboard.enableRepeatEvents(true);
        button(left, height - 29, 95, "Back", () -> mc.displayGuiScreen(parent));
        switch (page) {
            case HOME: homePage(); break;
            case INBOX: inboxPage(); break;
            case PROPOSE: proposePage(); break;
            case CONFIRM: confirmPage(); break;
            case LEAVE: leavePage(); break;
            case CONFIG: configPage(); break;
            case ZONES: zonesPage(); break;
            case ZONE: zonePage(); break;
            case FEATURES: featuresPage(); break;
            case BLUEPRINT: blueprintPage(); break;
            case WAR: warPage(); break;
            case WAR_OFFER: warOfferPage(); break;
            case ROOM: roomPage(); break;
            default: break;
        }
    }

    private void homePage() {
        NBTTagCompound affiliation = selected.hasNoTags() ? state.affiliation() : ClientState.byId(state.entries(), selected.getString("id"));
        NBTTagCompound owned = affiliation.getBoolean("owned") && !affiliation.getBoolean("proposal") ? affiliation : new NBTTagCompound();
        boolean visitor = !affiliation.hasNoTags() && !affiliation.getBoolean("proposal")
                && !affiliation.getBoolean("member") && !affiliation.getBoolean("owned");
        lines.add(affiliation.hasNoTags() ? selected.hasNoTags() ? "You do not belong to a settlement yet." : "This settlement is no longer available."
                : (affiliation.getBoolean("proposal") ? "Your proposal: " : visitor ? "Settlement: " : "Your settlement: ") + affiliation.getString("name"));
        if (status.isEmpty()) status = visitor ? "Owner: " + affiliation.getString("ownerName")
                : "Outside a settlement, " + ClientEvents.PLANNER.getDisplayName() + " equips the flag planner.";
        button(left, top + 30, 200, "Boundaries: " + (state.boundaries ? "shown" : "hidden"), () -> {
            state.boundaries = !state.boundaries;
            initGui();
        });
        button(left + 210, top + 30, 200, "Refresh from server", () -> state.send("sync", ""));
        if (!owned.hasNoTags() && !owned.getBoolean("proposal")) {
            button(left, top + 56, 200, "Inbox (" + owned.getTagList("requests", 10).tagCount() + ")",
                    () -> mc.displayGuiScreen(new SettlementScreen(this, Page.INBOX, owned, null)));
            button(left + 210, top + 56, 200, "Accepting: " + (owned.getBoolean("accepting") ? "yes" : "no"), () -> {
                state.send("accepting", BlockPos.ORIGIN, owned.getBoolean("accepting") ? 0 : 1, "", settlementPayload(owned));
            });
            button(left, top + 82, 200, "Disband settlement...", () -> mc.displayGuiScreen(new SettlementScreen(this, Page.CONFIRM, owned, null)));
            button(left + 210, top + 82, 200, "Optional settlement systems", () -> mc.displayGuiScreen(new SettlementScreen(this, Page.FEATURES, owned, null)));
        } else if (!affiliation.hasNoTags() && !affiliation.getBoolean("proposal") && !visitor) {
            button(left, top + 56, 200, "Leave settlement...", () -> mc.displayGuiScreen(new SettlementScreen(this, Page.LEAVE, affiliation, null)));
            button(left + 210, top + 56, 200, "Optional settlement systems", () -> mc.displayGuiScreen(new SettlementScreen(this, Page.FEATURES, affiliation, null)));
        } else if (affiliation.getBoolean("proposal") && affiliation.getBoolean("owned")) {
            button(left, top + 56, 410, "Cancel my proposal", () -> state.send("cancel", ""));
        } else if (visitor) {
            NBTTagCompound membership = state.affiliation();
            boolean alreadyMember = !membership.hasNoTags() && !membership.getBoolean("proposal");
            String label = alreadyMember ? "You already belong to another settlement"
                    : affiliation.getBoolean("accepting") ? "Request to join this settlement" : "Closed to membership requests";
            button(left, top + 56, 410, label, () -> state.send("join", affiliation.getString("id")))
                    .enabled = !alreadyMember && affiliation.getBoolean("accepting");
        }
        if (state.operator()) {
            button(left, top + 109, 200, "Server settings", () -> mc.displayGuiScreen(config(this)));
            button(left + 210, top + 109, 200, "Edit zones", () -> mc.displayGuiScreen(zones(this)));
        }
        int y = top + 146;
        NBTTagList entries = state.entries();
        int available = Math.max(0, (height - y - 65) / 24);
        for (int i = index; i < entries.tagCount() && i < index + available; i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            if (mc.player != null && entry.getInteger("dimension") != mc.player.dimension) continue;
            String kind = entry.getBoolean("proposal") ? "Proposed" : entry.getBoolean("accepting") ? "Open" : "Closed";
            button(left, y, 410, entry.getString("name") + " | " + kind + " | " + entry.getInteger("players") + " players", () -> {
                if (!entry.getBoolean("proposal")) {
                    state.activeSettlementId = entry.getString("id");
                    if (entry.getBoolean("owned")) state.activeOwnedId = entry.getString("id");
                    state.send("sync", "");
                    mc.displayGuiScreen(home(parent, entry));
                } else if (entry.getBoolean("proposal")) state.send("support", entry.getString("id"));
            });
            y += 24;
        }
        if (available > 0) pagination(entries.tagCount(), available);
    }

    private void inboxPage() {
        NBTTagCompound owned = ClientState.byId(state.entries(), selected.getString("id"));
        NBTTagList requests = owned.getTagList("requests", 10);
        if (requests.tagCount() == 0) lines.add("No pending requests.");
        for (int i = index; i < requests.tagCount() && i < index + rows; i++) {
            NBTTagCompound request = requests.getCompoundTagAt(i);
            int y = top + 14 + (i - index) * 25;
            lines.add(request.getString("name"));
            button(left + 200, y, 100, "Accept", () -> state.send("accept", BlockPos.ORIGIN, 0, request.getString("id"), settlementPayload(owned)));
            button(left + 305, y, 100, "Deny", () -> state.send("deny", BlockPos.ORIGIN, 0, request.getString("id"), settlementPayload(owned)));
        }
        pagination(requests.tagCount(), rows);
    }

    private void proposePage() {
        lines.add("Propose a settlement at " + position.getX() + ", " + position.getY() + ", " + position.getZ());
        lines.add("The server checks zones, overlap, flags, and willing players.");
        if (!values.containsKey("name")) values.put("name", mc.player.getName() + "'s Settlement");
        field("name", left, top + 36, 410, 48);
        fields.get("name").setFocused(true);
        button(left + 110, top + 65, 190, "Create proposal", () -> {
            rememberFields();
            String name = values.get("name").trim();
            if (name.isEmpty()) { status = "Enter a settlement name."; return; }
            state.send("propose", position, 0, name, null);
            mc.displayGuiScreen(null);
        });
    }

    private void confirmPage() {
        lines.add("Disband " + selected.getString("name") + "?");
        lines.add("Its claim and membership will be removed. Buildings stay in the world.");
        button(left + 110, top + 46, 190, "Confirm disband", () -> {
            NBTTagCompound payload = settlementPayload(selected);
            payload.setBoolean("confirm", true);
            state.send("disband", BlockPos.ORIGIN, 0, selected.getString("id"), payload);
            mc.displayGuiScreen(home(null));
        });
    }

    private void leavePage() {
        lines.add("Leave " + selected.getString("name") + "?");
        lines.add("You will need a new accepted request to join again.");
        button(left + 110, top + 46, 190, "Confirm leave", () -> {
            state.send("leave", "");
            mc.displayGuiScreen(home(null));
        });
    }

    private void configPage() {
        if (!state.operator()) { lines.add("Operator permission is required; wait for server sync or ask an operator."); return; }
        for (int i = index; i < CONFIG_KEYS.length && i < index + rows; i++) {
            String key = CONFIG_KEYS[i];
            String label = CONFIG_LABELS[i];
            int y = top + 12 + (i - index) * 25;
            if (isNumber(key) || isText(key)) {
                lines.add(label);
                field(key, left + 280, y, 125, isText(key) ? 2048 : 8);
            } else {
                lines.add(label);
                button(left + 280, y - 1, 125, toggles.get(key) ? "Enabled" : "Disabled", () -> {
                    toggles.put(key, !toggles.get(key));
                    initGui();
                });
            }
        }
        button(left + 315, height - 29, 95, "Save & close", () -> save(false));
        pagination(CONFIG_KEYS.length, rows);
    }

    private void zonesPage() {
        if (!state.operator()) { lines.add("Operator permission is required."); return; }
        NBTTagList zones = state.zones();
        if (zones.tagCount() == 0) {
            lines.add("No zones. Use the developer tool to select two corners.");
            lines.add("Sneak-use the tool to open settings.");
        }
        for (int i = index; i < zones.tagCount() && i < index + rows; i++) {
            NBTTagCompound zone = zones.getCompoundTagAt(i);
            String label = zone.getString("name") + " | " + zone.getString("type") + " | " + (zone.getBoolean("allowed") ? "allowed" : "restricted");
            button(left, top + 12 + (i - index) * 25, 410, label, () -> mc.displayGuiScreen(zone(this, zone)));
        }
        pagination(zones.tagCount(), rows);
    }

    private void zonePage() {
        if (!state.operator()) { lines.add("Operator permission is required."); return; }
        lines.add(selected.getString("name") + " | " + selected.getString("type"));
        lines.add("X " + selected.getInteger("minX") + " to " + selected.getInteger("maxX")
                + " / Z " + selected.getInteger("minZ") + " to " + selected.getInteger("maxZ"));
        String[] keys = { "allowed", "preventBuild", "decay" };
        String[] labels = { "Settlement placement allowed", "Prevent outsider building", "Enable block decay" };
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            button(left, top + 34 + i * 25, 410, labels[i] + ": " + (toggles.get(key) ? "yes" : "no"), () -> {
                toggles.put(key, !toggles.get(key));
                initGui();
            });
        }
        field("blacklist", left, top + 115, 410, 512);
        button(left + 315, height - 29, 95, "Save & close", () -> save(true));
        button(left + 115, height - 29, 190, "Delete zone...", () -> {
            if ("Click delete again to confirm.".equals(status)) {
                state.send("zone_delete", selected.getString("id"));
                mc.displayGuiScreen(parent);
            } else status = "Click delete again to confirm.";
        });
    }

    private void featuresPage() {
        lines.add("Optional systems follow the server's enabled settings.");
        button(left, top + 34, 200, "Blueprints and repair", () -> mc.displayGuiScreen(new SettlementScreen(this, Page.BLUEPRINT, selected, null)));
        button(left + 210, top + 34, 200, "NPC rooms", () -> mc.displayGuiScreen(new SettlementScreen(this, Page.ROOM, selected, null)));
        button(left, top + 60, 200, "Start a defense raid", () -> feature("feature_raid_start"));
        button(left + 210, top + 60, 200, "Stop the current raid", () -> feature("feature_raid_stop"));
        button(left, top + 86, 410, "Wars without ruin", () -> mc.displayGuiScreen(new SettlementScreen(this, Page.WAR, selected, null)));
        NBTTagCompound raid = state.features().getCompoundTag("raid");
        if (!raid.hasNoTags()) status = "Raid wave " + raid.getInteger("wave") + "/" + raid.getInteger("waves") + ": " + raid.getInteger("remaining") + " enemies.";
    }

    private void blueprintPage() {
        lines.add("Save a blueprint around your current position (radius 1-15).");
        if (!values.containsKey("blueprintRadius")) values.put("blueprintRadius", "8");
        field("blueprintRadius", left + 310, top + 31, 95, 2);
        button(left, top + 30, 200, "Save blueprint", () -> {
            rememberFields();
            try {
                int radius = Integer.parseInt(values.get("blueprintRadius"));
                if (radius < 1 || radius > 15) throw new NumberFormatException();
                state.send("feature_blueprint_save", mc.player.getPosition(), radius, "", settlementPayload(selected));
                status = "Blueprint save requested.";
            } catch (NumberFormatException exception) { status = "Use a blueprint radius between 1 and 15."; }
        });
        button(left, top + 56, 200, "Preview missing blocks", () -> {
            state.boundaries = true;
            feature("feature_blueprint_preview");
        });
        button(left + 210, top + 56, 200, "Repair from inventory", () -> feature("feature_blueprint_repair"));
        NBTTagList materials = state.features().getTagList("blueprintMaterials", 10);
        for (int i = 0; i < materials.tagCount() && i < 5; i++) {
            NBTTagCompound material = materials.getCompoundTagAt(i);
            String line = material.getString("item") + ": " + material.getInteger("count");
            lines.add(line);
        }
    }

    private void roomPage() {
        lines.add("Stand inside an enclosed room with a door, bed, and chest.");
        lines.add("Register it for an NPC definition from the server NPC file.");
        if (!values.containsKey("npc")) values.put("npc", "settler");
        field("npc", left, top + 39, 410, 64);
        button(left, top + 68, 200, "Register room here", () -> {
            rememberFields();
            state.send("feature_room_add", mc.player.getPosition(), 0, values.get("npc"), settlementPayload(selected));
            status = "Room validation requested.";
        });
        button(left + 210, top + 68, 200, "Remove room here", () -> feature("feature_room_remove"));
    }

    private void warPage() {
        button(left, top + 26, 410, "Offer a war and set terms...", () -> mc.displayGuiScreen(new SettlementScreen(this, Page.WAR_OFFER, selected, null)));
        button(left, top + 52, 200, "Accept offer", () -> feature("feature_war_accept"));
        button(left + 210, top + 52, 200, "Decline offer", () -> feature("feature_war_decline"));
        button(left, top + 78, 200, "Opt in as participant", () -> feature("feature_war_join"));
        button(left + 210, top + 78, 200, "Withdraw before start", () -> feature("feature_war_leave"));
        button(left, top + 104, 200, "Leader: ready / start", () -> feature("feature_war_start"));
        button(left + 210, top + 104, 200, "Leader: surrender", () -> {
            if ("Click surrender again to confirm.".equals(status)) feature("feature_war_surrender");
            else status = "Click surrender again to confirm.";
        });
        button(left, top + 130, 410, "Show current war status in chat", () -> feature("feature_war_status"));
        NBTTagCompound war = state.features().getCompoundTag("war");
        if (!war.hasNoTags() && status.isEmpty()) status = war.getString("status") + " vs " + war.getString("other")
                + " | " + war.getInteger("participants") + " participants | " + war.getInteger("remainingSeconds") + "s";
    }

    private void warOfferPage() {
        if (!values.containsKey("target")) values.put("target", "");
        if (!values.containsKey("minutes")) values.put("minutes", "10");
        if (!values.containsKey("lives")) values.put("lives", "3");
        if (!values.containsKey("stakeItem")) values.put("stakeItem", "minecraft:emerald");
        if (!values.containsKey("stakeMeta")) values.put("stakeMeta", "0");
        if (!values.containsKey("stakeCount")) values.put("stakeCount", "0");
        if (!toggles.containsKey("beds")) toggles.put("beds", false);
        if (!toggles.containsKey("annex")) toggles.put("annex", false);
        field("target", left, top + 12, 410, 64);
        field("minutes", left, top + 43, 200, 3);
        field("lives", left + 210, top + 43, 200, 3);
        field("stakeItem", left, top + 74, 410, 128);
        field("stakeMeta", left, top + 105, 200, 5);
        field("stakeCount", left + 210, top + 105, 200, 2);
        button(left, top + 136, 200, "Win: " + (toggles.get("beds") ? "destroy beds" : "exhaust lives"), () -> {
            toggles.put("beds", !toggles.get("beds")); initGui();
        });
        button(left + 210, top + 136, 200, "Annex loser: " + (toggles.get("annex") ? "YES" : "no"), () -> {
            toggles.put("annex", !toggles.get("annex")); initGui();
        });
        button(left + 315, height - 29, 95, "Offer war", () -> {
            rememberFields();
            try {
                int minutes = Integer.parseInt(values.get("minutes"));
                int lives = Integer.parseInt(values.get("lives"));
                int meta = Integer.parseInt(values.get("stakeMeta"));
                int count = Integer.parseInt(values.get("stakeCount"));
                if (minutes < 1 || lives < 1 || meta < 0 || count < 0 || count > 64 || values.get("target").trim().isEmpty()) throw new NumberFormatException();
                NBTTagCompound payload = settlementPayload(selected);
                payload.setString("condition", toggles.get("beds") ? "beds" : "lives");
                payload.setInteger("lives", lives);
                payload.setString("stakeItem", values.get("stakeItem").trim());
                payload.setInteger("stakeMeta", meta);
                payload.setInteger("stakeCount", count);
                payload.setBoolean("annex", toggles.get("annex"));
                state.send("feature_war_offer", mc.player.getPosition(), minutes, values.get("target").trim(), payload);
                mc.displayGuiScreen(parent);
            } catch (NumberFormatException exception) { status = "Use positive minutes/lives and a stake count of 0-64."; }
        });
    }

    private void feature(String action) {
        state.send(action, mc.player.getPosition(), 0, "", settlementPayload(selected));
        status = "Request sent. The server will report the result.";
    }

    private void save(boolean zone) {
        rememberFields();
        NBTTagCompound payload = new NBTTagCompound();
        for (Map.Entry<String, Boolean> entry : toggles.entrySet()) payload.setBoolean(entry.getKey(), entry.getValue());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (zone || isText(entry.getKey())) payload.setString(entry.getKey(), entry.getValue());
            else {
                try {
                    int value = Integer.parseInt(entry.getValue());
                    if (value < 0) throw new NumberFormatException();
                    payload.setInteger(entry.getKey(), value);
                } catch (NumberFormatException exception) {
                    status = "Use non-negative whole numbers. Invalid field: " + entry.getKey();
                    return;
                }
            }
        }
        state.send(zone ? "zone_settings" : "config", BlockPos.ORIGIN, 0, zone ? selected.getString("id") : "", payload);
        mc.displayGuiScreen(parent);
    }

    private static boolean isNumber(String key) {
        return "radius".equals(key) || "requiredFlags".equals(key) || "requiredPlayers".equals(key)
                || "spawnMinDistance".equals(key) || "spawnMaxDistance".equals(key) || "maxNpcs".equals(key)
                || "maxBlueprintVolume".equals(key) || "raidWaves".equals(key) || "raidMobsPerWave".equals(key) || "raidRewardEmeralds".equals(key)
                || "firstSettlementDistance".equals(key) || "decaySeconds".equals(key);
    }

    private static boolean isText(String key) { return "blacklist".equals(key); }

    private void pagination(int total, int size) {
        if (total <= size) return;
        button(left + 115, height - 29, 90, "Previous", () -> { index = Math.max(0, index - size); initGui(); });
        button(left + 215, height - 29, 90, "Next", () -> { index = index + size < total ? index + size : 0; initGui(); });
    }

    private GuiButton button(int x, int y, int buttonWidth, String label, Runnable action) {
        int scaledWidth = Math.round(buttonWidth * panelScale);
        GuiButton button = new GuiButton(nextId++, left + Math.round((x - left) * panelScale), y, scaledWidth, 20,
                fontRenderer.trimStringToWidth(label, scaledWidth - 8));
        buttonList.add(button);
        actions.put(button.id, action);
        return button;
    }

    private static NBTTagCompound settlementPayload(NBTTagCompound entry) {
        NBTTagCompound payload = new NBTTagCompound();
        if (!entry.hasNoTags()) payload.setString("settlement", entry.getString("id"));
        return payload;
    }

    private void field(String key, int x, int y, int fieldWidth, int maxLength) {
        GuiTextField field = new GuiTextField(nextId++, fontRenderer, left + Math.round((x - left) * panelScale), y,
                Math.round(fieldWidth * panelScale), 19);
        field.setMaxStringLength(maxLength);
        field.setText(values.getOrDefault(key, ""));
        fields.put(key, field);
    }

    private void rememberFields() {
        for (Map.Entry<String, GuiTextField> entry : fields.entrySet()) values.put(entry.getKey(), entry.getValue().getText());
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        Runnable action = actions.get(button.id);
        if (action != null) action.run();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, title(), width / 2, 17, 0xF0E5BE);
        int spacing = page == Page.CONFIG || page == Page.INBOX ? 25 : 14;
        for (int i = 0; i < lines.size(); i++) {
            int lineWidth = Math.round((page == Page.CONFIG || page == Page.INBOX ? 270 : 410) * panelScale);
            int lineY = page == Page.BLUEPRINT && i > 0 ? top + 86 + (i - 1) * 14 : top + 16 + i * spacing;
            fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(lines.get(i).trim(), lineWidth), left + 2, lineY, 0xDDDDDD);
        }
        for (GuiTextField field : fields.values()) field.drawTextBox();
        if (page == Page.ZONE) fontRenderer.drawStringWithShadow("Block blacklist: registry IDs separated by commas", left + 2, top + 138, 0xAAAAAA);
        if (page == Page.WAR) {
            NBTTagCompound war = state.features().getCompoundTag("war");
            if (!war.hasNoTags()) {
                String terms = "Win: " + war.getString("condition") + "; lives " + war.getInteger("livesA") + "/" + war.getInteger("livesB")
                        + "; " + war.getInteger("minutes") + " minutes";
                String stakes = "Annex loser: " + (war.getBoolean("annex") ? "YES" : "no") + " | Stake each: "
                        + war.getInteger("stakeCount") + " x " + war.getString("stakeItem") + ":" + war.getInteger("stakeMeta");
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(terms, Math.round(410 * panelScale)), left + 2, top - 2, 0xFFDD99);
                fontRenderer.drawStringWithShadow(fontRenderer.trimStringToWidth(stakes, Math.round(410 * panelScale)), left + 2, top + 11, 0xFFDD99);
            }
        }
        if (page == Page.WAR_OFFER) {
            formLabel("Target settlement name", 0, 0);
            formLabel("Minutes", 0, 31);
            formLabel("Lives", 210, 31);
            formLabel("Stake item registry ID (escrowed from both leaders)", 0, 62);
            formLabel("Item metadata", 0, 93);
            formLabel("Stake count (0 disables)", 210, 93);
        }
        if (!status.isEmpty()) drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(status, width - 16), width / 2,
                page == Page.WAR_OFFER ? top - 12 : height - 45, page == Page.HOME ? 0xBBBBBB : 0xEACB85);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String title() {
        switch (page) {
            case INBOX: return "Settlements - Inbox";
            case PROPOSE: return "Settlements - New proposal";
            case CONFIRM: return "Settlements - Confirm disband";
            case LEAVE: return "Settlements - Confirm leaving";
            case CONFIG: return "Settlements - Server settings";
            case ZONES: return "Settlements - Zones";
            case ZONE: return "Settlements - Zone settings";
            case FEATURES: return "Settlements - Optional systems";
            case BLUEPRINT: return "Settlements - Blueprints";
            case WAR: return "Settlements - Wars without ruin";
            case WAR_OFFER: return "Settlements - War offer terms";
            case ROOM: return "Settlements - NPC rooms";
            default: return "Settlements";
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField field : fields.values()) field.updateCursorCounter();
        if (lastSnapshot != state.snapshot() && (page == Page.HOME || page == Page.INBOX || page == Page.ZONES || page == Page.FEATURES || page == Page.BLUEPRINT)) initGui();
    }

    private void formLabel(String text, int x, int y) {
        fontRenderer.drawStringWithShadow(text, left + Math.round(x * panelScale), top + y, 0xAAAAAA);
    }

    @Override
    protected void keyTyped(char character, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        for (GuiTextField field : fields.values()) if (field.textboxKeyTyped(character, keyCode)) return;
        super.keyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (GuiTextField field : fields.values()) field.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
