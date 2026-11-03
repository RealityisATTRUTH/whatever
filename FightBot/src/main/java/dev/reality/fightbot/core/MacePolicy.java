package dev.reality.fightbot.core;

import java.util.Random;

/**
 * Mace styles. Both get height first, then fall onto the opponent and smash, because the mace
 * only deals its bonus damage after falling more than 1.5 blocks, and never while gliding.
 *
 * Elytra mace: swap the elytra on, jump, open it, rocket up, glide over the opponent, swap the
 * chestplate back on to stop gliding, then fall and smash. After a smash the fall is cancelled,
 * so it swaps the elytra on again in the air and relaunches.
 *
 * Wind charge mace: get close, jump and throw a wind charge at its own feet to launch straight up,
 * steer over the opponent in the air, and smash on the way down. Between launches it fights with
 * the sword style on the floor instead of backing away.
 *
 * With an axe in the hotbar and the opponent holding a shield up, it hits the shield with the axe
 * and smashes with the mace inside the same tick. With attribute swaps on, it falls holding a charged
 * sword and switches to the mace inside the hit. With a Lunge spear, it jabs in the air to dash
 * sideways when a drop is going to miss.
 */
public final class MacePolicy implements Policy {
    public enum Phase { GROUND, CLIMB, GLIDE, FALL, RISE }

    private static final double GRAVITY = 0.08, AIR_DRAG = 0.91;

    private final BotProfile p;
    private final boolean elytra;
    /** Wind charge mace fights with the sword style while on the floor between launches. */
    private final SwordPolicy groundFighter;
    /** The elytra, chestplate, elytra, chestplate, axe, mace combo. */
    public final ElytraCombo combo;
    private final Random rng;
    private Phase phase = Phase.GROUND;
    private long lastRocket = -1000;
    private boolean axeHitThisFall;
    private boolean smashedThisFall;
    private int glideTicks;
    private long lastLunge = -1000;
    private double lastFall;
    private float yaw, pitch;
    private boolean aimSet;

    public MacePolicy(BotProfile profile, boolean elytra) {
        this.p = profile;
        this.groundFighter = new SwordPolicy(profile);
        this.combo = new ElytraCombo(profile);
        this.elytra = elytra;
        this.rng = new Random(profile.seed ^ 0x6d616365L);
    }

    @Override
    public String stateName() {
        if (combo != null && combo.active()) return "ELYTRA_COMBO_" + combo.step().name();
        return (elytra ? "ELYTRA_" : "WIND_") + phase.name();
    }

    public Phase phase() {
        return phase;
    }

    @Override
    public BotAction step(Observation o) {
        if (!aimSet) { yaw = o.yaw(); pitch = o.pitch(); aimSet = true; }
        if (!o.hasOpponent() || o.stale()) return BotAction.idle(o.yaw(), o.pitch());
        BotAction a = new BotAction();
        if (elytra) {
            BotAction ground = elytraStep(o, a);
            if (ground != null) return ground;
        } else {
            BotAction ground = windStep(o, a);
            if (ground != null) return ground;
        }
        a.state = stateName();
        return a;
    }

    // ---------------------------------------------------------------- elytra mace

    private boolean blaster;

    /** Told each tick whether the opponent fights with crystals or anchors. */
    public void setBlaster(boolean b) {
        blaster = b;
    }

    private long lastEquipRequest = -1000;
    private String equipWant;
    private int failedEquips;
    private int targetAirTicks;
    private long lastFollow = -1000;
    private double launchHeight = Double.NaN;
    private long lastSmashTick = -1000;
    private boolean followJumped;
    private long lastCovered = -1000;
    private double coverX, coverZ;

    /**
     * The target is really up in the air, not just hopping: shooting up fast, off the ground for a while,
     * or at least 2 blocks above a bot that is standing on the ground.
     */
    private boolean targetUp(Observation o) {
        if (o.oOnGround()) return false;
        return o.ovy() > 0.45 || targetAirTicks >= 8 || (o.onGround() && o.oy() - o.y() >= 2);
    }

