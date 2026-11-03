package dev.reality.fightbot.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The elytra, rocket, chestplate, elytra, chestplate, axe, mace combo, as explicit steps.
 *
 * The bot is already gliding after a rocket when the combo starts. It swaps to the chestplate to stop
 * gliding and start building fall distance. If the drop is going to miss, it swaps the elytra back on,
 * opens it again, and dives steeply. The fall distance keeps building while it dives faster than half a
 * block per tick, and the glide lets it steer back over the target. Then it swaps to the chestplate again,
 * holds the axe, and switches to the mace for the smash.
 *
 * Every swap and glide is checked against the next observations before the combo moves on. Opening the
 * elytra is its own step, because equipping it never starts a glide by itself. A step that is not
 * confirmed in time is retried, then the combo is abandoned. It also gives up and recovers when the
 * target gets away, line of sight is lost, health is low, or the fall is too short for a smash.
 * Every step is logged with its tick, slot, chest item, velocity, fall distance, target distance, line
 * of sight, attack bar, and result.
 */
public final class ElytraCombo {
    public enum Step { DROP1, FALL1, REEQUIP, REGLIDE, DIVE, F_EQUIP, F_CHASE, DROP2, FALL2, SMASH, CHECK_HIT, RECOVER, DONE }

    /** One logged step. */
    public record Entry(long tick, String step, String action, int slot, String chest, double vx, double vy, double vz,
                        double fallDistance, double targetDistance, boolean lineOfSight, double charge, String result) {
        public String toJson(int combo) {
            return new Json().put("combo", combo).put("tick", tick).put("step", step).put("action", action).put("slot", slot)
                    .put("chest", chest).put("vx", vx, 4).put("vy", vy, 4).put("vz", vz, 4).put("fallDistance", fallDistance, 3)
                    .put("targetDistance", targetDistance, 3).put("lineOfSight", lineOfSight).put("charge", charge, 3)
                    .put("result", result).build();
        }
    }

    private final BotProfile p;
    private Step step = Step.DONE;
    private long stepStart, lastSeen;
    private int retries;
    private boolean axeSelected;
    private int combos, landed, abandoned;
    private String outcome = "";
    private final List<String> log = new ArrayList<>();
    private final List<Entry> current = new ArrayList<>();
    private double hpBeforeSmash = Double.NaN;
    private boolean abandonCounted, axeDecisionLogged;
    /** True for the quick follow-up on a target in the air, false for the full drop combo. */
    private boolean following;
    private double speedAtRocket;
    private boolean wornSeen, openSeen, boostSeen, refiredAfterOpen, rocketAskedWhileOpen;

    public ElytraCombo(BotProfile profile) {
        this.p = profile;
    }

    public boolean active() {
        return step != Step.DONE;
    }

    /** Ends the combo from outside, for when the bot switches to another style mid-combo. */
    public void cancel(String why) {
        if (step == Step.DONE) return;
        outcome = "abandoned: " + why;
        countAbandon();
        step = Step.DONE;
        flush();
    }

    public Step step() {
        return step;
    }

    /** How the last combo ended: "hit", "miss", or why it was abandoned. */
    public String outcome() {
        return outcome;
    }

    public List<String> logLines() {
        return log;
    }

    public List<Entry> lastCombo() {
        return current;
    }

    public int combos() {
        return combos;
    }

    public int landedCombos() {
        return landed;
    }

    public int abandonedCombos() {
        return abandoned;
    }

    /** Why the combo cannot start, or null when it can. */
    public String missing(Observation o) {
        Loadout l = o.loadout();
        if (l.slot("mace") < 0) return "no mace in the hotbar";
        if (!l.wearingElytra()) return "not wearing the elytra";
        if (!o.gliding()) return "not gliding";
        if (l.slot(Loadout::isChestplate) < 0) return "no chestplate in the hotbar to swap to";
        if (l.slot("firework_rocket") < 0) return "no firework in the hotbar for recovery";
        if (o.effectiveHealth() <= p.comboAbortHealth) return "health too low for the combo";
        if (o.opponentCovered(p.minDropHeadroom)) return "target is under cover";
        return null;
    }

