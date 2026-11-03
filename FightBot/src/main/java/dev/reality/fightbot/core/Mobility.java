package dev.reality.fightbot.core;

import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.place.Placer;
import dev.reality.fightbot.core.world.WorldView;

import java.util.List;

/**
 * Moves every style shares: following a target who towers up, by pillaring or by throwing an ender pearl
 * onto them, catching itself on the side of the tower when a pearl falls short, and putting out fire with
 * a water bucket. Each method returns true when it used the tick.
 */
public final class Mobility {
    /** Blocks it builds with, cheapest first. Obsidian goes last, since crystal fights need it. */
    static final List<String> BUILD = List.of("cobblestone", "cobbled_deepslate", "stone", "deepslate", "netherrack",
            "end_stone", "dirt", "blackstone", "sandstone", "oak_planks", "spruce_planks", "birch_planks",
            "dark_oak_planks", "obsidian");

    private final BotProfile p;
    private final Placer placer;
    private WorldView world;

    private int upTicks, stuckTicks;
    private double lastHdist = Double.MAX_VALUE;
    private boolean towering;
    private long lastPearl = -1000;
    private int baseX, baseY = Integer.MIN_VALUE, baseZ;
    private long waterAt = -1000;
    private boolean pickupPending;
    private String state = "";
    /** Counts for the round's results. */
    public int pillarBlocks, pearls, catches, pearlCatches, waterUses;

    public Mobility(BotProfile p) {
        this.p = p;
        this.placer = new Placer(p.placeReach, p.placeConfirmTicks);
    }

    public void setWorld(WorldView w) {
        world = w;
    }

    public String stateName() {
        return state;
    }

    public boolean towering() {
        return towering;
    }

    // ---------------------------------------------------------------- fire

    /** On fire: water at its feet, then the water back into the bucket a few ticks later. */
    public boolean fire(Observation o, BotAction a) {
        Loadout l = o.loadout();
        long t = o.tick();
        if (pickupPending && t - waterAt >= p.waterPickupTicks) {
            if (pickup(o, a)) return true;
        }
        if (p.useWater != 1 || world == null || !l.onFire || l.effectTicks("fire_resistance") != 0) return false;
        if (!l.has("water_bucket") || world.dimension().equals("the_nether") || !o.onGround() || o.gliding()) return false;
        if (t - waterAt < 20) return false;
        int bx = (int) Math.floor(o.x()), by = (int) Math.floor(o.y() + 0.01), bz = (int) Math.floor(o.z());
        if (!world.solid(bx, by - 1, bz) || world.solid(bx, by, bz)) return false;
        if (!inHotbar(l, "water_bucket", a)) return true;
        lookDown(o, a);
        a.useItem = "water_bucket";
        a.forward = 0;
        a.strafe = 0;
        a.jump = false;
        a.state = "WATER";
        a.technique = "water_out_fire";
        state = "WATER";
        placedWater(t, bx, by, bz);
        waterUses++;
        return true;
    }

    private int wx, wy, wz;

    private void placedWater(long t, int x, int y, int z) {
        waterAt = t;
        pickupPending = true;
        wx = x;
        wy = y;
        wz = z;
    }

    /**
     * Takes back the water it put down, aimed at that block, for up to three seconds. It stops once the
     * water is gone, or it has walked out of reach.
     */
    private boolean pickup(Observation o, BotAction a) {
        Loadout l = o.loadout();
        if (o.tick() - waterAt > 60 || (world != null && !world.water(wx, wy, wz)) || !l.has("bucket")) {
            pickupPending = false;
            return false;
        }
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        double hx = wx + 0.5, hy = wy + 0.5, hz = wz + 0.5;
        if (Math.sqrt((ex - hx) * (ex - hx) + (ey - hy) * (ey - hy) + (ez - hz) * (ez - hz)) > 4.2) return false;
        if (!inHotbar(l, "bucket", a)) return true;
        a.yaw = Placer.yawTo(ex, ez, hx, hz);
        a.pitch = Math.abs(hx - ex) < 0.6 && Math.abs(hz - ez) < 0.6 ? 90 : Placer.pitchTo(ex, ey, ez, hx, hy, hz);
        a.useItem = "bucket";
        a.state = "WATER";
        a.technique = "water_pickup";
        state = "WATER";
        return true;
    }

