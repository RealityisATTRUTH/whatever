import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.world.WorldView;

import java.util.List;

/** Fighting in water: no rocket spam, swimming at the target, coming up for air, and getting out. */
public class WaterTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    /** A lake: water from y 0 to 9 everywhere x is below 20, dry land at y 10 beyond that. */
    static final class Lake implements WorldView {
        public Kind kind(int x, int y, int z) {
            if (y < 0) return Kind.SOLID;
            if (x >= 20 && y < 10) return Kind.SOLID;
            return y < 10 ? Kind.REPLACEABLE : Kind.AIR;
        }

        public boolean water(int x, int y, int z) {
            return x < 20 && y >= 0 && y < 10;
        }

        public int anchorCharge(int x, int y, int z) {
            return -1;
        }

        public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
            return false;
        }

        public List<Crystal> crystals() {
            return List.of();
        }

        public String dimension() {
            return "overworld";
        }

        public String difficulty() {
            return "normal";
        }

        public Armor armor(boolean self) {
            return new Armor(20, 12, 16);
        }
    }

    static Loadout elytraKit(boolean wet, boolean under, int air) {
        Loadout l = Loadout.of("elytra", "totem_of_undying", "netherite_sword", "netherite_axe", "mace", "firework_rocket*64",
                "netherite_chestplate", "golden_apple*64");
        l.inWater = wet;
        l.underWater = under;
        l.air = air;
        return l;
    }

    static Observation obs(long t, double y, double ox, double oy, double vy, Loadout l, boolean onGround) {
        return new Observation(t, true, 0.5, y, 0.5, 0, vy, 0, 0, 0, onGround, false, false, 20, 0,
                "netherite_sword", 1.0, 0, -1, 0, ox, oy, 0.5, 0, 0, 0, 0, false, false, false, false, 20, 0,
                "netherite_sword", true, 0, false, 1, -1, 0, 64, 0, 1.0, false, 0, 0, l, 0, false, true, 2, 2);
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        p.usePotions = 0;
        StyleBrain sb = new StyleBrain(p, null);
        sb.setWorld(new Lake());
        ActionValidator v = new ActionValidator(p);
        int rockets = 0, glides = 0;
        for (long t = 0; t < 200; t++) {
            Observation o = obs(t, 4, 12.5, 4, 0, elytraKit(true, true, 300), false);
            BotAction a = v.check(o, sb.step(o));
            String held = a.hotbar >= 0 ? o.loadout().hotbar(a.hotbar) : o.mainHand();
            if (a.useOnce && held.equals("firework_rocket")) rockets++;
            if (a.startGlide) glides++;
        }
        check(rockets == 0 && glides == 0, "no rockets and no gliding underwater with the elytra kit (" + rockets + " rockets, " + glides + " glides)");

        // The validator alone also stops a rocket in water, whatever asks for it.
        BotAction rocket = BotAction.idle(0, 0);
        rocket.useOnce = true;
        rocket.hotbar = 5;
        rocket.startGlide = true;
        Observation wet = obs(300, 4, 12.5, 4, 0, elytraKit(true, true, 300), false);
        v.check(wet, rocket);
        check(!rocket.useOnce && !rocket.startGlide, "the validator drops rockets and gliding in water");

        StyleBrain s2 = new StyleBrain(p, null);
        s2.setWorld(new Lake());
        BotAction far = s2.step(obs(400, 4, 12.5, 4, 0, elytraKit(true, true, 300), false));
        check(far.forward > 0 && far.sprint, "swims fast at a far target underwater");

        BotAction breath = s2.step(obs(401, 4, 12.5, 4, 0, elytraKit(true, true, 40), false));
        check("surface_for_air".equals(breath.technique) && breath.holdJump && breath.pitch < 0, "comes up for air when breath runs low");

        BotAction up = s2.step(obs(402, 2, 3.5, 6, 0, elytraKit(true, true, 300), false));
        check(up.holdJump, "holds jump to swim up to a target above");

        BotAction out = s2.step(obs(403, 8.5, 22.5, 10, 0, elytraKit(true, false, 300), false));
        check(out.holdJump && out.forward > 0 && "swim_out".equals(out.technique), "climbs out after a target on land");

        Mobility m = new Mobility(p);
        m.setWorld(new Lake());
        BotAction fall = BotAction.idle(0, 0);
        Loadout dry = Loadout.of("netherite_chestplate", "totem_of_undying", "netherite_sword", "wind_charge*16", "ender_pearl*16");
        check(!m.rescue(obs(500, 40, 60.5, 10, -2, dry, false), fall), "no fall save over deep water");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
