package dev.reality.fightbot.core;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Sword kit policy, in the layers the spec asks for:
 * strategic state, then a technique for that state, then raw inputs, which the validator checks.
 */
public final class SwordPolicy implements Policy {
    public enum State { ASSESS, ENGAGE, FINISH, RECOVER, DISENGAGE }

    private final BotProfile p;
    private final Random rng;
    private final ArrayDeque<Observation> history = new ArrayDeque<>();

    private State state = State.ASSESS;
    private int strafeDir = 1;
    private long nextStrafeSwitch;
    private long wTapUntil = -1, sTapUntil = -1;
    private boolean critPending;
    private long eatStart = -1;
    private double lastEffective = Double.NaN;
    private float yaw, pitch;
    private boolean aimSet;
    private int blockRequestTicks;
    private int baitTicks;
    private boolean baiting;
    private int oppBlockTicks;
    private boolean axeMode;
    private long lastShieldUp = -1000;
    private long axeSince = -1;
    private long shieldDisabledUntil = -1;
    /** Until when the opponent's shield is on cooldown from the bot's axe. */
    private long oShieldDownUntil = -1;
    private long lastRestock = -1000;
    private long lastLunge = -1000;

    public SwordPolicy(BotProfile profile) {
        this.p = profile;
        this.rng = new Random(profile.seed);
    }

    public State state() {
        return state;
    }

    @Override
    public String stateName() {
        return state.name();
    }

    public BotAction step(Observation o) {
        history.addLast(o);
        while (history.size() > p.reactionTicks + 1) history.pollFirst();
        // Decisions react to the situation a few ticks ago, like a human would. Aim follows the latest view.
        Observation d = history.peekFirst();

        if (!aimSet) { yaw = o.yaw(); pitch = o.pitch(); aimSet = true; }
        if (!o.hasOpponent() || o.stale()) {
            state = State.ASSESS;
            return BotAction.idle(o.yaw(), o.pitch());
        }

        boolean hurt = !Double.isNaN(lastEffective) && o.effectiveHealth() < lastEffective - 0.01;
        lastEffective = o.effectiveHealth();
        state = chooseState(d, o);

        BotAction a = new BotAction();
        a.state = state.name();
        switch (state) {
            case RECOVER -> recover(o, a);
            case ASSESS -> { }
            case DISENGAGE -> disengage(o, a);
            default -> engage(d, o, a, hurt);
        }
        return a;
    }

    // ---------------------------------------------------------------- strategic layer

    private State chooseState(Observation d, Observation o) {
        double hp = o.effectiveHealth();
        boolean canHeal = (o.healSlot() >= 0 && o.healCount() > 0) || (o.egapSlot() >= 0 && o.egapCount() > 0);
        // Still covered by absorption from the last apple: no need to eat again unless it is critical.
        if (o.absorption() > 2 && hp > p.egapAt) canHeal = false;
        if (eatStart >= 0) {
            // Keep eating unless it is about to get punished.
            if (o.distance() < 3.2 && hp > p.disengageAt) eatStart = -1;
            else if (o.tick() - eatStart > 40) eatStart = -1;
            else return State.RECOVER;
        }
        // Running away to heal fails when the opponent sprints just as fast, so it only eats when a
        // knockback hit or the opponent's own retreat has already made room. Otherwise it keeps fighting.
        // With a healing potion on hand at potion health, the potion goes instead: it is instant, and starting an
        // apple there only had the potion cancel it. A recorded session finished 3 apples out of 32 started.
        boolean potFirst = p.usePotions == 1 && hp <= p.potHealAt && o.loadout().potions("splash_potion", "healing") > 0;
        if (hp <= p.healAt && canHeal && d.distance() >= p.safeEatDistance && !potFirst) {
            eatStart = o.tick();
            return State.RECOVER;
        }
        // Only back off when the opponent cannot keep up, for example while they are eating.
        if (hp <= p.disengageAt && !canHeal && o.oUsingItem()) return State.DISENGAGE;
        if (!Double.isNaN(o.oHealth()) && o.oEffectiveHealth() <= 4) return State.FINISH;
        return State.ENGAGE;
    }

    // ---------------------------------------------------------------- tactical layer

