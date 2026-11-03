import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.nav.Navigator;
import dev.reality.fightbot.core.nav.Pathfinder;
import dev.reality.fightbot.core.world.WorldView;

import java.util.*;

/** Getting to a target past walls: around them, up steps, through doors, by mining, and by blasting. */
public class NavTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    public static class Grid implements WorldView {
        final Map<String, Kind> kinds = new HashMap<>();
        final List<Crystal> crystals = new ArrayList<>();
        Body bot, target;
        int nextId = 1;

        /** Columns with nothing under the floor: a drop into the void. */
        final Set<String> voids = new HashSet<>();
        final Set<String> lavas = new HashSet<>();

        public Kind kind(int x, int y, int z) {
            if (y < 0) return voids.contains(x + "," + z) || lavas.contains(x + "," + y + "," + z) ? Kind.AIR : Kind.SOLID;
            return kinds.getOrDefault(x + "," + y + "," + z, Kind.AIR);
        }

        public boolean lava(int x, int y, int z) {
            return lavas.contains(x + "," + y + "," + z);
        }

        /** Takes the floor out from under a column, all the way down. */
        public void hole(int x, int z) {
            voids.add(x + "," + z);
        }

        void set(int x, int y, int z, Kind k) {
            if (k == Kind.AIR) kinds.remove(x + "," + y + "," + z);
            else kinds.put(x + "," + y + "," + z, k);
        }

        public int anchorCharge(int x, int y, int z) {
            return -1;
        }

