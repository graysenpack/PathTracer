package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens to client ticks and records which block the player (and optionally
 * other nearby players) is walking on.
 * Updated for Minecraft 26.1 (official Mojang mappings).
 *
 * Tracking rules (applied to both local and other players):
 *   - Must be on the ground (not jumping/falling)
 *   - Not swimming
 *   - Not riding a vehicle
 *   - Not in creative/spectator flight or spectator mode
 *
 * Other-player tracking is opt-in via PathTracerConfig.trackOtherPlayers.
 */
@Environment(EnvType.CLIENT)
public class WalkTracker {

    private static BlockPos lastTrackedPos = null;

    // Last recorded ground position per other player — avoids recording the
    // same position every tick when a player stands still.
    private static final Map<UUID, BlockPos> otherPlayerLastPos = new HashMap<>();

    private static final int SAVE_INTERVAL_TICKS  = 200;
    private static final int PRUNE_INTERVAL_TICKS = 6000;

    private static int saveTimer  = 0;
    private static int pruneTimer = 0;

    public static void register() {

        // ── World join ────────────────────────────────────────────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String worldId     = resolveWorldId(client);
            String dimensionId = client.level != null
                    ? client.level.dimension().identifier().toString()
                    : "minecraft:overworld";
            WalkDataStore.getInstance().loadForWorld(worldId, dimensionId);
            lastTrackedPos = null;
            otherPlayerLastPos.clear();
            saveTimer  = 0;
            pruneTimer = 0;
        });

        // ── World disconnect ──────────────────────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WalkDataStore.getInstance().saveData();
            WalkDataStore.getInstance().clear();
            lastTrackedPos = null;
            otherPlayerLastPos.clear();
        });

        // ── Per-tick tracking ─────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null || client.level == null) return;

            // Detect dimension changes (portal travel) and swap data stores.
            String dimensionId = client.level.dimension().identifier().toString();
            WalkDataStore store = WalkDataStore.getInstance();
            if (!dimensionId.equals(store.getCurrentDimensionId())) {
                store.switchDimension(dimensionId);
                lastTrackedPos = null;
                otherPlayerLastPos.clear();
            }

            long currentGameDay = client.level.getGameTime() / 24000L;

            // ── Local player ─────────────────────────────────────────────────
            if (player.onGround() && !player.isSwimming()
                    && !player.isPassenger() && !player.getAbilities().flying) {
                BlockPos groundPos = resolveGroundPos(
                        player.blockPosition(), client.level.getBlockState(player.blockPosition()), client);
                if (!groundPos.equals(lastTrackedPos)) {
                    lastTrackedPos = groundPos;
                    recordIfAllowed(groundPos, store, currentGameDay, client);
                }
            }

            // ── Other players ─────────────────────────────────────────────────
            if (WalkDataStore.TRACK_OTHER_PLAYERS) {
                for (Player other : client.level.players()) {
                    if (other == player || other.isSpectator()) continue;
                    if (!other.onGround() || other.isSwimming()
                            || other.isPassenger() || other.getAbilities().flying) continue;

                    BlockPos groundPos = resolveGroundPos(
                            other.blockPosition(), client.level.getBlockState(other.blockPosition()), client);
                    UUID uuid = other.getUUID();
                    if (groundPos.equals(otherPlayerLastPos.get(uuid))) continue;
                    otherPlayerLastPos.put(uuid, groundPos);
                    recordIfAllowed(groundPos, store, currentGameDay, client);
                }
            }

            // ── Periodic save / prune ─────────────────────────────────────────
            if (++saveTimer >= SAVE_INTERVAL_TICKS) {
                saveTimer = 0;
                store.saveData();
            }
            if (++pruneTimer >= PRUNE_INTERVAL_TICKS) {
                pruneTimer = 0;
                store.pruneExpiredData(currentGameDay);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Determine the actual ground block, accounting for short/sink blocks. */
    private static BlockPos resolveGroundPos(BlockPos feetPos, BlockState feetBlock,
                                              Minecraft client) {
        boolean feetHasCollision = !feetBlock.isAir()
                && feetBlock.getFluidState().isEmpty()
                && !feetBlock.getCollisionShape(client.level, feetPos).isEmpty();
        return feetHasCollision ? feetPos : feetPos.below();
    }

    /** Record a footprint if the ground block is not on the ignore list.
     *  Explorer mode always uses 1×1 (radius 0); otherwise uses the configured radius. */
    private static void recordIfAllowed(BlockPos groundPos, WalkDataStore store,
                                         long currentGameDay, Minecraft client) {
        String blockId = BuiltInRegistries.BLOCK
                .getKey(client.level.getBlockState(groundPos).getBlock()).toString();
        if (WalkDataStore.IGNORED_BLOCKS.contains(blockId)) return;
        int radius = WalkDataStore.EXPLORER_MODE ? 0 : WalkDataStore.FOOTPRINT_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                store.recordWalk(groundPos.offset(dx, 0, dz), currentGameDay);
            }
        }
    }

    private static String resolveWorldId(Minecraft client) {
        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
            return "sp_" + levelName;
        }
        if (client.getCurrentServer() != null) {
            return "mp_" + client.getCurrentServer().ip;
        }
        return "unknown_world";
    }
}
