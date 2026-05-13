package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a colored overlay on top of blocks the player has walked on.
 *
 * Color gradient (all values tweakable in WalkDataStore):
 *   - Below MIN_WALK_THRESHOLD  →  invisible
 *   - At MIN_WALK_THRESHOLD     →  faint green   (0,   255, 0, alpha ~50)
 *   - At MAX_WALK_COUNT         →  bright red    (255,   0, 0, alpha ~180)
 *
 * Iris shader compatibility: registers on END_MAIN instead of BEFORE_TRANSLUCENT
 * so the overlay draws after Iris's gbuffer/composite passes, using the same
 * VertexConsumerProvider that vanilla uses for debug layers.
 */
@Environment(EnvType.CLIENT)
public class PathRenderer {

    private static boolean overlayEnabled = true;

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String state = overlayEnabled ? "§aEnabled" : "§cDisabled";
            client.player.sendMessage(Text.literal("[PathTracer] Overlay " + state), true);
        }
    }

    public static boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public static void register() {
        // Tell Iris to route our pipeline through its BASIC program so it is
        // composited correctly rather than discarded or overwritten.
        // Using reflection avoids a compile-time dependency on the Iris jar.
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            assignIrisPipeline();
        }

        WorldRenderEvents.END_MAIN.register(context -> renderOverlay(context));
    }

    private static void assignIrisPipeline() {
        try {
            Class<?> apiClass     = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object   api          = apiClass.getMethod("getInstance").invoke(null);
            Object   basic        = programClass.getField("BASIC").get(null);
            apiClass.getMethod("assignPipeline",
                            com.mojang.blaze3d.pipeline.RenderPipeline.class,
                            programClass)
                    .invoke(api, RenderLayers.debugFilledBox().getRenderPipeline(), basic);
        } catch (Exception ignored) {}
    }

    // ── Core render logic ─────────────────────────────────────────────────────

    private static void renderOverlay(WorldRenderContext context) {
        if (!overlayEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        MatrixStack matrices = context.matrices();
        if (matrices == null) return;

        Map<BlockPos, WalkData> walkMap = WalkDataStore.getInstance().getWalkMap();
        if (walkMap.isEmpty()) return;

        boolean explorerMode = WalkDataStore.EXPLORER_MODE;
        long currentGameDay  = client.world.getTime() / 24000L;

        Vec3d camPos = context.worldState().cameraRenderState.pos;
        BlockPos playerPos = client.player.getBlockPos();
        int      radius   = WalkDataStore.RENDER_RADIUS;
        int      minWalks = explorerMode ? 1 : WalkDataStore.MIN_WALK_THRESHOLD;

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

        var layer = RenderLayers.debugFilledBox();
        VertexConsumer vertexConsumer = context.consumers().getBuffer(layer);

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        for (Map.Entry<BlockPos, WalkData> entry : toRender) {
            BlockPos pos  = entry.getKey();
            WalkData data = entry.getValue();

            int[] color = explorerMode
                    ? computeExplorerColor(currentGameDay - data.getLastGameDay())
                    : computeColor(data.getCount());
            int   r = color[0], g = color[1], b = color[2], a = color[3];

            float x1 = pos.getX();
            float y  = pos.getY() + 1.002f;
            float z1 = pos.getZ();
            float x2 = x1 + 1.0f;
            float z2 = z1 + 1.0f;

            vertexConsumer.vertex(mat, x1, y, z1).color(r, g, b, a);
            vertexConsumer.vertex(mat, x1, y, z2).color(r, g, b, a);
            vertexConsumer.vertex(mat, x2, y, z2).color(r, g, b, a);
            vertexConsumer.vertex(mat, x2, y, z1).color(r, g, b, a);
        }

        matrices.pop();

        // Flush immediately while the 3D camera matrices are still active.
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    // ── Color math ────────────────────────────────────────────────────────────

    /**
     * Maps walk count to an RGBA color array (each value 0–255).
     *
     * Four-stop heat-map gradient (light → heavy use):
     *   t = 0.00  →  green   (  0, 255, 0)  barely walked
     *   t = 0.33  →  yellow  (255, 255, 0)
     *   t = 0.67  →  orange  (255, 128, 0)
     *   t = 1.00  →  red     (255,   0, 0)  heavily walked
     *
     * Alpha also scales with use: 50 (faint) → 180 (solid).
     */
    private static int[] computeColor(int count) {
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

        return new int[]{r, g, 0, a};
    }

    /**
     * 14 fixed colour stops spanning lime green → green → cyan → blue → dark blue → dark grey.
     * Stop 0 is always lime green; stop 13 is always dark grey (same as expired).
     * Each entry is {R, G, B, A}.
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
     * Maps block age to a colour by evenly stretching the 14-stop palette across
     * the configured gradient window.
     *
     * Day 0 always maps to stop 0 (lime green).
     * Day gradientDays−1 always maps to stop 13 (dark grey).
     * Ages ≥ gradientDays return the same dark grey (seamless expiry).
     *
     * Formula: stopIndex = round(age × 13 / (gradientDays − 1))
     */
    private static int[] computeExplorerColor(long ageDays) {
        int gradDays = WalkDataStore.EXPLORER_GRADIENT_DAYS;
        if (ageDays >= gradDays) return EXPLORER_EXPIRED;
        if (gradDays == 1)       return EXPLORER_STOPS[0];
        int idx = (int) Math.round(ageDays * 13.0 / (gradDays - 1));
        return EXPLORER_STOPS[Math.min(idx, 13)];
    }

}
