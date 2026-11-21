package com.reality.mobcraft.warden;

import com.reality.mobcraft.mob.MobAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * The Echolocation Markers setting in /mobcraftsettings. On by default.
 * Switching it off hides the vibration and scent markers and the blue glow,
 * both for the Warden's own senses and for bat ear or warden tendril helmets.
 * Saved on the player and kept through death.
 */
public final class Senses {

    public static final String SETTING = "senses";

    private Senses() {}

    /** Works on both sides, so the settings page can show the current state. */
    public static boolean isOn(Player player) {
        return !Boolean.TRUE.equals(player.getAttached(MobAttachments.SENSES_OFF));
    }

    /** Server only. */
    public static void set(Player player, boolean on) {
        if (on) {
            player.removeAttached(MobAttachments.SENSES_OFF);
        } else {
            player.setAttached(MobAttachments.SENSES_OFF, true);
        }
    }
}