    /** Asks for a chest swap, but not again while the last request may still be on its way. */
    private void requestEquip(Observation o, BotAction a, String what) {
        if (o.tick() - lastEquipRequest <= p.comboConfirmTicks) return;
        a.equip = what;
        equipWant = what;
        lastEquipRequest = o.tick();
    }

    /** Once a swap request has had time to land, checks whether it did. Only failures in a row count. */
    private void checkEquip(Observation o) {
        if (equipWant == null || o.tick() - lastEquipRequest <= p.comboConfirmTicks * 2L) return;
        Loadout l = o.loadout();
        boolean ok = equipWant.equals("elytra") ? l.wearingElytra() : l.chest.endsWith("_chestplate");
        failedEquips = ok ? 0 : failedEquips + 1;
        equipWant = null;
    }

    /** True once chest swaps keep failing, so the elytra styles cannot work here. */
    public boolean gaveUp() {
        return failedEquips >= 3;
    }

    /** Returns the ground fighter's action when fighting on foot, or null when this policy filled in a. */
    private BotAction elytraStep(Observation o, BotAction a) {
        checkEquip(o);
        targetAirTicks = o.oOnGround() ? 0 : targetAirTicks + 1;
        if (gaveUp() && !combo.active()) {
            // Swaps keep failing: glide down without rockets so the sword style can take over on the ground.
            aim(o, a, o.yawToOpponent(), 25);
            a.technique = "land_to_switch_style";
            return null;
        }
        if (combo.active()) {
            if (combo.step(o, a)) return null;
            // The combo is over: relaunch from wherever it left the bot.
            a.state = "";
            a.technique = combo.outcome().equals("hit") ? "combo_hit" : "combo_" + (combo.outcome().startsWith("abandoned") ? "abandoned" : combo.outcome());
            smashedThisFall = combo.outcome().equals("hit");
            if (smashedThisFall) lastSmashTick = o.tick();
            if (combo.outcome().contains("swap was not confirmed")) failedEquips++;
            else if (combo.outcome().equals("hit")) failedEquips = 0;
            phase = o.gliding() ? Phase.CLIMB : o.onGround() ? Phase.GROUND : Phase.RISE;
            glideTicks = 0;
            return null;
        }
        // The target is up in the air, for example knocked up by a hit or launched by a wind charge.
        // Chase it right away with a short rocket instead of climbing high, which is easy to read.
        // A target knocked out from under a roof for a moment steps right back, so a roof seen in the last
        // couple of seconds still counts while they are near it. Otherwise every hit wastes a takeoff.
        if (o.opponentCovered(p.minDropHeadroom)) {
            lastCovered = o.tick();
            coverX = o.ox();
            coverZ = o.oz();
        }
        boolean covered = o.opponentCovered(p.minDropHeadroom) || o.botCovered(3)
                || (o.tick() - lastCovered <= p.coverMemoryTicks && Math.hypot(o.ox() - coverX, o.oz() - coverZ) <= p.coverMemoryRadius);
        // Right after its own smash knocks them off the ground, the follow-up starts at once. A hit only knocks a
        // player up about 0.4 blocks a tick, so waiting for them to fly up fast would let them land first.
        // With Wind Burst the smash throws it back up for free, so it rides that bounce down instead.
        boolean justSmashed = o.tick() - lastSmashTick <= p.followAfterSmashTicks && !o.oOnGround() && o.vy() < 0.5;
        boolean targetUp = targetUp(o) || justSmashed;
        if (p.followUp == 1 && !o.onGround() && targetUp && !covered && o.distance3d() <= p.followRange
                && (o.tick() - lastFollow > 15 || followJumped || justSmashed) && combo.followMissing(o) == null) {
            followJumped = false;
            lastFollow = o.tick();
            if (combo.beginFollowUp(o, lastSmashTick + p.hurtCooldownTicks)) {
                combo.step(o, a);
                return null;
            }
        }
        Loadout l = o.loadout();
        int fireworkSlot = l.slot("firework_rocket");
        double height = o.heightAbove();

        if (covered) {
            BotAction g = underCover(o, a);
            if (g != null) return g;
            return null;
        }
        // On the ground with the target up in the air: jump with the elytra going on, and follow up next tick.
        if (o.onGround() && p.followUp == 1 && targetUp && !covered && o.distance3d() <= p.followRange && l.slot("firework_rocket") >= 0
                && l.hasElytra() && o.effectiveHealth() > p.comboAbortHealth) {
            if (!l.wearingElytra()) requestEquip(o, a, "elytra");
            aim(o, a, o.yawToOpponent(), -20);
            a.jump = true;
            a.forward = 1;
            a.sprint = true;
            a.technique = "follow_jump";
            followJumped = true;
            return null;
        }
        if (o.onGround()) {
            String missed = phase == Phase.FALL && !smashedThisFall ? "missed_drop" : "";
            phase = Phase.GROUND;
            resetFall();
            // Up close on the ground, fight on foot with the chestplate on. The elytra has no armor.
            // Not against a crystal player while it can still fly: on foot next to them is where the blasts land.
            boolean flyInstead = blaster && l.hasElytra() && l.fireworks() >= p.minFireworks && l.slot("firework_rocket") >= 0;
            if (o.distance() <= p.elytraGroundFight && o.oOnGround() && !flyInstead) {
                BotAction g = groundFighter.step(o);
                if (l.wearingElytra() && l.slot(Loadout::isChestplate) >= 0 && g.hotbar < 0 && !g.use) requestEquip(o, g, "chestplate");
                g.state = "ELYTRA_ON_FOOT";
                if (g.technique.isEmpty()) g.technique = missed;
                yaw = g.yaw;
                pitch = g.pitch;
                return g;
            }
            a.technique = missed;
            // Each launch climbs to a slightly different height, so the drop is harder to time.
            launchHeight = p.maceHeight + rng.nextGaussian() * p.heightJitter;
            launchHeight = Math.max(p.maceMinHeight + 2, Math.min(p.maceHeight + 2 * p.heightJitter, launchHeight));
            // Fast mode: a short climb, a block either way, so the dive comes before they can read it.
            if (p.fastElytra == 1) launchHeight = p.fastClimbHeight + rng.nextGaussian() * 0.7;
            // Put the elytra on, then sprint-jump toward the opponent to take off.
            if (!l.wearingElytra()) {
                requestEquip(o, a, "elytra");
                if (a.technique.isEmpty()) a.technique = "equip_elytra";
            }
            aim(o, a, o.yawToOpponent(), -10);
            a.forward = 1;
            a.sprint = true;
            a.jump = true;
            if (fireworkSlot >= 0) a.hotbar = fireworkSlot;
            return null;
        }

        if (!o.gliding() && phase != Phase.FALL) {
            // In the air and not falling on purpose: just jumped, or bounced off a smash. Take off.
            if (!l.wearingElytra()) {
                requestEquip(o, a, "elytra");
                a.technique = "equip_elytra";
                aim(o, a, o.yawToOpponent(), -30);
                if (fireworkSlot >= 0) a.hotbar = fireworkSlot;
                return null;
            }
            a.startGlide = true;
            phase = Phase.CLIMB;
            aim(o, a, o.yawToOpponent(), o.distance() < 4 ? -88 : -60);
            rocket(o, a, fireworkSlot, "takeoff");
            return null;
        }

        if (phase == Phase.FALL) {
            fall(o, a);
            return null;
        }

        // Gliding.
        if (phase == Phase.CLIMB) {
            aim(o, a, o.yawToOpponent(), o.distance() < 4 ? -88 : -60);
            glideTicks = 0;
            if (height >= (Double.isNaN(launchHeight) ? p.maceHeight : launchHeight)) phase = Phase.GLIDE;
            else if (o.tick() - lastRocket > 22 && o.vy() < 0.4) rocket(o, a, fireworkSlot, "climb");
            if (phase == Phase.CLIMB) return null;
        }

        // GLIDE: drop when a fall from here, upward speed included, lands near where the opponent will be.
        glideTicks++;
        double[] land = landing(o);
        double leadX = o.ox() + o.ovx() * land[2], leadZ = o.oz() + o.ovz() * land[2];
        double miss = Math.hypot(land[0] - leadX, land[1] - leadZ);
        // Air control moves the bot about a quarter block per tick at most, so it can fix part of a miss.
        double steer = Math.min(4, 0.012 * land[2] * land[2] / 2 + 0.5);
        double apex = height + Math.max(0, o.vy()) * Math.max(0, o.vy()) / (2 * GRAVITY);
        boolean highEnough = apex >= (p.fastElytra == 1 ? p.fastClimbHeight - 1 : p.maceMinHeight);
        // The combo drops early and re-glides to steer, so it starts as soon as the bot is high and close enough.
        boolean comboWindow = p.combo == 1 && highEnough && miss <= 10 && combo.missing(o) == null;
        if (comboWindow && combo.begin(o)) {
            combo.step(o, a);
            return null;
        }
        if (highEnough && (miss <= Math.max(1.5, steer) || glideTicks > 80)) {
            a.equip = "chestplate";
            phase = Phase.FALL;
            resetFall();
            a.technique = "drop";
            aim(o, a, o.yawToOpponent(), o.pitchToOpponent());
            int mace = l.slot("mace");
            if (mace >= 0) a.hotbar = mace;
            return null;
        }
        double dx = leadX - o.x(), dz = leadZ - o.z();
        float leadYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        // Far: fly level toward it. Close with too much speed: pull up to bleed speed and gain height.
        double toLead = Math.hypot(dx, dz);
        float glidePitch = toLead > 12 ? 0 : -35;
        // Already well above the planned height, for example with a rocket still burning: glide down toward
        // them instead of pulling up, or it keeps climbing and the drop comes from far too high.
        double planned = Double.isNaN(launchHeight) ? (p.fastElytra == 1 ? p.fastClimbHeight : p.maceHeight) : launchHeight;
        if (height > planned + 3) glidePitch = 25;
        aim(o, a, leadYaw, glidePitch);
        if (!highEnough && o.tick() - lastRocket > 22) {
            aim(o, a, leadYaw, -50);
            rocket(o, a, fireworkSlot, "climb");
        }
        return null;
    }

