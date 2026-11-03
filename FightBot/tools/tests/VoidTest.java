import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.world.WorldView;
import java.util.*;

/**
 * The whole brain in a void world, with movement worked out the way the game does it on the ground (see
 * BridgeTest). Two cases: bridging out to someone off at an angle, which used to zigzag and spin the bot,
 * and a sword fight on a one block bridge, where strafing and S-taps used to walk it off.
 */
public class VoidTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    static final class World implements WorldView {
        final Set<String> solid = new HashSet<>();
        void floor(int x, int z) { solid.add(x + "," + z); }
        public Kind kind(int x, int y, int z) { return y == -1 && solid.contains(x + "," + z) ? Kind.SOLID : Kind.AIR; }
        public int anchorCharge(int x, int y, int z) { return -1; }
        public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) { return false; }
        public List<Crystal> crystals() { return List.of(); }
        public String dimension() { return "overworld"; }
        public String difficulty() { return "normal"; }
        public Armor armor(boolean self) { return new Armor(20, 12, 16); }
    }

    static final class Body {
        double x, y, z, vx, vy, vz;
        float yaw, pitch;
        boolean onGround = true;
        Body(double x, double z) { this.x = x; this.z = z; }
    }

    static boolean supported(World w, double x, double z, double y) {
        int fy = (int) Math.floor(y - 1e-3);
        for (double ox : new double[] {-0.2999, 0.2999}) for (double oz : new double[] {-0.2999, 0.2999}) {
            if (w.solid((int) Math.floor(x + ox), fy, (int) Math.floor(z + oz))) return true;
        }
        return false;
    }

    /** One tick of ground and air movement for these keys. */
    static void move(World w, Body b, BotAction a) {
        double ix = a.strafe * 0.98, iz = a.forward * 0.98;
        double len = Math.sqrt(ix * ix + iz * iz);
        if (len > 1) { ix /= len; iz /= len; }
        if (a.sneak) { ix *= 0.3; iz *= 0.3; }
        double acc = b.onGround ? (a.sprint && a.forward > 0 && !a.sneak ? 0.13 : 0.1) : (a.sprint ? 0.026 : 0.02);
        if (a.jump && b.onGround) {
            b.vy = 0.42;
            b.onGround = false;
            if (a.sprint) {
                double jr = Math.toRadians(b.yaw);
                b.vx += -Math.sin(jr) * 0.2;
                b.vz += Math.cos(jr) * 0.2;
            }
        }
        double yr = Math.toRadians(b.yaw);
        b.vx += (ix * Math.cos(yr) - iz * Math.sin(yr)) * acc;
        b.vz += (iz * Math.cos(yr) + ix * Math.sin(yr)) * acc;
        double dx = b.vx, dz = b.vz;
        if (a.sneak && b.onGround) {
            while (dx != 0 && !supported(w, b.x + dx, b.z, b.y)) dx = Math.abs(dx) < 0.05 ? 0 : dx - Math.signum(dx) * 0.05;
            while (dz != 0 && !supported(w, b.x + dx, b.z + dz, b.y)) dz = Math.abs(dz) < 0.05 ? 0 : dz - Math.signum(dz) * 0.05;
        }
        b.x += dx;
        b.z += dz;
        if (dx != b.vx) b.vx = dx;
        if (dz != b.vz) b.vz = dz;
        if (b.onGround && b.vy <= 0 && supported(w, b.x, b.z, b.y)) {
            b.vy = 0;
        } else {
            b.onGround = false;
            b.y += b.vy;
            b.vy = (b.vy - 0.08) * 0.98;
            if (b.vy < 0 && b.y <= 0 && b.y > -0.6 && supported(w, b.x, b.z, 0)) {
                b.y = 0;
                b.vy = 0;
                b.onGround = true;
            }
        }
        double drag = b.onGround ? 0.546 : 0.91;
        b.vx *= drag;
        b.vz *= drag;
    }

    static Observation obs(long t, Body b, Body o, Loadout l, double charge, String held) {
        return new Observation(t, true, b.x, b.y, b.z, b.vx, b.vy, b.vz, b.yaw, b.pitch, b.onGround, false, false, 20, 0,
                held, charge, 0, -1, 0, o.x, o.y, o.z, o.vx, 0, o.vz, 0, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.6, false, 0, 0, l, 0, false, true, 10, 10);
    }

    record Run(boolean reached, boolean fell, long ticks, int bigTurns, int saves) { }

    /** Bridges from a small platform out to a target standing on one off at an angle. */
    static Run diagonal(BotProfile p, long seed) {
        World w = new World();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) w.floor(x, z);
        for (int x = 18; x <= 24; x++) for (int z = 14; z <= 20; z++) w.floor(x, z);
        Body bot = new Body(0.5, 0.5), target = new Body(21.5 + seed % 3, 17.5);
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "cobblestone*64");
        StyleBrain sb = new StyleBrain(p, null);
        int bigTurns = 0;
        for (long t = 0; t < 1500; t++) {
            if (bot.y < -2) return new Run(false, true, t, bigTurns, sb.edgeGuard().saves);
            if (Math.hypot(bot.x - target.x, bot.z - target.z) < 3 && bot.onGround) return new Run(true, false, t, bigTurns, sb.edgeGuard().saves);
            sb.setWorld(w);
            BotAction a = sb.step(obs(t, bot, target, l, 1.0, "netherite_sword"));
            if (Math.abs(Angles.wrap(a.yaw - bot.yaw)) > 60) bigTurns++;
            if (System.getProperty("traceDiag") != null && seed == 0) System.out.printf("t%d x%.2f z%.2f y%.2f g%b yaw%.0f p%.0f f%d s%d sn%b j%b %s %s place%b%n", t, bot.x, bot.z, bot.y, bot.onGround, a.yaw, a.pitch, a.forward, a.strafe, a.sneak, a.jump, a.state, a.technique, a.place != null);
            bot.yaw = a.yaw;
            bot.pitch = a.pitch;
            if (a.place != null) {
                PlaceRequest r = a.place;
                double ex = bot.x, ey = bot.y + 1.62, ez = bot.z;
                int nx = r.tx() - r.cx(), ny = r.ty() - r.cy(), nz = r.tz() - r.cz();
                double cx = r.cx() + 0.5 + nx * 0.5, cy = r.cy() + 0.5 + ny * 0.5, cz = r.cz() + 0.5 + nz * 0.5;
                if ((ex - cx) * nx + (ey - cy) * ny + (ez - cz) * nz > 0 && w.solid(r.cx(), r.cy(), r.cz()) && r.ty() == -1) w.floor(r.tx(), r.tz());
            }
            move(w, bot, a);
        }
        return new Run(false, false, 1500, bigTurns, sb.edgeGuard().saves);
    }

    /** A sword fight on a one block bridge over the void, with the target stepping back and forth along it. */
    static Run bridgeFight(BotProfile p, long seed) {
        Random rng = new Random(seed);
        World w = new World();
        for (int x = -20; x <= 40; x++) w.floor(x, 0);
        Body bot = new Body(8.5, 0.5), target = new Body(11.5, 0.5);
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "netherite_axe", "golden_apple*8");
        StyleBrain sb = new StyleBrain(p, null);
        double charge = 1;
        for (long t = 0; t < 600; t++) {
            if (bot.y < -2) return new Run(false, true, t, 0, sb.edgeGuard().saves);
            // The target walks a few blocks back and forth along the bridge.
            target.vx = (t / 25 % 2 == 0 ? 1 : -1) * 0.12 + (rng.nextDouble() - 0.5) * 0.05;
            target.x = Math.max(-15, Math.min(35, target.x + target.vx));
            sb.setWorld(w);
            BotAction a = sb.step(obs(t, bot, target, l, charge, "netherite_sword"));
            bot.yaw = a.yaw;
            bot.pitch = a.pitch;
            charge = a.attack ? 0 : Math.min(1, charge + 0.08);
            // A hit now and then knocks it back along the bridge, the way a sword hit would.
            if (rng.nextDouble() < 0.02 && Math.abs(bot.x - target.x) < 3.5) {
                bot.vx += Math.signum(bot.x - target.x) * 0.4;
                bot.vy = 0.36;
                bot.onGround = false;
            }
            if (System.getProperty("traceFight") != null && s0(seed)) System.out.printf("t%d x%.2f z%.3f y%.2f vz%.3f g%b yaw%.0f f%d s%d sn%b j%b %s %s%n", t, bot.x, bot.z, bot.y, bot.vz, bot.onGround, a.yaw, a.forward, a.strafe, a.sneak, a.jump, a.state, a.technique);
            move(w, bot, a);
        }
        return new Run(true, false, 600, 0, sb.edgeGuard().saves);
    }

    static boolean s0(long seed) { return seed == 0; }

    public static void main(String[] args) {
        // Bridging out to someone off at an angle, with the human-like layer on, as in game.
        int reached = 0, fell = 0, turns = 0, oldTurns = 0;
        long ticks = 0;
        for (long s = 0; s < 10; s++) {
            BotProfile p = new BotProfile();
            Run r = diagonal(p, s);
            if (r.reached()) reached++;
            if (r.fell()) fell++;
            turns += r.bigTurns();
            ticks += r.ticks();
        }
        System.out.printf("  bridging out at an angle: reached %d of 10, fell %d, %d sharp turns in all, %.0f ticks on average%n", reached, fell, turns, ticks / 10.0);
        check(fell == 0, "never falls bridging out at an angle");
        check(reached >= 9, "gets there");
        check(turns <= 30, "bridges straight with a turn or two, not a staircase that spins it round every block");

        // A sword fight on a one block bridge: with the edge guard, and without it.
        int falls = 0, oldFalls = 0, saves = 0;
        for (long s = 0; s < 30; s++) {
            BotProfile p = new BotProfile();
            Run r = bridgeFight(p, s);
            if (r.fell()) falls++;
            saves += r.saves();
            BotProfile off = new BotProfile();
            off.edgeGuard = 0;
            if (bridgeFight(off, s).fell()) oldFalls++;
        }
        System.out.printf("  sword fight on a one block bridge: fell in %d of 30 with the edge guard, %d of 30 without, %d moves stopped%n", falls, oldFalls, saves);
        check(falls == 0, "never walks off a one block bridge while fighting");
        check(oldFalls > falls, "the edge guard is what keeps it on");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
