package com.MechGaming.settlements.common;

import com.MechGaming.settlements.Settlements;
import com.MechGaming.settlements.config.SettlementConfig;
import com.MechGaming.settlements.data.*;
import com.MechGaming.settlements.features.FeatureService;
import com.MechGaming.settlements.registry.RegistryHandler;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import java.util.*;

/** All client intent and command mutations pass through these server-side checks. */
public final class SettlementService {
    private static final Map<UUID,Long> LAST_ACTION=new HashMap<>();
    private static final Map<UUID,Long> PROPOSAL_WARNINGS=new HashMap<>();
    private static final Map<UUID,Corner> CORNERS=new HashMap<>();
    private static final Map<UUID,String> GUI=new HashMap<>(), GUI_ZONE=new HashMap<>(),GUI_SETTLEMENT=new HashMap<>();
    private static final Map<UUID,String> SELECTION=new HashMap<>();
    private static final class Corner { final BlockPos pos;final int dim,mode;final boolean allow;Corner(BlockPos p,int d,int m,boolean a){pos=p;dim=d;mode=m;allow=a;} }
    private SettlementService(){}
    public static SettlementData data(World world){return SettlementData.get(world);}
    public static Settlement findAt(World world,BlockPos pos){for(Settlement s:data(world).settlements)if(s.contains(world.provider.getDimension(),pos))return s;return null;}
    public static Settlement ownedBy(EntityPlayerMP p){for(Settlement s:data(p.world).settlements)if(s.owner.equals(p.getUniqueID()))return s;return null;}
    /** An explicit claim selection never falls back to a different claim. */
    public static Settlement targetOwned(EntityPlayerMP player,NBTTagCompound payload){return targetOwned(player,payload==null?"":payload.getString("settlement"));}
    public static Settlement targetOwned(EntityPlayerMP player,String selection){if(selection==null||selection.isEmpty())return ownedBy(player);UUID id=uuid(selection);for(Settlement s:data(player.world).settlements)if(s.id.equals(id)&&s.owner.equals(player.getUniqueID()))return s;return null;}
    public static Settlement forPlayer(EntityPlayerMP p){for(Settlement s:data(p.world).settlements)if(isMember(s,p.getUniqueID()))return s;return null;}
    public static boolean isMember(Settlement s,UUID p){return s!=null&&(s.owner.equals(p)||s.members.contains(p));}
    public static boolean isOperator(EntityPlayer p){return p.canUseCommand(2,"settlements");}
    public static void tell(EntityPlayer p,String text){p.sendMessage(new TextComponentString("[Settlements] "+text));}
    public static void syncAll(World world){for(EntityPlayerMP p:world.getMinecraftServer().getPlayerList().getPlayers())Settlements.sendState(p);}
    public static Zone zoneAt(World world,BlockPos pos,String type){Zone result=null;for(Zone z:data(world).zones)if(z.type.equals(type)&&z.contains(world.provider.getDimension(),pos))result=z;return result;}
    public static boolean allowedAt(World world,BlockPos pos,String type){Zone z=zoneAt(world,pos,type);return z==null?("spawn".equals(type)||SettlementConfig.defaultAllowed):z.allowed;}
    public static boolean canModify(EntityPlayerMP p,BlockPos pos){if(isOperator(p))return true;Settlement s=findAt(p.world,pos);return !SettlementConfig.protectClaims||s==null||isMember(s,p.getUniqueID())||FeatureService.canAttack(p,s);}
    public static int flagCount(EntityPlayer p){int count=0;for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==RegistryHandler.FLAG_ITEM)count+=stack.getCount();for(ItemStack stack:p.inventory.offHandInventory)if(stack.getItem()==RegistryHandler.FLAG_ITEM)count+=stack.getCount();return count;}
    private static void consumeFlags(EntityPlayerMP p,int count){for(ItemStack stack:p.inventory.mainInventory){if(stack.getItem()==RegistryHandler.FLAG_ITEM){int n=Math.min(count,stack.getCount());stack.shrink(n);count-=n;if(count==0)break;}}if(count>0)for(ItemStack stack:p.inventory.offHandInventory)if(stack.getItem()==RegistryHandler.FLAG_ITEM){int n=Math.min(count,stack.getCount());stack.shrink(n);count-=n;}p.inventory.markDirty();p.inventoryContainer.detectAndSendChanges();}
    private static UUID uuid(String text){try{return UUID.fromString(text);}catch(IllegalArgumentException e){return null;}}
    private static String name(String text){String s=text==null?"":text.replaceAll("[\\p{Cntrl}§]","").trim();return s.substring(0,Math.min(40,s.length()));}
    private static boolean near(EntityPlayerMP p,BlockPos pos,double range){return p.getDistanceSqToCenter(pos)<=range*range&&p.world.isBlockLoaded(pos)&&p.world.getWorldBorder().contains(pos);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    public static void handle(EntityPlayerMP player,String action,BlockPos pos,int value,String text,NBTTagCompound payload){
        if(player==null||player.world.isRemote)return;
        if(pos==null)pos=player.getPosition();if(payload==null)payload=new NBTTagCompound();if(text==null)text="";
        if(text.length()>512||action==null||action.length()>64)return;
        long now=System.currentTimeMillis();Long last=LAST_ACTION.get(player.getUniqueID());
        if(last!=null&&now-last<("sync".equals(action)?200:100))return;LAST_ACTION.put(player.getUniqueID(),now);
        SettlementData d=data(player.world); boolean changed=false;
        if(payload.hasKey("settlement",8)){UUID requested=uuid(payload.getString("settlement"));for(Settlement s:d.settlements)if(s.id.equals(requested)&&isMember(s,player.getUniqueID())){SELECTION.put(player.getUniqueID(),s.id.toString());break;}}
        try{
            Settlement own=targetOwned(player,payload);UUID selected=uuid(text);
            switch(action){
                case "sync": break;
                case "context": {Settlement here=findAt(player.world,pos);if(here!=null&&here.owner.equals(player.getUniqueID())){GUI.put(player.getUniqueID(),"settlement");GUI_SETTLEMENT.put(player.getUniqueID(),here.id.toString());SELECTION.put(player.getUniqueID(),here.id.toString());}break;}
                case "propose": {
                    require(forPlayer(player)==null,"Leave your current settlement before founding another.");require(near(player,pos,8),"Place a flag within eight blocks of you.");
                    String error=claimError(player.world,pos,SettlementConfig.radius,null);require(error==null,error);
                    require(flagCount(player)>=SettlementConfig.requiredFlags,"You need "+SettlementConfig.requiredFlags+" settlement flag(s).");
                    require(d.settlements.size()<SettlementConfig.maxSettlements,"This world's settlement limit has been reached.");
                    for(Proposal candidate:d.proposals)require(!candidate.owner.equals(player.getUniqueID()),"You already have a proposal. Support it, cancel it, or wait for expiry.");
                    require(d.proposals.size()<SettlementConfig.maxSettlements,"The proposal limit has been reached.");
                    Proposal p=new Proposal();p.owner=player.getUniqueID();p.flag=pos.toImmutable();p.dimension=player.dimension;p.radius=SettlementConfig.radius;p.name=name(text);if(p.name.isEmpty())p.name=player.getName()+"'s Settlement";p.expires=now+SettlementConfig.proposalMinutes*60000L;p.supporters.put(player.getUniqueID(),player.getName());d.proposals.add(p);changed=true;tell(player,"Proposal created: "+p.name+". Nearby willing players can support it.");tryFinalize(player.world,p);break;
                }
                case "support": {
                    Proposal p=null;for(Proposal candidate:d.proposals)if(candidate.id.equals(selected))p=candidate;require(p!=null,"That proposal no longer exists.");require(forPlayer(player)==null,"You already belong to a settlement.");require(p.contains(player.dimension,player.getPosition()),"Stand inside the proposed settlement to support it.");require(p.expires>now,"That proposal expired.");p.supporters.put(player.getUniqueID(),player.getName());changed=true;tell(player,"You are ready to join "+p.name+".");tryFinalize(player.world,p);break;
                }
                case "cancel": {Proposal p=null;for(Proposal q:d.proposals)if(q.owner.equals(player.getUniqueID()))p=q;require(p!=null,"You have no proposal.");d.proposals.remove(p);changed=true;tell(player,"Proposal cancelled; no flags were consumed.");break;}
                case "join": {
                    require(forPlayer(player)==null,"You already belong to a settlement.");Settlement target=null;for(Settlement s:d.settlements)if(s.id.equals(selected))target=s;require(target!=null,"That settlement no longer exists.");require(target.accepting,"That settlement is closed to requests.");require(target.contains(player.dimension,player.getPosition()),"Stand inside the settlement to request membership.");require(target.requests.size()<256,"The settlement inbox is full.");target.requests.put(player.getUniqueID(),player.getName());EntityPlayerMP leader=player.getServer().getPlayerList().getPlayerByUUID(target.owner);if(leader!=null)tell(leader,player.getName()+" requested to join "+target.name+". Check your settlement inbox.");tell(player,"Membership request sent.");changed=true;break;
                }
                case "accept":case "deny": {
                    require(own!=null,"Only the settlement owner can manage requests.");require(selected!=null&&own.requests.containsKey(selected),"That request no longer exists.");if("accept".equals(action))for(Settlement s:d.settlements)require(!isMember(s,selected),"That player has already joined another settlement.");String applicant=own.requests.remove(selected);if("accept".equals(action)){for(Settlement s:d.settlements){if(s.owner.equals(own.owner)){s.members.add(selected);s.memberNames.put(selected,applicant);}s.requests.remove(selected);}}EntityPlayerMP target=player.getServer().getPlayerList().getPlayerByUUID(selected);if(target!=null)tell(target,"Your request to join "+own.name+" was "+("accept".equals(action)?"accepted.":"declined."));changed=true;break;
                }
                case "accepting": require(own!=null,"Only the settlement owner can change recruitment.");own.accepting=value!=0;changed=true;break;
                case "leave": {Settlement memberOf=forPlayer(player);require(memberOf!=null,"You do not belong to a settlement.");require(!memberOf.owner.equals(player.getUniqueID()),"Owners must disband their settlement.");require(!FeatureService.hasWar(player.world,memberOf.id),"Wait until your settlement's war agreement ends before leaving its membership. You may opt out of combat during war preparation.");for(Settlement s:d.settlements)if(s.owner.equals(memberOf.owner)){s.members.remove(player.getUniqueID());s.memberNames.remove(player.getUniqueID());}changed=true;break;}
                case "disband": {
                    Settlement target=own;if(!text.isEmpty()){target=null;require(selected!=null,"Select a valid settlement UUID.");for(Settlement s:d.settlements)if(s.id.equals(selected)&&(isOperator(player)||s.owner.equals(player.getUniqueID())))target=s;}
                    require(target!=null,"Only the owner or an operator can disband this settlement.");require(payload.getBoolean("confirm"),"Disbanding requires explicit confirmation.");require(!FeatureService.hasWar(player.world,target.id),"Resolve the settlement's war agreement before disbanding: decline/cancel before combat, or surrender during an active war.");World world=player.getServer().getWorld(target.dimension);require(world!=null,"That settlement's dimension is unavailable; retry when it is loaded.");world.getChunkFromBlockCoords(target.flag);if(world.getBlockState(target.flag).getBlock()==RegistryHandler.SETTLEMENT_FLAG)world.setBlockToAir(target.flag);d.settlements.remove(target);changed=true;tell(player,"Settlement disbanded. Consumed flags are not refunded.");break;
                }
                case "dev_menu": {
                    require(isOperator(player),"The developer tool requires operator permission level 2.");Zone z=zoneAt(player.world,player.getPosition(),"build");GUI.put(player.getUniqueID(),value==1||z==null?"config":"zone");if(z!=null)GUI_ZONE.put(player.getUniqueID(),z.id.toString());break;
                }
                case "zone_mode": {
                    require(isOperator(player),"Only operators can edit zones.");ItemStack held=player.getHeldItemMainhand();require(held.getItem()==RegistryHandler.DEV_TOOL,"Hold the developer tool.");NBTTagCompound n=held.getTagCompound();if(n==null){n=new NBTTagCompound();held.setTagCompound(n);}if(value==2)n.setBoolean("zoneAllow",!n.getBoolean("zoneAllow"));else n.setInteger("zoneMode",value==1?1:0);CORNERS.remove(player.getUniqueID());tell(player,"Tool: "+(n.getInteger("zoneMode")==1?"spawn":"build")+" zones, "+(n.getBoolean("zoneAllow")?"allow":"restrict")+".");break;
                }
                case "zone_corner": {
                    require(isOperator(player),"Only operators can edit zones.");require(near(player,pos,128),"Zone corners must be within 128 blocks and loaded.");boolean allow="allow".equals(text);int mode=value==1?1:0;Corner c=CORNERS.get(player.getUniqueID());if(c==null||c.dim!=player.dimension||c.mode!=mode||c.allow!=allow){CORNERS.put(player.getUniqueID(),new Corner(pos.toImmutable(),player.dimension,mode,allow));tell(player,"First corner selected at "+pos.getX()+", "+pos.getZ()+". Select the opposite corner.");break;}
                    require(d.zones.size()<SettlementConfig.maxZones,"Zone limit reached.");require(Math.abs((long)c.pos.getX()-pos.getX())+1<=SettlementConfig.maxZoneSize&&Math.abs((long)c.pos.getZ()-pos.getZ())+1<=SettlementConfig.maxZoneSize,"Zone exceeds the configured side length limit.");Zone z=new Zone();z.type=mode==1?"spawn":"build";z.dimension=player.dimension;z.allowed=allow;z.name=(allow?"Allowed ":"Restricted ")+z.type+" zone";z.minX=Math.min(c.pos.getX(),pos.getX());z.maxX=Math.max(c.pos.getX(),pos.getX());z.minZ=Math.min(c.pos.getZ(),pos.getZ());z.maxZ=Math.max(c.pos.getZ(),pos.getZ());z.preventBuild=SettlementConfig.preventBuildOutsideZones;z.decay=SettlementConfig.decayOutsideZones;d.zones.add(z);CORNERS.remove(player.getUniqueID());changed=true;tell(player,"Created "+z.name+" ("+z.id+"). Later zones override earlier zones.");if(SettlementConfig.advancedZoneSettings){GUI.put(player.getUniqueID(),"zone");GUI_ZONE.put(player.getUniqueID(),z.id.toString());}break;
                }
                case "zone_settings":case "zone_delete": {
                    require(isOperator(player),"Only operators can edit zones.");Zone z=null;for(Zone q:d.zones)if(q.id.equals(selected))z=q;require(z!=null,"Zone not found.");if("zone_delete".equals(action))d.zones.remove(z);else {String[] newBlacklist=payload.hasKey("blacklist",8)?SettlementConfig.parseBlacklist(payload.getString("blacklist")):null;if(payload.hasKey("allowed",1))z.allowed=payload.getBoolean("allowed");if(payload.hasKey("preventBuild",1))z.preventBuild=payload.getBoolean("preventBuild");if(payload.hasKey("decay",1))z.decay=payload.getBoolean("decay");if(payload.hasKey("name",8))z.name=name(payload.getString("name"));if(newBlacklist!=null){z.blacklist.clear();z.blacklist.addAll(Arrays.asList(newBlacklist));}}changed=true;break;
                }
                case "config": require(isOperator(player),"Only operators can change server configuration.");SettlementConfig.apply(payload);tell(player,"Configuration saved and applied. Existing claim sizes stay unchanged.");changed=true;break;
                default: if(!FeatureService.handle(player,action,pos,value,text,payload))tell(player,"Unknown action. Use /settlements help.");break;
            }
        }catch(IllegalArgumentException ex){tell(player,ex.getMessage()==null?"Action rejected.":ex.getMessage());}
        if(changed){d.markDirty();syncAll(player.world);}else Settlements.sendState(player);
    }
    public static String claimError(World world,BlockPos flag,int radius,Proposal self){
        if(!world.isBlockLoaded(flag)||!world.getWorldBorder().contains(flag.add(-radius,0,-radius))||!world.getWorldBorder().contains(flag.add(radius,0,radius)))return "The entire claim must lie inside the world border.";
        if(flag.getY()<1||flag.getY()>=world.getHeight()-1||!world.getBlockState(flag).getBlock().isReplaceable(world,flag)||!world.getBlockState(flag.down()).isSideSolid(world,flag.down(),net.minecraft.util.EnumFacing.UP))return "The flag needs clear space above a solid supporting block.";
        SettlementData d=data(world);for(Settlement s:d.settlements)if(s.dimension==world.provider.getDimension()&&Math.abs((long)s.flag.getX()-flag.getX())<=s.radius+radius&&Math.abs((long)s.flag.getZ()-flag.getZ())<=s.radius+radius)return "The proposed area overlaps an existing settlement.";
        for(Proposal p:d.proposals)if(p!=self&&p.dimension==world.provider.getDimension()&&Math.abs((long)p.flag.getX()-flag.getX())<=p.radius+radius&&Math.abs((long)p.flag.getZ()-flag.getZ())<=p.radius+radius)return "The proposed area overlaps another active proposal.";
        if(SettlementConfig.firstSettlementDistance>0&&!d.settlements.isEmpty()){Settlement first=d.settlements.get(0);if(first.dimension!=world.provider.getDimension()||first.flag.distanceSq(flag)>(double)SettlementConfig.firstSettlementDistance*SettlementConfig.firstSettlementDistance)return "The flag is too far from this world's first settlement.";}
        if(!ZonePolicy.allows(d.zones,SettlementConfig.defaultAllowed,world.provider.getDimension(),flag,radius))return "Part of this claim is inside a restricted build zone.";
        if(SettlementConfig.requireMobZone&&!hasAllowedSpawnRing(world,flag,radius))return "This settlement requires an allowed mob spawning zone in its outer spawning ring.";
        return null;
    }
    private static boolean hasAllowedSpawnRing(World world,BlockPos flag,int radius){
        int outer=radius+SettlementConfig.spawnMaxDistance,inner=radius+SettlementConfig.spawnMinDistance,width=outer*2+1,left=flag.getX()-outer,top=flag.getZ()-outer;BitSet[] rows=new BitSet[width];for(int row=0;row<width;row++)rows[row]=new BitSet(width);
        for(Zone z:data(world).zones){if(!"spawn".equals(z.type)||z.dimension!=world.provider.getDimension())continue;int x1=Math.max(0,z.minX-left),x2=Math.min(width-1,z.maxX-left),z1=Math.max(0,z.minZ-top),z2=Math.min(width-1,z.maxZ-top);if(x1>x2||z1>z2)continue;for(int row=z1;row<=z2;row++)rows[row].set(x1,x2+1,z.allowed);}
        // An explicit allowed zone must retain at least one point outside the inner square,
        // even after later restricted zones override it.
        for(int row=0;row<width;row++){if(Math.abs(row-outer)<inner)rows[row].clear(outer-inner+1,outer+inner);if(!rows[row].isEmpty())return true;}return false;
    }
    public static boolean tryFinalize(World world,Proposal p){
        if(p.expires<System.currentTimeMillis())return false;EntityPlayerMP owner=world.getMinecraftServer().getPlayerList().getPlayerByUUID(p.owner);if(owner==null||owner.dimension!=p.dimension||!p.contains(owner.dimension,owner.getPosition())||forPlayer(owner)!=null)return false;
        List<EntityPlayerMP> ready=new ArrayList<>();for(UUID id:p.supporters.keySet()){EntityPlayerMP candidate=world.getMinecraftServer().getPlayerList().getPlayerByUUID(id);if(candidate!=null&&p.contains(candidate.dimension,candidate.getPosition())&&forPlayer(candidate)==null)ready.add(candidate);}
        if(ready.size()<SettlementConfig.requiredPlayers||flagCount(owner)<SettlementConfig.requiredFlags)return false;
        long now=System.currentTimeMillis();Long warned=PROPOSAL_WARNINGS.get(p.id);if(warned!=null&&now-warned<10000)return false;String error=claimError(owner.world,p.flag,p.radius,p);if(error!=null){tell(owner,error);PROPOSAL_WARNINGS.put(p.id,now);return false;}
        SettlementData d=data(world);if(d.settlements.size()>=SettlementConfig.maxSettlements)return false;
        if(!owner.canPlayerEdit(p.flag,net.minecraft.util.EnumFacing.UP,new ItemStack(RegistryHandler.FLAG_ITEM))||owner.getServer().isBlockProtected(owner.world,p.flag,owner)||!owner.world.mayPlace(RegistryHandler.SETTLEMENT_FLAG,p.flag,false,net.minecraft.util.EnumFacing.UP,owner)){PROPOSAL_WARNINGS.put(p.id,now);tell(owner,"The flag cannot be placed here: check spawn protection, edit permissions, and entity collisions.");return false;}
        net.minecraftforge.common.util.BlockSnapshot previous=net.minecraftforge.common.util.BlockSnapshot.getBlockSnapshot(owner.world,p.flag);
        if(!owner.world.setBlockState(p.flag,RegistryHandler.SETTLEMENT_FLAG.getDefaultState(),3))return false;
        if(net.minecraftforge.event.ForgeEventFactory.onPlayerBlockPlace(owner,previous,net.minecraft.util.EnumFacing.UP,net.minecraft.util.EnumHand.MAIN_HAND).isCanceled()){previous.restore(true,true);PROPOSAL_WARNINGS.put(p.id,now);tell(owner,"Another protection rule prevented placing this flag.");return false;}
        consumeFlags(owner,SettlementConfig.requiredFlags);Settlement s=new Settlement();s.owner=p.owner;s.name=p.name;s.flag=p.flag;s.radius=p.radius;s.dimension=p.dimension;s.created=System.currentTimeMillis();for(EntityPlayerMP member:ready){s.members.add(member.getUniqueID());s.memberNames.put(member.getUniqueID(),member.getName());tell(member,"Settlement established: "+s.name+".");}d.settlements.add(s);d.proposals.remove(p);d.markDirty();return true;
    }
    public static NBTTagCompound snapshot(EntityPlayerMP player){
        NBTTagCompound n=new NBTTagCompound();n.setString("myId",player.getUniqueID().toString());n.setBoolean("operator",isOperator(player));n.setInteger("flags",flagCount(player));n.setInteger("requiredFlags",SettlementConfig.requiredFlags);n.setInteger("requiredPlayers",SettlementConfig.requiredPlayers);n.setInteger("radius",SettlementConfig.radius);n.setBoolean("defaultAllowed",SettlementConfig.defaultAllowed);n.setInteger("dimension",player.dimension);
        NBTTagList entries=new NBTTagList();Settlement own=ownedBy(player);if(own!=null)n.setString("owned",own.id.toString());
        for(Settlement s:data(player.world).settlements){NBTTagCompound e=entry(s.id,s.name,s.owner,s.dimension,s.flag,s.radius);e.setString("ownerName",s.memberNames.getOrDefault(s.owner,"Owner"));e.setBoolean("owned",s.owner.equals(player.getUniqueID()));e.setBoolean("member",isMember(s,player.getUniqueID()));e.setBoolean("accepting",s.accepting);e.setInteger("players",s.members.size());if(s.owner.equals(player.getUniqueID())){NBTTagList requests=new NBTTagList();s.requests.forEach((id,name)->{NBTTagCompound r=new NBTTagCompound();r.setString("id",id.toString());r.setString("name",name);requests.appendTag(r);});e.setTag("requests",requests);}entries.appendTag(e);}
        for(Proposal p:data(player.world).proposals){NBTTagCompound e=entry(p.id,p.name,p.owner,p.dimension,p.flag,p.radius);e.setString("ownerName",p.supporters.getOrDefault(p.owner,"Owner"));e.setBoolean("proposal",true);e.setBoolean("owned",p.owner.equals(player.getUniqueID()));e.setBoolean("member",p.supporters.containsKey(player.getUniqueID()));int count=0;NBTTagList supporters=new NBTTagList(),readyIds=new NBTTagList();for(Map.Entry<UUID,String> supporter:p.supporters.entrySet()){EntityPlayerMP candidate=player.getServer().getPlayerList().getPlayerByUUID(supporter.getKey());boolean ready=candidate!=null&&p.contains(candidate.dimension,candidate.getPosition())&&forPlayer(candidate)==null;NBTTagCompound person=new NBTTagCompound();person.setString("id",supporter.getKey().toString());person.setString("name",supporter.getValue());person.setBoolean("ready",ready);supporters.appendTag(person);if(ready){count++;readyIds.appendTag(new NBTTagString(supporter.getKey().toString()));}}e.setTag("supporters",supporters);e.setTag("readyIds",readyIds);e.setInteger("players",count);e.setInteger("requiredPlayers",SettlementConfig.requiredPlayers);e.setLong("expires",p.expires);entries.appendTag(e);}n.setTag("entries",entries);
        NBTTagList zones=new NBTTagList();for(Zone z:data(player.world).zones){NBTTagCompound e=z.write();e.setString("id",z.id.toString());zones.appendTag(e);}n.setTag("zones",zones);
        if(isOperator(player))n.setTag("config",SettlementConfig.snapshot());n.setTag("features",FeatureService.snapshot(player,SELECTION.getOrDefault(player.getUniqueID(),"")));
        String gui=GUI.remove(player.getUniqueID());if(gui!=null)n.setString("openGui",gui);String zone=GUI_ZONE.remove(player.getUniqueID());if(zone!=null)n.setString("selectedZone",zone);String selectedSettlement=GUI_SETTLEMENT.remove(player.getUniqueID());if(selectedSettlement!=null)n.setString("selectedSettlement",selectedSettlement);return n;
    }
    private static NBTTagCompound entry(UUID id,String name,UUID owner,int dimension,BlockPos pos,int radius){NBTTagCompound e=new NBTTagCompound();e.setString("id",id.toString());e.setString("name",name);e.setString("owner",owner.toString());e.setInteger("dimension",dimension);e.setInteger("x",pos.getX());e.setInteger("y",pos.getY());e.setInteger("z",pos.getZ());e.setInteger("radius",radius);return e;}
    public static void forget(UUID id){LAST_ACTION.remove(id);CORNERS.remove(id);GUI.remove(id);GUI_ZONE.remove(id);GUI_SETTLEMENT.remove(id);SELECTION.remove(id);}
}

