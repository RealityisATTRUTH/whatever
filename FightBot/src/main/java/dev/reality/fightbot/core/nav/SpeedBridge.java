package dev.reality.fightbot.core.nav;

import dev.reality.fightbot.core.BotAction;
import dev.reality.fightbot.core.Observation;
import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.place.Placer;
import dev.reality.fightbot.core.world.WorldView;

/**
 * Ninja bridging: walking backward at 45 degrees, sneak off for speed, sneak on just before the edge, and
 * a block on the side of the one underfoot as soon as the eyes are out past the edge.
 *
 * <h2>Why it works</h2>
 * A player is 0.6 wide and stays standing while any part of that width is over a block, so the middle of
 * the body can lean up to 0.3 past an edge before it falls. Sneaking stops the game from moving a player off
 * an edge at all. To click the side face of the block underfoot, the eyes have to be out past that face,
 * since a face pointing away cannot be seen. So there is a window between 0 and 0.3 blocks past the edge
 * where the side face is visible and the bot is still standing. Sneaking walks slowly into that window and
 * holds it there. Walking without sneak covers the rest of each block three times as fast.
 *
 * <h2>Tick timing</h2>
 * On the ground the game moves a player each tick by their velocity, then multiplies it by 0.546, which is
 * the block's 0.6 slipperiness times 0.91. Each tick with a movement key down adds 0.1 times the input,
 * where the input is 0.98 per key, a diagonal is scaled to length 1, and sneaking multiplies it by 0.3. So
 * the speed settles at 0.1 / (1 - 0.546), about 0.22 blocks a tick walking and 0.065 sneaking.
 *
 * The velocity the game reports is read after that friction, so the next tick's move is the reported
 * velocity plus the next input. What the bot decides now is what moves it on the next tick. So each tick it
 * works out how far past the edge the next tick's move would carry it with sneak off:
 *
 * <pre>
 *   move      = velocity + 0.1
 *   rest      = overhang now + move + move * 0.546 / (1 - 0.546)
 * </pre>
 *
 * The second term is the slide: sneaking only cuts the push from the keys, not the speed it already has,
 * which dies off by 0.546 a tick. When the rest point passes the safe limit, 0.3 less a margin, it sneaks
 * now, so it slows into the window instead of sliding through it. Once the block is confirmed, the new block is the floor, and the edge is a full
 * block further on, so it lets go of sneak and speeds up again.
 *
 * <h2>Edge detection</h2>
 * The overhang is measured along the bridge direction D, from the far edge of the block it stands on:
 * the bot's position dotted with D, minus the edge's position dotted with D. Negative means still over
 * the block. Between 0.02 and 0.3 the side face is in sight.
 *
 * <h2>Aim</h2>
 * It keeps the walking yaw, 45 degrees off straight back, and works out the pitch where that look ray meets
 * the side face: the horizontal distance to the face along the ray, and the drop from the eyes to the middle
 * of the face, give the pitch as atan(drop / distance). If the ray would meet the face outside its edges, it
 * aims straight at the face's middle instead.
 *
 * <h2>Recovery</h2>
 * Any surprise forces sneak first: a click that did not show up, a position that jumped from what the
 * movement predicted, which is a server correction, or its feet leaving the ground. While recovering it
 * steps back onto the block, waits a few ticks, and starts the cycle again.
 */
public final class SpeedBridge {
    public enum Phase { APPROACH_EDGE, UN_SHIFT, PLACE_BLOCK, RE_SHIFT, ALIGN, RECOVER }

    /** Velocity kept each tick on normal ground: 0.6 slipperiness times 0.91. */
    static final double GROUND_DRAG = 0.546;
    /** Speed added per tick by one full movement input, walking. */
    static final double WALK_ACCEL = 0.1;
    /** Sneaking multiplies the input by this. */
    static final double SNEAK_FACTOR = 0.3;
    /** Half a player's width: how far the middle can lean past an edge and still stand. */
    static final double SUPPORT = 0.3;

