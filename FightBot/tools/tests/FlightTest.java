import dev.reality.fightbot.flight.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the flight module without Minecraft: state transitions, crashes, missing data, disconnects,
 * action results, prediction error, config loading, and saving. The samples come from a small
 * scripted flight using vanilla's movement rules, so these tests check the module's logic, not the game.
 */
public class FlightTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    /** A scripted body that produces samples. */
    static final class Body {
        long t;
        double x, y = 64, z, vx, vy, vz;
        float yaw, pitch;
        boolean ground = true, gliding, hCol;
        double fall;
        int fireworks = 10, wind = 5, slot;
        String chest = "elytra";
        float health = 20;
        int rocket;

        FlightSample sample() {
            return new FlightSample(t, t * 50, true, x, y, z, yaw, pitch, vx, vy, vz, "server", ground, gliding, fall, hCol, false,
                    slot, "netherite_sword", chest, fireworks, wind, 400, ground ? 0 : y - 64, 0, health);
        }

        /** One tick of vanilla-style movement, matching the module's default config. */
        void tick() {
            t++;
            hCol = false;
            if (gliding) {
                double pr = Math.toRadians(pitch), yr = Math.toRadians(yaw);
                double lx = -Math.sin(yr) * Math.cos(pr), ly = -Math.sin(pr), lz = Math.cos(yr) * Math.cos(pr);
                if (rocket > 0) {
                    vx += lx * 0.1 + (lx * 1.5 - vx) * 0.5; vy += ly * 0.1 + (ly * 1.5 - vy) * 0.5; vz += lz * 0.1 + (lz * 1.5 - vz) * 0.5;
                    rocket--;
                }
                double horiz = Math.hypot(lx, lz), hs = Math.hypot(vx, vz), cos = Math.cos(pr);
                cos *= cos;
                vy += 0.08 * (-1 + cos * 0.75);
                if (vy < 0 && horiz > 0) { double d = vy * -0.1 * cos; vx += lx * d / horiz; vy += d; vz += lz * d / horiz; }
                if (pr < 0 && horiz > 0) { double d = hs * -Math.sin(pr) * 0.04; vx += -lx * d / horiz; vy += d * 3.2; vz += -lz * d / horiz; }
                if (horiz > 0) { vx += (lx / horiz * hs - vx) * 0.1; vz += (lz / horiz * hs - vz) * 0.1; }
                vx *= 0.99; vy *= 0.98; vz *= 0.99;
                x += vx; y += vy; z += vz;
            } else if (!ground) {
                vx *= 0.91; vy = (vy - 0.08) * 0.98; vz *= 0.91;
                x += vx; y += vy; z += vz;
                if (vy < 0) fall -= vy;
            } else {
                vx *= 0.546; vz *= 0.546; vy = 0;
            }
            if (y <= 64 && !ground && t > 1) {
                y = 64; vy = 0; ground = true; gliding = false; rocket = 0;
                if (fall > 3) health -= (float) (fall - 3);
                fall = 0;
            }
        }
    }

    static List<MovementState> run(FlightSession s, List<FlightSample> samples) {
        List<MovementState> seen = new ArrayList<>();
        for (FlightSample f : samples) {
            s.add(f);
            MovementState st = s.estimator.state();
            if (seen.isEmpty() || seen.get(seen.size() - 1) != st) seen.add(st);
        }
        return seen;
    }

    static boolean inOrder(List<MovementState> seen, MovementState... want) {
        int i = 0;
        for (MovementState m : seen) if (i < want.length && m == want[i]) i++;
        return i == want.length;
    }

    static ActionTracker.ActionRecord find(FlightSession s, String action) {
        for (ActionTracker.ActionRecord r : s.actions.all()) if (r.action.equals(action)) return r;
        return null;
    }

    public static void main(String[] args) throws Exception {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        dev.reality.fightbot.core.BotProfile.defaultHumanize = 0;
        MechanicsConfig cfg = new MechanicsConfig();
        cfg.gameVersion = "test";

        // 1. Take off, rocket, glide, land.
        Body b = new Body();
        FlightSession s1 = new FlightSession("Bot", "takeoff", cfg);
        List<FlightSample> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) { list.add(b.sample()); b.tick(); }
        b.ground = false; b.vy = 0.42;
        for (int i = 0; i < 3; i++) { b.tick(); list.add(b.sample()); }
        b.gliding = true; b.pitch = -40;
        b.tick(); list.add(b.sample());
        b.fireworks--; b.rocket = 25;
        for (int i = 0; i < 60; i++) { b.tick(); list.add(b.sample()); }
        b.pitch = 20;
        for (int i = 0; i < 400 && !b.ground; i++) { b.tick(); list.add(b.sample()); }
        for (int i = 0; i < 10; i++) { b.tick(); list.add(b.sample()); }
        List<MovementState> seen = run(s1, list);
        s1.end("done");
        System.out.println("  states: " + seen);
        check(inOrder(seen, MovementState.GROUNDED, MovementState.AIRBORNE, MovementState.GLIDING, MovementState.LANDING, MovementState.GROUNDED),
                "goes grounded, airborne, gliding, landing, grounded");
        ActionTracker.ActionRecord fw = find(s1, "firework");
        check(fw != null && Boolean.TRUE.equals(fw.accepted) && fw.confidence == ActionTracker.Confidence.MEDIUM,
                "a firework seen from the count dropping is accepted once speed jumps");
        ActionTracker.ActionRecord gs = find(s1, "glide_start");
        check(gs != null && Boolean.TRUE.equals(gs.accepted), "glide start is recorded and accepted");
        double ev = s1.analyzer.meanVerticalError(MovementState.GLIDING), eh = s1.analyzer.meanHorizontalError(MovementState.GLIDING);
        System.out.printf("  gliding prediction error with matching config: vertical %.6f, horizontal %.6f%n", ev, eh);
        check(ev < 1e-6 && eh < 1e-6, "predictions match a flight that follows the config exactly");
        check(s1.analyzer.glides() == 1 && s1.analyzer.ticksIn(MovementState.GLIDING) > 50, "counts one glide and its length");

        // A wrong config shows up as prediction error.
        MechanicsConfig wrong = new MechanicsConfig();
        wrong.gravity = 0.1;
        FlightSession s1b = new FlightSession("Bot", "wrong", wrong);
        run(s1b, list);
        System.out.printf("  gliding vertical error with gravity set wrong: %.5f%n", s1b.analyzer.meanVerticalError(MovementState.GLIDING));
        check(s1b.analyzer.meanVerticalError(MovementState.GLIDING) > 0.005, "a wrong gravity number shows up as prediction error");

        // 2. Crash into a wall while gliding.
        Body c = new Body();
        c.ground = false; c.gliding = true; c.y = 80; c.vz = 1.4; c.pitch = 0;
        FlightSession s2 = new FlightSession("Bot", "crash", cfg);
        List<FlightSample> cl = new ArrayList<>();
        for (int i = 0; i < 5; i++) { c.tick(); cl.add(c.sample()); }
        c.vx = 0; c.vz = 0; c.hCol = true; c.health -= 4;
        cl.add(c.sample());
        c.gliding = false;
        for (int i = 0; i < 200 && !c.ground; i++) { c.tick(); cl.add(c.sample()); }
        for (int i = 0; i < 15; i++) { c.tick(); cl.add(c.sample()); }
        List<MovementState> cs = run(s2, cl);
        System.out.println("  states: " + cs);
        ActionTracker.ActionRecord ge = find(s2, "glide_end");
        check(ge != null && "crash into a wall".equals(ge.interruption), "a crash interrupts the glide and says why");
        check(cs.contains(MovementState.RECOVERY), "a crash leads to recovery");

        // 3. Missing data.
        FlightSession s3 = new FlightSession("Bot", "missing", cfg);
        Body m = new Body();
        boolean threw = false;
        try {
            s3.add(m.sample());
            s3.add(FlightSample.missing(1, 50));
            s3.add(null);
            s3.add(new FlightSample(3, 150, true, 0, 64, 0, 0, 0, Double.NaN, Double.NaN, Double.NaN, "none", null, null,
                    Double.NaN, null, null, -1, null, null, -1, -1, -1, Double.NaN, -1, Float.NaN));
        } catch (Exception e) {
            threw = true;
        }
        check(!threw && s3.estimator.state() == MovementState.UNKNOWN, "missing players and missing fields give unknown, without crashing");
        FlightSession s3b = new FlightSession("Bot", "nan", cfg);
        s3b.add(new FlightSample(0, 0, true, 0, 70, 0, 0, 0, 0, 0, 0, "server", false, false, 0, false, false, 0, "a", "elytra", 1, 1, 1, 6, 0, 20));
        s3b.add(new FlightSample(1, 50, true, 0, 70, 0, 0, 0, Double.NaN, Double.NaN, Double.NaN, "none", false, false, 0, false, false, 0, "a", "elytra", 1, 1, 1, 6, 0, 20));
        check(s3b.estimator.state() == MovementState.UNKNOWN, "in the air with no velocity reported gives unknown, not a guess");
        check(Double.isNaN(s3b.analyzer.meanVerticalError(MovementState.AIRBORNE)), "no prediction is scored without velocity");

        // 4. Disconnect.
        FlightSession s4 = new FlightSession("Bot", "disconnect", cfg);
        Body d = new Body();
        s4.add(d.sample());
        s4.commanded("firework", 1);
        boolean alive = true;
        for (int i = 2; i < 100 && alive; i++) alive = s4.add(FlightSample.missing(i, i * 50L));
        check(!alive && "disconnect".equals(s4.endReason), "the episode ends as a disconnect when the player stays missing");
        ActionTracker.ActionRecord orphan = find(s4, "firework");
        check(orphan != null && orphan.interruption != null, "open actions are closed with the reason when the player disconnects");
        check(!s4.add(d.sample()), "nothing is added after the episode ended");

        // 5. Hard landing.
        Body h = new Body();
        h.ground = false; h.y = 80;
        FlightSession s5 = new FlightSession("Bot", "hard", cfg);
        List<FlightSample> hl = new ArrayList<>();
        hl.add(h.sample());
        for (int i = 0; i < 200 && !h.ground; i++) { h.tick(); hl.add(h.sample()); }
        for (int i = 0; i < 15; i++) { h.tick(); hl.add(h.sample()); }
        List<MovementState> hs = run(s5, hl);
        System.out.println("  states: " + hs);
        check(inOrder(hs, MovementState.DESCENDING, MovementState.RECOVERY, MovementState.GROUNDED), "a fall that hurts goes descending, recovery, grounded");

        // 6. Wind charges and a commanded action that does nothing.
        Body w = new Body();
        FlightSession s6 = new FlightSession("Bot", "wind", cfg);
        s6.add(w.sample());
        w.wind--; w.ground = false; w.vy = 1.1; w.tick();
        s6.add(w.sample());
        for (int i = 0; i < 6; i++) { w.tick(); s6.add(w.sample()); }
        ActionTracker.ActionRecord wc = find(s6, "wind_charge");
        check(wc != null && Boolean.TRUE.equals(wc.accepted), "a wind charge that launches is accepted");
        Body w2 = new Body();
        FlightSession s6b = new FlightSession("Bot", "dud", cfg);
        s6b.add(w2.sample());
        s6b.commanded("firework", 1);
        for (int i = 0; i < 8; i++) { w2.tick(); s6b.add(w2.sample()); }
        ActionTracker.ActionRecord dud = find(s6b, "firework");
        check(dud != null && Boolean.FALSE.equals(dud.accepted) && dud.confidence == ActionTracker.Confidence.HIGH,
                "a commanded firework with no boost is rejected, with high confidence it was tried");

        // 7. Config file and saving.
        Path tmp = Files.createTempDirectory("flighttest");
        MechanicsConfig fresh = MechanicsConfig.load(tmp, "26.3");
        check(Files.exists(tmp.resolve("26.3.properties")) && !fresh.verified, "a new version gets a default config marked unverified");
        Files.writeString(tmp.resolve("26.3.properties"), Files.readString(tmp.resolve("26.3.properties")).replace("gravity=0.08", "gravity=0.0799"));
        check(MechanicsConfig.load(tmp, "26.3").gravity == 0.0799, "edited numbers are read back");
        Path saved = s1.save(tmp);
        String summary = Files.readString(saved.resolve("summary.json"));
        check(Files.exists(saved.resolve("samples.jsonl")) && Files.exists(saved.resolve("actions.jsonl"))
                && summary.contains("\"gameVersion\":\"test\"") && summary.contains("\"configVerified\":false"),
                "saves samples, actions, and a summary with the game version and config");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
