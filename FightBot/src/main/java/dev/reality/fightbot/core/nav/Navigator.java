package dev.reality.fightbot.core.nav;

import dev.reality.fightbot.core.BotAction;
import dev.reality.fightbot.core.BotProfile;
import dev.reality.fightbot.core.Loadout;
import dev.reality.fightbot.core.Observation;
import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.place.Placer;
import dev.reality.fightbot.core.world.WorldView;
import dev.reality.fightbot.core.world.WorldView.Kind;

/**
 * Gets the bot to a target it cannot run straight at: around walls, up steps, through doors and gates,
 * and through walls by mining them or blowing them open with a crystal. It only takes over while the
 * straight line is blocked. Once the target is in sight and close, the fighting style has the tick.
 */
public final class Navigator {
    private enum Blast { NONE, OBSIDIAN, CRYSTAL, BACK, HIT }

    private final BotProfile p;
    private final Placer placer;
    private WorldView world;

    private Pathfinder.Path path;
    private int idx;
    /** The look-ahead point picked on the ground, kept through a jump. */
    private int aimIdx = -1;
    private Pathfinder.Path aimPath;
    private long planned = -1000;
    private double ptx, pty, ptz;
    private double bestDist = Double.MAX_VALUE;
    private long progressAt;
    private int chaseStuck;
    private double stillX, stillY, stillZ;
    private long stillSince;
    private long forceUntil = -1;
    private int pbx, pby = Integer.MIN_VALUE, pbz;
    private long noBlastUntil = -1;

    private Blast blast = Blast.NONE;
    private int bx, by, bz;
    private long blastStart;

    private String state = "";
    /** Counts for the round's results. */
    public int doorsOpened, blocksMined, blasts, plans;

    public Navigator(BotProfile p) {
        this.p = p;
        this.placer = new Placer(p.placeReach, p.placeConfirmTicks);
        this.speedBridge = new SpeedBridge(p.bridgeMargin, p.bridgeAngle);
    }

    public void setWorld(WorldView w) {
        world = w;
    }

    public String stateName() {
        return state;
    }

    public Pathfinder.Path path() {
        return path;
    }

