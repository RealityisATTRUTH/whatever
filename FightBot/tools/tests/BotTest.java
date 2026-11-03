import dev.reality.fightbot.core.*;
import java.nio.file.*;
import java.util.*;

public class BotTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    static Observation obs(long t, double dist, double charge, float hp, int heals, boolean ground, double vy, float yawToOpp) {
        // Bot at origin facing the opponent straight south.
        return new Observation(t, true, 0, 64, 0, 0, vy, 0, yawToOpp, 0, ground, true, false, hp, 0, "diamond_sword", charge,
                0, heals > 0 ? 1 : -1, heals, 0, 64, dist, 0, 0, 0, 180, true, true, false, false, 20, 0, "diamond_sword", true, 0);
    }

    public static void main(String[] args) throws Exception {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        p.reactionTicks = 0;
        p.critJumpChance = 0;
        p.aimJitter = 0;

        // Charge discipline: never attack under the threshold.
        SwordPolicy pol = new SwordPolicy(p);
        ActionValidator v = new ActionValidator(p);
        boolean attackedLow = false;
        for (int t = 0; t < 50; t++) {
            BotAction a = v.check(obs(t, 2.6, 0.5, 20, 0, true, 0, 0), pol.step(obs(t, 2.6, 0.5, 20, 0, true, 0, 0)));
            if (a.attack) attackedLow = true;
        }
        check(!attackedLow, "no swings at half charge");
        BotAction full = v.check(obs(60, 2.6, 1.0, 20, 0, true, 0, 0), pol.step(obs(60, 2.6, 1.0, 20, 0, true, 0, 0)));
        check(full.attack, "swings at full charge in reach");
        BotAction far = new SwordPolicy(p).step(obs(1, 6, 1.0, 20, 0, true, 0, 0));
        check(!far.attack && far.forward == 1 && far.sprint, "closes distance with a sprint when far");

        // Spacing: too close means an S-tap back.
        BotProfile close = new BotProfile();
        close.reactionTicks = 0; close.sTapChance = 1; close.aimJitter = 0;
        BotAction st = new SwordPolicy(close).step(obs(5, 1.2, 0.3, 20, 0, true, 0, 0));
        check(st.forward == -1 && st.technique.equals("s_tap"), "S-taps out when the opponent is too close");

        // W-tap after a hit: forward released for a few ticks.
        BotProfile wt = new BotProfile();
        wt.reactionTicks = 0; wt.wTapChance = 1; wt.aimJitter = 0; wt.critJumpChance = 0;
        SwordPolicy wp = new SwordPolicy(wt);
        wp.step(obs(10, 2.6, 1.0, 20, 0, true, 0, 0));
        BotAction after = wp.step(obs(11, 2.6, 0.05, 20, 0, true, 0, 0));
        check(after.forward == 0 && after.technique.equals("w_tap"), "W-taps right after a hit");

        // Healing: low health with distance means eat, low health up close means back off first.
        SwordPolicy hp = new SwordPolicy(p);
        BotAction eat = hp.step(obs(1, 7, 1.0, 6, 3, true, 0, 0));
        check(eat.use && eat.hotbar == 1 && eat.state.equals("RECOVER"), "eats golden apples at low health with room");
        BotAction run = new SwordPolicy(p).step(obs(1, 2, 1.0, 6, 3, true, 0, 0));
        check(run.state.equals("ENGAGE") && !run.use, "keeps fighting instead of eating when the opponent is close");

        // Aim smoothing: never turns faster than the profile allows.
        BotAction turn = new SwordPolicy(p).step(obs(1, 3, 1.0, 20, 0, true, 0, 120));
        check(Angles.diff(turn.yaw, 120) <= p.swordTurnPerTick + 0.01 && !turn.attack, "turns at human speed and does not swing while aimed away");

        // Stale data: do nothing.
        Observation stale = new Observation(1, true, 0, 64, 0, 0, 0, 0, 0, 0, true, true, false, 20, 0, "diamond_sword", 1.0,
                0, -1, 0, 0, 64, 2.5, 0, 0, 0, 180, true, true, false, false, 20, 0, "diamond_sword", true, 5);
        BotAction sa = new ActionValidator(p).check(stale, new SwordPolicy(p).step(stale));
        check(!sa.attack && sa.forward == 0 && sa.state.equals("STALE"), "stale observations stop the bot");

        // Simulated duels.
        DuelSim sim = new DuelSim(7);
        BotProfile def = new BotProfile();
        DuelSim.Result vsSpam = sim.run(200, s -> DuelSim.bot(copy(def, s)), s -> DuelSim.spamClicker(), 2);
        System.out.println("  vs spam clicker: " + vsSpam);
        check(vsSpam.aWinRate() >= 0.8, "beats a spam clicker most of the time");
        DuelSim.Result vsPlain = sim.run(200, s -> DuelSim.bot(copy(def, s)), s -> DuelSim.plainSprinter(), 2);
        System.out.println("  vs plain sprinter: " + vsPlain);
        check(vsPlain.aWinRate() >= 0.6, "beats a full-charge player who never resets sprint or strafes");
        check(vsPlain.aSprintHits > vsPlain.aSwings * 0.3, "lands real sprint hits thanks to W-taps");
        DuelSim.Result mirror = sim.run(200, s -> DuelSim.bot(copy(def, s)), s -> DuelSim.bot(copy(def, s + 99)), 2);
        System.out.println("  vs itself: " + mirror);
        check(mirror.aWinRate() > 0.3 && mirror.aWinRate() < 0.7, "evenly matched against itself");

        // Episode records use the recorder's layout.
        Episode ep = new Episode("Bot1", "Target", "default", "sword");
        SwordPolicy ep1 = new SwordPolicy(p);
        float oh = 20;
        for (int t = 0; t < 80; t++) {
            if (t == 30) oh = 15;
            Observation o = new Observation(t, true, 0, 64, 0, 0, 0, 0, 0, 0, true, true, false, 20, 0, "diamond_sword", t % 13 / 12.0,
                    0, -1, 0, 0, 64, 2.6, 0, 0, 0, 180, true, true, false, false, oh, 0, "diamond_sword", true, 0);
            ep.add(o, new ActionValidator(p).check(o, ep1.step(o)));
        }
        ep.outcome = "win";
        List<String> recs = ep.trainingRecords();
        check(recs.size() == 40 && recs.get(0).contains("\"state\":") && recs.get(0).contains("\"action\":") && recs.get(0).contains("\"result\":"),
                "episode writes state, action, result records");
        check(Math.abs(ep.damageDealt() - 5) < 1e-6, "episode counts damage dealt");
        Path dir = Files.createTempDirectory("bot");
        ep.save(dir, new ActionValidator(p));
        check(Files.readAllLines(dir.resolve("results.csv")).size() == 2, "results row written");
        System.out.println("  " + ep.summaryLine());

        // Profiles save and load.
        BotProfile custom = new BotProfile();
        custom.name = "nekfron_style"; custom.preferredDistance = 2.2; custom.wTapChance = 0.4;
        Path pf = dir.resolve("profiles/nekfron_style.properties");
        custom.save(pf);
        BotProfile loaded = BotProfile.load(pf);
        check(loaded.name.equals("nekfron_style") && loaded.preferredDistance == 2.2 && loaded.wTapChance == 0.4, "profile round trip");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static BotProfile copy(BotProfile base, long seed) {
        BotProfile p = new BotProfile();
        for (java.lang.reflect.Field f : BotProfile.class.getFields()) {
            try { f.set(p, f.get(base)); } catch (ReflectiveOperationException ignored) { }
        }
        p.seed = seed + 1;
        return p;
    }
}
