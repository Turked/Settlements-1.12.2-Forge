package com.mechgaming.settlements.features;

import com.mechgaming.settlements.data.Settlement;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.BlockEvent;
import java.util.*;

/** Only vanilla bed orientation and dye color are captured. No arbitrary tile data is copied. */
final class BedRestorationService {
    private BedRestorationService() { }

    static void describe(World world, BlockPos pos, NBTTagCompound n) {
        IBlockState state = world.getBlockState(pos); n.setLong("pos", pos.toLong()); n.setInteger("meta", Blocks.BED.getMetaFromState(state));
        TileEntity tile = world.getTileEntity(pos); n.setInteger("color", tile instanceof TileEntityBed ? ((TileEntityBed) tile).getColor().getMetadata() : EnumDyeColor.RED.getMetadata());
    }

    static void queue(WorldServer world, Settlement s, NBTTagList halves) {
        FeatureWorldData data = FeatureWorldData.get(world);
        for (int i = 0; i < halves.tagCount(); i++) {
            NBTTagCompound head = halves.getCompoundTagAt(i); IBlockState state = Blocks.BED.getStateFromMeta(head.getInteger("meta"));
            if (state.getValue(BlockBed.PART) != BlockBed.EnumPartType.HEAD) continue;
            BlockPos headPos = BlockPos.fromLong(head.getLong("pos")), footPos = headPos.offset(state.getValue(BlockBed.FACING).getOpposite());
            NBTTagCompound foot = null;
            for (int j = 0; j < halves.tagCount(); j++) if (halves.getCompoundTagAt(j).getLong("pos") == footPos.toLong()) { foot = halves.getCompoundTagAt(j); break; }
            if (foot == null) continue;
            NBTTagCompound pair = new NBTTagCompound(); pair.setUniqueId("settlement", s.id); pair.setInteger("dimension", s.dimension);
            pair.setLong("head", headPos.toLong()); pair.setLong("foot", footPos.toLong()); pair.setInteger("headMeta", head.getInteger("meta") & ~4); pair.setInteger("footMeta", foot.getInteger("meta") & ~4); pair.setInteger("color", head.getInteger("color"));
            data.bedRestorations.appendTag(pair);
        }
        data.markDirty();
    }

    static void tick(MinecraftServer server) {
        FeatureWorldData data = FeatureWorldData.get(server.getWorld(0)); int budget = 16;
        for (int i = data.bedRestorations.tagCount() - 1; i >= 0 && budget > 0; i--) {
            NBTTagCompound pair = data.bedRestorations.getCompoundTagAt(i); WorldServer world = server.getWorld(pair.getInteger("dimension"));
            if (world == null) continue;
            BlockPos head = BlockPos.fromLong(pair.getLong("head")), foot = BlockPos.fromLong(pair.getLong("foot"));
            if (!world.isBlockLoaded(head) || !world.isBlockLoaded(foot)) continue;
            Settlement s = FeatureService.settlement(world, pair.getUniqueId("settlement"));
            if (s == null || !s.contains(s.dimension, head) || !s.contains(s.dimension, foot)) { data.bedRestorations.removeTag(i); data.markDirty(); continue; }
            IBlockState headState = Blocks.BED.getStateFromMeta(pair.getInteger("headMeta")), footState = Blocks.BED.getStateFromMeta(pair.getInteger("footMeta"));
            if ((!world.isAirBlock(head) && !same(world.getBlockState(head), headState)) || (!world.isAirBlock(foot) && !same(world.getBlockState(foot), footState))) { data.bedRestorations.removeTag(i); data.markDirty(); continue; }
            // Ordinary blueprint restoration runs first; unsupported floors remain pending.
            if (!world.getBlockState(head.down()).isTopSolid() || !world.getBlockState(foot.down()).isTopSolid()) continue;
            world.setBlockState(foot, footState, 2); world.setBlockState(head, headState, 2);
            for (BlockPos p : Arrays.asList(head, foot)) {
                TileEntity tile = world.getTileEntity(p);
                if (tile instanceof TileEntityBed) { ((TileEntityBed) tile).setColor(EnumDyeColor.byMetadata(pair.getInteger("color"))); tile.markDirty(); world.notifyBlockUpdate(p, world.getBlockState(p), world.getBlockState(p), 3); }
            }
            world.notifyNeighborsOfStateChange(foot, Blocks.BED, false); world.notifyNeighborsOfStateChange(head, Blocks.BED, false);
            data.bedRestorations.removeTag(i); budget--; data.markDirty();
        }
    }
    private static boolean same(IBlockState a, IBlockState b) { return a.getBlock() == Blocks.BED && a.getValue(BlockBed.PART) == b.getValue(BlockBed.PART) && a.getValue(BlockBed.FACING) == b.getValue(BlockBed.FACING); }

    /** Replace vanilla harvesting for tracked bed objectives, removing both halves without item drops. */
    static void breakBed(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() != Blocks.BED || !tracked(event.getWorld(), event.getPos())) return;
        event.setCanceled(true); event.setExpToDrop(0);
        if (!(event.getPlayer() instanceof EntityPlayerMP) || !WarService.isParticipantAt((EntityPlayerMP) event.getPlayer(), event.getPos())) return;
        BlockPos pos = event.getPos(); IBlockState state = event.getState();
        BlockPos other = pos.offset(state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD ? state.getValue(BlockBed.FACING).getOpposite() : state.getValue(BlockBed.FACING));
        if (!event.getWorld().isBlockLoaded(other)) return;
        event.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        if (event.getWorld().getBlockState(other).getBlock() == Blocks.BED) event.getWorld().setBlockState(other, Blocks.AIR.getDefaultState(), 2);
        event.getWorld().notifyNeighborsOfStateChange(pos, Blocks.BED, false); event.getWorld().notifyNeighborsOfStateChange(other, Blocks.BED, false);
    }

    static boolean tracked(World world, BlockPos pos) {
        FeatureWorldData data = FeatureWorldData.get(world);
        for (int i = 0; i < data.wars.tagCount(); i++) {
            NBTTagCompound w = data.wars.getCompoundTagAt(i);
            if (!w.getString("status").equals("active") || w.getInteger("dimension") != world.provider.getDimension()) continue;
            for (String side : Arrays.asList("A", "B")) {
                NBTTagList list = w.getTagList("beds" + side, 10);
                for (int b = 0; b < list.tagCount(); b++) if (list.getCompoundTagAt(b).getLong("pos") == pos.toLong()) return true;
            }
        }
        for (int i = 0; i < data.bedRestorations.tagCount(); i++) { NBTTagCompound pair = data.bedRestorations.getCompoundTagAt(i); if (pair.getInteger("dimension") == world.provider.getDimension() && (pair.getLong("head") == pos.toLong() || pair.getLong("foot") == pos.toLong())) return true; }
        return false;
    }
}
