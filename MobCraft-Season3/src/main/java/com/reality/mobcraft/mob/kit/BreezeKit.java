package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import net.minecraft.world.entity.LivingEntity;

/**
 * Breeze (Williy). DRAFT numbers from the balance pass.
 *
 *   Passives      no fall damage, Jump Boost II. Arrows and tridents bounce
 *                 off, except in water. Takes 1.5 times the knockback.
 *   Gust          R: a wind charge where you look. 10 energy, 2s cooldown.
 *   Leap          G: launches you high and forward. 15 energy, 6s cooldown.
 *   Whirlwind     third key: players within 5 blocks are thrown up and away.
 *                 35 energy, 30s cooldown.
 *   Home          Trial Chambers: Speed I and double energy regen.
 *   Allies        breezes.
 *   Self-give     wind charges.
 *   Curse Still Air  no Leap, nothing bounces off, no self-given wind charges.
 *   Drops         4 breeze rods and 32 wind charges (MobDrops).
 */
public final class BreezeKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.BREEZE);

    private static final String GUST = "gust";
    private static final String LEAP = "leap";
    private static final String WHIRLWIND = "whirlwind";
    private static final double KNOCKBACK_TAKEN = 1.5;
    private static final double WHIRL_RANGE = 5;
    private static final Set<EntityType<?>> BOUNCES_OFF = Set.of(EntityTypes.ARROW, EntityTypes.SPECTRAL_ARROW, EntityTypes.TRIDENT);

    private static final KitUtil.Flags IN_CHAMBERS = new KitUtil.Flags();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        if (player.tickCount % 40 == 0) IN_CHAMBERS.set(player, KitUtil.inStructure(player, BuiltinStructures.TRIAL_CHAMBERS));
        boolean home = IN_CHAMBERS.is(player);
        KitUtil.passive(player, MobEffects.JUMP_BOOST, 1, true);
        KitUtil.passive(player, MobEffects.SPEED, 0, home);
        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.JUMP_BOOST, 1, false);
        KitUtil.passive(player, MobEffects.SPEED, 0, false);
        forget(player);
    }

    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        return KitUtil.doubledAtHome(IN_CHAMBERS.is(player), type);
    }

    /** No fall damage, and arrows and tridents bounce off unless cursed or in water. */
    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        if (source.is(DamageTypes.FALL)) return false;
        Entity direct = source.getDirectEntity();
        if (direct != null && BOUNCES_OFF.contains(direct.getType())
                && !PlayerMob.isCursed(player) && !player.isInWater()) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 1.0f, 1.0f);
            return false; // the game bounces an arrow back when it can't hurt its target
        }
        return true;
    }

    /** Light as air: knockback from a hit is half again as strong. */
    @Override
    public void afterHurt(ServerPlayer player, DamageSource source, float damage) {
        if (source.getEntity() == null || source.is(DamageTypes.FALL)) return;
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * KNOCKBACK_TAKEN, v.y, v.z * KNOCKBACK_TAKEN);
        KitUtil.syncMotion(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (!KitUtil.tryUse(player, GUST, "Gust", 10, 40)) return;
            gust(player);
        } else if (slot == ABILITY_2) {
            if (PlayerMob.isCursed(player)) {
                KitUtil.actionBar(player, "Still air: you can't Leap while cursed.");
                return;
            }
            if (!KitUtil.tryUse(player, LEAP, "Leap", 15, 120)) return;
            leap(player);
        } else {
            if (!KitUtil.tryUse(player, WHIRLWIND, "Whirlwind", 35, 600)) return;
            whirlwind(player);
        }
    }

    /** A wind charge fired from your eyes, owned by you so you get the credit. */
    private static void gust(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition().add(look.scale(1.2));
        Vec3 motion = look.scale(1.5);
        KitUtil.command(level, "summon minecraft:wind_charge %.3f %.3f %.3f {Motion:[%.3fd,%.3fd,%.3fd],Owner:%s}",
                eye.x, eye.y, eye.z, motion.x, motion.y, motion.z, KitUtil.uuidNbt(player.getUUID()));
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void leap(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
        player.setDeltaMovement(flat.x * 1.4, Math.max(0.9, look.y * 1.2 + 0.7), flat.z * 1.4);
        KitUtil.syncMotion(player);
        player.resetFallDistance();
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void whirlwind(ServerPlayer player) {
        ServerLevel level = player.level();
        for (Player target : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(WHIRL_RANGE),
                p -> p != player && p.isAlive() && !p.isSpectator() && !p.isAlliedTo(player)
                        && p.distanceTo(player) <= WHIRL_RANGE)) {
            Vec3 away = target.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0, away.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
            target.setDeltaMovement(flat.x * 1.2, 1.0, flat.z * 1.2);
            KitUtil.syncMotion(target);
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            level.sendParticles(ParticleTypes.GUST, player.getX() + Math.cos(angle) * 2.5, player.getY() + 0.5,
                    player.getZ() + Math.sin(angle) * 2.5, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 1.5f, 0.7f);
    }

    @Override
    public boolean canSelfGive(ServerPlayer player, String itemId) {
        return !(itemId.equals("wind_charge") && PlayerMob.isCursed(player));
    }

    static void forget(ServerPlayer player) {
        IN_CHAMBERS.forget(player);
    }

    // --- Ultimate: Tempest ---------------------------------------------------------------
    /**
     * 8 wind charges fly out in a ring, enemies within 10 blocks are thrown
     * high into the air, and you're lifted up with them.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Tempest";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        ServerLevel level = player.level();
        String owner = KitUtil.uuidNbt(player.getUUID());
        Vec3 center = player.position().add(0, 1, 0);
        for (int i = 0; i < 8; i++) {
            Vec3 d = UltimateFx.around(i, 8, 0);
            Vec3 start = center.add(d.scale(1.5));
            Vec3 motion = d.scale(1.2);
            KitUtil.command(level, "summon minecraft:wind_charge %.3f %.3f %.3f {Motion:[%.3fd,%.3fd,%.3fd],Owner:%s}",
                    start.x, start.y, start.z, motion.x, motion.y, motion.z, owner);
        }
        for (LivingEntity enemy : UltimateFx.enemies(player, 10)) UltimateFx.throwFrom(enemy, player.position(), 1.4, 1.6);
        player.setDeltaMovement(player.getDeltaMovement().x, 1.0, player.getDeltaMovement().z);
        KitUtil.syncMotion(player);
        for (int i = 0; i < 12; i++) {
            Vec3 d = UltimateFx.around(i, 12, 0);
            level.sendParticles(ParticleTypes.GUST, player.getX() + d.x * 4, player.getY() + 0.5, player.getZ() + d.z * 4, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 2.0f, 0.5f);
        KitUtil.actionBar(player, "Tempest!");
        return true;
    }
}
