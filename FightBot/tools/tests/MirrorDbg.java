import dev.reality.fightbot.core.*;
public class MirrorDbg {
    public static void main(String[] a) {
        BotProfile on = new BotProfile();
        DuelSim.Kit both = new DuelSim.Kit(2, 0, true, true, true, true);
        new DuelSim(32).run(1, s -> {
            DuelSim.Brain in = DuelSim.bot(BotTest.copy(on, s));
            return o -> {
                BotAction act = in.decide(o);
                if (o.tick() % 10 == 0 && o.tick() < 300)
                    System.out.printf("t%d dist %.2f my %.2f opp %.2f oppBlocking %b hand %s -> fwd %d block %b attack %b hotbar %d tech %s%n",
                        o.tick(), o.distance(), o.charge(), o.oCharge(), o.oBlocking(), o.mainHand(), act.forward, act.block, act.attack, act.hotbar, act.technique);
                return act;
            };
        }, s -> DuelSim.bot(BotTest.copy(on, s + 9)), both);
    }
}