    private void engage(Observation d, Observation o, BotAction a, boolean hurt) {
        aimAt(o, a, false);
        double dist = d.distance();

        // Shield breaking: commit to the axe only when the opponent keeps the shield up and is close,
        // and stay on it until it lands a hit. Swapping empties the attack bar, so flip-flopping wastes hits.
        // Recorded fights showed players flicking the shield up for 3 to 5 ticks, eating the sword hit and
        // swinging back. So the axe comes out as soon as the shield starts to rise, ready to disable it.
        oppBlockTicks = o.oBlocking() ? oppBlockTicks + 1 : 0;
        boolean raising = o.oUsingItem() && o.oHasShield() && (isWeapon(o.oMainHand()) || o.oMainHand().equals("shield"));
        boolean shieldUp = o.oBlocking() || raising;
        if (!axeMode && p.breakShields == 1 && o.axeSlot() >= 0 && shieldUp && o.tick() >= oShieldDownUntil
                && dist <= p.reach + 1.5) {
            axeMode = true;
            axeSince = o.tick();
        }
        // The shield went back down without being hit: back to the sword soon, not after two seconds. Two bots
        // flicking shields at each other kept both on the slower axe about seven tenths of every fight.
        if (shieldUp) lastShieldUp = o.tick();
        if (axeMode && (o.tick() - axeSince > 40 || o.tick() - lastShieldUp > p.axeDropTicks)) axeMode = false;
        boolean holdingAxe = o.mainHand().endsWith("_axe");
        boolean breakingShield = axeMode;
        if (axeMode) {
            if (!holdingAxe) a.hotbar = o.axeSlot();
        } else if (o.weaponSlot() >= 0) {
            boolean hasSword = o.weaponSlot() != o.axeSlot();
            // Holding a charged axe outside axe mode: use it once instead of throwing the charge away.
            boolean keepAxe = holdingAxe && o.charge() >= 0.5;
            if (!keepAxe && (hasSword ? !o.mainHand().endsWith("_sword") : !isWeapon(o.mainHand()))) a.hotbar = o.weaponSlot();
        }
        restock(o, a);

        if (o.tick() >= nextStrafeSwitch) {
            strafeDir = -strafeDir;
            nextStrafeSwitch = o.tick() + p.strafeSwitchMin + rng.nextInt(Math.max(1, p.strafeSwitchMax - p.strafeSwitchMin + 1));
        }
        a.strafe = dist < 5 ? strafeDir : 0;

        if (hurt && o.onGround() && rng.nextDouble() < p.jumpResetChance) {
            a.jump = true;
            a.technique = "jump_reset";
        }

        if (o.tick() < sTapUntil) {
            a.forward = -1;
            a.technique = "s_tap";
        } else if (o.tick() < wTapUntil) {
            a.forward = 0;
            a.technique = "w_tap";
        } else if (dist < p.tooClose && rng.nextDouble() < p.sTapChance) {
            sTapUntil = o.tick() + 3;
            a.forward = -1;
            a.technique = "s_tap";
        } else if (dist > p.preferredDistance + 0.3 || state == State.FINISH) {
            a.forward = 1;
            a.sprint = true;
        } else {
            a.forward = dist > p.preferredDistance ? 1 : 0;
            a.sprint = true;
        }

        // Sprint jump: each jump while sprinting adds a burst of speed, so it covers ground faster.
        if (p.sprintJump == 1 && o.onGround() && !a.jump && a.forward > 0 && a.sprint && dist > p.sprintJumpBeyond) {
            a.jump = true;
            if (a.technique.isEmpty()) a.technique = "sprint_jump";
        }
        // Crit jump: close the last gap in the air so the hit lands while falling.
        // The game gives no crit in water, on a ladder, or while blind, so it does not jump for one then.
        boolean critPossible = !o.loadout().inWater && o.loadout().effectTicks("blindness") == 0;
        if (critPossible && o.onGround() && !critPending && dist > p.reach && dist < p.reach + 1.3 && o.charge() >= 0.75
                && rng.nextDouble() < p.critJumpChance) {
            a.jump = true;
            critPending = true;
            a.technique = "crit_jump";
        }
        // Axe crit: the axe's bar takes a full second to fill, which is time for a jump in place. Jumping at
        // half charge means it is falling, with a full bar, right as it comes back down: a 1.5 times hit.
        boolean axeMain = o.mainHand().endsWith("_axe") && o.axeSlot() >= 0 && o.weaponSlot() == o.axeSlot();
        if (axeMain && critPossible && o.onGround() && !critPending && !a.jump && d.distance() - 0.3 <= p.reach
                && o.charge() >= p.axeCritJumpAt && o.charge() <= p.axeCritJumpAt + 0.2
                && (!o.oBlocking() || o.tick() < shieldDisabledUntil) && rng.nextDouble() < p.axeCritChance) {
            a.jump = true;
            critPending = true;
            a.sprint = false;
            a.technique = "axe_crit_jump";
        }
        if (o.onGround() && !a.jump) critPending = false;
        boolean falling = !o.onGround() && o.vy() < 0;
        // Sprinting cancels a crit, so it lets go of sprint for the whole jump.
        if (critPending && (falling || axeMain)) a.sprint = false;

        boolean ready = o.charge() >= p.chargeThreshold || (state == State.FINISH && o.charge() >= 0.85);
        boolean waitForFall = critPending && !o.onGround() && o.vy() >= 0;
        boolean inReach = o.distance() - 0.3 <= p.reach && Angles.diff(a.yaw, o.yawToOpponent()) <= p.maxAimError;
        // Shield bait: the opponent is ready to swing at us, so hold the shield and let them swing into it.
        boolean oppReady = !Double.isNaN(o.oCharge()) && o.oCharge() >= 0.9 && !o.oppHasAxe() && dist <= p.blockRange;
        boolean shieldReady = p.useShield == 1 && o.hasShield() && o.tick() >= shieldDisabledUntil;
        if (shieldReady && oppReady && baitTicks < p.baitPatience) {
            baitTicks++;
            baiting = true;
        } else {
            if (!oppReady) baitTicks = 0;
            baiting = false;
        }
        // Lunge in: a Lunge spear jab dashes forward. The jab empties the attack bar, so it is only worth it
        // when the opponent is running, eating, or drawing a bow, not when they are ready to trade hits.
        Loadout l = o.loadout();
        double away = (o.ovx() * (o.ox() - o.x()) + o.ovz() * (o.oz() - o.z())) / Math.max(0.01, dist);
        boolean worthIt = o.oUsingItem() || away > 0.12;
        if (p.useLunge == 1 && l.canLunge() && o.onGround() && !axeMode && worthIt && dist >= p.lungeMin && dist <= p.lungeMax
                && o.charge() >= 1 && Angles.diff(a.yaw, o.yawToOpponent()) < 15 && o.tick() - lastLunge > 30) {
            a.jabSlot = l.lungeSlot;
            a.swapBack = o.weaponSlot() >= 0 ? o.weaponSlot() : -1;
            a.pitch = 0;
            a.forward = 1;
            a.sprint = true;
            a.technique = "lunge_in";
            lastLunge = o.tick();
            return;
        }
        // Axe on a raised shield: hit right away, charge does not matter for disabling it.
        if (axeMode && holdingAxe && a.hotbar < 0 && inReach && o.oBlocking()) {
            a.attack = true;
            a.technique = "shield_break";
            axeMode = false;
            // An axe hit on a raised shield puts it on a 5 second cooldown.
            oShieldDownUntil = o.tick() + 100;
            return;
        }
        boolean axeWaiting = axeMode && (!holdingAxe || a.hotbar >= 0);
        // A sword hit into a raised shield does nothing and spends the bar, so it keeps the charge for the
        // moment the shield comes down, when they swing back.
        boolean intoShield = o.oBlocking() && !holdingAxe;
        if (intoShield && ready && inReach && a.technique.isEmpty()) a.technique = "hold_for_shield";
        if (ready && !waitForFall && inReach && !axeWaiting && !baiting && !intoShield) {
            if (axeMode) axeMode = false;
            a.attack = true;
            if (a.technique.isEmpty()) a.technique = state == State.FINISH ? "finish" : critPending ? "crit" : "hit";
            critPending = false;
            if (rng.nextDouble() < p.wTapChance) wTapUntil = o.tick() + 3;
        }
        shield(o, a, dist);
    }

