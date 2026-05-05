package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Listens to client ticks and records which block the player is walking on.
 * Updated for Minecraft 26.1 (official Mojang mappings).
 */
@Environment(EnvType.CLIENT)
public class WalkTracker {

    private static BlockPos lastTrackedPos = null;

    private static final int SAVE_INTERVAL_TICKS  = 200;
    private static final int PRUNE_INTERVAL_TICKS = 6000;
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
            LocalPlayer player = client.player;
            // Mojang: client.level (was client.world in Yarn)
            if (player == null || client.level == null) return;

            // Gate: only track genuine overland walking
            // Mojang: onGround() (was isOnGround() in Yarn)
            if (!player.onGround())              return;
            if (player.isSwimming())             return;
            // Mojang: isPassenger() (was hasVehicle() in Yarn)
            if (player.isPassenger())            return;
            if (player.getAbilities().flying)    return;

            // For most blocks, feet are in the air above the block.  But short
            // blocks like Dirt Path (15/16 height) and sink blocks like Soul Sand
            // / Mud partially swallow the player, so the feet block position IS
            // the block we want.  Rule: if the block at feetPos is solid
            // (non-air, non-liquid), use feetPos; otherwise fall back to below.
            BlockPos feetPos = player.blockPosition();
            BlockState feetBlock = client.level.getBlockState(feetPos);
            BlockPos groundPos = (!feetBlock.isAir() && feetBlock.getFluidState().isEmpty())
                    ? feetPos
                    : feetPos.below();

            if (!groundPos.equals(lastTrackedPos)) {
                lastTrackedPos = groundPos;
                // Mojang: getGameTime() (was getTime() in Yarn)
                long currentGameDay = client.level.getGameTime() / 24000L;
                WalkDataStore store = WalkDataStore.getInstance();

                for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx++) {
                    for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz++) {
                        store.recordWalk(groundPos.offset(dx, 0, dz), currentGameDay);
                    }
                }
            }

            if (++saveTimer >= SAVE_INTERVAL_TICKS) {
                saveTimer = 0;
                WalkDataStore.getInstance().saveData();
            }

            if (++pruneTimer >= PRUNE_INTERVAL_TICKS) {
                pruneTimer = 0;
                long currentGameDay = client.level.getGameTime() / 24000L;
                WalkDataStore.getInstance().pruneExpiredData(currentGameDay);
            }
        });
    }

    private static String resolveWorldId(Minecraft client) {
        // Mojang: hasSingleplayerServer() + getSingleplayerServer() (were isInSingleplayer/getServer in Yarn)
        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
            return "sp_" + levelName;
        }
        // Mojang: getCurrentServer() (was getCurrentServerEntry() in Yarn), .ip (was .address)
        if (client.getCurrentServer() != null) {
            return "mp_" + client.getCurrentServer().ip;
        }
        return "unknown_world";
    }
}
