package dev.reality.fightbot.core;

import dev.reality.fightbot.core.world.WorldView;

/**
 * Low on health against someone using end crystals or respawn anchors: get out of blast range, heal, then come back.
 *
 * The recorded elytra mace fights against a crystal player were lost the same way every time. The bot kept diving
 * or fighting on foot at two or three hearts, right on top of the crystals, and popped totem after totem until it
 * ran out. A blast reaches about twelve blocks, so a sword or a dive at low health is a death against them.
 *
 * - **Who counts.** An opponent seen holding crystals, anchors or glowstone, or standing next to a crystal, in
 *   the last twenty seconds.
 * - **Getting away.** Gliding: steer straight away and rocket while slow. On the ground: pearl away when it has
 *   pearls, otherwise put the elytra on and take off away from them, otherwise sprint away.
 * - **Healing.** Once far enough off, eat a golden apple, in the air or on the ground, and do not rocket or switch
 *   items while eating, since that cancels the bite. Splash potions still come from the potion layer.
 * - **Coming back.** Once health is back up, when there is nothing left to heal with, or after a while.
 */
public final class BlastGuard {
    private final BotProfile p;
    private WorldView world;
    private long lastBlastSeen = -100000;
    private boolean retreating;
    private long retreatStart = -1;
    private long eatStart = -1;
    private long lastRocket = -1000;
    private int takeoffTicks;
    public String lastWhy = "";
    /** Health to start getting away at, and to come back at. Each style sets its own. */
    public double retreatAt, resumeAt;

    public BlastGuard(BotProfile p) {
        this.p = p;
        this.retreatAt = p.blastRetreatHealth;
        this.resumeAt = p.blastResumeHealth;
    }

    public void setWorld(WorldView world) {
        this.world = world;
    }

    /** Remembers whether the opponent fights with blasts. Called every tick. */
    public void observe(Observation o) {
        if (!o.hasOpponent()) return;
        String held = o.oMainHand() == null ? "" : o.oMainHand();
        if (held.equals("end_crystal") || held.equals("respawn_anchor") || held.equals("glowstone")) lastBlastSeen = o.tick();
        if (world != null) {
            for (WorldView.Crystal c : world.crystals()) {
                if (Math.hypot(c.x() - o.ox(), c.z() - o.oz()) <= 6 && Math.abs(c.y() - o.oy()) <= 4) {
                    lastBlastSeen = o.tick();
                    break;
                }
            }
        }
    }

    /** True when the opponent has used blasts recently. */
    public boolean blaster(long tick) {
        return tick - lastBlastSeen <= p.blastMemoryTicks;
    }

    /** True while it is partway through an apple, so nothing else should switch items. */
    public boolean eating() {
        return eatStart >= 0;
    }

    public boolean retreating() {
        return retreating;
    }

