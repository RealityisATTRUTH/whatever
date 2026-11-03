package dev.reality.fightbot.core;

import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.place.Placer;
import dev.reality.fightbot.core.world.WorldView;

/**
 * The UHC tools: lava and cobwebs. Lava goes at the target's feet to set them on fire and is taken back a
 * couple of seconds later. A cobweb at their feet holds them in place for hits. A cobweb at its own feet
 * stops a combo, since knockback cannot push it out. It cuts itself out of webs with the sword, steps
 * around lava, and climbs out if it ends up in it. Any style uses these when the items are there.
 */
public final class UhcTools {
    private final BotProfile p;
    private final Placer placer;
    private WorldView world;

    private long lavaAt = -1000, webAt = -1000, selfWebAt = -1000;
    private int lx, ly, lz;
    private boolean lavaOut;
    private String state = "";
    /** Counts for the round's results. */
    public int lavaPours, webs, selfWebs;

    public UhcTools(BotProfile p) {
        this.p = p;
        this.placer = new Placer(p.placeReach, p.placeConfirmTicks);
    }

    public void setWorld(WorldView w) {
        world = w;
    }

    public String stateName() {
        return state;
    }

    /** Returns true when it used the tick. */
    public boolean step(Observation o, BotAction a) {
        if (world == null || !o.hasOpponent()) return false;
        long t = o.tick();
        Loadout l = o.loadout();
        placer.verify(world, t);
        int bx = (int) Math.floor(o.x()), by = (int) Math.floor(o.y() + 0.01), bz = (int) Math.floor(o.z());

        // In lava: out, toward the nearest dry spot, holding jump.
        if (l.inLava || world.lava(bx, by, bz)) {
            int[] dry = dryCell(bx, by, bz);
            a.state = "UHC";
            a.technique = "escape_lava";
            state = "ESCAPE_LAVA";
            if (dry != null) {
                a.yaw = Placer.yawTo(o.x(), o.z(), dry[0] + 0.5, dry[2] + 0.5);
                a.pitch = 0;
            }
            a.forward = 1;
            a.sprint = true;
            a.holdJump = true;
            return true;
        }
        // Take the lava back after a while, or once the target has left it.
        if (lavaOut && pickupLava(o, a)) return true;

        double dist = o.distance();
        double height = o.oy() - o.y();
        int tx = (int) Math.floor(o.ox()), ty = (int) Math.floor(o.oy() + 0.01), tz = (int) Math.floor(o.oz());
        boolean targetStanding = o.oOnGround() && world.solid(tx, ty - 1, tz) && !world.solid(tx, ty, tz);

        // Stuck in a web it did not put down on purpose, or kept on too long: cut out with the sword.
        boolean inWeb = world.web(bx, by, bz) || world.web(bx, by + 1, bz);
        boolean wantsWeb = t - selfWebAt < p.selfWebHoldTicks;
        if (inWeb && !wantsWeb && !(dist <= 3.0 && world.web(tx, ty, tz))) {
            int[] cell = world.web(bx, by, bz) ? new int[] {bx, by, bz} : new int[] {bx, by + 1, bz};
            int sword = l.slot(Loadout::isSword);
            a.mine = cell;
            a.mineSlot = sword >= 0 ? sword : o.weaponSlot();
            a.yaw = o.yaw();
            a.pitch = cell[1] == by ? 80 : -60;
            a.state = "UHC";
            a.technique = "cut_web";
            state = "CUT_WEB";
            return true;
        }

        // Self web: getting comboed at low health, so it stops the knockback where it stands.
        if (l.has("cobweb") && o.onGround() && !inWeb && t - selfWebAt > 100 && dist <= 3.2
                && o.health() + o.absorption() <= p.selfWebHealth && o.oCharge() >= 0.8 && world.solid(bx, by - 1, bz)) {
            if (placeWeb(o, a, l, bx, by, bz, "web_self")) {
                selfWebAt = t;
                selfWebs++;
                return true;
            }
        }

        // Web the target: they cannot back off or be knocked out of it, so every hit lands.
        if (l.has("cobweb") && targetStanding && dist >= 1.5 && dist <= p.webRange && t - webAt > p.webCooldown
                && !world.web(tx, ty, tz) && !world.lava(tx, ty, tz) && !world.water(tx, ty, tz)) {
            if (placeWeb(o, a, l, tx, ty, tz, "web_target")) {
                webAt = t;
                webs++;
                return true;
            }
        }

        // Lava at their feet: not when they are above it, so it cannot run down onto the bot, and not too close.
        if (l.has("lava_bucket") && !lavaOut && targetStanding && dist >= p.lavaMinDistance && dist <= 4.2
                && height <= 0.5 && t - lavaAt > p.lavaCooldown && !world.water(tx, ty, tz) && !world.web(tx, ty, tz)
                && !world.dimension().equals("the_nether") && o.health() + o.absorption() > 6) {
            double ex = o.x(), ey = o.eyeY(), ez = o.z();
            double hx = tx + 0.5, hy = ty - 0.02, hz = tz + 0.5;
            if (Math.sqrt((ex - hx) * (ex - hx) + (ey - hy) * (ey - hy) + (ez - hz) * (ez - hz)) <= 4.3
                    && world.clearLine(ex, ey, ez, hx, ty + 0.1, hz)) {
                if (l.slot("lava_bucket") < 0) {
                    a.restock = "lava_bucket";
                    return false;
                }
                a.yaw = Placer.yawTo(ex, ez, hx, hz);
                a.pitch = Placer.pitchTo(ex, ey, ez, hx, hy, hz);
                a.useItem = "lava_bucket";
                a.exactAim = true;
                a.forward = -1;
                a.sprint = false;
                a.state = "UHC";
                a.technique = "lava_pour";
                state = "LAVA";
                lavaAt = t;
                lavaOut = true;
                lx = tx;
                ly = ty;
                lz = tz;
                lavaPours++;
                return true;
            }
        }
        return false;
    }

