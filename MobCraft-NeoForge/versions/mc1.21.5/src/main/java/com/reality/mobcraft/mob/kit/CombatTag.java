package com.reality.mobcraft.mob.kit;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** When each player last hit or was hit by something. Used for "only out of combat" rules. */
public final class CombatTag {

    private static final Map<UUID, Integer> LAST_COMBAT = new HashMap<>();

    private CombatTag() {}

    static void mark(ServerPlayer player) {
        LAST_COMBAT.put(player.getUUID(), KitUtil.now(player));
    }

    /** True if the player hit or was hit in the last given ticks. */
    public static boolean inCombat(ServerPlayer player, int ticks) {
        Integer last = LAST_COMBAT.get(player.getUUID());
        return last != null && KitUtil.now(player) - last < ticks;
    }

    static void forget(ServerPlayer player) {
        LAST_COMBAT.remove(player.getUUID());
    }
}
