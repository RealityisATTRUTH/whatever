package com.reality.mobcraft.mob.kit;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ability cooldowns, counted in server ticks. Kept in memory, so they reset
 * when the server restarts. They survive death and switching keys.
 */
public final class Cooldowns {

    private static final Map<UUID, Map<String, Integer>> READY_AT = new HashMap<>();

    private Cooldowns() {}

    public static void start(ServerPlayer player, String abilityId, int ticks) {
        READY_AT.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(abilityId, KitUtil.now(player) + ticks);
    }

    /** Ticks left, or 0 if ready. */
    public static int remaining(ServerPlayer player, String abilityId) {
        Map<String, Integer> map = READY_AT.get(player.getUUID());
        if (map == null) return 0;
        Integer readyAt = map.get(abilityId);
        if (readyAt == null) return 0;
        return Math.max(0, readyAt - KitUtil.now(player));
    }

    /** Clears every cooldown, used when a player switches mobs. */
    public static void clear(ServerPlayer player) {
        READY_AT.remove(player.getUUID());
    }
}