    private final double margin;
    private final double angle;
    private Phase phase = Phase.APPROACH_EDGE;
    private boolean sneaking;
    private long recoverUntil = -1;
    private double expectU = Double.NaN;
    private long lastTick = Long.MIN_VALUE;
    private String lastKey = "";
    /** Which hand it bridges with this run: 1 walks S plus D, -1 walks S plus A. Players pick one and keep it. */
    private int side = 1;
    private double runAngle;
    private int runDx, runDz;
    private long lastBridgeTick = -1000;
    private double yawNoise, pitchNoise;
    private boolean waitedOnce;
    private final java.util.Random rng;
    /** Counts for the round's results. */
    public int placed, recoveries, desyncs, turns;

    /**
     * @param margin how far short of the 0.3 limit it sneaks, in blocks
     * @param angle  degrees off straight back it walks, 45 for ninja bridging, 0 for plain backward
     */
    public SpeedBridge(double margin, double angle) {
        this(margin, angle, 0x5eedL);
    }

    public SpeedBridge(double margin, double angle, long seed) {
        this.margin = margin;
        this.angle = angle;
        this.runAngle = angle;
        this.rng = new java.util.Random(seed);
    }

    public Phase phase() {
        return phase;
    }

    /**
     * One tick of bridging from the block at (bx, floorY, bz) toward the next block in direction (dx, dz).
     * Fills in movement, sneak, aim and the click. Returns false when it cannot bridge from here, for example
     * when the step is not straight ahead.
     */
    public boolean step(Observation o, BotAction a, WorldView w, Placer placer, int slot, int bx, int floorY, int bz, int dx, int dz) {
        if (Math.abs(dx) + Math.abs(dz) != 1) return false;
        long t = o.tick();
        int tx = bx + dx, tz = bz + dz;
        double u = o.x() * dx + o.z() * dz;
        double edge = bx * dx + bz * dz + (dx + dz > 0 ? 1 : 0);
        double overhang = u - edge;
        double vu = o.vx() * dx + o.vz() * dz;

        // A new bridge, or a new direction: pick the hand and the exact angle for this run, the way a player
        // settles into one and keeps it, a few degrees either side of 45.
        if (dx != runDx || dz != runDz || t - lastBridgeTick > 40) {
            if (dx != runDx || dz != runDz) turns++;
            runDx = dx;
            runDz = dz;
            side = rng.nextBoolean() ? 1 : -1;
            runAngle = angle > 1 ? angle + (rng.nextDouble() - 0.5) * 10 : angle;
        }
        lastBridgeTick = t;
        // A little drift in the hands between blocks, never enough to matter.
        if (rng.nextDouble() < 0.2) {
            yawNoise = (rng.nextDouble() - 0.5) * 2;
            pitchNoise = (rng.nextDouble() - 0.5) * 3;
        }
        // Movement: walk backward at the angle, so the move points along the bridge.
        double yaw = walkYaw(dx, dz, side) + yawNoise;
        // Steer back to the middle of the bridge. A degree or two of drift, held for 30 blocks, walks it off the
        // side, so a player keeps nudging the mouse. Turning the yaw turns the move the same amount.
        double lateral = dx != 0 ? o.z() - (bz + 0.5) : o.x() - (bx + 0.5);
        yaw += steer(dx, dz, yaw, lateral);
        a.yaw = (float) yaw;
        a.forward = -1;
        a.strafe = runAngle > 1 ? -side : 0;
        a.sprint = false;
        a.jump = false;
        a.state = "NAVIGATE";

        // A position far from where last tick's move should have put it: the server moved it.
        if (t == lastTick + 1 && !Double.isNaN(expectU) && Math.abs(u - expectU) > 0.25) {
            desyncs++;
            startRecover(t);
        }
        lastTick = t;

        // Feet off the ground over a bridge is the one thing that must never get worse: sneak, and catch.
        if (!o.onGround()) {
            sneak(a, true);
            phase = Phase.RECOVER;
            a.forward = 0;
            a.strafe = 0;
            a.technique = "bridge_recover";
            catchBlock(o, a, w, placer, slot, tx, floorY, tz, dx, dz);
            expectU = Double.NaN;
            return true;
        }

        if (phase == Phase.RECOVER) {
            sneak(a, true);
            if (t < recoverUntil) {
                // Back onto the block, away from the edge, then try again.
                a.forward = overhang > -0.2 ? 1 : 0;
                a.strafe = overhang > -0.2 && runAngle > 1 ? side : 0;
                a.technique = "bridge_recover";
                expectU = Double.NaN;
                return true;
            }
            placer.forgive(lastKey);
            phase = Phase.APPROACH_EDGE;
        }

        // Turning a corner, or the first block: stand still, sneaking, and turn to face the new way first. Walking
        // while the yaw swings would push it sideways off the bridge.
        double off = Math.abs(dev.reality.fightbot.core.Angles.wrap(o.yaw() - (float) yaw));
        if (off > 20 && !w.solid(tx, floorY, tz)) {
            sneak(a, true);
            a.forward = 0;
            a.strafe = 0;
            a.pitch = (float) (restingPitch(o, floorY) + pitchNoise);
            phase = Phase.ALIGN;
            a.technique = "bridge_turn";
            expectU = Double.NaN;
            return true;
        }

        if (w.solid(tx, floorY, tz)) {
            // The block is down: the edge is now a block further on. Let go of sneak and speed up.
            if (sneaking) {
                sneak(a, false);
                phase = Phase.UN_SHIFT;
            } else {
                phase = Phase.APPROACH_EDGE;
            }
            a.pitch = (float) (restingPitch(o, floorY) + pitchNoise);
            a.technique = "speed_bridge";
            expectU = u + vu + WALK_ACCEL;
            return true;
        }

        // In the window: eyes past the face, feet still on. Click the side face of the block underfoot. Now and
        // then it clicks a tick later than it could, as a hand does, when there is room in the window for it.
        boolean inWindow = overhang > 0.02 && overhang < SUPPORT;
        if (inWindow && !placer.busy() && !waitedOnce && overhang < 0.12 && sneaking && rng.nextDouble() < 0.25) {
            waitedOnce = true;
            inWindow = false;
        }
        if (inWindow && !placer.busy()) {
            double[] aim = aimAtFace(o, w, yaw, bx, floorY, bz, dx, dz, overhang);
            if (aim != null) {
                PlaceRequest r = new PlaceRequest("block", slot, bx, floorY, bz, faceName(dx, dz), aim[2], aim[3], aim[4],
                        tx, floorY, tz, t);
                if (placer.request(w, r)) {
                    a.place = r;
                    a.hotbar = slot;
                    a.yaw = (float) aim[0];
                    a.pitch = (float) aim[1];
                    lastKey = r.key();
                    waitedOnce = false;
                    sneak(a, true);
                    phase = Phase.PLACE_BLOCK;
                    a.technique = "bridge_place";
                    placed++;
                    expectU = Double.NaN;
                    return true;
                }
            }
        }
        // A click went out and the block is not there yet: hold at the edge until it shows up or fails.
        if (placer.busy()) {
            sneak(a, true);
            hold(a, overhang, vu);
            phase = Phase.RE_SHIFT;
            a.pitch = (float) restingPitch(o, floorY);
            a.technique = "bridge_wait";
            expectU = Double.NaN;
            return true;
        }
        // The next tick's move without sneak would carry it past the limit: sneak now, before it.
        // Where it would come to rest if it walked one more tick with sneak off and then let go: the move
        // itself, then the slide, which is 0.546 of the move each tick after, about 1.2 times the move in all.
        double move = Math.max(0, vu) + WALK_ACCEL;
        double nextFree = overhang + move + move * GROUND_DRAG / (1 - GROUND_DRAG);
        // The same check with the whole hitbox in both directions, so drifting sideways off a one block bridge,
        // or over a corner, sneaks too, not only running off the end.
        boolean restSafe = restSupported(o, w, yaw, floorY);
        if (nextFree > SUPPORT - margin || !restSafe) {
            sneak(a, true);
            hold(a, overhang, vu);
            phase = Phase.RE_SHIFT;
            a.technique = "bridge_shift";
            expectU = Double.NaN;
        } else {
            // Letting go of sneak after a block went down is the un-shift. Otherwise it is still approaching.
            phase = sneaking ? Phase.UN_SHIFT : Phase.APPROACH_EDGE;
            sneak(a, false);
            a.technique = "speed_bridge";
            expectU = u + vu + WALK_ACCEL;
        }
        a.pitch = (float) (restingPitch(o, floorY) + pitchNoise);
        return true;
    }

