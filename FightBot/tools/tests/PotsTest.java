import dev.reality.fightbot.core.*;

import java.util.*;

/** Pots only: heal at 12, back off so the opponent is not in the splash, double pot when about to die, keep the hotbar full. */
public class PotsTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    /** A pots kit: sword, pearls, speed, and healing pots in the hotbar, with more in the main inventory. */
    static Loadout pots(int hotbarHeals, int totalHeals) {
        String[] h = new String[9];
        int[] c = new int[9];
        h[0] = "netherite_sword";
        c[0] = 1;
        h[1] = "ender_pearl";
        c[1] = 16;
        h[2] = "splash_potion:strong_swiftness";
        c[2] = 1;
        for (int i = 0; i < hotbarHeals && 3 + i < 9; i++) {
            h[3 + i] = "splash_potion:strong_healing";
            c[3 + i] = 1;
        }
        Map<String, Integer> totals = new HashMap<>();
        totals.put("netherite_sword", 1);
        totals.put("ender_pearl", 16);
        totals.put("splash_potion:strong_swiftness", 2);
        totals.put("splash_potion:strong_healing", totalHeals);
        Loadout l = new Loadout(h, c, totals, "netherite_chestplate", "air");
        l.effects = Map.of("speed", 2000);
        return l;
    }

    static Observation obs(long t, double oz, float health, Loadout l) {
        return obs(t, oz, health, l, 0);
    }

    static Observation obs(long t, double oz, float health, Loadout l, float yaw) {
        return new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, yaw, 0, true, false, false, health, 0,
                "netherite_sword", 1.0, 0, -1, 0, 0.5, 0, oz, 0, 0, 0, 0, true, true, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, l, 0, false, true, 10, 10);
    }

    static StyleBrain brain() {
        BotProfile p = new BotProfile();
        StyleBrain sb = new StyleBrain(p, null);
        sb.startRound();
        return sb;
    }

    /** Runs past the start-of-round pot up, at full health with the target far away. */
    static void settle(StyleBrain sb, Loadout l) {
        for (long t = 0; t < 80; t++) sb.step(obs(t, 30.5, 20, l));
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        Loadout kit = pots(6, 30);
        check(Style.pick(kit, new BotProfile()).use() == Style.SWORD, "the pots kit fights with the sword style");

        StyleBrain a = brain();
        settle(a, kit);
        BotAction at13 = a.step(obs(100, 9.5, 13, kit));
        BotAction at12 = a.step(obs(101, 9.5, 12, kit));
        check(!at13.technique.startsWith("pot_heal") && "pot_heal".equals(at12.technique) && at12.pitch == 90, "pots at 12 health, not above, so no heal is wasted");

        StyleBrain b = brain();
        settle(b, kit);
        // The bot turns over a few ticks, so the turn is fed back in each tick the way the game does.
        float yaw = 0;
        BotAction close = null;
        boolean allRetreat = true;
        for (long t = 200; t < 205; t++) {
            close = b.step(obs(t, 3.0, 11, kit, yaw));
            allRetreat &= "pot_retreat".equals(close.technique);
            yaw = close.yaw;
        }
        double dirZ = Math.cos(Math.toRadians(close.yaw));
        System.out.println("  backing off: heading " + String.format("%.2f", dirZ) + " along the line to the opponent");
        check(allRetreat && dirZ < -0.8 && close.forward > 0, "backs away before potting when the opponent would share the splash");
        BotAction far = b.step(obs(201, 5.5, 11, kit));
        check("pot_heal".equals(far.technique), "pots once out of splash range");

        StyleBrain c = brain();
        settle(c, kit);
        BotAction crit1 = c.step(obs(300, 2.0, 4, kit));
        BotAction crit2 = c.step(obs(301, 2.0, 4, kit));
        check(crit1.technique.startsWith("pot") && crit1.useOnce && "pot_double".equals(crit2.technique), "about to die: pots right away, twice in a row, opponent close or not");

        StyleBrain d = brain();
        Loadout low = pots(1, 20);
        settle(d, low);
        BotAction refill = d.step(obs(400, 9.5, 20, low));
        check("splash_potion:healing".equals(refill.restock), "refills healing pots into the hotbar between exchanges");

        // Few pots: the old rule stays, potting at 8.
        StyleBrain e = brain();
        Loadout few = pots(2, 2);
        settle(e, few);
        BotAction at11 = e.step(obs(500, 9.5, 11, few));
        check(!at11.technique.startsWith("pot_heal"), "with only a couple of pots it saves them for 8 health");

        // Buffs: only when 10 seconds or less are left, and standing still for the throw.
        Loadout buffKit = pots(6, 30);
        buffKit.effects = java.util.Map.of("speed", 260);
        StyleBrain f = brain();
        settle(f, buffKit);
        BotAction early2 = f.step(obs(600, 20.5, 20, buffKit));
        check(!early2.technique.startsWith("pot_speed"), "leaves speed alone with 13 seconds left");
        Loadout low2 = pots(6, 30);
        low2.effects = java.util.Map.of("speed", 190);
        BotAction rebuff = f.step(obs(601, 20.5, 20, low2));
        check("pot_speed".equals(rebuff.technique) && rebuff.forward == 0 && !rebuff.sprint, "throws speed again at under 10 seconds, standing still so the splash lands on it");
        Loadout endless = pots(6, 30);
        endless.effects = java.util.Map.of("speed", -1);
        StyleBrain g = brain();
        settle(g, endless);
        boolean wasted = false;
        for (long t = 700; t < 760; t++) if (g.step(obs(t, 20.5, 20, endless)).technique.startsWith("pot_speed")) wasted = true;
        check(!wasted, "never throws a buff it already has for good");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
