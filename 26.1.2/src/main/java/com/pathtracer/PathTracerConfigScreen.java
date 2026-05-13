package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Config screen for Path Tracer — three tabs.
 * Updated for Minecraft 26.1 (official Mojang mappings).
 *
 * Two-column layout: label on the left, control on the right.
 */
@Environment(EnvType.CLIENT)
public class PathTracerConfigScreen extends Screen {

    private static final int TAB_Y      = 28;
    private static final int CONTENT_Y  = 56;
    private static final int ROW_H      = 26;
    private static final int FIELD_H    = 20;
    private static final int LABEL_X    = 8;

    private static final String[] FOOTPRINT_LABELS = {"1×1", "3×3", "5×5"};

    private final Screen  parent;
    private int           activeTab         = 0;
    private KeyMapping    activelyRebinding = null;

    private boolean workingExplorerMode;
    private int     workingRenderRadius;
    private int     workingClearRadius;
    private boolean workingTrackOthers;
    private int     workingFootprint;
    private int     workingMinPasses;
    private int     workingMaxPasses;
    private int     workingPathMaxAge;
    private int     workingGradientDays;
    private int     workingExplorerMaxAge;

    public PathTracerConfigScreen(Screen parent) {
        super(Component.literal("Path Tracer Settings"));
        this.parent           = parent;
        workingExplorerMode   = PathTracerConfig.explorerMode;
        workingRenderRadius   = PathTracerConfig.renderRadius;
        workingClearRadius    = PathTracerConfig.clearRadius;
        workingTrackOthers    = PathTracerConfig.trackOtherPlayers;
        workingFootprint      = PathTracerConfig.footprintRadius;
        workingMinPasses      = PathTracerConfig.minPassCount;
        workingMaxPasses      = PathTracerConfig.maxPassCount;
        workingPathMaxAge     = PathTracerConfig.maxAgeDays;
        workingGradientDays   = PathTracerConfig.explorerGradientDays;
        workingExplorerMaxAge = PathTracerConfig.explorerMaxAgeDays;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        int tw = font.width(this.title);
        addRenderableWidget(new StringWidget(cx - tw / 2, 10, tw + 2, 10, this.title, font));

        String[] names = {"General", "Path Mode", "Explorer Mode"};
        int tabW = this.width / 3;
        for (int i = 0; i < 3; i++) {
            final int tab = i;
            boolean active = (activeTab == i);
            Button btn = Button.builder(
                            Component.literal(active ? "§l§n" + names[i] : names[i]),
                            b -> switchTab(tab))
                    .pos(i * tabW, TAB_Y)
                    .size(i < 2 ? tabW : this.width - i * tabW, 20)
                    .build();
            btn.active = !active;
            addRenderableWidget(btn);
        }

        switch (activeTab) {
            case 0 -> buildGeneralTab(cx);
            case 1 -> buildPathTab(cx);
            case 2 -> buildExplorerTab(cx);
        }
    }

