package dev.reality.fightbot.core;

/**
 * Everything the bot is allowed to know on one tick, already normalized.
 * Velocities are blocks per tick. Missing values are NaN or -1, never guessed.
 */
public record Observation(
        long tick,
        boolean hasOpponent,
        // The bot
        double x, double y, double z,
        double vx, double vy, double vz,
        float yaw, float pitch,
        boolean onGround, boolean sprinting, boolean usingItem,
        float health, float absorption,
        String mainHand,
        /** Exact attack bar from 0 to 1, read on the server. */
        double charge,
        /** Hotbar slot 0 to 8 holding a melee weapon, or -1. */
        int weaponSlot,
        /** Hotbar slot 0 to 8 holding golden apples, or -1. */
        int healSlot,
        int healCount,
        // The opponent
        double ox, double oy, double oz,
        double ovx, double ovy, double ovz,
        float oyaw,
        boolean oOnGround, boolean oSprinting, boolean oBlocking, boolean oUsingItem,
        float oHealth, float oAbsorption,
        String oMainHand,
        boolean lineOfSight,
        /** Ticks since this observation was read. Above 2 counts as stale. */
        int ageTicks,
        // Shield, axe and apples. The server sees every inventory, so these are exact.
        boolean hasShield,
        /** Hotbar slot 0 to 8 holding an axe, or -1. */
        int axeSlot,
        /** Hotbar slot 0 to 8 holding enchanted golden apples, or -1. */
        int egapSlot,
        int egapCount,
        /** Golden apples and enchanted golden apples anywhere in the inventory, hotbar included. */
        int inventoryGapples,
        int inventoryEgaps,
        /** The opponent's exact attack bar, 0 to 1. */
        double oCharge,
        boolean oHasShield,
        int oGapples,
        int oEgaps,
        // Everything carried, and the air state used by mace styles.
        Loadout loadout,
        /** Blocks fallen since last on the ground, as the server counts it for mace smashes. */
        double fallDistance,
        boolean gliding,
        /** False while wind charges are on cooldown. */
        boolean windReady,
        /** Open space above the bot's head, and above the opponent's head, in blocks. NaN when unknown. */
        double headroom,
        double oHeadroom
) {
    /** Shape without headroom: treated as open sky, which is what the older tests and simulator assume. */
    public Observation(long tick, boolean hasOpponent, double x, double y, double z, double vx, double vy, double vz,
                       float yaw, float pitch, boolean onGround, boolean sprinting, boolean usingItem, float health,
                       float absorption, String mainHand, double charge, int weaponSlot, int healSlot, int healCount,
                       double ox, double oy, double oz, double ovx, double ovy, double ovz, float oyaw, boolean oOnGround,
                       boolean oSprinting, boolean oBlocking, boolean oUsingItem, float oHealth, float oAbsorption,
                       String oMainHand, boolean lineOfSight, int ageTicks, boolean hasShield, int axeSlot, int egapSlot,
                       int egapCount, int inventoryGapples, int inventoryEgaps, double oCharge, boolean oHasShield,
                       int oGapples, int oEgaps, Loadout loadout, double fallDistance, boolean gliding, boolean windReady) {
        this(tick, hasOpponent, x, y, z, vx, vy, vz, yaw, pitch, onGround, sprinting, usingItem, health, absorption,
                mainHand, charge, weaponSlot, healSlot, healCount, ox, oy, oz, ovx, ovy, ovz, oyaw, oOnGround, oSprinting,
                oBlocking, oUsingItem, oHealth, oAbsorption, oMainHand, lineOfSight, ageTicks, hasShield, axeSlot,
                egapSlot, egapCount, inventoryGapples, inventoryEgaps, oCharge, oHasShield, oGapples, oEgaps,
                loadout, fallDistance, gliding, windReady, Double.NaN, Double.NaN);
    }

    /**
     * True when a roof over the opponent blocks a mace drop. Unknown headroom counts as open,
     * so a missing reading never stops the bot from fighting normally.
     */
    public boolean opponentCovered(double needed) {
        return Double.isFinite(oHeadroom) && oHeadroom < needed;
    }

    public boolean botCovered(double needed) {
        return Double.isFinite(headroom) && headroom < needed;
    }

    /** Shape without the loadout and air state, used by the sword tests. */
    public Observation(long tick, boolean hasOpponent, double x, double y, double z, double vx, double vy, double vz,
                       float yaw, float pitch, boolean onGround, boolean sprinting, boolean usingItem, float health,
                       float absorption, String mainHand, double charge, int weaponSlot, int healSlot, int healCount,
                       double ox, double oy, double oz, double ovx, double ovy, double ovz, float oyaw, boolean oOnGround,
                       boolean oSprinting, boolean oBlocking, boolean oUsingItem, float oHealth, float oAbsorption,
                       String oMainHand, boolean lineOfSight, int ageTicks, boolean hasShield, int axeSlot, int egapSlot,
                       int egapCount, int inventoryGapples, int inventoryEgaps, double oCharge, boolean oHasShield,
                       int oGapples, int oEgaps) {
        this(tick, hasOpponent, x, y, z, vx, vy, vz, yaw, pitch, onGround, sprinting, usingItem, health, absorption,
                mainHand, charge, weaponSlot, healSlot, healCount, ox, oy, oz, ovx, ovy, ovz, oyaw, oOnGround, oSprinting,
                oBlocking, oUsingItem, oHealth, oAbsorption, oMainHand, lineOfSight, ageTicks, hasShield, axeSlot,
                egapSlot, egapCount, inventoryGapples, inventoryEgaps, oCharge, oHasShield, oGapples, oEgaps,
                Loadout.EMPTY, 0, false, true, Double.NaN, Double.NaN);
    }

    /** Older shape without shield and apple details, used by tests and the simulator. */
    public Observation(long tick, boolean hasOpponent, double x, double y, double z, double vx, double vy, double vz,
                       float yaw, float pitch, boolean onGround, boolean sprinting, boolean usingItem, float health,
                       float absorption, String mainHand, double charge, int weaponSlot, int healSlot, int healCount,
                       double ox, double oy, double oz, double ovx, double ovy, double ovz, float oyaw, boolean oOnGround,
                       boolean oSprinting, boolean oBlocking, boolean oUsingItem, float oHealth, float oAbsorption,
                       String oMainHand, boolean lineOfSight, int ageTicks) {
        this(tick, hasOpponent, x, y, z, vx, vy, vz, yaw, pitch, onGround, sprinting, usingItem, health, absorption,
                mainHand, charge, weaponSlot, healSlot, healCount, ox, oy, oz, ovx, ovy, ovz, oyaw, oOnGround, oSprinting,
                oBlocking, oUsingItem, oHealth, oAbsorption, oMainHand, lineOfSight, ageTicks,
                false, -1, -1, 0, healCount, 0, Double.NaN, false, 0, 0, Loadout.EMPTY, 0, false, true);
    }

    public boolean oppHasAxe() {
        return oMainHand.endsWith("_axe");
    }

    public boolean oppDrawingBow() {
        return oUsingItem && (oMainHand.equals("bow") || oMainHand.equals("crossbow"));
    }

    public static Observation none(long tick) {
        return new Observation(tick, false, 0, 0, 0, 0, 0, 0, 0, 0, true, false, false, 20, 0, "", -1, -1, -1, 0,
                0, 0, 0, 0, 0, 0, 0, true, false, false, false, Float.NaN, 0, "", false, 0);
    }

    /** Straight-line distance between the two players' feet, height included. */
    public double distance3d() {
        return Math.sqrt((ox - x) * (ox - x) + (oy - y) * (oy - y) + (oz - z) * (oz - z));
    }

    public double heightAbove() {
        return y - oy;
    }

    public double horizontalSpeed() {
        return Math.hypot(vx, vz);
    }

    public double distance() {
        return Math.hypot(ox - x, oz - z);
    }

    public double effectiveHealth() {
        return health + absorption;
    }

    public double oEffectiveHealth() {
        return oHealth + oAbsorption;
    }

    /** Yaw that faces the opponent, in Minecraft's convention where 0 faces south. */
    public float yawToOpponent() {
        return (float) Math.toDegrees(Math.atan2(-(ox - x), oz - z));
    }

    /** Pitch that looks at the opponent's chest. */
    public float pitchToOpponent() {
        double dy = (oy + loadout.oHeight * 0.67) - eyeY();
        return (float) -Math.toDegrees(Math.atan2(dy, distance()));
    }

    public double facingError() {
        return Angles.diff(yaw, yawToOpponent());
    }

    /** Opponent's facing error toward the bot. */
    public double oFacingError() {
        float toBot = (float) Math.toDegrees(Math.atan2(-(x - ox), z - oz));
        return Angles.diff(oyaw, toBot);
    }

    public boolean stale() {
        return ageTicks > 2;
    }

    /** Height of the bot's eyes, for its current pose. */
    public double eyeY() {
        return y + loadout.eyeHeight;
    }

    /** Top of the target's hitbox, for their current pose. */
    public double oTop() {
        return oy + loadout.oHeight;
    }

    /** Distance from the bot's eyes to the closest point of the target's hitbox, the way reach is measured. */
    public double reachToTarget() {
        double hw = loadout.oWidth / 2;
        double ex = x, ey = eyeY(), ez = z;
        double cx = Math.max(ox - hw, Math.min(ex, ox + hw));
        double cy = Math.max(oy, Math.min(ey, oTop()));
        double cz = Math.max(oz - hw, Math.min(ez, oz + hw));
        return Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy) + (ez - cz) * (ez - cz));
    }
}
