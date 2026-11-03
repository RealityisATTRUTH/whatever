package dev.reality.fightbot.core.nav;

import dev.reality.fightbot.core.world.WorldView;
import dev.reality.fightbot.core.world.WorldView.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Finds a way to the target over real blocks, the way a player would go: walking straight or diagonally,
 * stepping up one block, dropping down, sprint jumping over gaps, bridging over a drop with blocks, opening
 * doors and gates, mining through blocks with the right tool, or blowing a wall open with a crystal. It
 * keeps out of lava and fire and goes around cobwebs. Costs are in ticks, so it picks whatever gets there
 * soonest.
 */
public final class Pathfinder {
    private Pathfinder() {
    }

    /** What the bot carries for getting through walls. */
    public record Tools(Map<String, Double> speeds, boolean canBlast, boolean canBuild, boolean canWind) {
        public Tools(Map<String, Double> speeds, boolean canBlast) {
            this(speeds, canBlast, false, false);
        }

        public Tools(Map<String, Double> speeds, boolean canBlast, boolean canBuild) {
            this(speeds, canBlast, canBuild, false);
        }

        double speed(String tool) {
            return speeds.getOrDefault(tool, 1.0);
        }

        boolean has(String tool) {
            return speeds.containsKey(tool);
        }
    }

    /** One cell to stand in next, and what has to happen first to get into it. */
    /**
     * One cell to stand in next, and what has to happen first to get into it. bridge means a block goes under
     * the cell first. gap is how many empty blocks a sprint jump crosses to land in it, or 0.
     */
    public record Step(int x, int y, int z, List<int[]> mine, int[] door, boolean blast, boolean pillar, boolean wind,
                       boolean bridge, int gap) {
        public Step(int x, int y, int z, List<int[]> mine, int[] door, boolean blast) {
            this(x, y, z, mine, door, blast, false, false, false, 0);
        }

        public Step(int x, int y, int z, List<int[]> mine, int[] door, boolean blast, boolean pillar) {
            this(x, y, z, mine, door, blast, pillar, false, false, 0);
        }

        public Step(int x, int y, int z, List<int[]> mine, int[] door, boolean blast, boolean pillar, boolean wind) {
            this(x, y, z, mine, door, blast, pillar, wind, false, 0);
        }

        /** Just walking: nothing to mine, open, place, or jump over. */
        public boolean free() {
            return mine.isEmpty() && door == null && !blast && !pillar && !wind && !bridge && gap == 0;
        }
    }

    /** Highest ledge a wind charge launch gets it onto. */
    public static int windClimb = 5;

    public record Path(List<Step> steps, double cost, boolean complete, int searched) {
    }

    static final double WALK = 4.6;
    /** Blocks that take longer than this to mine get blown up instead, when it can. */
    static final double BLAST_AFTER = 80;
    static final double BLAST_COST = 90;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /** A block placed under its feet at the edge, sneaking backward, takes about this long. */
    static final double BRIDGE_COST = 12;
    /** Extra for a cobweb in the way, which slows a player to a crawl. */
    static final double WEB_COST = 60;
    /** Extra for walking on magma or past a berry bush. */
    static final double HURT_COST = 25;
    /**
     * Extra for a bridge that changes direction. Without it, a bridge toward someone off at an angle comes out
     * as a staircase that turns every block, and the bot spins round each time. With it, a bridge goes straight
     * and turns once, the way players bridge.
     */
    static final double BRIDGE_TURN_COST = 30;

    private record Cell(int x, int y, int z) {
    }

    private static final class Node implements Comparable<Node> {
        final Cell c;
        final double g, f;
        final Node parent;
        final Step step;
        /** Which way the move into this cell went: 0 to 3 for east, west, south, north, or -1. */
        final int dir;

        Node(Cell c, double g, double f, Node parent, Step step) {
            this(c, g, f, parent, step, parent == null ? -1 : dirOf(c.x - parent.c.x, c.z - parent.c.z));
        }

        Node(Cell c, double g, double f, Node parent, Step step, int dir) {
            this.c = c;
            this.g = g;
            this.f = f;
            this.parent = parent;
            this.step = step;
            this.dir = dir;
        }