    /** Why the quick follow-up cannot start, or null when it can. */
    public String followMissing(Observation o) {
        Loadout l = o.loadout();
        if (l.slot("mace") < 0) return "no mace in the hotbar";
        if (!l.hasElytra()) return "no elytra";
        if (!l.chest.endsWith("_chestplate") && l.slot(Loadout::isChestplate) < 0) return "no chestplate to swap to";
        if (l.slot("firework_rocket") < 0) return "no firework in the hotbar";
        if (o.onGround()) return "on the ground";
        if (o.effectiveHealth() <= p.comboAbortHealth) return "health too low for the combo";
        if (o.opponentCovered(p.minDropHeadroom)) return "target is under cover";
        return null;
    }

    /**
     * The quick follow-up: the target is up in the air, so instead of climbing high it puts the elytra on,
     * opens it, fires one rocket at a point just above the target, swaps to the chestplate over them,
     * and hits with the axe and mace.
     */
    public boolean beginFollowUp(Observation o) {
        return beginFollowUp(o, Long.MIN_VALUE);
    }

    /**
     * @param earliestSmash first tick a smash can count. A player hit less than half a second ago only takes
     *                      the part of a new hit that is bigger than the last one, so it waits that out.
     */
    public boolean beginFollowUp(Observation o, long earliestSmash) {
        this.earliestSmash = earliestSmash;
        current.clear();
        combos++;
        String why = followMissing(o);
        if (why != null) {
            record(o, "CHECK", "follow_up_requirements", -1, "not started: " + why);
            abandoned++;
            outcome = why;
            return false;
        }
        record(o, "CHECK", "follow_up_requirements", -1, "ok, target in the air");
        following = true;
        axeSelected = false;
        axeDecisionLogged = false;
        abandonCounted = false;
        retries = 0;
        hpBeforeSmash = Double.NaN;
        wornSeen = false;
        openSeen = false;
        boostSeen = false;
        refiredAfterOpen = false;
        rocketAskedWhileOpen = false;
        divingFollow = false;
        go(o, Step.F_EQUIP);
        firstTick = true;
        return true;
    }

    private boolean firstTick;
    private long earliestSmash = Long.MIN_VALUE;

    /** Starts the combo. Returns false and logs the reason when the requirements are not met. */
    public boolean begin(Observation o) {
        current.clear();
        String why = missing(o);
        combos++;
        if (why != null) {
            record(o, "CHECK", "requirements", -1, "not started: " + why);
            flush();
            abandoned++;
            outcome = why;
            return false;
        }
        record(o, "CHECK", "requirements", -1, "ok");
        following = false;
        earliestSmash = Long.MIN_VALUE;
        firstTick = false;
        axeSelected = false;
        axeDecisionLogged = false;
        abandonCounted = false;
        retries = 0;
        hpBeforeSmash = Double.NaN;
        go(o, Step.DROP1);
        return true;
    }

