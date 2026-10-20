package com.MechGaming.settlements.block;
import com.MechGaming.settlements.common.SettlementService;
import com.MechGaming.settlements.registry.ModCreativeTabs;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Settlement anchor with collision and targeting matching the pole and cloth model. */
public final class SettlementFlagBlock extends Block {
    private static final AxisAlignedBB POLE_BOX = new AxisAlignedBB(7D / 16D, 0D, 7D / 16D, 9D / 16D, 12D / 16D, 9D / 16D);
    private static final AxisAlignedBB CLOTH_BOX = new AxisAlignedBB(7D / 16D, 7D / 16D, -1D / 16D, 9D / 16D, 12D / 16D, 7D / 16D);
    private static final AxisAlignedBB BOUNDS = POLE_BOX.union(CLOTH_BOX);

    public SettlementFlagBlock(){super(Material.CLOTH);setRegistryName("settlements","settlement_flag");setUnlocalizedName("settlements.settlement_flag");setHardness(2F);setResistance(6000000F);setSoundType(SoundType.CLOTH);setCreativeTab(ModCreativeTabs.SETTLEMENTS);}

    @Override public boolean isOpaqueCube(IBlockState state){return false;}
    @Override public boolean isFullCube(IBlockState state){return false;}
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess world,IBlockState state,BlockPos pos,EnumFacing face){return BlockFaceShape.UNDEFINED;}
    @Override public BlockRenderLayer getBlockLayer(){return BlockRenderLayer.CUTOUT;}
    @Override public AxisAlignedBB getBoundingBox(IBlockState state,IBlockAccess world,BlockPos pos){return BOUNDS;}

    @Override
    public void addCollisionBoxToList(IBlockState state,World world,BlockPos pos,AxisAlignedBB entityBox,List<AxisAlignedBB> collidingBoxes,@Nullable Entity entity,boolean isActualState){
        addCollisionBoxToList(pos,entityBox,collidingBoxes,POLE_BOX);
        addCollisionBoxToList(pos,entityBox,collidingBoxes,CLOTH_BOX);
    }

    @Override
    @Nullable
    public RayTraceResult collisionRayTrace(IBlockState state,World world,BlockPos pos,Vec3d start,Vec3d end){
        RayTraceResult poleHit=rayTrace(pos,start,end,POLE_BOX);
        RayTraceResult clothHit=rayTrace(pos,start,end,CLOTH_BOX);
        if(poleHit==null)return clothHit;
        if(clothHit==null)return poleHit;
        return poleHit.hitVec.squareDistanceTo(start)<=clothHit.hitVec.squareDistanceTo(start)?poleHit:clothHit;
    }

    @Override public boolean onBlockActivated(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand,EnumFacing facing,float x,float y,float z){if(!world.isRemote&&player instanceof EntityPlayerMP)SettlementService.handle((EntityPlayerMP)player,"context",pos,0,"",null);return true;}
    @Override public EnumPushReaction getMobilityFlag(IBlockState state){return EnumPushReaction.BLOCK;}
    @Override public boolean isFlammable(net.minecraft.world.IBlockAccess world,BlockPos pos,EnumFacing face){return false;}
}