    private void buildGeneralTab(int cx) {
        int y = CONTENT_Y;

        String desc = "Path Mode: heat map of activity.  Explorer Mode: age-based trail.";
        int dw = font.width(desc);
        addRenderableWidget(new StringWidget(cx - dw / 2, y, dw + 2, 10,
                Component.literal(desc), font));
        y += 18;

        row2Toggle(cx, y, "Mode",
                workingExplorerMode ? "§bExplorer" : "§aPath Building",
                btn -> {
                    workingExplorerMode = !workingExplorerMode;
                    btn.setMessage(Component.literal(workingExplorerMode ? "§bExplorer" : "§aPath Building"));
                });
        y += ROW_H;

        row2Field(cx, y, "Render Radius (blocks)", workingRenderRadius,
                PathTracerConfig.RENDER_RADIUS_MIN, PathTracerConfig.RENDER_RADIUS_MAX,
                v -> workingRenderRadius = v);
        y += ROW_H;

        row2Field(cx, y, "Clear Radius (blocks)", workingClearRadius,
                PathTracerConfig.CLEAR_RADIUS_MIN, PathTracerConfig.CLEAR_RADIUS_MAX,
                v -> workingClearRadius = v);
        y += ROW_H;

        row2Key(cx, y, "Show / Hide Overlay", ModKeybindings.toggleOverlay); y += ROW_H;
        row2Key(cx, y, "Clear Nearby Paths",  ModKeybindings.clearArea);     y += ROW_H;
        row2Key(cx, y, "Toggle Mode",          ModKeybindings.toggleMode);    y += ROW_H;

        row2Toggle(cx, y, "Other Players",
                workingTrackOthers ? "§aON" : "§cOFF",
                btn -> {
                    workingTrackOthers = !workingTrackOthers;
                    btn.setMessage(Component.literal(workingTrackOthers ? "§aON" : "§cOFF"));
                });
        y += ROW_H;

        addRenderableWidget(Button.builder(Component.literal("§cDelete All Paths"), btn -> {
            WalkDataStore.getInstance().clearAllDimensions();
            btn.setMessage(Component.literal("§aAll paths deleted!"));
            btn.active = false;
        }).pos(cx - 100, y).size(200, FIELD_H).build());
        y += ROW_H;

        done(cx, y);
    }

    private void buildPathTab(int cx) {
        int y = CONTENT_Y;

        row2Toggle(cx, y, "Footprint Size",
                FOOTPRINT_LABELS[workingFootprint],
                btn -> {
                    workingFootprint = (workingFootprint + 1) % 3;
                    btn.setMessage(Component.literal(FOOTPRINT_LABELS[workingFootprint]));
                });
        y += ROW_H;

        row2Field(cx, y, "Min Passes", workingMinPasses,
                PathTracerConfig.PASS_COUNT_MIN, PathTracerConfig.PASS_COUNT_MAX,
                v -> workingMinPasses = v);
        y += ROW_H;

        row2Field(cx, y, "Max Passes", workingMaxPasses,
                PathTracerConfig.PASS_COUNT_MIN, PathTracerConfig.PASS_COUNT_MAX,
                v -> workingMaxPasses = v);
        y += ROW_H;

        row2Field(cx, y, "Max Age — Path Mode (days)", workingPathMaxAge,
                PathTracerConfig.MAX_AGE_DAYS_MIN, PathTracerConfig.MAX_AGE_DAYS_MAX,
                v -> workingPathMaxAge = v);
        y += ROW_H;

        done(cx, y);
    }

    private void buildExplorerTab(int cx) {
        int y = CONTENT_Y;

        row2Field(cx, y, "Gradient Days (max 14)", workingGradientDays,
                PathTracerConfig.EXPLORER_GRADIENT_MIN, PathTracerConfig.EXPLORER_GRADIENT_MAX,
                v -> workingGradientDays = v);
        y += ROW_H;

        row2Field(cx, y, "Max Age — Explorer Mode (days)", workingExplorerMaxAge,
                PathTracerConfig.EXPLORER_MAX_AGE_MIN, PathTracerConfig.EXPLORER_MAX_AGE_MAX,
                v -> workingExplorerMaxAge = v);
        y += ROW_H;

        done(cx, y);
    }

    // ── Two-column row helpers ────────────────────────────────────────────────