        public int compareTo(Node o) {
            return Double.compare(f, o.f);
        }
    }

    /**
     * A path from the cell the bot stands in to any cell within reach of the target. When the search runs
     * out before reaching them, it returns the path to the cell that got closest.
     */
    public static Path find(WorldView w, Tools tools, int sx, int sy, int sz, double tx, double ty, double tz,
                            double reach, int maxNodes) {
        return find(w, tools, sx, sy, sz, tx, ty, tz, reach, maxNodes, 0, 0);
    }

    /**
     * The same, already heading (hx, hz): a bridge carrying on that way costs no turn, so a new plan keeps
     * the bridge it is on instead of flipping to another one that costs the same.
     */
    public static Path find(WorldView w, Tools tools, int sx, int sy, int sz, double tx, double ty, double tz,
                            double reach, int maxNodes, int hx, int hz) {
        Cell start = new Cell(sx, sy, sz);
        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<Cell, Double> best = new HashMap<>();
        Node first = new Node(start, 0, h(start, tx, ty, tz), null, null, dirOf(hx, hz));
        open.add(first);
        best.put(start, 0.0);
        Node closest = first;
        double closestH = h(start, tx, ty, tz);
        int searched = 0;
        while (!open.isEmpty() && searched < maxNodes) {
            Node n = open.poll();
            if (n.g > best.getOrDefault(n.c, Double.MAX_VALUE)) continue;
            searched++;
            if (goal(n.c, tx, ty, tz, reach)) return build(n, true, searched);
            double hn = h(n.c, tx, ty, tz);
            if (hn < closestH) {
                closestH = hn;
                closest = n;
            }
            for (Node next : neighbors(w, tools, n, sx, sy, sz, tx, ty, tz)) {
                if (next.g < best.getOrDefault(next.c, Double.MAX_VALUE)) {
                    best.put(next.c, next.g);
                    open.add(next);
                }
            }
        }
        return build(closest, false, searched);
    }

    private static boolean goal(Cell c, double tx, double ty, double tz, double reach) {
        double dx = c.x + 0.5 - tx, dz = c.z + 0.5 - tz, dy = c.y - ty;
        return Math.sqrt(dx * dx + dz * dz) <= reach && Math.abs(dy) <= 1.2;
    }

