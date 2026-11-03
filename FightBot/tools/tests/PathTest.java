import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.nav.*;
import dev.reality.fightbot.core.world.WorldView;
import java.util.*;

/**
 * Path tracking over rough ground, like the blasted hills in the fights against Canaan, where the bot weaved
 * from side to side on the way to him. It measures how long the trip takes and how much the bot turns.
 */
public class PathTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    /** Rolling ground one or two blocks high, with a few lone pillars and pits. */
    static NavTest.Grid rough(long seed) {
        NavTest.Grid w = new NavTest.Grid();
        Random r = new Random(seed);
        double[] ax = new double[4], az = new double[4], ph = new double[4];
        for (int i = 0; i < 4; i++) { ax[i] = 0.05 + r.nextDouble() * 0.15; az[i] = 0.05 + r.nextDouble() * 0.15; ph[i] = r.nextDouble() * 6; }
        for (int x = -10; x <= 70; x++) for (int z = -30; z <= 30; z++) {
            double n = 0;
            for (int i = 0; i < 4; i++) n += Math.sin(x * ax[i] + ph[i]) * Math.cos(z * az[i] + ph[i] * 0.7);
            int h = (int) Math.max(0, Math.min(3, Math.round(1 + n * 0.8)));
            for (int y = 0; y < h; y++) w.set(x, y, z, WorldView.Kind.SOLID);
        }
        for (int i = 0; i < 25; i++) {
            int x = 3 + r.nextInt(55), z = -12 + r.nextInt(25);
            int top = 0;
            while (w.solid(x, top, z)) top++;
            for (int y = top; y < top + 3; y++) w.set(x, y, z, WorldView.Kind.SOLID);
        }
        return w;
    }

    static int ground(NavTest.Grid w, int x, int z) {
        int y = 0;
        while (w.solid(x, y, z)) y++;
        return y;
    }

    record Trip(boolean reached, long ticks, double meanTurn, double bigTurns, double straightness) { }

    static Trip trip(NavTest.Grid w, double tx, double tz, int maxTicks) {
        NavTest.Body bot = new NavTest.Body(0.5, ground(w, 0, 0), 0.5);
        NavTest.Body target = new NavTest.Body(tx, ground(w, (int) Math.floor(tx), (int) Math.floor(tz)), tz);
        BotProfile p = new BotProfile();
        p.reactionTicks = 0;
        Navigator nav = new Navigator(p);
        nav.setWorld(w);
        MotionSmoother sm = new MotionSmoother(p);
        w.bot = bot;
        w.target = target;
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword");
        double turnSum = 0, path = 0;
        int big = 0, n = 0;
        double sx = bot.x, sz = bot.z;
        for (long t = 0; t < maxTicks; t++) {
            Observation o = NavTest.obs(t, w, bot, target, l);
            if (o.distance() <= 3.5 && Math.abs(target.y - bot.y) <= 1.6) {
                double straight = Math.hypot(bot.x - sx, bot.z - sz);
                return new Trip(true, t, turnSum / Math.max(1, n), (double) big / Math.max(1, n), straight / Math.max(1e-6, path));
            }
            BotAction a = BotAction.idle(bot.yaw, bot.pitch);
            if (!nav.step(o, a)) {
                a.yaw = dev.reality.fightbot.core.place.Placer.yawTo(bot.x, bot.z, target.x, target.z);
                a.forward = 1;
                a.sprint = true;
                Navigator.autoJump(w, o, a);
            }
            sm.apply(o, a);
            double turn = Math.abs(Angles.wrap(a.yaw - bot.yaw));
            if (t > 0) { turnSum += turn; n++; if (turn > 25) big++; }
            bot.yaw = a.yaw;
            double ox = bot.x, oz = bot.z;
            if (a.jump && bot.onGround && bot.vy <= 0) bot.vy = 0.42;
            if (a.forward != 0 || a.strafe != 0) {
                double sp = a.sneak ? 0.065 : a.sprint && a.forward > 0 ? (bot.onGround ? 0.28 : 0.31) : 0.21;
                double r = Math.toRadians(a.yaw);
                double len = Math.hypot(a.forward, a.strafe);
                double f = a.forward / len, st = a.strafe / len;
                double nx = bot.x + (st * Math.cos(r) - f * Math.sin(r)) * sp, nz = bot.z + (f * Math.cos(r) + st * Math.sin(r)) * sp;
                if (!NavTest.blocked(w, nx, bot.y, bot.z)) bot.x = nx;
                if (!NavTest.blocked(w, bot.x, bot.y, nz)) bot.z = nz;
            }
            path += Math.hypot(bot.x - ox, bot.z - oz);
            bot.vx = bot.x - ox;
            bot.vz = bot.z - oz;
            bot.y += bot.vy;
            if (bot.vy <= 0 && NavTest.standing(w, bot, bot.y - 1e-3)) {
                bot.y = Math.floor(bot.y - 1e-3) + 1;
                bot.vy = 0;
                bot.onGround = true;
            } else {
                bot.onGround = false;
                bot.vy = (bot.vy - 0.08) * 0.98;
            }
        }
        return new Trip(false, maxTicks, turnSum / Math.max(1, n), (double) big / Math.max(1, n), 0);
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;
        int reached = 0, trips = 0;
        double ticks = 0, turn = 0, big = 0, straight = 0;
        for (long seed = 1; seed <= 12; seed++) {
            NavTest.Grid w = rough(seed);
            Trip tr = trip(w, 50.5, (seed % 5) * 4 - 8 + 0.5, 600);
            trips++;
            if (tr.reached()) {
                reached++;
                ticks += tr.ticks();
                straight += tr.straightness();
            }
            turn += tr.meanTurn();
            big += tr.bigTurns();
        }
        System.out.printf("  reached %d of %d, %.0f ticks on average, %.1f degrees of turn a tick, %.1f%% of ticks turn over 25, straightness %.2f%n",
                reached, trips, ticks / Math.max(1, reached), turn / trips, 100 * big / trips, straight / Math.max(1, reached));
        check(reached == trips, "gets across rough ground every time");
        check(turn / trips < 4.0, "keeps a steady heading over rough ground");
        check(straight / Math.max(1, reached) > 0.85, "runs close to a straight line");
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
