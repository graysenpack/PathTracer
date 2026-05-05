package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Listens to client ticks and records which block the player is walking on.
 *
 * Only counts movement that is:
 *   - On the ground (not jumping / falling)
 *   - Not swimming
 *   - Not riding a vehicle (boat, horse, minecart, etc.)
 *   - Not in creative/spectator flight
 *
 * Data is saved periodically while playing and on world disconnect.
 */
@Environment(EnvType.CLIENT)
public class WalkTracker {

    private static BlockPos lastTrackedPos = null;

    // How often (in ticks) to auto-save and to prune old data.
    // 20 ticks = 1 second.
    private static final int SAVE_INTERVAL_TICKS  = 200;   // every 10 s
    private static final int PRUNE_INTERVAL_TICKS = 6000;  // every 5 min

    /**
     * Radius of the footprint recorded on each step.
     *   0 → just the block under the player (single block)
     *   1 → 3×3 area (center + all 8 neighbours)
     *
     * A radius of 1 fills in short jump gaps and produces naturally wider
     * paths that are easier to read at a glance.
     */
    private static final int FOOTPRINT_RADIUS = 1;

    private static int saveTimer  = 0;
    private static int pruneTimer = 0;

    public static void register() {

        // ── World join ────────────────────────────────────────────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String worldId = resolveWorldId(client);
            WalkDataStore.getInstance().loadForWorld(worldId);
            lastTrackedPos = null;
            saveTimer      = 0;
            pruneTimer     = 0;
        });

        // ── World disconnect ──────────────────────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WalkDataStore.getInstance().saveData();
            WalkDataStore.getInstance().clear();
            lastTrackedPos = null;
        });

        // ── Per-tick tracking ─────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null || client.world == null) return;

            // Gate: only track genuine overland walking
            if (!player.isOnGround())           return;
            if (player.isSwimming())             return;
            if (player.hasVehicle())             return;
            if (player.getAbilities().flying)    return;

            // The block the player is physically standing ON is one block below
            // their feet block position.  E.g. feet at y=65 → standing on y=64.
            BlockPos groundPos = player.getBlockPos().down();

            if (!groundPos.equals(lastTrackedPos)) {
                lastTrackedPos = groundPos;
                // Divide total world ticks by 24 000 to get the current in-game day number.
                long currentGameDay = client.world.getTime() / 24000L;
                WalkDataStore store = WalkDataStore.getInstance();

                // Record the center block and all neighbours within FOOTPRINT_RADIUS.
                // A 3×3 footprint (radius = 1) naturally bridges 1–2 block jump gaps:
                // the landing block's neighbourhood overlaps with the takeoff block's,
                // so short jumps leave no visible gap in the overlay.
                for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx++) {
                    for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz++) {
                        store.recordWalk(groundPos.add(dx, 0, dz), currentGameDay);
                    }
                }
            }

            // Periodic save
            if (++saveTimer >= SAVE_INTERVAL_TICKS) {
                saveTimer = 0;
                WalkDataStore.getInstance().saveData();
            }

            // Periodic prune of expired data
            if (++pruneTimer >= PRUNE_INTERVAL_TICKS) {
                pruneTimer = 0;
                long currentGameDay = client.world.getTime() / 24000L;
                WalkDataStore.getInstance().pruneExpiredData(currentGameDay);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Build a stable identifier for the current world/server so each gets its
     * own save file.
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