    /** Never walks into lava: stops short of a lava block ahead, at its feet or one below. */
    public void avoidLava(Observation o, BotAction a) {
        if (world == null || (a.forward == 0 && a.strafe == 0)) return;
        double ang = Math.toRadians(a.yaw + (a.forward >= 0 ? -45 * a.strafe : 0) + (a.forward < 0 ? 180 : 0));
        if (a.forward == 0) ang = Math.toRadians(a.yaw - 90 * a.strafe);
        int fy = (int) Math.floor(o.y() + 0.01);
        for (double ahead : new double[] {0.7, 1.4}) {
            int px = (int) Math.floor(o.x() - Math.sin(ang) * ahead), pz = (int) Math.floor(o.z() + Math.cos(ang) * ahead);
            if (world.lava(px, fy, pz) || world.lava(px, fy - 1, pz) || world.lava(px, fy + 1, pz)) {
                a.forward = 0;
                a.strafe = 0;
                a.sprint = false;
                a.jump = false;
                if (a.technique.isEmpty()) a.technique = "avoid_lava";
                return;
            }
        }
    }

    private boolean pickupLava(Observation o, BotAction a) {
        long t = o.tick();
        boolean left = Math.hypot(o.ox() - (lx + 0.5), o.oz() - (lz + 0.5)) > 1.5;
        if (!world.lava(lx, ly, lz)) {
            lavaOut = false;
            return false;
        }
        if (t - lavaAt < p.lavaHoldTicks && !left) return false;
        if (t - lavaAt > 200) {
            lavaOut = false;
            return false;
        }
        Loadout l = o.loadout();
        if (!l.has("bucket")) return false;
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        double hx = lx + 0.5, hy = ly + 0.3, hz = lz + 0.5;
        if (Math.sqrt((ex - hx) * (ex - hx) + (ey - hy) * (ey - hy) + (ez - hz) * (ez - hz)) > 4.3) return false;
        if (l.slot("bucket") < 0) {
            a.restock = "bucket";
            return false;
        }
        a.yaw = Placer.yawTo(ex, ez, hx, hz);
        a.pitch = Placer.pitchTo(ex, ey, ez, hx, hy, hz);
        a.useItem = "bucket";
        a.exactAim = true;
        a.state = "UHC";
        a.technique = "lava_pickup";
        state = "LAVA";
        return true;
    }

    private boolean placeWeb(Observation o, BotAction a, Loadout l, int x, int y, int z, String technique) {
        int slot = l.slot("cobweb");
        if (slot < 0) {
            a.restock = "cobweb";
            return false;
        }
        if (placer.busy()) return false;
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        Placer.Spot s = placer.blockSpot(world, ex, ey, ez, x, y, z);
        if (s == null) return false;
        PlaceRequest r = new PlaceRequest("web", slot, s.cx(), s.cy(), s.cz(), s.face(), s.hx(), s.hy(), s.hz(), x, y, z, o.tick());
        if (!placer.request(world, r)) return false;
        a.place = r;
        a.yaw = Placer.yawTo(ex, ez, s.hx(), s.hz());
        a.pitch = Placer.pitchTo(ex, ey, ez, s.hx(), s.hy(), s.hz());
        a.state = "UHC";
        a.technique = technique;
        state = "WEB";
        return true;
    }

    /** The closest cell next to it that has no lava and solid ground. */
    private int[] dryCell(int x, int y, int z) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -1; dy <= 1; dy++) {
            int cx = x + dx, cy = y + dy, cz = z + dz;
            if (world.lava(cx, cy, cz) || world.lava(cx, cy + 1, cz) || world.solid(cx, cy, cz) || world.solid(cx, cy + 1, cz)) continue;
            if (!world.solid(cx, cy - 1, cz)) continue;
            double d = dx * dx + dz * dz + dy * dy * 0.5;
            if (d < bestD) {
                bestD = d;
                best = new int[] {cx, cy, cz};
            }
        }
        return best;
    }
}
