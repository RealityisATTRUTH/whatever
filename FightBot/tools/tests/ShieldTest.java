import dev.reality.fightbot.core.*;

public class ShieldTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    static Observation full(long t, double dist, double charge, double oCharge, String oMain, boolean oBlocking, boolean hasShield,
                            int axeSlot, float hp, float abs, int gapSlot, int gaps, int egapSlot, int egaps, int invGaps, int invEgaps,
                            boolean usingItem, boolean oUsing, String main) {
        return new Observation(t, true, 0, 64, 0, 0, 0, 0, 0, 0, true, true, usingItem, hp, abs, main, charge, 0, gapSlot, gaps,
                0, 64, dist, 0, 0, 0, 180, true, true, oBlocking, oUsing, 20, 0, oMain, true, 0,
                hasShield, axeSlot, egapSlot, egaps, invGaps, invEgaps, oCharge, true, 0, 0);
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        p.reactionTicks = 0;
        p.aimJitter = 0;

        // Blocks when the opponent's bar is nearly full and its own is not.
        BotAction b = new ActionValidator(p).check(full(1, 2.8, 0.3, 0.95, "diamond_sword", false, true, -1, 20, 0, -1, 0, -1, 0, 0, 0, false, false, "diamond_sword"),
                new SwordPolicy(p).step(full(1, 2.8, 0.3, 0.95, "diamond_sword", false, true, -1, 20, 0, -1, 0, -1, 0, 0, 0, false, false, "diamond_sword")));
        check(b.block && !b.attack, "raises the shield against a ready sword: " + b.technique);

        // Never holds the shield against an axe.
        SwordPolicy pa = new SwordPolicy(p);
        BotAction ax = pa.step(full(1, 2.8, 0.3, 0.95, "diamond_axe", false, true, -1, 20, 0, -1, 0, -1, 0, 0, 0, false, false, "diamond_sword"));
        check(!ax.block, "does not block an axe");

        // Both bars full: hold the shield and let the opponent swing first.
        BotAction bait = new SwordPolicy(p).step(full(1, 2.6, 1.0, 0.95, "diamond_sword", false, true, -1, 20, 0, -1, 0, -1, 0, 0, 0, false, false, "diamond_sword"));
        check(bait.block && !bait.attack && bait.technique.equals("shield_bait"), "baits with the shield when both bars are full");
        // The opponent just swung, its own bar is full: hit back.
        BotAction at = new SwordPolicy(p).step(full(1, 2.6, 1.0, 0.1, "diamond_sword", false, true, -1, 20, 0, -1, 0, -1, 0, 0, 0, false, false, "diamond_sword"));
        check(at.attack && !at.block, "hits back while the opponent is on cooldown");

        // Blocks a bow being drawn from range.
        BotAction bow = new SwordPolicy(p).step(full(1, 12, 1.0, 0.2, "bow", false, true, -1, 20, 0, -1, 0, -1, 0, 0, 0, false, true, "diamond_sword"));
        check(bow.block && bow.technique.equals("block_arrow"), "blocks arrows from range");

        // A shield that never comes up counts as disabled for 5 seconds.
        SwordPolicy pd = new SwordPolicy(p);
        BotAction last = null;
        for (int t = 0; t < 12; t++) last = pd.step(full(t, 2.8, 0.3, 0.95, "diamond_sword", false, true, -1, 20, 0, -1, 0, -1, 0, 0, 0, false, false, "diamond_sword"));
        BotAction after = pd.step(full(12, 2.8, 0.3, 0.95, "diamond_sword", false, true, -1, 20, 0, -1, 0, -1, 0, 0, 0, false, false, "diamond_sword"));
        check(!after.block, "stops trying to block once the shield is disabled");

        // Opponent blocking and an axe in the hotbar: swap to the axe, then hit to break the shield.
        SwordPolicy ps = new SwordPolicy(p);
        BotAction swap = null;
        for (int t = 0; t < 4; t++) swap = ps.step(full(t - 3, 2.6, 0.3, 0.2, "diamond_sword", true, true, 2, 20, 0, -1, 0, -1, 0, 0, 0, false, true, "diamond_sword"));
        check(swap.hotbar == 2 && !swap.attack, "swaps to the axe once the opponent keeps blocking");
        BotAction brk = new ActionValidator(p).check(full(2, 2.6, 0.1, 0.2, "diamond_sword", true, true, 2, 20, 0, -1, 0, -1, 0, 0, 0, false, true, "diamond_axe"),
                ps.step(full(2, 2.6, 0.1, 0.2, "diamond_sword", true, true, 2, 20, 0, -1, 0, -1, 0, 0, 0, false, true, "diamond_axe")));
        check(brk.attack && brk.technique.equals("shield_break"), "hits the shield with the axe at any charge");
        BotAction back = ps.step(full(3, 2.6, 0.1, 0.2, "diamond_sword", false, true, 2, 20, 0, -1, 0, -1, 0, 0, 0, false, false, "diamond_axe"));
        check(back.hotbar == 0, "goes back to the sword once the shield is down");

        // Egaps: eats an enchanted golden apple when health is very low.
        BotAction eg = new SwordPolicy(p).step(full(1, 7, 1.0, 0.2, "diamond_sword", false, true, -1, 5, 0, 1, 3, 4, 2, 3, 2, false, false, "diamond_sword"));
        check(eg.use && eg.hotbar == 4 && eg.technique.equals("eat_egap"), "eats an egap when very low");
        BotAction gp = new SwordPolicy(p).step(full(1, 7, 1.0, 0.2, "diamond_sword", false, true, -1, 8, 0, 1, 3, 4, 2, 3, 2, false, false, "diamond_sword"));
        check(gp.use && gp.hotbar == 1 && gp.technique.equals("eat"), "uses a normal golden apple when only a bit low");
        BotAction full2 = new SwordPolicy(p).step(full(1, 7, 1.0, 0.2, "diamond_sword", false, true, -1, 8, 10, 1, 3, 4, 2, 3, 2, false, false, "diamond_sword"));
        check(!full2.use, "does not eat again while still covered by absorption");

        // Egaps only in the main inventory: restock them into the hotbar when nobody is close.
        BotAction rs = new SwordPolicy(p).step(full(1, 9, 1.0, 0.2, "diamond_sword", false, true, -1, 20, 0, 1, 3, -1, 0, 3, 2, false, false, "diamond_sword"));
        check("enchanted_golden_apple".equals(rs.restock), "restocks egaps to the hotbar when safe");
        BotAction nr = new SwordPolicy(p).step(full(1, 3, 1.0, 0.2, "diamond_sword", false, true, -1, 20, 0, 1, 3, -1, 0, 3, 2, false, false, "diamond_sword"));
        check(nr.restock == null, "never restocks with the opponent close");

        // Simulated duels with shields.
        DuelSim sim = new DuelSim(21);
        BotProfile on = new BotProfile();
        BotProfile off = new BotProfile(); off.useShield = 0;
        DuelSim.Kit swordShield = new DuelSim.Kit(2, 0, true, false, false, false);
        DuelSim.Result withShield = sim.run(300, s -> DuelSim.bot(BotTest.copy(on, s)), s -> DuelSim.plainSprinter(), swordShield);
        DuelSim.Result noShield = new DuelSim(21).run(300, s -> DuelSim.bot(BotTest.copy(off, s)), s -> DuelSim.plainSprinter(), swordShield);
        System.out.printf("  vs plain sprinter: with shield won %.0f%%, took %.0f damage, blocked %d hits. Without shield won %.0f%%, took %.0f%n",
                withShield.aWinRate() * 100, withShield.aTaken, withShield.aBlocked, noShield.aWinRate() * 100, noShield.aTaken);
        check(withShield.aBlocked > 0 && withShield.aTaken < noShield.aTaken, "the shield blocks hits and cuts damage taken");

        DuelSim.Kit vsAxe = new DuelSim.Kit(2, 0, true, false, false, true);
        DuelSim.Result axe = new DuelSim(22).run(300, s -> DuelSim.bot(BotTest.copy(on, s)), s -> DuelSim.axeUser(), vsAxe);
        System.out.printf("  vs axe user: won %.0f%%, blocks %d%n", axe.aWinRate() * 100, axe.aTech.getOrDefault("block", 0));
        check(axe.aTech.getOrDefault("block", 0) == 0, "never blocks against an axe user");

        DuelSim.Kit breakKit = new DuelSim.Kit(2, 0, true, true, true, false);
        BotProfile noBreak = new BotProfile(); noBreak.breakShields = 0;
        DuelSim.Result breaker = new DuelSim(23).run(300, s -> DuelSim.bot(BotTest.copy(on, s)), s -> DuelSim.shieldTurtle(), breakKit);
        DuelSim.Result noBreaker = new DuelSim(23).run(300, s -> DuelSim.bot(BotTest.copy(noBreak, s)), s -> DuelSim.shieldTurtle(), breakKit);
        System.out.printf("  vs shield turtle: breaking shields won %.0f%% with %d shields broken, without won %.0f%%%n",
                breaker.aWinRate() * 100, breaker.aShieldsBroken, noBreaker.aWinRate() * 100);
        check(breaker.aShieldsBroken > 0 && breaker.aWinRate() >= noBreaker.aWinRate(), "axe shield breaks help against a shield user");

        DuelSim.Kit egapKit = new DuelSim.Kit(2, 2, true, false, false, false);
        DuelSim.Result eg2 = new DuelSim(24).run(300, s -> DuelSim.bot(BotTest.copy(on, s)), s -> DuelSim.bot(BotTest.copy(on, s + 77)), egapKit);
        System.out.printf("  mirror with egaps: egaps eaten %d, gapples %d%n", eg2.aTech.getOrDefault("eat_egap", 0), eg2.aTech.getOrDefault("eat", 0));
        check(eg2.aTech.getOrDefault("eat_egap", 0) > 0, "eats egaps in real duels");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
