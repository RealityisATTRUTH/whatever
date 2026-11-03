package dev.reality.fightbot.core;

import dev.reality.fightbot.core.place.PlaceRequest;
import dev.reality.fightbot.core.place.Placer;
import dev.reality.fightbot.core.world.Explosions;
import dev.reality.fightbot.core.world.WorldView;
import dev.reality.fightbot.core.world.WorldView.Kind;

import java.util.ArrayList;
import java.util.List;

/**
 * Crystal and respawn anchor PvP. Each tick it does at most one block click and one hit.
 *
 * In order of preference: hit a crystal that would hurt the target more than the bot; place a crystal on
 * obsidian or bedrock where its blast would; place obsidian next to the target to make such a spot; and,
 * where anchors explode, place a respawn anchor next to the target, charge it with glowstone, and set it
 * off with any other item. Every option is scored as damage to the target minus damage to the bot, with
 * the target's position predicted a couple of ticks ahead. Blasts that would hurt the bot too much, or
 * leave it nearly dead, are never set off. With nothing worth doing, it swings its sword or repositions.
 *
 * Placement geometry, reach, line of sight, and checking that a click worked live in the Placer. Every
 * action is logged with what happened next, and blasts are measured from health afterward.
 */
public final class CrystalPolicy implements Policy {
    private final BotProfile p;
    private final Placer placer;
    private final SwordPolicy melee;
    private WorldView world;
    private String state = "CRYSTAL_IDLE";
    /** The anchor this bot placed and is working on, or null. */
    private int[] anchor;
    private int strafeDir = 1;
    private long nextStrafe;
    public final CrystalLog log = new CrystalLog();

    public CrystalPolicy(BotProfile profile) {
        this.p = profile;
        this.placer = new Placer(profile.placeReach, profile.placeConfirmTicks);
        this.melee = new SwordPolicy(profile);
    }

    /** The world to read this tick. Set by whoever runs the bot before each step. */
    public void setWorld(WorldView w) {
        this.world = w;
    }

    public Placer placer() {
        return placer;
    }

    @Override
    public String stateName() {
        return state;
    }

    /** One scored option. */
    private record Option(String what, double target, double self, Placer.Spot spot, int crystalId, double cx, double cy, double cz) {
        double score(BotProfile p) {
            return target - self * p.selfDamageWeight;
        }
    }

