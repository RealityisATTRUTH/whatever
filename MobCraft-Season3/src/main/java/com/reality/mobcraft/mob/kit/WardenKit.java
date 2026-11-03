package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.item.ArmorUpgrades;
import com.reality.mobcraft.item.ModItems;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobTicker;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.warden.Senses;
import com.reality.mobcraft.warden.WardenEcholocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Warden. Draft numbers, waiting on Reality's sheet.
 *
 *   Passive          +9 attack damage, Resistance I, Fire Resistance
 *   R  Sonic Shriek  a sonic boom 15 blocks ahead, through walls and armor,
 *                    10 damage and knockback. 30 energy, 10s cooldown.
 *   Sneak+R  Rage    Strength I and Speed I for 15s. 40 energy, 1:00 cooldown.
 *   G  Echolocation  for 10s, white ghosts show where everything nearby was
 *                    1s ago. Sneaking makes no vibration, so it leaves none.
 *                    15 energy, 20s cooldown.
 *   Sneak+G Sniffing for 10s, red ghosts show where everything nearby was
 *                    10s ago. Sneaking doesn't hide scent. 15 energy, 20s cooldown.
 *   V  Burrow        dig into the ground for up to 8s: invisible, can't be hurt,
 *                    can't move or attack, mobs lose track of you, Regeneration I.
 *                    Press V again to come up early. Coming up knocks nearby
 *                    things back. 25 energy, 30s cooldown.
 *   Sneak+V  Sculk Teleport  jump to the sculk block you're looking at, up to
 *                    48 blocks away. 20 energy, 15s cooldown.
 *   Energy drain     every hit you give or take costs 1 energy
 *   Exhaustion       at 0 energy you're cursed for 1:00
 *   Allies           wardens, always, even if you hit them
 *   Sculk            shriekers ignore you, so you never summon wardens or get
 *                    Darkness from them
 *   Curse            no abilities, passives stay
 */
