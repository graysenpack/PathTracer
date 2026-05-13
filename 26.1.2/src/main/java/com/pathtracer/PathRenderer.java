package com.pathtracer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a colored overlay on top of blocks the player has walked on.
 * Updated for Minecraft 26.1 (official Mojang mappings).
 *
 * Iris shader compatibility: at mod init we call IrisApi.assignPipeline() to
 * register our debugFilledBox pipeline under IrisProgram.BASIC. This tells
 * Iris to route our geometry through its own rendering pipeline so it gets
 * included in compositing rather than being overwritten by finalizeLevelRendering().
 *
 * Vertices are submitted with camera-relative coordinates (world pos - cam pos)
 * so RenderSystem's model-view (camera rotation) transforms them correctly.
 * We call endBatch() immediately after submission to flush while the 3D camera
 * matrices are still active.
 */
@Environment(EnvType.CLIENT)
public class PathRenderer {

    private static boolean overlayEnabled = true;

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            String state = overlayEnabled ? "§aEnabled" : "§cDisabled";
            client.player.sendOverlayMessage(
                    Component.literal("[PathTracer] Overlay " + state));
        }
    }

    public static boolean isOverlayEnabled() { return overlayEnabled; }

    public static void register() {
        // Tell Iris to route our pipeline through its BASIC program so it is
        // composited correctly rather than discarded or overwritten.
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            IrisApi.getInstance().assignPipeline(
                    RenderTypes.debugFilledBox().pipeline(),
                    IrisProgram.BASIC);
        }

        LevelRenderEvents.END_MAIN.register(PathRenderer::renderOverlay);
    }

    // ── Core render logic ─────────────────────────────────────────────────────

    private static void renderOverlay(LevelRenderContext context) {
        if (!overlayEnabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Map<BlockPos, WalkData> walkMap = WalkDataStore.getInstance().getWalkMap();
        if (walkMap.isEmpty()) return;

        boolean explorerMode = WalkDataStore.EXPLORER_MODE;
        long currentGameDay  = client.level.getGameTime() / 24000L;

        Vec3     cam       = context.levelState().cameraRenderState.pos;
        BlockPos playerPos = client.player.blockPosition();
        int      radius    = WalkDataStore.RENDER_RADIUS;
        int      minWalks  = explorerMode ? 1 : WalkDataStore.MIN_WALK_THRESHOLD;

        List<Map.Entry<BlockPos, WalkData>> toRender = new ArrayList<>();
        for (Map.Entry<BlockPos, WalkData> entry : walkMap.entrySet()) {
            BlockPos pos  = entry.getKey();
            WalkData data = entry.getValue();
            if (data.getCount() < minWalks) continue;
            if (Math.abs(pos.getX() - playerPos.getX()) > radius) continue;
            if (Math.abs(pos.getZ() - playerPos.getZ()) > radius) continue;
            toRender.add(entry);
        }
        if (toRender.isEmpty()) return;

        MultiBufferSource.BufferSource bufferSource = context.bufferSource();
        VertexConsumer vc = bufferSource.getBuffer(RenderTypes.debugFilledBox());

        for (Map.Entry<BlockPos, WalkData> entry : toRender) {
            BlockPos pos   = entry.getKey();
            WalkData data  = entry.getValue();
            int      color = explorerMode
                    ? computeExplorerColor(currentGameDay - data.getLastGameDay())
                    : computeColor(data.getCount());

            float x1 = pos.getX() - (float) cam.x;
            float y  = pos.getY() + 1.002f - (float) cam.y;
            float z1 = pos.getZ() - (float) cam.z;
            float x2 = x1 + 1.0f;
            float z2 = z1 + 1.0f;

            vc.addVertex(x1, y, z1).setColor(color);
            vc.addVertex(x1, y, z2).setColor(color);
            vc.addVertex(x2, y, z2).setColor(color);
            vc.addVertex(x2, y, z1).setColor(color);
        }

        // Flush immediately while the 3D camera matrices are still active.
        bufferSource.endBatch(RenderTypes.debugFilledBox());
    }

    // ── Color math ────────────────────────────────────────────────────────────

    /**
     * Four-stop heat-map gradient, returned as a packed ARGB int:
     *   t = 0.00  →  green   (  0, 255, 0)
     *   t = 0.33  →  yellow  (255, 255, 0)
     *   t = 0.67  →  orange  (255, 128, 0)
     *   t = 1.00  →  red     (255,   0, 0)
     *
     * Alpha scales 50 → 180 with use.
     */
    private static int computeColor(int count) {
        int   minCount = WalkDataStore.MIN_WALK_THRESHOLD;
        int   maxCount = WalkDataStore.MAX_WALK_COUNT;
        float t = Math.min(1.0f, (float)(count - minCount) / (float)(maxCount - minCount));

        int r, g;
        final float third = 1.0f / 3.0f;

        if (t < third) {
            float s = t / third;
            r = Math.round(255 * s);
            g = 255;
        } else {
            float s = (t - third) / (2.0f * third);
            r = 255;
            g = Math.round(255 * (1.0f - s));
        }

        int a = Math.round(50 + 130 * t);

        return ARGB.color(a, r, g, 0);
    }

    /**
     * 14 fixed colour stops spanning lime green → green → cyan → blue → dark blue → dark grey.
     * Stop 0 = lime green; stop 13 = dark grey (same as expired).
     */
    private static final int[][] EXPLORER_STOPS = {
        { 50, 255,  50, 200},  //  0 – lime green
        {  0, 230,  70, 195},  //  1 – bright green
        {  0, 200,  80, 190},  //  2 – green
        {  0, 195, 130, 185},  //  3 – green-teal
        {  0, 205, 195, 180},  //  4 – teal
        {  0, 185, 230, 175},  //  5 – cyan
        {  0, 150, 255, 170},  //  6 – light blue
        {  0, 100, 255, 165},  //  7 – blue
        { 30,  60, 230, 160},  //  8 – royal blue
        { 40,  30, 200, 155},  //  9 – medium blue
        { 20,  20, 170, 150},  // 10 – dark blue
        { 10,  10, 140, 145},  // 11 – navy
        { 10,  10, 110, 140},  // 12 – dark navy
        { 90,  90,  90, 130},  // 13 – dark grey (gradient end = expired)
    };
    private static final int[] EXPLORER_EXPIRED = {90, 90, 90, 130};

    /**
     * Evenly stretches the 14-stop palette across the gradient window.
     * Day 0 = stop 0 (lime green); day gradientDays−1 = stop 13 (dark grey).
     * Ages ≥ gradientDays return the same dark grey (seamless expiry).
     */
    private static int computeExplorerColor(long ageDays) {
        int gradDays = WalkDataStore.EXPLORER_GRADIENT_DAYS;
        if (ageDays >= gradDays) return ARGB.color(EXPLORER_EXPIRED[3], EXPLORER_EXPIRED[0], EXPLORER_EXPIRED[1], EXPLORER_EXPIRED[2]);
        int[] c = (gradDays == 1) ? EXPLORER_STOPS[0]
                : EXPLORER_STOPS[Math.min((int) Math.round(ageDays * 13.0 / (gradDays - 1)), 13)];
        return ARGB.color(c[3], c[0], c[1], c[2]);
    }

}