    @Override
    public BotAction step(Observation o) {
        if (!o.hasOpponent() || o.stale() || world == null) {
            state = "CRYSTAL_IDLE";
            return BotAction.idle(o.yaw(), o.pitch());
        }
        log.measure(o);
        Placer.Result res = placer.verify(world, o.tick());
        if (res != null) {
            log.placementResult(o, res);
            if (res.ok() && res.request().what().equals("anchor")) anchor = new int[] {res.request().tx(), res.request().ty(), res.request().tz()};
            if (res.ok() && res.request().what().equals("detonate")) anchor = null;
        }
        if (anchor != null && world.kind(anchor[0], anchor[1], anchor[2]) != Kind.ANCHOR) anchor = null;
        // The target walked away from the anchor: leave it, and place a new one where they are.
        if (anchor != null && Math.hypot(anchor[0] + 0.5 - o.ox(), anchor[2] + 0.5 - o.oz()) > 3) anchor = null;

        Loadout l = o.loadout();
        double ex = o.x(), ey = o.eyeY(), ez = o.z();
        double px = o.ox() + o.ovx() * p.crystalLead, py = o.oy(), pz = o.oz() + o.ovz() * p.crystalLead;
        // A flyer: no chasing. It waits on the ground, and blasts are scored where the flight path will be.
        Predictor pr = Predictor.current();
        flyer = pr != null && p.flyerWait == 1 && pr.flying(o);
        flightPath = null;
        if (pr != null) {
            // The blast from hitting a crystal lands this tick, so it scores the next tick's position.
            double[] next = pr.at(o, flyer ? 1 : p.crystalLead);
            px = next[0];
            py = next[1];
            pz = next[2];
            if (flyer) flightPath = pr.path(o, p.flyerCrystalHorizon);
        }

        BotAction a = new BotAction();
        move(o, a);
        a.yaw = o.yawToOpponent();
        a.pitch = o.pitchToOpponent();

        // 0. Low on health: eat. In the recordings against Canaan it sat at one or two hearts for forty seconds
        // without one bite, popping totems, while every crystal spot was too dangerous to use at that health.
        // He ate between every exchange. A bite only pauses for a crystal that finishes them.
        Option hit = bestBreak(o, ex, ey, ez, px, py, pz);
        boolean killHit = hit != null && hit.target() >= o.oEffectiveHealth();
        BotAction eat = eatStep(o, l, a, killHit);
        if (eat != null) return eat;

        // 1. Hit the best crystal already standing.
        if (hit != null) {
            a.attackEntity = hit.crystalId();
            a.yaw = Placer.yawTo(ex, ez, hit.cx(), hit.cz());
            a.pitch = Placer.pitchTo(ex, ey, ez, hit.cx(), hit.cy() + 1, hit.cz());
            a.technique = "break_crystal";
            state = "CRYSTAL_BREAK";
            log.action(o, "break", "end_crystal", hit.target(), hit.self());
            return a;
        }

        // 2. One block click, unless the last one is unresolved.
        why.clear();
        sampleSpot = "";
        if (world.difficulty().equals("peaceful")) note("the world is on Peaceful, where blasts do not hurt players");
        if (l.slot("end_crystal") < 0 && l.slot("respawn_anchor") < 0) note("no crystals or anchors in the hotbar");
        if (!placer.busy()) {
            Option best = null;
            if (p.useAnchors == 1 && world.anchorsExplode()) best = anchorStep(o, l, ex, ey, ez, px, py, pz);
            if (best == null) best = flyer ? bestFlyerCrystal(o, l, ex, ey, ez) : bestCrystalPlace(o, l, ex, ey, ez, px, py, pz);
            if (best == null) best = flyer ? prepBase(o, l, ex, ey, ez) : bestObsidian(o, l, ex, ey, ez, px, py, pz);
            if (best == null && p.useAnchors == 1 && world.anchorsExplode() && anchor == null) best = bestAnchorPlace(o, l, ex, ey, ez, px, py, pz);
            if (best != null) {
                int slot = slotFor(l, best.what());
                if (slot >= 0) {
                    Placer.Spot s = best.spot();
                    PlaceRequest r = new PlaceRequest(best.what(), slot, s.cx(), s.cy(), s.cz(), s.face(), s.hx(), s.hy(), s.hz(),
                            s.tx(), s.ty(), s.tz(), o.tick());
                    if (placer.request(world, r)) {
                        clicks++;
                        lastClickTick = o.tick();
                        noOptionTicks = 0;
                        lastWhy = "clicking " + best.what();
                        a.place = r;
                        a.hotbar = slot;
                        a.yaw = Placer.yawTo(ex, ez, s.hx(), s.hz());
                        a.pitch = Placer.pitchTo(ex, ey, ez, s.hx(), s.hy(), s.hz());
                        a.technique = "place_" + best.what();
                        state = "CRYSTAL_" + best.what().toUpperCase();
                        log.action(o, best.what(), itemFor(best.what()), best.target(), best.self());
                        return a;
                    }
                }
            }
        }

        // 3. Nothing worth a click.
        if (!placer.busy()) {
            noOptionTicks++;
            StringBuilder sb = new StringBuilder("No click: ");
            if (why.isEmpty()) sb.append("no spots found near the target");
            else {
                boolean first = true;
                for (var e : why.entrySet()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append(e.getValue()).append(" ").append(e.getKey());
                }
            }
            lastWhy = sb.toString() + sampleSpot;
        }
        // After a second with no good spot, close in and fight with the sword instead of circling.
        if (noOptionTicks > 20 && o.distance() > 3.0 && !flyer) {
            a.forward = 1;
            a.sprint = true;
            a.strafe = 0;
            a.technique = "close_in";
        }
        sprintJump(o, a);
        if (o.distance() <= 3.2) {
            BotAction m = melee.step(o);
            if (m.attack || m.hotbar >= 0) {
                m.state = "CRYSTAL_MELEE";
                state = m.state;
                if (m.attack) log.action(o, "melee", o.mainHand(), Double.NaN, 0);
                return m;
            }
        }
        state = placer.busy() ? "CRYSTAL_WAIT" : "CRYSTAL_POSITION";
        // Out of something in the hotbar but carrying more: bring it down while nothing else is going on.
        for (String item : new String[] {"end_crystal", "obsidian", "respawn_anchor", "glowstone"}) {
            if (l.slot(item) < 0 && l.has(item)) {
                a.restock = item;
                a.technique = "restock_" + item;
                break;
            }
        }
        return a;
    }

    // ---------------------------------------------------------------- flyers

