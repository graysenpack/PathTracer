package com.pathtracer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a colored overlay on top of blocks the player has walked on.
 * Updated for Minecraft 26.1 (official Mojang mappings):
 *   WorldRenderEvents   → LevelRenderEvents
 *   WorldRenderContext  → LevelRenderContext
 *   MatrixStack         → PoseStack
 *   Vec3d               → Vec3
 *   RenderLayers        → RenderTypes
 *   context.matrices()  → context.poseStack()
 *   context.consumers() → context.bufferSource()
 *   context.worldState()→ context.levelState()
 *   vertex()/color()    → addVertex()/setColor() with ARGB int
 */
@Environment(EnvType.CLIENT)
public class PathRenderer {

    private static boolean overlayEnabled = true;

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            String state = overlayEnabled ? "§aEnabled" : "§cDisabled";
            // Mojang: sendOverlayMessage() for action bar (was sendMessage(text, true) in Yarn)
            client.player.sendOverlayMessage(
                    Component.literal("[PathTracer] Overlay " + state));
        }
    }

    public static boolean isOverlayEnabled() { return overlayEnabled; }

    public static void register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> renderOverlay(context));
    }

    // ── Core render logic ─────────────────────────────────────────────────────

    private static void renderOverlay(LevelRenderContext context) {
        if (!overlayEnabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        // Mojang: poseStack() (was matrices() in Yarn)
        PoseStack poseStack = context.poseStack();
        if (poseStack == null) return;

        Map<BlockPos, WalkData> walkMap = WalkDataStore.getInstance().getWalkMap();
        if (walkMap.isEmpty()) return;

        // Mojang: context.levelState().cameraRenderState.pos (same field path, new class name)
        Vec3     camPos    = context.levelState().cameraRenderState.pos;
        // Mojang: blockPosition() (was getBlockPos() in Yarn)
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

        // Mojang: bufferSource().getBuffer() (was consumers().getBuffer() in Yarn)
        // Mojang: RenderTypes.debugFilledBox() (was RenderLayers.debugFilledBox() in Yarn)
        VertexConsumer vertexConsumer =
                context.bufferSource().getBuffer(RenderTypes.debugFilledBox());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        // Mojang: poseStack.last().pose() (was matrices.peek().getPositionMatrix() in Yarn)
        Matrix4f mat = poseStack.last().pose();

        for (Map.Entry<BlockPos, WalkData> entry : toRender) {
            BlockPos pos  = entry.getKey();
            WalkData data = entry.getValue();

            // computeColor now returns an ARGB int (was int[] in 1.21.11)
            int color = computeColor(data.getCount());

            float x1 = pos.getX();
            float y  = pos.getY() + 1.002f;
            float z1 = pos.getZ();
            float x2 = x1 + 1.0f;
            float z2 = z1 + 1.0f;

            // Mojang: addVertex().setColor(argbInt) (was vertex().color(r,g,b,a) in Yarn)
            vertexConsumer.addVertex(mat, x1, y, z1).setColor(color);
            vertexConsumer.addVertex(mat, x1, y, z2).setColor(color);
            vertexConsumer.addVertex(mat, x2, y, z2).setColor(color);
            vertexConsumer.addVertex(mat, x2, y, z1).setColor(color);
        }

        poseStack.popPose();
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

        // Mojang: ARGB.color(alpha, red, green, blue) returns packed int
        return ARGB.color(a, r, g, 0);
    }

}