    /** Degrees to add to the yaw so the move drifts back toward the bridge's middle line. */
    private double steer(int dx, int dz, double yaw, double lateral) {
        if (Math.abs(lateral) < 0.03) return 0;
        // Which way one degree more of yaw turns the move across the bridge.
        double[] m0 = moveDir(yaw), m1 = moveDir(yaw + 1);
        double across0 = dx != 0 ? m0[1] : m0[0], across1 = dx != 0 ? m1[1] : m1[0];
        double rate = across1 - across0;
        double want = -Math.max(-8, Math.min(8, lateral * 30));
        return Math.signum(rate) * want;
    }

    /** Ground direction of the bridging keys at this yaw. */
    private double[] moveDir(double yaw) {
        double yr = Math.toRadians(yaw);
        double f = -1, st = runAngle > 1 ? -side : 0;
        double len = Math.hypot(f, st);
        f /= len;
        st /= len;
        return new double[] {st * Math.cos(yr) - f * Math.sin(yr), f * Math.cos(yr) + st * Math.sin(yr)};
    }

    /**
     * Whether it would still be standing where it comes to rest after one more tick of walking with sneak off:
     * its velocity and the push from the keys, then the slide, with its width shrunk by the margin.
     */
    private boolean restSupported(Observation o, WorldView w, double yaw, int floorY) {
        double yr = Math.toRadians(yaw);
        double f = -1, st = runAngle > 1 ? -side : 0;
        double len = Math.hypot(f, st);
        f /= len;
        st /= len;
        double mx = (st * Math.cos(yr) - f * Math.sin(yr)) * WALK_ACCEL, mz = (f * Math.cos(yr) + st * Math.sin(yr)) * WALK_ACCEL;
        double rx = o.x() + (o.vx() + mx) / (1 - GROUND_DRAG), rz = o.z() + (o.vz() + mz) / (1 - GROUND_DRAG);
        return supported(w, rx, rz, floorY, SUPPORT - margin);
    }

