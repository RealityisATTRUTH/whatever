package dev.reality.fightbot.flight;

import dev.reality.fightbot.core.Json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Records practice actions with their timing and result. Actions come from two places:
 * commands the bot itself sent (high confidence about the attempt), and changes seen in the samples,
 * like a firework count dropping or the elytra flag turning on (medium or low confidence).
 * An action is accepted when its expected effect shows up within the confirm window, rejected when it
 * does not, and left unknown when the samples needed to tell are missing.
 */
public final class ActionTracker {
    public enum Confidence { HIGH, MEDIUM, LOW }

    public static final class ActionRecord {
        public final String action;
        public final long startTick;
        public long endTick;
        public final MovementState before;
        public MovementState after;
        /** True, false, or null when it could not be told. */
        public Boolean accepted;
        public double dvx = Double.NaN, dvy = Double.NaN, dvz = Double.NaN, dSpeed = Double.NaN;
        public String interruption;
        public Confidence confidence;
        public final boolean commanded;
        final FlightSample startSample;
        boolean open = true;

        ActionRecord(String action, long tick, MovementState before, FlightSample start, boolean commanded, Confidence conf) {
            this.action = action;
            this.startTick = tick;
            this.endTick = tick;
            this.before = before;
            this.after = before;
            this.startSample = start;
            this.commanded = commanded;
            this.confidence = conf;
        }

        public String toJson() {
            return new Json().put("action", action).put("startTick", startTick).put("endTick", endTick)
                    .put("before", before.name()).put("after", after.name()).put("accepted", accepted)
                    .put("dvx", dvx, 4).put("dvy", dvy, 4).put("dvz", dvz, 4).put("dSpeed", dSpeed, 4)
                    .put("interruption", interruption).put("confidence", confidence.name()).put("commanded", commanded).build();
        }
    }

    private final MechanicsConfig c;
    private final List<ActionRecord> open = new ArrayList<>();
    private final List<ActionRecord> done = new ArrayList<>();
    private FlightSample last;
    private long rocketStart = Long.MIN_VALUE;
    private boolean rocketConfirmed;

    public ActionTracker(MechanicsConfig config) {
        this.c = config;
    }

    /**
     * Whether a firework pushes during this tick: true, false, or null when it cannot be told. A rocket's
     * burn time is random within a range, and an unconfirmed rocket may have failed, so those ticks are null.
     */
    public Boolean rocketStatus(long tick) {
        if (rocketStart == Long.MIN_VALUE || tick < rocketStart) return false;
        long age = tick - rocketStart;
        if (!rocketConfirmed) return age <= c.fireworkMaxTicks ? null : Boolean.FALSE;
        if (age < c.fireworkMinTicks) return true;
        if (age <= c.fireworkMaxTicks) return null;
        return false;
    }

    /** The bot sent this action this tick. Same-tick observations of the same action merge into it. */
    public void commanded(String action, long tick, MovementState state, FlightSample sample) {
        for (ActionRecord r : open) if (r.action.equals(action) && Math.abs(r.startTick - tick) <= 1) return;
        open.add(new ActionRecord(action, tick, state, sample, true, Confidence.HIGH));
    }

    /** Looks at what changed since the last sample, starts records for seen actions, and settles open ones. */
    public List<ActionRecord> update(FlightSample s, MovementState state, String interruption) {
        List<ActionRecord> finished = new ArrayList<>();
        if (s != null && s.present() && last != null && last.present()) {
            if (last.fireworks() >= 0 && s.fireworks() >= 0 && s.fireworks() < last.fireworks()) seen("firework", s, state, Confidence.MEDIUM);
            if (last.windCharges() >= 0 && s.windCharges() >= 0 && s.windCharges() < last.windCharges()) seen("wind_charge", s, state, Confidence.MEDIUM);
            if (Boolean.FALSE.equals(last.gliding()) && Boolean.TRUE.equals(s.gliding())) seen("glide_start", s, state, Confidence.HIGH);
            if (Boolean.TRUE.equals(last.gliding()) && Boolean.FALSE.equals(s.gliding())) seen("glide_end", s, state, Confidence.HIGH);
            if (last.chest() != null && s.chest() != null && !last.chest().equals(s.chest())) {
                String what = s.chest().equals("elytra") ? "elytra" : s.chest().endsWith("_chestplate") ? "chestplate" : s.chest();
                seen("equip_" + what, s, state, Confidence.HIGH);
            }
            if (last.selectedSlot() >= 0 && s.selectedSlot() >= 0 && last.selectedSlot() != s.selectedSlot()) seen("hotbar_" + s.selectedSlot(), s, state, Confidence.HIGH);
            if (Boolean.TRUE.equals(last.onGround()) && Boolean.FALSE.equals(s.onGround()) && s.hasVelocity() && s.vy() > 0.3) {
                seen("jump", s, state, Confidence.LOW);
            }
        }
        for (Iterator<ActionRecord> it = open.iterator(); it.hasNext(); ) {
            ActionRecord r = it.next();
            if (s == null) continue;
            if (settle(r, s, state, interruption)) {
                it.remove();
                done.add(r);
                finished.add(r);
            }
        }
        if (s != null) last = s;
        return finished;
    }

