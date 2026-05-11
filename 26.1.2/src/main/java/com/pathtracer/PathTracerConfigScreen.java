package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Config screen for Path Tracer, opened via Mod Menu.
 * Updated for Minecraft 26.1 (official Mojang mappings).
 *
 * Rows (top to bottom):
 *   Min Walk Count · Max Walk Count · Render Radius · Max Age · Clear Radius
 *
 * Buttons:
 *   Done                    — save & close
 *   Clear All Dimensions    — wipe all dimension data for the current world
 */
@Environment(EnvType.CLIENT)
public class PathTracerConfigScreen extends Screen {

    private static final int ROW_HEIGHT  = 34;
    private static final int FIRST_ROW_Y = 100;
    private static final int BTN_W       = 20;
    private static final int BTN_H       = 20;
    private static final int VALUE_W     = 40;
    private static final int GAP         = 6;

    private final Screen parent;

    private int minWalkThreshold;
    private int maxWalkCount;
    private int renderRadius;
    private int maxAgeDays;
    private int clearRadius;

    private StringWidget[] valueWidgets;

    public PathTracerConfigScreen(Screen parent) {
        super(Component.literal("Path Tracer Settings"));
        this.parent           = parent;
        this.minWalkThreshold = PathTracerConfig.minWalkThreshold;
        this.maxWalkCount     = PathTracerConfig.maxWalkCount;
        this.renderRadius     = PathTracerConfig.renderRadius;
        this.maxAgeDays       = PathTracerConfig.maxAgeDays;
        this.clearRadius      = PathTracerConfig.clearRadius;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        valueWidgets = new StringWidget[5];

        // Title
        int titleW = font.width(this.title);
        addRenderableWidget(new StringWidget(cx - titleW / 2, 12, titleW + 2, 10,
                this.title, font));

        // Description lines
        String[] desc = {
            "Path Tracer counts the block you stand on + each of",
            "the 8 surrounding blocks when you walk.",
            "Min and Max Walk Count is divided by 3 per pass.",
            "Ex. 3 trips over a block = 9 count."
        };
        for (int i = 0; i < desc.length; i++) {
            int dw = font.width(desc[i]);
            addRenderableWidget(new StringWidget(cx - dw / 2, 30 + i * 11, dw + 2, 10,
                    Component.literal(desc[i]), font));
        }

        // Setting rows
        addSettingRow(0, cx, "Min Walk Count",
                PathTracerConfig.MIN_WALK_THRESHOLD_MIN,
                PathTracerConfig.MIN_WALK_THRESHOLD_MAX,
                () -> minWalkThreshold, v -> minWalkThreshold = v);

        addSettingRow(1, cx, "Max Walk Count",
                PathTracerConfig.MAX_WALK_COUNT_MIN,
                PathTracerConfig.MAX_WALK_COUNT_MAX,
                () -> maxWalkCount, v -> maxWalkCount = v);

        addSettingRow(2, cx, "Render Radius (blocks)",
                PathTracerConfig.RENDER_RADIUS_MIN,
                PathTracerConfig.RENDER_RADIUS_MAX,
                () -> renderRadius, v -> renderRadius = v);

        addSettingRow(3, cx, "Max Age (in-game days)",
                PathTracerConfig.MAX_AGE_DAYS_MIN,
                PathTracerConfig.MAX_AGE_DAYS_MAX,
                () -> maxAgeDays, v -> maxAgeDays = v);

        addSettingRow(4, cx, "Clear Radius (blocks)",
                PathTracerConfig.CLEAR_RADIUS_MIN,
                PathTracerConfig.CLEAR_RADIUS_MAX,
                () -> clearRadius, v -> clearRadius = v);

        // Buttons — Done left, Clear All Dimensions right
        int btnY   = rowY(4) + BTN_H + 14;
        int btnW   = (this.width / 2) - 8;
        int doneX  = cx - btnW - 4;
        int clearX = cx + 4;

        addRenderableWidget(
                Button.builder(Component.literal("Done"), btn -> saveAndClose())
                        .pos(doneX, btnY)
                        .size(btnW, 20)
                        .build());

        addRenderableWidget(
                Button.builder(
                        Component.literal("§cClear All Dimensions"),
                        btn -> {
                            WalkDataStore.getInstance().clearAllDimensions();
                            btn.setMessage(Component.literal("§aAll data cleared!"));
                            btn.active = false;
                        })
                        .pos(clearX, btnY)
                        .size(btnW, 20)
                        .build());
    }

    // ── Row builder ───────────────────────────────────────────────────────────

    private void addSettingRow(int index, int cx, String label,
                               int min, int max,
                               IntSupplier getter, IntConsumer setter) {
        int y = rowY(index);

        int labelW = font.width(label);
        addRenderableWidget(new StringWidget(cx - labelW / 2, y - 12, labelW + 2, 10,
                Component.literal(label), font));

        addRenderableWidget(
                Button.builder(Component.literal("−"), btn -> {
                    setter.accept(clamp(getter.get() - 1, min, max));
                    valueWidgets[index].setMessage(
                            Component.literal(String.valueOf(getter.get())));
                })
                .pos(cx - BTN_W - VALUE_W / 2 - GAP, y)
                .size(BTN_W, BTN_H)
                .build());

        StringWidget vw = new StringWidget(cx - VALUE_W / 2, y + 6, VALUE_W, 10,
                Component.literal(String.valueOf(getter.get())), font);
        valueWidgets[index] = vw;
        addRenderableWidget(vw);

        addRenderableWidget(
                Button.builder(Component.literal("+"), btn -> {
                    setter.accept(clamp(getter.get() + 1, min, max));
                    valueWidgets[index].setMessage(
                            Component.literal(String.valueOf(getter.get())));
                })
                .pos(cx + VALUE_W / 2 + GAP, y)
                .size(BTN_W, BTN_H)
                .build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int rowY(int i) { return FIRST_ROW_Y + i * ROW_HEIGHT; }

    private void saveAndClose() {
        PathTracerConfig.minWalkThreshold = minWalkThreshold;
        PathTracerConfig.maxWalkCount     = maxWalkCount;
        PathTracerConfig.renderRadius     = renderRadius;
        PathTracerConfig.maxAgeDays       = maxAgeDays;
        PathTracerConfig.clearRadius      = clearRadius;
        PathTracerConfig.save();
        onClose();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @FunctionalInterface private interface IntSupplier { int get(); }
    @FunctionalInterface private interface IntConsumer  { void accept(int v); }
}