    /** Any corner of a body this wide, centered here, over a solid block at floorY. */
    static boolean supported(WorldView w, double x, double z, int floorY, double half) {
        for (double ox : new double[] {-half, half}) for (double oz : new double[] {-half, half}) {
            if (w.solid((int) Math.floor(x + ox), floorY, (int) Math.floor(z + oz))) return true;
        }
        return false;
    }

    /**
     * Past the middle of the window, it stops pushing into the edge, and past 0.2 it eases back. Sneak would
     * hold it at 0.3 anyway, but leaning on the very edge leaves no room if the server nudges it.
     */
    private void hold(BotAction a, double overhang, double vu) {
        // With no keys down it still slides: each tick's move is 0.546 of the last, about 2.2 times its speed in all.
        double rest = overhang + Math.max(0, vu) / (1 - GROUND_DRAG);
        if (overhang > 0.2 || rest > 0.22) {
            // Leaning further than it needs: ease back toward the block, still sneaking.
            a.forward = 1;
            a.strafe = runAngle > 1 ? side : 0;
        } else if (overhang > 0.12) {
            a.forward = 0;
            a.strafe = 0;
        }
    }

    /** Called when a click did not show up in the world. */
    public void failed(long tick) {
        recoveries++;
        startRecover(tick);
    }

    private void startRecover(long t) {
        phase = Phase.RECOVER;
        recoverUntil = t + 4;
        expectU = Double.NaN;
    }

    private void sneak(BotAction a, boolean on) {
        a.sneak = on;
        sneaking = on;
    }

