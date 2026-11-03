import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.world.WorldView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Towering, pearling up, catching itself on a tower, water on fire, and the shulker restock check. */
public class TowerTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    /** Ground below y 0, plus placed blocks. Two player boxes count as entities. */
    static final class Grid implements WorldView {
        final Set<String> solid = new HashSet<>();
        Body bot, target;

        public Kind kind(int x, int y, int z) {
            return y < 0 || solid.contains(x + "," + y + "," + z) ? Kind.SOLID : Kind.AIR;
        }

        void set(int x, int y, int z) {
            solid.add(x + "," + y + "," + z);
        }

        public int anchorCharge(int x, int y, int z) {
            return -1;
        }

        public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
            return overlaps(bot, x0, y0, z0, x1, y1, z1) || overlaps(target, x0, y0, z0, x1, y1, z1);
        }

        static boolean overlaps(Body b, double x0, double y0, double z0, double x1, double y1, double z1) {
            return b != null && b.x - 0.3 < x1 && b.x + 0.3 > x0 && b.y < y1 && b.y + 1.8 > y0 && b.z - 0.3 < z1 && b.z + 0.3 > z0;
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

    static final class Body {
        double x, y, z, vx, vy, vz;
        float yaw, pitch;
        boolean onGround = true;

        Body(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static Observation obs(long t, Body b, Body o, Loadout l) {
        return new Observation(t, true, b.x, b.y, b.z, b.vx, b.vy, b.vz, b.yaw, b.pitch, b.onGround, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, o.x, o.y, o.z, 0, 0, 0, 0, o.onGround, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 1.0, false, 0, 0, l, 0, false, true, 10, 10);
    }

    /** Moves the bot one tick the way the game roughly does: blocks first, then walking, jumping, and gravity. */
    static void step(Grid w, Body b, BotAction a, Body target, int[] placed) {
        b.yaw = a.yaw;
        b.pitch = a.pitch;
        if (a.place != null) {
            var r = a.place;
            if (w.open(r.tx(), r.ty(), r.tz()) && !Grid.overlaps(b, r.tx(), r.ty(), r.tz(), r.tx() + 1, r.ty() + 1, r.tz() + 1)) {
                w.set(r.tx(), r.ty(), r.tz());
                placed[0]++;
            }
        }
        if ("ender_pearl".equals(a.useItem) && pearlLands(w, b, a, target)) {
            b.x = target.x + 0.3;
            b.y = target.y;
            b.z = target.z;
            b.vy = 0;
            b.onGround = true;
            placed[1]++;
            return;
        }
        if (a.jump && b.onGround) b.vy = 0.42;
        if (a.forward > 0) {
            double sp = a.sprint ? 0.28 : 0.21;
            double r = Math.toRadians(a.yaw);
            double nx = b.x - Math.sin(r) * sp, nz = b.z + Math.cos(r) * sp;
            if (!blocked(w, nx, b.y, nz)) {
                b.x = nx;
                b.z = nz;
            }
        }
        b.y += b.vy;
        if (b.vy <= 0 && standing(w, b)) {
            b.y = Math.floor(b.y) + 1;
            if (standing(w, b)) b.y = Math.floor(b.y);
            b.vy = 0;
            b.onGround = true;
        } else {
            b.onGround = false;
        }
        b.vy = (b.vy - 0.08) * 0.98;
        if (b.onGround) b.vy = 0;
    }

    static boolean pearlLands(Grid w, Body b, BotAction a, Body t) {
        // The same trace the bot aims with, from the bot's eye.
        float[] aim = Mobility.pearlAim(w, b.x, b.y + 1.62, b.z, t.x, t.y, t.z);
        return aim != null && Math.abs(aim[1] - a.pitch) < 1 && Math.abs(aim[0] - a.yaw) < 1;
    }

    static boolean standing(Grid w, Body b) {
        int by = (int) Math.floor(b.y);
        for (double dx : new double[] {-0.29, 0.29}) for (double dz : new double[] {-0.29, 0.29}) {
            if (w.solid((int) Math.floor(b.x + dx), by, (int) Math.floor(b.z + dz))) return true;
        }
        return false;
    }

    static boolean blocked(Grid w, double x, double y, double z) {
        for (double dx : new double[] {-0.3, 0.3}) for (double dz : new double[] {-0.3, 0.3}) {
            for (int dy = 0; dy < 2; dy++) {
                if (w.solid((int) Math.floor(x + dx), (int) Math.floor(y + 0.01) + dy, (int) Math.floor(z + dz))) return true;
            }
        }
        return false;
    }

    /** Builds a tower of the given height at 0, 0 with the target on top. */
    static Grid tower(int h, Body target) {
        Grid w = new Grid();
        for (int y = 0; y < h; y++) w.set(0, y, 0);
        w.target = target;
        return w;
    }

    record Run(double topY, double lowestY, int blocks, int pearls, long ticks, List<String> techniques) {
    }

    static Run run(int h, Body bot, Loadout l, int maxTicks) {
        BotProfile p = new BotProfile();
        Mobility m = new Mobility(p);
        Body target = new Body(0.5, h, 0.5);
        Grid w = tower(h, target);
        w.bot = bot;
        m.setWorld(w);
        int[] placed = new int[2];
        double lowest = bot.y;
        List<String> tech = new java.util.ArrayList<>();
        long t;
        for (t = 0; t < maxTicks; t++) {
            Observation o = obs(t, bot, target, l);
            BotAction a = BotAction.idle(bot.yaw, bot.pitch);
            if (!m.tower(o, a)) {
                if (bot.y >= h - 0.01 && bot.onGround) break;
            }
            if (!a.technique.isEmpty() && (tech.isEmpty() || !tech.get(tech.size() - 1).equals(a.technique))) tech.add(a.technique);
            step(w, bot, a, target, placed);
            lowest = Math.min(lowest, bot.y);
        }
        return new Run(bot.y, lowest, placed[0], placed[1], t, tech);
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        Loadout cobble = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "cobblestone*64");
        Loadout both = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "cobblestone*64", "ender_pearl*16");

        Run r1 = run(6, new Body(3.5, 0, 0.5), cobble, 400);
        System.out.println("  pillar up 6 blocks from 3 blocks away: reached y " + String.format("%.2f", r1.topY()) + " in " + r1.ticks() + " ticks, " + r1.blocks() + " blocks, " + r1.techniques());
        check(r1.topY() >= 5.99, "builds up to a target on a 6 block tower");

        Run r2 = run(12, new Body(6.5, 0, 0.5), both, 200);
        System.out.println("  pearl up 12 blocks: reached y " + String.format("%.2f", r2.topY()) + " in " + r2.ticks() + " ticks, pearls landed " + r2.pearls() + ", " + r2.techniques());
        check(r2.pearls() >= 1 && r2.topY() >= 11.99, "throws a pearl onto a target on a tall tower");

        Body falling = new Body(1.5, 8, 0.5);
        falling.onGround = false;
        falling.vy = -0.2;
        Run r3 = run(12, falling, cobble, 600);
        System.out.println("  pearl fell short beside a 12 block tower at y 8: lowest y " + String.format("%.2f", r3.lowestY()) + ", reached y " + String.format("%.2f", r3.topY()) + ", " + r3.blocks() + " blocks, " + r3.techniques());
        check(r3.techniques().contains("catch_on_tower") && r3.lowestY() > 4, "catches itself on the side of the tower instead of falling");
        check(r3.topY() >= 11.99, "then builds up to the target");

        // No blocks and no pearls: it leaves towers alone.
        Run r4 = run(6, new Body(2.5, 0, 0.5), Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword"), 60);
        check(r4.techniques().isEmpty(), "does nothing about a tower without blocks or pearls");

        // Fire.
        BotProfile p = new BotProfile();
        Mobility m = new Mobility(p);
        Grid w = new Grid();
        m.setWorld(w);
        Body me = new Body(0.5, 0, 0.5);
        Body them = new Body(5.5, 0, 0.5);
        Loadout burning = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "water_bucket");
        burning.onFire = true;
        BotAction f = BotAction.idle(0, 0);
        boolean used = m.fire(obs(100, me, them, burning), f);
        check(used && "water_bucket".equals(f.useItem) && f.pitch == 90, "puts water down when on fire");
        Loadout after = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "bucket");
        BotAction g = BotAction.idle(0, 90);
        boolean early = m.fire(obs(101, me, them, after), g);
        BotAction h = BotAction.idle(0, 90);
        boolean pick = m.fire(obs(104, me, them, after), h);
        // This grid has no water in it, so the pickup itself is checked in SupplyTest, where the water is real.
        check(!early, "does not pick the water up the very next tick");
        Loadout resist = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "water_bucket");
        resist.onFire = true;
        resist.effects = java.util.Map.of("fire_resistance", 1000);
        check(!new Mobility(p).fire(obs(200, me, them, resist), BotAction.idle(0, 0)), "leaves the bucket alone with fire resistance");

        // Shulker restock check.
        StyleBrain sb = new StyleBrain(new BotProfile(), null);
        Loadout full = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "end_crystal*64", "obsidian*64",
                "golden_apple*64", "totem_of_undying*6", "red_shulker_box");
        sb.step(obs(1, me, them, full));
        check(!sb.needsRestock(full), "no restock with a full kit");
        Loadout lowCrystals = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "end_crystal*8", "obsidian*64",
                "golden_apple*64", "totem_of_undying*6", "red_shulker_box");
        check(sb.needsRestock(lowCrystals), "restock when crystals run low");
        Loadout lowTotems = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "end_crystal*64", "obsidian*64",
                "golden_apple*64", "red_shulker_box");
        check(sb.needsRestock(lowTotems), "restock on the last totem");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
