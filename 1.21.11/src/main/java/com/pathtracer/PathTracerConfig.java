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
 * Walk counts are stored and displayed in "passes" (how many times the player
 * actually walks over a block). The internal threshold written to WalkDataStore
 * is: passes × footprintMultiplier, where footprintMultiplier = 2×radius + 1.
 *
 *   radius 0 → 1×1 footprint → multiplier 1
 *   radius 1 → 3×3 footprint → multiplier 3  (default)
 *   radius 2 → 5×5 footprint → multiplier 5
 */
@Environment(EnvType.CLIENT)
public class PathTracerConfig {

    // ── Screen limits ─────────────────────────────────────────────────────────
    public static final int  PASS_COUNT_MIN        = 1;
    public static final int  PASS_COUNT_MAX        = 64;
    public static final int  RENDER_RADIUS_MIN     = 16;
    public static final int  RENDER_RADIUS_MAX     = 256;
    public static final int  MAX_AGE_DAYS_MIN      = 1;
    public static final int  MAX_AGE_DAYS_MAX      = 365;
    public static final int  CLEAR_RADIUS_MIN      = 8;
    public static final int  CLEAR_RADIUS_MAX      = 256;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── User-facing values ────────────────────────────────────────────────────
    /** Footprint radius: 0 = 1×1, 1 = 3×3, 2 = 5×5. */
    public static int     footprintRadius    = 1;
    /** Passes before a block appears on the overlay. */
    public static int     minPassCount       = 3;
    /** Passes for the overlay to reach full intensity. */
    public static int     maxPassCount       = 20;
    public static int     renderRadius       = WalkDataStore.RENDER_RADIUS;
    public static int     maxAgeDays         = (int) WalkDataStore.MAX_AGE_DAYS;
    public static int     clearRadius        = WalkDataStore.CLEAR_RADIUS;
    public static boolean trackOtherPlayers  = WalkDataStore.TRACK_OTHER_PLAYERS;

    // ── Ignored blocks ────────────────────────────────────────────────────────
    public static final Set<String> DEFAULT_IGNORED_BLOCKS = new HashSet<>(Arrays.asList(
        "minecraft:grass", "minecraft:short_grass",
        "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
        "minecraft:short_dry_grass",
        "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
        "minecraft:allium", "minecraft:azure_bluet",
        "minecraft:red_tulip", "minecraft:orange_tulip",
        "minecraft:white_tulip", "minecraft:pink_tulip",
        "minecraft:oxeye_daisy", "minecraft:cornflower",
        "minecraft:lily_of_the_valley", "minecraft:torchflower",
        "minecraft:wildflowers",
        "minecraft:sunflower", "minecraft:lilac",
        "minecraft:rose_bush", "minecraft:peony",
        "minecraft:pitcher_plant", "minecraft:firefly_bush",
        "minecraft:oak_sapling", "minecraft:spruce_sapling",
        "minecraft:birch_sapling", "minecraft:jungle_sapling",
        "minecraft:acacia_sapling", "minecraft:dark_oak_sapling",
        "minecraft:cherry_sapling", "minecraft:pale_oak_sapling",
        "minecraft:mangrove_propagule", "minecraft:bamboo_sapling",
        "minecraft:vine", "minecraft:cave_vines", "minecraft:cave_vines_plant",
        "minecraft:small_dripleaf",
        "minecraft:big_dripleaf", "minecraft:big_dripleaf_stem",
        "minecraft:dead_bush", "minecraft:bush",
        "minecraft:sugar_cane", "minecraft:pitcher_pod",
        "minecraft:seagrass", "minecraft:tall_seagrass",
        "minecraft:kelp", "minecraft:kelp_plant",
        "minecraft:snow", "minecraft:moss_carpet", "minecraft:pale_moss_carpet",
        "minecraft:pink_petals", "minecraft:leaf_litter", "minecraft:glow_lichen"
    ));
    public static Set<String> ignoredBlocks = new HashSet<>(DEFAULT_IGNORED_BLOCKS);

    // ── Public API ────────────────────────────────────────────────────────────

    /** Load config from disk and push computed values into WalkDataStore. */
    public static void load() {
        Path file = configFile();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                JsonObject obj = GSON.fromJson(r, JsonObject.class);
                if (obj != null) {
                    // New-format fields
                    if (obj.has("footprintRadius"))   footprintRadius  = obj.get("footprintRadius").getAsInt();
                    if (obj.has("minPassCount"))       minPassCount     = obj.get("minPassCount").getAsInt();
                    if (obj.has("maxPassCount"))       maxPassCount     = obj.get("maxPassCount").getAsInt();

                    // Migration: old raw-count fields → convert to passes assuming 3×3 (multiplier 3)
                    if (!obj.has("minPassCount") && obj.has("minWalkThreshold"))
                        minPassCount = Math.max(PASS_COUNT_MIN, obj.get("minWalkThreshold").getAsInt() / 3);
                    if (!obj.has("maxPassCount") && obj.has("maxWalkCount"))
                        maxPassCount = Math.max(minPassCount, obj.get("maxWalkCount").getAsInt() / 3);

                    if (obj.has("renderRadius"))      renderRadius     = obj.get("renderRadius").getAsInt();
                    if (obj.has("maxAgeDays"))         maxAgeDays       = obj.get("maxAgeDays").getAsInt();
                    if (obj.has("clearRadius"))        clearRadius      = obj.get("clearRadius").getAsInt();
                    if (obj.has("trackOtherPlayers"))  trackOtherPlayers = obj.get("trackOtherPlayers").getAsBoolean();
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
        obj.addProperty("footprintRadius",   footprintRadius);
        obj.addProperty("minPassCount",      minPassCount);
        obj.addProperty("maxPassCount",      maxPassCount);
        obj.addProperty("renderRadius",      renderRadius);
        obj.addProperty("maxAgeDays",        maxAgeDays);
        obj.addProperty("clearRadius",       clearRadius);
        obj.addProperty("trackOtherPlayers", trackOtherPlayers);
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

    /**
     * Push computed values into WalkDataStore.
     * Internal threshold = passes × footprintMultiplier (1, 3, or 5).
     */
    private static void applyToStore() {
        int multiplier = 2 * footprintRadius + 1;
        WalkDataStore.FOOTPRINT_RADIUS    = footprintRadius;
        WalkDataStore.MIN_WALK_THRESHOLD  = minPassCount * multiplier;
        WalkDataStore.MAX_WALK_COUNT      = maxPassCount * multiplier;
        WalkDataStore.RENDER_RADIUS       = renderRadius;
        WalkDataStore.MAX_AGE_DAYS        = maxAgeDays;
        WalkDataStore.IGNORED_BLOCKS      = new HashSet<>(ignoredBlocks);
        WalkDataStore.CLEAR_RADIUS        = clearRadius;
        WalkDataStore.TRACK_OTHER_PLAYERS = trackOtherPlayers;
    }

    private static Path configFile() {
        return MinecraftClient.getInstance()
                .runDirectory.toPath()
                .resolve("config")
                .resolve("path-tracer.json");
    }
}
