package com.MechGaming.settlements.verification;

import com.MechGaming.settlements.client.texture.FlagFabricSprite;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Exercises the actual 1.12 sprite loader and stitcher without creating an OpenGL context. */
public final class FlagTextureVerification {
    private FlagTextureVerification() { }

    public static void run() throws Exception {
        ResourceLocation expected = new ResourceLocation("settlements", "textures/blocks/flag_fabric.png");
        byte[] bytes = classpathBytes("assets/settlements/textures/blocks/flag_fabric.png");
        BufferedImage supplied = ImageIO.read(new ByteArrayInputStream(bytes));
        require(supplied != null && supplied.getWidth() == 20 && supplied.getHeight() == 40, "Actual supplied fabric is 20x40");
        FabricResources resources = new FabricResources(expected, bytes);
        FlagFabricSprite sprite = new FlagFabricSprite();

        // Use TextureMap's actual resource resolver; do not duplicate its filename rules.
        TextureMap map = new TextureMap("textures");
        require(map.setTextureEntry(sprite), "Custom sprite registers before model textures");
        require(map.registerSprite(FlagFabricSprite.ID) == sprite, "Model texture registration retains the custom loader");
        Method resolve = null;
        for (Method candidate : TextureMap.class.getDeclaredMethods()) {
            if (candidate.getReturnType() == ResourceLocation.class && candidate.getParameterTypes().length == 1
                    && candidate.getParameterTypes()[0] == TextureAtlasSprite.class) { resolve = candidate; break; }
        }
        require(resolve != null, "Minecraft texture resource resolver exists");
        resolve.setAccessible(true);
        ResourceLocation resolved = (ResourceLocation) resolve.invoke(map, sprite);
        require(expected.equals(resolved), "Actual TextureMap resolves the supplied PNG path");
        require(sprite.hasCustomLoader(resources, resolved), "Fabric selects the custom rectangular loader");
        require(!sprite.load(resources, resolved, ignored -> null), "False load result continues Forge stitching");
        require(resources.closed, "Custom loader closes the resource stream");
        require(sprite.getIconWidth() == 64 && sprite.getIconHeight() == 64, "Square reservation prevents rectangular atlas rotation");

        int[] pixels = sprite.getFrameTextureData(0)[0];
        require(pixels.length == 32 * 64, "Atlas buffer is a complete 32x64 image");
        for (int y = 0; y < 64; y++) for (int x = 0; x < 32; x++) {
            require(pixels[y * 32 + x] == supplied.getRGB(x * 20 / 32, y * 40 / 64), "Runtime preparation only repeats supplied nearest-neighbor colors");
        }
        for (int level = 0; level <= 4; level++) {
            require((32 >> level) * (1 << level) == 32 && (64 >> level) * (1 << level) == 64,
                    "Sprite dimensions stay aligned through Minecraft's maximum normal mip level");
        }

        BufferedImage oak = ImageIO.read(new ByteArrayInputStream(classpathBytes("assets/minecraft/textures/blocks/log_oak.png")));
        TextureAtlasSprite neighbor = new SquareSprite(oak.getWidth(), oak.getHeight());
        Stitcher stitcher = new Stitcher(512, 512, 0, 4);
        stitcher.addSprite(sprite);
        stitcher.addSprite(neighbor);
        stitcher.doStitch();
        List<TextureAtlasSprite> stitched = stitcher.getStichSlots();
        require(stitched.size() == 2 && stitched.contains(sprite) && stitched.contains(neighbor), "Actual Minecraft stitcher places both sprites");
        require(sprite.getIconWidth() == 32 && sprite.getIconHeight() == 64, "Stitched sprite exposes the actual rectangular upload dimensions");
        require(close((sprite.getMaxU() - sprite.getMinU()) * stitcher.getCurrentWidth(), 32), "U extent covers all 32 prepared pixels");
        require(close((sprite.getMaxV() - sprite.getMinV()) * stitcher.getCurrentHeight(), 64), "V extent covers all 64 prepared pixels");
        require(close(sprite.getInterpolatedU(16), sprite.getMaxU()) && close(sprite.getInterpolatedV(16), sprite.getMaxV()),
                "Original normalized model UVs still span the full fabric");
        require(!overlaps(sprite, neighbor), "Fabric upload rectangle does not overlap the adjacent vanilla sprite");
        require(sprite.getOriginX() % 16 == 0 && sprite.getOriginY() % 16 == 0, "Sprite origins align with mip level four");
        System.out.println("Validated supplied flag PNG, nearest-neighbor runtime preparation and actual Minecraft 1.12 atlas placement; GPU upload/mipmap generation requires the game client.");
    }

    private static boolean overlaps(TextureAtlasSprite a, TextureAtlasSprite b) {
        return a.getOriginX() < b.getOriginX() + b.getIconWidth() && a.getOriginX() + a.getIconWidth() > b.getOriginX()
                && a.getOriginY() < b.getOriginY() + b.getIconHeight() && a.getOriginY() + a.getIconHeight() > b.getOriginY();
    }
    private static boolean close(float a, float b) { return Math.abs(a - b) < .0001F; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static byte[] classpathBytes(String path) throws IOException {
        try (InputStream input = FlagTextureVerification.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new FileNotFoundException(path);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
    private static final class SquareSprite extends TextureAtlasSprite {
        SquareSprite(int width, int height) { super("minecraft:blocks/log_oak"); setIconWidth(width); setIconHeight(height); }
    }
    private static final class FabricResources implements IResourceManager {
        private final ResourceLocation expected;
        private final byte[] bytes;
        private boolean closed;
        FabricResources(ResourceLocation expected, byte[] bytes) { this.expected = expected; this.bytes = bytes; }
        @Override public Set<String> getResourceDomains() { return Collections.singleton("settlements"); }
        @Override public List<IResource> getAllResources(ResourceLocation location) throws IOException { return Collections.singletonList(getResource(location)); }
        @Override public IResource getResource(ResourceLocation location) throws IOException {
            if (!expected.equals(location)) throw new FileNotFoundException(location.toString());
            final ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            return new IResource() {
                @Override public ResourceLocation getResourceLocation() { return expected; }
                @Override public InputStream getInputStream() { return stream; }
                @Override public boolean hasMetadata() { return false; }
                @Override public <T extends IMetadataSection> T getMetadata(String sectionName) { return null; }
                @Override public String getResourcePackName() { return "Supplied flag verification"; }
                @Override public void close() throws IOException { stream.close(); closed = true; }
            };
        }
    }
}
