package com.pathtracer;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

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
 * Data is stored per-world and per-dimension so Overworld, Nether, End and
 * any modded dimension each get their own independent file:
 *   path-tracer-data/{worldId}/{dimensionId}.json
 *
 * Legacy flat files (path-tracer-data/{worldId}.json) are automatically
 * migrated to the Overworld slot on first load.
 */
@Environment(EnvType.CLIENT)
public class WalkDataStore {

    // Config defaults — overwritten by PathTracerConfig on init
    public static int  MIN_WALK_THRESHOLD = 9;
    public static int  MAX_WALK_COUNT     = 60;
    public static long MAX_AGE_DAYS       = 28;
    public static int  RENDER_RADIUS      = 64;

    // Block IDs that are never recorded. Populated from PathTracerConfig.
    public static Set<String> IGNORED_BLOCKS = new HashSet<>();

    private static WalkDataStore instance;

    private final Map<BlockPos, WalkData> walkMap = new HashMap<>();
    private Path    dataFilePath;
    private boolean dirty = false;

    // Tracked so switchDimension knows where to write the new file.
    private String currentWorldId     = null;
    private String currentDimensionId = null;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WalkDataStore() {}

    public static WalkDataStore getInstance() {
        if (instance == null) instance = new WalkDataStore();
        return instance;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Map<BlockPos, WalkData> getWalkMap() { return walkMap; }

    public String getCurrentDimensionId() { return currentDimensionId; }

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
     * Load walk data for the given world/server and dimension.
     * Called on world join.
     */
    public void loadForWorld(String worldId, String dimensionId) {
        currentWorldId    = worldId;
        currentDimensionId = dimensionId;
        walkMap.clear();
        dirty = false;

        try {
            Files.createDirectories(worldDir());
        } catch (IOException e) {
            System.err.println("[PathTracer] Could not create data directory: " + e.getMessage());
        }

        dataFilePath = worldDir().resolve(sanitize(dimensionId) + ".json");
        migrateIfNeeded(dimensionId);
        loadFromFile();
    }

    /**
     * Save current dimension's data, then load a different dimension's data.
     * Called when the player travels through a portal.
     */
    public void switchDimension(String dimensionId) {
        saveData();
        currentDimensionId = dimensionId;
        walkMap.clear();
        dirty = false;
        dataFilePath = worldDir().resolve(sanitize(dimensionId) + ".json");
        loadFromFile();
        System.out.println("[PathTracer] Switched to dimension: " + dimensionId);
    }

    /** Persist current walk data to disk. Only writes if dirty. */
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

        try {
            Files.createDirectories(dataFilePath.getParent());
            try (Writer writer = Files.newBufferedWriter(dataFilePath)) {
                GSON.toJson(root, writer);
                dirty = false;
            }
        } catch (IOException e) {
            System.err.println("[PathTracer] Failed to save walk data: " + e.getMessage());
        }
    }

    /** Remove entries older than MAX_AGE_DAYS game days. */
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
        if (pruned > 0) System.out.println("[PathTracer] Pruned " + pruned + " expired walk entries.");
    }

    /** Clear all in-memory state. Called on world disconnect. */
    public void clear() {
        walkMap.clear();
        dataFilePath      = null;
        currentWorldId    = null;
        currentDimensionId = null;
        dirty = false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Per-world subdirectory: path-tracer-data/{worldId}/ */
    private Path worldDir() {
        return Minecraft.getInstance()
                .gameDirectory.toPath()
                .resolve("path-tracer-data")
                .resolve(sanitize(currentWorldId));
    }

    /**
     * One-time migration: if the new dimension file doesn't exist yet but the
     * old flat file does, copy it into the Overworld slot.
     */
    private void migrateIfNeeded(String dimensionId) {
        if (Files.exists(dataFilePath) || !"minecraft:overworld".equals(dimensionId)) return;
        Path oldFile = Minecraft.getInstance()
                .gameDirectory.toPath()
                .resolve("path-tracer-data")
                .resolve(sanitize(currentWorldId) + ".json");
        if (!Files.exists(oldFile)) return;
        try {
            Files.copy(oldFile, dataFilePath);
            System.out.println("[PathTracer] Migrated legacy data → " + dataFilePath.getFileName());
        } catch (IOException e) {
            System.err.println("[PathTracer] Migration failed: " + e.getMessage());
        }
    }

    /** Load JSON from the current dataFilePath into walkMap. */
    private void loadFromFile() {
        if (dataFilePath == null || !Files.exists(dataFilePath)) return;
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
            System.out.println("[PathTracer] Loaded " + walkMap.size()
                    + " walk entries for " + currentWorldId + " / " + currentDimensionId);
        } catch (Exception e) {
            System.err.println("[PathTracer] Failed to load walk data: " + e.getMessage());
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
