import dev.reality.fightbot.core.*;
import java.util.*;

/** Low health against a crystal player, in the elytra mace style: get out of blast range and heal, then fight. */
public class BlastTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    static Loadout kit(String chest, boolean pearls) {
        Map<String, Integer> totals = new HashMap<>();
        totals.put("mace", 1);
        totals.put("netherite_sword", 1);
        totals.put("firework_rocket", 32);
        totals.put("golden_apple", 16);
        totals.put(chest.equals("elytra") ? "netherite_chestplate" : "elytra", 1);
        if (pearls) totals.put("ender_pearl", 8);
        String[] bar = {"netherite_sword", "mace", "firework_rocket", "golden_apple", chest.equals("elytra") ? "netherite_chestplate" : "elytra",
                pearls ? "ender_pearl" : "air", "air", "air", "air"};
        int[] counts = {1, 1, 32, 16, 1, pearls ? 8 : 0, 0, 0, 0};
        return new Loadout(bar, counts, totals, chest, "totem_of_undying");
    }

    /** Bot at the origin, opponent dist blocks south, holding the given item. */
    static Observation obs(long t, double y, boolean ground, boolean gliding, double vz, float hp, double dist, String oHeld, Loadout l) {
        return new Observation(t, true, 0.5, y, 0.5, 0, 0, vz, 0, 0, ground, false, false, hp, 0,
                "netherite_sword", 1.0, 0, 3, 16, 0.5, 0, 0.5 + dist, 0, 0, 0, 180, true, false, false, false, 20, 0,
                oHeld, true, 0, false, -1, -1, 0, 16, 0, 0.5, false, 0, 0, l, 0, gliding, true, 30, 30);
    }

    static StyleBrain brain(VoidTest.World w) {
        BotProfile p = new BotProfile();
        p.reactionTicks = 0;
        p.navigate = 0;
        StyleBrain sb = new StyleBrain(p, null);
        sb.setWorld(w);
        return sb;
    }

    static boolean awayYaw(BotAction a) {
        // The opponent is south, at yaw 0, so away is north, at 180.
        return Math.abs(Angles.diff(a.yaw, 180)) < 60;
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;
        VoidTest.World w = new VoidTest.World();
        for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) w.floor(x, z);

        // On the ground at low health, a crystal player 3 blocks off, no pearls: take off away from them.
        StyleBrain sb = brain(w);
        BotAction a = null;
        boolean tookOff = false;
        for (int t = 1; t <= 10 && !tookOff; t++) {
            a = sb.step(obs(t, 0, true, false, 0, 6, 3, "end_crystal", kit("netherite_chestplate", false)));
            tookOff = a.technique.equals("take_off_away_from_blasts") && a.jump && awayYaw(a);
        }
        check(tookOff, "takes off away from a crystal player at low health");

        // Same, with pearls: pearl away.
        sb = brain(w);
        boolean pearled = false;
        for (int t = 1; t <= 10 && !pearled; t++) {
            a = sb.step(obs(t, 0, true, false, 0, 6, 3, "end_crystal", kit("netherite_chestplate", true)));
            pearled = a.technique.equals("pearl_away_from_blasts");
        }
        check(pearled, "pearls away from a crystal player at low health when it has pearls");

        // Gliding toward them at low health: turn away instead of diving in.
        sb = brain(w);
        boolean glidedAway = false;
        for (int t = 1; t <= 80 && !glidedAway; t++) {
            a = sb.step(obs(t, 14, false, true, 0.8, 6, 8, "end_crystal", kit("elytra", false)));
            glidedAway = a.technique.startsWith("glide_away") && awayYaw(a) && !a.attack;
        }
        check(glidedAway, "glides away from a crystal player at low health instead of diving");

        // Far enough off: eat, and keep eating without a rocket or a switch until the apple is done.
        sb = brain(w);
        long ateAt = -1;
        boolean broke = false;
        for (int t = 1; t <= 60; t++) {
            a = sb.step(obs(t, 0, true, false, 0, 7, 24, "end_crystal", kit("netherite_chestplate", false)));
            if (ateAt < 0 && a.use && a.hotbar == 3) ateAt = t;
            else if (ateAt >= 0 && t < ateAt + 32 && (!a.use || (a.hotbar >= 0 && a.hotbar != 3) || a.useOnce)) broke = true;
        }
        check(ateAt >= 0 && !broke, "eats a whole apple once out of blast range");

        // Healthy again: back to the fight.
        sb = brain(w);
        sb.step(obs(1, 0, true, false, 0, 6, 24, "end_crystal", kit("netherite_chestplate", false)));
        a = sb.step(obs(2, 0, true, false, 0, 18, 24, "end_crystal", kit("netherite_chestplate", false)));
        check(!a.state.startsWith("BLAST"), "goes back to fighting once health is back up");

        // A sword player at the same health: no reason to run from blasts.
        sb = brain(w);
        boolean ran = false;
        for (int t = 1; t <= 10; t++) {
            a = sb.step(obs(t, 0, true, false, 0, 6, 3, "netherite_sword", kit("netherite_chestplate", false)));
            ran |= a.state.startsWith("BLAST");
        }
        check(!ran, "does not run from someone who has not used crystals");

        // Healthy, on foot next to a crystal player: take off again rather than fight on foot.
        sb = brain(w);
        boolean onFoot = false;
        for (int t = 1; t <= 10; t++) {
            a = sb.step(obs(t, 0, true, false, 0, 20, 2, "end_crystal", kit("netherite_chestplate", false)));
            onFoot |= "ELYTRA_ON_FOOT".equals(a.state);
        }
        check(!onFoot, "does not fight a crystal player on foot while it can fly");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
