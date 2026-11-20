package com.reality.mobcraft.warden;

import com.reality.mobcraft.glow.MobGlow;
import com.reality.mobcraft.net.GhostSyncPayload.Ghost;
import com.reality.mobcraft.net.GhostSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Warden echolocation, server side.
 *
 * For every Warden player, records where each nearby enemy was on every tick.
 * Each tick it sends the Warden's client two delayed ghosts per enemy:
 *   white = where the enemy was 1 second ago  (vibration)
 *   red   = where the enemy was 10 seconds ago (scent)
 *
 * The client draws each ghost as animated vibration waves (SenseMarkers):
 * cyan for vibration, red for scent. Nothing is spawned, so nobody else can
 * see, hit or detect them.
 *
 * Positions are recorded the whole time someone is the Warden, so the
 * delayed ghosts are ready the moment an ability shows them. The two layers
 * are separate Warden abilities, shown only while that ability is active:
 *   Echolocation = the white vibration ghosts. Sneaking makes no vibration,
 *                  so ticks spent sneaking leave no white ghost.
 *   Sniffing     = the red scent ghosts. Scent stays even when sneaking.
 */
public final class WardenEcholocation {

    public static final int WHITE_DELAY = 20;    // 1 second
    public static final int RED_DELAY = 200;     // 10 seconds
    public static final double RANGE = 40.0;

    public static final int WHITE = 0xFFFFFF;
    public static final int RED = 0xFF2020;

    /**
     * Who counts as an enemy. Default: every other player.
     * The allegiance system replaces this later, for example to skip teammates.
     */
    public static BiPredicate<ServerPlayer, ServerPlayer> isEnemy = (warden, other) -> true;

    /** Warden UUID -> enemy UUID -> that enemy's recent positions. */
    private static final Map<UUID, Map<UUID, History>> WARDENS = new HashMap<>();

    private WardenEcholocation() {}

    public static boolean isEnabled(ServerPlayer warden) {
        return WARDENS.containsKey(warden.getUUID());
    }

    public static void enable(ServerPlayer warden) {
        WARDENS.computeIfAbsent(warden.getUUID(), id -> new HashMap<>());
    }

    /** Which layers this Warden sees. Both are on unless switched off. */
    public static void setLayers(ServerPlayer warden, boolean echolocation, boolean sniffing) {
        if (echolocation) HIDE_WHITE.remove(warden.getUUID()); else HIDE_WHITE.add(warden.getUUID());
        if (sniffing) HIDE_RED.remove(warden.getUUID()); else HIDE_RED.add(warden.getUUID());
    }

    private static final Set<UUID> HIDE_WHITE = new HashSet<>();
    private static final Set<UUID> HIDE_RED = new HashSet<>();

    public static void disable(ServerPlayer warden) {
        WARDENS.remove(warden.getUUID());
        // An empty list removes every ghost on the Warden's client.
        ServerPlayNetworking.send(warden, new GhostSyncPayload(List.of()));
    }

    /** Called once per server tick. */
    public static void tick(MinecraftServer server) {
        for (Map.Entry<UUID, Map<UUID, History>> entry : WARDENS.entrySet()) {
            ServerPlayer warden = server.getPlayerList().getPlayer(entry.getKey());
            if (warden == null) continue; // offline: keep the setting, skip the work

            Map<UUID, History> histories = entry.getValue();
            Set<UUID> stillTracked = new HashSet<>();
            List<Ghost> ghosts = new ArrayList<>();

            for (LivingEntity other : candidates(server, warden)) {
                if (other instanceof ServerPlayer otherPlayer && !isEnemy.test(warden, otherPlayer)) continue;

                History history = histories.computeIfAbsent(other.getUUID(), id -> new History());
                history.record(other.position(), other.getYRot(), other.isShiftKeyDown());
                stillTracked.add(other.getUUID());

                if (!HIDE_WHITE.contains(warden.getUUID())) {
                    addGhost(ghosts, history.ago(WHITE_DELAY), other.getId() * 2, WHITE, true);
                }
                if (!HIDE_RED.contains(warden.getUUID())) {
                    addGhost(ghosts, history.ago(RED_DELAY), other.getId() * 2 + 1, RED, false);
                }
            }

            // Forget enemies who left range, died, changed dimension or logged off.
            histories.keySet().removeIf(id -> !stillTracked.contains(id));

            // Only send when there's something to show, or once to clear old ghosts.
            boolean had = HAD_GHOSTS.contains(warden.getUUID());
            if (!ghosts.isEmpty() || had) ServerPlayNetworking.send(warden, new GhostSyncPayload(ghosts));
            if (ghosts.isEmpty()) HAD_GHOSTS.remove(warden.getUUID()); else HAD_GHOSTS.add(warden.getUUID());
        }
    }

    /** The blue of the echolocation glow, the same as the helmets' senses. */
    public static final int GLOW_BLUE = 0x20FFFF;

    /** Makes everything the Warden can hear glow blue on his screen for a moment. */
    public static void glowNearby(ServerPlayer warden, int ticks) {
        for (LivingEntity other : candidates(warden.level().getServer(), warden)) {
            if (other instanceof ServerPlayer otherPlayer && !isEnemy.test(warden, otherPlayer)) continue;
            MobGlow.glow(warden, other, GLOW_BLUE, ticks);
        }
    }

    /** The closest players and mobs in range, like a real Warden hears everything. */
    private static List<LivingEntity> candidates(MinecraftServer server, ServerPlayer warden) {
        List<LivingEntity> out = new ArrayList<>(warden.level().getEntitiesOfClass(LivingEntity.class,
                warden.getBoundingBox().inflate(RANGE),
                e -> e != warden && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
                        && warden.distanceToSqr(e) <= RANGE * RANGE));
        out.sort(java.util.Comparator.comparingDouble(warden::distanceToSqr));
        return out.size() > MAX_TRACKED ? out.subList(0, MAX_TRACKED) : out;
    }

    /** Ghosts are capped so a mob farm doesn't flood the screen. */
    private static final int MAX_TRACKED = 24;

    /** Wardens whose last update had ghosts, so an empty update is sent once to clear them. */
    private static final Set<UUID> HAD_GHOSTS = new HashSet<>();

    private static void addGhost(List<Ghost> ghosts, Sample sample, int key, int color, boolean sneakingHides) {
        if (sample == null || (sneakingHides && sample.silent())) return;
        ghosts.add(new Ghost(key, sample.pos().x, sample.pos().y, sample.pos().z, sample.yRot(), color));
    }

    // ---------------------------------------------------------------------

    private record Sample(Vec3 pos, float yRot, boolean silent) {}

    /** Fixed-size ring of the last RED_DELAY + 1 ticks for one enemy. */
    private static final class History {
        private final Sample[] samples = new Sample[RED_DELAY + 1];
        private int written = 0;

        void record(Vec3 pos, float yRot, boolean silent) {
            samples[written % samples.length] = new Sample(pos, yRot, silent);
            written++;
        }

        /** The sample from ticksAgo ticks ago, or null if we haven't tracked them that long. */
        Sample ago(int ticksAgo) {
            if (ticksAgo >= written || ticksAgo >= samples.length) return null;
            return samples[(written - 1 - ticksAgo) % samples.length];
        }
    }
}
