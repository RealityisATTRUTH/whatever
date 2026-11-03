import dev.reality.fightbot.core.*;
public class ShieldDbg {
    public static void main(String[] a) {
        DuelSim.Kit k = new DuelSim.Kit(2, 0, true, false, false, false);
        BotProfile p = new BotProfile(); p.blockAtOppCharge = 0.5;
        DuelSim.Result r = new DuelSim(21).run(50, s -> DuelSim.bot(BotTest.copy(p, s)), s -> DuelSim.plainSprinter(), k);
        System.out.println(r.aTech + " blocked " + r.aBlocked);
    }
}