    private void row2Field(int cx, int y, String label, int initial, int min, int max,
                            IntSetter setter) {
        int ctrlX = cx + 5;
        int ctrlW = this.width - ctrlX - 5;
        int lw = font.width(label);
        addRenderableWidget(new StringWidget(LABEL_X, y + 5, lw + 2, 10,
                Component.literal(label), font));
        EditBox f = new EditBox(font, ctrlX, y, ctrlW, FIELD_H, Component.empty());
        f.setMaxLength(5);
        f.setValue(String.valueOf(initial));
        f.setResponder(s -> {
            try { setter.set(Integer.parseInt(s)); }
            catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(f);
    }

    private void row2Toggle(int cx, int y, String label, String btnText,
                             Button.OnPress action) {
        int ctrlX = cx + 5;
        int ctrlW = this.width - ctrlX - 5;
        int lw = font.width(label);
        addRenderableWidget(new StringWidget(LABEL_X, y + 5, lw + 2, 10,
                Component.literal(label), font));
        addRenderableWidget(Button.builder(Component.literal(btnText), action)
                .pos(ctrlX, y).size(ctrlW, FIELD_H).build());
    }

    private void row2Key(int cx, int y, String label, KeyMapping mapping) {
        int ctrlX  = cx + 5;
        int clearW = 42;
        int keyW   = this.width - ctrlX - 5 - clearW - 4;
        int lw = font.width(label);
        addRenderableWidget(new StringWidget(LABEL_X, y + 5, lw + 2, 10,
                Component.literal(label), font));
        boolean listening = (activelyRebinding == mapping);
        addRenderableWidget(Button.builder(
                listening ? Component.literal("§ePress a key…") : mapping.getTranslatedKeyMessage(),
                btn -> {
                    activelyRebinding = (activelyRebinding == mapping) ? null : mapping;
                    switchTab(activeTab);
                })
                .pos(ctrlX, y).size(keyW, FIELD_H).build());
        addRenderableWidget(Button.builder(Component.literal("✕ Clear"), btn -> {
            mapping.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            if (minecraft != null) minecraft.options.save();
            switchTab(activeTab);
        }).pos(ctrlX + keyW + 4, y).size(clearW, FIELD_H).build());
    }

    private void done(int cx, int y) {
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> saveAndClose())
                .pos(cx - 75, y).size(150, FIELD_H).build());
    }

    // ── Key capture ───────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activelyRebinding != null) {
            if (event.key() != GLFW.GLFW_KEY_ESCAPE) {
                activelyRebinding.setKey(InputConstants.getKey(event));
                KeyMapping.resetMapping();
                if (minecraft != null) minecraft.options.save();
            }
            activelyRebinding = null;
            switchTab(activeTab);
            return true;
        }
        return super.keyPressed(event);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void switchTab(int tab) {
        activeTab = tab;
        this.init(this.width, this.height);
    }

    private void saveAndClose() {
        PathTracerConfig.explorerMode         = workingExplorerMode;
        PathTracerConfig.renderRadius         = clamp(workingRenderRadius, PathTracerConfig.RENDER_RADIUS_MIN, PathTracerConfig.RENDER_RADIUS_MAX);
        PathTracerConfig.clearRadius          = clamp(workingClearRadius,  PathTracerConfig.CLEAR_RADIUS_MIN,  PathTracerConfig.CLEAR_RADIUS_MAX);
        PathTracerConfig.trackOtherPlayers    = workingTrackOthers;
        PathTracerConfig.footprintRadius      = workingFootprint;
        PathTracerConfig.minPassCount         = clamp(workingMinPasses, PathTracerConfig.PASS_COUNT_MIN, PathTracerConfig.PASS_COUNT_MAX);
        PathTracerConfig.maxPassCount         = Math.max(PathTracerConfig.minPassCount,
                clamp(workingMaxPasses, PathTracerConfig.PASS_COUNT_MIN, PathTracerConfig.PASS_COUNT_MAX));
        PathTracerConfig.maxAgeDays           = clamp(workingPathMaxAge,     PathTracerConfig.MAX_AGE_DAYS_MIN,      PathTracerConfig.MAX_AGE_DAYS_MAX);
        PathTracerConfig.explorerGradientDays = clamp(workingGradientDays,   PathTracerConfig.EXPLORER_GRADIENT_MIN, PathTracerConfig.EXPLORER_GRADIENT_MAX);
        PathTracerConfig.explorerMaxAgeDays   = clamp(workingExplorerMaxAge, PathTracerConfig.EXPLORER_MAX_AGE_MIN,  PathTracerConfig.EXPLORER_MAX_AGE_MAX);
        PathTracerConfig.save();
        onClose();
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @FunctionalInterface private interface IntSetter { void set(int v); }
}
