package com.reality.mobcraft.mob.kit;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Makes sure each death drops things once. Some deaths fire the death event
 * more than once before the player respawns, which was stacking up drops.
 * Cleared as soon as the player is alive again.
 */
public final class DeathOnce {

    private static final Set<String> DONE = new HashSet<>();

    private DeathOnce() {}

    /** True the first time this player's death is handled for this purpose. */
    public static boolean first(ServerPlayer player, String purpose) {
        return DONE.add(player.getUUID() + ":" + purpose);
    }

    static void reset(ServerPlayer player) {
        String prefix = player.getUUID() + ":";
        DONE.removeIf(key -> key.startsWith(prefix));
    }
}
