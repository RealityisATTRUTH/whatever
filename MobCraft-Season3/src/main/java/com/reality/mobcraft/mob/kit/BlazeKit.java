package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobKit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blaze. DRAFT numbers from Reality's design.
 *
 *   Energy        100, 1 per second
 *   Passives      Fire Resistance, slow hover flight
 *   Weaknesses    water and rain hurt you, half a heart a second. Snowballs do
 *                 3 damage, like they do to real blazes. No flying in water.
 *
 *   Combat Phase  starts on its own when you hit a player or one hits you.
 *                 Flames and smoke pour off you, your melee hits set targets
 *                 on fire, and the Fireballs cooldown is halved. But your
 *                 energy stops regenerating and water hurts twice as much.
 *                 Ends after 10s with no hits either way (15s at home).
 *
 *   R  Fireballs     3 small fireballs in quick succession where you look.
 *                    15 energy, 4s cooldown (2s in Combat Phase).
 *   G  Fire Shield   for 6s, anyone who hits you in melee is set on fire and
 *                    you take half melee damage. 30 energy, 30s cooldown.
 *   3  Blaze Burst   Combat Phase only. A ring of fire throws back players
 *                    within 4 blocks and sets them on fire. 35 energy, 45s cooldown.
 *
 *   Home          Nether Fortress: Strength I, double energy regen, and
 *                 Combat Phase lasts 15s.
 *   Allies        blazes
 *   Self-give     blaze rods
 *   Curse         Snuffed Out, 3:00: no Combat Phase, no Fireballs, no
 *                 flying, no self-given blaze rods.
 *   Drops         16 blaze rods and 32 blaze powder (MobDrops)
 */
