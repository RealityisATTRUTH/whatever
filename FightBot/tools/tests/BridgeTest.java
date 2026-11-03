import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.nav.Navigator;
import dev.reality.fightbot.core.nav.SpeedBridge;
import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.world.WorldView;
import java.util.*;

/**
 * Speed bridging over the void, with movement worked out the way the game does it on the ground: input of
 * 0.98 per key, a diagonal scaled to length 1, sneaking at 0.3 of that, 0.1 of it added a tick, then 0.546
 * of the speed kept. Sneaking on the ground backs the move off an edge in 0.05 block steps, as the game does.
 * A player stands while any of their 0.6 width is over a block. Clicks only land on a face the eyes are past.
 */
public class BridgeTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    /** A start platform, a goal platform 30 blocks away, and the void everywhere else. */
    static final class Void implements WorldView {
        final Set<String> placed = new HashSet<>();
        public Kind kind(int x, int y, int z) {
            if (y != -1) return Kind.AIR;
            boolean start = x >= -3 && x <= 2 && Math.abs(z) <= 3;
            boolean goal = x >= 33 && x <= 40 && Math.abs(z) <= 3;
            return start || goal || placed.contains(x + "," + z) ? Kind.SOLID : Kind.AIR;
        }
        public int anchorCharge(int x, int y, int z) { return -1; }
        public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) { return false; }
        public List<Crystal> crystals() { return List.of(); }
        public String dimension() { return "overworld"; }
        public String difficulty() { return "normal"; }
        public Armor armor(boolean self) { return new Armor(20, 12, 16); }
    }

    static boolean supported(Void w, double x, double z, double y) {
        int fy = (int) Math.floor(y - 1e-3);
        for (double ox : new double[] {-0.2999, 0.2999}) for (double oz : new double[] {-0.2999, 0.2999}) {
            if (w.solid((int) Math.floor(x + ox), fy, (int) Math.floor(z + oz))) return true;
        }
        return false;
    }

    record Result(boolean reached, boolean fell, long ticks, int placed, int recoveries, int desyncs, List<String> phases) { }

    static Result run(BotProfile p, double failChance, double nudgeChance, long seed) {
        return run(p, failChance, nudgeChance, 0, seed);
    }

    static Result run(BotProfile p, double failChance, double nudgeChance, double pullBackChance, long seed) {
        Random rng = new Random(seed);
        Void w = new Void();
        Navigator nav = new Navigator(p);
        nav.setWorld(w);
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "cobblestone*64");
        double x = 0.5, y = 0, z = 0.5, vx = 0, vy = 0, vz = 0;
        float yaw = 0, pitch = 0;
        boolean onGround = true;
        int placedBlocks = 0;
        List<String> phases = new ArrayList<>();
        boolean trace = Long.toString(seed).equals(System.getProperty("traceSeed")) && failChance > 0;
        for (long t = 0; t < 2400; t++) {
            if (y < -1.5) return new Result(false, true, t, placedBlocks, nav.speedBridge().recoveries, nav.speedBridge().desyncs, phases);
            if (x > 34 && onGround) return new Result(true, false, t, placedBlocks, nav.speedBridge().recoveries, nav.speedBridge().desyncs, phases);
            Observation o = new Observation(t, true, x, y, z, vx, vy, vz, yaw, pitch, onGround, false, false, 20, 0,
                    "cobblestone", 1.0, 0, -1, 0, 37.5, 0, 0.5, 0, 0, 0, 0, true, false, false, false, 20, 0,
                    "netherite_sword", false, 0, false, -1, -1, 0, 0, 0, 1.0, false, 0, 0, l, 0, false, true, 10, 10);
            BotAction a = BotAction.idle(yaw, pitch);
            if (!nav.step(o, a)) {
                a.yaw = dev.reality.fightbot.core.place.Placer.yawTo(x, z, 37.5, 0.5);
                a.forward = 1;
            }
            String ph = nav.stateName();
            if (trace) System.out.printf("t%d x%.3f z%.3f y%.2f v%.3f g%b %s %s sneak%b f%d s%d yaw%.0f place%s%n", t, x, z, y, vx, onGround, ph, a.technique, a.sneak, a.forward, a.strafe, a.yaw, a.place != null);
            if (phases.isEmpty() || !phases.get(phases.size() - 1).equals(ph)) phases.add(ph);
            yaw = a.yaw;
            pitch = a.pitch;
            // The click, from where the eyes are now: only a face they are past, in reach, and sometimes lost.
            if (a.place != null) {
                PlaceRequest r = a.place;
                double ex = x, ey = y + 1.62, ez = z;
                int nx = r.tx() - r.cx(), nz = r.tz() - r.cz(), ny = r.ty() - r.cy();
                double cx = r.cx() + 0.5 + nx * 0.5, cy = r.cy() + 0.5 + ny * 0.5, cz = r.cz() + 0.5 + nz * 0.5;
                boolean facing = (ex - cx) * nx + (ey - cy) * ny + (ez - cz) * nz > 0;
                double reach = Math.sqrt(Math.pow(ex - r.hx(), 2) + Math.pow(ey - r.hy(), 2) + Math.pow(ez - r.hz(), 2));
                if (facing && reach <= 4.5 && w.solid(r.cx(), r.cy(), r.cz()) && rng.nextDouble() >= failChance) {
                    w.placed.add(r.tx() + "," + r.tz());
                    placedBlocks++;
                }
            }
            // Movement, as the game runs it.
            double ix = a.strafe * 0.98, iz = a.forward * 0.98;
            double len = Math.sqrt(ix * ix + iz * iz);
            if (len > 1) { ix /= len; iz /= len; }
            if (a.sneak) { ix *= 0.3; iz *= 0.3; }
            double acc = onGround ? (a.sprint && a.forward > 0 && !a.sneak ? 0.13 : 0.1) : (a.sprint ? 0.026 : 0.02);
            if (a.jump && onGround) {
                vy = 0.42;
                onGround = false;
                if (a.sprint) {
                    double jr = Math.toRadians(yaw);
                    vx += -Math.sin(jr) * 0.2;
                    vz += Math.cos(jr) * 0.2;
                }
            }
            double yr = Math.toRadians(yaw);
            vx += (ix * Math.cos(yr) - iz * Math.sin(yr)) * acc;
            vz += (iz * Math.cos(yr) + ix * Math.sin(yr)) * acc;
            double dx = vx, dz = vz;
            if (a.sneak && onGround) {
                while (dx != 0 && !supported(w, x + dx, z, y)) dx = Math.abs(dx) < 0.05 ? 0 : dx - Math.signum(dx) * 0.05;
                while (dz != 0 && !supported(w, x + dx, z + dz, y)) dz = Math.abs(dz) < 0.05 ? 0 : dz - Math.signum(dz) * 0.05;
            }
            x += dx;
            z += dz;
            if (dx != vx) vx = dx;
            if (dz != vz) vz = dz;
            // A server correction now and then.
            if (rng.nextDouble() < nudgeChance) x += (rng.nextDouble() - 0.5) * 0.1;
            // A rubber band: the server puts it back where it was a few ticks ago.
            if (rng.nextDouble() < pullBackChance) x -= 0.4;
            if (onGround && vy <= 0 && supported(w, x, z, y)) {
                vy = 0;
            } else {
                onGround = false;
                y += vy;
                vy = (vy - 0.08) * 0.98;
                if (vy < 0 && y <= 0 && y > -0.6 && supported(w, x, z, 0)) {
                    y = 0;
                    vy = 0;
                    onGround = true;
                }
            }
            double drag = onGround ? 0.546 : 0.91;
            vx *= drag;
            vz *= drag;
        }
        return new Result(false, false, 2400, placedBlocks, nav.speedBridge().recoveries, nav.speedBridge().desyncs, phases);
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;

        BotProfile ninja = new BotProfile();
        Result r = run(ninja, 0, 0, 1);
        double perBlock = (double) r.ticks() / Math.max(1, r.placed());
        System.out.printf("  ninja bridge: %s in %d ticks, %d blocks, %.1f ticks a block%n", r.reached() ? "crossed" : r.fell() ? "fell" : "stuck", r.ticks(), r.placed(), perBlock);
        check(r.reached() && !r.fell(), "ninja bridges 30 blocks over the void");

        BotProfile slow = new BotProfile();
        slow.speedBridge = 0;
        Result rs = run(slow, 0, 0, 1);
        System.out.printf("  sneak bridge: %s in %d ticks, %d blocks%n", rs.reached() ? "crossed" : rs.fell() ? "fell" : "stuck", rs.ticks(), rs.placed());
        check(rs.reached() && r.ticks() < rs.ticks() * 0.7, "ninja bridging is much faster than sneaking the whole way");

        BotProfile straight = new BotProfile();
        straight.bridgeAngle = 0;
        Result r0 = run(straight, 0, 0, 1);
        System.out.printf("  straight back: %s in %d ticks%n", r0.reached() ? "crossed" : r0.fell() ? "fell" : "stuck", r0.ticks());
        check(r0.reached(), "also bridges walking straight back");

        // Lost clicks and server corrections: it may slow down, but it never falls.
        int falls = 0, crossed = 0, recov = 0, desync = 0;
        Set<String> allPhases = new HashSet<>(r.phases());
        for (long s = 0; s < 100; s++) {
            Result rr = run(ninja, 0.2, 0.03, s);
            if (rr.fell()) {
                falls++;
                System.out.println("  fell on seed " + s + " after " + rr.phases().subList(Math.max(0, rr.phases().size() - 4), rr.phases().size()));
            }
            allPhases.addAll(rr.phases());
            if (rr.reached()) crossed++;
            recov += rr.recoveries();
            desync += rr.desyncs();
        }
        System.out.printf("  with a fifth of clicks lost and server corrections: crossed %d of 100, fell %d, %d recoveries, %d corrections seen%n", crossed, falls, recov, desync);
        check(falls == 0, "never falls when clicks are lost or the server moves it");
        check(crossed >= 97, "still gets across nearly every time");
        check(recov > 0, "recovers from lost clicks");
        // Rubber bands back along the bridge: it notices, sneaks, and carries on.
        int rbFalls = 0, rbCrossed = 0, rbSeen = 0;
        for (long s = 0; s < 40; s++) {
            Result rr = run(ninja, 0, 0, 0.02, s);
            if (rr.fell()) rbFalls++;
            if (rr.reached()) rbCrossed++;
            rbSeen += rr.desyncs();
            allPhases.addAll(rr.phases());
        }
        System.out.printf("  with the server pulling it back now and then: crossed %d of 40, fell %d, %d pull backs noticed%n", rbCrossed, rbFalls, rbSeen);
        check(rbFalls == 0 && rbCrossed >= 39, "carries on after being pulled back");
        check(rbSeen > 0, "notices when the server moves it");
        check(allPhases.containsAll(List.of("SPEED_BRIDGE_APPROACH_EDGE", "SPEED_BRIDGE_UN_SHIFT", "SPEED_BRIDGE_PLACE_BLOCK",
                "SPEED_BRIDGE_RE_SHIFT", "SPEED_BRIDGE_RECOVER")), "goes through every bridging phase");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