    // ---------------------------------------------------------------- pearling away to restock

    /**
     * Needs a restock but the target is too close to open a box: pearl to solid ground well away from them,
     * on the far side, so there is time to restock before they catch up.
     */
    public boolean pearlAway(Observation o, BotAction a) {
        if (world == null || p.pearlToRestock != 1 || !o.hasOpponent() || !o.onGround() || o.gliding()) return false;
        Loadout l = o.loadout();
        long t = o.tick();
        if (!l.has("ender_pearl") || t - lastPearl < 20) return false;
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        double awayX = o.x() - o.ox(), awayZ = o.z() - o.oz();
        double len = Math.max(0.1, Math.hypot(awayX, awayZ));
        awayX /= len;
        awayZ /= len;
        int cx = (int) Math.floor(ex), cz = (int) Math.floor(ez), cy = (int) Math.floor(o.y());
        java.util.List<double[]> spots = new java.util.ArrayList<>();
        for (int x = cx - 30; x <= cx + 30; x += 2) {
            for (int z = cz - 30; z <= cz + 30; z += 2) {
                double fromTarget = Math.hypot(x + 0.5 - o.ox(), z + 0.5 - o.oz());
                double fromBot = Math.hypot(x - cx, z - cz);
                if (fromTarget < p.restockPearlDistance || fromBot > 32) continue;
                // On the far side from the target.
                if ((x + 0.5 - o.x()) * awayX + (z + 0.5 - o.z()) * awayZ < fromBot * 0.5) continue;
                for (int y = cy + 6; y >= cy - 12; y--) {
                    if (!world.solid(x, y, z)) continue;
                    if (world.solid(x, y + 1, z) || world.solid(x, y + 2, z) || world.water(x, y + 1, z)) break;
                    spots.add(new double[] {x + 0.5, y + 1, z + 0.5, fromBot + Math.abs(fromTarget - p.restockPearlDistance - 4)});
                    break;
                }
            }
        }
        spots.sort(java.util.Comparator.comparingDouble(s2 -> s2[3]));
        for (int i = 0; i < spots.size() && i < 25; i++) {
            double[] sp = spots.get(i);
            float[] aim = pearlAim(world, ex, ey, ez, sp[0], sp[1], sp[2]);
            if (aim == null) continue;
            if (!inHotbar(l, "ender_pearl", a)) return true;
            throwPearl(o, a, aim[0], aim[1], "pearl_away_to_restock");
            a.forward = 0;
            a.strafe = 0;
            a.jump = false;
            a.state = "PEARL";
            state = "PEARL_AWAY";
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- towers

    /** Follows a target standing up on a tower. */
    public boolean tower(Observation o, BotAction a) {
        if (world == null || p.followTowers != 1 || !o.hasOpponent()) {
            towering = false;
            return false;
        }
        long t = o.tick();
        placer.verify(world, t);
        Loadout l = o.loadout();
        double height = o.oy() - o.y();
        double hdist = Math.hypot(o.ox() - o.x(), o.oz() - o.z());
        String block = buildBlock(l);
        boolean canPearl = l.has("ender_pearl");
        upTicks = height >= p.towerTrigger && hdist <= p.towerFollowRange ? upTicks + 1 : 0;
        if (!towering && upTicks >= 4 && (block != null || canPearl)) {
            towering = true;
            baseY = Integer.MIN_VALUE;
            lastHdist = Double.MAX_VALUE;
            stuckTicks = 0;
        }
        // Caught short after a pearl: falling next to the tower, so it puts a block under itself on the tower's side.
        boolean besideTower = height >= p.towerTrigger && hdist <= 3;
        if (block != null && !o.onGround() && o.vy() < -0.1 && (towering || besideTower || t - lastPearl < 60) && catchSelf(o, a, l, block)) return true;
        if (!towering) return false;
        // They are dropping back down, faster than a pillar jump falls: it waits with a weapon out instead of aiming a pearl at the
        // sky. A recorded fight had it looking straight up with a pearl while they landed next to it.
        if (!o.oOnGround() && o.ovy() < -0.35) {
            towering = false;
            upTicks = 0;
            state = "";
            return false;
        }
        if ((height < 0.2 && o.onGround()) || height < -1.5 || hdist > p.towerFollowRange + 4 || (block == null && !canPearl)) {
            towering = false;
            state = "";
            return false;
        }
        // Pearl straight onto them when the tower is tall, since that is faster than building.
        if (canPearl && o.onGround() && t - lastPearl >= 20 && (height >= p.pearlUpHeight || block == null)) {
            float[] aim = pearlAim(world, o.x(), o.eyeY(), o.z(), o.ox(), o.oy(), o.oz());
            if (aim != null) {
                if (!inHotbar(l, "ender_pearl", a)) return true;
                a.yaw = aim[0];
                a.pitch = aim[1];
                a.useItem = "ender_pearl";
                a.forward = 0;
                a.strafe = 0;
                a.jump = false;
                a.state = "TOWER";
                a.technique = "pearl_up";
                state = "PEARL";
                lastPearl = t;
                pearls++;
                return true;
            }
        }
        if (block == null) return false;
        a.state = "TOWER";
        // Too far from the tower to reach the top from a pillar of its own, so it walks up to it first.
        if (o.onGround() && hdist < lastHdist - 0.05) {
            lastHdist = hdist;
            stuckTicks = 0;
        } else if (o.onGround()) {
            stuckTicks++;
        }
        if (hdist > p.pillarFrom && o.onGround() && stuckTicks < 10) {
            a.yaw = Placer.yawTo(o.x(), o.z(), o.ox(), o.oz());
            a.pitch = 0;
            a.forward = 1;
            a.sprint = true;
            a.technique = "to_tower";
            state = "TO_TOWER";
            inHotbar(l, block, a);
            return true;
        }
        a.yaw = Placer.yawTo(o.x(), o.z(), o.ox(), o.oz());
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        if (o.onGround()) {
            if (o.headroom() < 1.3) {
                towering = false;
                return false;
            }
            // Jump, then put a block where its feet were once it has risen above it.
            baseX = (int) Math.floor(o.x());
            baseY = (int) Math.floor(o.y() + 0.01);
            baseZ = (int) Math.floor(o.z());
            a.pitch = 90;
            a.jump = true;
            a.technique = "pillar_jump";
            state = "PILLAR";
            inHotbar(l, block, a);
            return true;
        }
        a.pitch = 90;
        state = "PILLAR";
        if (baseY != Integer.MIN_VALUE && o.y() >= baseY + 1.05 && world.open(baseX, baseY, baseZ) && !placer.busy()) {
            if (place(o, a, l, block, baseX, baseY, baseZ, "pillar_place")) {
                pillarBlocks++;
                baseY = Integer.MIN_VALUE;
            }
        }
        return true;
    }

    /** A block straight under its feet, clicked on whatever solid face is next to that spot. */
    private boolean catchSelf(Observation o, BotAction a, Loadout l, String block) {
        int bx = (int) Math.floor(o.x()), bz = (int) Math.floor(o.z());
        int by = (int) Math.floor(o.y()) - 1;
        if (world.solid(bx, by, bz) || placer.busy()) return false;
        // Only worth it when it would otherwise fall more than a couple of blocks.
        if (world.solid(bx, by - 1, bz) || world.solid(bx, by - 2, bz)) return false;
        if (!placer.canPlaceBlock(world, bx, by, bz) && !hasSolidSide(bx, by, bz)) return false;
        if (!place(o, a, l, block, bx, by, bz, "catch_on_tower")) return false;
        catches++;
        a.state = "TOWER";
        state = "CATCH";
        return true;
    }

    private boolean hasSolidSide(int x, int y, int z) {
        return world.solid(x + 1, y, z) || world.solid(x - 1, y, z) || world.solid(x, y, z + 1) || world.solid(x, y, z - 1);
    }

    private boolean place(Observation o, BotAction a, Loadout l, String block, int x, int y, int z, String technique) {
        int slot = l.slot(block);
        if (slot < 0) {
            a.restock = block;
            return false;
        }
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        Placer.Spot s = placer.blockSpot(world, ex, ey, ez, x, y, z);
        if (s == null) return false;
        PlaceRequest r = new PlaceRequest("block", slot, s.cx(), s.cy(), s.cz(), s.face(), s.hx(), s.hy(), s.hz(), x, y, z, o.tick());
        if (!placer.request(world, r)) return false;
        a.place = r;
        a.yaw = Placer.yawTo(ex, ez, s.hx(), s.hz());
        a.pitch = Placer.pitchTo(ex, ey, ez, s.hx(), s.hy(), s.hz());
        a.technique = technique;
        return true;
    }

    private static void lookDown(Observation o, BotAction a) {
        a.yaw = o.yaw();
        a.pitch = 90;
    }

    /** True when the item is in the hotbar. Otherwise it asks for it to be moved there this tick. */
    private static boolean inHotbar(Loadout l, String id, BotAction a) {
        if (l.slot(id) >= 0) return true;
        if (l.has(id)) a.restock = id;
        return false;
    }

    /** The block to build with. Obsidian only when there is plenty, or nothing else. */
    public static String buildBlock(Loadout l) {
        for (String b : BUILD) {
            if (!l.has(b)) continue;
            if (b.equals("obsidian") && l.count(b) < 16) continue;
            return b;
        }
        return l.has("obsidian") ? "obsidian" : null;
    }

    // ---------------------------------------------------------------- pearl aim

    /** Yaw, pitch, and flight ticks for a pearl at a player who keeps moving the way they are now. */
    public static float[] pearlLead(WorldView w, Observation o) {
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        double tx = o.ox(), ty = o.oy(), tz = o.oz();
        float[] aim = null;
        // Twice: aim, see how long it flies, aim again where they will be by then.
        for (int pass = 0; pass < 2; pass++) {
            aim = pearlAim(w, ex, ey, ez, tx, ty, tz);
            if (aim == null) return null;
            double ticks = aim[2];
            tx = o.ox() + o.ovx() * ticks;
            tz = o.oz() + o.ovz() * ticks;
            int gx = (int) Math.floor(tx), gz = (int) Math.floor(tz);
            int gy = (int) Math.floor(o.oy() + 0.01);
            // Where they will stand: the ground under that spot.
            for (int k = 0; k < 6 && !w.solid(gx, gy - 1, gz); k++) gy--;
            ty = gy;
        }
        return pearlAim(w, ex, ey, ez, tx, ty, tz);
    }

    // ---------------------------------------------------------------- falls

    private long rescuePearlAt = -1000;
    /** Counts for the round's results. */
    public int rescues;

    /**
     * Falling somewhere that would kill it or hurt badly: into the void, a long drop, or a drop its health
     * cannot take. Near the ground a wind charge at its feet or water breaks the fall. Over the void or a long
     * drop it pearls to the nearest safe ground, and with nothing else it pearls straight down.
     * A mace drop onto the target is on purpose, so that is left alone.
     */
    public boolean rescue(Observation o, BotAction a) {
        if (world == null || p.fallRescue != 1 || o.onGround() || o.gliding() || o.vy() > -0.2 || o.loadout().inWater) return false;
        Loadout l = o.loadout();
        long t = o.tick();
        int bx = (int) Math.floor(o.x()), bz = (int) Math.floor(o.z());
        int ground = groundBelow(bx, (int) Math.floor(o.y()), bz);
        boolean voidBelow = ground == Integer.MIN_VALUE;
        if (ground == Integer.MAX_VALUE) return false;
        double drop = voidBelow ? 999 : o.y() - ground;
        boolean overTarget = !voidBelow && o.hasOpponent() && Math.hypot(o.ox() - o.x(), o.oz() - o.z()) <= 4 && o.oy() <= o.y() + 0.5;
        if (overTarget) return false;
        double damage = Math.max(0, o.fallDistance() + drop - 3) * p.fallDamageScale;
        boolean danger = voidBelow || drop >= p.dangerDrop || damage >= o.health() + o.absorption() - 2;
        if (!danger) return false;
        int wind = l.slot("wind_charge");
        boolean water = l.has("water_bucket") && !world.dimension().equals("the_nether");
        boolean pearl = l.has("ender_pearl") && t - lastPearl >= 20;
        a.state = "RESCUE";
        state = "RESCUE";
        // Over the void or a long drop: pearl to solid ground.
        if ((voidBelow || drop >= p.dangerDrop) && pearl && t - rescuePearlAt >= 20) {
            float[] aim = safeLanding(o);
            if (aim != null) {
                if (!inHotbar(l, "ender_pearl", a)) return true;
                throwPearl(o, a, aim[0], aim[1], "rescue_pearl");
                rescuePearlAt = t;
                rescues++;
                return true;
            }
        }
        if (voidBelow) return false;
        // Close to the ground: a wind charge at its feet, which the game counts the fall from.
        if (wind >= 0 && o.windReady() && drop >= 2 && drop <= 6) {
            a.yaw = o.yaw();
            a.pitch = 90;
            a.hotbar = wind;
            a.useOnce = true;
            a.technique = "rescue_wind";
            rescues++;
            return true;
        }
        // Water right before landing, picked back up afterward.
        if (water && drop >= 1.2 && drop <= 3.2) {
            if (!inHotbar(l, "water_bucket", a)) return true;
            a.yaw = o.yaw();
            a.pitch = 90;
            a.useItem = "water_bucket";
            a.technique = "rescue_water";
            placedWater(t, bx, ground, bz);
            rescues++;
            return true;
        }
        // Still too high for wind or water: wait for them. With neither, a pearl straight down ends the fall.
        if (!(wind >= 0 || water) && pearl && drop >= 3) {
            if (!inHotbar(l, "ender_pearl", a)) return true;
            throwPearl(o, a, o.yaw(), 90, "rescue_pearl_down");
            rescues++;
            return true;
        }
        return false;
    }

    private void throwPearl(Observation o, BotAction a, float yaw, float pitch, String technique) {
        a.yaw = yaw;
        a.pitch = pitch;
        a.useItem = "ender_pearl";
        a.technique = technique;
        lastPearl = o.tick();
        pearls++;
    }

    /** Top of the first solid block under x, y, z. The lowest value there is for the void, the highest for water. */
    private int groundBelow(int x, int y, int z) {
        for (int k = y; k >= world.minY(); k--) {
            if (world.solid(x, k, z)) return k + 1;
            // Water first: it breaks the fall, so there is nothing to save.
            if (world.water(x, k, z)) return Integer.MAX_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** Aim for a pearl onto the best solid ground in reach: near the target, and not lower than it needs to be. */
    private float[] safeLanding(Observation o) {
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        int cx = (int) Math.floor(ex), cz = (int) Math.floor(ez), cy = (int) Math.floor(o.y());
        java.util.List<double[]> spots = new java.util.ArrayList<>();
        int r = 20;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > r * r) continue;
                for (int y = cy + 6; y >= cy - 30; y--) {
                    if (!world.solid(x, y, z)) continue;
                    if (world.solid(x, y + 1, z) || world.solid(x, y + 2, z)) break;
                    double toTarget = o.hasOpponent() ? Math.hypot(x + 0.5 - o.ox(), z + 0.5 - o.oz()) : 0;
                    double score = toTarget + Math.max(0, cy - (y + 1)) * 0.5 + Math.hypot(x - cx, z - cz) * 0.3;
                    spots.add(new double[] {x + 0.5, y + 1, z + 0.5, score});
                    break;
                }
            }
        }
        spots.sort(java.util.Comparator.comparingDouble(s2 -> s2[3]));
        for (int i = 0; i < spots.size() && i < 30; i++) {
            double[] sp = spots.get(i);
            float[] aim = pearlAim(world, ex, ey, ez, sp[0], sp[1], sp[2], o.vx(), o.vy(), o.vz());
            if (aim != null) return aim;
        }
        return null;
    }

    // ---------------------------------------------------------------- catching its own pearl

    private long ownLaunchAt = -1, ownUntil = -1;
    private double ownX, ownZ;
    /** Counts for the round's results. */
    public int ownCatches;

    /** True while it waits on the spot for its pearl to come back down. The style can still hit from there. */
    public boolean holdingForCatch(long tick) {
        return ownLaunchAt >= 0 && tick < ownLaunchAt;
    }

    /**
     * Wind mace: throw a pearl straight up, wait, then wind launch so the falling pearl hits the bot on the
     * way up. The teleport puts its feet where the pearl hit its head and keeps its speed, so it goes higher
     * than a plain launch and smashes harder.
     */
    public boolean ownCatch(Observation o, BotAction a, boolean windMace) {
        if (world == null || p.ownPearlCatch != 1 || !windMace || !o.hasOpponent()) {
            ownLaunchAt = -1;
            return false;
        }
        long t = o.tick();
        Loadout l = o.loadout();
        if (ownLaunchAt >= 0) {
            boolean moved = Math.hypot(o.x() - ownX, o.z() - ownZ) > 0.35;
            int wind = l.slot("wind_charge");
            if (t > ownUntil || moved || wind < 0 || !o.onGround() && t < ownLaunchAt) {
                ownLaunchAt = -1;
                return false;
            }
            if (t < ownLaunchAt) return false;
            if (!o.windReady()) return false;
            a.yaw = o.yawToOpponent();
            a.pitch = 90;
            a.hotbar = wind;
            a.useOnce = true;
            a.jump = true;
            a.state = "PEARL";
            a.technique = "own_catch_launch";
            state = "OWN_CATCH";
            ownLaunchAt = -1;
            return true;
        }
        double dist = o.distance();
        if (!o.onGround() || t - lastPearl < 20 || !l.has("ender_pearl") || l.slot("wind_charge") < 0) return false;
        if (dist > p.windLaunchDistance + 1 || dist < 1.5 || o.health() + o.absorption() < p.pearlMinHealth) return false;
        if (o.oCharge() >= 0.8 && dist < 3) return false;
        // It stands still for a few seconds, so only while the target is not coming at it.
        double toward = -((o.ox() - o.x()) * o.ovx() + (o.oz() - o.z()) * o.ovz()) / Math.max(0.1, Math.hypot(o.ox() - o.x(), o.oz() - o.z()));
        if (dist < 3 || toward > 0.1) return false;
        int[] plan = ownCatchPlan(o.y());
        if (plan == null || plan[2] < p.ownCatchMinGain * 10) return false;
        // The pearl goes about 25 blocks up, so the sky above has to be open.
        int bx = (int) Math.floor(o.x()), bz = (int) Math.floor(o.z()), by = (int) Math.floor(o.y());
        for (int k = 2; k <= 30; k++) if (world.solid(bx, by + k, bz)) return false;
        if (!inHotbar(l, "ender_pearl", a)) return true;
        throwPearl(o, a, o.yaw(), -90, "own_catch_throw");
        a.forward = 0;
        a.strafe = 0;
        a.jump = false;
        a.sprint = false;
        a.state = "PEARL";
        state = "OWN_CATCH";
        ownLaunchAt = t + plan[0];
        ownUntil = t + plan[1] + 5;
        ownX = o.x();
        ownZ = o.z();
        ownCatches++;
        return true;
    }

    /**
     * When to launch after throwing a pearl straight up, so it comes down onto the bot's head while rising.
     * Returns the launch tick, the catch tick, and the height gained in tenths of a block, or null.
     */
    public static int[] ownCatchPlan(double feetY) {
        int[] best = null;
        double bestApex = -1;
        for (int launch = 1; launch <= 110; launch++) {
            double py = feetY + 1.62, pvy = 1.5;
            double by = feetY, bvy = 0;
            boolean flying = false;
            for (int t = 1; t <= 130; t++) {
                pvy -= 0.03;
                pvy *= 0.99;
                double prevPy = py;
                py += pvy;
                if (t == launch) {
                    bvy = 1.1;
                    flying = true;
                }
                double prevHead = by + 1.8;
                if (flying) {
                    by += bvy;
                    bvy = (bvy - 0.08) * 0.98;
                }
                double head = by + 1.8;
                // The pearl comes down through the top of the head.
                if (flying && pvy < 0 && prevPy > prevHead && py <= head && py >= by) {
                    double gain = py - by;
                    double apex = py + rise(bvy);
                    if (bvy > 0 && apex > bestApex) {
                        bestApex = apex;
                        best = new int[] {launch, t, (int) Math.round(gain * 10)};
                    }
                    break;
                }
                if (flying && by < feetY) break;
            }
        }
        return best;
    }

    private static double rise(double vy) {
        double h = 0;
        while (vy > 0) {
            h += vy;
            vy = (vy - 0.08) * 0.98;
        }
        return h;
    }

    // ---------------------------------------------------------------- pearls to close in

    /** Aim for a pearl onto open ground within a few blocks of the target, closest to them first. */
    private float[] nearTarget(Observation o) {
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        int tx = (int) Math.floor(o.ox()), tz = (int) Math.floor(o.oz()), ty = (int) Math.floor(o.oy());
        java.util.List<double[]> spots = new java.util.ArrayList<>();
        int r = (int) Math.ceil(p.pearlNearRadius);
        for (int x = tx - r; x <= tx + r; x++) {
            for (int z = tz - r; z <= tz + r; z++) {
                double d = Math.hypot(x + 0.5 - o.ox(), z + 0.5 - o.oz());
                if (d > p.pearlNearRadius) continue;
                for (int y = ty + 3; y >= ty - 4; y--) {
                    if (!world.solid(x, y, z)) continue;
                    if (world.solid(x, y + 1, z) || world.solid(x, y + 2, z) || world.lava(x, y + 1, z)) break;
                    spots.add(new double[] {x + 0.5, y + 1, z + 0.5, d + Math.abs(y + 1 - o.oy()) * 0.5});
                    break;
                }
            }
        }
        spots.sort(java.util.Comparator.comparingDouble(s2 -> s2[3]));
        for (int i = 0; i < spots.size() && i < 20; i++) {
            double[] sp = spots.get(i);
            float[] aim = pearlAim(world, ex, ey, ez, sp[0], sp[1], sp[2]);
            if (aim != null) return aim;
        }
        return null;
    }

    private long catchWindAt = -1;
    private long catchUntil = -1;

    /**
     * Too far away to run in: an ender pearl onto them or the ground beside them. With a wind mace it is a
     * pearl catch: pearl first, then a wind charge at its own feet just before the pearl lands. The teleport
     * keeps the bot's speed, so it arrives at the target already flying up, and drops back down with the mace.
     */
    public boolean pearlIn(Observation o, BotAction a, boolean windMace) {
        if (world == null || p.pearlChase != 1 || !o.hasOpponent()) return false;
        long t = o.tick();
        Loadout l = o.loadout();
        // Waiting on a pearl in the air, for the moment to wind charge.
        if (catchWindAt >= 0) {
            int wind = l.slot("wind_charge");
            if (t > catchUntil || wind < 0) {
                catchWindAt = -1;
                return false;
            }
            a.state = "PEARL";
            a.yaw = (float) o.yawToOpponent();
            if (t >= catchWindAt && o.windReady()) {
                a.hotbar = wind;
                a.useOnce = true;
                a.jump = o.onGround();
                a.pitch = 90;
                a.technique = "pearl_catch_wind";
                state = "PEARL_CATCH";
                catchWindAt = -1;
                catchUntil = -1;
                return true;
            }
            a.pitch = o.pitch();
            a.forward = 0;
            a.technique = "pearl_catch_wait";
            state = "PEARL_CATCH";
            return true;
        }
        double dist = o.distance();
        if (!o.onGround() || o.gliding() || t - lastPearl < 20 || !l.has("ender_pearl")) return false;
        if (o.health() + o.absorption() < p.pearlMinHealth) return false;
        boolean catchIt = windMace && p.pearlCatch == 1 && l.slot("wind_charge") >= 0 && o.windReady()
                && dist >= p.pearlCatchFrom && dist <= p.pearlMaxRange
                && !o.opponentCovered(p.minDropHeadroom) && !o.botCovered(p.minDropHeadroom);
        // Farther than a pearl reaches: throw one most of the way. Against Canaan it walked fifty blocks back
        // after he pearled off to eat, and he had eaten twice by the time it got there.
        boolean closer = false;
        float[] aim = null;
        if (!catchIt && dist > p.pearlMaxRange && dist <= p.pearlMaxRange * 2 && p.pearlCloser == 1) {
            aim = closerSpot(o);
            closer = aim != null;
            if (!closer) return false;
        }
        if (!catchIt && !closer && (dist < p.pearlChaseBeyond || dist > p.pearlMaxRange)) return false;
        if (!closer) aim = pearlLead(world, o);
        // No clear throw onto them, for example behind a hill or a wall: the closest open ground near them.
        if (aim == null && !catchIt) aim = nearTarget(o);
        if (aim == null) return false;
        if (!inHotbar(l, "ender_pearl", a)) return true;
        a.yaw = aim[0];
        a.pitch = aim[1];
        a.useItem = "ender_pearl";
        a.forward = 0;
        a.strafe = 0;
        a.jump = false;
        a.state = "PEARL";
        a.technique = catchIt ? "pearl_catch_throw" : closer ? "pearl_closer" : "pearl_in";
        state = catchIt ? "PEARL_CATCH" : "PEARL";
        lastPearl = t;
        pearls++;
        if (catchIt) {
            // The pearl lands about aim[2] ticks from now. The wind charge goes off a little before.
            catchWindAt = t + Math.max(1, (long) aim[2] - p.pearlCatchLead);
            catchUntil = t + (long) aim[2] + 4;
            pearlCatches++;
        }
        return true;
    }

    /** A throw onto open ground on the way to a target out of pearl range, as far along as a pearl reaches. */
    private float[] closerSpot(Observation o) {
        double dx = o.ox() - o.x(), dz = o.oz() - o.z();
        double len = Math.hypot(dx, dz);
        if (len < 1) return null;
        dx /= len;
        dz /= len;
        int cy = (int) Math.floor(o.y());
        for (double d = p.pearlMaxRange - 2; d >= p.pearlMaxRange * 0.6; d -= 2) {
            int x = (int) Math.floor(o.x() + dx * d), z = (int) Math.floor(o.z() + dz * d);
            for (int y = cy + 8; y >= cy - 12; y--) {
                if (!world.solid(x, y, z)) continue;
                if (world.solid(x, y + 1, z) || world.solid(x, y + 2, z) || world.lava(x, y + 1, z) || world.lava(x, y, z)
                        || world.water(x, y + 1, z) || world.harmful(x, y, z)) break;
                float[] aim = pearlAim(world, o.x(), o.eyeY(), o.z(), x + 0.5, y + 1, z + 0.5);
                if (aim != null) return aim;
                break;
            }
        }
        return null;
    }

    /**
     * Yaw and pitch that land an ender pearl on a player standing at tx, ty, tz, or null. It traces the
     * pearl the way the game moves it: gravity, then drag, then the move, checking blocks on the way.
     */
    public static float[] pearlAim(WorldView w, double ex, double ey, double ez, double tx, double ty, double tz) {
        return pearlAim(w, ex, ey, ez, tx, ty, tz, 0, 0, 0);
    }

    /** The same, thrown while moving: the pearl starts with the thrower's speed added, as the game does it. */
    public static float[] pearlAim(WorldView w, double ex, double ey, double ez, double tx, double ty, double tz,
                                   double avx, double avy, double avz) {
        float yaw = Placer.yawTo(ex, ez, tx, tz);
        float[] best = null;
        int bestTicks = Integer.MAX_VALUE;
        for (double pitch = -70; pitch <= 60; pitch += 0.5) {
            int ticks = pearlHits(w, ex, ey, ez, yaw, pitch, tx, ty, tz, avx, avy, avz);
            if (ticks > 0 && ticks < bestTicks) {
                bestTicks = ticks;
                best = new float[] {yaw, (float) pitch, ticks};
            }
        }
        return best;
    }

    /** Ticks until the pearl hits the player's box or lands on top of the block they stand on, or -1. */
    static int pearlHits(WorldView w, double ex, double ey, double ez, float yaw, double pitch, double tx, double ty, double tz,
                         double avx, double avy, double avz) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double vx = -Math.sin(yr) * Math.cos(pr) * 1.5 + avx, vy = -Math.sin(pr) * 1.5 + avy, vz = Math.cos(yr) * Math.cos(pr) * 1.5 + avz;
        double x = ex, y = ey, z = ez;
        for (int tick = 1; tick <= 80; tick++) {
            vy -= 0.03;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;
            // Small steps, so a pearl never skips through a one block wall.
            int n = Math.max(4, (int) Math.ceil(Math.sqrt(vx * vx + vy * vy + vz * vz) / 0.15));
            for (int s = 1; s <= n; s++) {
                double px = x + vx * s / n, py = y + vy * s / n, pz = z + vz * s / n;
                if (Math.abs(px - tx) <= 0.425 && Math.abs(pz - tz) <= 0.425 && py >= ty - 0.1 && py <= ty + 1.9) return tick;
                int bx = (int) Math.floor(px), by = (int) Math.floor(py), bz = (int) Math.floor(pz);
                if (w.solid(bx, by, bz)) {
                    // Landing on the top face right beside them still puts it on the tower.
                    boolean top = py - by > 0.8 && Math.abs(by + 1 - ty) < 0.3 && Math.hypot(px - tx, pz - tz) < 1.6
                            && !w.solid(bx, by + 1, bz);
                    return top ? tick : -1;
                }
            }
            x += vx;
            y += vy;
            z += vz;
            if (y < ty - 30) return -1;
        }
        return -1;
    }
}