    private boolean flyer;
    private double[][] flightPath;
    /** Counts for the round's results. */
    public int flyerCrystals, flyerBases;

    /** True while the target is flying and the crystal style is waiting for them on the ground. */
    public boolean waitingForFlyer() {
        return flyer;
    }

    /**
     * A crystal set down ahead of a flyer: on a base near the bot, scored by the most damage it would do
     * anywhere along their predicted path from two ticks on, since the hit that sets it off comes later.
     */
    private Option bestFlyerCrystal(Observation o, Loadout l, double ex, double ey, double ez) {
        if (l.slot("end_crystal") < 0 || flightPath == null) return null;
        Option best = null;
        int fy = (int) Math.floor(o.y());
        for (int[] b : around(o.x(), o.z(), 4, fy - 2, fy + 1)) {
            if (dist(ex, ey, ez, b[0] + 0.5, b[1] + 1, b[2] + 0.5) > p.placeReach) continue;
            if (!placer.canPlaceCrystal(world, b[0], b[1], b[2])) continue;
            double cx = b[0] + 0.5, cy = b[1] + 1, cz = b[2] + 0.5;
            // It has to be able to hit the crystal when the flyer gets there.
            double nx = clamp(ex, cx - 1, cx + 1), ny = clamp(ey, cy, cy + 2), nz = clamp(ez, cz - 1, cz + 1);
            if (dist(ex, ey, ez, nx, ny, nz) > p.breakReach) continue;
            // Cheap check first: does the path come within blast range of this base at all?
            int bestT = -1;
            double bestD = 7.0;
            for (int t = 2; t < flightPath.length; t++) {
                double[] q = flightPath[t];
                double d = dist(cx, cy, cz, q[0], q[1] + 0.3, q[2]);
                if (d < bestD) {
                    bestD = d;
                    bestT = t;
                }
            }
            if (bestT < 0) continue;
            Placer.Spot s = placer.topSpot(world, ex, ey, ez, b[0], b[1], b[2], b[0], b[1] + 1, b[2]);
            if (s == null) continue;
            if (placer.skipped("crystal@" + b[0] + "," + (b[1] + 1) + "," + b[2], o.tick())) continue;
            double[] q = flightPath[bestT];
            Option opt = scoreBlast(world, "crystal", Explosions.CRYSTAL_POWER, cx, cy, cz, o, q[0], q[1], q[2], s);
            if (opt != null && (best == null || opt.score(p) > best.score(p))) best = opt;
        }
        if (best != null) flyerCrystals++;
        return best;
    }

