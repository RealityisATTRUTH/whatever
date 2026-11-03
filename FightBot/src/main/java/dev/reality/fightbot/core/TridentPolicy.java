package dev.reality.fightbot.core;

/**
 * Tridents, with the sword up close. A Loyalty trident is thrown like a slow arrow, with the same drop
 * and lead, and comes back on its own. A Riptide trident only works in water or rain: held for half a
 * second and let go, it throws the bot along its aim, and the spin hits whoever it runs into. It dashes
 * at the target from range that way, and swims with it underwater.
 */
public final class TridentPolicy implements Policy {
    private final BotProfile p;
    private final SwordPolicy melee;
    private int chargeTicks, ripTicks;
    private int strafeDir = 1;
    private long nextStrafe;
    private long countedAt = Long.MIN_VALUE, ripCountedAt = Long.MIN_VALUE;
    private String state = "TRIDENT";
    /** Counts for the round's results. */
    public int throwsMade, riptides;

    public TridentPolicy(BotProfile p) {
        this.p = p;
        this.melee = new SwordPolicy(p);
    }

    @Override
    public String stateName() {
        return state;
    }

    static int loyaltySlot(Loadout l) {
        return l.slot("trident");
    }

    static int riptideSlot(Loadout l) {
        return l.slot("trident:riptide");
    }

    @Override
    public BotAction step(Observation o) {
        if (!o.hasOpponent() || o.stale()) return BotAction.idle(o.yaw(), o.pitch());
        Loadout l = o.loadout();
        double dist = o.distance();

        // Wet: a Riptide dash at them from range.
        BotAction dash = riptide(o);
        if (dash != null) return dash;

        // Dry, or no Riptide: throw the Loyalty trident from range.
        BotAction thrown = throwLoyal(o, p.rangedMeleeWithin);
        if (thrown != null) return thrown;
        state = dist <= p.rangedMeleeWithin ? "TRIDENT_MELEE" : "TRIDENT_CHASE";
        BotAction m = melee.step(o);
        m.state = state;
        return m;
    }

    /**
     * A Loyalty trident thrown at the target from beyond minRange, or null when it does not apply. It comes
     * back on its own, so any style can throw it at someone out of reach and carry on.
     */
    public BotAction throwLoyal(Observation o, double minRange) {
        Loadout l = o.loadout();
        double dist = o.distance();
        boolean charging = o.usingItem() && o.mainHand().equals("trident");
        // Counted here, so the count is right whichever style calls this, once per tick.
        if (o.tick() != countedAt) {
            chargeTicks = charging ? chargeTicks + 1 : 0;
            countedAt = o.tick();
        }
        int loyal = loyaltySlot(l);
        if (loyal >= 0 && dist > minRange && dist <= p.tridentRange && o.lineOfSight()) {
            BotAction a = BotAction.idle(o.yaw(), o.pitch());
            float[] aim = Ballistics.aim(o, 2.5);
            if (aim != null) {
                a.yaw = aim[0];
                a.pitch = aim[1];
            }
            a.forward = 0;
            a.sprint = false;
            // Side to side while it draws, the way a player does, so it is not a still target for half a second.
            if (o.tick() >= nextStrafe) {
                strafeDir = -strafeDir;
                nextStrafe = o.tick() + 12 + (o.tick() * 7) % 9;
            }
            a.strafe = strafeDir;
            state = "TRIDENT_THROW";
            if (!o.mainHand().equals("trident")) {
                a.hotbar = loyal;
                a.technique = "trident_out";
                return a;
            }
            if (charging && chargeTicks >= p.tridentChargeTicks && aim != null && !o.oBlocking()) {
                a.use = false;
                a.exactAim = true;
                a.technique = "trident_throw";
                throwsMade++;
                chargeTicks = 0;
                return a;
            }
            a.use = true;
            a.technique = "trident_charge";
            return a;
        }
        return null;
    }

    /**
     * A Riptide dash toward the target, or null when it does not apply: no Riptide trident, not in water or
     * rain, too close or too far, or out of sight. Any style can call this, for example while swimming.
     */
    public BotAction riptide(Observation o) {
        Loadout l = o.loadout();
        int rip = riptideSlot(l);
        double dist = o.distance();
        boolean holding = o.usingItem() && o.mainHand().equals("trident:riptide");
        if (o.tick() != ripCountedAt) {
            ripTicks = holding ? ripTicks + 1 : 0;
            ripCountedAt = o.tick();
        }
        if (rip < 0 || !l.wet || dist < p.riptideMin || dist > p.riptideMax || !o.lineOfSight()) return null;
        BotAction a = BotAction.idle(o.yaw(), o.pitch());
        // At their middle where they will be when it gets there: Riptide III throws it about 3 blocks a tick, and
        // the dash goes where it looks. A little above, since it drops a bit on the way.
        double tx = o.ox(), ty = o.oy(), tz = o.oz();
        int ticks = (int) Math.ceil(dist / 3.0);
        Predictor pr = Predictor.current();
        if (pr != null) {
            double[] q = pr.at(o, ticks);
            tx = q[0];
            ty = q[1];
            tz = q[2];
        } else {
            tx += o.ovx() * ticks;
            tz += o.ovz() * ticks;
        }
        double h = Math.hypot(tx - o.x(), tz - o.z());
        a.yaw = (float) Math.toDegrees(Math.atan2(-(tx - o.x()), tz - o.z()));
        a.pitch = (float) (-Math.toDegrees(Math.atan2(ty + o.loadout().oHeight / 2 - o.eyeY(), h)) - h * 0.3);
        a.forward = 0;
        a.sprint = false;
        a.state = "TRIDENT_RIPTIDE";
        state = a.state;
        if (!o.mainHand().equals("trident:riptide")) {
            a.hotbar = rip;
            a.technique = "riptide_out";
            return a;
        }
        if (holding && ripTicks >= p.tridentChargeTicks) {
            a.use = false;
            a.exactAim = true;
            a.technique = "riptide_dash";
            riptides++;
            ripTicks = 0;
            return a;
        }
        a.use = true;
        a.technique = "riptide_charge";
        return a;
    }
}