    /**
     * Runs one tick of the combo and fills in the inputs. Returns false when the combo is over, after
     * which outcome() says how it ended and the elytra style takes over again.
     */
    public boolean step(Observation o, BotAction a) {
        if (!active()) return false;
        Loadout l = o.loadout();
        if (o.lineOfSight()) lastSeen = o.tick();

        // Safety checks first, every tick.
        if (step != Step.CHECK_HIT && step != Step.RECOVER) {
            if (o.onGround()) return finish(o, "abandoned: landed before the smash");
            if (o.distance() > (following ? p.followRange + 4 : p.comboAbortDistance)) return recover(o, a, "target out of range");
            if (o.opponentCovered(p.minDropHeadroom)) return recover(o, a, "target went under cover");
            if (o.tick() - lastSeen > 6) return recover(o, a, "lost line of sight");
            if (o.effectiveHealth() <= p.comboAbortHealth && step != Step.SMASH) return recover(o, a, "health too low");
            if (o.tick() - stepStart > 60) return recover(o, a, "step " + step + " took too long");
        }
        aimAtTarget(o, a);
        boolean first = firstTick;
        firstTick = false;

        switch (step) {
            case F_EQUIP -> {
                // The launch. Elytra on, glide open, and rocket are all asked for in the same tick, since the
                // server applies them in that order. Each one is still checked before the chase starts.
                boolean worn = l.wearingElytra(), open = o.gliding();
                double speed = Math.sqrt(o.vx() * o.vx() + o.vy() * o.vy() + o.vz() * o.vz());
                boolean boosted = rocketAskedWhileOpen && (speed - speedAtRocket >= 0.15 || speed >= 1.3);
                if (worn && !wornSeen) { wornSeen = true; record(o, "F_EQUIP", "swap_to_elytra", -1, "confirmed"); }
                if (open && !openSeen) { openSeen = true; record(o, "F_OPEN", "open_elytra", -1, "confirmed"); }
                if (boosted && !boostSeen) {
                    boostSeen = true;
                    record(o, "F_ROCKET", "firework", -1, String.format("confirmed, speed up %.2f", speed - speedAtRocket));
                }
                if (first) {
                    if (!worn) { a.equip = "elytra"; requestLogged(o, "swap_to_elytra", l.slot("elytra")); }
                    if (!open) { a.startGlide = true; requestLogged(o, "open_elytra", -1); }
                    // Already above them: the rocket goes straight down at them. Below them: over them first.
                    divingFollow = o.heightAbove() >= p.fastFollowHeight;
                    if (divingFollow && rocketLeavesCharge(o)) fireDown(o, a);
                    else fireAtTarget(o, a);
                    // The server opens the glide before it uses the rocket, so this rocket counts as fired while open.
                    rocketAskedWhileOpen = true;
                } else if (worn && open && boosted) {
                    go(o, Step.F_CHASE);
                    return step(o, a);
                } else {
                    if (divingFollow) aimAtBody(o, a);
                    else aimAbove(o, a);
                    // A rocket used before the glide opened does nothing and is not used up, so fire again once open.
                    if (open && !boosted && !refiredAfterOpen && o.tick() - stepStart >= 1) {
                        refiredAfterOpen = true;
                        if (divingFollow && rocketLeavesCharge(o)) fireDown(o, a);
                        else fireAtTarget(o, a);
                    }
                    long waited = o.tick() - stepStart;
                    if (waited > p.comboConfirmTicks * (retries + 1L)) {
                        if (retries < p.comboRetries) {
                            retries++;
                            if (!worn) a.equip = "elytra";
                            else if (!open) a.startGlide = true;
                            if (worn && !boosted) {
                            if (divingFollow && rocketLeavesCharge(o)) fireDown(o, a);
                            else fireAtTarget(o, a);
                        }
                            record(o, "F_EQUIP", "launch", -1, "not all seen after " + waited + " ticks, retry " + retries);
                        } else {
                            return recover(o, a, !worn ? "elytra swap was not confirmed" : !open ? "glide did not start" : "rocket did not fire");
                        }
                    }
                }
            }
            case F_CHASE -> {
                if (!o.gliding()) return recover(o, a, "glide ended during the chase");
                if (divingFollow || (p.fastElytra == 1 && o.heightAbove() >= p.fastFollowHeight)) {
                    // Diving at them from above: chestplate on a few blocks out, then the smash.
                    divingFollow = true;
                    aimAtBody(o, a);
                    holdCarrier(o, a);
                    if (dropNow(o)) {
                        go(o, Step.DROP2);
                        a.equip = "chestplate";
                        requestLogged(o, "swap_to_chestplate", l.slot(Loadout::isChestplate));
                    } else if (o.tick() - stepStart > 30) {
                        return recover(o, a, "could not reach the target");
                    }
                    break;
                }
                aimAbove(o, a);
                holdCarrier(o, a);
                double horizontal = o.distance();
                // High enough over them for a smash worth having. A taller drop hits harder, a shorter one comes sooner.
                boolean over = o.heightAbove() >= Math.max(1.0, p.followDropHeight) && horizontal <= 2.0;
                boolean close = o.distance3d() <= 3.2 && o.heightAbove() > 0.3 && p.followDropHeight <= 1.5;
                if (over || close) {
                    go(o, Step.DROP2);
                    a.equip = "chestplate";
                    requestLogged(o, "swap_to_chestplate", l.slot(Loadout::isChestplate));
                } else if (o.tick() - stepStart > 30) {
                    return recover(o, a, "could not get over the target");
                }
            }
            case DROP1, DROP2 -> {
                // Waiting for the chestplate swap to show up: chest is a chestplate and the glide has stopped.
                boolean done = l.chest.endsWith("_chestplate") && !o.gliding();
                if (!done && o.tick() == stepStart && step == Step.DROP1) {
                    a.equip = "chestplate";
                    requestLogged(o, "swap_to_chestplate", l.slot(Loadout::isChestplate));
                } else if (done) {
                    record(o, step.name(), "swap_to_chestplate", l.slot(Loadout::isChestplate), "confirmed");
                    go(o, step == Step.DROP1 ? Step.FALL1 : Step.FALL2);
                    // In a follow-up or a fast dive every tick counts, so the falling step starts right away.
                    if (following || p.fastElytra == 1) return step(o, a);
                } else if (!confirmOrRetry(o, a, () -> a.equip = "chestplate", "swap_to_chestplate")) {
                    return recover(o, a, "chestplate swap was not confirmed");
                }
            }
            case FALL1 -> {
                steer(o, a);
                holdCarrier(o, a);
                if (smashReady(o)) {
                    go(o, Step.SMASH);
                    return step(o, a);
                }
                double miss = miss(o);
                boolean fast = p.fastElytra == 1;
                boolean built = o.tick() - stepStart >= (fast ? p.fastDropTicks : p.comboFirstDropTicks);
                // Fast mode always goes back to the elytra, for the rocket dive straight at them.
                if (built && (fast || (miss > p.comboRegripMiss && o.heightAbove() > p.comboRegripHeight))) {
                    record(o, "FALL1", "plan_regrip", -1, String.format("drop would miss by %.1f blocks", miss));
                    go(o, Step.REEQUIP);
                    a.equip = "elytra";
                    requestLogged(o, "swap_to_elytra", l.slot("elytra"));
                }
            }
            case REEQUIP -> {
                if (l.wearingElytra()) {
                    record(o, "REEQUIP", "swap_to_elytra", -1, "confirmed");
                    go(o, Step.REGLIDE);
                    // Equipping does not start a glide, so opening it is a separate request.
                    a.startGlide = true;
                    requestLogged(o, "open_elytra", -1);
                } else if (!confirmOrRetry(o, a, () -> a.equip = "elytra", "swap_to_elytra")) {
                    return recover(o, a, "elytra swap was not confirmed");
                }
            }
            case REGLIDE -> {
                if (o.gliding()) {
                    record(o, "REGLIDE", "open_elytra", -1, "confirmed");
                    go(o, Step.DIVE);
                    // Fast mode: the rocket goes the same tick the glide is seen open.
                    if (p.fastElytra == 1) return step(o, a);
                } else if (!confirmOrRetry(o, a, () -> a.startGlide = true, "open_elytra")) {
                    return recover(o, a, "glide did not start");
                }
            }
            case DIVE -> {
                if (!o.gliding()) return recover(o, a, "glide ended during the dive");
                if (p.fastElytra == 1 || following) {
                    // A rocket aimed straight at them: the fastest way down, and fall distance keeps adding up
                    // while it dives faster than half a block a tick.
                    aimAtBody(o, a);
                    holdCarrier(o, a);
                    if (o.tick() == stepStart && rocketLeavesCharge(o)) fireDown(o, a);
                    if (dropNow(o)) {
                        go(o, Step.DROP2);
                        a.equip = "chestplate";
                        requestLogged(o, "swap_to_chestplate", l.slot(Loadout::isChestplate));
                    }
                    break;
                }
                // Dive steeply toward where the target will be. Faster than half a block per tick down,
                // the game keeps adding to the fall distance while gliding.
                double[] land = MacePolicy.landing(o);
                double leadX = o.ox() + o.ovx() * land[2], leadZ = o.oz() + o.ovz() * land[2];
                a.yaw = (float) Math.toDegrees(Math.atan2(-(leadX - o.x()), leadZ - o.z()));
                a.pitch = (float) p.comboDivePitch;
                holdCarrier(o, a);
                if (miss(o) <= 1.5 || o.heightAbove() < p.comboFinalDropHeight) {
                    go(o, Step.DROP2);
                    a.equip = "chestplate";
                    requestLogged(o, "swap_to_chestplate", l.slot(Loadout::isChestplate));
                }
            }
            case FALL2 -> {
                steer(o, a);
                int axe = l.slot(Loadout::isAxe);
                // The axe needs a full second to charge. Select it only if the fall leaves that long, or the
                // target is blocking and the axe is needed for the shield. Otherwise the sword stays the carrier.
                double ticksLeft = MacePolicy.landing(o)[2];
                boolean axeUseful = axe >= 0 && (ticksLeft >= p.axeChargeTicks || (o.oBlocking() && p.stunSlam == 1));
                if (axeUseful && !axeSelected) {
                    a.hotbar = axe;
                    axeSelected = true;
                    record(o, "FALL2", "select_axe", axe, String.format("requested, about %.0f ticks to impact", ticksLeft));
                } else if (!axeSelected) {
                    if (axe >= 0 && !axeDecisionLogged) {
                        axeDecisionLogged = true;
                        record(o, "FALL2", "select_axe", axe, String.format("skipped, only %.0f ticks to impact, not enough to charge", ticksLeft));
                    }
                    holdCarrier(o, a);
                }
                if (smashReady(o)) {
                    go(o, Step.SMASH);
                    return step(o, a);
                }
                // Coming down fast with no smash in reach: open the elytra again instead of taking the fall.
                double ticksToLand = MacePolicy.landing(o)[2];
                if (p.fastElytra == 1 && p.fastBailout == 1 && miss(o) > 2.5 && !MacePolicy.inReachSoon(o) && o.fallDistance() > 4 && ticksToLand <= 3 && l.has("elytra")) {
                    return recover(o, a, String.format("drop would miss, pulling up with %.1f blocks of fall", o.fallDistance()));
                }
                if (MacePolicy.inReachSoon(o) && o.fallDistance() < minFall() && o.vy() < 0 && o.heightAbove() < 1.0) {
                    return recover(o, a, String.format("only %.1f blocks of fall, not enough for a smash", o.fallDistance()));
                }
            }
            case SMASH -> {
                int mace = l.slot("mace");
                int axe = l.slot(Loadout::isAxe);
                boolean holdingAxe = o.mainHand().endsWith("_axe");
                hpBeforeSmash = o.oEffectiveHealth();
                record(o, "SMASH", "select_mace", mace, "inside the hit");
                // In a follow-up the target may raise a shield at the last moment, so a shield in their offhand counts.
                boolean shieldThreat = o.oBlocking() || (following && o.oHasShield());
                if (shieldThreat && axe >= 0 && p.stunSlam == 1) {
                    a.swapAttack = new int[] {axe, mace};
                    a.technique = "stun_slam";
                } else if (holdingAxe || o.mainHand().endsWith("_sword")) {
                    a.swapAttack = new int[] {mace};
                    a.technique = "swap_smash";
                } else {
                    a.hotbar = mace;
                    a.attack = true;
                    a.technique = "smash";
                }
                a.swapBack = l.slot(Loadout::isSword) >= 0 ? l.slot(Loadout::isSword) : mace;
                record(o, "SMASH", a.technique, mace, String.format("attempt at %.1f blocks of fall, bar %.2f", o.fallDistance(), o.charge()));
                go(o, Step.CHECK_HIT);
            }
            case CHECK_HIT -> {
                // A smash cancels the fall, and the target loses health. Either shows the hit landed.
                boolean fallCancelled = o.fallDistance() < 0.5 && !o.onGround();
                boolean hurt = !Double.isNaN(hpBeforeSmash) && o.oEffectiveHealth() < hpBeforeSmash - 0.01;
                if (fallCancelled || hurt) {
                    landed++;
                    return finish(o, "hit");
                }
                if (o.tick() - stepStart >= 2 || o.onGround()) return finish(o, o.oBlocking() ? "blocked" : "miss");
            }
            case RECOVER -> {
                // Get the elytra back on and open it, then the elytra style rockets out.
                if (o.onGround()) return finish(o, outcome);
                if (!l.wearingElytra()) {
                    a.equip = "elytra";
                } else if (!o.gliding()) {
                    a.startGlide = true;
                } else {
                    record(o, "RECOVER", "gliding_again", -1, "confirmed");
                    return finish(o, outcome);
                }
                a.pitch = -30;
                if (o.tick() - stepStart > 10) return finish(o, outcome);
            }
            case DONE -> {
                return false;
            }
        }
        a.state = "ELYTRA_COMBO";
        if (a.technique.isEmpty()) a.technique = "combo_" + step.name().toLowerCase();
        return true;
    }

