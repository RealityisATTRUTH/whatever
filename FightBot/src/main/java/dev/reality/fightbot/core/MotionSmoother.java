package dev.reality.fightbot.core;

/**
 * Last step before inputs go to the game. It eases turns instead of snapping, and stops strafing
 * from flipping straight from left to right, so the bot moves like a player with a mouse and keys.
 * Ticks that click something keep the policy's exact aim, so hits and placements still land.
 */
public final class MotionSmoother {
    private final BotProfile p;
    private int strafe;
    private long strafeSince = -1000;
    private long pauseUntil = -1;

    public MotionSmoother(BotProfile p) {
        this.p = p;
    }

    public void apply(Observation o, BotAction a) {
        boolean clicking = a.attack || a.attackEntity >= 0 || a.place != null || a.swapAttack != null
                || a.useOnce || a.useItem != null || a.mine != null || a.exactAim || a.startGlide || a.jabSlot >= 0;
        if (!clicking && p.smoothTurn > 0) {
            // In flight the pitch steers, so it eases less there.
            // Up close in a sword or axe fight on the ground it eases less: a recorded fight showed it turning only 20 degrees a tick and getting
            // hit from the side while it came round.
            boolean close = o.hasOpponent() && o.distance() < p.fightTurnRange && o.oOnGround()
                    && (Loadout.isSword(o.mainHand()) || Loadout.isAxe(o.mainHand()));
            double k = o.gliding() ? Math.max(p.smoothTurn, 0.7) : close ? Math.max(p.smoothTurn, p.fightSmoothTurn) : p.smoothTurn;
            a.yaw = ease(o.yaw(), a.yaw, k);
            a.pitch = ease(o.pitch(), a.pitch, k);
        }
        smoothStrafe(o.tick(), a);
    }

    /** Moves a share of the way, so big turns start fast and settle softly. */
    private float ease(float current, float target, double k) {
        double d = Angles.wrap(target - current);
        if (Math.abs(d) < 1.0) return target;
        double step = d * k;
        if (Math.abs(step) < 1.0) step = Math.signum(d);
        return (float) (current + step);
    }

    /** A reversal of strafe waits until the old one has run a few ticks and lets go of the key for one tick. */
    private void smoothStrafe(long tick, BotAction a) {
        int want = a.strafe;
        if (want != 0 && strafe != 0 && want != strafe) {
            if (tick - strafeSince < p.strafeHoldTicks) {
                a.strafe = strafe;
                return;
            }
            pauseUntil = tick + 1;
            strafe = 0;
            strafeSince = tick;
            a.strafe = 0;
            return;
        }
        if (tick < pauseUntil && want != 0) {
            a.strafe = 0;
            return;
        }
        if (want != strafe) strafeSince = tick;
        strafe = want;
    }
}
