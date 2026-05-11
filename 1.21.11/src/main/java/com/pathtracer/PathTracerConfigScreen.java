package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

/**
 * Config screen for Path Tracer, opened via Mod Menu.
 *
 * Each setting is a labelled text field. Values are parsed and clamped
 * when Done is pressed; invalid or empty entries revert to the value
 * the screen opened with.
 *
 * Rows (top to bottom):
 *   Min Walk Count · Max Walk Count · Render Radius · Max Age · Clear Radius
 *
 * Buttons:
 *   Done                 — save & close
 *   Clear All Dimensions — wipe all dimension data for the current world
 */
@Environment(EnvType.CLIENT)
public class PathTracerConfigScreen extends Screen {

    private static final int ROW_HEIGHT   = 34;
    private static final int FIRST_ROW_Y  = 100;
    private static final int FIELD_W      = 80;
    private static final int FIELD_H      = 20;

    private final Screen parent;

    // Values the screen was opened with — used as fallback if a field is invalid.
    private final int[] initialValues = {
        PathTracerConfig.minWalkThreshold,
        PathTracerConfig.maxWalkCount,
        PathTracerConfig.renderRadius,
        PathTracerConfig.maxAgeDays,
        PathTracerConfig.clearRadius,
    };

    private final int[] mins = {
        PathTracerConfig.MIN_WALK_THRESHOLD_MIN,
        PathTracerConfig.MAX_WALK_COUNT_MIN,
        PathTracerConfig.RENDER_RADIUS_MIN,
        PathTracerConfig.MAX_AGE_DAYS_MIN,
        PathTracerConfig.CLEAR_RADIUS_MIN,
    };

    private final int[] maxs = {
        PathTracerConfig.MIN_WALK_THRESHOLD_MAX,
        PathTracerConfig.MAX_WALK_COUNT_MAX,
        PathTracerConfig.RENDER_RADIUS_MAX,
        PathTracerConfig.MAX_AGE_DAYS_MAX,
        PathTracerConfig.CLEAR_RADIUS_MAX,
    };

    private final String[] labels = {
        "Min Walk Count",
        "Max Walk Count",
        "Render Radius (blocks)",
        "Max Age (in-game days)",
        "Clear Radius (blocks)",
    };

    private final TextFieldWidget[] fields = new TextFieldWidget[5];

    public PathTracerConfigScreen(Screen parent) {
        super(Text.literal("Path Tracer Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

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

        // Setting rows — one text field per row
        for (int i = 0; i < 5; i++) {
            int y = rowY(i);

            // Centered label
            int lw = textRenderer.getWidth(labels[i]);
            addDrawableChild(new TextWidget(cx - lw / 2, y - 12, lw + 2, 10,
                    Text.literal(labels[i]), textRenderer));

            // Text field (digits only)
            TextFieldWidget field = new TextFieldWidget(
                    textRenderer, cx - FIELD_W / 2, y, FIELD_W, FIELD_H, Text.empty());
            field.setMaxLength(5);
            field.setText(String.valueOf(initialValues[i]));
            field.setTextPredicate(s -> s.matches("\\d*"));
            fields[i] = field;
            addDrawableChild(field);
        }

        // Buttons
        int btnY  = rowY(4) + FIELD_H + 14;
        int btnW  = (this.width / 2) - 8;
        int doneX = cx - btnW - 4;
        int clrX  = cx + 4;

        addDrawableChild(
                ButtonWidget.builder(Text.literal("Done"), btn -> saveAndClose())
                        .position(doneX, btnY).size(btnW, 20).build());

        addDrawableChild(
                ButtonWidget.builder(Text.literal("§cClear All Dimensions"), btn -> {
                    WalkDataStore.getInstance().clearAllDimensions();
                    btn.setMessage(Text.literal("§aAll data cleared!"));
                    btn.active = false;
                }).position(clrX, btnY).size(btnW, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int rowY(int i) { return FIRST_ROW_Y + i * ROW_HEIGHT; }

    private int parseField(int index) {
        try {
            String text = fields[index].getText().trim();
            if (text.isEmpty()) return initialValues[index];
            return Math.max(mins[index], Math.min(maxs[index], Integer.parseInt(text)));
        } catch (NumberFormatException e) {
            return initialValues[index];
        }
    }

    private void saveAndClose() {
        PathTracerConfig.minWalkThreshold = parseField(0);
        PathTracerConfig.maxWalkCount     = parseField(1);
        PathTracerConfig.renderRadius     = parseField(2);
        PathTracerConfig.maxAgeDays       = parseField(3);
        PathTracerConfig.clearRadius      = parseField(4);
        PathTracerConfig.save();
        close();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