    /**
     * The yaw where walking backward and sideways moves along (dx, dz). With W, S, A and D the game turns the
     * input by the yaw: x = strafe cos(yaw) - forward sin(yaw), z = forward cos(yaw) + strafe sin(yaw).
     */
    double walkYaw(int dx, int dz, int side) {
        // Facing back along the bridge, then turned by the angle so that S plus a side key points along it.
        double back = Math.toDegrees(Math.atan2(dx, -dz));
        return back + runAngle * side;
    }

    /** Pitch to hold between clicks: about where the next click will be, so the aim barely moves to click. */
    private static double restingPitch(Observation o, int floorY) {
        double drop = o.eyeY() - (floorY + 0.5);
        return Math.min(85, Math.toDegrees(Math.atan2(drop, 0.25)));
    }

    /**
     * Where to look to click the side face: {yaw, pitch, x, y, z}. It keeps the walking yaw and finds the pitch
     * where that ray meets the face, or aims at the face's middle when the ray meets the face plane off its edges.
     */
    static double[] aimAtFace(Observation o, WorldView w, double yaw, int bx, int floorY, int bz, int dx, int dz, double overhang) {
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        double yr = Math.toRadians(yaw);
        double hx = -Math.sin(yr), hz = Math.cos(yr);
        // Horizontal distance along the ray to the face plane. The ray has to point back toward the face.
        double toward = -(hx * dx + hz * dz);
        if (toward > 0.2) {
            double th = overhang / toward;
            double px = ex + hx * th, pz = ez + hz * th;
            // Across the face: the block's other axis.
            double across = dx != 0 ? pz - bz : px - bx;
            double py = floorY + 0.5;
            double pitch = Math.toDegrees(Math.atan2(ey - py, Math.max(1e-4, th)));
            // Snap the hit point onto the face plane exactly.
            double fx = dx != 0 ? bx + (dx > 0 ? 1 : 0) : px, fz = dz != 0 ? bz + (dz > 0 ? 1 : 0) : pz;
            if (across > 0.05 && across < 0.95 && pitch <= 89.9 && w.clearLine(ex, ey, ez, fx + dx * 0.02, py, fz + dz * 0.02)) {
                return new double[] {yaw, pitch, fx, py, fz};
            }
        }
        // The middle of the face.
        double fx = dx != 0 ? bx + (dx > 0 ? 1 : 0) : bx + 0.5, fz = dz != 0 ? bz + (dz > 0 ? 1 : 0) : bz + 0.5;
        double fy = floorY + 0.5;
        if (!w.clearLine(ex, ey, ez, fx + dx * 0.02, fy, fz + dz * 0.02)) return null;
        double ddx = fx - ex, ddz = fz - ez;
        double ay = Math.toDegrees(Math.atan2(-ddx, ddz));
        double ap = -Math.toDegrees(Math.atan2(fy - ey, Math.hypot(ddx, ddz)));
        return new double[] {ay, ap, fx, fy, fz};
    }

    /** Off the ground over the gap: a block under its feet if the side face is still in reach. */
    private void catchBlock(Observation o, BotAction a, WorldView w, Placer placer, int slot, int tx, int floorY, int tz, int dx, int dz) {
        if (placer.busy() || w.solid(tx, floorY, tz)) return;
        Placer.Spot sp = placer.blockSpot(w, o.x(), o.eyeY(), o.z(), tx, floorY, tz);
        if (sp == null) return;
        PlaceRequest r = new PlaceRequest("block", slot, sp.cx(), sp.cy(), sp.cz(), sp.face(), sp.hx(), sp.hy(), sp.hz(), tx, floorY, tz, o.tick());
        if (placer.request(w, r)) {
            a.place = r;
            a.hotbar = slot;
            a.yaw = Placer.yawTo(o.x(), o.z(), sp.hx(), sp.hz());
            a.pitch = Placer.pitchTo(o.x(), o.eyeY(), o.z(), sp.hx(), sp.hy(), sp.hz());
            a.technique = "bridge_catch";
            lastKey = r.key();
        }
    }

    /** The side of the block underfoot that faces along (dx, dz), as the placer names faces. */
    static String faceName(int dx, int dz) {
        return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
    }
}
