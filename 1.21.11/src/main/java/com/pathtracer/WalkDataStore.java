package com.pathtracer;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Singleton that stores per-block walk counts and handles JSON persistence.
 *
 * Config constants (tweak these to adjust mod behaviour):
 *   MIN_WALK_THRESHOLD  – how many times a spot must be walked before it shows up
 *   MAX_WALK_COUNT      – walk count at which the overlay reaches full-green
 *   MAX_AGE_DAYS        – in-game days before an entry is pruned
 *   RENDER_RADIUS       – horizontal block radius around the player that gets rendered
 */
@Environment(EnvType.CLIENT)
public class WalkDataStore {

    // ── Config (defaults; overwritten by PathTracerConfig on init) ───────────
    // Values are 3× the "single-block" equivalents because the 3×3 footprint
    // causes each tile to be recorded once on approach, once underfoot, and
    // once on departure — so one real step = ~3 counts per tile.
    public static int  MIN_WALK_THRESHOLD = 9;
    public static int  MAX_WALK_COUNT     = 60;
    public static long MAX_AGE_DAYS       = 28;
    public static int  RENDER_RADIUS      = 64;

    // Block IDs that are never recorded. Populated from PathTracerConfig.
    public static Set<String> IGNORED_BLOCKS = new HashSet<>();
    // ─────────────────────────────────────────────────────────────────────────

    private static WalkDataStore instance;

    private final Map<BlockPos, WalkData> walkMap = new HashMap<>();
    private Path   dataFilePath;
    private boolean dirty = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WalkDataStore() {}

    public static WalkDataStore getInstance() {
        if (instance == null) {
            instance = new WalkDataStore();
        }
        return instance;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Map<BlockPos, WalkData> getWalkMap() {
        return walkMap;
    }

    /**
     * Record that the player walked over the given block position on the given game day.
     */
    public void recordWalk(BlockPos pos, long currentGameDay) {
        WalkData data = walkMap.get(pos);
        if (data == null) {
            walkMap.put(pos, new WalkData(1, currentGameDay));
        } else {
            data.increment(currentGameDay);
        }
        dirty = true;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Load walk data for the given world/server identifier.
     * Called when the player joins a world.
     */
    public void loadForWorld(String worldId) {
        walkMap.clear();
        dirty = false;

        Path baseDir = MinecraftClient.getInstance()
                .runDirectory.toPath()
                .resolve("path-tracer-data");

        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            System.err.println("[PathTracer] Could not create data directory: " + e.getMessage());
        }

        dataFilePath = baseDir.resolve(sanitize(worldId) + ".json");

        if (!Files.exists(dataFilePath)) return;

        try (Reader reader = Files.newBufferedReader(dataFilePath)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("walks")) return;

            for (JsonElement elem : root.getAsJsonArray("walks")) {
                JsonObject obj = elem.getAsJsonObject();
                int  x       = obj.get("x").getAsInt();
                int  y       = obj.get("y").getAsInt();
                int  z       = obj.get("z").getAsInt();
                int  count   = obj.get("count").getAsInt();
                long lastDay = obj.get("lastDay").getAsLong();
                walkMap.put(new BlockPos(x, y, z), new WalkData(count, lastDay));
            }
            System.out.println("[PathTracer] Loaded " + walkMap.size() + " walk entries for: " + worldId);
        } catch (Exception e) {
            System.err.println("[PathTracer] Failed to load walk data: " + e.getMessage());
        }
    }

    /**
     * Persist current walk data to disk.  Only writes if dirty.
     */
    public void saveData() {
        if (!dirty || dataFilePath == null) return;

        JsonObject root  = new JsonObject();
        JsonArray  walks = new JsonArray();

        for (Map.Entry<BlockPos, WalkData> entry : walkMap.entrySet()) {
            BlockPos pos  = entry.getKey();
            WalkData data = entry.getValue();

            JsonObject obj = new JsonObject();
            obj.addProperty("x",       pos.getX());
            obj.addProperty("y",       pos.getY());
            obj.addProperty("z",       pos.getZ());
            obj.addProperty("count",   data.getCount());
            obj.addProperty("lastDay", data.getLastGameDay());
            walks.add(obj);
        }
        root.add("walks", walks);

        try (Writer writer = Files.newBufferedWriter(dataFilePath)) {
            GSON.toJson(root, writer);
            dirty = false;
        } catch (IOException e) {
            System.err.println("[PathTracer] Failed to save walk data: " + e.getMessage());
        }
    }

    /**
     * Remove entries older than MAX_AGE_DAYS game days.
     */
    public void pruneExpiredData(long currentGameDay) {
        Iterator<Map.Entry<BlockPos, WalkData>> iter = walkMap.entrySet().iterator();
        int pruned = 0;
        while (iter.hasNext()) {
            if (iter.next().getValue().isExpired(currentGameDay, MAX_AGE_DAYS)) {
                iter.remove();
                pruned++;
                dirty = true;
            }
        }
        if (pruned > 0) {
            System.out.println("[PathTracer] Pruned " + pruned + " expired walk entries.");
        }
    }

    /**
     * Clear all in-memory data.  Called on world disconnect.
     */
    public void clear() {
        walkMap.clear();
        dataFilePath = null;
        dirty = false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