    private static double h(Cell c, double tx, double ty, double tz) {
        double dx = c.x + 0.5 - tx, dz = c.z + 0.5 - tz, dy = c.y - ty;
        return WALK * Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Path build(Node n, boolean complete, int searched) {
        List<Step> steps = new ArrayList<>();
        double cost = n.g;
        for (Node at = n; at.parent != null; at = at.parent) steps.add(at.step);
        Collections.reverse(steps);
        return new Path(steps, cost, complete, searched);
    }

    private static List<Node> neighbors(WorldView w, Tools tools, Node n, int sx, int sy, int sz, double tx, double ty, double tz) {
        List<Node> out = new ArrayList<>(6);
        Cell c = n.c;
        for (int[] d : DIRS) {
            int nx = c.x + d[0], nz = c.z + d[1];
            if (Math.abs(nx - sx) > 40 || Math.abs(nz - sz) > 40) continue;
            // Flat, or a drop: up to three blocks, or farther into water, which breaks the fall.
            if (floor(w, nx, c.y - 1, nz)) {
                add(out, w, tools, n, nx, c.y, nz, 0, tx, ty, tz);
            } else {
                boolean landed = false;
                for (int k = 1; k <= 12; k++) {
                    int fy = c.y - 1 - k;
                    if (w.lava(nx, fy + 1, nz) || w.lava(nx, fy, nz)) break;
                    if (w.water(nx, fy + 1, nz)) {
                        // Into water: no fall damage however far.
                        add(out, w, tools, n, nx, fy + 1, nz, Math.min(k, 3), tx, ty, tz);
                        landed = true;
                        break;
                    }
                    if (w.solid(nx, fy, nz)) {
                        if (k <= 3 && floor(w, nx, fy, nz)) {
                            add(out, w, tools, n, nx, c.y - k, nz, k, tx, ty, tz);
                            landed = true;
                        }
                        break;
                    }
                    if (!passable(w, nx, fy, nz)) break;
                }
                // A gap: sprint jump over it, or bridge across with a block.
                jumpGap(out, w, n, d, tx, ty, tz);
                if (tools.canBuild() && bodyClear(w, nx, c.y, nz) && !w.solid(nx, c.y - 1, nz)
                        && passable(w, nx, c.y - 1, nz)) {
                    Cell cell = new Cell(nx, c.y, nz);
                    boolean turn = n.dir >= 0 && n.dir != dirOf(d[0], d[1]);
                    double g = n.g + WALK * 2 + BRIDGE_COST + (turn ? BRIDGE_TURN_COST : 0);
                    out.add(new Node(cell, g, g + h(cell, tx, ty, tz), n, new Step(nx, c.y, nz, List.of(), null, false, false, false, true, 0)));
                }
            }
            // A one block step up: room over its own head for the jump.
            if (w.solid(nx, c.y, nz) && w.kind(nx, c.y, nz) != Kind.DOOR_CLOSED && passable(w, c.x, c.y + 2, c.z)) {
                add(out, w, tools, n, nx, c.y + 1, nz, -1, tx, ty, tz);
            }
        }
        // Diagonal: only with both side cells open, so it never clips a corner, and onto a floor at the same level.
        for (int[] d : DIAGS) {
            int nx = c.x + d[0], nz = c.z + d[1];
            if (Math.abs(nx - sx) > 40 || Math.abs(nz - sz) > 40) continue;
            if (!bodyClear(w, c.x + d[0], c.y, c.z) || !bodyClear(w, c.x, c.y, c.z + d[1]) || !bodyClear(w, nx, c.y, nz)) continue;
            if (!floor(w, nx, c.y - 1, nz)) continue;
            double cost = WALK * Math.sqrt(2) + hazard(w, nx, c.y, nz);
            Cell cell = new Cell(nx, c.y, nz);
            double g = n.g + cost;
            out.add(new Node(cell, g, g + h(cell, tx, ty, tz), n, new Step(nx, c.y, nz, List.of(), null, false)));
        }
        // A wall two or more blocks tall: a wind charge at its feet throws it up onto the ledge.
        if (tools.canWind()) {
            for (int[] d : DIRS) {
                int nx = c.x + d[0], nz = c.z + d[1];
                if (!w.solid(nx, c.y, nz) || !w.solid(nx, c.y + 1, nz)) continue;
                for (int k = 2; k <= windClimb; k++) {
                    // Open sky over the bot all the way up, so the launch is not cut short.
                    if (!passable(w, c.x, c.y + k + 1, c.z)) break;
                    if (w.solid(nx, c.y + k - 1, nz) && passable(w, nx, c.y + k, nz) && passable(w, nx, c.y + k + 1, nz)) {
                        Cell cell = new Cell(nx, c.y + k, nz);
                        double g = n.g + WALK + 14 + k;
                        out.add(new Node(cell, g, g + h(cell, tx, ty, tz), n, new Step(nx, c.y + k, nz, List.of(), null, false, false, true)));
                        break;
                    }
                }
            }
        }
        // Pillar straight up a block, digging through what is overhead, when the target is higher up.
        if (tools.canBuild() && ty > c.y + 1 && c.y < ty + 1) {
            int headY = c.y + 2;
            double cost = WALK + 8;
            List<int[]> mine = new ArrayList<>(1);
            if (!passable(w, c.x, headY, c.z)) {
                double ticks = w.kind(c.x, headY, c.z) == Kind.DOOR_CLOSED ? Double.MAX_VALUE : mineTicks(w, tools, c.x, headY, c.z);
                if (ticks == Double.MAX_VALUE) return out;
                cost += ticks;
                mine.add(new int[] {c.x, headY, c.z});
            }
            Cell cell = new Cell(c.x, c.y + 1, c.z);
            double g = n.g + cost;
            out.add(new Node(cell, g, g + h(cell, tx, ty, tz), n, new Step(c.x, c.y + 1, c.z, mine, null, false, true)));
        }
        return out;
    }

    /** Counts a closed door below as floor, since doors have a top a player stands on. */
    private static boolean stand(WorldView w, int x, int y, int z) {
        return w.kind(x, y, z) == Kind.DOOR_CLOSED;
    }

    /** Moves into x, y, z: whatever is in the two body cells is opened, mined, or blasted first. */
    private static void add(List<Node> out, WorldView w, Tools tools, Node n, int x, int y, int z, int drop,
                            double tx, double ty, double tz) {
        List<int[]> mine = new ArrayList<>(2);
        int[] door = null;
        boolean blast = false;
        double cost = WALK + (drop > 0 ? drop : drop < 0 ? 2 : 0);
        // Never into lava or fire.
        for (int dy = 0; dy < 2; dy++) if (w.lava(x, y + dy, z) || w.harmful(x, y + dy, z) && !w.solid(x, y + dy, z)) return;
        if (w.lava(x, y - 1, z)) return;
        cost += hazard(w, x, y, z);
        for (int dy = 0; dy < 2; dy++) {
            int by = y + dy;
            Kind k = w.kind(x, by, z);
            if (k == Kind.AIR || k == Kind.REPLACEABLE || k == Kind.DOOR_OPEN) continue;
            if (k == Kind.DOOR_CLOSED) {
                if (door == null) {
                    door = new int[] {x, by, z};
                    cost += 3;
                }
                continue;
            }
            double ticks = mineTicks(w, tools, x, by, z);
            if (ticks > BLAST_AFTER && tools.canBlast() && w.blastResistance(x, by, z) < 9) {
                if (!blast) cost += BLAST_COST;
                blast = true;
                mine.add(new int[] {x, by, z});
                continue;
            }
            if (ticks == Double.MAX_VALUE) return;
            cost += ticks;
            mine.add(new int[] {x, by, z});
        }
        // Stepping up also needs the cell over the bot's own head clear.
        if (drop < 0 && !passable(w, n.c.x, n.c.y + 2, n.c.z)) return;
        Cell cell = new Cell(x, y, z);
        double g = n.g + cost;
        out.add(new Node(cell, g, g + h(cell, tx, ty, tz), n, new Step(x, y, z, mine, door, blast)));
    }

    /** Something to stand on that does not hurt: a full block or a shut door's top. Magma counts, at a cost. */
    static boolean floor(WorldView w, int x, int y, int z) {
        if (w.lava(x, y, z)) return false;
        return w.solid(x, y, z) || stand(w, x, y, z);
    }

    /** Room for the body: two open cells with no lava or fire in them. */
    static boolean bodyClear(WorldView w, int x, int y, int z) {
        for (int dy = 0; dy < 2; dy++) {
            if (!passable(w, x, y + dy, z) || w.lava(x, y + dy, z) || w.harmful(x, y + dy, z)) return false;
        }
        return true;
    }

    /** Extra ticks for standing in this cell: cobwebs, water, and magma or thorns. */
    static double hazard(WorldView w, int x, int y, int z) {
        double c = 0;
        if (w.web(x, y, z) || w.web(x, y + 1, z)) c += WEB_COST;
        if (w.water(x, y, z)) c += 4;
        if (w.harmful(x, y - 1, z) || w.harmful(x, y, z)) c += HURT_COST;
        return c;
    }

    /**
     * Sprint jumps over a gap of one to three blocks, landing level, one lower, or one block up over a single
     * gap. The arc needs room overhead the whole way, and every cell it crosses has no floor, or it would walk.
     */
    private static void jumpGap(List<Node> out, WorldView w, Node n, int[] d, double tx, double ty, double tz) {
        Cell c = n.c;
        if (!passable(w, c.x, c.y + 2, c.z)) return;
        for (int g = 1; g <= 3; g++) {
            int ix = c.x + d[0] * g, iz = c.z + d[1] * g;
            // The crossed cell: open for the body and the top of the arc, with nothing to stand on.
            if (!bodyClear(w, ix, c.y, iz) || !passable(w, ix, c.y + 2, iz)) return;
            if (floor(w, ix, c.y - 1, iz)) return;
            int lx = c.x + d[0] * (g + 1), lz = c.z + d[1] * (g + 1);
            double cost = WALK * (g + 1) + 4 + g;
            if (floor(w, lx, c.y - 1, lz) && bodyClear(w, lx, c.y, lz)) {
                addGap(out, n, lx, c.y, lz, g, cost + hazard(w, lx, c.y, lz), tx, ty, tz);
            } else if (!w.solid(lx, c.y - 1, lz) && floor(w, lx, c.y - 2, lz) && bodyClear(w, lx, c.y - 1, lz)
                    && passable(w, lx, c.y + 1, lz)) {
                addGap(out, n, lx, c.y - 1, lz, g, cost + 1, tx, ty, tz);
            } else if (g == 1 && floor(w, lx, c.y, lz) && bodyClear(w, lx, c.y + 1, lz) && passable(w, ix, c.y + 3, iz)) {
                addGap(out, n, lx, c.y + 1, lz, g, cost + 3, tx, ty, tz);
            }
        }
    }

    private static void addGap(List<Node> out, Node n, int x, int y, int z, int gap, double cost, double tx, double ty, double tz) {
        Cell cell = new Cell(x, y, z);
        double g = n.g + cost;
        out.add(new Node(cell, g, g + h(cell, tx, ty, tz), n, new Step(x, y, z, List.of(), null, false, false, false, false, gap)));
    }

    /** 0 to 3 for east, west, south, north, or -1 for anything else. Gap jumps count by their direction. */
    static int dirOf(int dx, int dz) {
        if (dz == 0 && dx != 0) return dx > 0 ? 0 : 1;
        if (dx == 0 && dz != 0) return dz > 0 ? 2 : 3;
        return -1;
    }

    public static boolean passable(WorldView w, int x, int y, int z) {
        Kind k = w.kind(x, y, z);
        return k == Kind.AIR || k == Kind.REPLACEABLE || k == Kind.DOOR_OPEN;
    }

    /**
     * Ticks to mine the block, the way the game counts it: tool speed over hardness, divided by 30 with
     * the right tool or by 100 without it. Unbreakable blocks cost the most there is.
     */
    public static double mineTicks(WorldView w, Tools tools, int x, int y, int z) {
        float hard = w.hardness(x, y, z);
        if (hard < 0) return Double.MAX_VALUE;
        if (hard == 0) return 1;
        String tool = w.toolFor(x, y, z);
        // Most blocks that want an axe, shovel, or hoe still break at the normal speed by hand. Pickaxe blocks do not.
        boolean right = tool.equals("any") || tools.has(tool) || !tool.equals("pickaxe");
        double speed = tool.equals("any") ? 1 : tools.speed(tool);
        double perTick = speed / hard / (right ? 30 : 100);
        double ticks = Math.ceil(1 / perTick) + 1;
        return ticks > 600 ? Double.MAX_VALUE : ticks;
    }

    /** Tool speeds for what the bot carries, assuming Efficiency V on the kit's netherite and diamond tools. */
    public static Tools tools(dev.reality.fightbot.core.Loadout l, boolean canBlast) {
        Tools t = toolsOnly(l, canBlast);
        return new Tools(t.speeds(), canBlast, dev.reality.fightbot.core.Mobility.buildBlock(l) != null, l.has("wind_charge"));
    }

    private static Tools toolsOnly(dev.reality.fightbot.core.Loadout l, boolean canBlast) {
        Map<String, Double> speeds = new HashMap<>();
        for (String tool : new String[] {"pickaxe", "axe", "shovel", "hoe"}) {
            String id = bestTool(l, tool);
            if (id == null) continue;
            double base = id.startsWith("netherite") ? 9 : id.startsWith("diamond") ? 8 : id.startsWith("iron") ? 6
                    : id.startsWith("golden") ? 12 : id.startsWith("stone") ? 4 : 2;
            if (id.startsWith("netherite") || id.startsWith("diamond")) base += 26;
            speeds.put(tool, base);
        }
        return new Tools(speeds, canBlast);
    }

    /** The best tool of that kind anywhere in the inventory, or null. */
    public static String bestTool(dev.reality.fightbot.core.Loadout l, String tool) {
        for (String m : new String[] {"netherite", "diamond", "iron", "golden", "stone", "wooden"}) {
            String id = m + "_" + tool;
            if (l.has(id)) return id;
        }
        return null;
    }
}
