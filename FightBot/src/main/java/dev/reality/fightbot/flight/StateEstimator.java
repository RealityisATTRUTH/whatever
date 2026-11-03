package dev.reality.fightbot.flight;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns samples into movement states. It only uses what the samples report: ground contact, the
 * elytra flag, vertical speed, collision flags, and health. When a key value is missing it reports
 * UNKNOWN rather than guess, and it never throws on odd input.
 */
public final class StateEstimator {
    public record Transition(long tick, MovementState from, MovementState to, String reason) {
    }

    private final MechanicsConfig c;
    private MovementState state = MovementState.UNKNOWN;
    private final List<Transition> transitions = new ArrayList<>();
    private FlightSample last;
    private int airTicks;
    private long holdUntil = Long.MIN_VALUE;
    private MovementState holdState;
    /** Reason for the latest crash or hard landing, for the action tracker. */
    private String lastInterruption;
    private long lastInterruptionTick = Long.MIN_VALUE;

    public StateEstimator(MechanicsConfig config) {
        this.c = config;
    }

    public MovementState state() {
        return state;
    }

    public List<Transition> transitions() {
        return transitions;
    }

    /** Most recent crash or hard landing within the given ticks, or null. */
    public String interruptionSince(long tick) {
        return lastInterruptionTick >= tick ? lastInterruption : null;
    }

    public MovementState update(FlightSample s) {
        if (s == null) return set(Long.MIN_VALUE, MovementState.UNKNOWN, "no sample");
        MovementState next;
        String why;
        if (!s.present()) {
            next = MovementState.UNKNOWN;
            why = "player missing";
        } else if (s.onGround() == null || s.gliding() == null) {
            next = MovementState.UNKNOWN;
            why = "ground or elytra flag not reported";
        } else {
            boolean hurt = last != null && last.present() && Float.isFinite(last.health()) && Float.isFinite(s.health())
                    && s.health() < last.health() - 0.01f;
            boolean crashed = Boolean.TRUE.equals(s.horizontalCollision()) && last != null && last.hasVelocity() && s.hasVelocity()
                    && last.speed() - s.speed() >= c.crashSpeedLoss;
            if (crashed) interrupt(s.tick(), "crash into a wall");

            if (s.gliding()) {
                next = MovementState.GLIDING;
                why = "elytra open";
                airTicks++;
            } else if (!s.onGround()) {
                airTicks++;
                if (!s.hasVelocity()) {
                    next = MovementState.UNKNOWN;
                    why = "in the air with no velocity reported";
                } else if (s.vy() < c.descendingVy) {
                    next = MovementState.DESCENDING;
                    why = "falling";
                } else {
                    next = MovementState.AIRBORNE;
                    why = "in the air";
                }
            } else {
                boolean wasInAir = state == MovementState.AIRBORNE || state == MovementState.DESCENDING || state == MovementState.GLIDING;
                if (wasInAir && airTicks >= c.minAirTicksForLanding) {
                    if (hurt || crashed) {
                        interrupt(s.tick(), hurt ? "hard landing" : "crash landing");
                        hold(s.tick(), MovementState.RECOVERY, c.recoveryTicks);
                        next = MovementState.RECOVERY;
                        why = lastInterruption;
                    } else {
                        hold(s.tick(), MovementState.LANDING, c.landingTicks);
                        next = MovementState.LANDING;
                        why = "touched down";
                    }
                } else if (s.tick() < holdUntil) {
                    next = holdState;
                    why = holdState == MovementState.LANDING ? "settling after landing" : "recovering";
                } else {
                    next = MovementState.GROUNDED;
                    why = "on the ground";
                }
                airTicks = 0;
            }
            // A crash in the air or a hit while flying also means recovery, once back on the ground or out of the glide.
            if (crashed && next != MovementState.GLIDING && next != MovementState.RECOVERY) {
                hold(s.tick(), MovementState.RECOVERY, c.recoveryTicks);
                next = MovementState.RECOVERY;
                why = "crash";
            }
        }
        last = s;
        return set(s.tick(), next, why);
    }

    private void hold(long tick, MovementState st, int ticks) {
        holdState = st;
        holdUntil = tick + ticks;
    }

    private void interrupt(long tick, String why) {
        lastInterruption = why;
        lastInterruptionTick = tick;
    }

    private MovementState set(long tick, MovementState next, String why) {
        if (next != state) transitions.add(new Transition(tick, state, next, why));
        state = next;
        return state;
    }
}