    /**
     * Obsidian set down beside the bot, toward where the flyer's path comes down, so there is somewhere to put
     * a crystal when the dive comes. It keeps a couple of these ready.
     */
    private Option prepBase(Observation o, Loadout l, double ex, double ey, double ez) {
        if (l.slot("obsidian") < 0 || l.slot("end_crystal") < 0 || flightPath == null) return null;
        int fy = (int) Math.floor(o.y());
        int ready = 0;
        for (int[] b : around(o.x(), o.z(), 3, fy - 1, fy)) {
            if (placer.canPlaceCrystal(world, b[0], b[1], b[2]) && dist(ex, ey, ez, b[0] + 0.5, b[1] + 1, b[2] + 0.5) <= p.placeReach) ready++;
        }
        if (ready >= p.crystalPrepBases) return null;
        // The lowest point of their path ahead is where a dive comes down.
        double[] low = flightPath[flightPath.length - 1];
        for (double[] q : flightPath) if (q[1] < low[1]) low = q;
        Option best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] b : around(o.x(), o.z(), 3, fy, fy)) {
            double hx = b[0] + 0.5 - o.x(), hz = b[2] + 0.5 - o.z();
            // Not where it stands, and not so close that its own crystal blast is point blank.
            if (Math.hypot(hx, hz) < 1.8) continue;
            if (!placer.canPlaceBlock(world, b[0], b[1], b[2])) continue;
            if (world.kind(b[0], b[1] + 1, b[2]) != Kind.AIR || world.kind(b[0], b[1] + 2, b[2]) != Kind.AIR) continue;
            Placer.Spot s = placer.blockSpot(world, ex, ey, ez, b[0], b[1], b[2]);
            if (s == null) continue;
            if (placer.skipped("obsidian@" + b[0] + "," + b[1] + "," + b[2], o.tick())) continue;
            double score = Math.hypot(b[0] + 0.5 - low[0], b[2] + 0.5 - low[2]);
            if (score < bestScore) {
                bestScore = score;
                best = new Option("obsidian", 0, 0, s, -1, b[0] + 0.5, b[1] + 1, b[2] + 0.5);
            }
        }
        if (best != null) flyerBases++;
        return best;
    }

    // ---------------------------------------------------------------- options

    /**
     * Worth it: enough damage to the target, more than the bot takes by a margin, and the bot survives it.
     * Taking a blast that pops the bot's own totem is only allowed when it pops or kills the target too.
     */
    private boolean acceptable(Observation o, double target, double self) {
        String why = null;
        if (target < p.crystalMinDamage) why = "too little damage to the target";
        else if (self > p.crystalMaxSelf) why = "too much damage to itself";
        else if (target - self * p.selfDamageWeight < p.crystalMinNet) why = "not enough more damage to the target than to itself";
        else {
            boolean survives = o.effectiveHealth() - self >= p.crystalSafetyHealth;
            boolean tradesTotems = o.loadout().offhand.equals("totem_of_undying") && target >= o.oEffectiveHealth();
            if (!survives && !tradesTotems) why = "it would not survive the blast";
        }
        if (why != null) note(why);
        return why == null;
    }

    // ---------------------------------------------------------------- diagnostics

    private final java.util.Map<String, Integer> why = new java.util.TreeMap<>();
    private String lastWhy = "nothing checked yet";
    private int noOptionTicks;
    private long lastClickTick = -1;
    private int clicks;

    private void note(String reason) {
        why.merge(reason, 1, Integer::sum);
    }

    private String sampleSpot = "";

    /** Keeps one failed spot and the eye position, so a report shows real coordinates to check. */
    private void sample(int[] b, double ex, double ey, double ez) {
        if (sampleSpot.isEmpty()) {
            sampleSpot = String.format(" For example the spot %d %d %d, from eyes at %.1f %.1f %.1f, block there: %s, block below: %s.",
                    b[0], b[1], b[2], ex, ey, ez, world.kind(b[0], b[1], b[2]), world.kind(b[0], b[1] - 1, b[2]));
        }
    }

    /** Why the last tick did not click, as counts of rejected spots per reason. */
    public String lastWhy() {
        return lastWhy;
    }

    /** A one-line summary for chat. */
    public String diagnose() {
        return String.format("%d clicks so far, last at tick %d. %s. World: %s, difficulty %s.", clicks, lastClickTick,
                lastWhy, world == null ? "not read" : world.dimension(), world == null ? "unknown" : world.difficulty());
    }

    public int ticksWithoutOption() {
        return noOptionTicks;
    }

    private Option bestBreak(Observation o, double ex, double ey, double ez, double px, double py, double pz) {
        Option best = null;
        for (WorldView.Crystal c : world.crystals()) {
            // Hitting needs the crystal's 2 by 2 by 2 box in entity reach and in sight.
            double nx = clamp(ex, c.x() - 1, c.x() + 1), ny = clamp(ey, c.y(), c.y() + 2), nz = clamp(ez, c.z() - 1, c.z() + 1);
            if (dist(ex, ey, ez, nx, ny, nz) > p.breakReach) continue;
            if (!world.clearLine(ex, ey, ez, c.x(), c.y() + 1, c.z())) continue;
            double t = Explosions.toPlayer(world, c.x(), c.y(), c.z(), Explosions.CRYSTAL_POWER, px, py, pz, false);
            double s = Explosions.toPlayer(world, c.x(), c.y(), c.z(), Explosions.CRYSTAL_POWER, o.x(), o.y(), o.z(), true);
            if (!acceptable(o, t, s)) continue;
            Option opt = new Option("break", t, s, null, c.id(), c.x(), c.y(), c.z());
            if (best == null || opt.score(p) > best.score(p)) best = opt;
        }
        return best;
    }

    private Option bestCrystalPlace(Observation o, Loadout l, double ex, double ey, double ez, double px, double py, double pz) {
        if (l.slot("end_crystal") < 0) return null;
        Option best = null;
        int fy = (int) Math.floor(py);
        for (int[] b : around(px, pz, 4, fy - 3, fy + 1)) {
            if (dist(ex, ey, ez, b[0] + 0.5, b[1] + 1, b[2] + 0.5) > p.placeReach) continue;
            if (!placer.canPlaceCrystal(world, b[0], b[1], b[2])) continue;
            Placer.Spot s = placer.topSpot(world, ex, ey, ez, b[0], b[1], b[2], b[0], b[1] + 1, b[2]);
            if (s == null) { note("crystal bases: " + placer.lastFail); sample(b, ex, ey, ez); continue; }
            if (placer.skipped("crystal@" + b[0] + "," + (b[1] + 1) + "," + b[2], o.tick())) continue;
            Option opt = scoreBlast(world, "crystal", Explosions.CRYSTAL_POWER, b[0] + 0.5, b[1] + 1, b[2] + 0.5, o, px, py, pz, s);
            if (opt != null && (best == null || opt.score(p) > best.score(p))) best = opt;
        }
        return best;
    }

    private Option bestObsidian(Observation o, Loadout l, double ex, double ey, double ez, double px, double py, double pz) {
        if (l.slot("obsidian") < 0 || l.slot("end_crystal") < 0) return null;
        Option best = null;
        int fy = (int) Math.floor(py);
        for (int[] b : around(px, pz, 2, fy - 1, fy)) {
            if (!placer.canPlaceBlock(world, b[0], b[1], b[2])) continue;
            // A crystal must fit on top afterward.
            if (world.kind(b[0], b[1] + 1, b[2]) != Kind.AIR || world.entityIn(b[0], b[1] + 1, b[2], b[0] + 1, b[1] + 3, b[2] + 1)) continue;
            Placer.Spot s = placer.blockSpot(world, ex, ey, ez, b[0], b[1], b[2]);
            if (s == null) { note("obsidian spots: " + placer.lastFail); sample(b, ex, ey, ez); continue; }
            if (placer.skipped("obsidian@" + b[0] + "," + b[1] + "," + b[2], o.tick())) continue;
            // Score the crystal as if the obsidian were already there, since the new block can shield the target.
            Option opt = scoreBlast(world.with(b[0], b[1], b[2], Kind.OBSIDIAN), "obsidian", Explosions.CRYSTAL_POWER, b[0] + 0.5, b[1] + 1, b[2] + 0.5, o, px, py, pz, s);
            if (opt != null && (best == null || opt.score(p) > best.score(p))) best = opt;
        }
        return best;
    }

    private Option bestAnchorPlace(Observation o, Loadout l, double ex, double ey, double ez, double px, double py, double pz) {
        if (l.slot("respawn_anchor") < 0 || l.slot("glowstone") < 0) return null;
        Option best = null;
        int fy = (int) Math.floor(py);
        for (int[] b : around(px, pz, 1, fy, fy + 1)) {
            if (!placer.canPlaceBlock(world, b[0], b[1], b[2])) continue;
            Placer.Spot s = placer.blockSpot(world, ex, ey, ez, b[0], b[1], b[2]);
            if (s == null) { note("anchor spots: " + placer.lastFail); sample(b, ex, ey, ez); continue; }
            if (placer.skipped("anchor@" + b[0] + "," + b[1] + "," + b[2], o.tick())) continue;
            Option opt = scoreBlast(world, "anchor", Explosions.ANCHOR_POWER, b[0] + 0.5, b[1] + 0.5, b[2] + 0.5, o, px, py, pz, s);
            if (opt != null && (best == null || opt.score(p) > best.score(p))) best = opt;
        }
        return best;
    }

    /** The anchor this bot placed: charge it, then set it off if the blast is worth it. */
    private Option anchorStep(Observation o, Loadout l, double ex, double ey, double ez, double px, double py, double pz) {
        if (anchor == null) return null;
        int x = anchor[0], y = anchor[1], z = anchor[2];
        Placer.Spot s = placer.anySpot(world, ex, ey, ez, x, y, z);
        if (s == null) return null;
        int charge = world.anchorCharge(x, y, z);
        if (charge <= 0) {
            if (l.slot("glowstone") < 0 || placer.skipped("charge@" + x + "," + y + "," + z, o.tick())) return null;
            return new Option("charge", 0, 0, s, -1, x + 0.5, y + 0.5, z + 0.5);
        }
        // The anchor is gone by the time it blows, so it does not shield anyone from its own blast.
        WorldView without = world.with(x, y, z, Kind.AIR);
        double t = Explosions.toPlayer(without, x + 0.5, y + 0.5, z + 0.5, Explosions.ANCHOR_POWER, px, py, pz, false);
        double self = Explosions.toPlayer(without, x + 0.5, y + 0.5, z + 0.5, Explosions.ANCHOR_POWER, o.x(), o.y(), o.z(), true);
        if (!acceptable(o, t, self) || placer.skipped("detonate@" + x + "," + y + "," + z, o.tick())) return null;
        return new Option("detonate", t, self, s, -1, x + 0.5, y + 0.5, z + 0.5);
    }

    private Option scoreBlast(WorldView w, String what, float power, double cx, double cy, double cz, Observation o, double px, double py, double pz, Placer.Spot s) {
        double t = Explosions.toPlayer(w, cx, cy, cz, power, px, py, pz, false);
        double self = Explosions.toPlayer(w, cx, cy, cz, power, o.x(), o.y(), o.z(), true);
        if (!acceptable(o, t, self)) return null;
        return new Option(what, t, self, s, -1, cx, cy, cz);
    }

    // ---------------------------------------------------------------- helpers

    /** Hotbar slot for the item the click needs. Setting off an anchor needs anything that is not glowstone or a block. */
    private static int slotFor(Loadout l, String what) {
        return switch (what) {
            case "obsidian" -> l.slot("obsidian");
            case "crystal" -> l.slot("end_crystal");
            case "anchor" -> l.slot("respawn_anchor");
            case "charge" -> l.slot("glowstone");
            case "detonate" -> {
                int s = l.slot(Loadout::isSword);
                if (s < 0) s = l.slot(id -> id.equals("totem_of_undying") || id.contains("golden_apple"));
                if (s < 0) s = l.slot("air");
                yield s;
            }
            default -> -1;
        };
    }

    private static String itemFor(String what) {
        return switch (what) {
            case "obsidian" -> "obsidian";
            case "crystal" -> "end_crystal";
            case "anchor" -> "respawn_anchor";
            case "charge" -> "glowstone";
            default -> "sword";
        };
    }

    /** Sprint jumps while running in, which is faster than plain sprinting. */
    private void sprintJump(Observation o, BotAction a) {
        if (p.sprintJump == 1 && o.onGround() && a.forward > 0 && a.sprint && o.distance() > p.sprintJumpBeyond) {
            a.jump = true;
            if (a.technique.isEmpty()) a.technique = "sprint_jump";
        }
    }

    /** Keeps a set distance from the target and strafes, so it is hard to crystal back. */
    private long eatStart = -1;

    /** True while it is partway through an apple. */
    public boolean eating() {
        return eatStart >= 0;
    }

    /** Starts or keeps an apple going, or returns null to go on with crystals. */
    private BotAction eatStep(Observation o, Loadout l, BotAction a, boolean killHit) {
        if (p.crystalEat != 1) return null;
        long t = o.tick();
        double hp = o.effectiveHealth();
        int ench = l.slot("enchanted_golden_apple"), gold = l.slot("golden_apple");
        // An enchanted apple when it is very low, since it gives four times the absorption and fire resistance.
        int apple = hp <= p.crystalEgapHealth && ench >= 0 ? ench : gold >= 0 ? gold : ench;
        if (eatStart >= 0 && (t - eatStart > 33 || apple < 0 || killHit)) eatStart = -1;
        if (eatStart < 0) {
            if (apple < 0 || killHit || placer.busy()) return null;
            // Absorption still up means the last apple is still working. Eat again once it is gone.
            boolean low = hp <= p.crystalEatHealth || (o.absorption() <= 0 && o.health() <= p.crystalTopUpHealth && noOptionTicks > 10);
            if (!low) return null;
            eatStart = t;
        }
        a.hotbar = apple;
        a.use = true;
        a.attack = false;
        a.attackEntity = -1;
        a.technique = "crystal_eat";
        state = "CRYSTAL_EAT";
        log.action(o, "eat", l.hotbar(apple), Double.NaN, 0);
        return a;
    }

    private void move(Observation o, BotAction a) {
        double d = o.distance();
        a.forward = d > p.crystalRange + 0.6 ? 1 : d < p.crystalRange - 1.2 ? -1 : 0;
        a.sprint = a.forward > 0;
        if (o.tick() >= nextStrafe) {
            strafeDir = -strafeDir;
            nextStrafe = o.tick() + 10 + (o.tick() * 7) % 15;
        }
        a.strafe = strafeDir;
    }

    private static List<int[]> around(double x, double z, int r, int y0, int y1) {
        List<int[]> out = new ArrayList<>();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int y = y0; y <= y1; y++) out.add(new int[] {bx + dx, y, bz + dz});
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double dist(double ax, double ay, double az, double bx, double by, double bz) {
        return Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
    }
}
