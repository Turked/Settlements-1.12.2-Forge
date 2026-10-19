package com.mechgaming.settlements.common;

import com.mechgaming.settlements.Settlements;
import com.mechgaming.settlements.config.SettlementConfig;
import com.mechgaming.settlements.data.*;
import com.mechgaming.settlements.registry.RegistryHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.*;
import java.util.*;

/** Uses ordinary Forge hooks; no coremod or replacement of RLCraft's mob AI. */
public final class CoreEvents {
    private int ticks,proposalCursor;
    private final Map<UUID,Long> warning=new HashMap<>();
    private void deny(EntityPlayer p,String message){long now=System.currentTimeMillis();Long last=warning.get(p.getUniqueID());if(last==null||now-last>1500){SettlementService.tell(p,message);warning.put(p.getUniqueID(),now);}}
    private boolean anchor(World world,BlockPos pos){Settlement s=SettlementService.findAt(world,pos);return s!=null&&s.flag.equals(pos);}
    @SubscribeEvent(priority=EventPriority.HIGH) public void onBreak(BlockEvent.BreakEvent e){if(e.getWorld().isRemote||!(e.getPlayer() instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.getPlayer();if(anchor(e.getWorld(),e.getPos())){e.setCanceled(true);deny(p,"Use the settlement menu to confirm disbanding before removing its flag.");}else if(!SettlementService.canModify(p,e.getPos())){e.setCanceled(true);deny(p,"Only members may break blocks inside this settlement.");}}
    @SubscribeEvent(priority=EventPriority.HIGH) public void onPlace(BlockEvent.EntityPlaceEvent e){
        if(e.getWorld().isRemote)return;
        List<net.minecraftforge.common.util.BlockSnapshot> blocks=e instanceof BlockEvent.EntityMultiPlaceEvent?((BlockEvent.EntityMultiPlaceEvent)e).getReplacedBlockSnapshots():Collections.singletonList(e.getBlockSnapshot());
        for(net.minecraftforge.common.util.BlockSnapshot block:blocks){BlockPos pos=block.getPos();if(!(e.getEntity() instanceof EntityPlayerMP)){if(SettlementConfig.protectClaims&&SettlementService.findAt(e.getWorld(),pos)!=null)e.setCanceled(true);continue;}EntityPlayerMP p=(EntityPlayerMP)e.getEntity();String error=placementError(p,pos,block.getCurrentBlock());if(error!=null){e.setCanceled(true);deny(p,error);return;}}
    }
    private String placementError(EntityPlayerMP p,BlockPos pos,net.minecraft.block.state.IBlockState state){
        if(!SettlementService.canModify(p,pos))return "Only members may build inside this settlement.";if(SettlementService.isOperator(p)||SettlementService.allowedAt(p.world,pos,"build"))return null;
        Zone z=SettlementService.zoneAt(p.world,pos,"build");boolean prevent=SettlementConfig.advancedZoneSettings&&z!=null?z.preventBuild:SettlementConfig.preventBuildOutsideZones;String id=String.valueOf(state.getBlock().getRegistryName());boolean banned=SettlementConfig.advancedZoneSettings&&z!=null?z.blacklist.contains(id):Arrays.asList(SettlementConfig.blacklist).contains(id);
        if(prevent)return "Building is disabled in this restricted area.";if(banned)return "This block is blacklisted in restricted areas.";if(SettlementService.data(p.world).decay.size()>=8192&&decays(p.world,pos))return "The restricted-area decay queue is full.";return null;
    }
    private boolean decays(World world,BlockPos pos){if(SettlementService.allowedAt(world,pos,"build"))return false;Zone z=SettlementService.zoneAt(world,pos,"build");return SettlementConfig.advancedZoneSettings&&z!=null?z.decay:SettlementConfig.decayOutsideZones;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void decayPlacement(BlockEvent.EntityPlaceEvent e){
        if(e.getWorld().isRemote||!(e.getEntity() instanceof EntityPlayerMP)||SettlementService.isOperator((EntityPlayerMP)e.getEntity()))return;
        List<net.minecraftforge.common.util.BlockSnapshot> blocks=e instanceof BlockEvent.EntityMultiPlaceEvent?((BlockEvent.EntityMultiPlaceEvent)e).getReplacedBlockSnapshots():Collections.singletonList(e.getBlockSnapshot());SettlementData d=SettlementService.data(e.getWorld());boolean added=false;
        for(net.minecraftforge.common.util.BlockSnapshot block:blocks){BlockPos pos=block.getPos();if(!decays(e.getWorld(),pos)||d.decay.size()>=8192)continue;NBTTagCompound n=new NBTTagCompound();n.setInteger("dimension",e.getWorld().provider.getDimension());n.setLong("pos",pos.toLong());n.setInteger("state",Block.getStateId(block.getCurrentBlock()));n.setLong("due",System.currentTimeMillis()+SettlementConfig.decaySeconds*1000L);d.decay.add(n);added=true;}if(added){d.markDirty();deny((EntityPlayerMP)e.getEntity(),"Newly placed blocks here will decay under the current zone rules.");}
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent.RightClickBlock e){if(e.getWorld().isRemote||!(e.getEntityPlayer() instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.getEntityPlayer();if(e.getItemStack().getItem()==RegistryHandler.DEV_TOOL)return;if(!SettlementService.canModify(p,e.getPos())){e.setCanceled(true);deny(p,"Only members may interact with blocks inside this settlement.");}}
    @SubscribeEvent(priority=EventPriority.HIGH) public void bucket(FillBucketEvent e){
        if(e.getWorld().isRemote||!(e.getEntityPlayer() instanceof EntityPlayerMP)||e.getTarget()==null||e.getTarget().typeOfHit!=net.minecraft.util.math.RayTraceResult.Type.BLOCK)return;
        EntityPlayerMP p=(EntityPlayerMP)e.getEntityPlayer();BlockPos hit=e.getTarget().getBlockPos();net.minecraftforge.fluids.FluidStack fluid=net.minecraftforge.fluids.FluidUtil.getFluidContained(e.getEmptyBucket());
        if(fluid==null){if(!SettlementService.canModify(p,hit)){e.setCanceled(true);deny(p,"Only members may remove fluids inside this settlement.");}return;}
        net.minecraft.util.EnumFacing face=e.getTarget().sideHit;if(face==null)return;BlockPos target=e.getWorld().getBlockState(hit).getBlock().isReplaceable(e.getWorld(),hit)&&face==net.minecraft.util.EnumFacing.UP?hit:hit.offset(face);
        Block block=fluid.getFluid().getBlock();if(block==null)block=net.minecraft.init.Blocks.WATER;String error=placementError(p,target,block.getDefaultState());
        if(error==null&&(block==net.minecraft.init.Blocks.WATER||block==net.minecraft.init.Blocks.FLOWING_WATER))error=placementError(p,target,net.minecraft.init.Blocks.FLOWING_WATER.getDefaultState());
        if(error==null&&(block==net.minecraft.init.Blocks.LAVA||block==net.minecraft.init.Blocks.FLOWING_LAVA))error=placementError(p,target,net.minecraft.init.Blocks.FLOWING_LAVA.getDefaultState());
        if(error!=null){e.setCanceled(true);deny(p,error);}
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public void entityInteract(PlayerInteractEvent.EntityInteract e){if(e.getWorld().isRemote||!(e.getEntityPlayer() instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.getEntityPlayer();if(!SettlementService.canModify(p,e.getPos())){e.setCanceled(true);deny(p,"This settlement protects its entities from visitors.");}}
    @SubscribeEvent(priority=EventPriority.HIGH) public void entityInteractAt(PlayerInteractEvent.EntityInteractSpecific e){if(e.getWorld().isRemote||!(e.getEntityPlayer() instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.getEntityPlayer();if(!SettlementService.canModify(p,e.getPos())){e.setCanceled(true);deny(p,"This settlement protects its entities from visitors.");}}
    @SubscribeEvent(priority=EventPriority.HIGH) public void attack(AttackEntityEvent e){if(e.getEntityPlayer().world.isRemote||!(e.getEntityPlayer() instanceof EntityPlayerMP)||e.getTarget() instanceof EntityPlayer||e.getTarget() instanceof IMob)return;EntityPlayerMP p=(EntityPlayerMP)e.getEntityPlayer();if(!SettlementService.canModify(p,e.getTarget().getPosition())){e.setCanceled(true);deny(p,"This settlement protects its animals and decorations.");}}
    @SubscribeEvent(priority=EventPriority.HIGH) public void explosion(ExplosionEvent.Detonate e){if(!e.getWorld().isRemote&&SettlementConfig.protectClaims)e.getAffectedBlocks().removeIf(pos->SettlementService.findAt(e.getWorld(),pos)!=null);}
    @SubscribeEvent(priority=EventPriority.HIGH) public void mobGrief(LivingDestroyBlockEvent e){if(!e.getEntityLiving().world.isRemote&&SettlementConfig.protectClaims&&SettlementService.findAt(e.getEntityLiving().world,e.getPos())!=null)e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGH) public void grief(net.minecraftforge.event.entity.EntityMobGriefingEvent e){if(!e.getEntity().world.isRemote&&SettlementConfig.protectClaims&&SettlementService.findAt(e.getEntity().world,e.getEntity().getPosition())!=null)e.setResult(Event.Result.DENY);}
    @SubscribeEvent(priority=EventPriority.HIGH) public void fire(BlockEvent.NeighborNotifyEvent e){if(e.getWorld().isRemote||!SettlementConfig.protectClaims)return;if(e.getState().getBlock()==net.minecraft.init.Blocks.FIRE&&SettlementService.findAt(e.getWorld(),e.getPos())!=null)e.getWorld().setBlockToAir(e.getPos());}
    @SubscribeEvent(priority=EventPriority.HIGH) public void fluid(BlockEvent.FluidPlaceBlockEvent e){if(!e.getWorld().isRemote&&SettlementConfig.protectClaims&&SettlementService.findAt(e.getWorld(),e.getPos())!=null)e.setNewState(e.getOriginalState());}
    @SubscribeEvent(priority=EventPriority.HIGH) public void spawn(LivingSpawnEvent.CheckSpawn e){if(e.getWorld().isRemote||!(e.getEntityLiving() instanceof IMob))return;BlockPos pos=new BlockPos(e.getX(),e.getY(),e.getZ());if(!SettlementService.allowedAt(e.getWorld(),pos,"spawn"))e.setResult(Event.Result.DENY);}
    @SubscribeEvent(priority=EventPriority.HIGH) public void joinWorld(net.minecraftforge.event.entity.EntityJoinWorldEvent e){if(!e.getWorld().isRemote&&e.getEntity() instanceof IMob&&e.getEntity().ticksExisted==0&&!SettlementService.allowedAt(e.getWorld(),e.getEntity().getPosition(),"spawn"))e.setCanceled(true);}
    @SubscribeEvent public void leftBlock(PlayerInteractEvent.LeftClickBlock e){if(e.getWorld().isRemote||e.getHand()!=EnumHand.MAIN_HAND||e.getItemStack().getItem()!=RegistryHandler.DEV_TOOL||!(e.getEntityPlayer() instanceof EntityPlayerMP))return;e.setCanceled(true);cycle((EntityPlayerMP)e.getEntityPlayer());}
    private void cycle(EntityPlayerMP p){ItemStack stack=p.getHeldItemMainhand();NBTTagCompound n=stack.getTagCompound();int mode=n==null?0:n.getInteger("zoneMode");SettlementService.handle(p,"zone_mode",p.getPosition(),p.isSneaking()?2:(mode==0?1:0),"",null);}
    @SubscribeEvent public void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e){if(e.player instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)e.player;Settlements.sendState(p);Settlement s=SettlementService.ownedBy(p);if(s!=null&&!s.requests.isEmpty())SettlementService.tell(p,"Your settlement has "+s.requests.size()+" pending membership request(s).");}}
    @SubscribeEvent public void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e){SettlementService.forget(e.player.getUniqueID());warning.remove(e.player.getUniqueID());}
    @SubscribeEvent public void changedDimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent e){if(e.player instanceof EntityPlayerMP)Settlements.sendState((EntityPlayerMP)e.player);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||++ticks%20!=0)return;net.minecraft.server.MinecraftServer server=net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();if(server==null)return;World w=server.getWorld(0);if(w==null)return;SettlementData d=SettlementService.data(w);long now=System.currentTimeMillis();boolean dirty=d.proposals.removeIf(p->p.expires<=now);List<Proposal> pending=new ArrayList<>(d.proposals);for(int checked=0;checked<Math.min(16,pending.size());checked++){Proposal p=pending.get(Math.floorMod(proposalCursor++,pending.size()));dirty|=SettlementService.tryFinalize(w,p);}
        Iterator<NBTTagCompound> it=d.decay.iterator();int budget=64;while(it.hasNext()&&budget>0){NBTTagCompound n=it.next();if(n.getLong("due")>now)continue;World world=server.getWorld(n.getInteger("dimension"));BlockPos pos=BlockPos.fromLong(n.getLong("pos"));if(world==null||!world.isBlockLoaded(pos))continue;budget--;boolean restricted=!SettlementService.allowedAt(world,pos,"build");Zone z=SettlementService.zoneAt(world,pos,"build");boolean decay=SettlementConfig.advancedZoneSettings&&z!=null?z.decay:SettlementConfig.decayOutsideZones;if(restricted&&decay&&SettlementService.findAt(world,pos)==null&&Block.getStateId(world.getBlockState(pos))==n.getInteger("state")&&world.getTileEntity(pos)==null)world.destroyBlock(pos,false);it.remove();dirty=true;}
        if(dirty)d.markDirty();if(dirty||ticks%100==0)SettlementService.syncAll(w);
    }
}