    /**
     * Raise the shield when a hit is coming and the bot is not swinging this tick: the opponent is close
     * with their bar filling, or drawing a bow from range. Never hold it against an axe, which disables it.
     * The shield takes 5 ticks to come up, so it goes up well before the opponent's bar is full.
     */
    private void shield(Observation o, BotAction a, double dist) {
        boolean disabled = o.tick() < shieldDisabledUntil;
        boolean meleeThreat = dist <= p.blockRange && !Double.isNaN(o.oCharge()) && o.oCharge() >= p.blockAtOppCharge
                && !o.oppHasAxe();
        boolean bowThreat = o.oppDrawingBow() && dist > p.blockBowsBeyond;
        // Lowering the shield costs a tick before a swing is possible, so it comes down one tick early.
        // How much the bar fills per tick comes from the held weapon's attack speed, read from the game.
        double perTick = o.loadout() == Loadout.EMPTY ? (o.mainHand().endsWith("_axe") ? 0.05 : 0.08) : o.loadout().attackSpeed / 20.0;
        // A shield only stops a hit once it has been up for five ticks. Raised for less before its own swing, it
        // blocks nothing and just costs the sprint: the bot duels showed raises of about two ticks, every one wasted.
        double ticksToSwing = Math.max(0, (p.chargeThreshold - o.charge()) / Math.max(1e-6, perTick));
        boolean swingNextTick = ticksToSwing < p.shieldUsefulTicks && dist - 0.3 <= p.reach + 0.3;
        if (blockRequestTicks > 0 && o.usingItem()) swingNextTick = ticksToSwing < 1 && dist - 0.3 <= p.reach + 0.3;
        boolean want = p.useShield == 1 && o.hasShield() && !disabled && !a.attack && !a.use && a.hotbar < 0
                && (baiting || (!swingNextTick && (meleeThreat || bowThreat)));
        if (want) {
            a.block = true;
            a.sprint = false;
            if (a.technique.isEmpty() || a.technique.equals("w_tap") || a.technique.equals("sprint_jump")) a.technique = baiting ? "shield_bait" : bowThreat ? "block_arrow" : "block";
            // The shield takes a few ticks to come up. If it never does, an axe has disabled it.
            if (++blockRequestTicks >= 8 && !o.usingItem()) {
                shieldDisabledUntil = o.tick() + 100;
                blockRequestTicks = 0;
                a.block = false;
                a.technique = "shield_disabled";
            }
        } else {
            blockRequestTicks = 0;
        }
    }

