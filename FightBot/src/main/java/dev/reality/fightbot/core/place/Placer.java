package dev.reality.fightbot.core.place;

import dev.reality.fightbot.core.world.WorldView;
import dev.reality.fightbot.core.world.WorldView.Kind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The block placement layer. The combat code decides what to place and where. This class handles the
 * geometry: which spots are legal, which face to click, where to aim, reach and line of sight, and then
 * checks the world afterward to see whether it worked. It never sends a new click while the last one is
 * unresolved, and a spot that failed is skipped for a while instead of being clicked again and again.
 */
public final class Placer {
    /** A legal spot: the block to click, its face, and where the result will be. */
    public record Spot(int cx, int cy, int cz, String face, double hx, double hy, double hz, int tx, int ty, int tz) {
    }

    public record Result(PlaceRequest request, boolean ok, String reason, long tick) {
    }

    private static final int[][] FACES = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    private static final String[] FACE_NAMES = {"up", "down", "south", "north", "east", "west"};

    private final double reach;
    private final int confirmTicks;
    private PlaceRequest pending;
    private int anchorChargeBefore = -1;
    private final Map<String, Long> skipUntil = new HashMap<>();
    private final List<Result> results = new ArrayList<>();

    public Placer(double reach, int confirmTicks) {
        this.reach = reach;
        this.confirmTicks = confirmTicks;
    }

    /** Why the last spot lookup found nothing: nothing to click, out of reach, or a block in the way. */
    public String lastFail = "";

    public boolean busy() {
        return pending != null;
    }

    public PlaceRequest pending() {
        return pending;
    }

    public List<Result> results() {
        return results;
    }

    // ---------------------------------------------------------------- can_place

    /** A block can go at x, y, z: the space is free, no entity is in it, and a solid neighbor face can be clicked. */
    public boolean canPlaceBlock(WorldView w, int x, int y, int z) {
        Kind k = w.kind(x, y, z);
        if (k != Kind.AIR && k != Kind.REPLACEABLE) return false;
        if (w.entityIn(x, y, z, x + 1, y + 1, z + 1)) return false;
        for (int[] f : FACES) if (w.solid(x + f[0], y + f[1], z + f[2])) return true;
        return false;
    }

    /** An end crystal can go on the base at x, y, z: obsidian or bedrock, open above, and no entity in the way. */
    public boolean canPlaceCrystal(WorldView w, int x, int y, int z) {
        Kind k = w.kind(x, y, z);
        if (k != Kind.OBSIDIAN && k != Kind.BEDROCK) return false;
        if (w.kind(x, y + 1, z) != Kind.AIR) return false;
        return !w.entityIn(x, y + 1, z, x + 1, y + 3, z + 1);
    }

    // ---------------------------------------------------------------- aim and reach

    /** Offsets across a face, center first: a blocked center often still leaves a corner in sight. */
    private static final double[][] FACE_POINTS = {{0, 0}, {0.3, 0.3}, {-0.3, 0.3}, {0.3, -0.3}, {-0.3, -0.3},
            {0.38, 0}, {-0.38, 0}, {0, 0.38}, {0, -0.38}};

    /**
     * A point on the face with normal (nx, ny, nz) through (cx, cy, cz) that the eye can click: the face points
     * toward the eye, the point is in reach, and nothing is in the way. Null when no point works. The reason
     * for the last failure is left in lastFail.
     */
    private double[] facePoint(WorldView w, double ex, double ey, double ez, double cx, double cy, double cz,
                               int nx, int ny, int nz, int ox, int oy, int oz) {
        // A face that points away from the eye cannot be seen, so a player could not click it.
        if ((ex - cx) * nx + (ey - cy) * ny + (ez - cz) * nz <= 0.01) {
            lastFail = "the face points away";
            return null;
        }
        // The two directions across the face.
        double ux, uy = 0, uz, vx, vy, vz;
        if (ny != 0) {
            // Top or bottom: across is x and z.
            ux = 1; uz = 0; vx = 0; vy = 0; vz = 1;
        } else if (nx != 0) {
            // East or west: across is z and up.
            ux = 0; uz = 1; vx = 0; vy = 1; vz = 0;
        } else {
            // North or south: across is x and up.
            ux = 1; uz = 0; vx = 0; vy = 1; vz = 0;
        }
        boolean anyReach = false;
        for (double[] f : FACE_POINTS) {
            double hx = cx + ux * f[0] + vx * f[1], hy = cy + uy * f[0] + vy * f[1], hz = cz + uz * f[0] + vz * f[1];
            if (dist(ex, ey, ez, hx, hy, hz) > reach) continue;
            anyReach = true;
            if (lineTo(w, ex, ey, ez, hx, hy, hz, ox, oy, oz)) return new double[] {hx, hy, hz};
        }
        lastFail = anyReach ? "a block in the way" : "out of reach";
        return null;
    }

