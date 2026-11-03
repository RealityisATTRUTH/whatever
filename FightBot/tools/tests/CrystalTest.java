import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.place.*;
import dev.reality.fightbot.core.world.*;
import dev.reality.fightbot.core.world.WorldView.Kind;

import java.util.*;

/**
 * Checks the block placement layer, the explosion estimate, the crystal and anchor choices, and
 * simulated crystal duels. Runs without Minecraft.
 */
public class CrystalTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    /** A small world for unit checks: grass floor, blocks in a map, players and crystals as boxes. */
    static final class MapWorld implements WorldView {
        final Map<String, Kind> blocks = new HashMap<>();
        final Map<String, Integer> charges = new HashMap<>();
        final List<Crystal> crystals = new ArrayList<>();
        final List<double[]> players = new ArrayList<>();
        String dim = "overworld";
        Kind floor = Kind.SOLID;

        void set(int x, int y, int z, Kind k) { blocks.put(x + "," + y + "," + z, k); }
        public Kind kind(int x, int y, int z) { Kind k = blocks.get(x + "," + y + "," + z); return k != null ? k : y <= 0 ? floor : Kind.AIR; }
        public int anchorCharge(int x, int y, int z) { return kind(x, y, z) == Kind.ANCHOR ? charges.getOrDefault(x + "," + y + "," + z, 0) : -1; }
        public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
            for (double[] p : players) if (p[0] + 0.3 > x0 && p[0] - 0.3 < x1 && p[1] + 1.8 > y0 && p[1] < y1 && p[2] + 0.3 > z0 && p[2] - 0.3 < z1) return true;
            for (Crystal c : crystals) if (c.x() + 1 > x0 && c.x() - 1 < x1 && c.y() + 2 > y0 && c.y() < y1 && c.z() + 1 > z0 && c.z() - 1 < z1) return true;
            return false;
        }
        public List<Crystal> crystals() { return crystals; }
        public String dimension() { return dim; }
        public String difficulty() { return "normal"; }
        public Armor armor(boolean self) { return new Armor(20, 12, 16); }
    }

    static Observation obs(long t, Loadout l, double x, double z, double ox, double oz, float hp) {
        return new Observation(t, true, x, 1, z, 0, 0, 0, 0, 0, true, false, false, hp, 0, "netherite_sword", 1.0, 0, -1, 0,
                ox, 1, oz, 0, 0, 0, 180, true, false, false, false, 20, 0, "netherite_sword", true, 0,
                false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, l, 0, false, true, 24, 24);
    }

    static Loadout crystalKit() {
        return Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "obsidian*64", "end_crystal*64",
                "respawn_anchor*16", "glowstone*64");
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        Placer pl = new Placer(4.5, 3);

        // can_place for crystals.
        MapWorld w = new MapWorld();
        w.set(0, 1, 3, Kind.OBSIDIAN);
        check(pl.canPlaceCrystal(w, 0, 1, 3), "a crystal fits on obsidian with air above");
        check(!pl.canPlaceCrystal(w, 1, 0, 3), "not on grass");
        w.set(0, 1, 4, Kind.BEDROCK);
        check(pl.canPlaceCrystal(w, 0, 1, 4), "bedrock works too");
        w.players.add(new double[] {0.5, 2, 4.5});
        check(!pl.canPlaceCrystal(w, 0, 1, 4), "not when a player stands in the space above");
        w.players.clear();
        w.set(0, 2, 3, Kind.SOLID);
        check(!pl.canPlaceCrystal(w, 0, 1, 3), "not when the space above is a block");
        w.set(0, 2, 3, Kind.AIR);

        // can_place for blocks.
        MapWorld w2 = new MapWorld();
        check(pl.canPlaceBlock(w2, 2, 1, 2), "a block can go on the floor");
        check(!pl.canPlaceBlock(w2, 2, 3, 2), "not floating with nothing to click");
        w2.players.add(new double[] {2.5, 1, 2.5});
        check(!pl.canPlaceBlock(w2, 2, 1, 2), "not inside a player");

        // Aim, reach, and line of sight.
        MapWorld w3 = new MapWorld();
        Placer.Spot near = pl.blockSpot(w3, 0.5, 2.62, 0.5, 0, 1, 3);
        check(near != null && near.face().equals("up") && near.cy() == 0, "clicks the top of the floor block under the spot");
        check(pl.blockSpot(w3, 0.5, 2.62, 0.5, 0, 1, 7) == null, "too far away to reach");
        w3.set(0, 1, 2, Kind.SOLID);
        w3.set(0, 2, 2, Kind.SOLID);
        w3.set(0, 3, 2, Kind.SOLID);
        w3.set(-1, 2, 2, Kind.SOLID);
        w3.set(1, 2, 2, Kind.SOLID);
        Placer.Spot behind = pl.blockSpot(w3, 0.5, 2.62, 0.5, 0, 1, 3);
        check(behind == null || !behind.face().equals("up") || behind.cz() != 3, "a wall in the way blocks the click");

        // Request and verify.
        MapWorld w4 = new MapWorld();
        PlaceRequest r = new PlaceRequest("obsidian", 1, 0, 0, 3, "up", 0.5, 1, 3.5, 0, 1, 3, 10);
        check(pl.request(w4, r), "a click is accepted");
        check(!pl.request(w4, new PlaceRequest("obsidian", 1, 1, 0, 3, "up", 1.5, 1, 3.5, 1, 1, 3, 10)), "no second click while the first is unresolved");
        check(pl.verify(w4, 11) == null, "waits for the world to change");
        w4.set(0, 1, 3, Kind.OBSIDIAN);
        Placer.Result ok = pl.verify(w4, 12);
        check(ok != null && ok.ok(), "confirms the block once it shows up");
        PlaceRequest r2 = new PlaceRequest("obsidian", 1, 0, 0, 5, "up", 0.5, 1, 5.5, 0, 1, 5, 20);
        pl.request(w4, r2);
        w4.players.add(new double[] {0.5, 1, 5.5});
        Placer.Result bad = null;
        for (long t = 21; t < 30 && bad == null; t++) bad = pl.verify(w4, t);
        check(bad != null && !bad.ok() && bad.reason().contains("standing"), "a failed click says why: " + (bad == null ? "" : bad.reason()));
        check(pl.skipped("obsidian@0,1,5", 30) && !pl.request(w4, new PlaceRequest("obsidian", 1, 0, 0, 5, "up", 0.5, 1, 5.5, 0, 1, 5, 30)),
                "a spot that failed is skipped for a while instead of clicked again");

        // Explosion estimate.
        MapWorld e = new MapWorld();
        double close = Explosions.toPlayer(e, 0.5, 1, 0.5, Explosions.CRYSTAL_POWER, 1.5, 1, 0.5, false);
        double far = Explosions.toPlayer(e, 0.5, 1, 0.5, Explosions.CRYSTAL_POWER, 6.5, 1, 0.5, false);
        double outside = Explosions.toPlayer(e, 0.5, 1, 0.5, Explosions.CRYSTAL_POWER, 14.5, 1, 0.5, false);
        System.out.printf("  crystal damage in netherite with Protection IV: 1 block %.1f, 6 blocks %.1f, 14 blocks %.1f%n", close, far, outside);
        check(close > far && far > 0 && outside == 0, "blast damage falls off with distance and stops at 12 blocks");
        for (int y = 1; y <= 4; y++) for (int z = -2; z <= 2; z++) e.set(3, y, z, Kind.SOLID);
        double shielded = Explosions.toPlayer(e, 0.5, 1, 0.5, Explosions.CRYSTAL_POWER, 4.5, 1, 0.5, false);
        double open = Explosions.toPlayer(new MapWorld(), 0.5, 1, 0.5, Explosions.CRYSTAL_POWER, 4.5, 1, 0.5, false);
        check(shielded < open * 0.2, "a wall shields from the blast");
        MapWorld nether = new MapWorld();
        nether.dim = "the_nether";
        check(!nether.anchorsExplode() && new MapWorld().anchorsExplode(), "anchors only explode outside the Nether");

        // Choices.
        BotProfile p = new BotProfile();
        MapWorld c1 = new MapWorld();
        c1.floor = Kind.BEDROCK;
        c1.crystals.add(new WorldView.Crystal(7, 0.5, 1, 4.5));
        CrystalPolicy cp = new CrystalPolicy(p);
        cp.setWorld(c1);
        BotAction brk = cp.step(obs(1, crystalKit(), 0.5, 1.5, 0.5, 5.5, 20));
        check(brk.attackEntity == 7, "hits a crystal next to the target and away from itself");
        MapWorld c2 = new MapWorld();
        c2.floor = Kind.BEDROCK;
        c2.crystals.add(new WorldView.Crystal(8, 0.5, 1, 1.5));
        CrystalPolicy cp2 = new CrystalPolicy(p);
        cp2.setWorld(c2);
        BotAction noBrk = cp2.step(obs(1, crystalKit(), 0.5, 0.5, 0.5, 4.5, 20));
        check(noBrk.attackEntity != 8, "does not hit a crystal that would hurt itself more than the target");
        CrystalPolicy low = new CrystalPolicy(p);
        low.setWorld(c1);
        Loadout noTotem = Loadout.of("netherite_chestplate", "air", "netherite_sword", "obsidian*64", "end_crystal*64");
        BotAction risky = low.step(obs(1, noTotem, 0.5, 2.0, 0.5, 5.5, 3));
        check(risky.attackEntity < 0, "never takes a blast that would kill it without a totem");
        CrystalPolicy np = new CrystalPolicy(p);
        np.setWorld(nether);
        boolean anchorInNether = false;
        for (int t = 0; t < 10; t++) {
            BotAction x = np.step(obs(t, crystalKit(), 0.5, 1.5, 0.5, 4.5, 20));
            if (x.place != null && (x.place.what().equals("anchor") || x.place.what().equals("charge") || x.place.what().equals("detonate"))) anchorInNether = true;
        }
        check(!anchorInNether, "never uses anchors in the Nether");

        // Nothing worth a click: it says why, then closes in to fight instead of circling.
        CrystalPolicy idle = new CrystalPolicy(p);
        idle.setWorld(nether);
        Loadout crystalsOnly = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "end_crystal*64");
        BotAction last = null;
        for (int t = 0; t < 25; t++) last = idle.step(obs(t, crystalsOnly, 0.5, 0.5, 0.5, 6.5, 20));
        System.out.println("  " + idle.lastWhy());
        check(last.forward == 1 && last.technique.equals("close_in") && idle.lastWhy().startsWith("No click"), "with nothing to place it closes in and says why");

        // Simulated duels.
        CrystalSim.bedrockFloor = true;
        CrystalSim.Result bed = new CrystalSim(1).run(30, s -> CrystalSim.bot(p), s -> CrystalSim.swordsman(), false);
        CrystalSim.bedrockFloor = false;
        CrystalSim.Result grass = new CrystalSim(1).run(30, s -> CrystalSim.bot(p), s -> CrystalSim.swordsman(), false);
        System.out.println("  bedrock floor vs sword player: " + bed);
        System.out.println("  grass floor vs sword player: " + grass);
        check(bed.winRate() >= 0.9 && bed.crystalsBroken > 0 && bed.dealt > bed.taken, "crystals win on a bedrock floor and deal more than they take");
        check(grass.winRate() >= 0.9 && grass.anchorsSetOff > 0 && grass.dealt > grass.taken, "anchors win on a grass floor and deal more than they take");
        CrystalSim.placeDelay = 2;
        CrystalSim.Result slow = new CrystalSim(4).run(20, s -> CrystalSim.bot(p), s -> CrystalSim.swordsman(), false);
        CrystalSim.placeDelay = 0;
        System.out.println("  clicks that take 2 ticks: " + slow + " " + slow.failures);
        check(slow.winRate() >= 0.9 && slow.failedClicks <= slow.clicks / 10, "still works when the server takes 2 ticks to apply a click");

        // The log.
        StyleBrain[] holder = new StyleBrain[1];
        CrystalSim.bedrockFloor = true;
        new CrystalSim(5).run(1, s -> {
            StyleBrain b = new StyleBrain(p, null);
            holder[0] = b;
            ActionValidator v = new ActionValidator(p);
            return (o, wv) -> { b.setWorld(wv); return v.check(o, b.step(o)); };
        }, s -> CrystalSim.swordsman(), false);
        CrystalSim.bedrockFloor = false;
        List<String> lines = holder[0].crystal().log.flush();
        boolean fields = false, measured = false;
        for (String l : lines) {
            if (l.contains("\"tick\"") && l.contains("\"activeItem\"") && l.contains("\"targetX\"") && l.contains("\"targetHealth\"")
                    && l.contains("\"selfHealth\"") && l.contains("\"placementResult\"") && l.contains("\"nextAction\"")) fields = true;
            if (l.contains("\"action\":\"break\"") && !l.contains("\"damageDealt\":null")) measured = true;
        }
        check(fields, "each logged action has tick, item, target position and health, own health, placement result, and next action");
        check(measured, "blast damage is measured from health afterward");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
