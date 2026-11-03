import dev.reality.fightbot.core.*;
public class ShieldSweep {
    public static void main(String[] a) {
        DuelSim.Kit k = new DuelSim.Kit(2, 0, true, false, false, false);
        BotProfile off = new BotProfile(); off.useShield = 0;
        DuelSim.Result base = new DuelSim(21).run(300, s -> DuelSim.bot(BotTest.copy(off, s)), s -> DuelSim.plainSprinter(), k);
        System.out.printf("no shield: won %.0f%%, took %.0f%n", base.aWinRate()*100, base.aTaken);
        for (double c : new double[]{0.0, 0.3, 0.5}) {
            for (double range : new double[]{3.6, 5.0}) {
                BotProfile p = new BotProfile(); p.blockAtOppCharge = c; p.blockRange = range;
                DuelSim.Result r = new DuelSim(21).run(300, s -> DuelSim.bot(BotTest.copy(p, s)), s -> DuelSim.plainSprinter(), k);
                System.out.printf("block at opp charge %.2f range %.1f: won %.0f%%, took %.0f, blocked %d%n", c, range, r.aWinRate()*100, r.aTaken, r.aBlocked);
            }
        }
    }
}
