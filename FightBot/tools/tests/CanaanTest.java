import dev.reality.fightbot.core.*;
import java.util.*;

/** Checks for what went wrong in the crystal fights against Canaan. */
public class CanaanTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    static Loadout crystalKit(boolean pearls) {
        List<String> bar = new ArrayList<>(List.of("netherite_sword", "obsidian*64", "end_crystal*64", "golden_apple*16", "enchanted_golden_apple*4"));
        if (pearls) bar.add("ender_pearl*16");
        return Loadout.of("netherite_chestplate", "totem_of_undying", bar.toArray(new String[0]));
    }

    static Observation obs(long t, float hp, float abs, double dist, String oHeld, Loadout l) {
        return new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, false, hp, abs,
                "netherite_sword", 1.0, 0, 3, 16, 0.5, 0, 0.5 + dist, 0, 0, 0, 180, true, false, false, false, 20, 0,
                oHeld, true, 0, false, -1, 4, 4, 16, 4, 0.5, false, 0, 0, l, 0, false, true, 30, 30);
    }

    static StyleBrain brain(VoidTest.World w) {
        BotProfile p = new BotProfile();
        p.reactionTicks = 0;
        p.navigate = 0;
        StyleBrain sb = new StyleBrain(p, null);
        sb.setWorld(w);
        return sb;
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;
        VoidTest.World w = new VoidTest.World();
        for (int x = -90; x <= 90; x++) for (int z = -20; z <= 90; z++) w.floor(x, z);

        // Six hearts, Canaan four blocks off with crystals: eat, and finish the apple.
        StyleBrain sb = brain(w);
        long ateAt = -1;
        boolean broke = false;
        for (int t = 1; t <= 80; t++) {
            BotAction a = sb.step(obs(t, 12, 0, 4, "end_crystal", crystalKit(false)));
            boolean eatingNow = a.use && (a.hotbar == 3 || a.hotbar == 4 || a.hotbar < 0);
            if (ateAt < 0 && a.use && (a.hotbar == 3 || a.hotbar == 4)) ateAt = t;
            else if (ateAt >= 0 && t < ateAt + 32 && !eatingNow) broke = true;
        }
        check(ateAt >= 0 && !broke, "eats a whole apple in the crystal style when low");

        // Two hearts with pearls: pearl out of blast range first.
        sb = brain(w);
        boolean pearled = false;
        for (int t = 1; t <= 40 && !pearled; t++) {
            BotAction a = sb.step(obs(t, 4, 0, 3, "end_crystal", crystalKit(true)));
            pearled = a.technique.equals("pearl_away_from_blasts");
        }
        check(pearled, "pearls away from Canaan at two hearts");

        // Full health with absorption: no eating, it fights.
        sb = brain(w);
        boolean ate = false;
        for (int t = 1; t <= 40; t++) {
            BotAction a = sb.step(obs(t, 20, 8, 4, "end_crystal", crystalKit(false)));
            ate |= "crystal_eat".equals(a.technique);
        }
        check(!ate, "does not eat at full health");

        // Seventy blocks away: a pearl most of the way, not a long walk.
        BotProfile p = new BotProfile();
        p.reactionTicks = 0;
        Mobility m = new Mobility(p);
        m.setWorld(w);
        BotAction a = BotAction.idle(0, 0);
        boolean threw = m.pearlIn(obs(1, 20, 0, 70, "golden_apple", crystalKit(true)), a, false);
        check(threw && "pearl_closer".equals(a.technique), "throws a pearl most of the way to someone out of pearl range");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
