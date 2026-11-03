package dev.reality.fightbot.core;

import dev.reality.fightbot.core.world.WorldView;

/**
 * Where the target will be. It keeps the last few ticks of the target's movement and runs them forward
 * with the game's own movement rules:
 *
 * - Gliding: the vanilla elytra step each tick, from their look direction. It also notices a firework
 *   boost from how much faster they got than gliding alone allows, and keeps the boost going for the
 *   rest of a rocket's flight. A turn in progress keeps turning, easing off.
 * - On foot or falling: their average recent speed on the ground, and gravity and drag in the air, with
 *   a stop at the first solid block when the world is known.
 *
 * StyleBrain feeds it every tick and makes it reachable to every style through current(), so the
 * policies that know nothing about it still work in tests, where current() is null.
 */
public final class Predictor {
    private static Predictor active;

    /** The predictor for the bot being stepped right now, or null outside a StyleBrain step. */
    public static Predictor current() {
        return active;
    }

    public static void setCurrent(Predictor p) {
        active = p;
    }

    private static final int N = 10;
    private final double[][] hist = new double[N][];
    private int count;
    private long lastTick = Long.MIN_VALUE;
    private double yawRate, pitchRate;
    private int boostLeft;
    private double groundY = Double.NaN;
    private WorldView world;
    /** Ticks it has been gliding without a break. */
    private int glideTicks;

    public void setWorld(WorldView w) {
        world = w;
    }

    /** Records this tick's target movement. Call once a tick, before asking anything. */
    public void observe(Observation o) {
        if (!o.hasOpponent() || o.tick() == lastTick) return;
        if (lastTick != Long.MIN_VALUE && o.tick() - lastTick > 5) count = 0;
        lastTick = o.tick();
        Loadout l = o.loadout();
        float pitch = pitchOf(o);
        double[] s = {o.tick(), o.ox(), o.oy(), o.oz(), o.ovx(), o.ovy(), o.ovz(), o.oyaw(), pitch, gliding(o) ? 1 : 0, o.oOnGround() ? 1 : 0};
        if (o.oOnGround()) groundY = o.oy();
        glideTicks = s[9] == 1 ? glideTicks + 1 : 0;
        if (count > 0) {
            double[] prev = hist[(count - 1) % N];
            int dt = (int) Math.max(1, s[0] - prev[0]);
            double yr = Angles.wrap((float) (s[7] - prev[7])) / dt, pr = (s[8] - prev[8]) / dt;
            yawRate = yawRate * 0.5 + yr * 0.5;
            pitchRate = pitchRate * 0.5 + pr * 0.5;
            // A firework: they sped up along their look more than a glide can.
            if (s[9] == 1 && prev[9] == 1 && dt == 1) {
                double[] v = {prev[4], prev[5], prev[6]};
                glideStep(v, prev[7], prev[8], false);
                double rx = s[4] - v[0], ry = s[5] - v[1], rz = s[6] - v[2];
                double[] look = look(s[7], s[8]);
                double along = rx * look[0] + ry * look[1] + rz * look[2];
                if (along > 0.08) boostLeft = Math.max(boostLeft, 12);
            }
        }
        if (boostLeft > 0) boostLeft--;
        hist[count % N] = s;
        count++;
    }

    /** True when the target is flying with an elytra, read from the game or from their pose and speed. */
    public static boolean gliding(Observation o) {
        Loadout l = o.loadout();
        if (l.oGliding) return true;
        // Gliding shrinks the hitbox to 0.6 high, like swimming, but in the air and fast.
        double speed = Math.sqrt(o.ovx() * o.ovx() + o.ovy() * o.ovy() + o.ovz() * o.ovz());
        return !o.oOnGround() && l.oHeight > 0 && l.oHeight < 1.0 && !l.inWater && speed > 0.5;
    }

    /** True while the target is gliding, or was within the last few ticks. */
    public boolean flying(Observation o) {
        return gliding(o) || (glideTicks > 0 && !o.oOnGround());
    }

    /** True while a firework boost is thought to be pushing them. */
    public boolean boosting() {
        return boostLeft > 0;
    }

    private static float pitchOf(Observation o) {
        float lp = o.loadout().oPitch;
        if (!Float.isNaN(lp)) return lp;
        // Unknown look: a glider mostly looks the way it flies.
        double h = Math.hypot(o.ovx(), o.ovz());
        return (float) -Math.toDegrees(Math.atan2(o.ovy(), Math.max(1e-6, h)));
    }

    /** Position {x, y, z} of the target's feet that many ticks from now. */
    public double[] at(Observation o, int ticks) {
        double[][] path = path(o, ticks);
        return path[path.length - 1];
    }

