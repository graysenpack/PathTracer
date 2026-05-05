package com.pathtracer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Path Tracer — client-side walk heatmap for Minecraft 1.21.1 (Fabric).
 *
 * What it does:
 *   - Tracks every unique block the player walks on (ground-only, no flying/swimming/riding)
 *   - Renders a translucent color overlay on those blocks:
 *       faint yellow  = walked a few times (≥ 3)
 *       bright green  = walked many times  (≥ 20)
 *   - Data persists between sessions per world/server
 *   - Entries older than 7 in-game days are automatically pruned
 *   - Press H to toggle the overlay on/off
 *
 * No blocks are ever modified — purely visual and client-side only.
 */
@Environment(EnvType.CLIENT)
public class PathTracerMod implements ClientModInitializer {

    public static final String MOD_ID = "path-tracer";

    @Override
    public void onInitializeClient() {
        PathTracerConfig.load();   // load config before anything reads WalkDataStore constants
        ModKeybindings.register();
        WalkTracker.register();
        PathRenderer.register();
    }
}
