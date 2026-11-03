package dev.reality.fightbot.core;

import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.world.Explosions;
import dev.reality.fightbot.core.world.WorldView;
import dev.reality.fightbot.core.world.WorldView.Kind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Crystal and anchor duel simulator on a flat grass floor. The world checks every click with its own copy
 * of the placement rules: reach, a solid clicked block, free space, and no entity in the way. Crystals
 * explode at power 6 when hit, anchors at power 5 when used with anything but glowstone, outside the
 * Nether. Blast damage uses the same estimator the bot uses, with hit protection after each hit. Players
 * wear netherite with Protection IV and carry totems. Knockback, block damage from blasts, and healing
 * other than a popped totem's are left out. Clicks can be delayed to test waiting on a slow server.
 */
public final class CrystalSim {
    public interface Brain {
        BotAction act(Observation o, WorldView view);
    }

    public static int placeDelay = 0;
    public static String dimension = "overworld";
    public static String difficulty = "normal";
    /** Bedrock floor, as in many crystal arenas, instead of grass. */
    public static boolean bedrockFloor = false;

    static final class Body {
        double x, y = 1, z, vx, vz;
        float yaw, pitch;
        double hp = 20, absorption;
        int totems = 4, invuln;
        double lastHurt;
        int regenTicks;
        int selected;
        final String[] hotbar = new String[9];
        final int[] counts = new int[9];
        int attackTicker = 100;
        String offhand = "totem_of_undying";

        Loadout loadout() {
            Map<String, Integer> totals = new HashMap<>();
            String[] h = new String[9];
            for (int i = 0; i < 9; i++) {
                h[i] = hotbar[i] == null ? "air" : hotbar[i];
                if (hotbar[i] != null) totals.merge(hotbar[i], counts[i], Integer::sum);
            }
            if (!offhand.equals("air")) totals.merge(offhand, 1, Integer::sum);
            if (totems > 0) totals.merge("totem_of_undying", totems, Integer::sum);
            return new Loadout(h, counts, totals, "netherite_chestplate", offhand);
        }

        String held() {
            return hotbar[selected] == null ? "air" : hotbar[selected];
        }

        void use(int slot) {
            if (--counts[slot] <= 0) {
                hotbar[slot] = null;
                counts[slot] = 0;
            }
        }

        double charge() {
            double cd = held().endsWith("_sword") ? 12.5 : 5;
            return Math.min(1, (attackTicker + 0.5) / cd);
        }
    }

    public static final class Result {
        public int duels, wins, losses, draws;
        public int botTotemsPopped, enemyTotemsPopped, crystalsBroken, clicks, failedClicks, anchorsSetOff;
        public double dealt, taken;
        public final Map<String, Integer> failures = new TreeMap<>();

        public double winRate() {
            return duels == 0 ? 0 : (double) wins / duels;
        }

        @Override
        public String toString() {
            return String.format("won %d of %d, lost %d, drew %d. Totems popped: enemy %d, bot %d. Crystals hit %d, anchors set off %d, clicks %d, failed %d. Damage dealt %.0f, taken %.0f",
                    wins, duels, losses, draws, enemyTotemsPopped, botTotemsPopped, crystalsBroken, anchorsSetOff, clicks, failedClicks, dealt, taken);
        }
    }

    // ---------------------------------------------------------------- the world

    private final Map<Long, Kind> blocks = new HashMap<>();
    private final Map<Long, Integer> anchors = new HashMap<>();
    private final List<WorldView.Crystal> crystals = new ArrayList<>();
    private int nextId = 1000;
    private final Random rng;
    private Body a, b;

    private record Delayed(Body who, PlaceRequest r, long at) {
    }

    private final List<Delayed> delayed = new ArrayList<>();

