package com.pathtracer;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class PathTracerConfig {

    public static final int  MIN_WALK_THRESHOLD_MIN = 1;
    public static final int  MIN_WALK_THRESHOLD_MAX = 60;
    public static final int  MAX_WALK_COUNT_MIN     = 5;
    public static final int  MAX_WALK_COUNT_MAX     = 300;
    public static final int  RENDER_RADIUS_MIN      = 16;
    public static final int  RENDER_RADIUS_MAX      = 256;
    public static final int  MAX_AGE_DAYS_MIN       = 1;
    public static final int  MAX_AGE_DAYS_MAX       = 365;
    public static final int  CLEAR_RADIUS_MIN       = 8;
    public static final int  CLEAR_RADIUS_MAX       = 256;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static int  minWalkThreshold = WalkDataStore.MIN_WALK_THRESHOLD;
    public static int  maxWalkCount     = WalkDataStore.MAX_WALK_COUNT;
    public static int  renderRadius     = WalkDataStore.RENDER_RADIUS;
    public static int  maxAgeDays       = (int) WalkDataStore.MAX_AGE_DAYS;

    public static final Set<String> DEFAULT_IGNORED_BLOCKS = new HashSet<>(Arrays.asList(
        // Grasses & ferns
        "minecraft:grass", "minecraft:short_grass",
        "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
        "minecraft:short_dry_grass",
        // Single-height flowers
        "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
        "minecraft:allium", "minecraft:azure_bluet",
        "minecraft:red_tulip", "minecraft:orange_tulip",
        "minecraft:white_tulip", "minecraft:pink_tulip",
        "minecraft:oxeye_daisy", "minecraft:cornflower",
        "minecraft:lily_of_the_valley", "minecraft:torchflower",
        "minecraft:wildflowers",
        // Double-height flowers & plants
        "minecraft:sunflower", "minecraft:lilac",
        "minecraft:rose_bush", "minecraft:peony",
        "minecraft:pitcher_plant", "minecraft:firefly_bush",
        // Saplings
        "minecraft:oak_sapling", "minecraft:spruce_sapling",
        "minecraft:birch_sapling", "minecraft:jungle_sapling",
        "minecraft:acacia_sapling", "minecraft:dark_oak_sapling",
        "minecraft:cherry_sapling", "minecraft:pale_oak_sapling",
        "minecraft:mangrove_propagule", "minecraft:bamboo_sapling",
        // Vines & climbing plants
        "minecraft:vine", "minecraft:cave_vines", "minecraft:cave_vines_plant",
        // Dripleaf
        "minecraft:small_dripleaf",
        "minecraft:big_dripleaf", "minecraft:big_dripleaf_stem",
        // Other plants
        "minecraft:dead_bush", "minecraft:bush",
        "minecraft:sugar_cane", "minecraft:pitcher_pod",
        // Water plants
        "minecraft:seagrass", "minecraft:tall_seagrass",
        "minecraft:kelp", "minecraft:kelp_plant",
        // Ground covers
        "minecraft:snow", "minecraft:moss_carpet", "minecraft:pale_moss_carpet",
        "minecraft:pink_petals", "minecraft:leaf_litter", "minecraft:glow_lichen"
    ));
    public static Set<String> ignoredBlocks = new HashSet<>(DEFAULT_IGNORED_BLOCKS);
    public static int     clearRadius         = WalkDataStore.CLEAR_RADIUS;
    public static boolean trackOtherPlayers  = WalkDataStore.TRACK_OTHER_PLAYERS;

    public static void load() {
        Path file = configFile();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                JsonObject obj = GSON.fromJson(r, JsonObject.class);
                if (obj != null) {
                    if (obj.has("minWalkThreshold")) minWalkThreshold = obj.get("minWalkThreshold").getAsInt();
                    if (obj.has("maxWalkCount"))     maxWalkCount     = obj.get("maxWalkCount").getAsInt();
                    if (obj.has("renderRadius"))     renderRadius     = obj.get("renderRadius").getAsInt();
                    if (obj.has("maxAgeDays"))       maxAgeDays       = obj.get("maxAgeDays").getAsInt();
                    if (obj.has("clearRadius"))       clearRadius        = obj.get("clearRadius").getAsInt();
                    if (obj.has("trackOtherPlayers")) trackOtherPlayers  = obj.get("trackOtherPlayers").getAsBoolean();
                    if (obj.has("ignoredBlocks")) {
                        Set<String> blocks = new HashSet<>();
                        for (JsonElement e : obj.getAsJsonArray("ignoredBlocks"))
                            blocks.add(e.getAsString());
                        ignoredBlocks = blocks;
                    }
                }
            } catch (Exception e) {
                System.err.println("[PathTracer] Failed to load config: " + e.getMessage());
            }
        }
        applyToStore();
    }

    public static void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("minWalkThreshold", minWalkThreshold);
        obj.addProperty("maxWalkCount",     maxWalkCount);
        obj.addProperty("renderRadius",     renderRadius);
        obj.addProperty("maxAgeDays",       maxAgeDays);
        obj.addProperty("clearRadius",        clearRadius);
        obj.addProperty("trackOtherPlayers",  trackOtherPlayers);
        JsonArray arr = new JsonArray();
        ignoredBlocks.stream().sorted().forEach(arr::add);
        obj.add("ignoredBlocks", arr);

        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(obj, w);
            }
        } catch (IOException e) {
            System.err.println("[PathTracer] Failed to save config: " + e.getMessage());
        }
        applyToStore();
    }

    private static void applyToStore() {
        WalkDataStore.MIN_WALK_THRESHOLD = minWalkThreshold;
        WalkDataStore.MAX_WALK_COUNT     = maxWalkCount;
        WalkDataStore.RENDER_RADIUS      = renderRadius;
        WalkDataStore.MAX_AGE_DAYS       = maxAgeDays;
        WalkDataStore.IGNORED_BLOCKS     = new HashSet<>(ignoredBlocks);
        WalkDataStore.CLEAR_RADIUS        = clearRadius;
        WalkDataStore.TRACK_OTHER_PLAYERS = trackOtherPlayers;
    }

    private static Path configFile() {
        // Mojang: gameDirectory (was runDirectory in Yarn)
        return Minecraft.getInstance()
                .gameDirectory.toPath()
                .resolve("config")
                .resolve("path-tracer.json");
    }
}
