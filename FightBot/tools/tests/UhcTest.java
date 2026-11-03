import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.world.WorldView;

import java.util.*;

/** UHC: lava at the target's feet and taking it back, cobwebs on the target and on itself, cutting out, and staying out of lava. */
public class UhcTest {
    static int fails;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static final class Field implements WorldView {
        final Set<String> lava = new HashSet<>(), web = new HashSet<>(), solid = new HashSet<>();

        public Kind kind(int x, int y, int z) {
            return y < 0 || solid.contains(x + "," + y + "," + z) ? Kind.SOLID : Kind.AIR;
        }

        public boolean lava(int x, int y, int z) {
            return lava.contains(x + "," + y + "," + z);
        }

        public boolean web(int x, int y, int z) {
            return web.contains(x + "," + y + "," + z);
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
            return new Armor(20, 12, 12);
        }
    }

    static Observation obs(long t, double x, double z, double ox, double oy, double oz, float health, double oCharge, Loadout l) {
        return new Observation(t, true, x, 0, z, 0, 0, 0, 0, 0, true, false, false, health, 0,
                "diamond_sword", 1.0, 0, -1, 0, ox, oy, oz, 0, 0, 0, 0, true, false, false, false, 20, 0,
                "diamond_sword", true, 0, false, -1, -1, 0, 0, 0, oCharge, false, 0, 0, l, 0, false, true, 10, 10);
    }

    static Loadout kit(String... extra) {
        List<String> items = new ArrayList<>(List.of("diamond_sword", "diamond_axe", "bow"));
        items.addAll(List.of(extra));
        return Loadout.of("diamond_chestplate", "shield", items.toArray(new String[0]));
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();

        // Lava at the feet of a target 3 blocks away, then back into the bucket.
        Field w = new Field();
        UhcTools u = new UhcTools(p);
        u.setWorld(w);
        BotAction pour = BotAction.idle(0, 0);
        boolean poured = u.step(obs(100, 0.5, 0.5, 0.5, 0, 3.5, 20, 0.2, kit("lava_bucket")), pour);
        check(poured && "lava_bucket".equals(pour.useItem) && pour.exactAim && pour.pitch > 20, "pours lava at the feet of a target 3 blocks away");
        w.lava.add("0,0,3");
        BotAction early = BotAction.idle(0, 0);
        u.step(obs(110, 0.5, 0.5, 0.5, 0, 3.5, 20, 0.2, kit("bucket")), early);
        check(!"lava_pickup".equals(early.technique), "leaves the lava down for a while");
        BotAction back = BotAction.idle(0, 0);
        u.step(obs(141, 0.5, 0.5, 0.5, 0, 3.5, 20, 0.2, kit("bucket")), back);
        check("lava_pickup".equals(back.technique) && "bucket".equals(back.useItem), "takes the lava back after two seconds");
        UhcTools u2 = new UhcTools(p);
        Field w2 = new Field();
        u2.setWorld(w2);
        BotAction high = BotAction.idle(0, 0);
        w2.solid.add("0,0,3");
        u2.step(obs(100, 0.5, 0.5, 0.5, 1, 3.5, 20, 0.2, kit("lava_bucket")), high);
        check(!"lava_pour".equals(high.technique), "no lava on a target standing above it, where it would run down");

        // Cobweb on the target.
        Field w3 = new Field();
        UhcTools u3 = new UhcTools(p);
        u3.setWorld(w3);
        BotAction web = BotAction.idle(0, 0);
        boolean webbed = u3.step(obs(100, 0.5, 0.5, 0.5, 0, 3.0, 20, 0.2, kit("cobweb*8")), web);
        check(webbed && web.place != null && web.place.what().equals("web") && web.place.tx() == 0 && web.place.tz() == 3,
                "puts a cobweb on the target's feet");

        // Cobweb on itself when comboed at low health.
        Field w4 = new Field();
        UhcTools u4 = new UhcTools(p);
        u4.setWorld(w4);
        BotAction self = BotAction.idle(0, 0);
        boolean sw = u4.step(obs(100, 0.5, 0.5, 0.5, 0, 1.4, 6, 0.95, kit("cobweb*8")), self);
        check(sw && "web_self".equals(self.technique) && self.place.tx() == 0 && self.place.tz() == 0, "webs itself to stop a combo at low health");

        // Stuck in a web with the target far away: cuts out with the sword.
        Field w5 = new Field();
        w5.web.add("0,0,0");
        UhcTools u5 = new UhcTools(p);
        u5.setWorld(w5);
        BotAction cut = BotAction.idle(0, 0);
        u5.step(obs(500, 0.5, 0.5, 0.5, 0, 9.5, 20, 0.2, kit("cobweb*8")), cut);
        check("cut_web".equals(cut.technique) && cut.mine != null && cut.mineSlot == 0, "cuts itself out of a web with the sword");

        // Lava ahead: stops short.
        Field w6 = new Field();
        w6.lava.add("0,0,1");
        UhcTools u6 = new UhcTools(p);
        u6.setWorld(w6);
        BotAction walk = BotAction.idle(0, 0);
        walk.forward = 1;
        walk.sprint = true;
        u6.avoidLava(obs(1, 0.5, 0.5, 0.5, 0, 6.5, 20, 0.2, kit()), walk);
        check(walk.forward == 0 && !walk.sprint, "stops before walking into lava");

        // Standing in lava: out to dry ground.
        Field w7 = new Field();
        w7.lava.add("0,0,0");
        w7.lava.add("1,0,0");
        Loadout burning = kit();
        burning.inLava = true;
        UhcTools u7 = new UhcTools(p);
        u7.setWorld(w7);
        BotAction esc = BotAction.idle(0, 0);
        u7.step(obs(1, 0.5, 0.5, 0.5, 0, 9.5, 20, 0.2, burning), esc);
        double dirX = -Math.sin(Math.toRadians(esc.yaw));
        check("escape_lava".equals(esc.technique) && esc.holdJump && esc.forward > 0 && dirX < 0.5, "climbs out of lava toward dry ground");

        // The UHC kit picks the bow and crossbow style, which fights with the sword, axe, and shield up close.
        Loadout uhc = Loadout.of("diamond_chestplate", "shield", "diamond_sword", "diamond_axe", "bow", "water_bucket", "lava_bucket",
                "cobweb*8", "golden_apple*9", "cobblestone*64", "oak_planks*64", "arrow*16");
        System.out.println("  UHC kit: " + Style.pick(uhc, p).describe());

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
