import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.world.*;
import dev.reality.fightbot.core.world.WorldView.Kind;
import java.util.*;

/**
 * Fighting someone flying with an elytra from the ground. The flyer is simulated with the game's elytra
 * step: it circles, then dives at the bot with a firework, passes, pulls up, and comes round again.
 */
public class FlyerTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    /** Flat world with a floor at y 0 and below. */
    static final class Flat implements WorldView {
        final Map<String, Kind> blocks = new HashMap<>();
        final List<Crystal> crystals = new ArrayList<>();
        double[] flyer = {0, 0, 0};
        public Kind kind(int x, int y, int z) { Kind k = blocks.get(x + "," + y + "," + z); return k != null ? k : y <= 0 ? Kind.SOLID : Kind.AIR; }
        public int anchorCharge(int x, int y, int z) { return -1; }
        public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
            for (Crystal c : crystals) if (c.x() + 1 > x0 && c.x() - 1 < x1 && c.y() + 2 > y0 && c.y() < y1 && c.z() + 1 > z0 && c.z() - 1 < z1) return true;
            return false;
        }
        public List<Crystal> crystals() { return crystals; }
        public String dimension() { return "overworld"; }
        public String difficulty() { return "normal"; }
        public Armor armor(boolean self) { return new Armor(20, 12, 16); }
    }

    /** The flyer: a diver that homes in on a point with fireworks, then pulls up past it. */
    public static final class Diver {
        double x, y, z;
        double[] v = {0, 0, 0};
        float yaw, pitch;
        int rocket, phase, phaseTicks;
        public Diver(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public void tick(double bx, double by, double bz) {
            double dx = bx - x, dz = bz - z, dy = by + 1 - y;
            double h = Math.hypot(dx, dz);
            phaseTicks++;
            if (phase == 0) {
                // Dive: aim at the target, rocket on the way in.
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                pitch = (float) Math.max(-20, Math.min(70, -Math.toDegrees(Math.atan2(dy, h))));
                if (rocket <= 0 && h > 10 && phaseTicks % 20 == 1) rocket = 15;
                if (h < 1.5 || y < by + 0.5) { phase = 1; phaseTicks = 0; }
            } else {
                // Pull up and away, then turn back once far enough.
                pitch = -35;
                if (rocket <= 0 && phaseTicks % 25 == 1) rocket = 15;
                if (phaseTicks > 35) { phase = 0; phaseTicks = 0; }
            }
            Predictor.glide(v, yaw, pitch, rocket > 0);
            if (rocket > 0) rocket--;
            x += v[0]; y += v[1]; z += v[2];
            if (y < 1) { y = 1; v[1] = Math.max(0, v[1]); }
        }
    }

    public static Observation obs(long t, double bx, double bz, float yaw, float pitch, Diver d, Loadout l, float hp, double charge, String held) {
        return obs(t, bx, bz, yaw, pitch, d, l, hp, charge, held, false);
    }

    public static Observation obs(long t, double bx, double bz, float yaw, float pitch, Diver d, Loadout l, float hp, double charge, String held, boolean shield) {
        l.oGliding = true;
        l.oPitch = d.pitch;
        l.oHeight = 0.6;
        return new Observation(t, true, bx, 1, bz, 0, 0, 0, yaw, pitch, true, false, false, hp, 0, held, charge, 0, -1, 0,
                d.x, d.y, d.z, d.v[0], d.v[1], d.v[2], d.yaw, false, false, false, false, 20, 0, "mace", true, 0,
                shield, -1, -1, 0, 0, 0, 1.0, false, 0, 0, l, 0, false, true, 24, 24);
    }

    record Run(double crystalDamage, int meleeHits, int closePasses, int pearls, int towers, int dodges, int waits, int blocked) { }

    /** Runs a bot against the diver for some ticks and counts what happened. */
    static Run run(BotProfile p, boolean crystalKit, int ticks, long seed) {
        return run(p, crystalKit, ticks, seed, false);
    }

    static Run run(BotProfile p, boolean crystalKit, int ticks, long seed, boolean shield) {
        Flat w = new Flat();
        Diver d = new Diver(24 + seed % 5, 14, -6 + seed % 7);
        StyleBrain sb = new StyleBrain(p, null);
        double bx = 0.5, bz = 0.5;
        float yaw = 0, pitch = 0;
        double charge = 1;
        double crystalDamage = 0;
        int melee = 0, close = 0, pearls = 0, towers = 0;
        int nextId = 1;
        boolean wasClose = false;
        int blockTicks = 0, blocked = 0;
        for (long t = 1; t <= ticks; t++) {
            d.tick(bx, 1, bz);
            Loadout l = crystalKit
                    ? Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "obsidian*64", "end_crystal*64", "ender_pearl*16", "cobblestone*64")
                    : Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "netherite_axe", "ender_pearl*16", "cobblestone*64");
            Observation o = obs(t, bx, bz, yaw, pitch, d, l, 20, charge, "netherite_sword", shield);
            sb.setWorld(w);
            BotAction a = sb.step(o);
            yaw = a.yaw;
            pitch = a.pitch;
            charge = Math.min(1, charge + 0.08);
            if (a.technique.startsWith("pearl")) pearls++;
            if ("TOWER".equals(a.state) || a.technique.startsWith("pillar") || a.technique.startsWith("tower")) towers++;
            if (a.place != null) {
                PlaceRequest r = a.place;
                if (r.what().equals("obsidian")) w.blocks.put(r.tx() + "," + r.ty() + "," + r.tz(), Kind.OBSIDIAN);
                if (r.what().equals("crystal")) w.crystals.add(new WorldView.Crystal(nextId++, r.tx() + 0.5, r.ty(), r.tz() + 0.5));
            }
            if (a.attackEntity >= 0) {
                for (Iterator<WorldView.Crystal> it = w.crystals.iterator(); it.hasNext(); ) {
                    WorldView.Crystal c = it.next();
                    if (c.id() != a.attackEntity) continue;
                    crystalDamage += Explosions.toPlayer(w, c.x(), c.y(), c.z(), Explosions.CRYSTAL_POWER, d.x, d.y, d.z, false);
                    it.remove();
                }
            }
            double reach = Math.sqrt(Math.pow(Math.max(Math.abs(d.x - bx) - 0.3, 0), 2) + Math.pow(Math.max(d.y - 2.62, Math.max(0, 1 + 1.62 - d.y - 0.6)), 2)
                    + Math.pow(Math.max(Math.abs(d.z - bz) - 0.3, 0), 2));
            if (a.attack && charge >= 0.9 && reach <= 3.0) melee++;
            if (a.attack) charge = 0;
            // Moving: 0.2 blocks a tick on the keys, by the bot's facing.
            double yr = Math.toRadians(yaw);
            double fx = -Math.sin(yr), fz = Math.cos(yr), lx = Math.cos(yr), lz = Math.sin(yr);
            double sp = a.sprint ? 0.28 : 0.2;
            bx += (fx * a.forward + lx * a.strafe) * sp;
            bz += (fz * a.forward + lz * a.strafe) * sp;
            // A dive that reaches the bot's body counts as a mace hit landing.
            boolean isClose = Math.abs(d.x - bx) < 0.9 && Math.abs(d.z - bz) < 0.9 && d.y < 3.2;
            blockTicks = a.block ? blockTicks + 1 : 0;
            if (isClose && !wasClose) {
                // A shield up for 5 ticks or more stops the hit.
                if (blockTicks >= 5) blocked++;
                else close++;
            }
            wasClose = isClose;
        }
        AirDefense ad = sb.airDefense();
        return new Run(crystalDamage, melee, close, pearls, towers, ad.dodges, ad.waits, blocked);
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;

        // Prediction against the game's own elytra step, from moments with no new rocket or turn coming.
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword");
        double predSum = 0, linSum = 0;
        int[] starts = {5, 12, 45, 50};
        for (int start : starts) {
            Diver far = new Diver(30, 30, 0);
            far.phase = 1;
            Predictor pr = new Predictor();
            for (long t = 1; t <= start; t++) {
                far.tick(0, -200, 0);
                pr.observe(obs(t, 0.5, 0.5, 0, 0, far, l, 20, 1, "netherite_sword"));
            }
            double[] guess = pr.at(obs(start, 0.5, 0.5, 0, 0, far, l, 20, 1, "netherite_sword"), 10);
            double lx = far.x + far.v[0] * 10, ly = far.y + far.v[1] * 10, lz = far.z + far.v[2] * 10;
            Diver c = new Diver(far.x, far.y, far.z);
            c.v = far.v.clone(); c.yaw = far.yaw; c.pitch = far.pitch; c.rocket = far.rocket; c.phase = far.phase; c.phaseTicks = far.phaseTicks;
            for (int i = 0; i < 10; i++) c.tick(0, -200, 0);
            predSum += Math.sqrt(Math.pow(guess[0] - c.x, 2) + Math.pow(guess[1] - c.y, 2) + Math.pow(guess[2] - c.z, 2));
            linSum += Math.sqrt(Math.pow(lx - c.x, 2) + Math.pow(ly - c.y, 2) + Math.pow(lz - c.z, 2));
        }
        double worstPred = predSum / starts.length, worstLinear = linSum / starts.length;
        System.out.printf("  half a second ahead: predicted %.2f blocks off on average, straight-line guess %.2f blocks off%n", worstPred, worstLinear);
        check(worstPred < 1.0, "predicts a glide half a second ahead to within a block");
        check(worstPred < worstLinear, "predicts a glide better than a straight line");

        // Arrows at a glider: aimed at where the glide takes them, against aimed by straight-line lead.
        double missPred = 0, missLin = 0;
        int shots = 0;
        for (int start : new int[] {8, 12, 45, 50}) {
            Diver g = new Diver(30, 4, 6);
            g.phase = 1;
            Predictor gp = new Predictor();
            Observation go = null;
            for (long t = 1; t <= start; t++) {
                g.tick(0, -200, 0);
                go = obs(t, 0.5, 0.5, 0, 0, g, l, 20, 1, "bow");
                gp.observe(go);
            }
            Predictor.setCurrent(gp);
            boolean both = Ballistics.aim(go, 3.0) != null;
            Predictor.setCurrent(null);
            if (!both || Ballistics.aim(go, 3.0) == null) continue;
            for (int mode = 0; mode < 2; mode++) {
                Predictor.setCurrent(mode == 0 ? gp : null);
                float[] aim = Ballistics.aim(go, 3.0);
                Predictor.setCurrent(null);
                Diver c = new Diver(g.x, g.y, g.z);
                c.v = g.v.clone(); c.yaw = g.yaw; c.pitch = g.pitch; c.rocket = g.rocket; c.phase = g.phase; c.phaseTicks = g.phaseTicks;
                double yr = Math.toRadians(aim[0]), prr = Math.toRadians(aim[1]);
                double ax = 0.5, ay = 1 + 1.62 - 0.1, az = 0.5;
                double vx = -Math.sin(yr) * Math.cos(prr) * 3, vy = -Math.sin(prr) * 3, vz = Math.cos(yr) * Math.cos(prr) * 3;
                double best = 99;
                for (int t = 0; t < 40; t++) {
                    c.tick(0, -200, 0);
                    ax += vx; ay += vy; az += vz;
                    vx *= 0.99; vy = vy * 0.99 - 0.05; vz *= 0.99;
                    best = Math.min(best, Math.sqrt(Math.pow(ax - c.x, 2) + Math.pow(ay - c.y - 0.3, 2) + Math.pow(az - c.z, 2)));
                }
                if (mode == 0) missPred += best; else missLin += best;
            }
            shots++;
        }
        System.out.printf("  arrows at a glider: %.2f blocks off with the glide predicted, %.2f with a straight-line lead%n", missPred / shots, missLin / shots);
        check(missPred < missLin, "arrows lead a glider better with the glide predicted");

        // Sword bot against a diver: waits on the ground, dodges dives, no pearls or towers.
        double swordHits = 0, swordClose = 0, oldHits = 0, oldClose = 0;
        int pearls = 0, towers = 0, dodges = 0, oldPearls = 0, oldTowers = 0;
        for (long s = 0; s < 10; s++) {
            BotProfile p = new BotProfile();
            p.reactionTicks = 0;
            Run r = run(p, false, 300, s);
            swordHits += r.meleeHits(); swordClose += r.closePasses(); pearls += r.pearls(); towers += r.towers(); dodges += r.dodges();
            BotProfile old = new BotProfile();
            old.reactionTicks = 0;
            old.flyerWait = 0;
            Run ro = run(old, false, 300, s);
            oldHits += ro.meleeHits(); oldClose += ro.closePasses(); oldPearls += ro.pearls(); oldTowers += ro.towers();
        }
        System.out.printf("  sword vs diver, waiting: %.0f hits, %.0f dives reached it, %d dodges, %d pearls, %d tower ticks%n", swordHits, swordClose, dodges, pearls, towers);
        System.out.printf("  sword vs diver, chasing: %.0f hits, %.0f dives reached it, %d pearls, %d tower ticks%n", oldHits, oldClose, oldPearls, oldTowers);
        check(pearls == 0 && towers == 0, "never pearls or towers after a flyer");
        check(dodges > 0, "sidesteps dives");
        // With a shield it blocks the dives instead.
        int shBlocked = 0, shLanded = 0;
        for (long s2 = 0; s2 < 10; s2++) {
            BotProfile p = new BotProfile();
            p.reactionTicks = 0;
            Run r = run(p, false, 300, s2, true);
            shBlocked += r.blocked();
            shLanded += r.closePasses();
        }
        System.out.printf("  sword and shield vs diver: %d dives blocked, %d got through%n", shBlocked, shLanded);
        check(shBlocked > shLanded, "blocks most dives with a shield");
        check(swordHits >= oldHits * 0.8, "still lands about as many hits on the passes");

        // Crystal bot: stays on the ground, sets crystals along the dive, and sets them off.
        double dmg = 0, oldDmg = 0;
        int cPearls = 0, cTowers = 0;
        for (long s = 0; s < 10; s++) {
            BotProfile p = new BotProfile();
            p.reactionTicks = 0;
            Run r = run(p, true, 300, s);
            dmg += r.crystalDamage(); cPearls += r.pearls(); cTowers += r.towers();
            BotProfile old = new BotProfile();
            old.reactionTicks = 0;
            old.flyerWait = 0;
            oldDmg += run(old, true, 300, s).crystalDamage();
        }
        System.out.printf("  crystal vs diver: %.0f blast damage waiting, %.0f chasing%n", dmg, oldDmg);
        check(cPearls == 0 && cTowers == 0, "the crystal style stays on the ground for a flyer");
        check(dmg > oldDmg, "crystals a diver harder by waiting for the dive");
        check(dmg > 20, "sets crystals off on the dives");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
