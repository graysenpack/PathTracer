package com.pathtracer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
            String worldId     = resolveWorldId(client);
            String dimensionId = client.level != null
                    ? client.level.dimension().identifier().toString()
                    : "minecraft:overworld";
            WalkDataStore.getInstance().loadForWorld(worldId, dimensionId);
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
            if (player == null || client.level == null) return;

            // Detect dimension changes (portal travel) and swap data stores.
            String dimensionId = client.level.dimension().identifier().toString();
            WalkDataStore store = WalkDataStore.getInstance();
            if (!dimensionId.equals(store.getCurrentDimensionId())) {
                store.switchDimension(dimensionId);
                lastTrackedPos = null;
            }

            // Gate: only track genuine overland walking
            if (!player.onGround())              return;
            if (player.isSwimming())             return;
            // Mojang: isPassenger() (was hasVehicle() in Yarn)
            if (player.isPassenger())            return;
            if (player.getAbilities().flying)    return;

            // Determine which block the player is actually standing on.
            // Soul Sand / Mud / Dirt Path have a collision shape that reaches
            // the player's feet, so feetPos IS the block we want.
            // Flowers, grass, and other passable blocks have an empty collision
            // shape — the player walks through them — so we fall back to the
            // solid block below, avoiding a spurious overlay on the plant.
            BlockPos feetPos = player.blockPosition();
            BlockState feetBlock = client.level.getBlockState(feetPos);
            boolean feetHasCollision = !feetBlock.isAir()
                    && feetBlock.getFluidState().isEmpty()
                    && !feetBlock.getCollisionShape(client.level, feetPos).isEmpty();
            BlockPos groundPos = feetHasCollision ? feetPos : feetPos.below();

            if (!groundPos.equals(lastTrackedPos)) {
                lastTrackedPos = groundPos;

                // Skip blocks the player should not leave a trail on (grass, flowers, etc.).
                String blockId = BuiltInRegistries.BLOCK
                        .getKey(client.level.getBlockState(groundPos).getBlock()).toString();
                if (!WalkDataStore.IGNORED_BLOCKS.contains(blockId)) {
                    long currentGameDay = client.level.getGameTime() / 24000L;

                    for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx++) {
                        for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz++) {
                            store.recordWalk(groundPos.offset(dx, 0, dz), currentGameDay);
                        }
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
