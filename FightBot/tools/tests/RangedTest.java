import dev.reality.fightbot.core.*;

import java.util.Random;

/** Bow and crossbow: aim against still and running targets, the draw and release, crossbow loading, and the sword up close. */
public class RangedTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static Observation obs(long t, double ox, double oz, double ovx, double ovz, Loadout l, String held, boolean using, boolean oBlocking) {
        return new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, using, 20, 0,
                held, 1.0, 0, -1, 0, ox, 0, oz, ovx, 0, ovz, 0, true, true, oBlocking, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, l, 0, false, true, 10, 10);
    }

    /** Flies an arrow with the game's per-tick rules and a little random spread, against a moving player box. */
    static boolean hits(Observation o, float[] aim, double speed, Random rng) {
        double yr = Math.toRadians(aim[0]), pr = Math.toRadians(aim[1]);
        double vx = -Math.sin(yr) * Math.cos(pr), vy = -Math.sin(pr), vz = Math.cos(yr) * Math.cos(pr);
        // The game adds a small random spread to every shot.
        vx += rng.nextGaussian() * 0.0075;
        vy += rng.nextGaussian() * 0.0075;
        vz += rng.nextGaussian() * 0.0075;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx = vx / len * speed;
        vy = vy / len * speed;
        vz = vz / len * speed;
        double x = o.x(), y = o.y() + 1.52, z = o.z();
        double tx = o.ox(), tz = o.oz();
        for (int t = 1; t <= 100; t++) {
            for (int s = 1; s <= 8; s++) {
                double px = x + vx * s / 8, py = y + vy * s / 8, pz = z + vz * s / 8;
                double qx = tx + o.ovx() * s / 8, qz = tz + o.ovz() * s / 8;
                if (Math.abs(px - qx) <= 0.3 + 0.25 && Math.abs(pz - qz) <= 0.3 + 0.25 && py >= o.oy() && py <= o.oy() + 1.8) return true;
            }
            x += vx;
            y += vy;
            z += vz;
            tx += o.ovx();
            tz += o.ovz();
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            vy -= 0.05;
            if (y < -20) return false;
        }
        return false;
    }

    static double hitRate(double dist, double side, double speed) {
        Random rng = new Random(5);
        int hit = 0;
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "bow", "arrow*64");
        for (int i = 0; i < 200; i++) {
            Observation o = obs(0, 0.5, 0.5 + dist, side, 0, l, "bow", true, false);
            float[] aim = Ballistics.aim(o, speed);
            if (aim != null && hits(o, aim, speed, rng)) hit++;
        }
        return hit / 200.0;
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        for (double d : new double[] {10, 20, 30}) {
            double still = hitRate(d, 0, 3.0), running = hitRate(d, 0.28, 3.0);
            System.out.println("  bow at " + (int) d + " blocks: standing target " + Math.round(still * 100) + "%, sprinting sideways " + Math.round(running * 100) + "%");
            check(still >= 0.9, "hits a standing target at " + (int) d + " blocks");
            check(running >= 0.8, "leads a target sprinting sideways at " + (int) d + " blocks");
        }

        BotProfile p = new BotProfile();
        Loadout kit = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "bow", "crossbow", "arrow*64", "golden_apple*64");
        check(Style.pick(kit, p).use() == Style.RANGED, "the bow kit picks the bow and crossbow style");

        // Bow: draw for a full second, then let go with exact aim.
        RangedPolicy r = new RangedPolicy(p);
        BotAction out = r.step(obs(0, 0.5, 20.5, 0, 0, kit, "netherite_sword", false, false));
        check(out.hotbar == 1, "takes the bow out from range");
        int releaseAt = -1;
        for (int t = 1; t <= 40 && releaseAt < 0; t++) {
            BotAction a = r.step(obs(t, 0.5, 20.5, 0, 0, kit, "bow", t > 1, false));
            if ("bow_release".equals(a.technique)) {
                releaseAt = t;
                check(!a.use && a.exactAim, "lets go of right click with exact aim");
            }
        }
        System.out.println("  bow released after " + releaseAt + " ticks");
        check(releaseAt >= 20 && releaseAt <= 23, "draws to full power before letting go");

        RangedPolicy r2 = new RangedPolicy(p);
        boolean shotShield = false;
        for (int t = 0; t <= 40; t++) {
            BotAction a = r2.step(obs(t, 0.5, 20.5, 0, 0, kit, "bow", t > 0, true));
            if ("bow_release".equals(a.technique)) shotShield = true;
        }
        check(!shotShield, "holds the shot while the target's shield is up");

        // Crossbow only: load it, then fire.
        Loadout xb = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "crossbow", "arrow*64");
        RangedPolicy r3 = new RangedPolicy(p);
        int loadedAt = -1;
        for (int t = 0; t <= 30 && loadedAt < 0; t++) {
            BotAction a = r3.step(obs(t, 0.5, 20.5, 0, 0, xb, t == 0 ? "netherite_sword" : "crossbow", t > 1, false));
            if ("crossbow_loaded".equals(a.technique)) loadedAt = t;
        }
        System.out.println("  crossbow let go to load after " + loadedAt + " ticks");
        check(loadedAt > 0 && loadedAt <= 14, "holds right click long enough to load the crossbow, then lets go");
        Loadout charged = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "crossbow:charged", "arrow*64");
        BotAction fire = r3.step(obs(40, 0.5, 20.5, 0, 0, charged, "crossbow:charged", false, false));
        check(fire.useOnce && fire.exactAim && "crossbow_fire".equals(fire.technique), "fires the loaded crossbow");
        ActionValidator v = new ActionValidator(p);
        BotAction checked = v.check(obs(41, 0.5, 20.5, 0, 0, charged, "crossbow:charged", false, false), fire);
        check(checked.useOnce, "the validator lets a loaded crossbow fire");

        // Close: the sword.
        RangedPolicy r4 = new RangedPolicy(p);
        BotAction near = r4.step(obs(0, 0.5, 3.0, 0, 0, kit, "bow", false, false));
        check(r4.stateName().equals("RANGED_MELEE") && near.hotbar == 0, "switches to the sword up close");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
