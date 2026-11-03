import dev.reality.fightbot.core.*;
public class Debug {
    public static void main(String[] a) {
        DuelSim sim = new DuelSim(7);
        DuelSim.Result r = sim.run(200, s -> DuelSim.bot(BotTest.copy(new BotProfile(), s)), s -> DuelSim.spamClicker(), 2);
        System.out.printf("A dealt %.0f, B dealt %.0f per 200 fights%n", r.aDealt, r.bDealt);
        DuelSim.Result r0 = sim.run(200, s -> DuelSim.bot(BotTest.copy(new BotProfile(), s)), s -> DuelSim.spamClicker(), 0);
        System.out.println("no gapples: " + r0 + String.format(" dealt %.0f vs %.0f", r0.aDealt, r0.bDealt));
        DuelSim.Result r1 = sim.run(200, s -> DuelSim.spamClicker(), s -> DuelSim.plainSprinter(), 2);
        System.out.println("spam vs sprinter: " + r1);
    }
}
