package com.terraformersmc.modmenu.api;

// In 26.1, no remapping — Screen is the real Mojang class name.
@FunctionalInterface
public interface ConfigScreenFactory<S extends net.minecraft.client.gui.screens.Screen> {
    S create(net.minecraft.client.gui.screens.Screen parent);
}
