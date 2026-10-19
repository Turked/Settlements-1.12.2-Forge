package com.mechgaming.settlements.item;
import com.mechgaming.settlements.common.SettlementService;
import com.mechgaming.settlements.registry.ModCreativeTabs;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class DevToolItem extends Item {
    public DevToolItem(){setRegistryName("settlements","dev_tool");setUnlocalizedName("settlements.dev_tool");setMaxStackSize(1);setCreativeTab(ModCreativeTabs.SETTLEMENTS);}
    @Override public EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos pos,EnumHand hand,EnumFacing face,float x,float y,float z){if(!world.isRemote&&player instanceof EntityPlayerMP){if(player.isSneaking())SettlementService.handle((EntityPlayerMP)player,"dev_menu",pos,1,"",null);else {ItemStack stack=player.getHeldItem(hand);NBTTagCompound n=stack.getTagCompound();SettlementService.handle((EntityPlayerMP)player,"zone_corner",pos,n!=null?n.getInteger("zoneMode"):0,n!=null&&n.getBoolean("zoneAllow")?"allow":"restrict",null);}}return EnumActionResult.SUCCESS;}
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand){if(!world.isRemote&&player instanceof EntityPlayerMP)SettlementService.handle((EntityPlayerMP)player,"dev_menu",player.getPosition(),player.isSneaking()?1:0,"",null);return new ActionResult<>(EnumActionResult.SUCCESS,player.getHeldItem(hand));}
}
