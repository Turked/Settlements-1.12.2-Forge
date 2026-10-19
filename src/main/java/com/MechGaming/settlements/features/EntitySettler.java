package com.MechGaming.settlements.features;

import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.SoundEvents;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.world.World;

/** Vanilla villager appearance and AI, with a definition-controlled trade list. */
public final class EntitySettler extends EntityVillager {
    public EntitySettler(World world) { super(world); }

    @Override public void useRecipe(MerchantRecipe recipe) {
        // Vanilla useRecipe schedules populateBuyingList(), which adds random career trades.
        // Definition trades instead retain their configured maximum uses across world saves.
        recipe.incrementToolUses();
        playSound(SoundEvents.ENTITY_VILLAGER_YES, getSoundVolume(), getSoundPitch());
        if (!world.isRemote && recipe.getRewardsExp()) world.spawnEntity(new EntityXPOrb(world, posX, posY + .5, posZ, 3 + rand.nextInt(4)));
    }

    @Override public boolean getIsWillingToMate(boolean updateFirst) { return false; }
    @Override public void setIsWillingToMate(boolean willing) { super.setIsWillingToMate(false); }
}