    /** Ends every open action, for when the episode stops or the player disconnects. */
    public List<ActionRecord> closeAll(String why, MovementState state) {
        List<ActionRecord> out = new ArrayList<>(open);
        for (ActionRecord r : open) {
            r.after = state;
            if (r.accepted == null) r.interruption = why;
            done.add(r);
        }
        open.clear();
        return out;
    }

    public List<ActionRecord> all() {
        return done;
    }

    private void seen(String action, FlightSample s, MovementState state, Confidence conf) {
        for (ActionRecord r : open) {
            if (r.action.equals(action) && s.tick() - r.startTick <= c.confirmWindow) {
                // The bot's own command, now also seen in the samples.
                if (r.commanded) r.confidence = Confidence.HIGH;
                return;
            }
        }
        // Count drops are seen one tick after the action, so the start uses the previous sample.
        open.add(new ActionRecord(action, s.tick(), state, last, false, conf));
        if (action.equals("firework")) {
            rocketStart = s.tick();
            rocketConfirmed = false;
        }
    }

    /** Returns true when the record is finished. */
    private boolean settle(ActionRecord r, FlightSample s, MovementState state, String interruption) {
        r.endTick = s.tick();
        r.after = state;
        FlightSample a = r.startSample;
        if (a != null && a.hasVelocity() && s.hasVelocity()) {
            r.dvx = s.vx() - a.vx();
            r.dvy = s.vy() - a.vy();
            r.dvz = s.vz() - a.vz();
            r.dSpeed = s.speed() - a.speed();
        }
        if (interruption != null && r.interruption == null) r.interruption = interruption;
        long age = s.tick() - r.startTick;
        boolean windowOver = age >= c.confirmWindow;
        Boolean effect = effect(r, s);
        if (Boolean.TRUE.equals(effect)) {
            r.accepted = true;
            if (r.action.equals("firework")) {
                rocketStart = r.startTick;
                rocketConfirmed = true;
            }
            return true;
        }
        if (windowOver) {
            r.accepted = effect == null ? null : false;
            if (effect == null && r.interruption == null) r.interruption = s.present() ? "effect could not be measured" : "player missing";
            return true;
        }
        return false;
    }

    /** Whether the action's effect is visible yet: true, false, or null when the data cannot tell. */
    private Boolean effect(ActionRecord r, FlightSample s) {
        switch (r.action) {
            case "glide_start":
                return s.gliding();
            case "glide_end":
                return s.gliding() == null ? null : !s.gliding();
            case "firework":
                if (Double.isNaN(r.dSpeed)) return null;
                return r.dSpeed >= c.fireworkConfirmGain ? Boolean.TRUE : Boolean.FALSE;
            case "wind_charge":
                if (Double.isNaN(r.dvy)) return null;
                return r.dvy >= c.windConfirmVy ? Boolean.TRUE : Boolean.FALSE;
            case "jump":
                return s.onGround() == null ? null : !s.onGround();
            default:
                if (r.action.startsWith("equip_")) {
                    if (s.chest() == null) return null;
                    String want = r.action.equals("equip_elytra") ? "elytra" : r.action.substring(6);
                    return want.equals("chestplate") ? s.chest().endsWith("_chestplate") : s.chest().equals(want);
                }
                if (r.action.startsWith("hotbar_")) {
                    if (s.selectedSlot() < 0) return null;
                    return s.selectedSlot() == Integer.parseInt(r.action.substring(7));
                }
                return null;
        }
    }
}
