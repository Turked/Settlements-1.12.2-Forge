package com.mechgaming.settlements.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import java.util.HashSet;
import java.util.Set;

/** Independent boundary overlay, compatible with Xaero being present or absent. */
@SideOnly(Side.CLIENT)
public final class BoundaryRenderer {
    private static final int MAX_REGIONS = 256;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ClientState state = ClientState.INSTANCE;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private double range;

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (mc.world == null || mc.player == null || mc.gameSettings.hideGUI) return;
        boolean showRegions = state.boundaries || state.planner;
        boolean highlightRequest = mc.world.getTotalWorldTime() < state.highlightUntil;
        if (!showRegions && !highlightRequest) return;
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) return;
        float partial = event.getPartialTicks();
        cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        range = Math.min(192, Math.max(32, mc.gameSettings.renderDistanceChunks * 16));
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(1.5F);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        NBTTagList zones = showRegions ? state.zones() : new NBTTagList();
        int count = 0;
        for (int i = 0; i < zones.tagCount() && count < MAX_REGIONS / 2; i++) {
            NBTTagCompound z = zones.getCompoundTagAt(i);
            if (z.getInteger("dimension") != mc.player.dimension) continue;
            double minX = z.getInteger("minX"), maxX = (double) z.getInteger("maxX") + 1;
            double minZ = z.getInteger("minZ"), maxZ = (double) z.getInteger("maxZ") + 1;
            if (!near(minX, minZ, maxX, maxZ)) continue;
            boolean allowed = z.getBoolean("allowed");
            box(buffer, minX, cameraY - 1, minZ, maxX, cameraY + 3, maxZ,
                    allowed ? 0.25F : 1, allowed ? 0.9F : 0.2F, 0.25F, 0.7F);
            count++;
        }
        NBTTagList entries = showRegions ? state.entries() : new NBTTagList();
        Set<String> readyPlayers = new HashSet<>();
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound e = entries.getCompoundTagAt(i);
            if (e.getInteger("dimension") != mc.player.dimension) continue;
            if (e.getBoolean("proposal") && (state.boundaries || state.slot == 1)) {
                NBTTagList supporters = e.getTagList("supporters", 10);
                for (int supporter = 0; supporter < supporters.tagCount(); supporter++) {
                    NBTTagCompound person = supporters.getCompoundTagAt(supporter);
                    if (person.getBoolean("ready")) readyPlayers.add(person.getString("id"));
                }
            }
            if (count >= MAX_REGIONS) continue;
            int radius = e.getInteger("radius");
            double minX = (double) e.getInteger("x") - radius, maxX = (double) e.getInteger("x") + radius + 1;
            double minZ = (double) e.getInteger("z") - radius, maxZ = (double) e.getInteger("z") + radius + 1;
            if (!near(minX, minZ, maxX, maxZ)) continue;
            boolean proposal = e.getBoolean("proposal");
            box(buffer, minX, cameraY - 0.8, minZ, maxX, cameraY + 3.2, maxZ,
                    proposal ? 1 : 0.7F, proposal ? 0.75F : 0.7F, proposal ? 0.2F : 0.7F, 0.85F);
            count++;
        }
        for (EntityPlayer player : mc.world.playerEntities) {
            if (!(readyPlayers.contains(player.getUniqueID().toString()) || player == mc.player && highlightRequest)
                    || player.getDistanceSq(mc.player) > range * range) continue;
            AxisAlignedBB bounds = player.getEntityBoundingBox().offset(
                    player.lastTickPosX + (player.posX - player.lastTickPosX) * partial - player.posX,
                    player.lastTickPosY + (player.posY - player.lastTickPosY) * partial - player.posY,
                    player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial - player.posZ).grow(0.05);
            box(buffer, bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ, 0.2F, 1, 0.35F, 0.9F);
        }
        NBTTagList missing = showRegions ? state.features().getTagList("blueprintMissing", 10) : new NBTTagList();
        for (int i = 0; i < missing.tagCount() && i < 256; i++) {
            BlockPos pos = BlockPos.fromLong(missing.getCompoundTagAt(i).getLong("pos"));
            if (pos.distanceSq(mc.player.getPosition()) > range * range) continue;
            box(buffer, pos.getX() + 0.02, pos.getY() + 0.02, pos.getZ() + 0.02,
                    pos.getX() + 0.98, pos.getY() + 0.98, pos.getZ() + 0.98, 0.3F, 0.85F, 1, 0.65F);
        }
        Tessellator.getInstance().draw();
        GL11.glLineWidth(1);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private boolean near(double minX, double minZ, double maxX, double maxZ) {
        double nearestX = Math.max(minX, Math.min(maxX, cameraX));
        double nearestZ = Math.max(minZ, Math.min(maxZ, cameraZ));
        return (nearestX - cameraX) * (nearestX - cameraX) + (nearestZ - cameraZ) * (nearestZ - cameraZ) <= range * range;
    }

    private void box(BufferBuilder b, double x1, double y1, double z1, double x2, double y2, double z2,
                     float red, float green, float blue, float alpha) {
        line(b, x1, y1, z1, x2, y1, z1, red, green, blue, alpha);
        line(b, x2, y1, z1, x2, y1, z2, red, green, blue, alpha);
        line(b, x2, y1, z2, x1, y1, z2, red, green, blue, alpha);
        line(b, x1, y1, z2, x1, y1, z1, red, green, blue, alpha);
        line(b, x1, y2, z1, x2, y2, z1, red, green, blue, alpha);
        line(b, x2, y2, z1, x2, y2, z2, red, green, blue, alpha);
        line(b, x2, y2, z2, x1, y2, z2, red, green, blue, alpha);
        line(b, x1, y2, z2, x1, y2, z1, red, green, blue, alpha);
        line(b, x1, y1, z1, x1, y2, z1, red, green, blue, alpha);
        line(b, x2, y1, z1, x2, y2, z1, red, green, blue, alpha);
        line(b, x2, y1, z2, x2, y2, z2, red, green, blue, alpha);
        line(b, x1, y1, z2, x1, y2, z2, red, green, blue, alpha);
    }

    private void line(BufferBuilder b, double x1, double y1, double z1, double x2, double y2, double z2,
                      float red, float green, float blue, float alpha) {
        b.pos(x1 - cameraX, y1 - cameraY, z1 - cameraZ).color(red, green, blue, alpha).endVertex();
        b.pos(x2 - cameraX, y2 - cameraY, z2 - cameraZ).color(red, green, blue, alpha).endVertex();
    }
}