    public CrystalSim(long seed) {
        this.rng = new Random(seed);
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 1_000_000) << 40) ^ ((long) (y + 2048) << 20) ^ (z + 1_000_000);
    }

    Kind kindAt(int x, int y, int z) {
        Kind k = blocks.get(key(x, y, z));
        if (k != null) return k;
        return y <= 0 ? (bedrockFloor ? Kind.BEDROCK : Kind.SOLID) : Kind.AIR;
    }

    /** Puts a block in the world, for setting up test arenas. */
    public void set(int x, int y, int z, Kind k) {
        if (k == Kind.AIR && y > 0) blocks.remove(key(x, y, z));
        else blocks.put(key(x, y, z), k);
        if (k != Kind.ANCHOR) anchors.remove(key(x, y, z));
        else anchors.putIfAbsent(key(x, y, z), 0);
    }

    boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
        for (Body p : new Body[] {a, b}) {
            if (p == null) continue;
            if (p.x + 0.3 > x0 && p.x - 0.3 < x1 && p.y + 1.8 > y0 && p.y < y1 && p.z + 0.3 > z0 && p.z - 0.3 < z1) return true;
        }
        for (WorldView.Crystal c : crystals) {
            if (c.x() + 1 > x0 && c.x() - 1 < x1 && c.y() + 2 > y0 && c.y() < y1 && c.z() + 1 > z0 && c.z() - 1 < z1) return true;
        }
        return false;
    }

    /** The world from one player's side, so "self" armor means that player. */
    public WorldView view() {
        return viewFor(true);
    }

    private WorldView viewFor(boolean forA) {
        CrystalSim sim = this;
        return new WorldView() {
            public Kind kind(int x, int y, int z) {
                return sim.kindAt(x, y, z);
            }

            public int anchorCharge(int x, int y, int z) {
                Integer c = anchors.get(key(x, y, z));
                return sim.kindAt(x, y, z) == Kind.ANCHOR && c != null ? c : -1;
            }

            public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
                return sim.entityIn(x0, y0, z0, x1, y1, z1);
            }

            public List<Crystal> crystals() {
                return new ArrayList<>(crystals);
            }

            public String dimension() {
                return dimension;
            }

            public String difficulty() {
                return difficulty;
            }

            public Armor armor(boolean self) {
                return new Armor(20, 12, 16);
            }
        };
    }

    // ---------------------------------------------------------------- brains

    public static Brain bot(BotProfile p) {
        StyleBrain brain = new StyleBrain(p, null);
        ActionValidator v = new ActionValidator(p);
        return (o, w) -> {
            brain.setWorld(w);
            return v.check(o, brain.step(o));
        };
    }

    /** Walks in and swings a sword at full charge. */
    public static Brain swordsman() {
        return (o, w) -> {
            BotAction x = new BotAction();
            x.yaw = o.yawToOpponent();
            x.pitch = o.pitchToOpponent();
            x.hotbar = 0;
            x.forward = o.distance() > 2.2 ? 1 : 0;
            x.sprint = true;
            x.attack = o.charge() >= 0.95 && o.distance() < 3.0;
            return x;
        };
    }

    // ---------------------------------------------------------------- duels

    public Result run(int duels, java.util.function.Function<Long, Brain> botA, java.util.function.Function<Long, Brain> botB, boolean bCrystals) {
        Result r = new Result();
        for (int d = 0; d < duels; d++) {
            long seed = rng.nextLong();
            duel(botA.apply(seed), botB.apply(seed + 1), bCrystals, r);
        }
        return r;
    }

    private void kit(Body p, boolean crystals) {
        p.hotbar[0] = "netherite_sword";
        p.counts[0] = 1;
        if (crystals) {
            p.hotbar[1] = "obsidian"; p.counts[1] = 64;
            p.hotbar[2] = "end_crystal"; p.counts[2] = 64;
            p.hotbar[3] = "respawn_anchor"; p.counts[3] = 16;
            p.hotbar[4] = "glowstone"; p.counts[4] = 64;
        }
    }

    private void duel(Brain ba, Brain bb, boolean bCrystals, Result r) {
        blocks.clear();
        anchors.clear();
        crystals.clear();
        delayed.clear();
        a = new Body();
        b = new Body();
        a.x = 0.5; a.z = 0.5;
        b.x = 0.5 + rng.nextInt(3) - 1; b.z = 6.5 + rng.nextInt(3);
        kit(a, true);
        kit(b, bCrystals);
        r.duels++;
        WorldView va = viewFor(true), vb = viewFor(false);
        long t = 0;
        for (; t < 2400; t++) {
            Observation oa = observe(t, a, b), ob = observe(t, b, a);
            BotAction xa = ba.act(oa, va), xb = bb.act(ob, vb);
            apply(t, a, b, xa, r, true);
            apply(t, b, a, xb, r, false);
            for (Delayed d : new ArrayList<>(delayed)) {
                if (d.at() <= t) {
                    delayed.remove(d);
                    click(t, d.who(), d.r(), r, d.who() == a);
                }
            }
            move(a);
            move(b);
            for (Body p : new Body[] {a, b}) {
                if (p.invuln > 0) p.invuln--;
                if (p.regenTicks > 0) {
                    p.regenTicks--;
                    p.hp = Math.min(20, p.hp + 0.08);
                }
                p.attackTicker++;
            }
            if (dead(a) || dead(b)) break;
        }
        if (dead(b) && !dead(a)) r.wins++;
        else if (dead(a) && !dead(b)) r.losses++;
        else r.draws++;
    }

    private static boolean dead(Body p) {
        return p.hp <= 0;
    }

    private Observation observe(long t, Body me, Body them) {
        Loadout l = me.loadout();
        int weapon = l.slot(Loadout::isSword);
        return new Observation(t, true, me.x, me.y, me.z, me.vx, 0, me.vz, me.yaw, me.pitch, true, false, false,
                (float) me.hp, (float) me.absorption, me.held(), me.charge(), weapon, -1, 0,
                them.x, them.y, them.z, them.vx, 0, them.vz, them.yaw, true, false, false, false, (float) them.hp,
                (float) them.absorption, them.held(), true, 0, false, -1, -1, 0, 0, 0, them.charge(), false, 0, 0,
                l, 0, false, true, 24, 24);
    }

    private void apply(long t, Body me, Body them, BotAction x, Result r, boolean isBot) {
        me.yaw = x.yaw;
        me.pitch = x.pitch;
        if (x.hotbar >= 0) {
            if (!me.held().equals(me.hotbar[x.hotbar] == null ? "air" : me.hotbar[x.hotbar])) me.attackTicker = 0;
            me.selected = x.hotbar;
        }
        if (x.offhandTotem && me.offhand.equals("air") && me.totems > 0) {
            me.offhand = "totem_of_undying";
            me.totems--;
        }
        if (x.attackEntity >= 0) {
            WorldView.Crystal hit = null;
            for (WorldView.Crystal c : crystals) if (c.id() == x.attackEntity) hit = c;
            double ex = me.x, ey = me.y + 1.62, ez = me.z;
            if (hit != null) {
                double nx = clamp(ex, hit.x() - 1, hit.x() + 1), ny = clamp(ey, hit.y(), hit.y() + 2), nz = clamp(ez, hit.z() - 1, hit.z() + 1);
                double d = Math.sqrt((ex - nx) * (ex - nx) + (ey - ny) * (ey - ny) + (ez - nz) * (ez - nz));
                if (d <= 3.0 + 0.5) {
                    crystals.remove(hit);
                    if (isBot) r.crystalsBroken++;
                    explode(hit.x(), hit.y(), hit.z(), Explosions.CRYSTAL_POWER, r);
                }
            }
        }
        if (x.place != null) {
            if (isBot) r.clicks++;
            if (placeDelay > 0) delayed.add(new Delayed(me, x.place, t + placeDelay));
            else click(t, me, x.place, r, isBot);
        }
        if (x.attack && them.hp > 0) {
            double charge = me.charge();
            me.attackTicker = 0;
            double dx = them.x - me.x, dz = them.z - me.z;
            if (Math.hypot(dx, dz) <= 3.3 && them.invuln == 0) {
                double base = me.held().endsWith("_sword") ? 8 : 1;
                double dmg = base * (0.2 + charge * charge * 0.8);
                damage(them, afterArmor(dmg), r, them == a);
            }
        }
        double f = x.forward * 0.98, s = x.strafe * 0.98;
        double speed = 0.1 * (x.sprint && x.forward > 0 ? 1.3 : 1);
        double yr = Math.toRadians(me.yaw);
        me.vx += (s * Math.cos(yr) - f * Math.sin(yr)) * speed;
        me.vz += (f * Math.cos(yr) + s * Math.sin(yr)) * speed;
    }

    private void click(long t, Body me, PlaceRequest req, Result r, boolean isBot) {
        double ex = me.x, ey = me.y + 1.62, ez = me.z;
        double reach = Math.sqrt((ex - req.hx()) * (ex - req.hx()) + (ey - req.hy()) * (ey - req.hy()) + (ez - req.hz()) * (ez - req.hz()));
        String item = me.hotbar[req.slot()] == null ? "air" : me.hotbar[req.slot()];
        me.selected = req.slot();
        String fail = null;
        Kind clicked = kindAt(req.cx(), req.cy(), req.cz());
        if (reach > 4.5 + 0.5) fail = "out of reach";
        else if (clicked == Kind.AIR || clicked == Kind.REPLACEABLE) fail = "clicked air";
        else {
            switch (req.what()) {
                case "obsidian", "anchor" -> {
                    String want = req.what().equals("obsidian") ? "obsidian" : "respawn_anchor";
                    Kind k = kindAt(req.tx(), req.ty(), req.tz());
                    if (!item.equals(want)) fail = "wrong item";
                    else if (k != Kind.AIR && k != Kind.REPLACEABLE) fail = "occupied";
                    else if (entityIn(req.tx(), req.ty(), req.tz(), req.tx() + 1, req.ty() + 1, req.tz() + 1)) fail = "entity in the way";
                    else {
                        set(req.tx(), req.ty(), req.tz(), req.what().equals("obsidian") ? Kind.OBSIDIAN : Kind.ANCHOR);
                        me.use(req.slot());
                    }
                }
                case "crystal" -> {
                    if (!item.equals("end_crystal")) fail = "wrong item";
                    else if (clicked != Kind.OBSIDIAN && clicked != Kind.BEDROCK) fail = "not a crystal base";
                    else if (kindAt(req.cx(), req.cy() + 1, req.cz()) != Kind.AIR) fail = "blocked above";
                    else if (entityIn(req.cx(), req.cy() + 1, req.cz(), req.cx() + 1, req.cy() + 3, req.cz() + 1)) fail = "entity in the way";
                    else {
                        crystals.add(new WorldView.Crystal(nextId++, req.cx() + 0.5, req.cy() + 1, req.cz() + 0.5));
                        me.use(req.slot());
                    }
                }
                case "charge" -> {
                    Integer c = anchors.get(key(req.cx(), req.cy(), req.cz()));
                    if (clicked != Kind.ANCHOR) fail = "not an anchor";
                    else if (!item.equals("glowstone")) fail = "wrong item";
                    else if (c != null && c >= 4) fail = "full";
                    else {
                        anchors.put(key(req.cx(), req.cy(), req.cz()), (c == null ? 0 : c) + 1);
                        me.use(req.slot());
                    }
                }
                case "detonate" -> {
                    Integer c = anchors.get(key(req.cx(), req.cy(), req.cz()));
                    if (clicked != Kind.ANCHOR) fail = "not an anchor";
                    else if (item.equals("glowstone")) fail = "glowstone charges instead";
                    else if (c == null || c <= 0) fail = "not charged";
                    else if (dimension.equals("the_nether")) fail = "sets spawn in the Nether";
                    else {
                        set(req.cx(), req.cy(), req.cz(), Kind.AIR);
                        anchors.remove(key(req.cx(), req.cy(), req.cz()));
                        if (isBot) r.anchorsSetOff++;
                        explode(req.cx() + 0.5, req.cy() + 0.5, req.cz() + 0.5, Explosions.ANCHOR_POWER, r);
                    }
                }
                default -> fail = "unknown";
            }
        }
        if (fail != null && isBot) {
            r.failedClicks++;
            r.failures.merge(req.what() + ": " + fail, 1, Integer::sum);
        }
    }

    private void explode(double cx, double cy, double cz, float power, Result r) {
        WorldView w = viewFor(true);
        for (Body p : new Body[] {a, b}) {
            double d = Explosions.toPlayer(w, cx, cy, cz, power, p.x, p.y, p.z, true);
            if (d > 0) damage(p, d, r, p == a);
        }
        // Other crystals caught in the blast are destroyed too.
        crystals.removeIf(c -> Math.abs(c.x() - cx) < 2.5 && Math.abs(c.y() - cy) < 2.5 && Math.abs(c.z() - cz) < 2.5);
    }

    private void damage(Body p, double d, Result r, boolean isBot) {
        if (p.invuln > 0) {
            if (d <= p.lastHurt) return;
            double extra = d - p.lastHurt;
            p.lastHurt = d;
            d = extra;
        } else {
            p.invuln = 10;
            p.lastHurt = d;
        }
        if (isBot) r.taken += d; else r.dealt += d;
        double fromAbs = Math.min(p.absorption, d);
        p.absorption -= fromAbs;
        p.hp -= d - fromAbs;
        if (p.hp <= 0 && p.offhand.equals("totem_of_undying")) {
            p.hp = 1;
            p.absorption = 8;
            p.regenTicks = 200;
            p.offhand = "air";
            if (isBot) r.botTotemsPopped++; else r.enemyTotemsPopped++;
        }
    }

    private void move(Body p) {
        double nx = p.x + p.vx, nz = p.z + p.vz;
        // Blocks at body height stop movement.
        if (!solidColumn((int) Math.floor(nx), (int) Math.floor(p.z), p.y)) p.x = nx;
        else p.vx = 0;
        if (!solidColumn((int) Math.floor(p.x), (int) Math.floor(nz), p.y)) p.z = nz;
        else p.vz = 0;
        p.vx *= 0.546;
        p.vz *= 0.546;
    }

    private boolean solidColumn(int x, int z, double y) {
        int fy = (int) Math.floor(y);
        return viewFor(true).solid(x, fy, z) || viewFor(true).solid(x, fy + 1, z);
    }

    static double afterArmor(double d) {
        double cut = Math.min(20, Math.max(20 / 5.0, 20 - 4 * d / (12 + 8))) / 25;
        return d * (1 - cut) * (1 - 16 / 25.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static void main(String[] args) {
        BotProfile p = new BotProfile();
        System.out.println("vs swordsman: " + new CrystalSim(1).run(100, s -> bot(p), s -> swordsman(), false));
        System.out.println("vs itself: " + new CrystalSim(2).run(100, s -> bot(p), s -> bot(p), true));
    }
}
