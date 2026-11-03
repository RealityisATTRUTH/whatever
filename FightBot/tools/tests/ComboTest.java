import dev.reality.fightbot.core.*;

import java.util.List;

/**
 * Checks the elytra, rocket, chestplate, elytra, chestplate, axe, mace combo: requirements, step order,
 * confirmations, retries, slow swaps, failed swaps, failed glides, aborts, the fall-distance rule,
 * and simulated duels. Runs without Minecraft.
 */
public class ComboTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    static Observation obs(long t, Loadout l, double y, double vy, boolean ground, boolean gliding, double fall,
                           double ox, double oz, boolean blocking) {
        return new Observation(t, true, 0, y, 0, 0, vy, 0, 0, 90, ground, false, false, 20, 0, "diamond_sword", 1.0, 1, -1, 0,
                ox, 0, oz, 0, 0, 0, 180, true, false, blocking, false, 20, 0, "diamond_sword", true, 0,
                false, 2, -1, 0, 0, 0, 0.5, false, 0, 0, l, fall, gliding, true);
    }

    static Loadout kit(String chest, String chestSlot) {
        return Loadout.of(chest, "totem_of_undying", "mace", "diamond_sword", "diamond_axe", "firework_rocket*32", chestSlot);
    }

    static StyleBrain last;

    static MaceSim.Result duel(BotProfile p, MaceSim.Brain enemy, boolean shield, int n, long seed) {
        return new MaceSim(seed).run(n, s -> {
            StyleBrain b = new StyleBrain(p, null);
            last = b;
            ActionValidator v = new ActionValidator(p);
            return o -> v.check(o, b.step(o));
        }, s -> enemy, MaceSim.Kit.ELYTRA, shield);
    }

    static int indexOf(List<String> log, String... parts) {
        for (int i = 0; i < log.size(); i++) {
            boolean all = true;
            for (String part : parts) all &= log.get(i).contains(part);
            if (all) return i;
        }
        return -1;
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        p.aimJitter = 0;

        // Requirements.
        ElytraCombo c0 = new ElytraCombo(p);
        Loadout noChest = Loadout.of("elytra", "air", "mace", "diamond_sword", "firework_rocket*8");
        check(!c0.begin(obs(1, noChest, 30, -0.2, false, true, 1, 3, 3, false)) && c0.logLines().get(0).contains("no chestplate"),
                "does not start without a chestplate to swap to, and logs why");
        Loadout noRocket = Loadout.of("elytra", "air", "mace", "diamond_sword", "diamond_chestplate");
        check(!new ElytraCombo(p).begin(obs(1, noRocket, 30, -0.2, false, true, 1, 3, 3, false)), "does not start without a firework for recovery");
        check(!new ElytraCombo(p).begin(obs(1, kit("elytra", "diamond_chestplate"), 30, -0.2, false, false, 1, 3, 3, false)),
                "does not start unless actually gliding");

        // The first step asks for the chestplate and waits to see it. Classic mode, with a free fall after it.
        BotProfile classic = new BotProfile();
        classic.fastElytra = 0;
        ElytraCombo c1 = new ElytraCombo(classic);
        Loadout gliding = kit("elytra", "diamond_chestplate");
        c1.begin(obs(10, gliding, 30, -0.2, false, true, 1, 3, 3, false));
        BotAction a1 = new BotAction();
        c1.step(obs(10, gliding, 30, -0.2, false, true, 1, 3, 3, false), a1);
        check("chestplate".equals(a1.equip) && c1.step() == ElytraCombo.Step.DROP1, "first step requests the chestplate swap");
        BotAction a2 = new BotAction();
        c1.step(obs(11, gliding, 29.8, -0.3, false, true, 1, 3, 3, false), a2);
        check(c1.step() == ElytraCombo.Step.DROP1, "does not move on until the swap is seen");
        Loadout dropped = kit("diamond_chestplate", "elytra");
        BotAction a3 = new BotAction();
        c1.step(obs(12, dropped, 29.5, -0.4, false, false, 1.5, 3, 3, false), a3);
        check(c1.step() == ElytraCombo.Step.FALL1, "moves on once the chestplate is on and the glide stopped");
        // Fast mode goes straight from the chestplate back to the elytra, the same tick.
        ElytraCombo cf = new ElytraCombo(p);
        cf.begin(obs(10, gliding, 30, -0.2, false, true, 1, 3, 3, false));
        cf.step(obs(10, gliding, 30, -0.2, false, true, 1, 3, 3, false), new BotAction());
        BotAction f3 = new BotAction();
        cf.step(obs(12, dropped, 29.5, -0.4, false, false, 1.5, 3, 3, false), f3);
        check(cf.step() == ElytraCombo.Step.REEQUIP && "elytra".equals(f3.equip), "fast mode puts the elytra back on as soon as the chestplate shows");

        // The fall-distance rule: in reach with too little fall, it abandons instead of swinging.
        ElytraCombo c2 = new ElytraCombo(p);
        c2.begin(obs(20, gliding, 4, -0.2, false, true, 1, 0.5, 0.5, false));
        c2.step(obs(20, gliding, 4, -0.2, false, true, 1, 0.5, 0.5, false), new BotAction());
        BotAction low = new BotAction();
        c2.step(obs(21, dropped, 3.5, -0.3, false, false, 1.2, 0.5, 0.5, false), low);
        BotAction low2 = new BotAction();
        for (int t = 22; t < 30 && c2.active(); t++) {
            low2 = new BotAction();
            c2.step(obs(t, dropped, 2.6, -0.4, false, false, 1.4, 0.5, 0.5, false), low2);
        }
        check(!low.attack && low.swapAttack == null && !low2.attack && low2.swapAttack == null, "never swings a smash with too little fall");

        // Target out of range: abandon and recover by getting the elytra back on.
        ElytraCombo c3 = new ElytraCombo(p);
        c3.begin(obs(30, gliding, 30, -0.2, false, true, 1, 3, 3, false));
        c3.step(obs(30, gliding, 30, -0.2, false, true, 1, 3, 3, false), new BotAction());
        c3.step(obs(31, dropped, 29, -0.4, false, false, 2, 3, 3, false), new BotAction());
        BotAction away = new BotAction();
        c3.step(obs(32, dropped, 28, -0.5, false, false, 3, 30, 3, false), away);
        check(c3.outcome().contains("target out of range") && "elytra".equals(away.equip), "abandons when the target gets away and puts the elytra back on");

        // Simulated duels.
        MaceSim.Result chase = duel(p, MaceSim.chaser(), false, 100, 3);
        System.out.println("  vs chaser: " + chase);
        MaceSim.Result dodge = duel(p, MaceSim.dodger(), false, 100, 3);
        System.out.println("  vs dodger: " + dodge);
        System.out.println("  dodger combo steps: " + dodge.techniques);
        MaceSim.Result shield = duel(p, MaceSim.shieldUp(), true, 100, 3);
        System.out.println("  vs shield: " + shield);
        check(chase.winRate() >= 0.9 && dodge.winRate() >= 0.9 && shield.winRate() >= 0.9, "wins against all three opponents");
        check(dodge.techniques.getOrDefault("combo_reglide", 0) > 0 && dodge.techniques.getOrDefault("combo_dive", 0) > 0,
                "uses the re-glide against a player who runs out from under it");
        check(shield.stunSlams > 0, "uses the axe on a raised shield");

        // Step order in one full combo with a re-glide.
        duel(p, MaceSim.dodger(), false, 1, 3);
        List<String> log = last.comboLog();
        int[] order = {
                indexOf(log, "\"requirements\"", "\"ok\""),
                indexOf(log, "swap_to_chestplate", "confirmed"),
                indexOf(log, "plan_regrip"),
                indexOf(log, "swap_to_elytra", "confirmed"),
                indexOf(log, "open_elytra", "requested"),
                indexOf(log, "open_elytra", "confirmed"),
                indexOf(log, "\"DROP2\"", "confirmed"),
                indexOf(log, "select_mace"),
                indexOf(log, "attempt at")};
        boolean inOrder = true;
        for (int i = 0; i < order.length; i++) inOrder &= order[i] >= 0 && (i == 0 || order[i] > order[i - 1]);
        check(inOrder, "logs check, chestplate, re-glide plan, elytra, open, chestplate again, mace, smash, in that order");
        check(log.get(0).contains("\"tick\"") && log.get(0).contains("\"vy\"") && log.get(0).contains("\"fallDistance\"")
                && log.get(0).contains("\"slot\"") && log.get(0).contains("\"charge\""), "each step logs tick, slot, velocity, fall, and attack bar");
        check(indexOf(log, "select_axe") >= 0, "logs the axe decision");

        // Slow swaps: waits, then retries, and still lands.
        MaceSim.equipDelay = 2;
        MaceSim.Result slow = duel(p, MaceSim.dodger(), false, 50, 5);
        System.out.println("  swaps that take 2 ticks: " + slow);
        check(slow.winRate() >= 0.9, "still wins when swaps take 2 ticks to show up");
        MaceSim.equipDelay = 5;
        duel(p, MaceSim.dodger(), false, 1, 5);
        check(indexOf(last.comboLog(), "retry") >= 0, "retries a swap that has not shown up in time");
        MaceSim.equipDelay = 0;

        // Swaps that never happen: abandon, never smash while still gliding.
        // It takes off once, then every swap fails, so the combo meets a swap that never happens.
        MaceSim.startWearingElytra = true;
        MaceSim.equipFails = true;
        MaceSim.Result broken = duel(p, MaceSim.chaser(), false, 20, 6);
        List<String> blog = last.comboLog();
        System.out.println("  swaps that never happen: " + broken);
        check(indexOf(blog, "chestplate swap was not confirmed") >= 0, "abandons the combo when the chestplate swap never shows up");
        check(indexOf(blog, "select_mace") < 0, "never tries the smash when the swap failed");
        System.out.println("  steps used when swaps fail: " + broken.techniques);
        check(broken.techniques.getOrDefault("land_to_switch_style", 0) > 0 && broken.techniques.getOrDefault("hit", 0) > 0,
                "lands and switches to the sword style when chest swaps keep failing");
        MaceSim.equipFails = false;
        MaceSim.startWearingElytra = false;

        // Glides that never open: equipping the elytra is not taken as gliding.
        MaceSim.glideFails = true;
        duel(p, MaceSim.dodger(), false, 3, 3);
        List<String> glog = last.comboLog();
        check(indexOf(glog, "glide did not start") >= 0 || indexOf(glog, "plan_regrip") < 0,
                "a glide that never opens is caught instead of assumed");
        check(indexOf(glog, "open_elytra", "confirmed") < 0, "never logs a glide as confirmed when it did not open");
        MaceSim.glideFails = false;

        // Swaps that work are never counted as failures, so the bot keeps elytra macing.
        MaceSim.Result keep = duel(p, MaceSim.dodger(), false, 100, 8);
        check(keep.techniques.getOrDefault("land_to_switch_style", 0) == 0, "working swaps never make it give up on elytra mace");

        // Follow-up on a target that goes up in the air.
        MaceSim.enemyWind = true;
        MaceSim.Result esc = duel(p, MaceSim.windEscaper(), false, 100, 7);
        BotProfile noFollow = new BotProfile(); noFollow.followUp = 0;
        MaceSim.Result escNo = duel(noFollow, MaceSim.windEscaper(), false, 100, 7);
        System.out.println("  vs wind charge escaper with follow-ups: " + esc);
        System.out.println("  same without follow-ups: " + escNo);
        boolean followed = false;
        for (int seed = 1; seed < 30 && !followed; seed++) {
            duel(p, MaceSim.windEscaper(), false, 1, seed);
            followed = indexOf(last.comboLog(), "follow_up_requirements", "ok") >= 0 && indexOf(last.comboLog(), "F_ROCKET", "confirmed") >= 0;
        }
        MaceSim.enemyWind = false;
        check(esc.winRate() >= 0.9, "beats a player who escapes upward with wind charges");
        check(followed, "follows a target in the air with elytra, rocket, chestplate, then the smash");

        // On the ground next to the target, it takes the elytra off and fights on foot.
        MacePolicy mp = new MacePolicy(p, true);
        Loadout wearing = kit("elytra", "diamond_chestplate");
        BotAction foot = mp.step(obs(100, wearing, 0, 0, true, false, 0, 2, 1, false));
        check("chestplate".equals(foot.equip) && foot.state.equals("ELYTRA_ON_FOOT"), "puts the chestplate on to fight on foot up close");

        // Totems and potions do not wait for the style to be idle.
        Loadout popped = Loadout.of("diamond_chestplate", "air", "mace", "diamond_sword", "diamond_axe", "firework_rocket*32", "elytra",
                "splash_potion:strong_healing", "air", "air", "totem_of_undying*3");
        BotAction tot = new StyleBrain(p, null).step(obs(5, popped, 20, -0.5, false, false, 3, 2, 2, false));
        check(tot.offhandTotem, "puts a totem in the offhand even mid-air, from the main inventory");
        Observation hurt = new Observation(6, true, 0, 0, 0, 0, 0, 0, 0, 0, true, false, false, 5, 0, "diamond_sword", 1.0, 1, -1, 0,
                3, 0, 3, 0, 0, 0, 180, true, false, false, false, 20, 0, "diamond_sword", true, 0,
                false, 2, -1, 0, 0, 0, 0.5, false, 0, 0, popped, 0, false, true);
        BotAction heal = new StyleBrain(p, null).step(hurt);
        check(heal.useOnce && heal.technique.equals("pot_heal"), "the elytra style still splashes healing when low on the ground");

        // A player hiding under a roof: no mace drops, sword fighting on foot instead.
        MaceSim.roof = true;
        BotProfile blind = new BotProfile(); blind.minDropHeadroom = 0;
        MaceSim.Result aware = duel(p, MaceSim.hider(), false, 100, 9);
        MaceSim.Result unaware = duel(blind, MaceSim.hider(), false, 100, 9);
        MaceSim.roof = false;
        System.out.printf("  vs player under a roof, roof aware: %s, %d hits, %d rockets%n", aware, aware.botHits, aware.rockets);
        System.out.printf("  same, not roof aware: %s, %d hits, %d rockets%n", unaware, unaware.botHits, unaware.rockets);
        // Hits knock the hider out from under the roof now and then. A drop then is fine, and the roof stops any
        // drop while they are under it, so what counts is that it wins instead of wasting takeoffs.
        // Over six seeds it wins about 43 in 100 against the hider, from 11 before the roof memory.
        check(aware.wins >= 35, "wins at least a third of fights against a player who hides under a roof");
        check(aware.botHits > 500 && aware.techniques.getOrDefault("hit_out", 0) > 0, "fights them on foot with the sword instead");
        check(aware.wins > unaware.wins + 30, "does far better than ignoring the roof");
        Observation coveredObs = new Observation(1, true, 0, 0, 0, 0, 0, 0, 0, 0, true, false, false, 20, 0, "diamond_sword", 1.0, 1, -1, 0,
                0, 0, 2.5, 0, 0, 0, 180, true, false, false, false, 20, 0, "diamond_sword", true, 0,
                false, 2, -1, 0, 0, 0, 0.5, false, 0, 0, kit("elytra", "diamond_chestplate"), 0, false, true, 24, 1.2);
        BotAction under = new MacePolicy(p, true).step(coveredObs);
        check(under.state.equals("COVERED_ON_FOOT") && !under.startGlide && !under.useOnce && under.hotbar != 0 && "chestplate".equals(under.equip),
                "sees a roof over the target, puts the chestplate on, and does not take off or pull out the mace");

        // Faster follow-up: starts from the ground when the target is up high.
        MacePolicy fp = new MacePolicy(p, true);
        Loadout ground = kit("diamond_chestplate", "elytra");
        BotAction jumpUp = null;
        for (int t = 0; t < 1; t++) jumpUp = fp.step(new Observation(t, true, 0, 0, 0, 0, 0, 0, 0, 0, true, false, false, 20, 0, "diamond_sword", 1.0, 1, -1, 0,
                1, 3, 2, 0, 0.6, 0, 180, false, false, false, false, 20, 0, "diamond_sword", true, 0,
                false, 2, -1, 0, 0, 0, 0.5, false, 0, 0, ground, 0, false, true, 24, 24));
        check(jumpUp.jump && "elytra".equals(jumpUp.equip) && jumpUp.technique.equals("follow_jump"), "jumps with the elytra going on when the target is up in the air");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
