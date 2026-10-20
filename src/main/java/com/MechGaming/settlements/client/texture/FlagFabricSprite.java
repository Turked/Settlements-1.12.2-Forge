package com.MechGaming.settlements.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Function;

/** Keeps the supplied PNG unchanged and prepares compatible atlas pixels at runtime. */
@SideOnly(Side.CLIENT)
public final class FlagFabricSprite extends TextureAtlasSprite {
    public static final ResourceLocation ID = new ResourceLocation("settlements", "blocks/flag_fabric");
    private boolean reserveAtlasSlot = true;

    public FlagFabricSprite() { super(ID.toString()); }

    @Override public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) { return true; }

    @Override public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> getter) {
        try (IResource resource = manager.getResource(location)) {
            BufferedImage image = ImageIO.read(resource.getInputStream());
            if (image == null) throw new IOException("Unsupported fabric image");
            if (image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 4096 || image.getHeight() > 4096) {
                throw new IOException("Fabric dimensions must be between 1 and 4096 pixels");
            }
            int sourceWidth = image.getWidth(), sourceHeight = image.getHeight();
            setIconWidth(nextPowerOfTwo(sourceWidth));
            setIconHeight(nextPowerOfTwo(sourceHeight));
            int[] sourcePixels = image.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);
            int[] atlasPixels = new int[width * height];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                atlasPixels[y * width + x] = sourcePixels[(y * sourceHeight / height) * sourceWidth + x * sourceWidth / width];
            }
            reserveAtlasSlot = true;
            // Vanilla's mipmap generator expects slots for its requested levels; it fills
            // these null entries normally once the real client's OpenGL context exists.
            int[][] frame = new int[17][];
            frame[0] = atlasPixels;
            setFramesTextureData(Collections.<int[][]>singletonList(frame));
            // In Forge 1.12 TextureMap, false continues to mipmap preparation and stitching.
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load supplied flag fabric: " + location, exception);
        }
    }

    // The 1.12 stitcher can rotate rectangular reservations but its ordinary model UVs
    // cannot rotate with them. Reserve a square slot, without padding the image itself.
    @Override public int getIconWidth() { return reserveAtlasSlot ? Math.max(width, height) : width; }
    @Override public int getIconHeight() { return reserveAtlasSlot ? Math.max(width, height) : height; }

    @Override public void initSprite(int atlasWidth, int atlasHeight, int originX, int originY, boolean rotated) {
        reserveAtlasSlot = false;
        super.initSprite(atlasWidth, atlasHeight, originX, originY, false);
    }

    private static int nextPowerOfTwo(int size) {
        int result = 1;
        while (result < size) result <<= 1;
        return result;
    }
}
