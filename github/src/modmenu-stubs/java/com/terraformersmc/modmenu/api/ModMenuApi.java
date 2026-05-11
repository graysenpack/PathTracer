package com.terraformersmc.modmenu.api;

/**
 * Compile-time stub for {@code com.terraformersmc.modmenu.api.ModMenuApi}.
 *
 * The real interface is provided by Mod Menu at runtime.  The default method
 * body here is never called — it exists only so the project compiles without
 * Mod Menu on the compile classpath.
 */
@SuppressWarnings("rawtypes")
public interface ModMenuApi {
    default ConfigScreenFactory getModConfigScreenFactory() {
        return parent -> null;
    }
}
