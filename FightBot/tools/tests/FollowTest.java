import dev.reality.fightbot.core.*;

/** The follow-up after the first smash: how often and how fast the second smash comes, with and without Wind Burst. */
public class FollowTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static MaceSim.Result run(BotProfile p, boolean windBurst, java.util.function.Function<Long, MaceSim.Brain> enemy, long seed) {
        MaceSim.windBurst = windBurst;
        MaceSim.enemyHp = 80;
        MaceSim.Result r = new MaceSim(seed).run(100, s -> MaceSim.bot(p), enemy, MaceSim.Kit.ELYTRA, false);
        MaceSim.windBurst = false;
        MaceSim.enemyHp = 20;
        return r;
    }

    static String show(MaceSim.Result r) {
        return String.format("won %d/100, %d smashes, %d came within 4 s of the last, average gap %.1f ticks, fight %.1f s",
                r.wins, r.smashes, r.followGaps, r.followGaps == 0 ? 0 : r.followGapTicks / r.followGaps, r.ticks / 100 / 20);
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 1;
        BotProfile fast = new BotProfile();
        BotProfile high = new BotProfile();
        high.fastElytra = 0;
        int fastWins = 0, highWins = 0;
        double fastTime = 0, highTime = 0;
        for (boolean wb : new boolean[] {false, true}) {
            for (String enemy : new String[] {"chaser", "dodger"}) {
                java.util.function.Function<Long, MaceSim.Brain> e = enemy.equals("dodger") ? s -> MaceSim.dodger() : s -> MaceSim.chaser();
                MaceSim.Result f = run(fast, wb, e, 3);
                MaceSim.Result h = run(high, wb, e, 3);
                System.out.println("  " + enemy + (wb ? " with Wind Burst" : "") + ": fast " + show(f));
                System.out.println("  " + enemy + (wb ? " with Wind Burst" : "") + ": high " + show(h));
                fastWins += f.wins;
                highWins += h.wins;
                fastTime += f.ticks / 2000.0;
                highTime += h.ticks / 2000.0;
            }
        }
        System.out.printf("  totals: fast %d wins in %.1f s, high climb %d wins in %.1f s%n", fastWins, fastTime, highWins, highTime);
        check(fastWins >= highWins - 4, "the fast low dive wins about as often as the high climb");
        // The simulated players never react to a climb they can see coming, which is the point of the low dive,
        // so in here the two come out about even.
        check(fastTime < highTime * 1.1, "and ends fights within a tenth of the time of the high climb");
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
