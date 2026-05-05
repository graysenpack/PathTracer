package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a colored overlay on top of blocks the player has walked on.
 *
 * Color gradient (all values tweakable in WalkDataStore):
 *   - Below MIN_WALK_THRESHOLD  →  invisible
 *   - At MIN_WALK_THRESHOLD     →  faint yellow  (255, 255, 0, alpha ~50)
 *   - At MAX_WALK_COUNT         →  bright green  (0,   255, 0, alpha ~160)
 *
 * Iris shader compatibility:
 *   Without Iris (or Iris active but no shader pack loaded):
 *     – Renders in BEFORE_TRANSLUCENT, which is the correct semantic slot.
 *   With Iris shaders active:
 *     – Renders in END_MAIN, which fires after Iris has finished compositing
 *       its deferred framebuffers, so the overlay paints on top correctly.
 *   Detection is done at render time via reflection so no compile-time
 *   dependency on Iris is required.
 */
@Environment(EnvType.CLIENT)
public class PathRenderer {

    private static boolean overlayEnabled = true;

    // ── Iris reflection cache ─────────────────────────────────────────────────
    private static boolean   irisReflInitialized = false;
    private static Method    irisGetInstance     = null;
    private static Method    irisIsShaderPackInUse = null;

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String state = overlayEnabled ? "§aEnabled" : "§cDisabled";
            // Show in the action bar (above hotbar), not in chat
            client.player.sendMessage(Text.literal("[PathTracer] Overlay " + state), true);
        }
    }

    public static boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public static void register() {
        // Without Iris shaders: render in BEFORE_TRANSLUCENT (ideal depth slot).
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            if (!isShadersActive()) renderOverlay(context);
        });

        // With Iris shaders: render in END_MAIN, which fires after Iris has
        // composited its deferred pass.  This ensures the overlay is drawn on
        // top of the shader output rather than being swallowed by it.
        WorldRenderEvents.END_MAIN.register(context -> {
            if (isShadersActive()) renderOverlay(context);
        });
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

        // Camera position via the 1.21.11 render-state system
        Vec3d camPos = context.worldState().cameraRenderState.pos;
        BlockPos playerPos = client.player.getBlockPos();
        int      radius   = WalkDataStore.RENDER_RADIUS;
        int      minWalks = WalkDataStore.MIN_WALK_THRESHOLD;

        // ── Pre-filter: collect only entries that should be drawn ─────────────
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

        // ── Vertex consumer for transparent coloured quads ────────────────────
        // DEBUG_FILLED_BOX is a translucent POSITION_COLOR render layer.
        // The world renderer flushes the consumer automatically after the event.
        VertexConsumer vertexConsumer =
                context.consumers().getBuffer(RenderLayers.debugFilledBox());

        // Translate from camera-relative space to world space
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        for (Map.Entry<BlockPos, WalkData> entry : toRender) {
            BlockPos pos  = entry.getKey();
            WalkData data = entry.getValue();

            int[] color = computeColor(data.getCount());
            int   r = color[0], g = color[1], b = color[2], a = color[3];

            float x1 = pos.getX();
            float y  = pos.getY() + 1.002f;   // just above the block's top face
            float z1 = pos.getZ();
            float x2 = x1 + 1.0f;
            float z2 = z1 + 1.0f;

            vertexConsumer.vertex(mat, x1, y, z1).color(r, g, b, a);
            vertexConsumer.vertex(mat, x1, y, z2).color(r, g, b, a);
            vertexConsumer.vertex(mat, x2, y, z2).color(r, g, b, a);
            vertexConsumer.vertex(mat, x2, y, z1).color(r, g, b, a);
        }

        matrices.pop();
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
            // green → yellow: ramp red up, keep green at 255
            float s = t / third;
            r = Math.round(255 * s);
            g = 255;
        } else {
            // yellow → orange → red: keep red at 255, ramp green down
            float s = (t - third) / (2.0f * third);
            r = 255;
            g = Math.round(255 * (1.0f - s));
        }

        int a = Math.round(50 + 130 * t);   // 50 → 180

        return new int[]{r, g, 0, a};
    }

    // ── Iris detection ────────────────────────────────────────────────────────

    /**
     * Returns true if Iris is loaded AND a shader pack is currently active.
     *
     * Uses reflection so PathTracer compiles and runs regardless of whether
     * Iris is installed.  Method objects are cached after the first call so
     * the reflection overhead is paid only once.
     */
    private static boolean isShadersActive() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return false;

        // Lazy-initialise reflection cache
        if (!irisReflInitialized) {
            irisReflInitialized = true;
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisGetInstance       = apiClass.getMethod("getInstance");
                irisIsShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            } catch (Exception e) {
                // Iris installed but API unavailable / changed — treat as inactive
            }
        }

        if (irisGetInstance == null || irisIsShaderPackInUse == null) return false;
        try {
            Object instance = irisGetInstance.invoke(null);
            return (Boolean) irisIsShaderPackInUse.invoke(instance);
        } catch (Exception e) {
            return false;
        }
    }
}
