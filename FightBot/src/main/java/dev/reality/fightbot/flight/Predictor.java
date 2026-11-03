package dev.reality.fightbot.flight;

/**
 * Predicts next tick's velocity from this tick's sample, using the mechanics config. Comparing the
 * prediction with what the server reports next shows how well the config matches the game version.
 *
 * It only predicts what does not depend on unknown key presses: falling and gliding. On the ground,
 * and for horizontal speed in plain air, the player's own input changes the result, so the horizontal
 * part is only compared when the player is gliding. Only samples carrying the server's own velocity are
 * predicted. Real players' velocity on the server comes from their client, so it is worked out from
 * positions instead, and that is a different quantity.
 */
public final class Predictor {
    public record Prediction(double vx, double vy, double vz, boolean horizontalComparable) {
    }

    private final MechanicsConfig c;

    public Predictor(MechanicsConfig config) {
        this.c = config;
    }

    /**
     * @param now        this tick's sample
     * @param next       next tick's sample, for the look direction the game uses when it moves the player
     * @param rocketOn   whether a firework pushes during next's tick, or null when that is not known
     * @return the predicted velocity reported next tick, or null when there is not enough data
     */
    public Prediction predict(FlightSample now, FlightSample next, Boolean rocketOn) {
        if (now == null || next == null || !now.hasVelocity() || !next.present()) return null;
        if (rocketOn == null && Boolean.TRUE.equals(next.gliding())) return null;
        // Velocity worked out from positions is the move before drag, not the game's own velocity, so it is not scored.
        if (!"server".equals(now.velocitySource()) || !"server".equals(next.velocitySource())) return null;
        if (now.onGround() == null || now.gliding() == null || next.gliding() == null) return null;
        if (Boolean.TRUE.equals(next.onGround()) || Boolean.TRUE.equals(next.horizontalCollision()) || Boolean.TRUE.equals(next.verticalCollision())) {
            return null;
        }
        double vx = now.vx(), vy = now.vy(), vz = now.vz();
        if (next.gliding()) {
            if (!Float.isFinite(next.pitch()) || !Float.isFinite(next.yaw())) return null;
            double pr = Math.toRadians(next.pitch()), yr = Math.toRadians(next.yaw());
            double lx = -Math.sin(yr) * Math.cos(pr), ly = -Math.sin(pr), lz = Math.cos(yr) * Math.cos(pr);
            if (Boolean.TRUE.equals(rocketOn)) {
                vx += lx * c.fireworkPush + (lx * c.fireworkTargetSpeed - vx) * c.fireworkBlend;
                vy += ly * c.fireworkPush + (ly * c.fireworkTargetSpeed - vy) * c.fireworkBlend;
                vz += lz * c.fireworkPush + (lz * c.fireworkTargetSpeed - vz) * c.fireworkBlend;
            }
            double horiz = Math.hypot(lx, lz);
            double hSpeed = Math.hypot(vx, vz);
            double cos = Math.cos(pr);
            cos = cos * cos;
            vy += c.gravity * (-1 + cos * c.glideLift);
            if (vy < 0 && horiz > 0) {
                double d = vy * -c.glideDiveTransfer * cos;
                vx += lx * d / horiz;
                vy += d;
                vz += lz * d / horiz;
            }
            if (pr < 0 && horiz > 0) {
                double d = hSpeed * -Math.sin(pr) * c.glideClimbTransfer;
                vx += -lx * d / horiz;
                vy += d * c.glideClimbLift;
                vz += -lz * d / horiz;
            }
            if (horiz > 0) {
                vx += (lx / horiz * hSpeed - vx) * c.glideTurnBlend;
                vz += (lz / horiz * hSpeed - vz) * c.glideTurnBlend;
            }
            return new Prediction(vx * c.glideDragHorizontal, vy * c.glideDragVertical, vz * c.glideDragHorizontal, true);
        }
        if (Boolean.TRUE.equals(now.onGround())) return null;
        return new Prediction(vx * c.airDrag, (vy - c.gravity) * c.verticalDrag, vz * c.airDrag, false);
    }
}