    /** Predicted feet positions, index 0 being now and the last being that many ticks ahead. */
    public double[][] path(Observation o, int ticks) {
        ticks = Math.max(0, ticks);
        double[][] out = new double[ticks + 1][];
        double x = o.ox(), y = o.oy(), z = o.oz();
        out[0] = new double[] {x, y, z};
        boolean glide = gliding(o);
        double yaw = o.oyaw(), pitch = pitchOf(o);
        double[] v;
        if (glide || count == 0) {
            v = new double[] {o.ovx(), o.ovy(), o.ovz()};
        } else {
            // On foot the velocity jumps around with every key press, so it uses the last few ticks.
            int n = Math.min(3, count);
            double ax = 0, az = 0;
            for (int i = 0; i < n; i++) {
                double[] s = hist[(count - 1 - i) % N];
                ax += s[4];
                az += s[6];
            }
            v = new double[] {ax / n, o.ovy(), az / n};
        }
        boolean onGround = o.oOnGround();
        int boost = boostLeft;
        double yr = yawRate, pr = pitchRate;
        for (int t = 1; t <= ticks; t++) {
            if (glide) {
                yaw += yr;
                pitch = Math.max(-90, Math.min(90, pitch + pr));
                yr *= 0.85;
                pr *= 0.7;
                glideStep(v, yaw, pitch, boost > 0);
                if (boost > 0) boost--;
            } else if (onGround) {
                // Running: the speed holds for a while, then they may stop or turn.
                double keep = t <= 6 ? 1.0 : 0.85;
                v[0] *= keep;
                v[2] *= keep;
                v[1] = 0;
            } else {
                v[1] = (v[1] - 0.08) * 0.98;
                v[0] *= 0.91;
                v[2] *= 0.91;
            }
            double nx = x + v[0], ny = y + v[1], nz = z + v[2];
            if (world != null && world.solid((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz))) {
                // Into a block: on the way down that is the ground, so they stop on top of it.
                if (v[1] < 0 && !world.solid((int) Math.floor(nx), (int) Math.floor(ny) + 1, (int) Math.floor(nz))) {
                    ny = Math.floor(ny) + 1;
                    v[1] = 0;
                    glide = false;
                    onGround = true;
                } else {
                    nx = x;
                    nz = z;
                    v[0] = 0;
                    v[2] = 0;
                }
            } else if (!glide && !onGround && !Double.isNaN(groundY) && world == null && ny < groundY && y >= groundY) {
                ny = groundY;
                v[1] = 0;
                onGround = true;
            }
            x = nx;
            y = ny;
            z = nz;
            out[t] = new double[] {x, y, z};
        }
        return out;
    }

    /** The closest the target gets to a point over the next ticks. */
    public record Approach(int tick, double distance, double x, double y, double z, double fromAbove) { }

    /** When and how close the target passes the given point, and how far above it they are then. */
    public Approach approach(Observation o, double px, double py, double pz, int horizon) {
        double[][] path = path(o, horizon);
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int t = 0; t < path.length; t++) {
            double[] q = path[t];
            // Distance to their body's middle.
            double d = Math.sqrt(sq(q[0] - px) + sq(q[1] + 0.3 - py) + sq(q[2] - pz));
            if (d < bd) {
                bd = d;
                best = t;
            }
        }
        double[] q = path[best];
        return new Approach(best, bd, q[0], q[1], q[2], q[1] - py);
    }

    /** Closest pass to the bot's own body. */
    public Approach approachBot(Observation o, int horizon) {
        return approach(o, o.x(), o.y() + 0.9, o.z(), horizon);
    }

    /** Yaw and pitch from the bot's eyes to the target's body, that many ticks ahead. */
    public float[] aimAhead(Observation o, int ticks) {
        double[] q = at(o, ticks);
        double h = o.loadout().oHeight > 0 ? o.loadout().oHeight : 1.8;
        double dx = q[0] - o.x(), dy = q[1] + h * 0.5 - o.eyeY(), dz = q[2] - o.z();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new float[] {yaw, pitch};
    }

    // ---------------------------------------------------------------- the game's elytra step

    static double[] look(double yawDeg, double pitchDeg) {
        double yr = Math.toRadians(yawDeg), pr = Math.toRadians(pitchDeg);
        return new double[] {-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)};
    }

    /** One tick of vanilla elytra flight, for simulators and tests. */
    public static void glide(double[] v, double yawDeg, double pitchDeg, boolean boosted) {
        glideStep(v, yawDeg, pitchDeg, boosted);
    }

    /** One tick of vanilla elytra flight on velocity v, with a firework pushing when boosted. */
    static void glideStep(double[] v, double yawDeg, double pitchDeg, boolean boosted) {
        double[] look = look(yawDeg, pitchDeg);
        if (boosted) {
            v[0] += look[0] * 0.1 + (look[0] * 1.5 - v[0]) * 0.5;
            v[1] += look[1] * 0.1 + (look[1] * 1.5 - v[1]) * 0.5;
            v[2] += look[2] * 0.1 + (look[2] * 1.5 - v[2]) * 0.5;
        }
        double pr = Math.toRadians(pitchDeg);
        double hLook = Math.hypot(look[0], look[2]);
        double hVel = Math.hypot(v[0], v[2]);
        double cos = Math.cos(pr);
        double cos2 = cos * cos;
        v[1] += 0.08 * (-1 + cos2 * 0.75);
        if (v[1] < 0 && hLook > 0) {
            double d = v[1] * -0.1 * cos2;
            v[0] += look[0] * d / hLook;
            v[1] += d;
            v[2] += look[2] * d / hLook;
        }
        if (pr < 0 && hLook > 0) {
            double d = hVel * -Math.sin(pr) * 0.04;
            v[0] -= look[0] * d / hLook;
            v[1] += d * 3.2;
            v[2] -= look[2] * d / hLook;
        }
        if (hLook > 0) {
            v[0] += (look[0] / hLook * hVel - v[0]) * 0.1;
            v[2] += (look[2] / hLook * hVel - v[2]) * 0.1;
        }
        v[0] *= 0.99;
        v[1] *= 0.98;
        v[2] *= 0.99;
    }

    private static double sq(double d) {
        return d * d;
    }
}
