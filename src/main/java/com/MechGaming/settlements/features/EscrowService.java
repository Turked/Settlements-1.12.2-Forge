package com.MechGaming.settlements.features;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import java.util.*;

/** Narrow inventory wagers with persistent offline payouts; no arbitrary container snapshots. */
final class EscrowService {
    private EscrowService() { }

    static boolean terms(NBTTagCompound war, NBTTagCompound payload, EntityPlayerMP player) {
        int count = payload == null ? 0 : payload.getInteger("stakeCount");
        if (count == 0) return true;
        if (count < 0 || count > 64) { FeatureService.tell(player, "Stake count must be 0 (none) or 1..64 items from each leader."); return false; }
        String name = payload.getString("stakeItem"); int meta = payload.getInteger("stakeMeta");
        try {
            ResourceLocation id = new ResourceLocation(name);
            if (!Item.REGISTRY.containsKey(id) || Item.REGISTRY.getObject(id) == Items.AIR || meta < 0 || meta > 32767) throw new IllegalArgumentException();
            war.setString("stakeItem", id.toString()); war.setInteger("stakeMeta", meta); war.setInteger("stakeCount", count); return true;
        } catch (RuntimeException error) { FeatureService.tell(player, "Stake item must be an installed item registry ID with metadata 0..32767."); return false; }
    }

    static boolean collect(MinecraftServer server, NBTTagCompound war) {
        if (war.getInteger("stakeCount") == 0) return true;
        EntityPlayerMP a = server.getPlayerList().getPlayerByUUID(war.getUniqueId("ownerA")), b = server.getPlayerList().getPlayerByUUID(war.getUniqueId("ownerB"));
        ResourceLocation id = new ResourceLocation(war.getString("stakeItem"));
        if (!Item.REGISTRY.containsKey(id) || Item.REGISTRY.getObject(id) == Items.AIR) return false;
        Item item = Item.REGISTRY.getObject(id); int count = war.getInteger("stakeCount"), meta = war.getInteger("stakeMeta");
        if (a == null || b == null || a.isCreative() || b.isCreative() || !has(a, item, meta, count) || !has(b, item, meta, count)) return false;
        // Both inventories are checked before either changes, on the single server thread.
        take(a, item, meta, count); take(b, item, meta, count);
        war.setTag("escrowA", new ItemStack(item, count, meta).writeToNBT(new NBTTagCompound()));
        war.setTag("escrowB", new ItemStack(item, count, meta).writeToNBT(new NBTTagCompound()));
        FeatureWorldData.get(server.getWorld(0)).markDirty(); return true;
    }

    private static boolean has(EntityPlayerMP p, Item item, int meta, int need) {
        for (ItemStack stack : p.inventory.mainInventory) if (!stack.isEmpty() && stack.getItem() == item && stack.getMetadata() == meta && !stack.hasTagCompound()) need -= stack.getCount();
        return need <= 0;
    }
    private static void take(EntityPlayerMP p, Item item, int meta, int need) {
        for (ItemStack stack : p.inventory.mainInventory) if (need > 0 && !stack.isEmpty() && stack.getItem() == item && stack.getMetadata() == meta && !stack.hasTagCompound()) { int n = Math.min(need, stack.getCount()); stack.shrink(n); need -= n; }
        p.inventory.markDirty(); p.inventoryContainer.detectAndSendChanges();
    }

    static void finish(MinecraftServer server, NBTTagCompound war, String winner) {
        FeatureWorldData data = FeatureWorldData.get(server.getWorld(0));
        for (String side : Arrays.asList("A", "B")) {
            if (!war.hasKey("escrow" + side, 10)) continue;
            ItemStack stack = new ItemStack(war.getCompoundTag("escrow" + side));
            if (!stack.isEmpty()) {
                NBTTagCompound payout = new NBTTagCompound(); payout.setUniqueId("owner", war.getUniqueId("owner" + (winner == null ? side : winner)));
                payout.setTag("stack", stack.writeToNBT(new NBTTagCompound())); data.payouts.appendTag(payout);
            }
            war.removeTag("escrow" + side);
        }
        data.markDirty(); tick(server);
    }

    static void tick(MinecraftServer server) {
        FeatureWorldData data = FeatureWorldData.get(server.getWorld(0));
        for (int i = data.payouts.tagCount() - 1; i >= 0; i--) {
            NBTTagCompound payout = data.payouts.getCompoundTagAt(i); EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(payout.getUniqueId("owner"));
            if (p == null) continue; ItemStack stack = new ItemStack(payout.getCompoundTag("stack"));
            if (!stack.isEmpty()) {
                p.inventory.addItemStackToInventory(stack);
                p.inventory.markDirty(); p.inventoryContainer.detectAndSendChanges();
                if (!stack.isEmpty()) { payout.setTag("stack", stack.writeToNBT(new NBTTagCompound())); data.markDirty(); continue; }
                FeatureService.tell(p, "Your agreed war wager payout/refund was delivered.");
            }
            data.payouts.removeTag(i); data.markDirty();
        }
    }
}
