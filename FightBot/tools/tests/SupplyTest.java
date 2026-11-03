import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.world.WorldView;

import java.util.*;

/** Pearling away to restock, taking the water back after a fire, and ender chest restocks. */
public class SupplyTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static final class Flat implements WorldView {
        final Set<String> water = new HashSet<>();

        public Kind kind(int x, int y, int z) {
            return y < 0 ? Kind.SOLID : Kind.AIR;
        }

        public boolean water(int x, int y, int z) {
            return water.contains(x + "," + y + "," + z);
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

    static Observation obs(long t, double x, double z, double ox, double oz, Loadout l) {
        return new Observation(t, true, x, 0, z, 0, 0, 0, 0, 0, true, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, ox, 0, oz, 0, 0, 0, 0, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 1.0, false, 0, 0, l, 0, false, true, 10, 10);
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        Flat w = new Flat();

        // Pearl away: target 4 blocks to the east, so it should go west and land at least 20 from them.
        Mobility m = new Mobility(p);
        m.setWorld(w);
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "ender_pearl*16", "red_shulker_box");
        BotAction a = BotAction.idle(0, 0);
        boolean threw = m.pearlAway(obs(10, 0.5, 0.5, 4.5, 0.5, l), a);
        double yr = Math.toRadians(a.yaw);
        double dirX = -Math.sin(yr);
        System.out.println("  pearl away: yaw " + a.yaw + ", pitch " + a.pitch + ", heading x " + String.format("%.2f", dirX));
        check(threw && "pearl_away_to_restock".equals(a.technique) && dirX < -0.5, "pearls away from a close target to restock");

        // The brain does it on its own when it needs a restock and the target is close.
        BotProfile bp = new BotProfile();
        bp.usePotions = 0;
        StyleBrain sb = new StyleBrain(bp, null);
        sb.setWorld(w);
        Loadout start = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "ender_pearl*16", "red_shulker_box",
                "golden_apple*64", "totem_of_undying*6");
        sb.step(obs(1, 0.5, 0.5, 4.5, 0.5, start));
        Loadout low = Loadout.of("netherite_chestplate", "air", "netherite_sword", "ender_pearl*16", "red_shulker_box", "golden_apple*64");
        String seen = "";
        for (long t = 300; t < 320 && seen.isEmpty(); t++) {
            BotAction x = sb.step(obs(t, 0.5, 0.5, 4.5, 0.5, low));
            if (x.technique.startsWith("pearl_away")) seen = x.technique;
        }
        check(!seen.isEmpty(), "the brain pearls away when out of totems with the target close");

        // Ender chest instead of a shulker box.
        Loadout ec = Loadout.of("netherite_chestplate", "air", "netherite_sword", "golden_apple*64", "ender_chest");
        ec.enderStock = 27;
        Loadout ecEmpty = Loadout.of("netherite_chestplate", "air", "netherite_sword", "golden_apple*64", "ender_chest");
        StyleBrain sb2 = new StyleBrain(bp, null);
        sb2.setWorld(w);
        sb2.step(obs(1, 0.5, 0.5, 30.5, 0.5, start));
        boolean restocked = false;
        for (long t = 300; t < 320 && !restocked; t++) restocked = sb2.step(obs(t, 0.5, 0.5, 30.5, 0.5, ec)).shulkerRestock;
        check(restocked, "restocks from a filled ender chest when it has no shulker box");
        StyleBrain sb3 = new StyleBrain(bp, null);
        sb3.setWorld(w);
        sb3.step(obs(1, 0.5, 0.5, 30.5, 0.5, start));
        boolean emptyTry = false;
        for (long t = 300; t < 320 && !emptyTry; t++) emptyTry = sb3.step(obs(t, 0.5, 0.5, 30.5, 0.5, ecEmpty)).shulkerRestock;
        check(!emptyTry, "skips an empty ender chest");

        // Water: put it down on fire, then take it back even after stepping off the spot.
        Mobility f = new Mobility(p);
        f.setWorld(w);
        Loadout burning = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "water_bucket");
        burning.onFire = true;
        BotAction put = BotAction.idle(0, 0);
        f.fire(obs(100, 0.5, 0.5, 9.5, 0.5, burning), put);
        check("water_bucket".equals(put.useItem), "water down when burning");
        w.water.add("0,0,0");
        Loadout bucket = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "bucket");
        BotAction moved = BotAction.idle(0, 0);
        boolean took = f.fire(obs(105, 1.6, 0.5, 9.5, 0.5, bucket), moved);
        check(took && "bucket".equals(moved.useItem) && moved.pitch > 30, "takes the water back after stepping a block away, aimed at it");
        BotAction again = BotAction.idle(0, 0);
        boolean retry = f.fire(obs(106, 1.6, 0.5, 9.5, 0.5, bucket), again);
        check(retry && "bucket".equals(again.useItem), "keeps trying while the water is still there");
        w.water.clear();
        BotAction done = BotAction.idle(0, 0);
        check(!f.fire(obs(107, 1.6, 0.5, 9.5, 0.5, bucket), done), "stops once the water is gone");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
