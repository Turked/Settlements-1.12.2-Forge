package com.mechgaming.settlements.verification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
                require(!model.has("elements"), "No authored geometry");
                require(model.get("parent").getAsString().startsWith("minecraft:"), "Models reuse existing vanilla parents");
            }
            if (path.toString().contains("recipes")) {
                JsonObject recipe = json.getAsJsonObject();
                require(recipe.get("type").getAsString().equals("minecraft:crafting_shaped"), "Legacy recipe format");
                require(recipe.getAsJsonObject("result").get("item").getAsString().equals("settlements:settlement_flag"), "Recipe registry ID");
            }
        }
        require(parsed >= 6, "Generated JSON set present");
        ClassLoader loader = ResourceVerification.class.getClassLoader();
        require(loader.getResource("assets/minecraft/models/block/white_wool.json") != null, "Vanilla wool model exists on actual Minecraft classpath");
        require(loader.getResource("assets/minecraft/textures/items/blaze_rod.png") != null, "Vanilla tool texture exists on actual Minecraft classpath");
        System.out.println("Validated " + parsed + " generated JSON documents, metadata and actual vanilla asset references.");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
