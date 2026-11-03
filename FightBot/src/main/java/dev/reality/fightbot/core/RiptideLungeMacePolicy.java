package dev.reality.fightbot.core;

import dev.reality.fightbot.core.world.WorldView;

/**
 * Drives the Riptide -> Lunge spear -> mace sequence for kits that carry a Riptide trident,
 * a Lunge spear, and a Wind Burst mace. It uses the same one-tick action model as the other
 * policies and delegates the fall and smash to {@link MacePolicy}.
 *
 * <p>The sequence is intentionally state-based rather than issuing a burst of guessed packets:
 * each phase checks the next observed game state before advancing. A Riptide launch is only
 * attempted while the player is wet, and the water bucket is only placed on a clear cell above
 * solid ground and outside the Nether.</p>
 */
public final class RiptideLungeMacePolicy implements Policy {
    public enum Phase {
        PREPARE_WATER,
        WAIT_FOR_WATER,
        CHARGE_RIPTIDE_UP,
        PICK_UP_WATER,
        AIM_DOWN,
        LUNGE,
        MACE,
        FAILED
    }

    private final BotProfile profile;
    private MacePolicy macePolicy;
    private WorldView world;
    private Phase phase = Phase.PREPARE_WATER;
    private long phaseStarted = -1;
    private long launchTick = -1;
    private long pickupRequestedAt = -1;
    private long lastChargeCountedTick = Long.MIN_VALUE;
    private int chargeTicks;
    private int aimTicks;
    private int waterX, waterY, waterZ;
    private boolean placedWater;
    private double launchY;
    private boolean launchObserved;

    public RiptideLungeMacePolicy(BotProfile profile) {
        this.profile = profile;
        this.macePolicy = new MacePolicy(profile, false);
    }

    /** Supplies the live or simulated block view used by the water-bucket setup. */
    public void setWorld(WorldView world) {
        this.world = world;
    }

    public Phase phase() {
        return phase;
    }

    /** True while water setup, Riptide, aiming, or the Lunge transition is in progress. */
    public boolean preComboActive() {
        return phase != Phase.MACE && phase != Phase.FAILED;
    }

    /** True while this policy must own movement, including the airborne mace finish. */
    public boolean controlsMovement(boolean onGround) {
        return preComboActive() || (phase == Phase.MACE && !onGround);
    }

    @Override
    public String stateName() {
        return switch (phase) {
            case PREPARE_WATER -> "OREO_PREPARE_WATER";
            case WAIT_FOR_WATER -> "OREO_WAIT_WATER";
            case CHARGE_RIPTIDE_UP -> "OREO_RIPTIDE_CHARGE";
            case PICK_UP_WATER -> "OREO_PICKUP_WATER";
            case AIM_DOWN -> "OREO_AIM_DOWN";
            case LUNGE -> "OREO_LUNGE";
            case MACE -> "OREO_MACE_" + macePolicy.stateName();
            case FAILED -> "OREO_FALLBACK_" + macePolicy.stateName();
        };
    }

    @Override
    public BotAction step(Observation o) {
        if (!o.hasOpponent() || o.stale()) return BotAction.idle(o.yaw(), o.pitch());

        // A release action can be accepted by the controller but fail to launch on a dry or modified
        // server. Confirm actual upward movement before we commit to the rest of the combo.
        if (launchTick >= 0 && !launchObserved
                && (phase == Phase.PICK_UP_WATER || phase == Phase.AIM_DOWN || phase == Phase.LUNGE)) {
            if (o.y() > launchY + 0.25 || o.vy() > 0.25) {
                launchObserved = true;
            } else if (o.tick() - launchTick > 2) {
                phase = Phase.FAILED;
                return delegateMace(o);
            }
        }

        if (phase == Phase.MACE || phase == Phase.FAILED) {
            // Once a drop has finished, start another Riptide cycle if the water bucket was recovered.
            if (phase == Phase.MACE && o.onGround() && launchTick >= 0 && o.tick() - launchTick > 5) {
                resetCycle();
                return prepareWater(o);
            }
            BotAction a = macePolicy.step(o);
            a.state = stateName();
            return a;
        }

        return switch (phase) {
            case PREPARE_WATER -> prepareWater(o);
            case WAIT_FOR_WATER -> waitForWater(o);
            case CHARGE_RIPTIDE_UP -> chargeRiptideUp(o);
            case PICK_UP_WATER -> pickUpWater(o);
            case AIM_DOWN -> aimDown(o);
            case LUNGE -> lunge(o);
            case MACE, FAILED -> throw new IllegalStateException("Handled terminal phase before dispatch");
        };
    }

