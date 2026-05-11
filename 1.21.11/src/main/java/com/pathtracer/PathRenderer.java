package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
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
            // Show in the action bar (above hotbar), not in chat
            client.player.sendMessage(Text.literal("[PathTracer] Overlay " + state), true);
        }
    }

    public static boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public static void register() {
        WorldRenderEvents.END_MAIN.register(context -> renderOverlay(context));
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

}