    /**
     * A roof over the target, or over the bot, blocks a mace drop. On the ground it fights on foot with the
     * sword and chestplate, and its sprint hits knock the target out from under the roof. In the air it
     * stops rocketing and glides down next to the target. Returns the ground fighter's action, or null.
     */
    private BotAction underCover(Observation o, BotAction a) {
        Loadout l = o.loadout();
        phase = o.onGround() ? Phase.GROUND : phase;
        if (o.onGround() && o.y() - o.oy() > 1.5) {
            // Standing on the roof the target hides under: walk off its edge first.
            resetFall();
            aim(o, a, o.yawToOpponent() + 180, 0);
            a.forward = 1;
            a.sprint = true;
            a.technique = "get_down";
            if (l.wearingElytra() && l.slot(Loadout::isChestplate) >= 0) requestEquip(o, a, "chestplate");
            return null;
        }
        // On the ground, or just hopping, like a jump reset or a sprint jump: keep fighting on foot.
        if (o.onGround() || (!o.gliding() && Math.abs(o.heightAbove()) < 2)) {
            if (o.onGround()) resetFall();
            BotAction g = groundFighter.step(o);
            if (l.wearingElytra() && l.slot(Loadout::isChestplate) >= 0 && g.hotbar < 0 && !g.use) requestEquip(o, g, "chestplate");
            g.state = "COVERED_ON_FOOT";
            // No sprint jumps under a roof: the head hits the ceiling, and the jump lands the bot in a hit trade.
            if (g.technique.equals("sprint_jump")) {
                g.jump = false;
                g.technique = "";
            }
            if (g.technique.isEmpty()) g.technique = "hit_out";
            yaw = g.yaw;
            pitch = g.pitch;
            return g;
        }
        aim(o, a, o.yawToOpponent(), o.gliding() ? 30 : o.pitchToOpponent());
        // Close to the ground, swap the chestplate back on so it lands with armor.
        if (o.gliding() && o.heightAbove() < 3 && l.slot(Loadout::isChestplate) >= 0) requestEquip(o, a, "chestplate");
        if (!o.gliding()) {
            a.forward = 1;
            a.sprint = true;
        }
        a.technique = "land_near_covered_target";
        return null;
    }