    // ---------------------------------------------------------------- helpers

    /** The smash is allowed: falling far enough, not gliding, target in reach now, and this is the last good tick. */
    private boolean smashReady(Observation o) {
        if (o.gliding() || o.vy() >= 0 || o.fallDistance() < minFall() || !MacePolicy.inReachSoon(o)) return false;
        // The follow-up is about speed: swing on the first good tick instead of waiting for a deeper fall,
        // but not while the target is still protected by the last hit.
        double nvy = (o.vy() - 0.08) * 0.98;
        double ny = o.y() + nvy;
        boolean stillNext = MacePolicy.reachFrom(o, o.x() + o.vx() + o.ovx(), ny, o.z() + o.vz() + o.ovz()) <= 2.8 && ny > o.oy() + 0.2;
        // A bar still filling is worth one more tick when the target stays in reach: full is a crit.
        boolean filling = p.critSmash == 1 && o.charge() < 0.92 && stillNext;
        if (following) return o.tick() >= earliestSmash && !filling;
        return !stillNext || (o.oBlocking() && p.stunSlam == 1);
    }

    private double minFall() {
        return following ? p.followMinFall : p.comboMinFall;
    }

    /** Fires a rocket aimed just above where the target is heading. */
    private void fireAtTarget(Observation o, BotAction a) {
        Loadout l = o.loadout();
        int fw = l.slot("firework_rocket");
        if (fw < 0) return;
        aimAbove(o, a);
        a.hotbar = fw;
        a.useOnce = true;
        speedAtRocket = Math.sqrt(o.vx() * o.vx() + o.vy() * o.vy() + o.vz() * o.vz());
        record(o, step.name(), "firework", fw, "requested");
    }

