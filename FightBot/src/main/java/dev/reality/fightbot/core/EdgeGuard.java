package dev.reality.fightbot.core;

import dev.reality.fightbot.core.world.WorldView;

/**
 * The last thing before inputs go to the game: no move that walks the bot off an edge into the void, lava, or a
 * drop that would hurt. Every fighting style strafes, S-taps, chases and jumps without looking down, which is
 * fine on the ground and deadly on a one block bridge. This works out where the keys pressed this tick would
 * leave the bot once it stops sliding, using the game's own ground movement: 0.1 added a tick per key, 0.3 of
 * that sneaking, and 0.546 of the speed kept a tick. A jump is checked where it lands.
 *
 * When that spot is unsafe it lets go of the strafe key, then the forward key, then both, and keeps the first
 * that is safe. If even standing still slides it off, it sneaks, which the game never lets walk off an edge.
 * Deliberate gap jumps, drops and bridging are left alone: those check their own landing.
 */
public final class EdgeGuard {
    static final double DRAG = 0.546;
    private final BotProfile p;
    private WorldView world;
    /** Counts for the round's results. */
    public int saves, sneaks;

    public EdgeGuard(BotProfile p) {
        this.p = p;
    }

    public void setWorld(WorldView w) {
        world = w;
    }

    /** Techniques that already look after their own footing. */
    private static boolean deliberate(String technique) {
        return technique.startsWith("gap_") || technique.startsWith("bridge_") || technique.equals("speed_bridge")
                || technique.startsWith("pillar") || technique.startsWith("wind_") || technique.startsWith("pearl")
                || technique.equals("step_up") || technique.startsWith("rocket") || technique.startsWith("launch");
    }

    private int groundFloor = Integer.MIN_VALUE;

    /** Returns true when it changed the action. */
    public boolean apply(Observation o, BotAction a) {
        if (p.edgeGuard != 1 || world == null || o.gliding() || a.startGlide || deliberate(a.technique)) return false;
        if (o.onGround()) groundFloor = (int) Math.floor(o.y() + 0.01) - 1;
        if (!o.onGround()) return air(o, a);
        if (a.forward == 0 && a.strafe == 0 && !a.jump && Math.hypot(o.vx(), o.vz()) < 0.02) return false;
        int floorY = (int) Math.floor(o.y() + 0.01) - 1;
        if (safe(o, a.yaw, a.forward, a.strafe, a.sneak, a.sprint, a.jump, floorY)) return false;
        // Keep as much of the move as is safe: drop the jump, then a key at a time.
        int[][] tries = {{a.forward, a.strafe}, {a.forward, 0}, {0, a.strafe}, {0, 0}};
        for (int i = 0; i < tries.length; i++) {
            if (safe(o, a.yaw, tries[i][0], tries[i][1], a.sneak, a.sprint, false, floorY)) {
                a.jump = false;
                a.forward = tries[i][0];
                a.strafe = tries[i][1];
                if (a.forward <= 0) a.sprint = false;
                mark(a);
                return true;
            }
        }
        // Even standing still it slides off: sneak, which the game never walks off an edge.
        a.jump = false;
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        a.sneak = true;
        sneaks++;
        mark(a);
        return true;
    }

    private void mark(BotAction a) {
        saves++;
        if (a.technique.isEmpty() || a.technique.equals("s_tap") || a.technique.equals("w_tap") || a.technique.equals("sprint_jump")
                || a.technique.equals("crit_jump") || a.technique.equals("jump_reset")) a.technique = "edge_guard";
    }

    /** True when the bot ends up standing, or over a short safe drop, after this tick's keys and the slide. */
    boolean safe(Observation o, float yaw, int forward, int strafe, boolean sneak, boolean sprint, boolean jump, int floorY) {
        double f = forward, st = strafe;
        double len = Math.hypot(f, st);
        double ax = 0, az = 0;
        if (len > 0) {
            f = f * 0.98 / Math.max(1, len * 0.98);
            st = st * 0.98 / Math.max(1, len * 0.98);
            double acc = (sprint && forward > 0 && !sneak ? 0.13 : 0.1) * (sneak ? 0.3 : 1);
            double yr = Math.toRadians(yaw);
            ax = (st * Math.cos(yr) - f * Math.sin(yr)) * acc;
            az = (f * Math.cos(yr) + st * Math.sin(yr)) * acc;
        }
        double vx = o.vx() + ax, vz = o.vz() + az;
        double rx, rz;
        if (jump) {
            // A jump carries on for about 12 ticks in the air, losing 9% a tick, plus 0.2 more with sprint.
            if (sprint) {
                double yr = Math.toRadians(yaw);
                vx += -Math.sin(yr) * 0.2;
                vz += Math.cos(yr) * 0.2;
            }
            double carry = (1 - Math.pow(0.91, 12)) / (1 - 0.91);
            rx = o.x() + vx * carry;
            rz = o.z() + vz * carry;
        } else {
            rx = o.x() + vx / (1 - DRAG);
            rz = o.z() + vz / (1 - DRAG);
        }
        // Sneaking on the ground cannot walk off an edge at all.
        if (sneak && !jump) return true;
        if (standing(rx, rz, floorY)) return true;
        // Also along the way, so a fast move does not skip over a hole.
        for (double s = 0.25; s < 1; s += 0.25) {
            if (!standing(o.x() + (rx - o.x()) * s, o.z() + (rz - o.z()) * s, floorY) && !shortDrop(o.x() + (rx - o.x()) * s, o.z() + (rz - o.z()) * s, floorY)) return false;
        }
        return shortDrop(rx, rz, floorY);
    }

