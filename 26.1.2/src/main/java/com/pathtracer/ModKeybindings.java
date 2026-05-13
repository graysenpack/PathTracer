package com.pathtracer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers keybinds for Path Tracer.
 * Updated for Minecraft 26.1 (official Mojang mappings).
 *
 *   H               — Toggle path overlay on/off
 *   (unbound)       — Clear path data within clear radius in the current dimension
 */
@Environment(EnvType.CLIENT)
public class ModKeybindings {

    public static KeyMapping toggleOverlay;
    public static KeyMapping clearArea;
    public static KeyMapping toggleMode;

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("path-tracer", "controls"));

        toggleOverlay = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.path-tracer.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category));

        clearArea = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.path-tracer.clear_area",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));

        toggleMode = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.path-tracer.toggle_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,   // unbound by default
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleOverlay.consumeClick()) {
                PathRenderer.toggleOverlay();
            }
            while (toggleMode.consumeClick()) {
                PathTracerConfig.explorerMode = !PathTracerConfig.explorerMode;
                PathTracerConfig.save();
                if (client.player != null) {
                    String msg = PathTracerConfig.explorerMode
                            ? "§b[PathTracer] Explorer Mode"
                            : "§a[PathTracer] Path Building Mode";
                    client.player.sendOverlayMessage(Component.literal(msg));
                }
            }
            while (clearArea.consumeClick()) {
                if (client.player == null) return;
                BlockPos center  = client.player.blockPosition();
                int      removed = WalkDataStore.getInstance().clearArea(center);
                client.player.sendOverlayMessage(
                        Component.literal("§6[PathTracer] Cleared " + removed
                                + " entries within " + WalkDataStore.CLEAR_RADIUS
                                + " blocks (current dimension)"));
            }
        });
    }
}
