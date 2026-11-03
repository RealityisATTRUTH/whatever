import dev.reality.fightbot.core.*;

/** Pearling in from range, leading a running target, and the wind mace pearl catch timing. */
public class PearlTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static Observation obs(long t, double x, double z, double ox, double oz, double ovx, double ovz, Loadout l, boolean windReady) {
        return new Observation(t, true, x, 0, z, 0, 0, 0, 0, 0, true, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, ox, 0, oz, ovx, 0, ovz, 0, true, true, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 1.0, false, 0, 0, l, 0, false, windReady, 10, 10);
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        TowerTest.Grid w = new TowerTest.Grid();
        BotProfile p = new BotProfile();
        Loadout sword = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "ender_pearl*16");
        Loadout wind = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "mace", "wind_charge*64", "ender_pearl*16");

        Mobility m = new Mobility(p);
        m.setWorld(w);
        BotAction near = BotAction.idle(0, 0);
        check(!m.pearlIn(obs(0, 0.5, 0.5, 6.5, 0.5, 0, 0, sword, true), near, false), "no pearl when the target is close");

        BotAction far = BotAction.idle(0, 0);
        boolean threw = m.pearlIn(obs(1, 0.5, 0.5, 24.5, 0.5, 0, 0, sword, true), far, false);
        check(threw && "ender_pearl".equals(far.useItem) && "pearl_in".equals(far.technique), "pearls in at a target 24 blocks away");
        BotAction again = BotAction.idle(0, 0);
        check(!m.pearlIn(obs(5, 0.5, 0.5, 24.5, 0.5, 0, 0, sword, true), again, false), "waits out the pearl cooldown");

        // Running sideways at sprint speed: the lead aim should land where they will be.
        Observation run = obs(0, 0.5, 0.5, 20.5, 0.5, 0, 0.28, sword, true);
        float[] lead = Mobility.pearlLead(w, run);
        float[] still = Mobility.pearlAim(w, 0.5, 1.62, 0.5, 20.5, 0, 0.5);
        System.out.println("  running target: aim yaw " + String.format("%.1f", lead[0]) + " vs standing " + String.format("%.1f", still[0]) + ", flight " + (int) lead[2] + " ticks");
        double predictedZ = 0.5 + 0.28 * lead[2];
        int hit = dev.reality.fightbot.core.Mobility.pearlAim(w, 0.5, 1.62, 0.5, 20.5, 0, predictedZ) != null ? 1 : 0;
        check(Math.abs(lead[0] - still[0]) > 3 && hit == 1, "leads a running target");

        // Wind mace: throw, wait, then wind charge a few ticks before the pearl lands.
        Mobility wm = new Mobility(p);
        wm.setWorld(w);
        BotAction throwA = BotAction.idle(0, 0);
        boolean c = wm.pearlIn(obs(100, 0.5, 0.5, 12.5, 0.5, 0, 0, wind, true), throwA, true);
        float[] aim = Mobility.pearlAim(w, 0.5, 1.62, 0.5, 12.5, 0, 0.5);
        check(c && "pearl_catch_throw".equals(throwA.technique), "wind mace throws a pearl to catch from 12 blocks");
        long windTick = -1;
        for (long t = 101; t < 140 && windTick < 0; t++) {
            BotAction a = BotAction.idle(0, 0);
            wm.pearlIn(obs(t, 0.5, 0.5, 12.5, 0.5, 0, 0, wind, true), a, true);
            if ("pearl_catch_wind".equals(a.technique)) {
                windTick = t;
                check(a.useOnce && a.pitch == 90 && a.jump, "wind charge at its own feet with a jump");
            }
        }
        long lands = 100 + (long) aim[2];
        System.out.println("  pearl lands at tick " + lands + ", wind charge at tick " + windTick);
        check(windTick > 100 && lands - windTick == p.pearlCatchLead, "wind charge goes off " + p.pearlCatchLead + " ticks before the pearl lands");

        Loadout hurt = wind;
        BotAction low = BotAction.idle(0, 0);
        Observation weak = new Observation(300, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, false, 6, 0,
                "netherite_sword", 1.0, 0, -1, 0, 20.5, 0, 0.5, 0, 0, 0, 0, true, true, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 1.0, false, 0, 0, hurt, 0, false, true, 10, 10);
        check(!new Mobility(p).pearlIn(weak, low, false), "no pearl at low health, since a pearl costs health");

        // A target inside a small hut with a roof: no throw lands on them, so it pearls to open ground beside it.
        TowerTest.Grid hut = new TowerTest.Grid();
        for (int x = 19; x <= 21; x++) for (int z = -1; z <= 1; z++) {
            hut.set(x, 2, z);
            if (x != 20 || z != 0) { hut.set(x, 0, z); hut.set(x, 1, z); }
        }
        Mobility hm = new Mobility(p);
        hm.setWorld(hut);
        BotAction ha = BotAction.idle(0, 0);
        boolean threwNear = hm.pearlIn(obs(1, 0.5, 0.5, 20.5, 0.5, 0, 0, sword, true), ha, false);
        check(Mobility.pearlAim(hut, 0.5, 1.62, 0.5, 20.5, 0, 0.5) == null, "no throw lands on a target inside a roofed hut");
        check(threwNear && "pearl_in".equals(ha.technique), "pearls to open ground beside the hut instead");

        // The elytra style pearls in too once the target is far enough that a pearl beats flying there.
        BotProfile ep = new BotProfile();
        ep.usePotions = 0;
        StyleBrain eb = new StyleBrain(ep, null);
        eb.setWorld(w);
        Loadout ely = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "netherite_axe", "mace", "firework_rocket*64",
                "elytra", "ender_pearl*16", "golden_apple*64");
        boolean elyPearl = false, elyNear = false;
        for (long t = 0; t < 120; t++) {
            BotAction x = eb.step(obs(t, 0.5, 0.5, 30.5, 0.5, 0, 0, ely, true));
            if ("pearl_in".equals(x.technique)) elyPearl = true;
        }
        StyleBrain eb2 = new StyleBrain(ep, null);
        eb2.setWorld(w);
        for (long t = 0; t < 120; t++) {
            BotAction x = eb2.step(obs(t, 0.5, 0.5, 16.5, 0.5, 0, 0, ely, true));
            if ("pearl_in".equals(x.technique)) elyNear = true;
        }
        check(elyPearl, "the elytra style pearls at a target 30 blocks away");
        check(!elyNear, "but flies to one 16 blocks away, which is quicker than a pearl");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
