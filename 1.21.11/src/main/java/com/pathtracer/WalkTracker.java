package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens to client ticks and records which block the player (and optionally
 * other nearby players) is walking on.
 *
 * Tracking rules (applied to both local and other players):
 *   - Must be on the ground (not jumping/falling)
 *   - Not swimming
 *   - Not riding a vehicle
 *   - Not in creative/spectator flight or spectator mode
 *
 * Other-player tracking is opt-in via PathTracerConfig.trackOtherPlayers.
 * Data is stored in the same walkMap so shared paths naturally accumulate.
 */
@Environment(EnvType.CLIENT)
public class WalkTracker {

    private static BlockPos lastTrackedPos = null;

    // Last recorded ground position per other player — avoids recording the
    // same position every tick when a player stands still.
    private static final Map<UUID, BlockPos> otherPlayerLastPos = new HashMap<>();

    private static final int SAVE_INTERVAL_TICKS  = 200;   // every 10 s
    private static final int PRUNE_INTERVAL_TICKS = 6000;  // every 5 min
    private static final int FOOTPRINT_RADIUS     = 1;

    private static int saveTimer  = 0;
    private static int pruneTimer = 0;

    public static void register() {

        // ── World join ────────────────────────────────────────────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String worldId     = resolveWorldId(client);
            String dimensionId = client.world != null
                    ? client.world.getRegistryKey().getValue().toString()
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
            ClientPlayerEntity player = client.player;
            if (player == null || client.world == null) return;

            // Detect dimension changes (portal travel) and swap data stores.
            String dimensionId = client.world.getRegistryKey().getValue().toString();
            WalkDataStore store = WalkDataStore.getInstance();
            if (!dimensionId.equals(store.getCurrentDimensionId())) {
                store.switchDimension(dimensionId);
                lastTrackedPos = null;
                otherPlayerLastPos.clear();
            }

            long currentGameDay = client.world.getTime() / 24000L;

            // ── Local player ─────────────────────────────────────────────────
            if (player.isOnGround() && !player.isSwimming()
                    && !player.hasVehicle() && !player.getAbilities().flying) {
                BlockPos groundPos = resolveGroundPos(player.getBlockPos(), client.world.getBlockState(player.getBlockPos()), client);
                if (!groundPos.equals(lastTrackedPos)) {
                    lastTrackedPos = groundPos;
                    recordIfAllowed(groundPos, store, currentGameDay, client);
                }
            }

            // ── Other players ─────────────────────────────────────────────────
            if (WalkDataStore.TRACK_OTHER_PLAYERS) {
                for (PlayerEntity other : client.world.getPlayers()) {
                    if (other == player || other.isSpectator()) continue;
                    if (!other.isOnGround() || other.isSwimming()
                            || other.hasVehicle() || other.getAbilities().flying) continue;

                    BlockPos groundPos = resolveGroundPos(other.getBlockPos(), client.world.getBlockState(other.getBlockPos()), client);
                    UUID uuid = other.getUuid();
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
                                              MinecraftClient client) {
        boolean feetHasCollision = !feetBlock.isAir()
                && feetBlock.getFluidState().isEmpty()
                && !feetBlock.getCollisionShape(client.world, feetPos).isEmpty();
        return feetHasCollision ? feetPos : feetPos.down();
    }

    /** Record a 3×3 footprint if the ground block is not on the ignore list. */
    private static void recordIfAllowed(BlockPos groundPos, WalkDataStore store,
                                         long currentGameDay, MinecraftClient client) {
        String blockId = Registries.BLOCK
                .getId(client.world.getBlockState(groundPos).getBlock()).toString();
        if (WalkDataStore.IGNORED_BLOCKS.contains(blockId)) return;
        for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx++) {
            for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz++) {
                store.recordWalk(groundPos.add(dx, 0, dz), currentGameDay);
            }
        }
    }

    /**
     * Build a stable identifier for the current world/server so each gets its
     * own save directory.
     *   - Single-player: "sp_<level name>"
     *   - Multiplayer:   "mp_<server address>"
     */
    private static String resolveWorldId(MinecraftClient client) {
        if (client.isInSingleplayer() && client.getServer() != null) {
            String levelName = client.getServer().getSaveProperties().getLevelName();
            return "sp_" + levelName;
        }
        if (client.getCurrentServerEntry() != null) {
            return "mp_" + client.getCurrentServerEntry().address;
        }
        return "unknown_world";
    }
}
