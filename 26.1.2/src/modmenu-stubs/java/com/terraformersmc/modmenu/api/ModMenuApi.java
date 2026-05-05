package com.terraformersmc.modmenu.api;

@SuppressWarnings("rawtypes")
public interface ModMenuApi {
    default ConfigScreenFactory getModConfigScreenFactory() {
        return parent -> null;
    }
}