    /** Moves apples into the hotbar when none are there, only when nobody is close. */
    private void restock(Observation o, BotAction a) {
        if (p.restock != 1 || o.distance() < 6 || o.tick() - lastRestock < 40) return;
        boolean noEgapInHotbar = o.egapSlot() < 0 && o.inventoryEgaps() > 0;
        boolean noGapInHotbar = o.healSlot() < 0 && o.inventoryGapples() > 0;
        if (noEgapInHotbar) a.restock = "enchanted_golden_apple";
        else if (noGapInHotbar) a.restock = "golden_apple";
        if (a.restock != null) {
            lastRestock = o.tick();
            if (a.technique.isEmpty()) a.technique = "restock";
        }
    }



    /** True while it is partway through eating an apple. */
    public boolean eating() {
        return eatStart >= 0;
    }

    private void recover(Observation o, BotAction a) {
        aimAt(o, a, false);
        boolean egap = o.egapSlot() >= 0 && o.egapCount() > 0
                && (o.effectiveHealth() <= p.egapAt || o.healSlot() < 0 || o.healCount() <= 0);
        a.hotbar = egap ? o.egapSlot() : o.healSlot();
        a.use = true;
        a.forward = -1;
        a.strafe = strafeDir;
        a.technique = egap ? "eat_egap" : "eat";
    }

    private void disengage(Observation o, BotAction a) {
        aimAt(o, a, true);
        a.forward = 1;
        a.sprint = true;
        a.strafe = strafeDir;
        a.technique = "retreat";
        if (p.sprintJump == 1 && o.onGround()) a.jump = true;
    }

    // ---------------------------------------------------------------- action helpers

    private void aimAt(Observation o, BotAction a, boolean away) {
        float targetYaw = o.yawToOpponent() + (away ? 180 : 0) + (float) (rng.nextGaussian() * p.aimJitter);
        float targetPitch = away ? 0 : o.pitchToOpponent() + (float) (rng.nextGaussian() * p.aimJitter * 0.5);
        yaw = Angles.approach(o.yaw(), targetYaw, p.swordTurnPerTick);
        pitch = Angles.approach(o.pitch(), targetPitch, p.swordTurnPerTick);
        a.yaw = yaw;
        a.pitch = Math.max(-90, Math.min(90, pitch));
    }

    static boolean isWeapon(String item) {
        return item.endsWith("_sword") || item.endsWith("_axe");
    }
}
