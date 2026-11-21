package com.reality.mobcraft.hitbox;

import com.reality.mobcraft.MobCraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Dual hitbox.
 *
 * Any player scaled under 1 block tall keeps their small collision box for
 * movement, but gets a normal player-sized box for being hit: crosshair,
 * projectiles, mob melee, player melee, explosions and Carpet bots.
 *
 * Triggers on SCALE alone, so it works for every small mob (Rabbit, Bat, Vex)
 * without each kit having to opt in.
 */
public final class SmallMobHitbox {

    /** Player-mobs standing shorter than this (blocks) get the dual hitbox. */
    public static final double SMALL_HEIGHT_THRESHOLD = 1.0;

    /** Normal player standing height at SCALE 1.0. */
    private static final double BASE_PLAYER_HEIGHT = 1.8;

    private SmallMobHitbox() {}

    /** The player's SCALE attribute, or 1.0 if it has none. */
    public static double scaleOf(Entity entity) {
        if (entity instanceof Player player && player.getAttribute(Attributes.SCALE) != null) {
            return player.getAttributeValue(Attributes.SCALE);
        }
        return 1.0;
    }

    /**
     * True if this entity is a player scaled under the height threshold.
     * Uses SCALE, not the live box, so a full-size player who swims or
     * crawls (box drops to 0.6 tall) does NOT turn on the dual hitbox.
     */
    public static boolean isSmall(Entity entity) {
        if (!(entity instanceof Player)) return false;
        return (BASE_PLAYER_HEIGHT * scaleOf(entity)) < SMALL_HEIGHT_THRESHOLD;
    }

    /**
     * The attack box: the player's real vanilla box, scaled back up to
     * SCALE 1.0 around the same feet position.
     *
     * Because it starts from the live box, it follows every pose change the
     * vanilla box makes. Standing gives 0.6 x 1.8, sneaking 0.6 x 1.5,
     * swimming or crawling 0.6 x 0.6, exactly what a normal-size player has.
     */
    public static AABB inflateToNormal(Entity e, AABB original) {
        double scale = scaleOf(e);
        if (scale <= 0.0) return original;

        double halfX = original.getXsize() / scale / 2.0;
        double halfZ = original.getZsize() / scale / 2.0;
        double height = original.getYsize() / scale;
        double cx = (original.minX + original.maxX) / 2.0;
        double cz = (original.minZ + original.maxZ) / 2.0;
        double feetY = original.minY;

        return new AABB(
                cx - halfX, feetY,          cz - halfZ,
                cx + halfX, feetY + height, cz + halfZ
        );
    }

    /** The box the hit raycast should use: inflated for small players, else unchanged. */
    public static AABB hitboxFor(Entity e, AABB original) {
        return isSmall(e) ? inflateToNormal(e, original) : original;
    }

    /**
     * Same as hitboxFor, but logs once per damage path the first time it
     * actually swaps in the attack box. The log shows which paths are live.
     */
    public static AABB hitboxFor(Entity e, AABB original, String path) {
        if (!isSmall(e)) return original;
        if (REPORTED.add(path)) {
            MobCraft.LOGGER.info("[mobcraft] {} is using the green attack box", path);
        }
        return inflateToNormal(e, original);
    }

    private static final java.util.Set<String> REPORTED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
}