    /** Fills in the retreat or the heal and returns true, or returns false to let the fight go on. */
    public boolean step(Observation o, BotAction a) {
        if (p.blastGuard != 1 || !o.hasOpponent()) return stop();
        long t = o.tick();
        Loadout l = o.loadout();
        double hp = o.effectiveHealth();
        int apple = appleSlot(l, hp);
        boolean canHeal = apple >= 0 || l.potions("splash_potion", "healing") > 0 || l.apples() > 0;
        if (!retreating) {
            if (!blaster(t) || hp > retreatAt || !canHeal) return stop();
            retreating = true;
            retreatStart = t;
            takeoffTicks = 0;
        } else if (hp >= resumeAt || (!canHeal && eatStart < 0) || t - retreatStart > p.blastMaxRetreatTicks) {
            return stop();
        }
        double dist = o.distance3d();
        // Already eating: keep the bite going. Only turn away, never switch items.
        if (eatStart >= 0) {
            // An apple takes 32 ticks. After that, or if the apples ran out, it decides again.
            if (t - eatStart > 33 || apple < 0) {
                eatStart = -1;
            } else {
                a.hotbar = apple;
                a.use = true;
                steerAway(o, a, false);
                a.state = "BLAST_HEAL";
                a.technique = "eat_away_from_blasts";
                return true;
            }
        }
        boolean far = dist >= p.blastSafeDistance;
        if (far && apple >= 0 && hp < resumeAt) {
            eatStart = t;
            a.hotbar = apple;
            a.use = true;
            steerAway(o, a, false);
            a.state = "BLAST_HEAL";
            a.technique = "eat_away_from_blasts";
            return true;
        }
        if (far) {
            // Far enough and nothing to eat in the hotbar: bring apples down, and hold the distance meanwhile.
            if (apple < 0 && l.apples() > 0) a.restock = l.count("golden_apple") > 0 ? "golden_apple" : "enchanted_golden_apple";
            steerAway(o, a, o.gliding());
            a.state = "BLAST_HEAL";
            a.technique = "wait_out_of_blast_range";
            return true;
        }
        a.state = "BLAST_RETREAT";
        if (o.gliding()) {
            steerAway(o, a, true);
            a.technique = "glide_away_from_blasts";
            return true;
        }
        if (o.onGround()) {
            // Pearls first: one throw clears the blast range at once.
            if (pearlAway != null && pearlAway.test(o, a)) {
                a.state = "BLAST_RETREAT";
                a.technique = "pearl_away_from_blasts";
                return true;
            }
            int rockets = l.slot("firework_rocket");
            if (l.hasElytra() && rockets >= 0 && l.fireworks() >= 2) {
                if (!l.wearingElytra()) a.equip = "elytra";
                a.yaw = (float) Angles.wrap(o.yawToOpponent() + 180);
                a.pitch = -20;
                a.forward = 1;
                a.sprint = true;
                a.jump = true;
                a.hotbar = rockets;
                takeoffTicks = 1;
                a.technique = "take_off_away_from_blasts";
                return true;
            }
            steerAway(o, a, false);
            a.forward = 1;
            a.sprint = true;
            a.jump = t % 6 == 0;
            a.technique = "run_away_from_blasts";
            return true;
        }
        // Just jumped for the takeoff: open the elytra and rocket away.
        if (takeoffTicks > 0 && l.wearingElytra()) {
            takeoffTicks = 0;
            a.startGlide = true;
            steerAway(o, a, false);
            a.pitch = -25;
            rocket(o, a);
            a.technique = "take_off_away_from_blasts";
            return true;
        }
        steerAway(o, a, false);
        a.forward = 1;
        a.sprint = true;
        a.technique = "fall_away_from_blasts";
        return true;
    }

    /** Hook for the pearl throw, so this class does not need the mobility layer itself. */
    public interface PearlAway {
        boolean test(Observation o, BotAction a);
    }

    public PearlAway pearlAway;

    private boolean stop() {
        retreating = false;
        eatStart = -1;
        takeoffTicks = 0;
        return false;
    }

    /** Faces straight away from the opponent. Gliding, it climbs gently and rockets once the glide gets slow. */
    private void steerAway(Observation o, BotAction a, boolean rocketIfSlow) {
        a.yaw = (float) Angles.wrap(o.yawToOpponent() + 180);
        a.attack = false;
        if (o.gliding()) {
            double above = heightAboveGround(o);
            double speed = Math.sqrt(o.vx() * o.vx() + o.vy() * o.vy() + o.vz() * o.vz());
            // Low over the ground: climb. Otherwise a shallow glide keeps speed without losing much height.
            a.pitch = above < p.blastCruiseHeight ? -25 : 5;
            if (rocketIfSlow && eatStart < 0 && speed < 1.0 && o.tick() - lastRocket > p.blastRocketTicks) rocket(o, a);
        } else {
            a.pitch = 0;
            a.forward = 1;
            a.sprint = true;
        }
    }

    private void rocket(Observation o, BotAction a) {
        int s = o.loadout().slot("firework_rocket");
        if (s < 0) return;
        a.hotbar = s;
        a.useOnce = true;
        lastRocket = o.tick();
    }

    /** An enchanted apple when very low, otherwise a plain one. */
    private static int appleSlot(Loadout l, double hp) {
        int gold = l.slot("golden_apple"), ench = l.slot("enchanted_golden_apple");
        if (hp <= 6 && ench >= 0) return ench;
        return gold >= 0 ? gold : ench;
    }

    private double heightAboveGround(Observation o) {
        if (world == null) return 100;
        int x = (int) Math.floor(o.x()), z = (int) Math.floor(o.z()), y = (int) Math.floor(o.y());
        for (int d = 0; d <= 40; d++) {
            if (y - d < world.minY()) return 100;
            if (world.solid(x, y - d, z) || world.water(x, y - d, z)) return o.y() - (y - d + 1);
        }
        return 41;
    }
}
