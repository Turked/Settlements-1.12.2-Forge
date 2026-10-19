package com.MechGaming.settlements.item;
import com.MechGaming.settlements.common.SettlementService;
import net.minecraft.block.Block;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Only the service may place the anchor; using the physical item starts the same validation path. */
public final class FlagItem extends ItemBlock {
    public FlagItem(Block block){super(block);setRegistryName(block.getRegistryName());}
    @Override public EnumActionResult onItemUse(EntityPlayer player,World world,BlockPos pos,EnumHand hand,EnumFacing face,float x,float y,float z){if(!world.isRemote&&player instanceof EntityPlayerMP){BlockPos target=world.getBlockState(pos).getBlock().isReplaceable(world,pos)?pos:pos.offset(face);SettlementService.handle((EntityPlayerMP)player,"propose",target,0,player.getName()+"'s Settlement",null);}return EnumActionResult.SUCCESS;}
}
