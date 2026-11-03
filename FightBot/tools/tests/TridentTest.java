import dev.reality.fightbot.core.*;

/** Tridents: picking the style, the Loyalty throw, the Riptide dash only when wet, the sword up close, and riptide while swimming. */
public class TridentTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static Observation obs(long t, double oz, Loadout l, String held, boolean using) {
        return new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, using, 20, 0,
                held, 1.0, 0, -1, 0, 0.5, 0, oz, 0, 0, 0, 0, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, l, 0, false, true, 10, 10);
    }

    static Loadout kit(boolean wet) {
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "trident", "trident:riptide", "golden_apple*64");
        l.wet = wet;
        return l;
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        check(Style.pick(kit(false), p).use() == Style.TRIDENT, "the trident kit picks the trident style");
        System.out.println("  " + Style.pick(kit(false), p).describe());

        // Dry: throw the Loyalty trident after a half second of charge.
        TridentPolicy t = new TridentPolicy(p);
        BotAction out = t.step(obs(0, 15.5, kit(false), "netherite_sword", false));
        check(out.hotbar == 1, "takes the Loyalty trident out from range when dry");
        int threw = -1;
        for (int i = 1; i <= 20 && threw < 0; i++) {
            BotAction a = t.step(obs(i, 15.5, kit(false), "trident", i > 1));
            if ("trident_throw".equals(a.technique)) {
                threw = i;
                check(!a.use && a.exactAim && a.pitch < 0, "lets go with exact aim, a little above the target for the drop");
            }
        }
        System.out.println("  thrown after " + threw + " ticks");
        check(threw >= 10 && threw <= 13, "charges for half a second before throwing");

        // Wet: the Riptide dash.
        TridentPolicy w = new TridentPolicy(p);
        BotAction rOut = w.step(obs(0, 10.5, kit(true), "netherite_sword", false));
        check(rOut.hotbar == 2 && "riptide_out".equals(rOut.technique), "takes the Riptide trident out in rain or water");
        int dashed = -1;
        for (int i = 1; i <= 20 && dashed < 0; i++) {
            BotAction a = w.step(obs(i, 10.5, kit(true), "trident:riptide", i > 1));
            if ("riptide_dash".equals(a.technique)) dashed = i;
        }
        check(dashed >= 10 && dashed <= 13, "dashes at the target after half a second of charge");
        TridentPolicy dry = new TridentPolicy(p);
        boolean dryDash = false;
        for (int i = 0; i <= 20; i++) {
            BotAction a = dry.step(obs(i, 10.5, kit(false), i == 0 ? "netherite_sword" : "trident:riptide", i > 1));
            if (a.technique.startsWith("riptide")) dryDash = true;
        }
        check(!dryDash, "never tries Riptide when dry, where it does nothing");

        // Close: the sword.
        TridentPolicy c = new TridentPolicy(p);
        c.step(obs(0, 3.0, kit(false), "trident", false));
        check(c.stateName().equals("TRIDENT_MELEE"), "fights with the sword up close");

        // The validator lets the Riptide trident charge only when wet.
        ActionValidator v = new ActionValidator(p);
        BotAction wetUse = BotAction.idle(0, 0);
        wetUse.use = true;
        v.check(obs(5, 10.5, kit(true), "trident:riptide", false), wetUse);
        BotAction dryUse = BotAction.idle(0, 0);
        dryUse.use = true;
        Loadout dryNoFood = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "trident", "trident:riptide");
        new ActionValidator(p).check(obs(5, 10.5, dryNoFood, "trident:riptide", false), dryUse);
        check(wetUse.use && !dryUse.use, "the validator allows Riptide only in water or rain");

        // Swimming with a Riptide trident: it dashes instead of swimming.
        BotProfile sp = new BotProfile();
        sp.usePotions = 0;
        StyleBrain sb = new StyleBrain(sp, null);
        Loadout swim = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "trident", "trident:riptide", "golden_apple*64");
        swim.inWater = true;
        swim.underWater = true;
        swim.wet = true;
        Observation under = new Observation(10, true, 0.5, 4, 0.5, 0, 0, 0, 0, 0, false, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, 0.5, 4, 12.5, 0, 0, 0, 0, false, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, swim, 0, false, true, 2, 2);
        BotAction s = sb.step(under);
        check(s.technique.startsWith("riptide"), "uses Riptide to cross water toward the target");

        // Strafes while drawing a throw, instead of standing still for half a second.
        TridentPolicy st = new TridentPolicy(p);
        st.step(obs(0, 15.5, kit(false), "netherite_sword", false));
        boolean strafed = false;
        for (int i = 1; i <= 8; i++) if (st.step(obs(i, 15.5, kit(false), "trident", i > 1)).strafe != 0) strafed = true;
        check(strafed, "moves side to side while drawing a throw");

        // A second Loyalty trident goes out while the first is still coming back.
        Loadout second = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "air", "trident:riptide",
                "golden_apple*64", "air", "air", "air", "air", "trident");
        BotAction next = new TridentPolicy(p).step(obs(30, 15.5, second, "netherite_sword", false));
        check(next.hotbar == 8 && "trident_out".equals(next.technique), "throws the second Loyalty trident while the first is out");

        // Riptide leads someone running across: it aims where they will be, not where they are.
        Observation running = new Observation(5, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, true, 20, 0,
                "trident:riptide", 1.0, 0, -1, 0, 0.5, 0, 12.5, 0.28, 0, 0, 0, true, true, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, kit(true), 0, false, true, 10, 10);
        BotAction lead = new TridentPolicy(p).riptide(running);
        // Straight at them is yaw 0. Running toward +x means leading to negative yaw.
        check(lead != null && lead.yaw < -2, "leads a Riptide dash at someone running across");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
