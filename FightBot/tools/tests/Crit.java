import dev.reality.fightbot.core.*;
public class Crit {
    public static void main(String[] a) {
        for (double c : new double[]{0.35, 0.15, 0.05}) {
            DuelSim sim = new DuelSim(11);
            DuelSim.Result r = sim.run(300, s -> { BotProfile p = BotTest.copy(new BotProfile(), s); p.critJumpChance = c; return DuelSim.bot(p); },
                    s -> { BotProfile p = BotTest.copy(new BotProfile(), s + 500); p.critJumpChance = 0; return DuelSim.bot(p); }, 2);
            System.out.printf("crit jump chance %.2f vs never: wins %.0f%%, %s%n", c, r.aWinRate() * 100, r);
        }
        DuelSim sim = new DuelSim(3);
        BotProfile nc = new BotProfile(); nc.critJumpChance = 0;
        System.out.println("no-crit bot vs spam: " + sim.run(200, s -> { BotProfile p = BotTest.copy(nc, s); return DuelSim.bot(p); }, s -> DuelSim.spamClicker(), 2));
        System.out.println("no-crit bot vs sprinter: " + sim.run(200, s -> { BotProfile p = BotTest.copy(nc, s); return DuelSim.bot(p); }, s -> DuelSim.plainSprinter(), 2));
    }
}
