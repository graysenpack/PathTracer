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

        Vec3     cam       = context.levelState().cameraRenderState.pos;
        BlockPos playerPos = client.player.blockPosition();
        int      radius    = WalkDataStore.RENDER_RADIUS;
        int      minWalks  = WalkDataStore.MIN_WALK_THRESHOLD;

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

        // Submit through bufferSource so Iris (if active) can intercept and
        // route via the assigned BASIC program. Camera-relative coordinates
        // avoid any PoseStack accumulation issues.
        MultiBufferSource.BufferSource bufferSource = context.bufferSource();
        VertexConsumer vc = bufferSource.getBuffer(RenderTypes.debugFilledBox());

        for (Map.Entry<BlockPos, WalkData> entry : toRender) {
            BlockPos pos   = entry.getKey();
            WalkData data  = entry.getValue();
            int      color = computeColor(data.getCount());

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

}
