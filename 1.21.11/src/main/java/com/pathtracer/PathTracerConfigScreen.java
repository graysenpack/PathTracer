package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

/**
 * Config screen for Path Tracer, opened via Mod Menu.
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

    private TextWidget[] valueWidgets;

    public PathTracerConfigScreen(Screen parent) {
        super(Text.literal("Path Tracer Settings"));
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
        valueWidgets = new TextWidget[5];

        // Title
        int titleW = textRenderer.getWidth(this.title);
        addDrawableChild(new TextWidget(cx - titleW / 2, 12, titleW + 2, 10,
                this.title, textRenderer));

        // Description lines
        String[] desc = {
            "Path Tracer counts the block you stand on + each of",
            "the 8 surrounding blocks when you walk.",
            "Min and Max Walk Count is divided by 3 per pass.",
            "Ex. 3 trips over a block = 9 count."
        };
        for (int i = 0; i < desc.length; i++) {
            int dw = textRenderer.getWidth(desc[i]);
            addDrawableChild(new TextWidget(cx - dw / 2, 30 + i * 11, dw + 2, 10,
                    Text.literal(desc[i]), textRenderer));
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
        int btnY    = rowY(4) + BTN_H + 14;
        int btnW    = (this.width / 2) - 8;   // half width minus a small margin
        int doneX   = cx - btnW - 4;
        int clearX  = cx + 4;

        addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), btn -> saveAndClose())
                        .position(doneX, btnY)
                        .size(btnW, 20)
                        .build());

        addDrawableChild(
                ButtonWidget.builder(
                        Text.literal("§cClear All Dimensions"),
                        btn -> {
                            WalkDataStore.getInstance().clearAllDimensions();
                            btn.setMessage(Text.literal("§aAll data cleared!"));
                            btn.active = false;
                        })
                        .position(clearX, btnY)
                        .size(btnW, 20)
                        .build());
    }

    // ── Row builder ───────────────────────────────────────────────────────────

    private void addSettingRow(int index, int cx, String label,
                               int min, int max,
                               IntSupplier getter, IntConsumer setter) {
        int y = rowY(index);

        int labelW = textRenderer.getWidth(label);
        addDrawableChild(new TextWidget(cx - labelW / 2, y - 12, labelW + 2, 10,
                Text.literal(label), textRenderer));

        addDrawableChild(
                ButtonWidget.builder(Text.literal("−"), btn -> {
                    setter.accept(clamp(getter.get() - 1, min, max));
                    valueWidgets[index].setMessage(
                            Text.literal(String.valueOf(getter.get())));
                })
                .position(cx - BTN_W - VALUE_W / 2 - GAP, y)
                .size(BTN_W, BTN_H)
                .build());

        TextWidget vw = new TextWidget(cx - VALUE_W / 2, y + 6, VALUE_W, 10,
                Text.literal(String.valueOf(getter.get())), textRenderer);
        valueWidgets[index] = vw;
        addDrawableChild(vw);

        addDrawableChild(
                ButtonWidget.builder(Text.literal("+"), btn -> {
                    setter.accept(clamp(getter.get() + 1, min, max));
                    valueWidgets[index].setMessage(
                            Text.literal(String.valueOf(getter.get())));
                })
                .position(cx + VALUE_W / 2 + GAP, y)
                .size(BTN_W, BTN_H)
                .build());
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
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
        close();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @FunctionalInterface private interface IntSupplier { int get(); }
    @FunctionalInterface private interface IntConsumer  { void accept(int v); }
}