    /** Returns true when it used the tick to get closer. */
    public boolean step(Observation o, BotAction a) {
        if (world == null || p.navigate != 1 || !o.hasOpponent()) return false;
        long t = o.tick();
        var r = placer.verify(world, t);
        if (r != null && r.ok() && r.request().what().equals("door")) doorsOpened++;
        // A bridge block that never showed up: sneak, step back, and try again.
        if (r != null && !r.ok() && r.request().what().equals("block")) speedBridge.failed(t);
        if (blast != Blast.NONE) return blastStep(o, a);
        // Not moving for two seconds while it cannot reach the target: route there whatever the straight line says.
        if (Math.abs(o.x() - stillX) + Math.abs(o.y() - stillY) + Math.abs(o.z() - stillZ) > 0.6) {
            stillX = o.x();
            stillY = o.y();
            stillZ = o.z();
            stillSince = t;
        } else if (t - stillSince > 40 && o.distance() > 3.0) {
            forceUntil = t + 80;
            stillSince = t;
            planned = -1000;
        }
        // Once it has taken over, it keeps the route until the way has been open for a while. Handing the steering
        // back and forth every tick turned the bot one way, then the other, which looked like spinning.
        boolean need = needed(o);
        if (need) needSince = t;
        boolean close = o.lineOfSight() && o.distance() <= 3.5 && Math.abs(o.oy() - o.y()) <= 1.6;
        if (t >= forceUntil && !need && (path == null || close || t - needSince > p.navHoldTicks)) {
            path = null;
            state = "";
            return false;
        }
        int cx = (int) Math.floor(o.x()), cy = (int) Math.floor(o.y() + 0.01), cz = (int) Math.floor(o.z());
        // Leaning out over an edge while sneaking: it stands on the block its feet still overlap, not the air.
        if (o.onGround() && !Pathfinder.floor(world, cx, cy - 1, cz)) {
            double bestD = Double.MAX_VALUE;
            for (double ox : new double[] {-0.3, 0.3}) for (double oz : new double[] {-0.3, 0.3}) {
                int qx = (int) Math.floor(o.x() + ox), qz = (int) Math.floor(o.z() + oz);
                if (!Pathfinder.floor(world, qx, cy - 1, qz)) continue;
                double d = Math.hypot(qx + 0.5 - o.x(), qz + 0.5 - o.z());
                if (d < bestD) {
                    bestD = d;
                    cx = qx;
                    cz = qz;
                }
            }
        }
        double[] goal = goal(o);
        boolean moved = path != null && Math.hypot(goal[0] - ptx, goal[2] - ptz) + Math.abs(goal[1] - pty) > 2;
        boolean done = path != null && idx >= path.steps().size();
        // Someone built or broke something on the way: the plan is stale now, not in a second.
        boolean broken = path != null && !done && !stillGood(path, idx);
        // Fell off a jump or a bridge: plan from where it landed.
        boolean fell = path != null && !done && o.onGround() && cy < path.steps().get(Math.min(idx, path.steps().size() - 1)).y() - 1
                && !(idx > 0 && path.steps().get(idx - 1).y() <= cy);
        if (o.onGround() && (path == null || done || moved || broken || fell || t - planned >= p.replanTicks)) {
            Pathfinder.Tools tools = Pathfinder.tools(o.loadout(), canBlast(o));
            // Bridging one way a moment ago: a new plan keeps going that way rather than flipping to an equal one.
            int hx = t - bridgeDirAt < 40 ? bridgeDx : 0, hz = t - bridgeDirAt < 40 ? bridgeDz : 0;
            path = Pathfinder.find(world, tools, cx, cy, cz, goal[0], goal[1], goal[2], p.pathReach, p.pathNodes, hx, hz);
            idx = 0;
            planned = t;
            ptx = goal[0];
            pty = goal[1];
            ptz = goal[2];
            bestDist = Double.MAX_VALUE;
            progressAt = t;
            plans++;
        }
        if (path == null || path.steps().isEmpty()) return false;
        while (idx < path.steps().size() && at(o, path.steps().get(idx), cx, cy, cz) && built(path.steps().get(idx))) {
            idx++;
            bestDist = Double.MAX_VALUE;
            progressAt = t;
        }
        // Cut a corner and already standing in a later cell: skip ahead to it.
        for (int j = Math.min(path.steps().size() - 1, idx + 8); j > idx; j--) {
            Pathfinder.Step q = path.steps().get(j);
            if (q.x() == cx && q.z() == cz && q.y() == cy && o.onGround() && built(q)) {
                idx = j + 1;
                bestDist = Double.MAX_VALUE;
                progressAt = t;
                break;
            }
        }
        if (idx >= path.steps().size()) return false;
        Pathfinder.Step s = path.steps().get(idx);
        a.state = "NAVIGATE";
        a.yaw = Placer.yawTo(o.x(), o.z(), s.x() + 0.5, s.z() + 0.5);
        a.pitch = 0;

        // Doors and gates: one click opens them.
        if (s.door() != null && world.kind(s.door()[0], s.door()[1], s.door()[2]) == Kind.DOOR_CLOSED) {
            if (clickDoor(o, a, s.door())) return true;
        }
        // Blocks in the way: mine them, or blow them open when mining would take too long.
        for (int[] b : s.mine()) {
            if (!world.solid(b[0], b[1], b[2])) continue;
            // Mining in the air is five times slower, so a pillar step clears overhead from the ground.
            if (s.pillar() && !o.onGround()) break;
            if (s.blast() && canBlast(o) && startBlast(o, cx, cy, cz, s)) return blastStep(o, a);
            if (mine(o, a, b)) return true;
            break;
        }
        if (s.pillar()) return pillar(o, a, s, cy);
        if (s.wind()) return windUp(o, a, s, cy);
        if (s.bridge() && !world.solid(s.x(), s.y() - 1, s.z())) {
            if (p.speedBridge == 1 && speedBridgeStep(o, a, s, cx, cy, cz)) return true;
            return bridge(o, a, s, cx, cy, cz);
        }
        if (s.gap() > 0) return gapJump(o, a, s, cy);
        // Look ahead: steer at the farthest cell it can walk straight to, so it runs smooth lines, not zigzags.
        // Over rough ground the line may climb a block or drop a few: a person runs straight over that and hops
        // the steps. Only checking the same level made it steer cell by cell over every bump, weaving from side
        // to side the whole way to Canaan. In the air it keeps the point it picked on the ground, since steering at
        // the very next cell mid-jump swung the heading every hop.
        Pathfinder.Step aimAt = s;
        if (s.free() && o.onGround()) {
            for (int j = idx + 1; j < Math.min(path.steps().size(), idx + p.navLookAhead + 1); j++) {
                Pathfinder.Step q = path.steps().get(j);
                if (!q.free()) break;
                boolean ok = q.y() == cy ? walkable(o.x(), o.z(), q.x() + 0.5, q.z() + 0.5, cy)
                        : roughWalkable(o.x(), o.z(), cy, q.x() + 0.5, q.z() + 0.5, q.y());
                if (!ok) break;
                aimAt = q;
            }
            aimIdx = path.steps().indexOf(aimAt);
            aimPath = path;
        } else if (!o.onGround() && aimPath == path && aimIdx >= idx && aimIdx < path.steps().size()) {
            aimAt = path.steps().get(aimIdx);
        }
        a.yaw = Placer.yawTo(o.x(), o.z(), aimAt.x() + 0.5, aimAt.z() + 0.5);
        // Eyes a little down the way it walks, the way a player watches the ground ahead, not a fixed level stare.
        a.pitch = (float) (8 + 4 * Math.sin(t * 0.07) + (s.y() < cy ? 12 : 0));
        double run = Math.hypot(aimAt.x() + 0.5 - o.x(), aimAt.z() + 0.5 - o.z());
        // Walk to the next cell, jumping up steps.
        double d = Math.hypot(s.x() + 0.5 - o.x(), s.z() + 0.5 - o.z());
        if (d < bestDist - 0.05) {
            bestDist = d;
            progressAt = t;
        } else if (t - progressAt > 30) {
            // Not getting closer: plan again next tick, and hop in case a lip is in the way.
            planned = -1000;
            a.jump = o.onGround();
        }
        a.forward = 1;
        a.sprint = s.free() && (d > 0.8 || run > 1.5);
        if (s.y() > cy && o.onGround() && d < 1.6) a.jump = true;
        if (o.onGround() && !a.jump) autoJump(world, o, a);
        a.technique = s.y() > cy ? "step_up" : "path";
        // A long straight stretch: sprint jump it, which is faster, with room overhead the whole way.
        if (p.sprintJump == 1 && o.onGround() && !a.jump && a.sprint && run >= 3.5
                && Math.abs(dev.reality.fightbot.core.Angles.wrap(a.yaw - o.yaw())) < 20 && headroom(o.x(), o.z(), aimAt.x() + 0.5, aimAt.z() + 0.5, cy)) {
            a.jump = true;
            a.technique = "path_sprint_jump";
        }
        state = "PATH";
        return true;
    }