    private void rocket(Observation o, BotAction a, int slot, String why) {
        if (slot < 0) return;
        a.hotbar = slot;
        a.useOnce = true;
        lastRocket = o.tick();
        if (a.technique.isEmpty()) a.technique = "rocket_" + why;
    }

    // ---------------------------------------------------------------- wind charge mace

    /** Returns the ground fighter's action while waiting on the floor, or null when this policy filled in a. */
    private BotAction windStep(Observation o, BotAction a) {
        Loadout l = o.loadout();
        int wind = l.slot("wind_charge");
        double dist = o.distance();
        if (o.onGround()) {
            String missed = (phase == Phase.FALL || phase == Phase.RISE) && !smashedThisFall ? "missed_drop" : "";
            phase = Phase.GROUND;
            resetFall();
            // Launch right next to the opponent: straight up, then straight back down onto them.
            // Launching in front of a full attack bar just eats a hit that knocks the bot away.
            boolean safe = Double.isNaN(o.oCharge()) || o.oCharge() < 0.8 || dist > 2.5 || o.oUsingItem();
            boolean covered = o.opponentCovered(p.minDropHeadroom) || o.botCovered(p.minDropHeadroom);
            if (dist <= p.windLaunchDistance && o.windReady() && wind >= 0 && safe && !covered) {
                aim(o, a, o.yawToOpponent(), 90);
                // Jump and throw the charge straight down at its own feet, then switch back to the sword.
                a.hotbar = wind;
                a.useOnce = true;
                a.jump = true;
                a.forward = dist > 1.2 ? 1 : 0;
                a.sprint = a.forward > 0;
                a.pitch = 90;
                pitch = 90;
                a.technique = "wind_launch";
                phase = Phase.RISE;
                return null;
            }
            // Waiting on the floor, for the charge to cool down or to get close: fight like the sword style,
            // and come in close for the next launch.
            BotAction g = groundFighter.step(o);
            g.state = "WIND_GROUND";
            if (g.technique.isEmpty()) g.technique = missed;
            if (o.windReady() && wind >= 0 && !g.use && g.forward <= 0 && dist > p.windLaunchDistance) {
                g.forward = 1;
                g.sprint = true;
            }
            yaw = g.yaw;
            pitch = g.pitch;
            return g;
        }
        if (o.vy() < 0 && o.fallDistance() > 0.5) phase = Phase.FALL;
        if (phase == Phase.GROUND) phase = Phase.RISE;
        fall(o, a);
        return null;
    }

