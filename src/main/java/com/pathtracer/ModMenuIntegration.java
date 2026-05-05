package com.pathtracer;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Hooks Path Tracer into Mod Menu so the config screen is accessible from
 * the mods list.  This class is only loaded when Mod Menu is present.
 */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return PathTracerConfigScreen::new;
    }
}