public final class WardenKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.WARDEN);

    private static final String SHRIEK = "sonic_shriek";
    private static final String RAGE = "rage";
    private static final String ECHOLOCATION = "echolocation";
    private static final String SNIFFING = "sniffing";
    private static final String BURROW = "burrow";
    private static final String SCULK_TELEPORT = "sculk_teleport";

    private static final double SHRIEK_RANGE = 15;
    private static final double SHRIEK_WIDTH = 1.5;
    private static final float SHRIEK_DAMAGE = 10;
    private static final int SENSE_TICKS = 200;     // Echolocation and Sniffing last 10s
    private static final int BURROW_TICKS = 160;    // 8s
    private static final double TELEPORT_RANGE = 48;
    private static final int EXHAUSTION_CURSE = 1200;

    private static final KitUtil.Timers ECHO_ACTIVE = new KitUtil.Timers();
    private static final KitUtil.Timers SNIFF_ACTIVE = new KitUtil.Timers();
    private static final KitUtil.Timers BURROWED = new KitUtil.Timers();
    private static final Set<UUID> WAS_BURROWED = new HashSet<>();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.ATTACK_DAMAGE, "warden_damage", 9.0, AttributeModifier.Operation.ADD_VALUE, true);
        KitUtil.passive(player, MobEffects.RESISTANCE, 0, true);
        KitUtil.passive(player, MobEffects.FIRE_RESISTANCE, 0, true);

        // Positions are always recorded. Markers only show while an ability is active,
        // and not at all with Echolocation Markers switched off in /mobcraftsettings.
        if (!WardenEcholocation.isEnabled(player)) WardenEcholocation.enable(player);
        boolean show = Senses.isOn(player);
        boolean echo = show && ECHO_ACTIVE.active(player);
        WardenEcholocation.setLayers(player, echo, show && SNIFF_ACTIVE.active(player));
        // While listening, everything it can hear glows blue as well.
        if (echo && player.tickCount % 10 == 0) WardenEcholocation.glowNearby(player, 15);

        tickBurrow(player);

        // Wardens are always friendly, even after you hit them. Sculk Darkness doesn't affect you.
        KitUtil.calmAllies(player, 48, ALLIES);
        if (player.hasEffect(MobEffects.DARKNESS)) player.removeEffect(MobEffects.DARKNESS);

        // Exhaustion.
        if (PlayerMob.energy(player) < 1.0f && !PlayerMob.isCursed(player)) {
            MobTicker.curse(player, EXHAUSTION_CURSE);
            KitUtil.actionBar(player, "You're exhausted! Cursed for 1:00.");
        }
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.ATTACK_DAMAGE, "warden_damage", 0, AttributeModifier.Operation.ADD_VALUE, false);
        KitUtil.passive(player, MobEffects.RESISTANCE, 0, false);
        KitUtil.passive(player, MobEffects.FIRE_RESISTANCE, 0, false);
        if (WardenEcholocation.isEnabled(player)) WardenEcholocation.disable(player);
        BURROWED.stop(player);
        setBurrowed(player, false);
        forget(player);
    }

    // --- Ultimate: Sculk Eruption, see SculkEruption ------------------------------------

    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Sculk Eruption";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        if (BURROWED.active(player)) {
            KitUtil.actionBar(player, "Come up first. Press V.");
            return false;
        }
        return SculkEruption.erupt(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        // Pressing V while burrowed always lets you come up, even when cursed.
        if (slot == ABILITY_3 && !sneaking && BURROWED.active(player)) {
            BURROWED.stop(player);
            return;
        }
        if (PlayerMob.isCursed(player)) {
            KitUtil.actionBar(player, "You're too exhausted to use abilities.");
            return;
        }
        if (BURROWED.active(player)) {
            KitUtil.actionBar(player, "Come up first. Press V.");
            return;
        }
        switch (slot) {
            case ABILITY_1 -> {
                if (sneaking) rage(player); else sonicShriek(player);
            }
            case ABILITY_2 -> {
                if (sneaking) sniff(player); else echolocate(player);
            }
            default -> {
                if (sneaking) sculkTeleport(player); else burrow(player);
            }
        }
    }

    // --- Sonic Shriek and Rage --------------------------------------------------------

    /** Like the vanilla Warden's sonic boom: hits everything along the line, through walls. */
    private static void sonicShriek(ServerPlayer player) {
        if (!KitUtil.tryUse(player, SHRIEK, "Sonic Shriek", 30, 200)) return;
        ServerLevel level = player.level();
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = start.add(look.scale(SHRIEK_RANGE));

        for (int i = 1; i <= SHRIEK_RANGE; i++) {
            Vec3 point = start.add(look.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 1.0f);

        AABB area = new AABB(start.x, start.y, start.z, end.x, end.y, end.z).inflate(SHRIEK_WIDTH);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
            Vec3 center = target.getBoundingBox().getCenter();
            double along = center.subtract(start).dot(look);
            if (along < 0 || along > SHRIEK_RANGE) continue;
            Vec3 closest = start.add(look.scale(along));
            if (closest.distanceTo(center) > SHRIEK_WIDTH + target.getBbWidth() / 2) continue;

            target.hurtServer(level, player.damageSources().sonicBoom(player), SHRIEK_DAMAGE);
            Vec3 push = look.scale(2.5);
            target.push(push.x, 0.5, push.z);
            KitUtil.syncMotion(target);
        }
    }

    private static void rage(ServerPlayer player) {
        if (!KitUtil.tryUse(player, RAGE, "Rage", 40, 1200)) return;
        KitUtil.effect(player, MobEffects.STRENGTH, 300, 0);
        KitUtil.effect(player, MobEffects.SPEED, 300, 0);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 2.0f, 1.0f);
        KitUtil.actionBar(player, "Rage for 15 seconds.");
    }

    // --- Echolocation and Sniffing ----------------------------------------------------

    private static void echolocate(ServerPlayer player) {
        if (!KitUtil.tryUse(player, ECHOLOCATION, "Echolocation", 15, 400)) return;
        ECHO_ACTIVE.start(player, SENSE_TICKS);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_LISTENING, SoundSource.PLAYERS, 1.0f, 1.0f);
        KitUtil.actionBar(player, "Listening for 10 seconds.");
    }

    private static void sniff(ServerPlayer player) {
        if (!KitUtil.tryUse(player, SNIFFING, "Sniffing", 15, 400)) return;
        SNIFF_ACTIVE.start(player, SENSE_TICKS);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 1.0f, 1.0f);
        KitUtil.actionBar(player, "Sniffing for 10 seconds.");
    }

    // --- Burrow -------------------------------------------------------------------

    private static void burrow(ServerPlayer player) {
        if (!player.onGround()) {
            KitUtil.actionBar(player, "You need to be on the ground to burrow.");
            return;
        }
        if (!KitUtil.tryUse(player, BURROW, "Burrow", 25, 600)) return;
        BURROWED.start(player, BURROW_TICKS);
        setBurrowed(player, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.0f, 1.0f);
        KitUtil.actionBar(player, "Burrowed. Press V to come up.");
    }

    private static void tickBurrow(ServerPlayer player) {
        boolean burrowed = BURROWED.active(player);
        boolean was = WAS_BURROWED.contains(player.getUUID());
        if (burrowed) {
            KitUtil.calmAllies(player, 32, mob -> true); // nothing can find you underground
            if (player.tickCount % 5 == 0) {
                player.level().sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.1, player.getZ(),
                        2, 0.3, 0.0, 0.3, 0.01);
            }
        }
        if (was && !burrowed) emerge(player);
        setBurrowed(player, burrowed);
    }

    private static void setBurrowed(ServerPlayer player, boolean on) {
        if (on) WAS_BURROWED.add(player.getUUID()); else WAS_BURROWED.remove(player.getUUID());
        KitUtil.passive(player, MobEffects.INVISIBILITY, 0, on);
        KitUtil.passive(player, MobEffects.REGENERATION, 0, on);
        KitUtil.modifier(player, Attributes.MOVEMENT_SPEED, "burrow_still", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
        KitUtil.modifier(player, Attributes.JUMP_STRENGTH, "burrow_jump", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
    }

    /** Coming up throws nearby things back. */
    private static void emerge(ServerPlayer player) {
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.5f, 1.0f);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4),
                e -> e != player && e.isAlive())) {
            Vec3 away = entity.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() < 1.0E-4) continue;
            flat = flat.normalize().scale(1.2);
            entity.push(flat.x, 0.4, flat.z);
            KitUtil.syncMotion(entity);
        }
    }

    // --- Sculk Teleport -----------------------------------------------------------------

    private static void sculkTeleport(ServerPlayer player) {
        HitResult hit = player.pick(TELEPORT_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            KitUtil.actionBar(player, "Look at a sculk block to teleport.");
            return;
        }
        ServerLevel level = player.level();
        BlockPos pos = blockHit.getBlockPos();
        if (!isSculk(level.getBlockState(pos))) {
            KitUtil.actionBar(player, "That isn't sculk.");
            return;
        }
        BlockPos feet = pos.above();
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
            KitUtil.actionBar(player, "There's no room on top of that sculk.");
            return;
        }
        if (!KitUtil.tryUse(player, SCULK_TELEPORT, "Sculk Teleport", 20, 300)) return;

        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
        player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private static boolean isSculk(BlockState state) {
        return state.is(Blocks.SCULK) || state.is(Blocks.SCULK_CATALYST) || state.is(Blocks.SCULK_SENSOR)
                || state.is(Blocks.CALIBRATED_SCULK_SENSOR) || state.is(Blocks.SCULK_SHRIEKER) || state.is(Blocks.SCULK_VEIN);
    }

    // --- Combat -----------------------------------------------------------------------

    @Override
    public boolean allowAttack(ServerPlayer player, LivingEntity victim, DamageSource source) {
        return !BURROWED.active(player);
    }

    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        // No warden ever hurts you, sonic booms included.
        if (source.getEntity() != null && source.getEntity().getType() == EntityTypes.WARDEN) return false;
        if (!BURROWED.active(player)) return true;
        // Underground you're safe from everything except the void and /kill.
        return source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL);
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        PlayerMob.setEnergy(player, PlayerMob.energy(player) - 1);
    }

    @Override
    public void afterHurt(ServerPlayer player, DamageSource source, float damage) {
        PlayerMob.setEnergy(player, PlayerMob.energy(player) - 1);
    }

    /**
     * The Warden's custom death drop: one tendril, left or right, from the side
     * of his head. Only when another player kills him, by hand or with a
     * projectile. Mobs, falling, lava and the like drop nothing.
     */
    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer killer) || killer == player) return;
        ArmorUpgrades.dropAt(player, player.getRandom().nextBoolean()
                ? ModItems.WARDEN_TENDRIL_LEFT : ModItems.WARDEN_TENDRIL_RIGHT);
    }

    static void forget(ServerPlayer player) {
        ECHO_ACTIVE.forget(player.getUUID());
        SNIFF_ACTIVE.forget(player.getUUID());
        BURROWED.forget(player.getUUID());
        WAS_BURROWED.remove(player.getUUID());
    }
}
