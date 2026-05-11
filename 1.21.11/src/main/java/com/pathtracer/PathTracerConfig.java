package com.pathtracer;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Persists Path Tracer settings to {@code config/path-tracer.json}.
 *
 * On load, values are written into {@link WalkDataStore}'s mutable statics so
 * the rest of the mod just reads those constants as before — no other classes
 * need to change.
 */
@Environment(EnvType.CLIENT)
public class PathTracerConfig {

    // ── Hard limits used by the config screen ────────────────────────────────
    // Ranges are 3× the pre-footprint values to match the new 3×3 step scale.
    public static final int  MIN_WALK_THRESHOLD_MIN = 1;
    public static final int  MIN_WALK_THRESHOLD_MAX = 60;
    public static final int  MAX_WALK_COUNT_MIN     = 5;
    public static final int  MAX_WALK_COUNT_MAX     = 300;
    public static final int  RENDER_RADIUS_MIN      = 16;
    public static final int  RENDER_RADIUS_MAX      = 256;
    public static final int  MAX_AGE_DAYS_MIN       = 1;
    public static final int  MAX_AGE_DAYS_MAX       = 365;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Current values — kept in sync with WalkDataStore statics
    public static int  minWalkThreshold = WalkDataStore.MIN_WALK_THRESHOLD;
    public static int  maxWalkCount     = WalkDataStore.MAX_WALK_COUNT;
    public static int  renderRadius     = WalkDataStore.RENDER_RADIUS;
    public static int  maxAgeDays       = (int) WalkDataStore.MAX_AGE_DAYS;

    // Default set of block IDs that are never recorded.
    // Both "minecraft:grass" and "minecraft:short_grass" are included so the
    // list works across versions where the block was renamed.
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

    // ── Public API ────────────────────────────────────────────────────────────

    /** Load config from disk and push values into WalkDataStore. */
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

    /** Save current values to disk. */
    public static void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("minWalkThreshold", minWalkThreshold);
        obj.addProperty("maxWalkCount",     maxWalkCount);
        obj.addProperty("renderRadius",     renderRadius);
        obj.addProperty("maxAgeDays",       maxAgeDays);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Push config values into WalkDataStore's mutable statics. */
    private static void applyToStore() {
        WalkDataStore.MIN_WALK_THRESHOLD = minWalkThreshold;
        WalkDataStore.MAX_WALK_COUNT     = maxWalkCount;
        WalkDataStore.RENDER_RADIUS      = renderRadius;
        WalkDataStore.MAX_AGE_DAYS       = maxAgeDays;
        WalkDataStore.IGNORED_BLOCKS     = new HashSet<>(ignoredBlocks);
    }

    private static Path configFile() {
        return MinecraftClient.getInstance()
                .runDirectory.toPath()
                .resolve("config")
                .resolve("path-tracer.json");
    }
}
