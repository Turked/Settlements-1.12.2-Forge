package com.MechGaming.settlements.verification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.block.model.ModelBlock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ResourceVerification {
    private ResourceVerification() { }
    public static void run() throws Exception {
        Path root = Paths.get("src/generated/resources");
        List<Path> paths;
        try (Stream<Path> files = Files.walk(root)) { paths = files.filter(Files::isRegularFile).collect(Collectors.toList()); }
        int parsed = 0;
        for (Path path : paths) {
            String name = path.getFileName().toString();
            if (name.endsWith(".png") || name.endsWith(".obj") || name.endsWith(".gltf")) throw new AssertionError("Generated artwork is forbidden");
            if (!name.endsWith(".json") && !name.equals("pack.mcmeta") && !name.equals("mcmod.info")) continue;
            JsonElement json = new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)); parsed++;
            if (name.equals("mcmod.info")) {
                JsonObject metadata = json.getAsJsonArray().get(0).getAsJsonObject();
                require(metadata.get("modid").getAsString().equals("settlements"), "Mod ID");
                require(metadata.get("name").getAsString().equals("Settlements"), "Mod name");
                require(metadata.getAsJsonArray("authorList").get(0).getAsString().equals("MechGaming"), "Mod author");
                require(metadata.get("mcversion").getAsString().equals("1.12.2"), "Minecraft version");
            }
            if (path.toString().contains("models")) {
                JsonObject model = json.getAsJsonObject();
                if (path.endsWith(Paths.get("assets", "settlements", "models", "block", "settlement_flag.json"))) {
                    verifyImportedFlag(model);
                } else if (path.getParent().getFileName().toString().equals("item")) {
                    require(!model.has("elements"), "Item models inherit their artwork");
                    String parent = model.get("parent").getAsString();
                    require(parent.startsWith("minecraft:") || parent.equals("settlements:block/settlement_flag"), "Item parent resolves to supplied or vanilla artwork");
                }
            }
            if (path.toString().contains("recipes")) {
                JsonObject recipe = json.getAsJsonObject();
                require(recipe.get("type").getAsString().equals("minecraft:crafting_shaped"), "Legacy recipe format");
                require(recipe.getAsJsonObject("result").get("item").getAsString().equals("settlements:settlement_flag"), "Recipe registry ID");
            }
        }
        require(parsed >= 6, "Generated JSON set present");
        ClassLoader loader = ResourceVerification.class.getClassLoader();
        require(loader.getResource("assets/minecraft/textures/blocks/log_oak.png") != null, "Vanilla oak side texture exists on actual Minecraft classpath");
        require(loader.getResource("assets/settlements/textures/blocks/flag_fabric.png") != null, "Supplied flag fabric is packaged");
        require(loader.getResource("assets/minecraft/textures/items/blaze_rod.png") != null, "Vanilla tool texture exists on actual Minecraft classpath");
        System.out.println("Validated " + parsed + " generated JSON documents, metadata and actual vanilla asset references.");
        FlagTextureVerification.run();
    }
    private static void verifyImportedFlag(JsonObject model) throws Exception {
        JsonObject source = new JsonParser().parse(new String(Files.readAllBytes(Paths.get("src/datagen/resources/models/flag.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        require(model.get("elements").equals(source.get("elements")), "Flag geometry, UVs and face rotations match the artist export");
        source.getAsJsonObject("display").remove("on_shelf");
        require(model.get("display").equals(source.get("display")), "All supported artist display transforms are preserved");
        require(!model.has("parent"), "Flag has no unresolved/self-referencing parent");
        ModelBlock legacyModel = ModelBlock.deserialize(model.toString());
        require(legacyModel.getElements().size() == 2, "Actual Minecraft 1.12 model parser accepts the two supplied elements");
        require(legacyModel.resolveTextureName("1").equals("minecraft:blocks/log_oak"), "Pole uses the vanilla oak side");
        require(legacyModel.resolveTextureName("2").equals("settlements:blocks/flag_fabric"), "Cloth uses supplied red fabric");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
