package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers the H-key toggle for the path overlay.
 * The keybind can be rebound in Options → Controls → Path Tracer.
 *
 * In MC 1.21.11 the KeyBinding constructor's category parameter changed from
 * a raw String to a KeyBinding.Category object.  Custom categories are created
 * with KeyBinding.Category.create(String) where the String is a translation key
 * that appears in the Controls menu (defined in en_us.json).
 */
@Environment(EnvType.CLIENT)
public class ModKeybindings {

    public static KeyBinding toggleOverlay;

    public static void register() {
        toggleOverlay = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.path-tracer.toggle",                          // translation key (en_us.json)
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,                                   // default: H
                KeyBinding.Category.create(Identifier.of("path-tracer", "controls"))  // Controls section
        ));

        // Poll the keybind each tick so it respects repeat-press correctly
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleOverlay.wasPressed()) {
                PathRenderer.toggleOverlay();
            }
        });
    }
}
