package dev.reality.fightbot.core;

/**
 * Arrow aim, worked out the way the game moves an arrow each tick: move, then 0.99 drag, then 0.05
 * gravity. The arrow starts just under the eye with the shooter's own speed added. It leads a moving
 * target by where they will be when the arrow gets there.
 */
public final class Ballistics {
    private Ballistics() {
    }

    /** Yaw, pitch, and flight ticks to hit the target's middle with this launch speed, or null when out of range. */
    public static float[] aim(Observation o, double speed) {
        double sx = o.x(), sy = o.eyeY() - 0.1, sz = o.z();
        double tx = o.ox(), ty = o.oy() + 0.9, tz = o.oz();
        double svx = o.vx(), svy = o.onGround() ? 0 : o.vy(), svz = o.vz();
        float[] best = null;
        for (int pass = 0; pass < 3; pass++) {
            best = solve(sx, sy, sz, tx, ty, tz, speed, svx, svy, svz);
            if (best == null) return null;
            Predictor pr = Predictor.current();
            if (pr != null) {
                // Where they will be when it gets there, with gliding, gravity, and the ground worked out.
                double[] q = pr.at(o, Math.round(best[2]));
                double h = o.loadout().oHeight > 0 ? o.loadout().oHeight : 1.8;
                tx = q[0];
                ty = q[1] + h * 0.5;
                tz = q[2];
                continue;
            }
            // Where they will be after that many ticks, running the way they are now.
            tx = o.ox() + o.ovx() * best[2];
            tz = o.oz() + o.ovz() * best[2];
            ty = o.oy() + 0.9 + (o.oOnGround() ? 0 : o.ovy() * best[2] * 0.5);
        }
        return best;
    }

    /** The flattest pitch that brings the arrow to the target point. */
    static float[] solve(double sx, double sy, double sz, double tx, double ty, double tz, double speed,
                         double svx, double svy, double svz) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(tx - sx), tz - sz));
        double yr = Math.toRadians(yaw);
        double dist = Math.hypot(tx - sx, tz - sz);
        float bestPitch = Float.NaN;
        double bestErr = 0.5;
        int bestTicks = 0;
        for (double pitch = -45; pitch <= 30; pitch += 0.25) {
            double pr = Math.toRadians(pitch);
            double vx = -Math.sin(yr) * Math.cos(pr) * speed + svx, vy = -Math.sin(pr) * speed + svy, vz = Math.cos(yr) * Math.cos(pr) * speed + svz;
            double x = sx, y = sy, z = sz;
            for (int t = 1; t <= 100; t++) {
                double px = x, py = y, pz = z;
                x += vx;
                y += vy;
                z += vz;
                double before = Math.hypot(px - sx, pz - sz), after = Math.hypot(x - sx, z - sz);
                if (after >= dist) {
                    // Height where it crosses the target's distance.
                    double f = (dist - before) / Math.max(1e-6, after - before);
                    double hy = py + (y - py) * f;
                    double err = Math.abs(hy - ty);
                    if (err < bestErr) {
                        bestErr = err;
                        bestPitch = (float) pitch;
                        bestTicks = t;
                    }
                    break;
                }
                vx *= 0.99;
                vy *= 0.99;
                vz *= 0.99;
                vy -= 0.05;
                if (y < ty - 40) break;
            }
            // The flattest shot that works is the fastest one, so stop at the first good pitch from above.
            if (!Float.isNaN(bestPitch) && bestErr < 0.15) break;
        }
        if (Float.isNaN(bestPitch)) return null;
        return new float[] {yaw, bestPitch, bestTicks};
    }
}
