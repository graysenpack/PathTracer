package com.pathtracer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Path Tracer — client-side walk heatmap for Minecraft 26.1.2 (Fabric).
 */
@Environment(EnvType.CLIENT)
public class PathTracerMod implements ClientModInitializer {

    public static final String MOD_ID = "path-tracer";

    @Override
    public void onInitializeClient() {
        PathTracerConfig.load();
        ModKeybindings.register();
        WalkTracker.register();
        PathRenderer.register();
    }
}