    // ---------------------------------------------------------------- following the route

    /** A bridge step only counts as done once its block is down, even with the bot leaning out over it. */
    private boolean built(Pathfinder.Step q) {
        if (!q.bridge()) return true;
        for (int j = Math.max(0, idx); j < path.steps().size(); j++) {
            Pathfinder.Step b = path.steps().get(j);
            if (b == q) break;
            if (b.bridge() && !world.solid(b.x(), b.y() - 1, b.z())) return false;
        }
        return world.solid(q.x(), q.y() - 1, q.z());
    }

    /** The next few steps still make sense: room for the body, and a floor where it walks. */
    private boolean stillGood(Pathfinder.Path path, int from) {
        for (int j = from; j < Math.min(path.steps().size(), from + 4); j++) {
            Pathfinder.Step q = path.steps().get(j);
            if (!q.free()) return true;
            if (!Pathfinder.bodyClear(world, q.x(), q.y(), q.z())) return false;
            if (!Pathfinder.floor(world, q.x(), q.y() - 1, q.z()) && !world.water(q.x(), q.y(), q.z())) return false;
        }
        return true;
    }

    /**
     * True when it can walk in a straight line from here to there at this level: floor all the way under its
     * middle, and room for its whole width, so it does not clip a corner or step off an edge.
     */
    private boolean walkable(double x0, double z0, double x1, double z1, int y) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int n = (int) Math.ceil(len / 0.2);
        for (int i = 1; i <= n; i++) {
            double f = (double) i / n;
            double x = x0 + (x1 - x0) * f, z = z0 + (z1 - z0) * f;
            if (!Pathfinder.floor(world, (int) Math.floor(x), y - 1, (int) Math.floor(z))) return false;
            for (double ox : new double[] {-0.31, 0.31}) for (double oz : new double[] {-0.31, 0.31}) {
                if (!Pathfinder.bodyClear(world, (int) Math.floor(x + ox), y, (int) Math.floor(z + oz))) return false;
            }
            if (world.web((int) Math.floor(x), y, (int) Math.floor(z))) return false;
        }
        return true;
    }

    /**
     * True when it can run a straight line over uneven ground from here to a cell at another level: each block
     * along the way at most one higher than the last, which a jump takes, or at most three lower, with room for
     * its whole width at every height, and it ends on that cell's level.
     */
    private boolean roughWalkable(double x0, double z0, int y0, double x1, double z1, int y1) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int n = (int) Math.ceil(len / 0.2);
        int y = y0;
        for (int i = 1; i <= n; i++) {
            double f = (double) i / n;
            double x = x0 + (x1 - x0) * f, z = z0 + (z1 - z0) * f;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            int ny = Integer.MIN_VALUE;
            for (int c = y + 1; c >= y - 3; c--) {
                if (Pathfinder.floor(world, bx, c - 1, bz) && Pathfinder.bodyClear(world, bx, c, bz)) {
                    ny = c;
                    break;
                }
            }
            if (ny == Integer.MIN_VALUE) return false;
            // Going up a block needs headroom for the jump where it takes off.
            if (ny > y && !Pathfinder.passable(world, bx, ny + 1, bz)) return false;
            for (double ox : new double[] {-0.31, 0.31}) for (double oz : new double[] {-0.31, 0.31}) {
                int cx = (int) Math.floor(x + ox), cz = (int) Math.floor(z + oz);
                if (!Pathfinder.bodyClear(world, cx, Math.max(ny, y), cz)) return false;
            }
            if (world.web(bx, ny, bz) || world.lava(bx, ny, bz) || world.lava(bx, ny - 1, bz) || world.harmful(bx, ny - 1, bz)) return false;
            y = ny;
        }
        return y == y1;
    }

    /** Open space above the head along the line, for a sprint jump. */
    private boolean headroom(double x0, double z0, double x1, double z1, int y) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int n = (int) Math.ceil(len / 0.5);
        for (int i = 0; i <= n; i++) {
            double f = n == 0 ? 0 : (double) i / n;
            if (!Pathfinder.passable(world, (int) Math.floor(x0 + (x1 - x0) * f), y + 2, (int) Math.floor(z0 + (z1 - z0) * f))) return false;
        }
        return true;
    }

    private final SpeedBridge speedBridge;
    private int bridgeDx, bridgeDz;
    private long bridgeDirAt = -1000, needSince = -1000;

    public SpeedBridge speedBridge() {
        return speedBridge;
    }

    /** Ninja bridging toward a bridge step straight ahead. False when it is not straight ahead. */
    private boolean speedBridgeStep(Observation o, BotAction a, Pathfinder.Step s, int cx, int cy, int cz) {
        Loadout l = o.loadout();
        String block = dev.reality.fightbot.core.Mobility.buildBlock(l);
        if (block == null) return false;
        int slot = l.slot(block);
        if (slot < 0) {
            a.restock = block;
            a.sneak = true;
            a.forward = 0;
            return true;
        }
        int dx = s.x() - cx, dz = s.z() - cz;
        if (Math.abs(dx) + Math.abs(dz) != 1 || s.y() != cy) return false;
        if (!speedBridge.step(o, a, world, placer, slot, cx, cy - 1, cz, dx, dz)) return false;
        bridgeDx = dx;
        bridgeDz = dz;
        bridgeDirAt = o.tick();
        state = "SPEED_BRIDGE_" + speedBridge.phase();
        if (a.place != null) bridged++;
        return true;
    }

    private long backUntil = -1, commitUntil = -1;
    /** Counts for the round's results. */
    public int bridged, gapsJumped;

    /**
     * Bridging over a drop the way players do it: face away from the gap, sneak backward to the edge so it
     * cannot fall, and once its eyes are past the edge, place a block on the side of the one it stands on.
     */
    private boolean bridge(Observation o, BotAction a, Pathfinder.Step s, int cx, int cy, int cz) {
        Loadout l = o.loadout();
        String block = dev.reality.fightbot.core.Mobility.buildBlock(l);
        a.state = "NAVIGATE";
        state = "BRIDGE";
        if (block == null) {
            planned = -1000;
            return false;
        }
        int slot = l.slot(block);
        if (slot < 0) {
            a.restock = block;
            return true;
        }
        int fx = s.x(), fy = s.y() - 1, fz = s.z();
        // Back toward the gap, facing the way it came.
        a.yaw = Placer.yawTo(s.x() + 0.5, s.z() + 0.5, o.x(), o.z());
        a.pitch = 78;
        a.forward = -1;
        a.strafe = 0;
        a.sprint = false;
        a.jump = false;
        a.sneak = true;
        a.technique = "bridge_edge";
        if (!placer.busy() && o.onGround()) {
            double ex = o.x(), ey = o.eyeY(), ez = o.z();
            Placer.Spot sp = placer.blockSpot(world, ex, ey, ez, fx, fy, fz);
            if (sp != null) {
                PlaceRequest r = new PlaceRequest("block", slot, sp.cx(), sp.cy(), sp.cz(), sp.face(), sp.hx(), sp.hy(), sp.hz(), fx, fy, fz, o.tick());
                if (placer.request(world, r)) {
                    a.place = r;
                    a.hotbar = slot;
                    a.yaw = Placer.yawTo(ex, ez, sp.hx(), sp.hz());
                    a.pitch = Placer.pitchTo(ex, ey, ez, sp.hx(), sp.hy(), sp.hz());
                    a.technique = "bridge_place";
                    bridged++;
                }
            }
        }
        return true;
    }

    /**
     * A sprint jump over a gap: a run-up when it is too slow for a long one, then the jump at the very edge,
     * and steering at the landing the whole way through the air.
     */
    private boolean gapJump(Observation o, BotAction a, Pathfinder.Step s, int cy) {
        a.state = "NAVIGATE";
        state = "GAP_JUMP";
        int ddx = Integer.signum(s.x() - (int) Math.floor(o.x())), ddz = Integer.signum(s.z() - (int) Math.floor(o.z()));
        // The start cell is right before the gap, gap + 1 cells back from the landing.
        int dirx = Math.abs(ddx) >= Math.abs(ddz) ? ddx : 0, dirz = dirx == 0 ? ddz : 0;
        int sx = s.x() - dirx * (s.gap() + 1), sz = s.z() - dirz * (s.gap() + 1);
        double edge = dirx > 0 ? sx + 1 - o.x() : dirx < 0 ? o.x() - sx : dirz > 0 ? sz + 1 - o.z() : o.z() - sz;
        a.yaw = Placer.yawTo(o.x(), o.z(), s.x() + 0.5, s.z() + 0.5);
        a.pitch = 0;
        a.forward = 1;
        a.sprint = true;
        a.strafe = 0;
        if (!o.onGround()) {
            a.technique = "gap_air";
            return true;
        }
        long t = o.tick();
        // Too slow and too close for a long jump: back up two blocks for a run-up, then commit to it. The game's
        // velocity reads after friction, so a full sprint shows as about 0.16, not the 0.29 it moves.
        if (s.gap() >= 2 && t >= commitUntil && o.horizontalSpeed() < 0.12 && edge < 2.0 && backUntil < 0) backUntil = t + 20;
        if (backUntil >= 0) {
            if (edge < 2.0 && t < backUntil) {
                a.forward = -1;
                a.sprint = false;
                a.technique = "gap_backup";
                return true;
            }
            backUntil = -1;
            commitUntil = t + 40;
        }
        if (edge < 0.35) {
            a.jump = true;
            a.technique = "gap_jump";
            gapsJumped++;
        } else {
            a.technique = "gap_runup";
        }
        return true;
    }

    /** One block straight up: jump, and put a block where its feet were once it has risen past it. */
    private boolean pillar(Observation o, BotAction a, Pathfinder.Step s, int cy) {
        Loadout l = o.loadout();
        String block = dev.reality.fightbot.core.Mobility.buildBlock(l);
        a.state = "NAVIGATE";
        state = "PILLAR";
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        a.pitch = 90;
        if (block == null) {
            planned = -1000;
            return false;
        }
        int slot = l.slot(block);
        if (slot < 0) {
            a.restock = block;
            return true;
        }
        if (o.onGround()) {
            // Centre over the block first, so the new block goes under its feet.
            double dx = s.x() + 0.5 - o.x(), dz = s.z() + 0.5 - o.z();
            if (Math.hypot(dx, dz) > 0.25) {
                a.yaw = Placer.yawTo(o.x(), o.z(), s.x() + 0.5, s.z() + 0.5);
                a.pitch = 0;
                a.forward = 1;
                a.technique = "path";
                return true;
            }
            pbx = s.x();
            pby = s.y() - 1;
            pbz = s.z();
            a.jump = true;
            a.technique = "pillar_jump";
            return true;
        }
        if (pby != Integer.MIN_VALUE && o.y() >= pby + 1.05 && world.open(pbx, pby, pbz) && !placer.busy()) {
            double ex = o.x(), ey = o.eyeY(), ez = o.z();
            Placer.Spot sp = placer.blockSpot(world, ex, ey, ez, pbx, pby, pbz);
            if (sp != null) {
                PlaceRequest r = new PlaceRequest("block", slot, sp.cx(), sp.cy(), sp.cz(), sp.face(), sp.hx(), sp.hy(), sp.hz(), pbx, pby, pbz, o.tick());
                if (placer.request(world, r)) {
                    a.place = r;
                    a.yaw = Placer.yawTo(ex, ez, sp.hx(), sp.hz());
                    a.pitch = Placer.pitchTo(ex, ey, ez, sp.hx(), sp.hy(), sp.hz());
                    a.technique = "pillar_place";
                    pby = Integer.MIN_VALUE;
                }
            }
        }
        return true;
    }

    private int windTries;
    private long windAt = -1000;
    private long noWindUntil = -1;
    public int windClimbs;

    /** Up a wall with a wind charge: jump and throw it at its own feet, then steer onto the ledge on the way up. */
    private boolean windUp(Observation o, BotAction a, Pathfinder.Step s, int cy) {
        Loadout l = o.loadout();
        int wind = l.slot("wind_charge");
        a.state = "NAVIGATE";
        state = "WIND_CLIMB";
        a.yaw = Placer.yawTo(o.x(), o.z(), s.x() + 0.5, s.z() + 0.5);
        if (wind < 0 && l.has("wind_charge")) {
            a.restock = "wind_charge";
            return true;
        }
        if (wind < 0 || o.tick() < noWindUntil) {
            planned = -1000;
            return false;
        }
        if (o.onGround()) {
            long since = o.tick() - windAt;
            // The launch shows up a tick or two after the throw.
            if (since < 4) {
                a.forward = 0;
                a.technique = "wind_climb";
                return true;
            }
            if (since < 40) {
                // Came back down without making it.
                if (++windTries >= 3) {
                    noWindUntil = o.tick() + 100;
                    windTries = 0;
                    planned = -1000;
                    return false;
                }
            }
            if (!o.windReady()) {
                a.forward = 0;
                a.technique = "wind_wait";
                return true;
            }
            a.pitch = 90;
            a.hotbar = wind;
            a.useOnce = true;
            a.jump = true;
            a.forward = 0;
            a.technique = "wind_climb";
            windAt = o.tick();
            windClimbs++;
            return true;
        }
        // In the air: push toward the ledge. The wall holds it back until it is high enough to clear the top.
        a.pitch = 0;
        a.forward = 1;
        a.sprint = false;
        a.technique = "wind_climb";
        return true;
    }

    /** A line for the status command: what it is doing to get to the target. */
    public String describe() {
        if (blast != Blast.NONE) return "blasting a wall, step " + blast.name().toLowerCase();
        if (path == null) return state.isEmpty() ? "running straight at the target" : state.toLowerCase();
        int left = path.steps().size() - idx;
        String next = "";
        if (idx < path.steps().size()) {
            Pathfinder.Step s = path.steps().get(idx);
            next = s.wind() ? ", next it wind charges up a wall" : s.pillar() ? ", next it pillars up" : s.door() != null ? ", next it opens a door" : s.blast() ? ", next it blasts a wall"
                    : !s.mine().isEmpty() ? ", next it mines " + s.mine().size() + " block" + (s.mine().size() == 1 ? "" : "s") : "";
        }
        return "following a route, " + left + " steps left" + (path.complete() ? "" : " toward the closest spot it found") + next
                + ". " + plans + " routes planned, " + doorsOpened + " doors opened, " + blocksMined + " blocks mined, " + blasts + " walls blasted";
    }

    /** True when it cannot just run straight at the target. */
    private boolean needed(Observation o) {
        double dist = o.distance();
        // In sight and close, on about the same level: the fighting style can reach them.
        if (o.lineOfSight() && dist <= 3.5 && Math.abs(o.oy() - o.y()) <= 1.6) {
            chaseStuck = 0;
            return false;
        }
        // Running straight at them is being blocked even though the line looked clear.
        chaseStuck = o.onGround() && o.horizontalSpeed() < 0.05 && dist > 3.5 ? chaseStuck + 1 : 0;
        if (chaseStuck > 10) return true;
        if (path != null && idx < path.steps().size()) {
            // Keep following a plan that is underway until the way ahead is open.
            return !straight(o);
        }
        return !straight(o);
    }

    /** Clear at the feet and at the head all the way, and on about the same level. */
    private boolean straight(Observation o) {
        if (Math.abs(o.oy() - o.y()) > 1.0 && !o.lineOfSight()) return false;
        if (Math.abs(o.oy() - o.y()) > 2.5) return false;
        return world.clearLine(o.x(), o.y() + 0.3, o.z(), o.ox(), o.oy() + 0.3, o.oz())
                && world.clearLine(o.x(), o.y() + 1.5, o.z(), o.ox(), o.oy() + 1.5, o.oz())
                && safeGround(o);
    }

    /**
     * Ground all the way to the target: under every point of the straight line there is something to land on
     * within three blocks, with no lava or fire on it. A clear line over a pit or a lava pool is not straight.
     */
    private boolean safeGround(Observation o) {
        double x0 = o.x(), z0 = o.z(), x1 = o.ox(), z1 = o.oz();
        double len = Math.hypot(x1 - x0, z1 - z0);
        int n = (int) Math.ceil(len / 0.4);
        int y = (int) Math.floor(o.y() + 0.01);
        for (int i = 1; i <= n; i++) {
            double f = (double) i / n;
            int x = (int) Math.floor(x0 + (x1 - x0) * f), z = (int) Math.floor(z0 + (z1 - z0) * f);
            if (world.lava(x, y, z) || world.harmful(x, y, z) && !world.solid(x, y, z)) return false;
            boolean ground = false;
            for (int k = 1; k <= 4 && !ground; k++) {
                int fy = y - k + 1;
                if (world.lava(x, fy, z) || world.lava(x, fy - 1, z)) return false;
                if (Pathfinder.floor(world, x, fy - 1, z) || world.water(x, fy, z) || world.solid(x, fy, z)) ground = true;
            }
            if (!ground) return false;
        }
        return true;
    }

    /** Where to go: the target, or the ground under them when they are in the air. */
    private double[] goal(Observation o) {
        int gx = (int) Math.floor(o.ox()), gz = (int) Math.floor(o.oz());
        int gy = (int) Math.floor(o.oy() + 0.01);
        for (int k = 0; k < 8 && !world.solid(gx, gy - 1, gz); k++) gy--;
        return new double[] {o.ox(), gy, o.oz()};
    }

    private static boolean at(Observation o, Pathfinder.Step s, int cx, int cy, int cz) {
        if (cx != s.x() || cz != s.z() || Math.abs(cy - s.y()) > 0) return false;
        return Math.hypot(s.x() + 0.5 - o.x(), s.z() + 0.5 - o.z()) < 0.6;
    }

    private boolean canBlast(Observation o) {
        Loadout l = o.loadout();
        return p.blastWalls == 1 && o.tick() >= noBlastUntil && l.has("end_crystal") && l.has("obsidian")
                && o.health() + o.absorption() >= p.blastMinHealth && !world.dimension().isEmpty()
                && !world.difficulty().equals("peaceful");
    }

    // ---------------------------------------------------------------- doors and mining

    private boolean clickDoor(Observation o, BotAction a, int[] door) {
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        double hx = door[0] + 0.5, hy = door[1] + 0.5, hz = door[2] + 0.5;
        // The face toward the bot.
        double dx = ex - hx, dz = ez - hz;
        String face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
        if (Math.abs(dx) > Math.abs(dz)) hx += Math.signum(dx) * 0.45;
        else hz += Math.signum(dz) * 0.45;
        if (Math.sqrt((ex - hx) * (ex - hx) + (ey - hy) * (ey - hy) + (ez - hz) * (ez - hz)) > p.placeReach - 0.3) return false;
        if (placer.busy()) {
            a.forward = 0;
            a.state = "NAVIGATE";
            state = "DOOR";
            return true;
        }
        int slot = o.weaponSlot() >= 0 ? o.weaponSlot() : 0;
        PlaceRequest r = new PlaceRequest("door", slot, door[0], door[1], door[2], face, hx, hy, hz, door[0], door[1], door[2], o.tick());
        if (!placer.request(world, r)) return false;
        a.place = r;
        a.yaw = Placer.yawTo(ex, ez, hx, hz);
        a.pitch = Placer.pitchTo(ex, ey, ez, hx, hy, hz);
        a.forward = 0;
        a.sprint = false;
        a.technique = "open_door";
        state = "DOOR";
        return true;
    }

    private boolean mine(Observation o, BotAction a, int[] b) {
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        double hx = b[0] + 0.5, hy = b[1] + 0.5, hz = b[2] + 0.5;
        if (Math.sqrt((ex - hx) * (ex - hx) + (ey - hy) * (ey - hy) + (ez - hz) * (ez - hz)) > p.placeReach) return false;
        Loadout l = o.loadout();
        String kind = world.toolFor(b[0], b[1], b[2]);
        String tool = kind.equals("any") ? null : Pathfinder.bestTool(l, kind);
        int slot = tool == null ? (o.weaponSlot() >= 0 ? o.weaponSlot() : 0) : l.slot(tool);
        if (slot < 0) {
            // The tool is in the main inventory: bring it to the hotbar this tick, mine next tick.
            a.restock = tool;
            a.forward = 0;
            state = "MINE";
            return true;
        }
        a.mine = new int[] {b[0], b[1], b[2]};
        a.mineSlot = slot;
        a.yaw = Placer.yawTo(ex, ez, hx, hz);
        a.pitch = Placer.pitchTo(ex, ey, ez, hx, hy, hz);
        a.forward = 0;
        a.sprint = false;
        a.technique = "mine";
        state = "MINE";
        return true;
    }

    /** Called by the bot when a block it was mining is gone. */
    public void mined() {
        blocksMined++;
    }

    /** Jumps when a one block step is right in front, the way auto-jump does. */
    public static void autoJump(WorldView w, Observation o, BotAction a) {
        if (w == null || !o.onGround() || a.jump || (a.forward == 0 && a.strafe == 0)) return;
        double ang = Math.toRadians(a.yaw + (a.forward >= 0 ? -45 * a.strafe : 0) + (a.forward < 0 ? 180 : 0));
        if (a.forward == 0) ang = Math.toRadians(a.yaw - 90 * a.strafe);
        int fy = (int) Math.floor(o.y() + 0.01);
        int px = (int) Math.floor(o.x() - Math.sin(ang) * 0.6), pz = (int) Math.floor(o.z() + Math.cos(ang) * 0.6);
        if (px == (int) Math.floor(o.x()) && pz == (int) Math.floor(o.z())) return;
        if (!w.solid(px, fy, pz) || w.kind(px, fy, pz) == Kind.DOOR_CLOSED) return;
        if (w.solid(px, fy + 1, pz) || w.solid(px, fy + 2, pz)) return;
        if (w.solid((int) Math.floor(o.x()), fy + 2, (int) Math.floor(o.z()))) return;
        a.jump = true;
        if (a.technique.isEmpty()) a.technique = "step_up";
    }

    // ---------------------------------------------------------------- blasting a wall open

    /** A spot beside the bot for an obsidian block with a crystal on it, next to the wall. */
    private boolean startBlast(Observation o, int cx, int cy, int cz, Pathfinder.Step s) {
        int dx = s.x() - cx, dz = s.z() - cz;
        int[][] sides = {{dz, dx}, {-dz, -dx}};
        for (int[] side : sides) {
            int px = cx + side[0], pz = cz + side[1];
            if (!Pathfinder.passable(world, px, cy, pz) || !Pathfinder.passable(world, px, cy + 1, pz)
                    || !Pathfinder.passable(world, px, cy + 2, pz) || !world.solid(px, cy - 1, pz)) continue;
            bx = px;
            by = cy;
            bz = pz;
            blast = Blast.OBSIDIAN;
            blastStart = o.tick();
            return true;
        }
        return false;
    }

    private boolean blastStep(Observation o, BotAction a) {
        long t = o.tick();
        Loadout l = o.loadout();
        a.state = "NAVIGATE";
        state = "BLAST_" + blast;
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        if (t - blastStart > 120 || (o.lineOfSight() && o.distance() <= 3.5)) {
            stopBlast(t);
            return false;
        }
        switch (blast) {
            case OBSIDIAN -> {
                if (world.kind(bx, by, bz) == Kind.OBSIDIAN) {
                    blast = Blast.CRYSTAL;
                    return blastStep(o, a);
                }
                if (placer.busy()) return true;
                int slot = l.slot("obsidian");
                if (slot < 0) {
                    a.restock = "obsidian";
                    return true;
                }
                Placer.Spot sp = placer.blockSpot(world, ex, ey, ez, bx, by, bz);
                if (sp == null) {
                    stopBlast(t);
                    return false;
                }
                PlaceRequest r = new PlaceRequest("obsidian", slot, sp.cx(), sp.cy(), sp.cz(), sp.face(), sp.hx(), sp.hy(), sp.hz(), bx, by, bz, t);
                if (placer.request(world, r)) {
                    a.place = r;
                    aim(a, ex, ey, ez, sp.hx(), sp.hy(), sp.hz());
                    a.technique = "blast_obsidian";
                }
                return true;
            }
            case CRYSTAL -> {
                if (Placer.crystalAt(world, bx, by + 1, bz)) {
                    blast = Blast.BACK;
                    return blastStep(o, a);
                }
                if (placer.busy()) return true;
                int slot = l.slot("end_crystal");
                if (slot < 0) {
                    a.restock = "end_crystal";
                    return true;
                }
                Placer.Spot sp = placer.topSpot(world, ex, ey, ez, bx, by, bz, bx, by + 1, bz);
                if (sp == null || !placer.canPlaceCrystal(world, bx, by, bz)) {
                    stopBlast(t);
                    return false;
                }
                PlaceRequest r = new PlaceRequest("crystal", slot, sp.cx(), sp.cy(), sp.cz(), sp.face(), sp.hx(), sp.hy(), sp.hz(), bx, by + 1, bz, t);
                if (placer.request(world, r)) {
                    a.place = r;
                    aim(a, ex, ey, ez, sp.hx(), sp.hy(), sp.hz());
                    a.technique = "blast_crystal";
                }
                return true;
            }
            case BACK -> {
                // Step away from the crystal, staying close enough to hit it.
                WorldView.Crystal c = crystal();
                if (c == null) {
                    stopBlast(t);
                    return false;
                }
                double d = Math.sqrt((ex - c.x()) * (ex - c.x()) + (ey - c.y() - 0.5) * (ey - c.y() - 0.5) + (ez - c.z()) * (ez - c.z()));
                if (d >= p.blastBackOff || !o.onGround() && d >= 2.2) {
                    blast = Blast.HIT;
                    return blastStep(o, a);
                }
                a.yaw = Placer.yawTo(c.x(), c.z(), ex, ez);
                a.pitch = 0;
                a.forward = 1;
                a.sprint = false;
                a.technique = "blast_back";
                if (t - blastStart > 60) blast = Blast.HIT;
                return true;
            }
            case HIT -> {
                WorldView.Crystal c = crystal();
                if (c == null) {
                    blasts++;
                    blast = Blast.NONE;
                    path = null;
                    planned = -1000;
                    return false;
                }
                aim(a, ex, ey, ez, c.x(), c.y() + 0.5, c.z());
                a.attackEntity = c.id();
                a.technique = "blast_hit";
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private WorldView.Crystal crystal() {
        for (WorldView.Crystal c : world.crystals()) {
            if (Math.abs(c.x() - (bx + 0.5)) < 0.6 && Math.abs(c.z() - (bz + 0.5)) < 0.6 && Math.abs(c.y() - (by + 1)) < 0.7) return c;
        }
        return null;
    }

    private void stopBlast(long t) {
        blast = Blast.NONE;
        noBlastUntil = t + 200;
        path = null;
        planned = -1000;
    }

    private static void aim(BotAction a, double ex, double ey, double ez, double x, double y, double z) {
        a.yaw = Placer.yawTo(ex, ez, x, z);
        a.pitch = Placer.pitchTo(ex, ey, ez, x, y, z);
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
    }
}
