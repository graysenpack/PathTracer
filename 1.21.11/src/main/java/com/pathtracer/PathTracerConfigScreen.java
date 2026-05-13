package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Config screen for Path Tracer — three tabs.
 *
 * Each row uses a two-column layout: label on the left, control on the right.
 * This matches the vanilla Controls screen style and prevents label/control overlap.
 */
@Environment(EnvType.CLIENT)
public class PathTracerConfigScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int TAB_Y      = 28;
    private static final int CONTENT_Y  = 56;
    private static final int ROW_H      = 26;   // field height (20) + 6px gap
    private static final int FIELD_H    = 20;
    private static final int LABEL_X    = 8;    // left edge of all labels

    private static final String[] FOOTPRINT_LABELS = {"1×1", "3×3", "5×5"};

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen   parent;
    private int            activeTab         = 0;
    private KeyBinding     activelyRebinding = null;

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
        super(Text.literal("Path Tracer Settings"));
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

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int cx = this.width / 2;

        int tw = textRenderer.getWidth(this.title);
        addDrawableChild(new TextWidget(cx - tw / 2, 10, tw + 2, 10, this.title, textRenderer));

        String[] names = {"General", "Path Mode", "Explorer Mode"};
        int tabW = this.width / 3;
        for (int i = 0; i < 3; i++) {
            final int tab = i;
            boolean active = (activeTab == i);
            ButtonWidget btn = ButtonWidget.builder(
                            Text.literal(active ? "§l§n" + names[i] : names[i]),
                            b -> switchTab(tab))
                    .position(i * tabW, TAB_Y)
                    .size(i < 2 ? tabW : this.width - i * tabW, 20)
                    .build();
            btn.active = !active;
            addDrawableChild(btn);
        }

        switch (activeTab) {
            case 0 -> buildGeneralTab(cx);
            case 1 -> buildPathTab(cx);
            case 2 -> buildExplorerTab(cx);
        }
    }

    // ── Tab content ───────────────────────────────────────────────────────────

    private void buildGeneralTab(int cx) {
        int y = CONTENT_Y;

        String desc = "Path Mode: heat map of activity.  Explorer Mode: age-based trail.";
        int dw = textRenderer.getWidth(desc);
        addDrawableChild(new TextWidget(cx - dw / 2, y, dw + 2, 10,
                Text.literal(desc), textRenderer));
        y += 18;

        row2Toggle(cx, y, "Mode",
                workingExplorerMode ? "§bExplorer" : "§aPath Building",
                btn -> {
                    workingExplorerMode = !workingExplorerMode;
                    btn.setMessage(Text.literal(workingExplorerMode ? "§bExplorer" : "§aPath Building"));
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
                    btn.setMessage(Text.literal(workingTrackOthers ? "§aON" : "§cOFF"));
                });
        y += ROW_H;

        // Delete — full-width centered, destructive styling
        addDrawableChild(ButtonWidget.builder(Text.literal("§cDelete All Paths"), btn -> {
            WalkDataStore.getInstance().clearAllDimensions();
            btn.setMessage(Text.literal("§aAll paths deleted!"));
            btn.active = false;
        }).position(cx - 100, y).size(200, FIELD_H).build());
        y += ROW_H;

        done(cx, y);
    }

    private void buildPathTab(int cx) {
        int y = CONTENT_Y;

        row2Toggle(cx, y, "Footprint Size",
                FOOTPRINT_LABELS[workingFootprint],
                btn -> {
                    workingFootprint = (workingFootprint + 1) % 3;
                    btn.setMessage(Text.literal(FOOTPRINT_LABELS[workingFootprint]));
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

    /** Label on left, text field on right. */
    private void row2Field(int cx, int y, String label, int initial, int min, int max,
                            IntSetter setter) {
        int ctrlX = cx + 5;
        int ctrlW = this.width - ctrlX - 5;
        int lw = textRenderer.getWidth(label);
        addDrawableChild(new TextWidget(LABEL_X, y + 5, lw + 2, 10,
                Text.literal(label), textRenderer));
        TextFieldWidget f = new TextFieldWidget(textRenderer, ctrlX, y, ctrlW, FIELD_H, Text.empty());
        f.setMaxLength(5);
        f.setText(String.valueOf(initial));
        f.setTextPredicate(s -> s.matches("\\d*"));
        f.setChangedListener(s -> {
            try { setter.set(Integer.parseInt(s)); }
            catch (NumberFormatException ignored) {}
        });
        addDrawableChild(f);
    }

    /** Label on left, toggle/cycle button on right. */
    private void row2Toggle(int cx, int y, String label, String btnText,
                             ButtonWidget.PressAction action) {
        int ctrlX = cx + 5;
        int ctrlW = this.width - ctrlX - 5;
        int lw = textRenderer.getWidth(label);
        addDrawableChild(new TextWidget(LABEL_X, y + 5, lw + 2, 10,
                Text.literal(label), textRenderer));
        addDrawableChild(ButtonWidget.builder(Text.literal(btnText), action)
                .position(ctrlX, y).size(ctrlW, FIELD_H).build());
    }

    /** Label on left, key-capture button + small Clear button on right. */
    private void row2Key(int cx, int y, String label, KeyBinding binding) {
        int ctrlX   = cx + 5;
        int clearW  = 42;
        int keyW    = this.width - ctrlX - 5 - clearW - 4;
        int lw = textRenderer.getWidth(label);
        addDrawableChild(new TextWidget(LABEL_X, y + 5, lw + 2, 10,
                Text.literal(label), textRenderer));
        boolean listening = (activelyRebinding == binding);
        addDrawableChild(ButtonWidget.builder(
                listening ? Text.literal("§ePress a key…") : binding.getBoundKeyLocalizedText(),
                btn -> {
                    activelyRebinding = (activelyRebinding == binding) ? null : binding;
                    switchTab(activeTab);
                })
                .position(ctrlX, y).size(keyW, FIELD_H).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("✕ Clear"), btn -> {
            binding.setBoundKey(InputUtil.UNKNOWN_KEY);
            KeyBinding.updateKeysByCode();
            if (client != null) client.options.write();
            switchTab(activeTab);
        }).position(ctrlX + keyW + 4, y).size(clearW, FIELD_H).build());
    }

    private void done(int cx, int y) {
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> saveAndClose())
                .position(cx - 75, y).size(150, FIELD_H).build());
    }

    // ── Key capture ───────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyInput input) {
        if (activelyRebinding != null) {
            if (input.key() != GLFW.GLFW_KEY_ESCAPE) {
                activelyRebinding.setBoundKey(InputUtil.fromKeyCode(input));
                KeyBinding.updateKeysByCode();
                if (client != null) client.options.write();
            }
            activelyRebinding = null;
            switchTab(activeTab);
            return true;
        }
        return super.keyPressed(input);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void switchTab(int tab) {
        activeTab = tab;
        clearAndInit();
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
        close();
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @FunctionalInterface private interface IntSetter { void set(int v); }
}
