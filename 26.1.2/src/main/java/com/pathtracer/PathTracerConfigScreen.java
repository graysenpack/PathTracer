package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Config screen for Path Tracer, opened via Mod Menu.
 * Updated for Minecraft 26.1 (official Mojang mappings).
 *
 * Layout (top → bottom):
 *   Footprint Size  — cycle button  (1×1 / 3×3 / 5×5)
 *   Min Passes      — text field
 *   Max Passes      — text field
 *   Render Radius   — text field
 *   Max Age         — text field
 *   Clear Radius    — text field
 *   Track Other Players — toggle button
 *   Done  |  Clear All Dimensions
 */
@Environment(EnvType.CLIENT)
public class PathTracerConfigScreen extends Screen {

    private static final int ROW_HEIGHT  = 32;
    private static final int FIRST_ROW_Y = 90;
    private static final int FIELD_W     = 80;
    private static final int FIELD_H     = 20;

    private static final String[] FOOTPRINT_LABELS = {"1×1", "3×3", "5×5"};

    private final Screen parent;

    private int     footprintRadius;
    private boolean trackOtherPlayers;

    private final int[] initialValues = {
        PathTracerConfig.minPassCount,
        PathTracerConfig.maxPassCount,
        PathTracerConfig.renderRadius,
        PathTracerConfig.maxAgeDays,
        PathTracerConfig.clearRadius,
    };
    private final int[] mins = {
        PathTracerConfig.PASS_COUNT_MIN,
        PathTracerConfig.PASS_COUNT_MIN,
        PathTracerConfig.RENDER_RADIUS_MIN,
        PathTracerConfig.MAX_AGE_DAYS_MIN,
        PathTracerConfig.CLEAR_RADIUS_MIN,
    };
    private final int[] maxs = {
        PathTracerConfig.PASS_COUNT_MAX,
        PathTracerConfig.PASS_COUNT_MAX,
        PathTracerConfig.RENDER_RADIUS_MAX,
        PathTracerConfig.MAX_AGE_DAYS_MAX,
        PathTracerConfig.CLEAR_RADIUS_MAX,
    };
    private final String[] labels = {
        "Min Passes",
        "Max Passes",
        "Render Radius (blocks)",
        "Max Age (in-game days)",
        "Clear Radius (blocks)",
    };

    private final EditBox[] fields = new EditBox[5];

    public PathTracerConfigScreen(Screen parent) {
        super(Component.literal("Path Tracer Settings"));
        this.parent            = parent;
        this.footprintRadius   = PathTracerConfig.footprintRadius;
        this.trackOtherPlayers = PathTracerConfig.trackOtherPlayers;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // Title
        int titleW = font.width(this.title);
        addRenderableWidget(new StringWidget(cx - titleW / 2, 12, titleW + 2, 10,
                this.title, font));

        // Description
        String[] desc = {
            "Min/Max Passes: how many walk-overs before the overlay appears / reaches full intensity.",
            "Footprint controls how many blocks around you are recorded each step.",
            "Changing footprint size automatically adjusts the internal thresholds."
        };
        for (int i = 0; i < desc.length; i++) {
            int dw = font.width(desc[i]);
            addRenderableWidget(new StringWidget(cx - dw / 2, 30 + i * 11, dw + 2, 10,
                    Component.literal(desc[i]), font));
        }

        // ── Row 0: Footprint Size (cycle button) ──────────────────────────────
        int fpY = rowY(0);
        int fpLabelW = font.width("Footprint Size");
        addRenderableWidget(new StringWidget(cx - fpLabelW / 2, fpY - 12, fpLabelW + 2, 10,
                Component.literal("Footprint Size"), font));
        addRenderableWidget(
                Button.builder(Component.literal(FOOTPRINT_LABELS[footprintRadius]), btn -> {
                    footprintRadius = (footprintRadius + 1) % 3;
                    btn.setMessage(Component.literal(FOOTPRINT_LABELS[footprintRadius]));
                })
                .pos(cx - FIELD_W / 2, fpY)
                .size(FIELD_W, FIELD_H)
                .build());

        // ── Rows 1-5: text fields ─────────────────────────────────────────────
        for (int i = 0; i < 5; i++) {
            int y = rowY(i + 1);
            int lw = font.width(labels[i]);
            addRenderableWidget(new StringWidget(cx - lw / 2, y - 12, lw + 2, 10,
                    Component.literal(labels[i]), font));
            EditBox field = new EditBox(font, cx - FIELD_W / 2, y, FIELD_W, FIELD_H, Component.empty());
            field.setMaxLength(5);
            field.setValue(String.valueOf(initialValues[i]));
            fields[i] = field;
            addRenderableWidget(field);
        }

        // ── Track Other Players toggle ────────────────────────────────────────
        int toggleY = rowY(6) + 4;
        addRenderableWidget(
                Button.builder(
                        Component.literal("Track Other Players: " + (trackOtherPlayers ? "§aON" : "§cOFF")),
                        btn -> {
                            trackOtherPlayers = !trackOtherPlayers;
                            btn.setMessage(Component.literal("Track Other Players: "
                                    + (trackOtherPlayers ? "§aON" : "§cOFF")));
                        })
                        .pos(cx - 100, toggleY)
                        .size(200, 20)
                        .build());

        // ── Action buttons ────────────────────────────────────────────────────
        int btnY  = toggleY + 28;
        int btnW  = (this.width / 2) - 8;
        int doneX = cx - btnW - 4;
        int clrX  = cx + 4;

        addRenderableWidget(
                Button.builder(Component.literal("Done"), btn -> saveAndClose())
                        .pos(doneX, btnY).size(btnW, 20).build());

        addRenderableWidget(
                Button.builder(Component.literal("§cClear All Dimensions"), btn -> {
                    WalkDataStore.getInstance().clearAllDimensions();
                    btn.setMessage(Component.literal("§aAll data cleared!"));
                    btn.active = false;
                }).pos(clrX, btnY).size(btnW, 20).build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int rowY(int i) { return FIRST_ROW_Y + i * ROW_HEIGHT; }

    private int parseField(int index) {
        try {
            String text = fields[index].getValue().trim();
            if (text.isEmpty()) return initialValues[index];
            return Math.max(mins[index], Math.min(maxs[index], Integer.parseInt(text)));
        } catch (NumberFormatException e) {
            return initialValues[index];
        }
    }

    private void saveAndClose() {
        PathTracerConfig.footprintRadius   = footprintRadius;
        PathTracerConfig.minPassCount      = parseField(0);
        PathTracerConfig.maxPassCount      = Math.max(parseField(1), PathTracerConfig.minPassCount);
        PathTracerConfig.renderRadius      = parseField(2);
        PathTracerConfig.maxAgeDays        = parseField(3);
        PathTracerConfig.clearRadius       = parseField(4);
        PathTracerConfig.trackOtherPlayers = trackOtherPlayers;
        PathTracerConfig.save();
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
