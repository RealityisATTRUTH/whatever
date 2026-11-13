package com.reality.mobcraft.mob;

import net.minecraft.server.level.ServerPlayer;

/**
 * What a player's mob parts are doing right now, for every nearby game to draw.
 * Each kit switches these on and off. See MobLookLayer for how they're drawn.
 *
 *   FUSING    Creeper: swelling and flashing before a blast
 *   CHARGED   Creeper: the charged-creeper energy swirl
 *   SPIKES    Elder Guardian: spikes out, just after being hit
 *   SENSING   Warden: tendrils shaking, while it senses something
 *   FLYING    Ender Dragon, Bat and Vex: wings spread and flapping. Ghast: riding its cloud
 *   SHIELD    Blaze: Fire Shield on, rods spinning faster
 *   PHASING   Vex: see-through while phasing
 *   CHARGING  Vex: eyes glowing red during a Charge
 *   WHIRL     Breeze: the wind swirl grows during Whirlwind
 *   TELEPORTING  Enderman: glowing purple just before and after a teleport
 *   SHELL     Shulker: the shell closed around it while Blocking
 *   EXPLOSIVE Creeper Essence, on anyone: glowing orange while holding a charge
 */
public final class MobLook {

    public static final int FUSING = 1;
    public static final int CHARGED = 2;
    public static final int SPIKES = 4;
    public static final int SENSING = 8;
    public static final int FLYING = 16;
    public static final int SHIELD = 32;
    public static final int PHASING = 64;
    public static final int CHARGING = 128;
    public static final int WHIRL = 256;
    public static final int TELEPORTING = 512;
    public static final int SHELL = 1024;
    /** A Shulker Essence shell that has been hit once and is cracked. */
    public static final int SHELL_CRACKED = 2048;
    /** Creeper Essence: holding a charged item, glowing orange all over. Any player, any mob. */
    public static final int EXPLOSIVE = 4096;
    /** Creeper Essence's last stand: swelling and flashing like a creeper's fuse. Any player, any mob. */
    public static final int SELF_FUSE = 8192;

    private MobLook() { }

    public static int get(net.minecraft.world.entity.player.Player player) {
        Integer v = player.getAttached(MobAttachments.LOOK);
        return v == null ? 0 : v;
    }

    /** Turns one look on or off. Only sends anything when it changes. */
    public static void set(ServerPlayer player, int look, boolean on) {
        int now = get(player);
        int next = on ? now | look : now & ~look;
        if (next == now) return;
        if (next == 0) player.removeAttached(MobAttachments.LOOK);
        else player.setAttached(MobAttachments.LOOK, next);
    }

    public static void clear(ServerPlayer player) {
        if (player.hasAttached(MobAttachments.LOOK)) player.removeAttached(MobAttachments.LOOK);
    }
}