    /**
     * The best face to click for a block going at x, y, z: a solid neighbor whose face toward the new block
     * points at the eye, with some point of it in reach and in sight. Null when none works.
     */
    public Spot blockSpot(WorldView w, double ex, double ey, double ez, int x, int y, int z) {
        Spot best = null;
        double bestDist = Double.MAX_VALUE;
        boolean anySolid = false;
        String fail = "";
        for (int i = 0; i < FACES.length; i++) {
            int nx = x + FACES[i][0], ny = y + FACES[i][1], nz = z + FACES[i][2];
            if (!w.solid(nx, ny, nz)) continue;
            anySolid = true;
            // The neighbor's face that touches the new block, and its outward direction.
            double cx = x + 0.5 + FACES[i][0] * 0.5, cy = y + 0.5 + FACES[i][1] * 0.5, cz = z + 0.5 + FACES[i][2] * 0.5;
            double[] h = facePoint(w, ex, ey, ez, cx, cy, cz, -FACES[i][0], -FACES[i][1], -FACES[i][2], nx, ny, nz);
            if (h == null) {
                if (fail.isEmpty() || !lastFail.equals("the face points away")) fail = lastFail;
                continue;
            }
            double d = dist(ex, ey, ez, h[0], h[1], h[2]);
            if (d < bestDist) {
                bestDist = d;
                best = new Spot(nx, ny, nz, FACE_NAMES[i], h[0], h[1], h[2], x, y, z);
            }
        }
        if (best == null) lastFail = !anySolid ? "nothing solid next to it to click" : fail;
        return best;
    }

    /** Clicking the top of a block, for crystals on a base and for using an anchor. */
    public Spot topSpot(WorldView w, double ex, double ey, double ez, int x, int y, int z, int tx, int ty, int tz) {
        double[] h = facePoint(w, ex, ey, ez, x + 0.5, y + 1.0, z + 0.5, 0, 1, 0, x, y, z);
        if (h == null) return null;
        return new Spot(x, y, z, "up", h[0], h[1], h[2], tx, ty, tz);
    }

