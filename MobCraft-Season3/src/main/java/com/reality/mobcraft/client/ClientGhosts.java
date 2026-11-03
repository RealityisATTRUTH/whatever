package com.reality.mobcraft.client;

import com.reality.mobcraft.net.GhostSyncPayload;
import com.reality.mobcraft.net.GhostSyncPayload.Ghost;
import com.reality.mobcraft.warden.WardenEcholocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.world.level.gameevent.EntityPositionSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The echolocation markers this client should show right now: where each
 * nearby creature was a moment ago (vibration) or 10 seconds ago (scent).
 * Each sync from the server is the full list.
 *
 * Vibrations travel: once a second, each creature that made a sound sends
 * the game's own vibration particle from where it was over to you, the way
 * a sculk sensor's signal flies to a shrieker and a Warden hears a player.
 * Scent stays put as a red marker where the creature was (SenseMarkers).
 */
public final class ClientGhosts {

    private static final int SEND_EVERY = 20; // a new vibration each second per creature
    private static List<Ghost> ghosts = List.of();
    private static ClientLevel ghostLevel;
    /** Ghost key -> the tick its last vibration was sent. */
    private static final Map<Integer, Long> LAST_SENT = new HashMap<>();
    private static long ticks;

    private ClientGhosts() {}

    public static void sync(Minecraft client, GhostSyncPayload payload) {
        ghostLevel = client.level;
        ghosts = List.copyOf(payload.ghosts());
    }

    /** The current markers, or none after a world or dimension change. */
    public static List<Ghost> current(Minecraft client) {
        if (client.level == null || client.level != ghostLevel) return List.of();
        return ghosts;
    }

    /** Called every client tick: sends vibrations from where each creature was, to you. */
    public static void tick(Minecraft client) {
        ticks++;
        if (client.player == null) return;
        List<Ghost> now = current(client);
        if (now.isEmpty()) {
            LAST_SENT.clear();
            return;
        }
        ClientLevel level = client.level;
        for (Ghost ghost : now) {
            if (ghost.color() != WardenEcholocation.WHITE) continue; // scent doesn't travel
            Long last = LAST_SENT.get(ghost.key());
            if (last != null && ticks - last < SEND_EVERY) continue;
            LAST_SENT.put(ghost.key(), ticks);
            double y = ghost.y() + 1.0;
            double distance = Math.sqrt(client.player.distanceToSqr(ghost.x(), y, ghost.z()));
            int arrival = (int) Math.max(8, Math.min(40, distance)); // about a block a tick, like sculk
            // Aimed at your waist, not your eyes, so you see them land instead of
            // having them streak past the camera.
            VibrationParticleOption vibration = new VibrationParticleOption(
                    new EntityPositionSource(client.player, client.player.getBbHeight() * 0.45f), arrival);
            level.addAlwaysVisibleParticle(vibration, ghost.x(), y, ghost.z(), 0, 0, 0);
        }
        if (LAST_SENT.size() > 256) LAST_SENT.clear();
    }

    /** Forget everything, for disconnects. */
    public static void reset() {
        ghosts = List.of();
        ghostLevel = null;
        LAST_SENT.clear();
    }
}
