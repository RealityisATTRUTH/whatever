package dev.reality.fightbot.core.world;

/**
 * Explosion damage to a player, worked out the way vanilla does it: distance falls off over twice the
 * power, scaled by how much of the player's box the blast can see, then difficulty, armor, and protection.
 * It is an estimate for choosing placements. The real damage is measured afterward from health.
 */
public final class Explosions {
    private Explosions() {
    }

    public static final float CRYSTAL_POWER = 6f;
    public static final float ANCHOR_POWER = 5f;

    /** Share of a player's box at feet position px, py, pz that the blast center can see, 0 to 1. */
    public static double exposure(WorldView w, double cx, double cy, double cz, double px, double py, double pz) {
        // A grid over the player's box like the game's, a little coarser to stay fast.
        int seen = 0, total = 0;
        for (int i = 0; i <= 2; i++)
            for (int j = 0; j <= 3; j++)
                for (int k = 0; k <= 2; k++) {
                    double x = px - 0.3 + 0.3 * i, y = py + 0.6 * j, z = pz - 0.3 + 0.3 * k;
                    total++;
                    if (w.clearLine(cx, cy, cz, x, y, z)) seen++;
                }
        return (double) seen / total;
    }

    /** Raw damage before difficulty, armor, and protection. */
    public static double raw(WorldView w, double cx, double cy, double cz, float power, double px, double py, double pz) {
        double diameter = power * 2.0;
        double dist = Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz)) / diameter;
        if (dist > 1) return 0;
        double impact = (1 - dist) * exposure(w, cx, cy, cz, px, py, pz);
        if (impact <= 0) return 0;
        return (impact * impact + impact) / 2.0 * 7.0 * diameter + 1.0;
    }

    /** Damage a player takes, after difficulty, armor, and protection. */
    public static double toPlayer(WorldView w, double cx, double cy, double cz, float power, double px, double py, double pz, boolean self) {
        double d = raw(w, cx, cy, cz, power, px, py, pz);
        if (d <= 0) return 0;
        switch (w.difficulty()) {
            case "peaceful" -> d = 0;
            case "easy" -> d = Math.min(d / 2 + 1, d);
            case "hard" -> d = d * 1.5;
            default -> { }
        }
        WorldView.Armor a = w.armor(self);
        double cut = Math.min(20, Math.max(a.points() / 5, a.points() - 4 * d / (a.toughness() + 8))) / 25;
        d *= 1 - cut;
        d *= 1 - Math.min(20, a.explosionProtection()) / 25.0;
        return d;
    }
}
