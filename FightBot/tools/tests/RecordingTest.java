import dev.reality.fightbot.core.*;
import java.util.*;

/** Checks for the problems found in the recorded fights against meowwbu. */
public class RecordingTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    /** Bot at the origin facing south, opponent 2.5 blocks south holding a sword with a shield in the offhand. */
    static Observation duel(long t, String held, double charge, int axeSlot, boolean oBlocking, boolean oUsing, float yaw, Loadout l) {
        return new Observation(t, true, 0, 64, 0, 0, 0, 0, yaw, 0, true, true, false, 20, 0,
                held, charge, 0, -1, 0, 0, 64, 2.5, 0, 0, 0, 180, true, false, oBlocking, oUsing, 20, 0,
                "netherite_sword", true, 0, false, axeSlot, -1, 0, 0, 0, 0.5, true, 0, 0, l, 0, false, true, 10, 10);
    }

    /** Bot at a height above flat ground at y 0, low on health, opponent 2.5 blocks away. */
    static Observation potObs(long t, double y, boolean ground, float hp, Loadout l) {
        return new Observation(t, true, 0.5, y, 0.5, 0, ground ? 0 : -0.1, 0, 0, 0, ground, false, false, hp, 0,
                "netherite_sword", 1.0, 0, -1, 0, 0.5, 0, 3.0, 0, 0, 0, 180, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, l, ground ? 0 : 0.6, false, true, 10, 10);
    }

    /** What a sword kit with a trident does over 80 ticks against someone 12 blocks off, wet or dry. */
    static String tridentTech(boolean wet, boolean riptide, boolean inHotbar) {
        String trident = riptide ? "trident:riptide" : "trident";
        Map<String, Integer> totals = new HashMap<>();
        totals.put("netherite_sword", 1);
        totals.put(trident, 1);
        String[] bar = {"netherite_sword", inHotbar ? trident : "air", "air", "air", "air", "air", "air", "air", "air"};
        Loadout l = new Loadout(bar, new int[] {1, inHotbar ? 1 : 0, 0, 0, 0, 0, 0, 0, 0}, totals, "netherite_chestplate", "totem_of_undying");
        l.wet = wet;
        VoidTest.World ground = new VoidTest.World();
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) ground.floor(x, z);
        BotProfile tp = new BotProfile();
        tp.reactionTicks = 0;
        tp.navigate = 0;
        StyleBrain sb = new StyleBrain(tp, null);
        sb.setWorld(ground);
        for (int t = 1; t <= 80; t++) {
            Observation o = new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, false, 20, 0,
                    "netherite_sword", 1.0, 0, -1, 0, 0.5, 0, 12.5, 0, 0, 0, 180, true, false, false, false, 20, 0,
                    "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, l, 0, false, true, 10, 10);
            BotAction a = sb.step(o);
            if (a.technique.startsWith("riptide") || a.technique.startsWith("trident")) return a.technique;
            if (a.restock != null && a.restock.startsWith("trident")) return "restock:" + a.restock;
        }
        return "";
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        p.reactionTicks = 0;
        p.critJumpChance = 0;
        p.aimJitter = 0;
        p.sTapChance = 0;
        p.wTapChance = 0;
        p.axeCritChance = 0;
        Loadout sw = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "netherite_axe");
        Loadout swordOnly = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword");

        // Shield flicks: the sword swing into a raised shield was blocked and punished 43 times in 47.
        BotAction intoShield = new SwordPolicy(p).step(duel(1, "netherite_sword", 1.0, -1, true, true, 0, swordOnly));
        check(!intoShield.attack, "keeps its sword charge instead of swinging into a raised shield");
        BotAction open = new SwordPolicy(p).step(duel(1, "netherite_sword", 1.0, -1, false, false, 0, swordOnly));
        check(open.attack, "still swings when the shield is down");

        SwordPolicy sp = new SwordPolicy(p);
        BotAction rising = sp.step(duel(1, "netherite_sword", 1.0, 1, false, true, 0, sw));
        check(rising.hotbar == 1 && !rising.attack, "takes the axe out as soon as the shield starts to rise");
        BotAction breakIt = sp.step(duel(2, "netherite_axe", 0.1, 1, true, true, 0, sw));
        check(breakIt.attack && breakIt.technique.equals("shield_break"), "hits the raised shield with the axe");

        // Bot duels: a two tick shield flick kept the other bot on its slower axe for two seconds.
        SwordPolicy flick = new SwordPolicy(p);
        flick.step(duel(1, "netherite_sword", 1.0, 1, false, true, 0, sw));
        int backAt = -1;
        for (int t = 2; t <= 40 && backAt < 0; t++) {
            BotAction f = flick.step(duel(t, "netherite_axe", 0.3, 1, false, false, 0, sw));
            if (f.hotbar == 0) backAt = t;
        }
        System.out.println("  back on the sword at tick " + backAt);
        check(backAt > 0 && backAt <= 14, "goes back to the sword soon after a shield flick that never came up");

        // Turning: it came round at 20 degrees a tick in the recording.
        BotProfile tp = new BotProfile();
        tp.reactionTicks = 0;
        tp.aimJitter = 0;
        BotAction turn = new SwordPolicy(tp).step(duel(1, "netherite_sword", 0.2, -1, false, false, 120, swordOnly));
        new MotionSmoother(tp).apply(duel(1, "netherite_sword", 0.2, -1, false, false, 120, swordOnly), turn);
        double turned = Angles.diff(turn.yaw, 120);
        System.out.println("  turned " + Math.round(turned) + " degrees in one tick");
        check(turned >= 40, "turns at least 40 degrees a tick in a close sword fight");

        // A weapon left in the backpack: it fought rounds with a potion in hand.
        Map<String, Integer> totals = new HashMap<>();
        totals.put("netherite_sword", 1);
        totals.put("splash_potion:strong_healing", 8);
        String[] hotbar = {"splash_potion:strong_healing", "air", "air", "air", "air", "air", "air", "air", "air"};
        int[] counts = {8, 0, 0, 0, 0, 0, 0, 0, 0};
        Loadout bag = new Loadout(hotbar, counts, totals, "netherite_chestplate", "totem_of_undying");
        check("netherite_sword".equals(bag.ownedId(Loadout::isSword)), "finds a sword outside the hotbar");
        StyleBrain sb = new StyleBrain(p, null);
        boolean pulled = false;
        for (int t = 1; t <= 10 && !pulled; t++) {
            BotAction a = sb.step(new Observation(t, true, 0, 64, 0, 0, 0, 0, 0, 0, true, false, false, 20, 0,
                    "splash_potion:strong_healing", 1.0, -1, -1, 0, 0, 64, 9, 0, 0, 0, 180, true, false, false, false, 20, 0,
                    "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, bag, 0, false, true, 10, 10));
            pulled = "netherite_sword".equals(a.restock);
        }
        check(pulled, "moves the sword into the hotbar when none is there");

        // Potions: it stopped potting once the hotbar ran dry, and never potted in the air.
        VoidTest.World ground = new VoidTest.World();
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) ground.floor(x, z);
        Map<String, Integer> potTotals = new HashMap<>();
        potTotals.put("netherite_sword", 1);
        potTotals.put("splash_potion:strong_healing", 3);
        String[] dryBar = {"netherite_sword", "air", "air", "air", "air", "air", "air", "air", "air"};
        Loadout dry = new Loadout(dryBar, new int[] {1, 0, 0, 0, 0, 0, 0, 0, 0}, potTotals, "netherite_chestplate", "totem_of_undying");
        StyleBrain pb = new StyleBrain(p, null);
        pb.setWorld(ground);
        BotAction refill = null;
        for (int t = 1; t <= 70 && (refill == null || !"splash_potion:healing".equals(refill.restock)); t++) {
            refill = pb.step(potObs(t, 0, true, 6, dry));
        }
        check(refill != null && "splash_potion:healing".equals(refill.restock), "brings a healing potion down from the backpack when the hotbar has none, with the opponent close");

        String[] wetBar = {"netherite_sword", "splash_potion:strong_healing", "air", "air", "air", "air", "air", "air", "air"};
        Loadout wet = new Loadout(wetBar, new int[] {1, 1, 0, 0, 0, 0, 0, 0, 0}, potTotals, "netherite_chestplate", "totem_of_undying");
        StyleBrain ab = new StyleBrain(p, null);
        ab.setWorld(ground);
        BotAction airPot = null;
        for (int t = 1; t <= 70 && (airPot == null || !airPot.useOnce); t++) airPot = ab.step(potObs(t, 0.6, false, 6, wet));
        check(airPot != null && airPot.useOnce && airPot.hotbar == 1, "splashes a healing potion while jumping, close above the ground");

        // Apples and potions: at potion health it potted, cancelling the apple it had just started.
        String[] mixBar = {"netherite_sword", "splash_potion:strong_healing", "golden_apple", "air", "air", "air", "air", "air", "air"};
        Map<String, Integer> mixTotals = new HashMap<>();
        mixTotals.put("netherite_sword", 1);
        mixTotals.put("splash_potion:strong_healing", 2);
        mixTotals.put("golden_apple", 8);
        Loadout mix = new Loadout(mixBar, new int[] {1, 1, 8, 0, 0, 0, 0, 0, 0}, mixTotals, "netherite_chestplate", "totem_of_undying");
        // The bot stands still in this check, which after two seconds would hand the tick to routing, so routing is off.
        BotProfile still = new BotProfile();
        still.reactionTicks = 0;
        still.navigate = 0;
        StyleBrain mb = new StyleBrain(still, null);
        mb.setWorld(ground);
        // It starts an apple at 8.6, then takes a hit to 7.5. The apple has to finish, not get cancelled by a potion.
        long eatAt = -1;
        boolean cancelled = false;
        for (int t = 1; t <= 120; t++) {
            float hpNow = eatAt < 0 ? 8.6f : 7.5f;
            Observation mo = new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, eatAt >= 0, hpNow, 0,
                    eatAt >= 0 ? "golden_apple" : "netherite_sword", 1.0, 0, 2, 8, 0.5, 0, 9.5, 0, 0, 0, 180, true, false, false, false, 20, 0,
                    "netherite_sword", true, 0, false, -1, -1, 0, 8, 0, 0.5, false, 0, 0, mix, 0, false, true, 10, 10);
            BotAction ma = mb.step(mo);
            if (eatAt < 0 && ma.use && ma.hotbar == 2) eatAt = t;
            else if (eatAt >= 0 && t < eatAt + 32 && ma.useOnce && ma.hotbar == 1) {
                if (!cancelled) System.out.println("  cancelled at tick " + t + " by " + ma.technique + ", style " + mb.choice().use() + ", state " + mb.stateName());
                cancelled = true;
            }
        }
        System.out.println("  apple started at tick " + eatAt + (cancelled ? ", cancelled by a potion" : ""));
        check(eatAt >= 0 && !cancelled, "finishes an apple it started instead of cancelling it for a potion");

        // Tridents in a sword kit: it never pulled one out, even in the rain.
        check(tridentTech(true, true, true).startsWith("riptide"), "a sword kit dashes with a Riptide trident in the rain");
        check(tridentTech(false, false, true).startsWith("trident"), "a sword kit throws a Loyalty trident at someone out of reach");
        check(tridentTech(true, true, false).equals("restock:trident:riptide"), "brings a Riptide trident down from the backpack when it starts raining");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
