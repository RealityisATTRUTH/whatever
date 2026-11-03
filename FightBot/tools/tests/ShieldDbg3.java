import dev.reality.fightbot.core.*;
public class ShieldDbg3 {
    public static void main(String[] a) {
        DuelSim.Kit k = new DuelSim.Kit(2, 0, true, false, false, false);
        BotProfile p = new BotProfile(); p.blockAtOppCharge = 0; p.blockRange = 5;
        int[] n = {0};
        new DuelSim(21).run(3, s -> {
            DuelSim.Brain inner = DuelSim.bot(BotTest.copy(p, s));
            return o -> {
                BotAction act = inner.decide(o);
                if (n[0] < 40 && o.tick() > 40 && o.tick() < 120) {
                    n[0]++;
                    System.out.printf("t%d dist %.2f oCharge %.2f myCharge %.2f shield %b using %b -> block %b attack %b state %s tech %s%n",
                        o.tick(), o.distance(), o.oCharge(), o.charge(), o.hasShield(), o.usingItem(), act.block, act.attack, act.state, act.technique);
                }
                return act;
            };
        }, s -> DuelSim.plainSprinter(), k);
    }
}