public final class BlazeKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.BLAZE);

    private static final String FIREBALLS = "fireballs";
    private static final String FIRE_SHIELD = "fire_shield";
    private static final String BLAZE_BURST = "blaze_burst";
    private static final float FLY_SPEED = 0.025f;

    private static final int PHASE_TICKS = 200;       // 10 seconds
    private static final int HOME_PHASE_TICKS = 300;  // 15 seconds in a fortress
    private static final float SNOWBALL_DAMAGE = 3.0f;
    private static final double BURST_RANGE = 4.0;

    /** Fireballs still to fire: player -> how many left. One every 5 ticks. */
    private static final Map<UUID, Integer> VOLLEY_LEFT = new HashMap<>();
    private static final KitUtil.Timers SHIELD = new KitUtil.Timers();
    private static final KitUtil.Timers PHASE = new KitUtil.Timers();
    private static final KitUtil.Flags IN_FORTRESS = new KitUtil.Flags();
    /** Whether the phase was on last tick, to tell the player when it ends. */
    private static final KitUtil.Flags WAS_IN_PHASE = new KitUtil.Flags();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        boolean cursed = PlayerMob.isCursed(player);
        if (player.tickCount % 20 == 0) IN_FORTRESS.set(player, KitUtil.inStructure(player, BuiltinStructures.FORTRESS));
        boolean home = IN_FORTRESS.is(player);
        boolean inPhase = PHASE.active(player);

        KitUtil.passive(player, MobEffects.FIRE_RESISTANCE, 0, true);
        KitUtil.passive(player, MobEffects.STRENGTH, 0, home);
        KitUtil.flight(player, !cursed && !player.isInWater(), FLY_SPEED);

        if (player.isInWaterOrRain() && player.tickCount % 20 == 0) {
            player.hurtServer(player.level(), player.damageSources().drown(), inPhase ? 2.0f : 1.0f);
        }

        catchSnowballs(player);

        Integer left = VOLLEY_LEFT.get(player.getUUID());
        if (left != null && player.tickCount % 5 == 0) {
            shoot(player);
            if (left <= 1) VOLLEY_LEFT.remove(player.getUUID()); else VOLLEY_LEFT.put(player.getUUID(), left - 1);
        }

        ServerLevel level = player.level();
        if (SHIELD.active(player) && player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 3, 0.5, 0.6, 0.5, 0.01);
        }
        if (inPhase && player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.35, 0.6, 0.35, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.6, player.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
        }
        if (inPhase && player.tickCount % 40 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
        if (WAS_IN_PHASE.is(player) && !inPhase) KitUtil.actionBar(player, "Your flames die down.");
        WAS_IN_PHASE.set(player, inPhase);

        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.FIRE_RESISTANCE, 0, false);
        KitUtil.passive(player, MobEffects.STRENGTH, 0, false);
        KitUtil.flight(player, false, FLY_SPEED);
        forget(player);
    }

    /** No regen while burning hot, double at home otherwise. */
    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        if (PHASE.active(player)) return 0.0f;
        return KitUtil.doubledAtHome(IN_FORTRESS.is(player), type);
    }

    // --- Combat Phase ---------------------------------------------------------------

    /** Starts the phase, or keeps it going, after a hit either way. */
    private static void stoke(ServerPlayer player) {
        if (PlayerMob.isCursed(player)) return;
        boolean wasOn = PHASE.active(player);
        PHASE.start(player, IN_FORTRESS.is(player) ? HOME_PHASE_TICKS : PHASE_TICKS);
        if (!wasOn) {
            KitUtil.actionBar(player, "Combat Phase! Your flames roar.");
            player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        boolean melee = source.getDirectEntity() == player;
        if (melee && PHASE.active(player)) victim.igniteForSeconds(4);
        if (victim instanceof Player) stoke(player);
    }

    @Override
    public void afterHurt(ServerPlayer player, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player other && other != player) stoke(player);
    }

    @Override
    public void onCursed(ServerPlayer player) {
        PHASE.stop(player);
        KitUtil.flight(player, false, FLY_SPEED);
    }

    // --- Abilities ------------------------------------------------------------------

    // --- Blaze rod firing ------------------------------------------------------------

    /** The setting's name in /mobcraftsettings. Off by default: Fireballs stay on the key. */
    public static final String ROD_SETTING = "blaze_rod";

    /** Works on both sides, so the settings page can show it. */
    public static boolean rodFiring(Player player) {
        return Boolean.TRUE.equals(player.getAttached(MobAttachments.BLAZE_ROD_FIRE));
    }

    /** Server only. */
    public static void setRodFiring(Player player, boolean on) {
        if (on) {
            player.setAttached(MobAttachments.BLAZE_ROD_FIRE, true);
        } else {
            player.removeAttached(MobAttachments.BLAZE_ROD_FIRE);
        }
    }

    /**
     * With Blaze Rod Fireballs on, right-clicking a blaze rod fires Fireballs,
     * the same as the key: same energy, cooldown and curse rule. The key still
     * works too, so each player can use whichever they like.
     */
    @Override
    public InteractionResult onUseItem(ServerPlayer player, InteractionHand hand) {
        if (!rodFiring(player) || !player.getItemInHand(hand).is(Items.BLAZE_ROD)) return InteractionResult.PASS;
        onAbility(player, ABILITY_1, player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        boolean cursed = PlayerMob.isCursed(player);
        if (slot == ABILITY_1) {
            if (cursed) {
                KitUtil.actionBar(player, "Snuffed out: you can't shoot while cursed.");
                return;
            }
            int cooldown = PHASE.active(player) ? 40 : 80;
            if (!KitUtil.tryUse(player, FIREBALLS, "Fireballs", 15, cooldown)) return;
            VOLLEY_LEFT.put(player.getUUID(), 3);
        } else if (slot == ABILITY_2) {
            if (!KitUtil.tryUse(player, FIRE_SHIELD, "Fire Shield", 30, 600)) return;
            SHIELD.start(player, 120);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 1.0f, 1.0f);
            KitUtil.actionBar(player, "Fire Shield for 6 seconds.");
        } else {
            if (!PHASE.active(player)) {
                KitUtil.actionBar(player, "Blaze Burst only works in Combat Phase.");
                return;
            }
            if (!KitUtil.tryUse(player, BLAZE_BURST, "Blaze Burst", 35, 900)) return;
            burst(player);
        }
    }

    private static void shoot(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle().normalize();
        SmallFireball fireball = new SmallFireball(level, player, look);
        Vec3 eye = player.getEyePosition();
        fireball.setPos(eye.x + look.x * 1.2, eye.y + look.y * 1.2 - 0.2, eye.z + look.z * 1.2);
        level.addFreshEntity(fireball);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Throws nearby enemy players up and away and sets them on fire. */
    private static void burst(ServerPlayer player) {
        ServerLevel level = player.level();
        for (Player target : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(BURST_RANGE),
                p -> p != player && p.isAlive() && !p.isSpectator() && !p.isAlliedTo(player)
                        && p.distanceTo(player) <= BURST_RANGE)) {
            Vec3 away = target.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0, away.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
            target.setDeltaMovement(flat.x * 1.3, 0.6, flat.z * 1.3);
            KitUtil.syncMotion(target);
            target.igniteForSeconds(5);
        }
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.FLAME, player.getX() + Math.cos(angle) * 2.0, player.getY() + 0.3,
                    player.getZ() + Math.sin(angle) * 2.0, 2, 0.1, 0.1, 0.1, 0.05);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.8f);
    }

    // --- Damage ---------------------------------------------------------------------

    /**
     * Snowballs hurt, like they do real blazes. A snowball does no damage to
     * players, so the game drops the hit before any damage code sees it.
     * Instead, a snowball about to hit you is caught a tick early, does its
     * damage and melts.
     */
    private static void catchSnowballs(ServerPlayer player) {
        ServerLevel level = player.level();
        var body = player.getBoundingBox().inflate(0.3);
        for (Snowball snowball : level.getEntitiesOfClass(Snowball.class, player.getBoundingBox().inflate(4),
                s -> s.isAlive() && s.getOwner() != player
                        && body.intersects(s.getBoundingBox().expandTowards(s.getDeltaMovement())))) {
            player.hurtServer(level, player.damageSources().thrown(snowball, snowball.getOwner()), SNOWBALL_DAMAGE);
            level.sendParticles(ParticleTypes.SMOKE, snowball.getX(), snowball.getY(), snowball.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.4f);
            snowball.discard();
        }
    }

    /** Fire Shield halves melee and burns the attacker. */
    @Override
    public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        boolean melee = source.getEntity() != null && source.getDirectEntity() == source.getEntity();
        if (!SHIELD.active(player) || !melee) return amount;
        if (source.getEntity() instanceof LivingEntity attacker) attacker.igniteForSeconds(4);
        return amount * 0.5f;
    }

    @Override
    public boolean canSelfGive(ServerPlayer player, String itemId) {
        return !(itemId.equals("blaze_rod") && PlayerMob.isCursed(player));
    }

    static void forget(ServerPlayer player) {
        VOLLEY_LEFT.remove(player.getUUID());
        SHIELD.forget(player.getUUID());
        PHASE.forget(player.getUUID());
        IN_FORTRESS.forget(player);
        WAS_IN_PHASE.forget(player);
    }

    // --- Ultimate: Inferno ---------------------------------------------------------------
    /**
     * Three rings of 16 small fireballs fly out from you half a second apart.
     * Enemies within 6 blocks catch fire for 8s, and Combat Phase starts.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Inferno";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        stoke(player);
        for (LivingEntity enemy : UltimateFx.enemies(player, 6)) enemy.igniteForSeconds(8);
        fireRing(player, 0);
        UltimateFx.later(player, 10, p -> fireRing(p, 1));
        UltimateFx.later(player, 20, p -> fireRing(p, 2));
        KitUtil.actionBar(player, "Inferno!");
        return true;
    }

    private static void fireRing(ServerPlayer player, int wave) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        for (int i = 0; i < 16; i++) {
            Vec3 dir = UltimateFx.around(i * 2 + (wave % 2), 32, -0.1);
            SmallFireball fireball = new SmallFireball(level, player, dir);
            fireball.setPos(eye.x + dir.x * 1.5, eye.y - 0.4, eye.z + dir.z * 1.5);
            level.addFreshEntity(fireball);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.7f);
    }
}
