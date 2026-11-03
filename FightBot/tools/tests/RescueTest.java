import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.world.WorldView;

import java.util.*;

/** Saving itself from falls, catching its own pearl, and getting unstuck inside a house. */
public class RescueTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    /** Only the blocks set, with the void under everything. */
    static final class Sky implements WorldView {
        final Set<String> solid = new HashSet<>();
        boolean floor;

        void set(int x, int y, int z) {
            solid.add(x + "," + y + "," + z);
        }

        public Kind kind(int x, int y, int z) {
            return solid.contains(x + "," + y + "," + z) || (floor && y < 0) ? Kind.SOLID : Kind.AIR;
        }

        public int anchorCharge(int x, int y, int z) {
            return -1;
        }

        public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
            return false;
        }

        public List<Crystal> crystals() {
            return List.of();
        }

        public String dimension() {
            return "overworld";
        }

        public String difficulty() {
            return "normal";
        }

        public Armor armor(boolean self) {
            return new Armor(20, 12, 16);
        }
    }

    static Observation fall(double x, double y, double z, double vy, double fallDist, float health, double ox, double oz, Loadout l, boolean windReady) {
        return new Observation(1000, true, x, y, z, 0, vy, 0, 0, 0, false, false, false, health, 0,
                "netherite_sword", 1.0, 0, -1, 0, ox, 0, oz, 0, 0, 0, 0, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 1.0, false, 0, 0, l, fallDist, false, windReady, 10, 10);
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        Loadout pearls = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "ender_pearl*16");
        Loadout wind = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "wind_charge*16", "ender_pearl*16");
        Loadout water = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "water_bucket");

        // Knocked off a platform over the void.
        Sky sky = new Sky();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) sky.set(x, -1, z);
        Mobility m = new Mobility(p);
        m.setWorld(sky);
        BotAction a = BotAction.idle(0, 0);
        Observation off = fall(7.5, -4, 0.5, -0.6, 4, 20, 0.5, 0.5, pearls, true);
        boolean saved = m.rescue(off, a);
        check(saved && "rescue_pearl".equals(a.technique), "pearls back to the platform when knocked into the void");
        float[] aim = saved ? Mobility.pearlAim(sky, 7.5, -4 + 1.62, 0.5, 0.5, 0, 0.5, 0, -0.6, 0) : null;
        System.out.println("  void save: aim pitch " + a.pitch + ", lands in " + (aim == null ? "?" : (int) aim[2]) + " ticks");

        // Falling onto the ground from high up, at low health, with each tool.
        Sky ground = new Sky();
        ground.floor = true;
        Mobility mw = new Mobility(p);
        mw.setWorld(ground);
        BotAction w1 = BotAction.idle(0, 0);
        check(!mw.rescue(fall(20.5, 12, 0.5, -1.2, 10, 6, 0.5, 0.5, wind, true), w1), "waits until close to the ground to use the wind charge");
        BotAction w2 = BotAction.idle(0, 0);
        check(mw.rescue(fall(20.5, 4, 0.5, -1.5, 18, 6, 0.5, 0.5, wind, true), w2) && "rescue_wind".equals(w2.technique) && w2.pitch == 90,
                "wind charge at its feet 4 blocks above the ground");
        Mobility mb = new Mobility(p);
        mb.setWorld(ground);
        BotAction b1 = BotAction.idle(0, 0);
        check(mb.rescue(fall(20.5, 2.5, 0.5, -1.5, 18, 6, 0.5, 0.5, water, true), b1) && "rescue_water".equals(b1.technique),
                "water bucket just before landing with no wind charge");
        Mobility mp = new Mobility(p);
        mp.setWorld(ground);
        BotAction p1 = BotAction.idle(0, 0);
        check(mp.rescue(fall(20.5, 8, 0.5, -1.2, 14, 6, 0.5, 0.5, pearls, true), p1) && "rescue_pearl_down".equals(p1.technique),
                "pearl straight down with only pearls");
        BotAction safe = BotAction.idle(0, 0);
        Mobility ms = new Mobility(p);
        ms.setWorld(ground);
        check(!ms.rescue(fall(20.5, 3, 0.5, -0.5, 3, 20, 0.5, 0.5, wind, true), safe), "leaves a small fall alone at full health");
        BotAction mace = BotAction.idle(0, 0);
        Mobility mm = new Mobility(p);
        mm.setWorld(ground);
        check(!mm.rescue(fall(0.8, 10, 0.5, -1.2, 20, 6, 0.5, 0.5, wind, true), mace), "leaves a mace drop onto the target alone");

        // Catching its own pearl.
        int[] plan = Mobility.ownCatchPlan(0);
        System.out.println("  own pearl catch: launch " + (plan == null ? "-" : plan[0]) + " ticks after the throw, catch at " + (plan == null ? "-" : plan[1]) + ", " + (plan == null ? 0 : plan[2] / 10.0) + " blocks higher");
        check(plan != null && plan[2] >= 8, "a launch time exists where the pearl hits it on the way up");
        Mobility mo = new Mobility(p);
        mo.setWorld(ground);
        Loadout wm = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "mace", "wind_charge*64", "ender_pearl*16");
        BotAction t0 = BotAction.idle(0, 0);
        Observation stand = new Observation(500, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, 3.5, 0, 0.5, 0, 0, 0, 0, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.2, false, 0, 0, wm, 0, false, true, 30, 30);
        boolean threw = mo.ownCatch(stand, t0, true);
        check(threw && t0.pitch == -90 && "ender_pearl".equals(t0.useItem), "throws the pearl straight up next to the target");
        long launch = -1;
        for (long t = 501; t < 600 && launch < 0; t++) {
            Observation st = new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, false, 20, 0,
                    "netherite_sword", 1.0, 0, -1, 0, 3.5, 0, 0.5, 0, 0, 0, 0, true, false, false, false, 20, 0,
                    "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.2, false, 0, 0, wm, 0, false, true, 30, 30);
            BotAction x = BotAction.idle(0, 0);
            boolean holding = mo.holdingForCatch(t);
            if (mo.ownCatch(st, x, true) && "own_catch_launch".equals(x.technique)) launch = t;
            else if (!holding) break;
        }
        check(plan != null && launch == 500 + plan[0], "wind launches on the planned tick");
        Sky roof = new Sky();
        roof.floor = true;
        roof.set(0, 10, 0);
        Mobility mr = new Mobility(p);
        mr.setWorld(roof);
        check(!mr.ownCatch(stand, BotAction.idle(0, 0), true), "skips it with a roof overhead");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
