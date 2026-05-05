package com.pathtracer;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;

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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static int  minWalkThreshold = WalkDataStore.MIN_WALK_THRESHOLD;
    public static int  maxWalkCount     = WalkDataStore.MAX_WALK_COUNT;
    public static int  renderRadius     = WalkDataStore.RENDER_RADIUS;
    public static int  maxAgeDays       = (int) WalkDataStore.MAX_AGE_DAYS;

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
    }

    private static Path configFile() {
        // Mojang: gameDirectory (was runDirectory in Yarn)
        return Minecraft.getInstance()
                .gameDirectory.toPath()
                .resolve("config")
                .resolve("path-tracer.json");
    }
}