    /** Any visible face of the block at x, y, z within reach, for anchors where any face works. */
    public Spot anySpot(WorldView w, double ex, double ey, double ez, int x, int y, int z) {
        Spot best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < FACES.length; i++) {
            int ox = x + FACES[i][0], oy = y + FACES[i][1], oz = z + FACES[i][2];
            if (w.solid(ox, oy, oz)) continue;
            double cx = x + 0.5 + FACES[i][0] * 0.5, cy = y + 0.5 + FACES[i][1] * 0.5, cz = z + 0.5 + FACES[i][2] * 0.5;
            double[] h = facePoint(w, ex, ey, ez, cx, cy, cz, FACES[i][0], FACES[i][1], FACES[i][2], x, y, z);
            if (h == null) continue;
            double d = dist(ex, ey, ez, h[0], h[1], h[2]);
            if (d < bestDist) {
                bestDist = d;
                // The face names in FACE_NAMES describe the clicked neighbor's side, so flip for this block's own face.
                best = new Spot(x, y, z, opposite(FACE_NAMES[i]), h[0], h[1], h[2], x, y, z);
            }
        }
        return best;
    }

    private static String opposite(String f) {
        return switch (f) {
            case "up" -> "down";
            case "down" -> "up";
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** Clear sight from the eye to the point, ignoring the block that owns the face. */
    private static boolean lineTo(WorldView w, double ex, double ey, double ez, double hx, double hy, double hz, int ox, int oy, int oz) {
        // Stop just short of the face, so the block being clicked does not count as in the way.
        double dx = hx - ex, dy = hy - ey, dz = hz - ez;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.05) return true;
        double s = Math.max(0, (len - 0.05) / len);
        return w.clearLine(ex, ey, ez, ex + dx * s, ey + dy * s, ez + dz * s);
    }

    public static float yawTo(double ex, double ez, double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-(x - ex), z - ez));
    }

    public static float pitchTo(double ex, double ey, double ez, double x, double y, double z) {
        return (float) -Math.toDegrees(Math.atan2(y - ey, Math.hypot(x - ex, z - ez)));
    }

    static double dist(double ax, double ay, double az, double bx, double by, double bz) {
        return Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
    }

    // ---------------------------------------------------------------- request and verify

    public boolean skipped(String key, long tick) {
        Long until = skipUntil.get(key);
        return until != null && tick < until;
    }

    /** Lets a spot that failed be tried again right away, for a click that was retried on purpose. */
    public void forgive(String key) {
        skipUntil.remove(key);
    }

    /** Records the click as pending. Returns false while another click is unresolved or the spot is being skipped. */
    public boolean request(WorldView w, PlaceRequest r) {
        if (pending != null || skipped(r.key(), r.tick())) return false;
        pending = r;
        anchorChargeBefore = w.anchorCharge(r.tx(), r.ty(), r.tz());
        return true;
    }

    /** Checks the world for the pending click's result. Returns the result once known, otherwise null. */
    public Result verify(WorldView w, long tick) {
        if (pending == null) return null;
        PlaceRequest r = pending;
        boolean ok = switch (r.what()) {
            case "obsidian" -> w.kind(r.tx(), r.ty(), r.tz()) == Kind.OBSIDIAN;
            case "block" -> w.solid(r.tx(), r.ty(), r.tz());
            case "door" -> w.kind(r.tx(), r.ty(), r.tz()) == Kind.DOOR_OPEN;
            case "web" -> w.web(r.tx(), r.ty(), r.tz());
            case "anchor" -> w.kind(r.tx(), r.ty(), r.tz()) == Kind.ANCHOR;
            case "charge" -> w.anchorCharge(r.tx(), r.ty(), r.tz()) > Math.max(0, anchorChargeBefore);
            case "detonate" -> w.kind(r.tx(), r.ty(), r.tz()) != Kind.ANCHOR;
            case "crystal" -> crystalAt(w, r.tx(), r.ty(), r.tz());
            default -> false;
        };
        if (ok) return finish(true, "confirmed", tick);
        if (tick - r.tick() < confirmTicks) return null;
        String why = switch (r.what()) {
            case "crystal" -> w.kind(r.tx(), r.ty() - 1, r.tz()) != Kind.OBSIDIAN && w.kind(r.tx(), r.ty() - 1, r.tz()) != Kind.BEDROCK
                    ? "the base is gone" : w.kind(r.tx(), r.ty(), r.tz()) != Kind.AIR ? "the space above the base is blocked"
                    : w.entityIn(r.tx(), r.ty(), r.tz(), r.tx() + 1, r.ty() + 2, r.tz() + 1) ? "something is standing there" : "not seen";
            case "obsidian", "anchor", "block" -> w.solid(r.tx(), r.ty(), r.tz()) ? "a different block took the spot"
                    : w.entityIn(r.tx(), r.ty(), r.tz(), r.tx() + 1, r.ty() + 1, r.tz() + 1) ? "something is standing there" : "not seen";
            case "charge" -> w.kind(r.tx(), r.ty(), r.tz()) != Kind.ANCHOR ? "the anchor is gone" : "charge did not go up";
            case "door" -> "the door did not open";
            case "web" -> "the cobweb did not go down";
            default -> "anchor did not go off";
        };
        Result res = finish(false, why, tick);
        skipUntil.put(r.key(), tick + 20);
        return res;
    }

    private Result finish(boolean ok, String reason, long tick) {
        Result res = new Result(pending, ok, reason, tick);
        results.add(res);
        pending = null;
        anchorChargeBefore = -1;
        return res;
    }

    /** A crystal stands on the base below x, y, z. */
    public static boolean crystalAt(WorldView w, int x, int y, int z) {
        for (WorldView.Crystal c : w.crystals()) {
            if (Math.floor(c.x()) == x && Math.abs(c.y() - y) < 0.6 && Math.floor(c.z()) == z) return true;
        }
        return false;
    }

    /** Drops a pending click without a result, for when the fight ends or the bot dies. */
    public void cancel() {
        pending = null;
    }
}
