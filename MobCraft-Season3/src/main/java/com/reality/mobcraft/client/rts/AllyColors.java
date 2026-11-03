package com.reality.mobcraft.client.rts;

/**
 * Each teammate's color in RTS: their mobs on the minimap, the marker over
 * their mobs, and their pings. Picked from the player's UUID, so a teammate
 * always has the same color on everyone's screen. The palette skips green,
 * yellow and red, which RTS already uses for your mobs, selections and enemies.
 */
public final class AllyColors {

    private static final int[] PALETTE = {
            0xFF4FA3FF, // sky blue
            0xFFB070FF, // violet
            0xFFFF70C8, // pink
            0xFF40E0D0, // turquoise
            0xFFFFFFFF, // white
            0xFFFFA040, // orange
            0xFF8080FF, // periwinkle
            0xFFC0C0C0, // silver
    };

    private AllyColors() {}

    public static int of(String uuid) {
        return PALETTE[Math.floorMod(uuid.hashCode(), PALETTE.length)];
    }
}
