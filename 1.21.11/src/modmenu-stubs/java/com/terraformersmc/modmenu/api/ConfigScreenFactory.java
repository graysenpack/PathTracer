package com.terraformersmc.modmenu.api;

/**
 * Compile-time stub for {@code com.terraformersmc.modmenu.api.ConfigScreenFactory}.
 *
 * The real class is provided by Mod Menu at runtime.  The method signature
 * must exactly match the real one — {@code create(Screen)} — so that the
 * lambda Loom generates implements the right bridge method.  Loom remaps
 * {@code Screen} → {@code class_437} in the output jar, which is exactly
 * what Mod Menu's real interface expects.
 */
@FunctionalInterface
public interface ConfigScreenFactory<S extends net.minecraft.client.gui.screen.Screen> {
    S create(net.minecraft.client.gui.screen.Screen parent);
}