    private BotAction prepareWater(Observation o) {
        Loadout l = o.loadout();
        int riptide = l.slot("trident:riptide");
        int mace = l.windBurstSlot >= 0 ? l.windBurstSlot : l.slot("mace");
        if (riptide < 0 || !l.canLunge() || profile.useLunge != 1 || mace < 0 || l.windBurstLevel < 1) {
            phase = Phase.FAILED;
            return delegateMace(o);
        }

        // Natural rain or existing water is sufficient; do not waste a water bucket in that case.
        if (l.wet) {
            phase = Phase.CHARGE_RIPTIDE_UP;
            phaseStarted = o.tick();
            return chargeRiptideUp(o);
        }

        if (world == null || "the_nether".equals(world.dimension()) || !o.onGround() || o.gliding()) {
            phase = Phase.FAILED;
            return delegateMace(o);
        }

        int bx = (int) Math.floor(o.x());
        int by = (int) Math.floor(o.y() + 0.01);
        int bz = (int) Math.floor(o.z());
        if (world.water(bx, by, bz)) {
            waterX = bx;
            waterY = by;
            waterZ = bz;
            placedWater = false;
            phase = Phase.WAIT_FOR_WATER;
            phaseStarted = o.tick();
            return waitForWater(o);
        }
        if (!world.solid(bx, by - 1, bz) || world.solid(bx, by, bz) || world.lava(bx, by, bz)) {
            phase = Phase.FAILED;
            return delegateMace(o);
        }

        int waterBucket = l.slot("water_bucket");
        if (waterBucket < 0) {
            if (l.has("water_bucket")) {
                BotAction restock = BotAction.idle(o.yaw(), o.pitch());
                restock.restock = "water_bucket";
                restock.state = stateName();
                restock.technique = "oreo_restock_water_bucket";
                return restock;
            }
            phase = Phase.FAILED;
            return delegateMace(o);
        }

        waterX = bx;
        waterY = by;
        waterZ = bz;
        placedWater = true;
        phase = Phase.WAIT_FOR_WATER;
        phaseStarted = o.tick();
        BotAction a = BotAction.idle(o.yaw(), o.pitch());
        a.pitch = 90;
        a.useItem = "water_bucket";
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        a.state = stateName();
        a.technique = "oreo_water_place";
        return a;
    }

    private BotAction waitForWater(Observation o) {
        Loadout l = o.loadout();
        boolean sourcePresent = world != null && world.water(waterX, waterY, waterZ);
        if (l.wet && (sourcePresent || !placedWater)) {
            phase = Phase.CHARGE_RIPTIDE_UP;
            phaseStarted = o.tick();
            return chargeRiptideUp(o);
        }
        if (o.tick() - phaseStarted > Math.max(12, profile.holdClickTicks + 4)) {
            phase = Phase.FAILED;
            return delegateMace(o);
        }
        BotAction a = BotAction.idle(o.yaw(), o.pitch());
        a.state = stateName();
        a.technique = "oreo_wait_for_wet_state";
        return a;
    }