    // ---------------------------------------------------------------- the smash

    /** Falling or rising toward the opponent: steer over them, hold the mace, smash when in reach. */
    private void fall(Observation o, BotAction a) {
        Loadout l = o.loadout();
        int mace = l.slot("mace");
        int axe = l.slot(Loadout::isAxe);
        double fallTicks = fallTicks(Math.max(0, o.heightAbove()));
        double leadX = o.ox() + o.ovx() * fallTicks, leadZ = o.oz() + o.ovz() * fallTicks;
        double dx = leadX - o.x(), dz = leadZ - o.z();
        double toLead = Math.hypot(dx, dz);
        aim(o, a, o.yawToOpponent(), o.pitchToOpponent());
        // Air control works on the input direction relative to the look direction.
        if (toLead > 0.4) {
            double moveYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = Angles.wrap(moveYaw - a.yaw);
            a.forward = Math.abs(rel) < 67.5 ? 1 : Math.abs(rel) > 112.5 ? -1 : 0;
            a.strafe = rel > 22.5 && rel < 157.5 ? -1 : rel < -22.5 && rel > -157.5 ? 1 : 0;
            // Sprint stays on in a wind charge fall: the short drop needs the air control more than a crit. In the
            // simulator, letting go of it near the smash tripled the drops that missed.
            a.sprint = a.forward > 0;
        }

        // With attribute swaps, it holds a charged sword during the fall and switches to the mace inside the hit.
        int sword = l.slot(Loadout::isSword);
        int carrier = p.attributeSwap == 1 && sword >= 0 ? sword : mace;
        boolean holdingCarrier = carrier >= 0 && o.mainHand().equals(l.hotbar(carrier));

        // A smash cancels the fall: the fall distance drops to nothing while still in the air.
        if (!smashedThisFall && lastFall > 1.5 && o.fallDistance() < 0.5 && !o.onGround()) {
            smashedThisFall = true;
            lastSmashTick = o.tick();
            phase = Phase.RISE;
        }
        lastFall = o.fallDistance();
        // Wind Burst throws it back up after a smash. Coming down again is a new drop, and a new smash.
        if (smashedThisFall && o.vy() < 0 && o.fallDistance() > 1.5 && o.tick() - lastSmashTick > 4) {
            smashedThisFall = false;
            lastFall = o.fallDistance();
        }
        if (smashedThisFall) {
            if (carrier >= 0 && !holdingCarrier) a.hotbar = carrier;
            return;
        }
        boolean canSmash = o.fallDistance() > 1.5 && !o.gliding() && o.vy() < 0;
        boolean reach = inReachSoon(o);
        // Smash damage grows with the fall, so wait for the last tick still in reach before landing or passing by.
        double nvy = (o.vy() - GRAVITY) * 0.98;
        double ny = o.y() + nvy;
        boolean stillInReachNext = reachFrom(o, o.x() + o.vx() + o.ovx(), ny, o.z() + o.vz() + o.ovz()) <= 2.8
                && ny > o.oy() + 0.2;
        // Against a raised shield, the axe and the mace both hit inside one tick, so it never waits.
        boolean stunPlanned = o.oBlocking() && p.stunSlam == 1 && axe >= 0 && mace >= 0;
        // Also wait while the bar is still filling, if it will still be in reach next tick: a full bar is a crit,
        // 1.5 times the whole smash, and the weapon's own damage at full instead of a third of it.
        boolean filling = p.critSmash == 1 && o.charge() < 0.92;
        boolean hold = (p.waitForDeeperSmash == 1 || filling) && stillInReachNext && !stunPlanned;
        if (canSmash && reach && !hold && mace >= 0) {
            if (stunPlanned) {
                a.swapAttack = new int[] {axe, mace};
                a.swapBack = carrier;
                a.technique = "stun_slam";
                return;
            }
            if (carrier != mace) {
                a.swapAttack = new int[] {mace};
                a.swapBack = carrier;
                a.technique = "swap_smash";
                return;
            }
            a.hotbar = mace;
            a.attack = true;
            a.technique = "smash";
            return;
        }
        if (carrier >= 0 && !holdingCarrier) a.hotbar = carrier;

        double[] land = landing(o);
        double missBy = Math.hypot(land[0] - (o.ox() + o.ovx() * land[2]), land[1] - (o.oz() + o.ovz() * land[2]));
        boolean willMiss = missBy > 2.6;
        // A Lunge jab dashes sideways through the air, which fixes a drop that is going to miss.
        if (p.useLunge == 1 && l.canLunge() && !o.gliding() && willMiss && !reach && o.heightAbove() > 2 && o.tick() - lastLunge > 15) {
            double ldx = o.ox() + o.ovx() * land[2] - land[0], ldz = o.oz() + o.ovz() * land[2] - land[1];
            a.jabSlot = l.lungeSlot;
            a.swapBack = carrier;
            a.yaw = (float) Math.toDegrees(Math.atan2(-ldx, ldz));
            a.pitch = 0;
            yaw = a.yaw;
            pitch = 0;
            a.technique = "air_lunge";
            lastLunge = o.tick();
            return;
        }
        // A long fall that will miss would hurt badly on landing, so open the elytra again and pull up.
        if (elytra && o.fallDistance() > 6 && o.heightAbove() < 7 && willMiss && !reach
                && o.loadout().has("elytra") && !o.gliding()) {
            a.equip = "elytra";
            a.startGlide = true;
            a.technique = "glide_save";
            aim(o, a, o.yawToOpponent(), -30);
            phase = Phase.CLIMB;
        }
    }

