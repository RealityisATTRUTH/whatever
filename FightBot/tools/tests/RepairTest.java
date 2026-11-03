import dev.reality.fightbot.core.*;

/** Mending repairs: bottles o' enchanting when gear is worn, only when safe, the elytra put on to repair it, and pearling away. */
public class RepairTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static Observation obs(long t, double oz, Loadout l) {
        return new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, 0.5, 0, oz, 0, 0, 0, 0, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, l, 0, false, true, 10, 10);
    }

    static Loadout kit(double armor, double elytra, boolean wearingElytra) {
        Loadout l = Loadout.of(wearingElytra ? "elytra" : "netherite_chestplate", "totem_of_undying", "netherite_sword", "experience_bottle*64",
                wearingElytra ? "netherite_chestplate" : "elytra", "golden_apple*64", "ender_pearl*16");
        l.armorWorst = armor;
        l.elytraLeft = elytra;
        return l;
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();

        Repair r = new Repair(p);
        check(!r.step(obs(0, 30.5, kit(0.5, 1, false)), BotAction.idle(0, 0)), "leaves armor with half its durability alone");
        BotAction a = BotAction.idle(0, 0);
        boolean fixing = r.step(obs(1, 30.5, kit(0.3, 1, false)), a);
        check(fixing && a.useOnce && a.pitch == 90 && a.hotbar == 1 && a.forward == 0, "throws bottles at its feet, standing still, once armor is under 35%");
        int thrown = 0;
        for (long t = 2; t < 22; t++) {
            BotAction x = BotAction.idle(0, 0);
            r.step(obs(t, 30.5, kit(0.6, 1, false)), x);
            if (x.useOnce) thrown++;
        }
        check(thrown == 10, "keeps throwing every other tick until it is nearly full (" + thrown + " in 20 ticks)");
        check(!r.step(obs(30, 30.5, kit(0.92, 1, false)), BotAction.idle(0, 0)), "stops at 90%");

        Repair near = new Repair(p);
        check(!near.step(obs(0, 6.5, kit(0.3, 1, false)), BotAction.idle(0, 0)), "does not stop to repair with the opponent 6 blocks away");
        BotAction crit = BotAction.idle(0, 0);
        check(near.step(obs(1, 6.5, kit(0.1, 1, false)), crit) && crit.useOnce, "repairs anyway when a piece is about to break");

        Repair ely = new Repair(p);
        BotAction on = BotAction.idle(0, 0);
        ely.step(obs(0, 30.5, kit(1, 0.2, false)), on);
        check("elytra".equals(on.equip), "puts a worn elytra on so it takes the repair");
        BotAction throwing = BotAction.idle(0, 0);
        ely.step(obs(1, 30.5, kit(1, 0.2, true)), throwing);
        check(throwing.useOnce, "then throws bottles");
        BotAction back = BotAction.idle(0, 0);
        ely.step(obs(5, 30.5, kit(1, 0.95, true)), back);
        check("chestplate".equals(back.equip), "and puts the chestplate back on when done");

        // In the brain: with the opponent close it pearls away to repair.
        TowerTest.Grid w = new TowerTest.Grid();
        StyleBrain sb = new StyleBrain(p, null);
        sb.setWorld(w);
        String seen = "";
        for (long t = 100; t < 140 && seen.isEmpty(); t++) {
            BotAction x = sb.step(obs(t, 6.5, kit(0.25, 1, false)));
            if (x.technique.startsWith("pearl_away")) seen = x.technique;
        }
        check(seen.equals("pearl_away_to_repair"), "pearls away from a close opponent to repair");

        ActionValidator v = new ActionValidator(p);
        BotAction bottle = BotAction.idle(0, 90);
        bottle.useOnce = true;
        bottle.hotbar = 1;
        v.check(obs(5, 30.5, kit(0.3, 1, false)), bottle);
        check(bottle.useOnce, "the validator lets a bottle be thrown");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
