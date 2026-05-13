package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Registers keybinds for Path Tracer.
 *
 *   H               — Toggle path overlay on/off
 *   (unbound)       — Clear path data within clear radius in the current dimension
 *
 * Both appear in Options → Controls → Path Tracer and can be rebound.
 */
@Environment(EnvType.CLIENT)
public class ModKeybindings {

    public static KeyBinding toggleOverlay;
    public static KeyBinding clearArea;
    public static KeyBinding toggleMode;

    public static void register() {
        var category = KeyBinding.Category.create(
                Identifier.of("path-tracer", "controls"));

        toggleOverlay = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.path-tracer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category));

        clearArea = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.path-tracer.clear_area",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));

        toggleMode = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.path-tracer.toggle_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,   // unbound by default
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleOverlay.wasPressed()) {
                PathRenderer.toggleOverlay();
            }
            while (toggleMode.wasPressed()) {
                PathTracerConfig.explorerMode = !PathTracerConfig.explorerMode;
                PathTracerConfig.save();
                if (client.player != null) {
                    String msg = PathTracerConfig.explorerMode
                            ? "§b[PathTracer] Explorer Mode"
                            : "§a[PathTracer] Path Building Mode";
                    client.player.sendMessage(Text.literal(msg), true);
                }
            }
            while (clearArea.wasPressed()) {
                if (client.player == null) return;
                BlockPos center  = client.player.getBlockPos();
                int      removed = WalkDataStore.getInstance().clearArea(center);
                client.player.sendMessage(
                        Text.literal("§6[PathTracer] Cleared " + removed
                                + " entries within " + WalkDataStore.CLEAR_RADIUS
                                + " blocks (current dimension)"),
                        true);
            }
        });
    }
}
