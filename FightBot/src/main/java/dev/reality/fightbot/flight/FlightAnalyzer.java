package dev.reality.fightbot.flight;

import dev.reality.fightbot.core.Json;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Running totals for one episode: displacement, distance, speed changes, time per state, glide
 * segments, how far the predictions were off, and how often actions failed or were interrupted.
 * Only samples that reported the needed values count toward each number, and each number says how
 * many samples it is based on.
 */
public final class FlightAnalyzer {
    private FlightSample first, lastPos, lastVel;
    private double path;
    private int pathSteps;
    private double maxSpeed, sumDv;
    private int dvCount;
    private final Map<MovementState, Integer> stateTicks = new EnumMap<>(MovementState.class);
    private int samples, missing;
    // Glide segments.
    private int glides;
    private long glideTicks, longestGlide, currentGlide;
    private double glideDistance;
    // Prediction error per state.
    private final Map<String, double[]> error = new TreeMap<>();
    // Actions.
    private final Map<String, int[]> actions = new TreeMap<>();

    public void sample(FlightSample s, MovementState state) {
        samples++;
        stateTicks.merge(state, 1, Integer::sum);
        if (s == null || !s.present()) {
            missing++;
            return;
        }
        if (s.hasPosition()) {
            if (first == null) first = s;
            if (lastPos != null) {
                double d = Math.sqrt(sq(s.x() - lastPos.x()) + sq(s.y() - lastPos.y()) + sq(s.z() - lastPos.z()));
                path += d;
                pathSteps++;
                if (state == MovementState.GLIDING) glideDistance += d;
            }
            lastPos = s;
        }
        if (s.hasVelocity()) {
            maxSpeed = Math.max(maxSpeed, s.speed());
            if (lastVel != null) {
                sumDv += Math.sqrt(sq(s.vx() - lastVel.vx()) + sq(s.vy() - lastVel.vy()) + sq(s.vz() - lastVel.vz()));
                dvCount++;
            }
            lastVel = s;
        }
        if (state == MovementState.GLIDING) {
            if (currentGlide == 0) glides++;
            currentGlide++;
            glideTicks++;
            longestGlide = Math.max(longestGlide, currentGlide);
        } else {
            currentGlide = 0;
        }
    }

    /** Records how far a prediction was from what came next, by state. */
    public void predictionError(MovementState state, Predictor.Prediction p, FlightSample actual) {
        if (p == null || actual == null || !actual.hasVelocity()) return;
        double[] e = error.computeIfAbsent(state.name(), k -> new double[4]);
        e[0] += Math.abs(actual.vy() - p.vy());
        if (p.horizontalComparable()) {
            e[1] += Math.abs(actual.vx() - p.vx()) + Math.abs(actual.vz() - p.vz());
            e[3]++;
        }
        e[2]++;
    }

    public void action(ActionTracker.ActionRecord r) {
        int[] a = actions.computeIfAbsent(r.action, k -> new int[4]);
        a[0]++;
        if (Boolean.TRUE.equals(r.accepted)) a[1]++;
        else if (Boolean.FALSE.equals(r.accepted)) a[2]++;
        if (r.interruption != null) a[3]++;
    }

    public String summaryJson() {
        Json j = new Json().put("samples", samples).put("missingSamples", missing);
        if (first != null && lastPos != null) {
            j.put("displacementX", lastPos.x() - first.x(), 3).put("displacementY", lastPos.y() - first.y(), 3)
                    .put("displacementZ", lastPos.z() - first.z(), 3)
                    .put("horizontalDisplacement", Math.hypot(lastPos.x() - first.x(), lastPos.z() - first.z()), 3);
        }
        j.put("distanceTraveled", path, 3).put("distanceSteps", pathSteps).put("maxSpeed", maxSpeed, 4)
                .put("averageVelocityChangePerTick", dvCount == 0 ? Double.NaN : sumDv / dvCount, 5).put("velocityChangeSteps", dvCount);
        Json st = new Json();
        for (var e : stateTicks.entrySet()) st.put(e.getKey().name(), e.getValue());
        j.raw("ticksPerState", st.build());
        j.put("glides", glides).put("glideTicks", glideTicks).put("longestGlideTicks", longestGlide).put("glideDistance", glideDistance, 3);
        Json err = new Json();
        for (var e : error.entrySet()) {
            double[] v = e.getValue();
            err.raw(e.getKey(), new Json().put("comparedTicks", (long) v[2]).put("meanVerticalError", v[2] == 0 ? Double.NaN : v[0] / v[2], 5)
                    .put("horizontalComparedTicks", (long) v[3]).put("meanHorizontalError", v[3] == 0 ? Double.NaN : v[1] / v[3], 5).build());
        }
        j.raw("predictionError", err.build());
        Json act = new Json();
        int total = 0, failed = 0, interrupted = 0;
        for (var e : actions.entrySet()) {
            int[] a = e.getValue();
            total += a[0];
            failed += a[2];
            interrupted += a[3];
            act.raw(e.getKey(), new Json().put("count", a[0]).put("accepted", a[1]).put("rejected", a[2])
                    .put("unknown", a[0] - a[1] - a[2]).put("interrupted", a[3]).build());
        }
        j.raw("actions", act.build());
        j.put("rejectedActionRate", total == 0 ? Double.NaN : (double) failed / total, 3)
                .put("interruptedActionRate", total == 0 ? Double.NaN : (double) interrupted / total, 3);
        return j.build();
    }

    public int ticksIn(MovementState s) {
        return stateTicks.getOrDefault(s, 0);
    }

    public int glides() {
        return glides;
    }

    public double meanVerticalError(MovementState s) {
        double[] e = error.get(s.name());
        return e == null || e[2] == 0 ? Double.NaN : e[0] / e[2];
    }

    public double meanHorizontalError(MovementState s) {
        double[] e = error.get(s.name());
        return e == null || e[3] == 0 ? Double.NaN : e[1] / e[3];
    }

    private static double sq(double v) {
        return v * v;
    }
}
