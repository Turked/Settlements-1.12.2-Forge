package com.mechgaming.settlements.block;
import com.mechgaming.settlements.common.SettlementService;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.SoundType;
import com.mechgaming.settlements.registry.ModCreativeTabs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Deliberately borrows vanilla wool's model until the author's flag asset is supplied. */
public final class SettlementFlagBlock extends Block {
    public SettlementFlagBlock(){super(Material.CLOTH);setRegistryName("settlements","settlement_flag");setUnlocalizedName("settlements.settlement_flag");setHardness(2F);setResistance(6000000F);setSoundType(SoundType.CLOTH);setCreativeTab(ModCreativeTabs.SETTLEMENTS);}
    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing facing,float x,float y,float z){if(!world.isRemote&&player instanceof EntityPlayerMP)SettlementService.handle((EntityPlayerMP)player,"context",pos,0,"",null);return true;}
    @Override public EnumPushReaction getMobilityFlag(IBlockState state){return EnumPushReaction.BLOCK;}
    @Override public boolean isFlammable(net.minecraft.world.IBlockAccess world,BlockPos pos,EnumFacing face){return false;}
}
