import dev.reality.fightbot.core.*;
import java.util.*;
public class ShieldDbg2 {
    public static void main(String[] a) {
        DuelSim.Kit k = new DuelSim.Kit(2, 0, true, false, false, false);
        BotProfile p = new BotProfile(); p.blockAtOppCharge = 0.5;
        Map<Integer,Integer> streaks = new TreeMap<>();
        Map<String,Integer> why = new TreeMap<>();
        new DuelSim(21).run(20, s -> {
            DuelSim.Brain inner = DuelSim.bot(BotTest.copy(p, s));
            int[] cur = {0};
            return o -> {
                BotAction act = inner.decide(o);
                if (act.block) cur[0]++; else { if (cur[0] > 0) streaks.merge(Math.min(cur[0], 10), 1, Integer::sum); cur[0] = 0; }
                if (!act.block && o.distance() < 3.6 && o.oCharge() > 0.5 && true)
                    why.merge("attack=" + act.attack + " use=" + act.use + " hotbar=" + act.hotbar + " tech=" + act.technique, 1, Integer::sum);
                return act;
            };
        }, s -> DuelSim.plainSprinter(), k);
        System.out.println("block streak lengths: " + streaks);
        System.out.println("threat but no block: " + why);
    }
}
