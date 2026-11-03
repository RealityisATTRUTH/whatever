package dev.reality.fightbot.core;

public final class Angles {
    private Angles() {
    }

    /** Smallest difference between two angles, 0 to 180 degrees. */
    public static double diff(double a, double b) {
        return Math.abs(wrap(a - b));
    }

    /** Wraps to -180..180. */
    public static double wrap(double a) {
        double d = ((a % 360) + 540) % 360 - 180;
        return d;
    }

    /** Turns from current toward target by at most maxStep degrees. */
    public static float approach(float current, float target, double maxStep) {
        double d = wrap(target - current);
        if (Math.abs(d) <= maxStep) return target;
        return (float) (current + Math.signum(d) * maxStep);
    }
}
