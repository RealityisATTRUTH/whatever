import dev.reality.fightbot.core.*;
public class ShieldMore {
    public static void main(String[] a) {
        BotProfile on = new BotProfile();
        BotProfile off = new BotProfile(); off.useShield = 0;
        DuelSim.Kit both = new DuelSim.Kit(2, 0, true, true, true, true);
        DuelSim.Result r1 = new DuelSim(31).run(300, s -> DuelSim.bot(BotTest.copy(on, s)), s -> DuelSim.bot(BotTest.copy(off, s + 9)), both);
        System.out.printf("shield bot vs same bot without shield: %.0f%%%n", r1.aWinRate() * 100);
        DuelSim.Result r2 = new DuelSim(32).run(300, s -> DuelSim.bot(BotTest.copy(on, s)), s -> DuelSim.bot(BotTest.copy(on, s + 9)), both);
        System.out.println("shield bot mirror: " + r2);
        DuelSim.Result r3 = new DuelSim(33).run(300, s -> DuelSim.bot(BotTest.copy(on, s)), s -> DuelSim.spamClicker(), new DuelSim.Kit(2, 0, true, false, false, false));
        System.out.printf("shield bot vs spam clicker: %.0f%%%n", r3.aWinRate() * 100);
    }
}
