package com.pathtracer;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class WalkDataStore {

    // Config defaults — overwritten by PathTracerConfig on init
    public static int  MIN_WALK_THRESHOLD = 9;
    public static int  MAX_WALK_COUNT     = 60;
    public static long MAX_AGE_DAYS       = 28;
    public static int  RENDER_RADIUS      = 64;

    private static WalkDataStore instance;

    private final Map<BlockPos, WalkData> walkMap = new HashMap<>();
    private Path    dataFilePath;
    private boolean dirty = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WalkDataStore() {}

    public static WalkDataStore getInstance() {
        if (instance == null) instance = new WalkDataStore();
        return instance;
    }

    public Map<BlockPos, WalkData> getWalkMap() { return walkMap; }

    public void recordWalk(BlockPos pos, long currentGameDay) {
        WalkData data = walkMap.get(pos);
        if (data == null) {
            walkMap.put(pos, new WalkData(1, currentGameDay));
        } else {
            data.increment(currentGameDay);
        }
        dirty = true;
    }

    public void loadForWorld(String worldId) {
        walkMap.clear();
        dirty = false;

        // Mojang: gameDirectory (was runDirectory in Yarn)
        Path baseDir = Minecraft.getInstance()
                .gameDirectory.toPath()
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

    public void clear() {
        walkMap.clear();
        dataFilePath = null;
        dirty = false;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
