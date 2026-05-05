package com.pathtracer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the H-key toggle for the path overlay.
 * Updated for Minecraft 26.1 (official Mojang mappings):
 *   KeyBinding → KeyMapping
 *   KeyBindingHelper → KeyMappingHelper
 *   InputUtil → InputConstants
 *   ResourceLocation → Identifier (net.minecraft.resources.Identifier)
 */
@Environment(EnvType.CLIENT)
public class ModKeybindings {

    public static KeyMapping toggleOverlay;

    public static void register() {
        // Mojang: KeyMapping.Category.register() with ResourceLocation
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("path-tracer", "controls")
        );

        toggleOverlay = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.path-tracer.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleOverlay.consumeClick()) {
                PathRenderer.toggleOverlay();
            }
        });
    }
}
