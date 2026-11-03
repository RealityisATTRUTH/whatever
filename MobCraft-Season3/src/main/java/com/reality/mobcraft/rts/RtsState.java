package com.reality.mobcraft.rts;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server side: who is in RTS mode right now. Their body is left standing
 * while they command from above, and it can be hurt: a hit closes RTS on
 * their game (RtsView). Cleared when they close RTS or disconnect.
 */
public final class RtsState {

    private static final Set<UUID> ACTIVE = new HashSet<>();

    private RtsState() {}

    public static boolean isActive(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID());
    }

    public static void set(ServerPlayer player, boolean active) {
        if (active) {
            ACTIVE.add(player.getUUID());
            com.reality.mobcraft.MobAdvancements.award(player, "commander");
        } else {
            ACTIVE.remove(player.getUUID());
        }
    }
}
