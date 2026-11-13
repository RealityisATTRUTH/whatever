package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobLook;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.effect.MobEffects;

/**
 * Vex (Aymeril).
 *
 *   Passive        vanilla vex size, flight at walking speed, no fall damage
 *   R  Phase       toggle: fly through blocks. Costs 2 energy a second. You
 *                  can't attack, open containers or use items while phasing.
 *                  Ends by itself at 0 energy. If you end inside a block you're
 *                  pushed to the nearest open space.
 *   G  Charge      dash forward and strike whatever's in front of you within
 *                  6 blocks with your held weapon. 15 energy, 6s cooldown.
 *   Borrowed Time  at 0 energy you take half a heart every 3s until energy recovers
 *   Allies         vexes and evokers
 *   Home           Woodland Mansion: double energy regen
 *   Curse          can't phase or fly
 */
public final class VexKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.VEX, EntityTypes.EVOKER);

    private static final String PHASE = "phase";
    private static final String CHARGE = "charge";
    private static final float FLY_SPEED = 0.02f;   // about walking speed
    private static final float PHASE_DRAIN = 2.0f;  // energy per second
    private static final double CHARGE_RANGE = 6;
    private static final int CHARGE_TICKS = 10;

    private record Charging(int targetId, int endsAt) {}

    private static final Map<UUID, Charging> CHARGING = new HashMap<>();
    private static final KitUtil.Flags IN_MANSION = new KitUtil.Flags();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        boolean cursed = PlayerMob.isCursed(player);
        KitUtil.modifier(player, Attributes.SCALE, "vex_scale", -0.55, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        KitUtil.flight(player, !cursed, FLY_SPEED);

        if (player.tickCount % 40 == 0) {
            IN_MANSION.set(player, KitUtil.inStructure(player, BuiltinStructures.WOODLAND_MANSION));
        }

        // Phase ends when cursed or out of energy.
        if (isPhasing(player) && (cursed || PlayerMob.energy(player) <= 0)) {
            stopPhasing(player);
            KitUtil.actionBar(player, cursed ? "The curse pulled you out of phase." : "Out of energy. Phase ended.");
        }

        // Borrowed Time.
        if (!isPhasing(player) && PlayerMob.energy(player) < 1.0f && player.tickCount % 60 == 0) {
            player.hurtServer(player.level(), player.damageSources().magic(), 1.0f);
        }

        tickCharge(player);

        // For MobLookLayer: wings flap in flight, see-through while phasing, red eyes while charging.
        MobLook.set(player, MobLook.FLYING, player.getAbilities().flying);
        MobLook.set(player, MobLook.PHASING, isPhasing(player));
        MobLook.set(player, MobLook.CHARGING, CHARGE_LOOK.active(player));

        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        if (isPhasing(player)) stopPhasing(player);
        KitUtil.modifier(player, Attributes.SCALE, "vex_scale", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.flight(player, false, FLY_SPEED);
        MobLook.clear(player);
        forget(player);
    }

    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        if (isPhasing(player)) return -PHASE_DRAIN;
        return KitUtil.doubledAtHome(IN_MANSION.is(player), type);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (isPhasing(player)) {
                stopPhasing(player);
                return;
            }
            if (PlayerMob.isCursed(player)) {
                KitUtil.actionBar(player, "You can't phase while cursed.");
                return;
            }
            if (!KitUtil.tryUse(player, PHASE, "Phase", 2, 20)) return;
            player.setAttached(MobAttachments.PHASING, true);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.level().playSound(null, player.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1.0f, 1.4f);
            KitUtil.actionBar(player, "Phasing. Press R to stop.");
        } else if (slot == ABILITY_2) {
            if (isPhasing(player)) {
                KitUtil.actionBar(player, "You can't charge while phasing.");
                return;
            }
            if (!KitUtil.tryUse(player, CHARGE, "Charge", 15, 120)) return;
            charge(player);
        } else {
            KitUtil.actionBar(player, "The Vex has no third ability.");
        }
    }

    // --- Phase ----------------------------------------------------------------------

    public static boolean isPhasing(Player player) {
        return Boolean.TRUE.equals(player.getAttached(MobAttachments.PHASING));
    }

    private static void stopPhasing(ServerPlayer player) {
        player.removeAttached(MobAttachments.PHASING);
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        boolean stuck = !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
        if (stuck) {
            BlockPos free = KitUtil.nearestFreeSpot(level, feet, 8);
            if (free != null) player.teleportTo(free.getX() + 0.5, free.getY(), free.getZ() + 0.5);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.VEX_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    @Override
    public InteractionResult onUseItem(ServerPlayer player, InteractionHand hand) {
        return isPhasing(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    @Override
    public InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand) {
        return isPhasing(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        if (source.is(DamageTypes.FALL)) return false;
        return !(isPhasing(player) && source.is(DamageTypes.IN_WALL)); // no suffocating while phasing
    }

    // --- Charge -----------------------------------------------------------------------

    /** How long the eyes glow red after a Charge. */
    private static final KitUtil.Timers CHARGE_LOOK = new KitUtil.Timers();

    private static void charge(ServerPlayer player) {
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity target = targetAhead(player, look);
        Vec3 direction = target == null ? look
                : target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        player.setDeltaMovement(direction.scale(1.6));
        KitUtil.syncMotion(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1.0f, 1.0f);
        CHARGE_LOOK.start(player, 30);
        if (target != null) {
            CHARGING.put(player.getUUID(), new Charging(target.getId(), KitUtil.now(player) + CHARGE_TICKS));
        }
    }

    private static void tickCharge(ServerPlayer player) {
        Charging charging = CHARGING.get(player.getUUID());
        if (charging == null) return;
        Entity target = player.level().getEntity(charging.targetId());
        if (target == null || !target.isAlive() || KitUtil.now(player) > charging.endsAt()) {
            CHARGING.remove(player.getUUID());
            return;
        }
        if (player.distanceTo(target) < 3.0) {
            CHARGING.remove(player.getUUID());
            player.attack(target); // a normal hit with whatever you're holding
        }
    }

    /** The closest living thing roughly in front of the player within charge range. */
    private static LivingEntity targetAhead(ServerPlayer player, Vec3 look) {
        Vec3 eye = player.getEyePosition();
        AABB area = player.getBoundingBox().inflate(CHARGE_RANGE);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > CHARGE_RANGE || to.normalize().dot(look) < 0.8) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    static void forget(ServerPlayer player) {
        if (isPhasing(player)) stopPhasing(player); // never log out stuck in a wall
        CHARGING.remove(player.getUUID());
        CHARGE_LOOK.forget(player.getUUID());
        IN_MANSION.forget(player);
    }

    // --- Ultimate: Spectral Legion ---------------------------------------------------------------
    /**
     * 5 vexes fight for you for 30s and take RTS orders, and you get
     * Strength I and Speed I for 10s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Spectral Legion";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI * 2 * i / 5;
            UltimateFx.summonFor(player, "minecraft:vex", player.getX() + Math.cos(angle), player.getY() + 1,
                    player.getZ() + Math.sin(angle), "LifeTicks:600", 600);
        }
        KitUtil.effect(player, MobEffects.STRENGTH, 200, 0);
        KitUtil.effect(player, MobEffects.SPEED, 200, 0);
        player.level().playSound(null, player.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1.5f, 0.7f);
        KitUtil.actionBar(player, "Spectral Legion!");
        return true;
    }
}
