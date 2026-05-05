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
 * Uses only proper MC widget objects (TextWidget + ButtonWidget) so that
 * all text goes through the standard widget render path.  Direct
 * DrawContext.drawCenteredTextWithShadow() calls were invisible in
 * environments with Architectury / Blur+ mixins active.
 *
 * Text widgets are manually centered by measuring text width via
 * textRenderer.getWidth() — alignCenter() is not available in 1.21.1.
 *
 * Layout per row (ROW_HEIGHT = 40 px):
 *
 *   y - 12  ── TextWidget label (centred)
 *   y       ── [−]  TextWidget value  [+]
 */
@Environment(EnvType.CLIENT)
public class PathTracerConfigScreen extends Screen {

    private static final int ROW_HEIGHT  = 40;
    private static final int FIRST_ROW_Y = 118;
    private static final int BTN_W       = 20;
    private static final int BTN_H       = 20;
    // Fixed width for value display — wide enough for up to 3 digits
    private static final int VALUE_W     = 40;
    // Gap between ±  buttons and the value label
    private static final int GAP         = 6;

    private final Screen parent;

    // Working copies — written to config only when Done is pressed
    private int minWalkThreshold;
    private int maxWalkCount;
    private int renderRadius;
    private int maxAgeDays;

    // References to value TextWidgets so we can update them on ±
    private TextWidget[] valueWidgets;

    public PathTracerConfigScreen(Screen parent) {
        super(Text.literal("Path Tracer Settings"));
        this.parent = parent;
        this.minWalkThreshold = PathTracerConfig.minWalkThreshold;
        this.maxWalkCount     = PathTracerConfig.maxWalkCount;
        this.renderRadius     = PathTracerConfig.renderRadius;
        this.maxAgeDays       = PathTracerConfig.maxAgeDays;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        valueWidgets = new TextWidget[4];

        // Title widget — manually centered
        int titleW = textRenderer.getWidth(this.title);
        addDrawableChild(
                new TextWidget(cx - titleW / 2, 12, titleW + 2, 10,
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
            addDrawableChild(new TextWidget(
                    cx - dw / 2, 30 + i * 11, dw + 2, 10,
                    Text.literal(desc[i]), textRenderer));
        }

        // Setting rows
        addSettingRow(0, cx, "Min Walk Count",
                PathTracerConfig.MIN_WALK_THRESHOLD_MIN,
                PathTracerConfig.MIN_WALK_THRESHOLD_MAX,
                () -> minWalkThreshold,
                v  -> minWalkThreshold = v);

        addSettingRow(1, cx, "Max Walk Count",
                PathTracerConfig.MAX_WALK_COUNT_MIN,
                PathTracerConfig.MAX_WALK_COUNT_MAX,
                () -> maxWalkCount,
                v  -> maxWalkCount = v);

        addSettingRow(2, cx, "Render Radius (blocks)",
                PathTracerConfig.RENDER_RADIUS_MIN,
                PathTracerConfig.RENDER_RADIUS_MAX,
                () -> renderRadius,
                v  -> renderRadius = v);

        addSettingRow(3, cx, "Max Age (in-game days)",
                PathTracerConfig.MAX_AGE_DAYS_MIN,
                PathTracerConfig.MAX_AGE_DAYS_MAX,
                () -> maxAgeDays,
                v  -> maxAgeDays = v);

        // Done button
        int doneY = rowY(3) + BTN_H + 18;
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), btn -> saveAndClose())
                        .position(cx - 75, doneY)
                        .size(150, 20)
                        .build());
    }

    // ── Row builder ───────────────────────────────────────────────────────────

    private void addSettingRow(int index, int cx, String label,
                               int min, int max,
                               IntSupplier getter, IntConsumer setter) {
        int y = rowY(index);

        // Label — manually centered
        int labelW = textRenderer.getWidth(label);
        addDrawableChild(
                new TextWidget(cx - labelW / 2, y - 12, labelW + 2, 10,
                        Text.literal(label), textRenderer));

        // Minus button
        addDrawableChild(
                ButtonWidget.builder(Text.literal("−"), btn -> {
                    setter.accept(clamp(getter.get() - 1, min, max));
                    valueWidgets[index].setMessage(
                            Text.literal(String.valueOf(getter.get())));
                })
                .position(cx - BTN_W - VALUE_W / 2 - GAP, y)
                .size(BTN_W, BTN_H)
                .build());

        // Value display — fixed-width widget centred between the two buttons
        TextWidget vw = new TextWidget(cx - VALUE_W / 2, y + 6, VALUE_W, 10,
                Text.literal(String.valueOf(getter.get())), textRenderer);
        valueWidgets[index] = vw;
        addDrawableChild(vw);

        // Plus button
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
        // All text is handled by TextWidget / ButtonWidget — no direct
        // drawCenteredTextWithShadow calls needed or used.
        super.render(context, mouseX, mouseY, delta);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int rowY(int i) {
        return FIRST_ROW_Y + i * ROW_HEIGHT;
    }

    private void saveAndClose() {
        PathTracerConfig.minWalkThreshold = minWalkThreshold;
        PathTracerConfig.maxWalkCount     = maxWalkCount;
        PathTracerConfig.renderRadius     = renderRadius;
        PathTracerConfig.maxAgeDays       = maxAgeDays;
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
