package com.reality.mobcraft.mob;

import net.minecraft.world.entity.player.Player;

/**
 * Reads and changes a player's mob, energy and curse.
 *
 * Works on both sides: the server owns the values, and the client reads its
 * synced copy for the HUD. Only call the setters on the server.
 */
public final class PlayerMob {

    private PlayerMob() {}

    // --- Mob ---------------------------------------------------------------

    /** The player's mob, or null if they don't have one. */
    public static MobType get(Player player) {
        return MobType.byId(player.getAttached(MobAttachments.MOB));
    }

    public static boolean has(Player player, MobType type) {
        return get(player) == type;
    }

    /** Server only. Pass null to remove the player's mob. */
    public static void set(Player player, MobType type) {
        if (type == null) {
            player.removeAttached(MobAttachments.MOB);
            player.removeAttached(MobAttachments.ENERGY);
            player.setAttached(MobAttachments.CURSE_TICKS, 0);
            player.removeAttached(MobAttachments.DISABLED_ABILITIES);
        } else {
            player.setAttached(MobAttachments.MOB, type.id());
            player.setAttached(MobAttachments.ENERGY, (float) type.maxEnergy);
            player.setAttached(MobAttachments.CURSE_TICKS, 0); // a new mob starts uncursed
            player.removeAttached(MobAttachments.DISABLED_ABILITIES); // and with every ability on
        }
    }

    // --- Energy ------------------------------------------------------------

    public static float energy(Player player) {
        return player.getAttachedOrElse(MobAttachments.ENERGY, 0.0f);
    }

    public static int maxEnergy(Player player) {
        MobType type = get(player);
        return type == null ? 0 : type.maxEnergy;
    }

    /** Server only. Clamped between 0 and the mob's max. */
    public static void setEnergy(Player player, float value) {
        float clamped = Math.max(0.0f, Math.min(value, maxEnergy(player)));
        if (clamped != energy(player)) {
            player.setAttached(MobAttachments.ENERGY, clamped);
        }
    }

    /** Server only. Spends energy if the player has enough. Returns false and spends nothing if not. */
    public static boolean spend(Player player, float amount) {
        float current = energy(player);
        if (current < amount) return false;
        setEnergy(player, current - amount);
        return true;
    }

    // --- Curse -------------------------------------------------------------

    public static int curseTicks(Player player) {
        return player.getAttachedOrElse(MobAttachments.CURSE_TICKS, 0);
    }

    public static boolean isCursed(Player player) {
        return curseTicks(player) > 0;
    }

    /** Server only. 0 lifts the curse. */
    public static void setCurse(Player player, int ticks) {
        // Set to 0 rather than removed: a removal didn't always reach the client,
        // which left the energy bar showing the curse after it was lifted.
        player.setAttached(MobAttachments.CURSE_TICKS, Math.max(0, ticks));
    }
}