    private BotAction chargeRiptideUp(Observation o) {
        Loadout l = o.loadout();
        int slot = l.slot("trident:riptide");
        if (slot < 0 || !l.wet) {
            phase = Phase.FAILED;
            return delegateMace(o);
        }
        BotAction a = BotAction.idle(o.yaw(), o.pitch());
        a.yaw = o.yaw();
        a.pitch = -90.0f;
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        a.state = stateName();

        if (!o.mainHand().equals("trident:riptide")) {
            a.hotbar = slot;
            a.technique = "oreo_select_riptide";
            return a;
        }

        if (o.tick() != lastChargeCountedTick) {
            chargeTicks = o.usingItem() ? chargeTicks + 1 : 0;
            lastChargeCountedTick = o.tick();
        }
        if (chargeTicks >= Math.max(1, profile.tridentChargeTicks)) {
            a.use = false;
            a.exactAim = true;
            a.technique = "oreo_riptide_launch_up";
            launchTick = o.tick();
            launchY = o.y();
            launchObserved = false;
            phase = Phase.PICK_UP_WATER;
            phaseStarted = o.tick();
            pickupRequestedAt = -1;
            return a;
        }
        a.use = true;
        a.technique = "oreo_riptide_charge_up";
        return a;
    }

    private BotAction pickUpWater(Observation o) {
        if (launchTick >= 0 && o.tick() - launchTick > 2 && o.vy() <= 0.05 && o.y() <= launchY + 0.25) {
            phase = Phase.FAILED;
            return delegateMace(o);
        }
        if (placedWater && world != null && world.water(waterX, waterY, waterZ)) {
            double dx = o.x() - (waterX + 0.5);
            double dy = o.eyeY() - (waterY + 0.5);
            double dz = o.z() - (waterZ + 0.5);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance <= 4.2) {
                Loadout l = o.loadout();
                if (l.slot("bucket") < 0 && l.has("bucket")) {
                    BotAction restock = BotAction.idle(o.yaw(), o.pitch());
                    restock.restock = "bucket";
                    restock.state = stateName();
                    restock.technique = "oreo_restock_bucket";
                    return restock;
                }
                if (l.slot("bucket") >= 0 && (pickupRequestedAt < 0 || o.tick() - pickupRequestedAt > Math.max(10, profile.holdClickTicks + 4))) {
                    BotAction a = BotAction.idle(o.yaw(), o.pitch());
                    a.yaw = (float) Math.toDegrees(Math.atan2(-((waterX + 0.5) - o.x()), (waterZ + 0.5) - o.z()));
                    a.pitch = pitchTo(o.x(), o.eyeY(), o.z(), waterX + 0.5, waterY + 0.5, waterZ + 0.5);
                    a.useItem = "bucket";
                    a.state = stateName();
                    a.technique = "oreo_water_pickup";
                    pickupRequestedAt = o.tick();
                    return a;
                }
                if (pickupRequestedAt >= 0 && o.tick() - pickupRequestedAt <= Math.max(10, profile.holdClickTicks + 4)) {
                    BotAction waiting = BotAction.idle(o.yaw(), o.pitch());
                    waiting.state = stateName();
                    waiting.technique = "oreo_water_pickup_pending";
                    return waiting;
                }
            }
        }
        // Once the source is gone, or we are already outside bucket reach, orient toward the opponent.
        if (!placedWater || world == null || !world.water(waterX, waterY, waterZ) || distanceToWater(o) > 4.2) {
            phase = Phase.AIM_DOWN;
            phaseStarted = o.tick();
            aimTicks = 0;
            return aimDown(o);
        }
        BotAction a = BotAction.idle(o.yaw(), o.pitch());
        a.state = stateName();
        a.technique = "oreo_wait_for_pickup";
        return a;
    }

    private BotAction aimDown(Observation o) {
        float[] aim = targetAngles(o);
        float turn = (float) Math.max(1.0, profile.maxTurnPerTick);
        BotAction a = BotAction.idle(o.yaw(), o.pitch());
        a.yaw = Angles.approach(o.yaw(), aim[0], turn);
        a.pitch = Math.max(-90.0f, Math.min(90.0f, Angles.approach(o.pitch(), aim[1], turn)));
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        a.state = stateName();
        a.technique = "oreo_align_downward";
        aimTicks++;
        double yawError = Angles.diff(o.yaw(), aim[0]);
        double pitchError = Math.abs(o.pitch() - aim[1]);
        double tolerance = Math.max(6.0, profile.clickAimTolerance + 2.0);
        boolean aligned = yawError <= tolerance && pitchError <= tolerance;
        // Prefer to lunge at or just after the apex, after the downward aim has settled. Keep a hard
        // timeout because servers can apply different Riptide velocity and animation durations.
        boolean nearApex = o.vy() <= 0.15;
        if ((aligned && (nearApex || aimTicks >= 12)) || aimTicks >= 16) {
            phase = Phase.LUNGE;
            phaseStarted = o.tick();
        }
        return a;
    }

    private BotAction lunge(Observation o) {
        Loadout l = o.loadout();
        int spear = l.lungeSlot;
        int mace = l.windBurstSlot >= 0 ? l.windBurstSlot : l.slot("mace");
        if (spear < 0 || mace < 0 || !l.canLunge() || profile.useLunge != 1 || o.gliding() || o.usingItem()) {
            phase = Phase.MACE;
            return delegateMace(o);
        }
        float[] aim = targetAngles(o);
        double yawError = Angles.diff(o.yaw(), aim[0]);
        double pitchError = Math.abs(o.pitch() - aim[1]);
        double tolerance = Math.max(7.0, profile.clickAimTolerance + 3.0);
        if (yawError > tolerance || pitchError > tolerance) {
            phase = Phase.AIM_DOWN;
            return aimDown(o);
        }
        BotAction a = BotAction.idle(o.yaw(), o.pitch());
        a.yaw = aim[0];
        a.pitch = aim[1];
        // jabSlot already marks this as an aimed action for MotionSmoother/Humanizer. Do not set
        // exactAim here: Humanizer treats exactAim + !use as a held-item release (for tridents/bows).
        a.jabSlot = spear;
        a.swapBack = mace;
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        a.state = stateName();
        a.technique = "oreo_lunge_swap";
        phase = Phase.MACE;
        return a;
    }

    private BotAction delegateMace(Observation o) {
        BotAction a = macePolicy.step(o);
        a.state = stateName();
        return a;
    }

    private double distanceToWater(Observation o) {
        double dx = o.x() - (waterX + 0.5);
        double dy = o.eyeY() - (waterY + 0.5);
        double dz = o.z() - (waterZ + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Calculates yaw/pitch for a slightly leaded point in the opponent's hitbox. */
    public static float[] targetAngles(Observation o) {
        double leadTicks = Math.min(3.0, Math.max(0.0, Math.abs(o.heightAbove()) / Math.max(0.6, Math.abs(o.vy()) + 0.3)));
        double tx = o.ox() + o.ovx() * leadTicks;
        double ty = o.oy() + o.loadout().oHeight * 0.55 + o.ovy() * leadTicks;
        double tz = o.oz() + o.ovz() * leadTicks;
        float yaw = (float) Math.toDegrees(Math.atan2(-(tx - o.x()), tz - o.z()));
        double horizontal = Math.hypot(tx - o.x(), tz - o.z());
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - o.eyeY(), horizontal));
        return new float[] {yaw, Math.max(-90.0f, Math.min(90.0f, pitch))};
    }

    private static float pitchTo(double x, double eyeY, double z, double tx, double ty, double tz) {
        return (float) -Math.toDegrees(Math.atan2(ty - eyeY, Math.hypot(tx - x, tz - z)));
    }

    private void resetCycle() {
        phase = Phase.PREPARE_WATER;
        phaseStarted = -1;
        launchTick = -1;
        pickupRequestedAt = -1;
        lastChargeCountedTick = Long.MIN_VALUE;
        chargeTicks = 0;
        aimTicks = 0;
        placedWater = false;
        launchY = 0;
        launchObserved = false;
        macePolicy = new MacePolicy(profile, false);
    }
}
