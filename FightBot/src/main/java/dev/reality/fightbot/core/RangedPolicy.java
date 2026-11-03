package dev.reality.fightbot.core;

/**
 * Bow and crossbow, with the sword for close range. From range it draws the bow to full power and lets go
 * with the aim the arrow's flight needs, leading a moving target. A crossbow is loaded ahead of time and
 * fired as an opener, since a loaded crossbow can be carried at full speed. Up close it fights with the
 * sword style. It never shoots into a raised shield.
 */
public final class RangedPolicy implements Policy {
    private final BotProfile p;
    private final SwordPolicy melee;
    private final java.util.Random rng;
    private int drawTicks;
    private int loadTicks;
    private int strafeDir = 1;
    private long nextStrafe;
    private String state = "RANGED";
    /** Counts for the round's results. */
    public int arrows, bolts;

    public RangedPolicy(BotProfile p) {
        this.p = p;
        this.melee = new SwordPolicy(p);
        this.rng = new java.util.Random(p.seed ^ 0x5EED);
    }

    @Override
    public String stateName() {
        return state;
    }

    @Override
    public BotAction step(Observation o) {
        if (!o.hasOpponent() || o.stale()) return BotAction.idle(o.yaw(), o.pitch());
        Loadout l = o.loadout();
        double dist = o.distance();
        int bow = l.slot("bow");
        int xbow = l.slot(id -> id.startsWith("crossbow"));
        boolean loaded = xbow >= 0 && l.hotbar(xbow).equals("crossbow:charged");
        boolean arrowsLeft = l.count(id -> id.equals("arrow") || id.equals("spectral_arrow") || id.startsWith("tipped_arrow")) > 0;
        String held = o.mainHand();
        boolean drawingBow = o.usingItem() && held.equals("bow");
        boolean loadingXbow = o.usingItem() && held.startsWith("crossbow");
        drawTicks = drawingBow ? drawTicks + 1 : 0;
        loadTicks = loadingXbow ? loadTicks + 1 : 0;

        boolean close = dist <= p.rangedMeleeWithin;
        boolean canSee = o.lineOfSight();
        boolean shielded = o.oBlocking();

        // Up close: the sword. A loaded crossbow still fires first when it is the opener.
        if (close) {
            if (loaded && canSee && !shielded && dist > 2.0) {
                BotAction f = fireCrossbow(o, a(o), xbow, true);
                if (f != null) return f;
            }
            state = "RANGED_MELEE";
            BotAction m = melee.step(o);
            m.state = state;
            return m;
        }

        // A loaded crossbow fires when the shot is good.
        if (loaded && canSee && !shielded && dist <= p.crossbowRange) {
            BotAction a = fireCrossbow(o, a(o), xbow, false);
            if (a != null) return a;
        }

        // The bow: hold right click until full power, keep the aim on where the target will be, then let go.
        if (bow >= 0 && arrowsLeft && canSee && dist <= p.bowRange) {
            BotAction a = a(o);
            float[] aim = Ballistics.aim(o, 3.0);
            if (aim != null) {
                a.yaw = aim[0];
                a.pitch = aim[1];
            }
            a.forward = 0;
            a.strafe = strafe(o);
            a.sprint = false;
            state = "RANGED_BOW";
            if (!held.equals("bow")) {
                a.hotbar = bow;
                a.technique = "bow_out";
                return a;
            }
            boolean full = drawTicks >= p.bowDrawTicks;
            // Let go once fully drawn, with a shot that lands, and not into a raised shield.
            if (drawingBow && full && aim != null && !shielded) {
                a.use = false;
                a.exactAim = true;
                a.technique = "bow_release";
                arrows++;
                drawTicks = 0;
                return a;
            }
            a.use = true;
            a.technique = drawingBow ? "bow_draw" : "bow_start";
            return a;
        }

        // Nothing to shoot right now: load the crossbow while closing in, or walk in with the sword out.
        if (xbow >= 0 && !loaded && arrowsLeft && dist > p.rangedMeleeWithin + 2) {
            BotAction a = a(o);
            aimAtTarget(o, a);
            a.forward = 1;
            a.sprint = false;
            state = "RANGED_LOAD";
            if (!held.startsWith("crossbow")) {
                a.hotbar = xbow;
                a.technique = "crossbow_out";
                return a;
            }
            // Hold until it is charged, then let go, which is when the crossbow loads.
            if (loadingXbow && loadTicks >= p.crossbowChargeTicks + 1) {
                a.use = false;
                a.technique = "crossbow_loaded";
                return a;
            }
            a.use = true;
            a.technique = "crossbow_load";
            return a;
        }
        state = "RANGED_CHASE";
        BotAction m = melee.step(o);
        m.state = state;
        return m;
    }

    private BotAction fireCrossbow(Observation o, BotAction a, int xbow, boolean close) {
        float[] aim = Ballistics.aim(o, 3.15);
        if (aim == null) return null;
        a.yaw = aim[0];
        a.pitch = aim[1];
        a.hotbar = xbow;
        a.useOnce = true;
        a.exactAim = true;
        a.forward = 0;
        a.sprint = false;
        a.technique = "crossbow_fire";
        state = "RANGED_CROSSBOW";
        bolts++;
        return a;
    }

    /** Side steps while drawing, so it is harder to hit back. */
    private int strafe(Observation o) {
        if (o.tick() >= nextStrafe) {
            strafeDir = -strafeDir;
            nextStrafe = o.tick() + 10 + rng.nextInt(15);
        }
        return strafeDir;
    }

    private static BotAction a(Observation o) {
        return BotAction.idle(o.yaw(), o.pitch());
    }

    private static void aimAtTarget(Observation o, BotAction a) {
        a.yaw = o.yawToOpponent();
        a.pitch = o.pitchToOpponent();
    }
}
