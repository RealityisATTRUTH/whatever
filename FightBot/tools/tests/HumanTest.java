import dev.reality.fightbot.core.*;

/** The human-like layer: turn cap, clicks that wait for the aim, mouse steps, click rate, and what it costs in fights. */
public class HumanTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static Observation obs(long t, float yaw, float pitch) {
        return new Observation(t, true, 0.5, 0, 0.5, 0, 0, 0, yaw, pitch, true, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, 0.5, 0, 3.5, 0, 0, 0, 0, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, Loadout.EMPTY, 0, false, true, 10, 10);
    }

    public static void main(String[] args) {
        BotProfile p = new BotProfile();
        p.humanize = 1;

        // A pot straight down from looking ahead: 90 degrees is more than one flick, so it turns, then throws.
        Humanizer h = new Humanizer(p);
        float yaw = 0, pitch = 0;
        int threwAt = -1;
        double biggest = 0;
        for (int t = 0; t < 6 && threwAt < 0; t++) {
            BotAction a = BotAction.idle(yaw, pitch);
            a.pitch = 90;
            a.useOnce = true;
            a.hotbar = 4;
            a.technique = "pot_heal";
            h.apply(obs(t, yaw, pitch), a);
            biggest = Math.max(biggest, Math.abs(a.pitch - pitch));
            if (a.useOnce) {
                threwAt = t;
                check(a.pitch > 85 && a.hotbar == 4, "throws once the crosshair is down, from the right slot");
            }
            yaw = a.yaw;
            pitch = a.pitch;
        }
        System.out.println("  pot down: thrown on tick " + threwAt + ", biggest turn in one tick " + String.format("%.1f", biggest) + " degrees");
        check(threwAt == 1, "the pot goes one tick later, not lost");
        check(biggest <= p.maxFlickPerTick + 0.5, "never turns more than a human flick in one tick");

        // The click is kept even when the style does not ask again on the next tick.
        Humanizer h2 = new Humanizer(p);
        BotAction first = BotAction.idle(0, 0);
        first.yaw = 170;
        first.useItem = "ender_pearl";
        h2.apply(obs(0, 0, 0), first);
        check(first.useItem == null, "holds a pearl throw while turning around");
        float y2 = first.yaw;
        BotAction second = BotAction.idle(y2, 0);
        h2.apply(obs(1, y2, 0), second);
        BotAction third = BotAction.idle(second.yaw, 0);
        h2.apply(obs(2, second.yaw, 0), third);
        check("ender_pearl".equals(second.useItem) || "ender_pearl".equals(third.useItem), "throws the held pearl once turned, without being asked again");

        // A bow shot: it keeps drawing while it turns, then lets go.
        Humanizer h3 = new Humanizer(p);
        BotAction shot = BotAction.idle(0, 0);
        shot.yaw = 120;
        shot.use = false;
        shot.exactAim = true;
        h3.apply(obs(0, 0, 0), shot);
        check(shot.use, "keeps the bow drawn while it turns onto the shot");

        // Mouse steps: every turn is a whole number of steps.
        Humanizer h4 = new Humanizer(p);
        double f = p.mouseSensitivity * 0.6 + 0.2, step = f * f * f * 8 * 0.15;
        boolean whole = true;
        float yy = 10, pp = 5;
        java.util.Random rng = new java.util.Random(3);
        for (int t = 0; t < 200; t++) {
            BotAction a = BotAction.idle(yy, pp);
            a.yaw = yy + (float) (rng.nextGaussian() * 20);
            a.pitch = (float) Math.max(-80, Math.min(80, pp + rng.nextGaussian() * 10));
            h4.apply(obs(t, yy, pp), a);
            double dy = Angles.wrap(a.yaw - yy) / step, dp = (a.pitch - pp) / step;
            if (Math.abs(dy - Math.round(dy)) > 1e-3 || (Math.abs(a.pitch) < 89.9 && Math.abs(dp - Math.round(dp)) > 1e-3)) whole = false;
            yy = a.yaw;
            pp = a.pitch;
        }
        check(whole, "every turn is a whole number of mouse steps (" + String.format("%.3f", step) + " degrees each)");

        // Click rate: a style that clicks every tick gets about 16 a second.
        Humanizer h5 = new Humanizer(p);
        int clicks = 0;
        for (int t = 0; t < 200; t++) {
            BotAction a = BotAction.idle(0, 0);
            a.attack = true;
            h5.apply(obs(t, 0, 0), a);
            if (a.attack) clicks++;
        }
        System.out.println("  clicking every tick for 10 seconds: " + clicks + " clicks went through");
        check(clicks <= 170 && clicks >= 150, "caps clicks at about 16 a second");

        // What it costs: the same fights with it on and off.
        BotProfile on = new BotProfile();
        on.humanize = 1;
        BotProfile off = new BotProfile();
        off.humanize = 0;
        MaceSim.Result eOn = new MaceSim(1).run(100, s -> MaceSim.bot(on), s -> MaceSim.chaser(), MaceSim.Kit.ELYTRA, false);
        MaceSim.Result eOff = new MaceSim(1).run(100, s -> MaceSim.bot(off), s -> MaceSim.chaser(), MaceSim.Kit.ELYTRA, false);
        MaceSim.Result wOn = new MaceSim(4).run(100, s -> MaceSim.bot(on), s -> MaceSim.dodger(), MaceSim.Kit.WIND, false);
        MaceSim.Result wOff = new MaceSim(4).run(100, s -> MaceSim.bot(off), s -> MaceSim.dodger(), MaceSim.Kit.WIND, false);
        CrystalSim.Result cOn = new CrystalSim(1).run(20, s -> CrystalSim.bot(on), s -> CrystalSim.swordsman(), false);
        CrystalSim.Result cOff = new CrystalSim(1).run(20, s -> CrystalSim.bot(off), s -> CrystalSim.swordsman(), false);
        System.out.println("  elytra mace vs chaser: human-like " + eOn.wins + "/100 in " + String.format("%.1f", eOn.ticks / 100 / 20) + " s, off " + eOff.wins + "/100 in " + String.format("%.1f", eOff.ticks / 100 / 20) + " s");
        System.out.println("  wind mace vs dodger: human-like " + wOn.wins + "/100 in " + String.format("%.1f", wOn.ticks / 100 / 20) + " s, off " + wOff.wins + "/100 in " + String.format("%.1f", wOff.ticks / 100 / 20) + " s");
        System.out.println("  crystal vs sword player: human-like " + cOn.wins + "/20, dealt " + Math.round(cOn.dealt) + ", off " + cOff.wins + "/20, dealt " + Math.round(cOff.dealt));
        check(eOn.wins >= eOff.wins - 3 && wOn.wins >= wOff.wins - 3 && cOn.wins >= cOff.wins - 1, "looking human costs it almost no wins");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
