import dev.reality.fightbot.core.*;

/** Reach and aim use the real poses: eye height and hitbox change with sneaking, gliding, and swimming. */
public class PoseTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static Observation obs(double ox, double oy, double oz, Loadout l) {
        return new Observation(0, true, 0.5, 0, 0.5, 0, 0, 0, 0, 0, true, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, ox, oy, oz, 0, 0, 0, 0, true, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, -1, -1, 0, 0, 0, 0.5, false, 0, 0, l, 0, false, true, 10, 10);
    }

    static Loadout pose(double eye, double oHeight, double oWidth, double reach) {
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword");
        l.eyeHeight = eye;
        l.oHeight = oHeight;
        l.oWidth = oWidth;
        l.entityReach = reach;
        return l;
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        ActionValidator v = new ActionValidator(p);

        // Straight ahead, eyes level with the box: reach is the gap to the front face.
        Observation level = obs(0.5, 0, 3.6, pose(1.62, 1.8, 0.6, 3.0));
        System.out.printf("  standing 3.1 apart: reach %.2f%n", level.reachToTarget());
        check(Math.abs(level.reachToTarget() - 2.8) < 1e-6, "reach is measured to the front of the box, 3.1 minus half the 0.6 width");

        // A target standing 3.4 blocks above the bot's feet: standing eyes at 1.62 reach their feet at 1.78.
        Observation above = obs(0.5, 3.4, 2.5, pose(1.62, 1.8, 0.6, 3.0));
        Observation aboveCrouched = obs(0.5, 3.4, 2.5, pose(1.27, 1.8, 0.6, 3.0));
        System.out.printf("  target 3.4 up: standing reach %.2f, sneaking reach %.2f%n", above.reachToTarget(), aboveCrouched.reachToTarget());
        check(above.reachToTarget() < aboveCrouched.reachToTarget(), "sneaking lowers the eyes, so a target above is farther");

        // A crouching target 2.2 blocks below the eyes' level: a shorter box is farther away from above.
        Observation tall = obs(0.5, -2.5, 2.0, pose(1.62, 1.8, 0.6, 3.0));
        Observation crouch = obs(0.5, -2.5, 2.0, pose(1.62, 1.5, 0.6, 3.0));
        check(crouch.reachToTarget() > tall.reachToTarget(), "a sneaking target's shorter box is farther from above");

        // A gliding target is a 0.6 tall box.
        Observation glider = obs(0.5, 0, 3.3, pose(1.62, 0.6, 0.6, 3.0));
        System.out.printf("  gliding target 2.8 away at the bot's feet: reach %.2f%n", glider.reachToTarget());
        check(glider.reachToTarget() > 2.5 && !v.inReach(obs(0.5, -0.5, 3.6, pose(1.62, 0.6, 0.6, 3.0))), "a low gliding target needs the right box height");

        // Reach comes from the game: a server that sets reach to 2.5 is respected.
        check(v.inReach(level) && !v.inReach(obs(0.5, 0, 3.6, pose(1.62, 1.8, 0.6, 2.5))), "uses the smaller of the profile's reach and the game's");

        // Arrows start under the eye for the pose.
        Observation lowEye = obs(0.5, 0, 20.5, pose(0.4, 1.8, 0.6, 3.0));
        Observation highEye = obs(0.5, 0, 20.5, pose(1.62, 1.8, 0.6, 3.0));
        float[] lo = Ballistics.aim(lowEye, 3.0), hi = Ballistics.aim(highEye, 3.0);
        check(lo != null && hi != null && lo[1] < hi[1], "aims higher from lower eyes, as when swimming");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