        public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
            for (Crystal c : crystals) if (c.x() - 1 < x1 && c.x() + 1 > x0 && c.y() < y1 && c.y() + 2 > y0 && c.z() - 1 < z1 && c.z() + 1 > z0) return true;
            return over(bot, x0, y0, z0, x1, y1, z1) || over(target, x0, y0, z0, x1, y1, z1);
        }

        static boolean over(Body b, double x0, double y0, double z0, double x1, double y1, double z1) {
            return b != null && b.x - 0.3 < x1 && b.x + 0.3 > x0 && b.y < y1 && b.y + 1.8 > y0 && b.z - 0.3 < z1 && b.z + 0.3 > z0;
        }

        public List<Crystal> crystals() {
            return crystals;
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

    static int windCooldown;

    static Observation obs(long t, Grid w, Body b, Body o, Loadout l) {
        boolean los = w.clearLine(b.x, b.y + 1.62, b.z, o.x, o.y + 1.62, o.z);
        return new Observation(t, true, b.x, b.y, b.z, b.vx, b.vy, b.vz, b.yaw, b.pitch, b.onGround, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, o.x, o.y, o.z, 0, 0, 0, 0, o.onGround, false, false, false, 20, 0,
                "netherite_sword", los, 0, false, -1, -1, 0, 0, 0, 1.0, false, 0, 0, l, 0, false, windCooldown == 0, 10, 10);
    }

    static boolean standing(Grid w, Body b, double y) {
        int by = (int) Math.floor(y);
        for (double dx : new double[] {-0.29, 0.29}) for (double dz : new double[] {-0.29, 0.29}) {
            if (w.solid((int) Math.floor(b.x + dx), by, (int) Math.floor(b.z + dz))) return true;
        }
        return false;
    }

    public static boolean standingAt(Grid w, double x, double z, double y) {
        Body b = new Body(x, y, z);
        return standing(w, b, y);
    }

    static boolean blocked(Grid w, double x, double y, double z) {
        for (double dx : new double[] {-0.3, 0.3}) for (double dz : new double[] {-0.3, 0.3}) {
            for (int dy = 0; dy < 2; dy++) {
                if (w.solid((int) Math.floor(x + dx), (int) Math.floor(y + 0.01) + dy, (int) Math.floor(z + dz))) return true;
            }
        }
        return false;
    }

    record Run(boolean reached, long ticks, int doors, int mined, int blasts, double nearestBlast, List<String> techniques,
               boolean inLava, int placed) {
        Run(boolean reached, long ticks, int doors, int mined, int blasts, double nearestBlast, List<String> techniques) {
            this(reached, ticks, doors, mined, blasts, nearestBlast, techniques, false, 0);
        }
    }

    static Run run(Grid w, Body bot, Body target, Loadout l, int maxTicks) {
        BotProfile p = new BotProfile();
        Navigator nav = new Navigator(p);
        nav.setWorld(w);
        w.bot = bot;
        w.target = target;
        Pathfinder.Tools tools = Pathfinder.tools(l, false);
        int[] mining = null;
        double progress = 0;
        int doors = 0, mined = 0, blasts = 0;
        double nearestBlast = 99;
        List<String> tech = new ArrayList<>();
        boolean inLava = false;
        int placed = 0;
        long t;
        for (t = 0; t < maxTicks; t++) {
            Observation o = obs(t, w, bot, target, l);
            if (o.lineOfSight() && o.distance() <= 3.5 && Math.abs(target.y - bot.y) <= 1.6) return new Run(true, t, doors, mined, blasts, nearestBlast, tech, inLava, placed);
            if (bot.y < -8) return new Run(false, t, doors, mined, blasts, nearestBlast, tech, inLava, placed);
            if (w.lava((int) Math.floor(bot.x), (int) Math.floor(bot.y), (int) Math.floor(bot.z))) inLava = true;
            BotAction a = BotAction.idle(bot.yaw, bot.pitch);
            if (!nav.step(o, a)) {
                // The fighting style: run straight at them.
                a.yaw = dev.reality.fightbot.core.place.Placer.yawTo(bot.x, bot.z, target.x, target.z);
                a.forward = 1;
                a.sprint = true;
                Navigator.autoJump(w, o, a);
                if (a.jump && a.technique.isEmpty()) a.technique = "step_up";
            }
            if (!a.technique.isEmpty() && (tech.isEmpty() || !tech.get(tech.size() - 1).equals(a.technique))) tech.add(a.technique);
            bot.yaw = a.yaw;
            bot.pitch = a.pitch;
            if (a.place != null) {
                var r = a.place;
                switch (r.what()) {
                    case "door" -> {
                        for (int dy = -1; dy <= 1; dy++) if (w.kind(r.tx(), r.ty() + dy, r.tz()) == WorldView.Kind.DOOR_CLOSED) w.set(r.tx(), r.ty() + dy, r.tz(), WorldView.Kind.DOOR_OPEN);
                        doors++;
                    }
                    case "obsidian" -> w.set(r.tx(), r.ty(), r.tz(), WorldView.Kind.OBSIDIAN);
                    case "block" -> {
                        if (!Grid.over(bot, r.tx(), r.ty(), r.tz(), r.tx() + 1, r.ty() + 1, r.tz() + 1)) {
                            // Only a click on a face the eye can see would land in the game.
                            w.set(r.tx(), r.ty(), r.tz(), WorldView.Kind.SOLID);
                            w.voids.remove(r.tx() + "," + r.tz());
                            placed++;
                        }
                    }
                    case "crystal" -> w.crystals.add(new WorldView.Crystal(w.nextId++, r.tx() + 0.5, r.ty(), r.tz() + 0.5));
                    default -> { }
                }
            }
            if (a.attackEntity >= 0) {
                for (Iterator<WorldView.Crystal> it = w.crystals.iterator(); it.hasNext(); ) {
                    WorldView.Crystal c = it.next();
                    if (c.id() != a.attackEntity) continue;
                    it.remove();
                    blasts++;
                    nearestBlast = Math.min(nearestBlast, Math.sqrt(Math.pow(bot.x - c.x(), 2) + Math.pow(bot.y + 1 - c.y(), 2) + Math.pow(bot.z - c.z(), 2)));
                    for (int x = (int) c.x() - 3; x <= c.x() + 3; x++) for (int y = (int) c.y() - 3; y <= c.y() + 3; y++) for (int z = (int) c.z() - 3; z <= c.z() + 3; z++) {
                        double d = Math.sqrt(Math.pow(x + 0.5 - c.x(), 2) + Math.pow(y + 0.5 - c.y(), 2) + Math.pow(z + 0.5 - c.z(), 2));
                        if (y >= 0 && d <= 2.2 && w.blastResistance(x, y, z) < 9) w.set(x, y, z, WorldView.Kind.AIR);
                    }
                }
            }
            if (a.mine != null) {
                if (mining == null || !Arrays.equals(mining, a.mine)) {
                    mining = a.mine;
                    progress = 0;
                }
                progress++;
                if (progress >= Pathfinder.mineTicks(w, tools, a.mine[0], a.mine[1], a.mine[2]) - 1) {
                    w.set(a.mine[0], a.mine[1], a.mine[2], WorldView.Kind.AIR);
                    mined++;
                    mining = null;
                }
            } else {
                mining = null;
            }
            if (windCooldown > 0) windCooldown--;
            if (a.useOnce && a.pitch > 60 && windCooldown == 0 && l.has("wind_charge")) {
                windCooldown = 10;
                // The burst under its feet throws it up, as the mace simulator models it.
                bot.vy = 1.1;
                bot.onGround = false;
                tech.add("launched");
            }
            double ox = bot.x, oz = bot.z;
            if (a.jump && bot.onGround && bot.vy <= 0) bot.vy = 0.42;
            if (a.forward != 0 || a.strafe != 0) {
                double sp = a.sneak ? 0.065 : a.sprint && a.forward > 0 ? 0.28 : 0.21;
                double r = Math.toRadians(a.yaw);
                double len = Math.hypot(a.forward, a.strafe);
                double f = a.forward / len, st = a.strafe / len;
                double nx = bot.x + (st * Math.cos(r) - f * Math.sin(r)) * sp, nz = bot.z + (f * Math.cos(r) + st * Math.sin(r)) * sp;
                // Sneaking on the ground never steps off an edge.
                boolean edgeX = a.sneak && bot.onGround && !standingAt(w, nx, bot.z, bot.y - 1e-3);
                if (!blocked(w, nx, bot.y, bot.z) && !edgeX) bot.x = nx;
                boolean edgeZ = a.sneak && bot.onGround && !standingAt(w, bot.x, nz, bot.y - 1e-3);
                if (!blocked(w, bot.x, bot.y, nz) && !edgeZ) bot.z = nz;
            }
            bot.vx = bot.x - ox;
            bot.vz = bot.z - oz;
            bot.y += bot.vy;
            if (bot.vy <= 0 && standing(w, bot, bot.y - 1e-3)) {
                bot.y = Math.floor(bot.y - 1e-3) + 1;
                bot.vy = 0;
                bot.onGround = true;
            } else {
                bot.onGround = false;
                bot.vy = (bot.vy - 0.08) * 0.98;
            }
        }
        return new Run(false, t, doors, mined, blasts, nearestBlast, tech, inLava, placed);
    }

    /** A 5 by 5 stone house from -2 to 2, three blocks tall with a roof. A door on the west wall if asked. */
    static Grid house(boolean door) {
        Grid w = new Grid();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++) {
            boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 3;
            if (wall) w.set(x, y, z, WorldView.Kind.SOLID);
        }
        if (door) {
            w.set(-2, 0, 0, WorldView.Kind.DOOR_CLOSED);
            w.set(-2, 1, 0, WorldView.Kind.DOOR_CLOSED);
        }
        return w;
    }

    static String show(Run r) {
        return (r.reached() ? "reached in " + r.ticks() + " ticks" : "did not reach") + ", doors " + r.doors() + ", mined " + r.mined() + ", blasts " + r.blasts() + ", " + r.techniques();
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        Loadout bare = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword");
        Loadout pick = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "netherite_pickaxe");
        Loadout boom = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "obsidian*64", "end_crystal*64");

        // A long wall with a way around the end.
        Grid wall = new Grid();
        for (int z = -6; z <= 6; z++) for (int y = 0; y < 3; y++) wall.set(0, y, z, WorldView.Kind.SOLID);
        Run r1 = run(wall, new Body(-4.5, 0, 0.5), new Body(4.5, 0, 0.5), bare, 400);
        System.out.println("  wall with a way around: " + show(r1));
        check(r1.reached() && r1.mined() == 0, "goes around a wall it cannot break");

        // One block in the way.
        Grid step = new Grid();
        for (int z = -3; z <= 3; z++) step.set(0, 0, z, WorldView.Kind.SOLID);
        Run r2 = run(step, new Body(-5.5, 0, 0.5), new Body(5.5, 0, 0.5), bare, 200);
        System.out.println("  one block step: " + show(r2));
        check(r2.reached() && r2.techniques().contains("step_up"), "jumps up a one block step instead of running into it");

        Run r3 = run(house(true), new Body(-7.5, 0, 0.5), new Body(0.5, 0, 0.5), bare, 400);
        System.out.println("  house with a door: " + show(r3));
        check(r3.reached() && r3.doors() >= 1, "opens the door to get into a house");

        Run r4 = run(house(false), new Body(-7.5, 0, 0.5), new Body(0.5, 0, 0.5), pick, 400);
        System.out.println("  house without a door, with a pickaxe: " + show(r4));
        check(r4.reached() && r4.mined() >= 1, "mines into a house with a pickaxe");

        Run r5 = run(house(false), new Body(-7.5, 0, 0.5), new Body(0.5, 0, 0.5), boom, 600);
        System.out.println("  house without a door, crystals only: " + show(r5) + ", nearest blast " + String.format("%.1f", r5.nearestBlast()));
        check(r5.reached() && r5.blasts() >= 1, "blows a hole in a house with a crystal when it has no tool");
        check(r5.nearestBlast() >= 2.0, "steps back before setting off its own crystal");

        // Same house, door present, but a pickaxe too: either way works, it just has to get in fast.
        Run r6 = run(house(true), new Body(-7.5, 0, 0.5), new Body(0.5, 0, 0.5), pick, 400);
        System.out.println("  house with a door and a pickaxe: " + show(r6));
        check(r6.reached() && r6.ticks() < 120, "gets into a house quickly with both a door and a pickaxe");

        // The target runs behind a wall and stands there.
        Grid corner = new Grid();
        for (int x = -3; x <= 3; x++) for (int y = 0; y < 3; y++) corner.set(x, y, 2, WorldView.Kind.SOLID);
        Run r7 = run(corner, new Body(0.5, 0, -2.5), new Body(0.5, 0, 5.5), bare, 300);
        System.out.println("  target behind a wall: " + show(r7));
        check(r7.reached(), "follows a target around a corner");

        // Upstairs: a two floor house with no stairs, the target on the upper floor right above.
        Grid twoFloor = house(true);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) twoFloor.set(x, 3, z, WorldView.Kind.SOLID);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            for (int y = 4; y <= 6; y++) if (Math.abs(x) == 2 || Math.abs(z) == 2) twoFloor.set(x, y, z, WorldView.Kind.SOLID);
            twoFloor.set(x, 7, z, WorldView.Kind.SOLID);
        }
        Loadout builder = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "netherite_pickaxe", "cobblestone*64");
        Run r8 = run(twoFloor, new Body(0.5, 0, -0.5), new Body(0.5, 4, 0.5), builder, 400);
        System.out.println("  target on the floor above: " + show(r8));
        check(r8.reached() && r8.mined() >= 1, "digs through the ceiling and pillars up to a target on the floor above");

        // Stuck in a one block hole next to the target.
        Grid pit = new Grid();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) pit.set(x, -1, z, WorldView.Kind.SOLID);
        Grid pitW = new Grid() {
            public WorldView.Kind kind(int x, int y, int z) {
                if (x == 0 && z == 0 && (y == -1 || y == -2)) return y == -2 ? WorldView.Kind.SOLID : WorldView.Kind.AIR;
                return super.kind(x, y, z);
            }
        };
        Run r9 = run(pitW, new Body(0.5, -1, 0.5), new Body(4.5, 0, 0.5), bare, 200);
        System.out.println("  stuck in a hole: " + show(r9));
        check(r9.reached(), "climbs out of a hole to get to the target");

        // A 4 block cliff with the target on top, and only wind charges to get up.
        Grid cliff = new Grid();
        for (int x = 0; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int y = 0; y < 4; y++) cliff.set(x, y, z, WorldView.Kind.SOLID);
        Loadout windy = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "mace", "wind_charge*32");
        windCooldown = 0;
        Run r10 = run(cliff, new Body(-2.5, 0, 0.5), new Body(3.5, 4, 0.5), windy, 300);
        System.out.println("  4 block cliff with wind charges: " + show(r10));
        check(r10.reached() && r10.techniques().contains("wind_climb"), "wind charges itself up a wall");

        // Diagonals: a straight line across open ground, not a staircase. A low wall makes it route.
        Grid open = new Grid();
        for (int z = -3; z <= 12; z++) { open.set(4, 0, z, WorldView.Kind.SOLID); open.set(4, 1, z, WorldView.Kind.SOLID); }
        open.set(4, 0, 10, WorldView.Kind.AIR); open.set(4, 1, 10, WorldView.Kind.AIR);
        Pathfinder.Path diag = Pathfinder.find(open, Pathfinder.tools(bare, false), 0, 0, 0, 10.5, 0, 10.5, 2.5, 4000);
        boolean anyDiag = false;
        int px0 = 0, pz0 = 0;
        for (Pathfinder.Step st : diag.steps()) { if (st.x() != px0 && st.z() != pz0) anyDiag = true; px0 = st.x(); pz0 = st.z(); }
        System.out.println("  route through a gap in a wall: " + diag.steps().size() + " steps");
        check(diag.complete() && anyDiag, "plans diagonal steps");
        Run rd = run(open, new Body(0.5, 0, 0.5), new Body(10.5, 0, 10.5), bare, 400);
        System.out.println("  " + show(rd));
        check(rd.reached(), "follows a route through a gap in a wall");

        // A gap too far down to drop into: sprint jump it with no blocks to bridge.
        Grid trench = new Grid();
        for (int z = -6; z <= 6; z++) { trench.hole(3, z); trench.hole(4, z); }
        Run rg = run(trench, new Body(0.5, 0, 0.5), new Body(9.5, 0, 0.5), bare, 300);
        System.out.println("  " + show(rg));
        check(rg.reached() && rg.techniques().contains("gap_jump"), "sprint jumps a two block gap");

        // Too wide to jump: bridge across, sneaking at the edge. It bridges part way and jumps the rest when that is quicker.
        Grid chasm = new Grid();
        for (int x = 3; x <= 12; x++) for (int z = -60; z <= 60; z++) chasm.hole(x, z);
        Loadout blocks = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "cobblestone*64");
        Run rb = run(chasm, new Body(0.5, 0, 0.5), new Body(16.5, 0, 0.5), blocks, 1200);
        System.out.println("  " + show(rb) + ", placed " + rb.placed());
        check(rb.reached() && rb.placed() >= 6, "bridges across a ten block drop");
        check((rb.techniques().contains("bridge_edge") || rb.techniques().contains("bridge_shift")) && rb.techniques().contains("bridge_place"), "bridges from the edge, sneaking");

        // Lava on the straight line: around it, never through.
        Grid pool = new Grid();
        for (int x = 3; x <= 6; x++) for (int z = -1; z <= 1; z++) { pool.lavas.add(x + ",-1," + z); pool.lavas.add(x + ",0," + z); }
        Pathfinder.Path lp = Pathfinder.find(pool, Pathfinder.tools(bare, false), 0, 0, 0, 9.5, 0, 0.5, 2.5, 4000);
        boolean throughLava = false;
        for (Pathfinder.Step st : lp.steps()) if (pool.lava(st.x(), st.y(), st.z()) || pool.lava(st.x(), st.y() - 1, st.z())) throughLava = true;
        check(lp.complete() && !throughLava, "plans around lava");
        Run rl = run(pool, new Body(0.5, 0, 0.5), new Body(9.5, 0, 0.5), bare, 400);
        System.out.println("  " + show(rl) + (rl.inLava() ? ", stepped in lava" : ""));
        check(rl.reached() && !rl.inLava(), "walks around a lava pool");

        // Clicking: never a face pointing away, and a corner when the middle is blocked.
        Grid pg = new Grid();
        pg.set(0, 0, 0, WorldView.Kind.SOLID);
        dev.reality.fightbot.core.place.Placer placer = new dev.reality.fightbot.core.place.Placer(4.5, 3);
        // The block at x 1 can only be placed on the east face of the block at x 0, which faces away from an eye west of it.
        var away = placer.blockSpot(pg, -2.5, 1.0, 0.5, 1, 0, 0);
        check(away == null || !away.face().equals("east") || away.cx() != 0, "never clicks a face that points away");
        // Somewhere a block hides the middle of every face but leaves a corner in sight.
        boolean cornerSeen = false;
        for (int ox = -1; ox <= 3 && !cornerSeen; ox++) for (int oy = 0; oy <= 2 && !cornerSeen; oy++) for (int oz = -1; oz <= 3 && !cornerSeen; oz++) {
            if (ox == 1 && oz == 0 || ox == 0 && oy == 0 && oz == 0) continue;
            Grid cg = new Grid();
            cg.set(0, 0, 0, WorldView.Kind.SOLID);
            cg.set(ox, oy, oz, WorldView.Kind.SOLID);
            double ex = 2.6, ey = 1.9, ez = 2.2;
            boolean middleHidden = !cg.clearLine(ex, ey, ez, 1.5, 0.05, 0.5) && !cg.clearLine(ex, ey, ez, 1.05, 0.5, 0.5);
            var sp = placer.blockSpot(cg, ex, ey, ez, 1, 0, 0);
            if (middleHidden && sp != null) cornerSeen = true;
        }
        check(cornerSeen, "clicks a corner of a face when a block hides its middle");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
