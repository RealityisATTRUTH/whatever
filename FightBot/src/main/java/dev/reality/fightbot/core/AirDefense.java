package dev.reality.fightbot.core;

import dev.reality.fightbot.core.world.WorldView;

/**
 * Fighting someone who is flying with an elytra, from the ground. Chasing a flyer with pearls and towers
 * only spends pearls and leaves the bot in the air, so it does neither. It stays on the ground, keeps its
 * attack bar full, and watches where the predicted flight path comes closest:
 *
 * - **Dive coming.** With a shield it raises it in time to block the dive, unless an axe is coming.
 *   Without one it sidesteps off the line late, when the flyer has the least time to correct.
 * - **Passing in reach.** It aims where they will be and hits as they pass or land, with a full bar.
 * - **Circling.** It holds its ground, strafes a little to be harder to line up, and faces their path.
 *
 * Styles that shoot, place crystals, or throw keep their own aim. They only get the sidestep.
 */
public final class AirDefense {
    private final BotProfile p;
    private WorldView world;
    private int strafeDir = 1;
    private long nextStrafe;
    private long dodgeUntil = -1;
    private int dodgeForward, dodgeStrafe;
    private String state = "";
    /** Counts for the round's results. */
    public int dodges, waits, blocks;

    public AirDefense(BotProfile p) {
        this.p = p;
    }

    public void setWorld(WorldView w) {
        world = w;
    }

    public String stateName() {
        return state;
    }

    /**
     * Changes the style's action for a flying target. Returns true when the target is flying and the action
     * was adjusted. keepAim is for styles that aim their own clicks: crystals, bows, and throws.
     */
    public boolean adjust(Observation o, BotAction a, Predictor pr, boolean keepAim, boolean keepMovement) {
        if (p.flyerWait != 1 || pr == null || !o.hasOpponent() || !pr.flying(o) || o.gliding() || !o.onGround() && o.vy() > 0.3) {
            state = "";
            return false;
        }
        Predictor.Approach ap = pr.approachBot(o, 30);
        long t = o.tick();
        boolean above = o.oy() - o.y() > 1.2 || ap.fromAbove() > 0.6;
        boolean incoming = ap.tick() >= 1 && ap.tick() <= p.dodgeLeadTicks && ap.distance() < p.dodgeWithin && above;
        // A shield stops a mace or sword dive outright, unless it is an axe coming. It takes 5 ticks to come up,
        // so it goes up early, and only comes down for a hit of its own once they are in reach.
        boolean axeComing = o.oMainHand().endsWith("_axe");
        boolean shieldIncoming = ap.tick() <= p.flyerShieldTicks && ap.distance() < 3.5 && above;
        if (o.hasShield() && !axeComing && shieldIncoming && p.useShield == 1) {
            boolean swingNow = a.attack && o.reachToTarget() <= o.loadout().entityReach;
            if (!swingNow) {
                a.block = true;
                a.attack = false;
                a.sprint = false;
                a.forward = 0;
                a.strafe = 0;
                a.jump = false;
                a.technique = "block_dive";
                if (!keepAim) aim(o, a, pr, ap);
                state = "AIR_BLOCK";
                blocks++;
                return true;
            }
        }
        if (incoming && o.onGround() && t >= dodgeUntil && !o.hasShield()) {
            // Off the line they are flying along: sideways to their path, away from where it ends.
            double hx = o.ovx(), hz = o.ovz();
            double h = Math.hypot(hx, hz);
            double ex, ez;
            if (h > 0.2) {
                ex = -hz / h;
                ez = hx / h;
                // Pick the side the bot is already on, so it moves away from the line, not across it.
                double side = (o.x() - ap.x()) * ex + (o.z() - ap.z()) * ez;
                if (side < 0) {
                    ex = -ex;
                    ez = -ez;
                }
                if (blocked(o, ex, ez)) {
                    ex = -ex;
                    ez = -ez;
                }
            } else {
                // Straight down on it: any way that is open.
                double ax = o.x() - o.ox(), az = o.z() - o.oz();
                double ah = Math.hypot(ax, az);
                ex = ah > 0.05 ? ax / ah : 1;
                ez = ah > 0.05 ? az / ah : 0;
            }
            setMove(o.yaw(), ex, ez);
            dodgeUntil = t + Math.max(3, ap.tick() + 2);
            dodges++;
        }
        if (t < dodgeUntil) {
            a.forward = dodgeForward;
            a.strafe = dodgeStrafe;
            a.sprint = dodgeForward > 0;
            a.jump = false;
            a.block = false;
            if (a.technique.isEmpty() || !a.attack) a.technique = "dodge_dive";
            state = "AIR_DODGE";
            if (!keepAim) aim(o, a, pr, ap);
            return true;
        }
        if (!keepMovement) {
            // Hold the ground and stay hard to line up.
            if (t >= nextStrafe) {
                strafeDir = -strafeDir;
                nextStrafe = t + 12 + (t * 5) % 10;
            }
            // Circling at a sprint: a moving target is harder to dive on than one standing still.
            // In the last ticks of a pass it plants its feet, so the hit or the crystal lines up.
            boolean circle = p.flyerCircle == 1 && !(ap.tick() <= p.flyerPlantTicks && ap.distance() < 4);
            a.forward = circle ? 1 : 0;
            a.strafe = strafeDir;
            a.sprint = circle;
            a.jump = false;
            if (a.technique.isEmpty() || a.technique.equals("sprint_jump") || a.technique.equals("crit_jump")) a.technique = "wait_for_flyer";
        }
        if (!keepAim) aim(o, a, pr, ap);
        // A swing at someone out of reach only empties the bar it needs for the pass.
        if (a.attack && a.attackEntity < 0 && o.reachToTarget() > o.loadout().entityReach) a.attack = false;
        state = "AIR_WAIT";
        waits++;
        return true;
    }

    private void aim(Observation o, BotAction a, Predictor pr, Predictor.Approach ap) {
        boolean clicking = a.place != null || a.attackEntity >= 0 || a.useOnce || a.useItem != null || a.use || a.mine != null;
        if (clicking) return;
        // Close: where they will be next tick. Farther: the closest point of their path, so the turn is done in time.
        int lead = o.distance() < 5 ? 1 : Math.max(1, Math.min(ap.tick(), 4));
        float[] aim = pr.aimAhead(o, lead);
        a.yaw = aim[0];
        a.pitch = aim[1];
    }

    /** Converts a wanted direction on the ground into forward and strafe keys for the bot's facing. */
    private void setMove(float yawDeg, double ex, double ez) {
        double yr = Math.toRadians(yawDeg);
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double lx = Math.cos(yr), lz = Math.sin(yr);
        double f = ex * fx + ez * fz, l = ex * lx + ez * lz;
        dodgeForward = f > 0.38 ? 1 : f < -0.38 ? -1 : 0;
        dodgeStrafe = l > 0.38 ? 1 : l < -0.38 ? -1 : 0;
        if (dodgeForward == 0 && dodgeStrafe == 0) dodgeStrafe = 1;
    }

    /** A wall or a drop two blocks that way. */
    private boolean blocked(Observation o, double ex, double ez) {
        if (world == null) return false;
        int x = (int) Math.floor(o.x() + ex * 2), y = (int) Math.floor(o.y()), z = (int) Math.floor(o.z() + ez * 2);
        return world.solid(x, y, z) || world.solid(x, y + 1, z) || !world.solid(x, y - 1, z) && !world.solid(x, y - 2, z);
    }
}
