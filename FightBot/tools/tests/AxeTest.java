import dev.reality.fightbot.core.*;

/** Axe and shield: the axe as the main weapon, crit jumps, and breaking shields. */
public class AxeTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static DuelSim.Result duel(int seed, double critChance, java.util.function.Function<Long, DuelSim.Brain> enemy, boolean enemyShield, boolean enemyAxe) {
        BotProfile p = new BotProfile();
        p.axeCritChance = critChance;
        DuelSim sim = new DuelSim(seed);
        sim.aAxeOnly = true;
        DuelSim.Kit kit = new DuelSim.Kit(2, 0, true, true, enemyShield, enemyAxe);
        return sim.run(300, s -> DuelSim.bot(BotTest.copy(p, s)), enemy, kit);
    }

    static double rate(DuelSim.Result r) {
        return (double) r.aWins / Math.max(1, r.aWins + r.bWins + r.draws);
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        DuelSim.Result sprinter = duel(31, 0.8, s -> DuelSim.plainSprinter(), false, false);
        DuelSim.Result sprinterNoCrit = duel(31, 0.0, s -> DuelSim.plainSprinter(), false, false);
        System.out.println("  vs plain sprinter: with crit jumps won " + Math.round(rate(sprinter) * 100) + "%, " + sprinter.aCrits + " crits. Without "
                + Math.round(rate(sprinterNoCrit) * 100) + "%.");
        check(rate(sprinter) >= rate(sprinterNoCrit), "crit jumps do not lose to plain axe swings against a sprinter");
        check(rate(sprinter) >= 0.7, "axe and shield beats a plain sprinter most of the time");

        DuelSim.Result swordShield = duel(32, 0.8, s -> DuelSim.shieldSwordsman(), true, false);
        DuelSim.Result swordShieldNoCrit = duel(32, 0.0, s -> DuelSim.shieldSwordsman(), true, false);
        System.out.println("  vs sword and shield: with crit jumps won " + Math.round(rate(swordShield) * 100) + "%, shields broken " + swordShield.aShieldsBroken
                + ". Without " + Math.round(rate(swordShieldNoCrit) * 100) + "%.");
        check(swordShield.aShieldsBroken > 0, "breaks the other player's shield with the axe");
        check(rate(swordShield) >= rate(swordShieldNoCrit) - 0.05, "crit jumps hold up against a sword and shield player");

        DuelSim.Result turtle = duel(33, 0.8, s -> DuelSim.shieldTurtle(), true, false);
        System.out.println("  vs shield turtle: won " + Math.round(rate(turtle) * 100) + "%, shields broken " + turtle.aShieldsBroken);
        check(rate(turtle) >= 0.8, "beats a player who hides behind a shield");

        DuelSim.Result axeMirror = duel(34, 0.8, s -> DuelSim.axeUser(), false, true);
        System.out.println("  vs axe user: won " + Math.round(rate(axeMirror) * 100) + "%");
        check(rate(axeMirror) >= 0.6, "beats a plain full-charge axe player");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