    private void resetFall() {
        axeHitThisFall = false;
        smashedThisFall = false;
        lastFall = 0;
    }

    /**
     * True when the opponent's hitbox is within reach of where the bot is now. The swing happens at the
     * start of the next server tick, before the bot moves again, so the current position is the one that counts.
     */
    static boolean inReachSoon(Observation o) {
        return reachFrom(o, o.x(), o.y(), o.z()) <= 3.0;
    }

    /** Where a fall with no elytra lands at the opponent's head height: x, z, and ticks. */
    static double[] landing(Observation o) {
        double x = o.x(), y = o.y(), z = o.z(), vx = o.vx(), vy = o.vy(), vz = o.vz();
        int t = 0;
        double stop = o.oy() + 1.5;
        while (t < 200 && (y > stop || vy > 0)) {
            x += vx; y += vy; z += vz;
            vy = (vy - GRAVITY) * 0.98;
            vx *= AIR_DRAG; vz *= AIR_DRAG;
            t++;
        }
        return new double[] {x, z, t};
    }

    static double reachFrom(Observation o, double x, double y, double z) {
        double hw = o.loadout().oWidth / 2;
        double hx = Math.max(0, Math.abs(o.ox() - x) - hw);
        double hz = Math.max(0, Math.abs(o.oz() - z) - hw);
        // A smash comes after the glide ends, standing up, so a gliding bot is measured with standing eyes.
        double eye = y + (o.gliding() ? 1.62 : o.loadout().eyeHeight);
        double dy = eye < o.oy() ? o.oy() - eye : eye > o.oTop() ? eye - o.oTop() : 0;
        return Math.sqrt(hx * hx + dy * dy + hz * hz);
    }

    /** Ticks to fall a height with no elytra, starting from a standstill. */
    static double fallTicks(double h) {
        double y = 0, v = 0;
        int t = 0;
        while (y < h && t < 200) {
            v = (v + GRAVITY) * 0.98;
            y += v;
            t++;
        }
        return t;
    }

    private void aim(Observation o, BotAction a, float targetYaw, float targetPitch) {
        float jitter = (float) (rng.nextGaussian() * p.aimJitter);
        yaw = Angles.approach(o.yaw(), targetYaw + jitter, p.maxTurnPerTick * 1.5);
        pitch = Angles.approach(o.pitch(), targetPitch, p.maxTurnPerTick * 1.5);
        a.yaw = yaw;
        a.pitch = Math.max(-90, Math.min(90, pitch));
    }
}
