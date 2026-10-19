package com.mechgaming.settlements.command;

import com.mechgaming.settlements.common.SettlementService;
import com.mechgaming.settlements.data.*;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import java.util.*;

/** Text equivalents remain available on a dedicated server and for pack administration. */
public final class SettlementCommand extends CommandBase {
    @Override public String getName(){return "settlements";}
    @Override public List<String> getAliases(){return Collections.singletonList("settlement");}
    @Override public String getUsage(ICommandSender sender){return "/settlements help";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
        EntityPlayerMP p=getCommandSenderAsPlayer(sender);if(args.length==0||"help".equals(args[0])){help(sender);return;}
        String action=args[0],text="";int value=0;BlockPos pos=p.getPosition();NBTTagCompound payload=new NBTTagCompound();
        switch(action){
            case "list": for(Settlement s:SettlementService.data(p.world).settlements)sender.sendMessage(new TextComponentString(s.id+" | "+s.name+" | dimension "+s.dimension+" | flag "+s.flag+" | "+s.members.size()+" members"));for(Proposal proposal:SettlementService.data(p.world).proposals)sender.sendMessage(new TextComponentString(proposal.id+" | PROPOSAL "+proposal.name+" | "+proposal.supporters.size()+" willing"));return;
            case "propose": if(args.length<2)throw new WrongUsageException("/settlements propose <name> (stand at flag location)");text=join(args,1);pos=p.getPosition().offset(p.getHorizontalFacing());break;
            case "support":case "join": text=select(p,args,1);break;
            case "accept":case "deny": if(args.length<2)throw new WrongUsageException("/settlements "+action+" <player name or UUID> [settlement name or UUID]");if(args.length>2)payload.setString("settlement",select(p,args,2));text=applicant(p,args[1],payload);break;
            case "disband": if(args.length<2||!"confirm".equals(args[1]))throw new WrongUsageException("/settlements disband confirm [settlement UUID for operators]");payload.setBoolean("confirm",true);if(args.length>2)text=select(p,args,2);break;
            case "accepting": if(args.length<2)throw new WrongUsageException("/settlements accepting <true|false> [settlement name or UUID]");value=parseBoolean(args[1])?1:0;if(args.length>2)payload.setString("settlement",select(p,args,2));break;
            case "leave":case "cancel":case "sync":break;
            case "zone": {
                requireOp(p);if(args.length<2)throw new WrongUsageException("/settlements zone <corner|list|delete|settings|around> ...");
                switch(args[1]){
                    case "list": for(Zone z:SettlementService.data(p.world).zones)sender.sendMessage(new TextComponentString(z.id+" | "+z.name+" | "+z.type+" | "+(z.allowed?"allow":"restrict")+" | dim "+z.dimension+" | "+z.minX+","+z.minZ+" -> "+z.maxX+","+z.maxZ));return;
                    case "corner": if(args.length<4)throw new WrongUsageException("/settlements zone corner <build|spawn> <allow|restrict> [x z]");if(!"build".equals(args[2])&&!"spawn".equals(args[2]))throw new WrongUsageException("Zone type is build or spawn");if(!"allow".equals(args[3])&&!"restrict".equals(args[3]))throw new WrongUsageException("Zone mode is allow or restrict");action="zone_corner";value="spawn".equals(args[2])?1:0;text=args[3];if(args.length>=6)pos=new BlockPos(parseInt(args[4],-29999984,29999984),p.getPosition().getY(),parseInt(args[5],-29999984,29999984));break;
                    case "delete": if(args.length!=3)throw new WrongUsageException("/settlements zone delete <UUID>");action="zone_delete";text=args[2];break;
                    case "settings": if(args.length<4)throw new WrongUsageException("/settlements zone settings <UUID> <NBT compound>");action="zone_settings";text=args[2];payload=parseNbt(join(args,3));break;
                    case "around": if(args.length!=3)throw new WrongUsageException("/settlements zone around <distance from first settlement; 0 disables>");action="config";payload.setInteger("firstSettlementDistance",parseInt(args[2],0,1000000));break;
                    default:throw new WrongUsageException("Unknown zone action.");
                }break;
            }
            case "config": requireOp(p);if(args.length==1){sender.sendMessage(new TextComponentString(com.mechgaming.settlements.config.SettlementConfig.snapshot().toString()));return;}payload=parseNbt(join(args,1));break;
            case "room": if(args.length<2)throw new WrongUsageException("/settlements room <add npcId|remove>");if("add".equals(args[1])){action="feature_room_add";if(args.length<3)throw new WrongUsageException("/settlements room add <npcId>");text=args[2];}else if("remove".equals(args[1]))action="feature_room_remove";else throw new WrongUsageException("Unknown room action.");break;
            case "blueprint": if(args.length<2||!Arrays.asList("save","preview","repair").contains(args[1]))throw new WrongUsageException("/settlements blueprint <save [radius]|preview|repair>");action="feature_blueprint_"+args[1];if(args.length>2)value=parseInt(args[2],1,32);break;
            case "raid": if(args.length<2||!Arrays.asList("start","stop").contains(args[1]))throw new WrongUsageException("/settlements raid <start|stop>");action="feature_raid_"+args[1];break;
            case "war": if(args.length<2||!Arrays.asList("offer","accept","join","leave","status","surrender","start","end","decline").contains(args[1]))throw new WrongUsageException("/settlements war <offer|accept|join|leave|status|start|surrender>");action="feature_war_"+("end".equals(args[1])?"surrender":args[1]);if("offer".equals(args[1])){if(args.length<3)throw new WrongUsageException("/settlements war offer <settlement UUID or single-word name> [minutes] [lives|beds] [lives]");text=args[2];if(args.length>3)value=parseInt(args[3],1,120);if(args.length>4){if(!Arrays.asList("lives","beds").contains(args[4]))throw new WrongUsageException("War condition must be lives or beds.");payload.setString("condition",args[4]);}if(args.length>5)payload.setInteger("lives",parseInt(args[5],1,100));}break;
            default: if(!action.startsWith("feature_"))throw new WrongUsageException(getUsage(sender));if(args.length>1)text=args[1];if(args.length>2)value=parseInt(args[2]);if(args.length>3)payload=parseNbt(join(args,3));break;
        }
        SettlementService.handle(p,action,pos,value,text,payload);
    }
    private static void requireOp(EntityPlayerMP p)throws CommandException{if(!SettlementService.isOperator(p))throw new CommandException("Operator permission level 2 required.");}
    private static NBTTagCompound parseNbt(String text)throws CommandException{try{return JsonToNBT.getTagFromJson(text);}catch(NBTException ex){throw new CommandException("Invalid NBT: "+ex.getMessage());}}
    private static String join(String[] a,int start){return String.join(" ",Arrays.copyOfRange(a,start,a.length));}
    private static String select(EntityPlayerMP p,String[] a,int start)throws CommandException{if(a.length<=start)throw new WrongUsageException("Provide a settlement name or UUID from /settlements list.");String text=join(a,start);for(Settlement s:SettlementService.data(p.world).settlements)if(s.id.toString().equals(text)||s.name.equalsIgnoreCase(text))return s.id.toString();for(Proposal s:SettlementService.data(p.world).proposals)if(s.id.toString().equals(text)||s.name.equalsIgnoreCase(text))return s.id.toString();throw new CommandException("No matching settlement or proposal.");}
    private static String applicant(EntityPlayerMP p,String text,NBTTagCompound payload)throws CommandException{Settlement s=SettlementService.targetOwned(p,payload);if(s!=null)for(Map.Entry<UUID,String> entry:s.requests.entrySet())if(entry.getKey().toString().equals(text)||entry.getValue().equalsIgnoreCase(text))return entry.getKey().toString();throw new CommandException("No pending request from that player in the selected settlement.");}
    private static void help(ICommandSender sender){String[] help={"/settlements list | propose <name> | support <name/UUID> | cancel","/settlements join <name/UUID> | accept <player> | deny <player> | accepting <true|false> | leave","/settlements disband confirm (irreversible; consumes the existing flag)","/settlements zone corner <build|spawn> <allow|restrict> [x z] (select twice)","/settlements zone list | delete <UUID> | settings <UUID> {allowed:0b,preventBuild:1b,decay:0b,blacklist:\"minecraft:tnt\"}","/settlements zone around <distance> | config {requiredPlayers:2,defaultAllowed:0b}","/settlements room add <npcId> | room remove (stand inside the room)","/settlements blueprint save [radius] | blueprint preview | blueprint repair","/settlements raid start | raid stop","/settlements war offer <settlement> [minutes] [lives|beds] [lives] | war accept | war decline | war join | war leave | war start | war status | war surrender"};for(String line:help)sender.sendMessage(new TextComponentString(line));}
    @Override public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos target){if(args.length==1)return getListOfStringsMatchingLastWord(args,"help","list","propose","support","cancel","join","accept","deny","accepting","leave","disband","zone","config","room","blueprint","raid","war");if(args.length==2&&"war".equals(args[0]))return getListOfStringsMatchingLastWord(args,"offer","accept","join","leave","start","status","surrender","decline");if(args.length==2&&"zone".equals(args[0]))return getListOfStringsMatchingLastWord(args,"corner","list","settings","delete","around");return Collections.emptyList();}
}