    /**
     * In the air after a jump or a hit: steer the landing back over the blocks. Air control is weak, 0.02 a tick,
     * but over half a second of a knockback arc that is the difference between the bridge and the void.
     */
    private boolean air(Observation o, BotAction a) {
        if (groundFloor == Integer.MIN_VALUE || o.y() < groundFloor + 1 - 0.5) return false;
        int floorY = groundFloor;
        if (landsSafe(o, a.yaw, a.forward, a.strafe, a.sprint, floorY)) return false;
        int bestF = 0, bestS = 0;
        double bestD = Double.MAX_VALUE;
        boolean found = false;
        for (int f = -1; f <= 1 && !found; f++) for (int st = -1; st <= 1; st++) {
            double[] land = landing(o, a.yaw, f, st, false, floorY);
            if (standing(land[0], land[1], floorY)) {
                bestF = f;
                bestS = st;
                found = true;
                break;
            }
            double d = nearestFloor(land[0], land[1], floorY);
            if (d < bestD) {
                bestD = d;
                bestF = f;
                bestS = st;
            }
        }
        if (bestF == a.forward && bestS == a.strafe) return false;
        a.forward = bestF;
        a.strafe = bestS;
        a.sprint = false;
        mark(a);
        return true;
    }

    private boolean landsSafe(Observation o, float yaw, int forward, int strafe, boolean sprint, int floorY) {
        double[] land = landing(o, yaw, forward, strafe, sprint, floorY);
        return standing(land[0], land[1], floorY) || shortDrop(land[0], land[1], floorY);
    }

    /** Where an airborne bot comes down to the floor's height with these keys held, by the game's air movement. */
    private double[] landing(Observation o, float yaw, int forward, int strafe, boolean sprint, int floorY) {
        double f = forward, st = strafe;
        double len = Math.hypot(f, st);
        double ax = 0, az = 0;
        if (len > 0) {
            f /= Math.max(1, len);
            st /= Math.max(1, len);
            double acc = sprint ? 0.026 : 0.02;
            double yr = Math.toRadians(yaw);
            ax = (st * Math.cos(yr) - f * Math.sin(yr)) * acc;
            az = (f * Math.cos(yr) + st * Math.sin(yr)) * acc;
        }
        double x = o.x(), y = o.y(), z = o.z(), vx = o.vx(), vy = o.vy(), vz = o.vz();
        for (int t = 0; t < 40; t++) {
            vx += ax;
            vz += az;
            x += vx;
            y += vy;
            z += vz;
            if (vy < 0 && y <= floorY + 1) break;
            vy = (vy - 0.08) * 0.98;
            vx *= 0.91;
            vz *= 0.91;
        }
        return new double[] {x, z};
    }

    /** Distance from here to the middle of the closest floor block near it, for steering toward it. */
    private double nearestFloor(double x, double z, int floorY) {
        double best = Double.MAX_VALUE;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (!world.solid(bx + dx, floorY, bz + dz)) continue;
            best = Math.min(best, Math.hypot(bx + dx + 0.5 - x, bz + dz + 0.5 - z));
        }
        return best;
    }

    private boolean standing(double x, double z, int floorY) {
        // A margin inside the 0.3 lean limit, so a knock or a slide still leaves it standing.
        double h = 0.3 - p.edgeMargin;
        for (double ox : new double[] {-h, h}) for (double oz : new double[] {-h, h}) {
            int bx = (int) Math.floor(x + ox), bz = (int) Math.floor(z + oz);
            if (world.solid(bx, floorY, bz) && !world.lava(bx, floorY + 1, bz)) return true;
        }
        return false;
    }

    /** A floor within the safe fall height below, with no lava on it. */
    private boolean shortDrop(double x, double z, int floorY) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int k = 1; k <= p.edgeSafeDrop; k++) {
            int y = floorY - k;
            if (world.lava(bx, y + 1, bz) || world.lava(bx, y, bz)) return false;
            if (world.water(bx, y + 1, bz)) return true;
            if (world.solid(bx, y, bz)) return true;
        }
        return false;
    }
}
