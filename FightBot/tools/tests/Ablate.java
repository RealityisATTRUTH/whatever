import dev.reality.fightbot.core.*;
public class Ablate {
    public static void main(String[] a) {
        String[] names = {"no W-taps", "no crit jumps", "swings at half charge", "no S-taps", "no jump resets"};
        for (int k = 0; k < names.length; k++) {
            final int kk = k;
            DuelSim sim = new DuelSim(11);
            DuelSim.Result r = sim.run(300, s -> DuelSim.bot(BotTest.copy(new BotProfile(), s)), s -> {
                BotProfile p = BotTest.copy(new BotProfile(), s + 500);
                if (kk == 0) p.wTapChance = 0;
                if (kk == 1) p.critJumpChance = 0;
                if (kk == 2) p.chargeThreshold = 0.5;
                if (kk == 3) p.sTapChance = 0;
                if (kk == 4) p.jumpResetChance = 0;
                return DuelSim.bot(p);
            }, 2);
            System.out.printf("full bot vs bot with %s: full bot wins %.0f%%%n", names[k], r.aWinRate() * 100);
        }
    }
}