    private boolean divingFollow;

    /**
     * Time to swap to the chestplate for the smash. The swap takes a tick to show and the smash another, so it
     * goes on when the bot is a few ticks from the target at its current speed, not at a fixed distance.
     */
    private boolean dropNow(Observation o) {
        double speed = Math.max(0.4, Math.sqrt(o.vx() * o.vx() + o.vy() * o.vy() + o.vz() * o.vz()));
        double ticks = Math.max(0, o.reachToTarget() - 2.5) / speed;
        return ticks <= p.fastDropTicksAhead || o.heightAbove() < p.fastDropHeight;
    }

    /** Looks straight at the middle of where the target will be when the bot gets there. */
    private void aimAtBody(Observation o, BotAction a) {
        double speed = Math.max(0.8, Math.sqrt(o.vx() * o.vx() + o.vy() * o.vy() + o.vz() * o.vz()));
        double t = Math.min(10, o.distance3d() / speed);
        double tx = o.ox() + o.ovx() * t, ty = o.oy() + o.loadout().oHeight / 2 + (o.oOnGround() ? 0 : o.ovy() * t), tz = o.oz() + o.ovz() * t;
        double dx = tx - o.x(), dy = ty - o.eyeY(), dz = tz - o.z();
        a.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        a.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    /**
     * Whether a rocket down still leaves time for the attack bar to fill before the smash. Taking the rocket
     * out and putting the weapon back both restart the bar. A recorded session had every smash land at
     * 36 to 52 percent, about 7 ticks after the rocket, which cuts the weapon's own damage to a third and
     * rules out a crit.
     */
    private boolean rocketLeavesCharge(Observation o) {
        if (p.fastChargeTicks <= 0) return true;
        double ticks = Math.max(0, o.reachToTarget() - 2.5) / 1.6;
        return ticks >= p.fastChargeTicks;
    }

    /** A rocket aimed straight at the target, for the dive. */
    private void fireDown(Observation o, BotAction a) {
        int fw = o.loadout().slot("firework_rocket");
        aimAtBody(o, a);
        if (fw < 0) return;
        a.hotbar = fw;
        a.useOnce = true;
        speedAtRocket = Math.sqrt(o.vx() * o.vx() + o.vy() * o.vy() + o.vz() * o.vz());
        record(o, step.name(), "firework_down", fw, "requested");
    }

    /** Looks at a point a few blocks above where the target will be in half a second. */
    private void aimAbove(Observation o, BotAction a) {
        double above = p.fastElytra == 1 ? p.fastFollowHeight + 1 : p.followAbove;
        double tx = o.ox() + o.ovx() * 10, ty = o.oy() + Math.max(-0.5, o.ovy()) * 10 + above, tz = o.oz() + o.ovz() * 10;
        double dx = tx - o.x(), dy = ty - (o.eyeY()), dz = tz - o.z();
        a.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        a.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    /** How far a fall from here would land from where the target will be. */
    private static double miss(Observation o) {
        double[] land = MacePolicy.landing(o);
        return Math.hypot(land[0] - (o.ox() + o.ovx() * land[2]), land[1] - (o.oz() + o.ovz() * land[2]));
    }

    private void steer(Observation o, BotAction a) {
        double[] land = MacePolicy.landing(o);
        double dx = o.ox() + o.ovx() * land[2] - land[0], dz = o.oz() + o.ovz() * land[2] - land[1];
        if (Math.hypot(dx, dz) < 0.4) return;
        double moveYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Angles.wrap(moveYaw - a.yaw);
        a.forward = Math.abs(rel) < 67.5 ? 1 : Math.abs(rel) > 112.5 ? -1 : 0;
        a.strafe = rel > 22.5 && rel < 157.5 ? -1 : rel < -22.5 && rel > -157.5 ? 1 : 0;
        // Sprinting rules out a crit, and a crit smash is 1.5 times the whole smash, fall bonus included.
        a.sprint = a.forward > 0 && (p.critSmash != 1 || o.distance3d() >= 5);
    }

    /** Holds the sword during falls so the swap hit keeps its charge, or the mace when there is no sword. */
    private void holdCarrier(Observation o, BotAction a) {
        Loadout l = o.loadout();
        int carrier = p.attributeSwap == 1 && l.slot(Loadout::isSword) >= 0 ? l.slot(Loadout::isSword) : l.slot("mace");
        if (carrier >= 0 && !o.mainHand().equals(l.hotbar(carrier))) a.hotbar = carrier;
    }

    private void aimAtTarget(Observation o, BotAction a) {
        a.yaw = o.yawToOpponent();
        a.pitch = o.pitchToOpponent();
    }

    /**
     * Still waiting for a requested change. Within the confirm window it waits. After that it resends once
     * per retry. Returns false when the retries are used up.
     */
    private boolean confirmOrRetry(Observation o, BotAction a, Runnable resend, String action) {
        long waited = o.tick() - stepStart;
        if (waited <= p.comboConfirmTicks * (retries + 1L)) return true;
        if (retries < p.comboRetries) {
            retries++;
            resend.run();
            record(o, step.name(), action, -1, "not seen after " + waited + " ticks, retry " + retries);
            return true;
        }
        record(o, step.name(), action, -1, "failed after " + waited + " ticks");
        return false;
    }

    private void requestLogged(Observation o, String action, int slot) {
        record(o, step.name(), action, slot, "requested");
    }

    private void go(Observation o, Step next) {
        step = next;
        stepStart = o.tick();
        retries = 0;
    }

    /** Starts recovery: the combo is over, but it first gets the elytra back open if it can. */
    private boolean recover(Observation o, BotAction a, String why) {
        record(o, step.name(), "abandon", -1, why);
        countAbandon();
        outcome = "abandoned: " + why;
        go(o, Step.RECOVER);
        return step(o, a);
    }

    private boolean finish(Observation o, String result) {
        outcome = result;
        if (result.startsWith("abandoned")) countAbandon();
        record(o, step.name(), "end", -1, result);
        step = Step.DONE;
        flush();
        return false;
    }

    private void countAbandon() {
        if (!abandonCounted) abandoned++;
        abandonCounted = true;
    }

    private void record(Observation o, String stepName, String action, int slot, String result) {
        Entry e = new Entry(o.tick(), stepName, action, slot, o.loadout().chest, o.vx(), o.vy(), o.vz(), o.fallDistance(),
                o.distance3d(), o.lineOfSight(), o.charge(), result);
        current.add(e);
        // Written at once, so a fight that ends mid-combo still has every step up to that point.
        log.add(e.toJson(combos));
    }

    private void flush() {
    }
}
