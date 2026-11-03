package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Elder Guardian. DRAFT numbers from the balance pass.
 *
 *   Water         Dolphin's Grace, Water Breathing, Night Vision while under.
 *   Land          Slowness I and no sprinting.
 *   Size          1.2 times normal.
 *   Spikes        standing still, melee attackers take 1 heart back per hit.
 *   Laser Beam    R: locks onto the player you're looking at (24 blocks),
 *                 charges 2s while you keep them in sight, then 6 damage
 *                 through armor. Losing sight cancels it. 30 energy, 15s.
 *   Elder Curse   G: enemy players within 20 blocks (40 in a monument) see
 *                 the ghost face and get Mining Fatigue III for 30s.
 *                 40 energy, 2:00 cooldown.
 *   Guardians     third key: 2 guardians for RTS, in water only, 6 at most.
 *                 35 energy, 45s cooldown.
 *   Home          Ocean Monument: Resistance I, double energy regen.
 *   Allies        guardians, elder guardians, drowned.
 *   Conduits      within 8 blocks of an active conduit, in water or rain:
 *                 2 hearts every 2 seconds.
 *   Curse Beached no swim speed, no Laser Beam, spikes off.
 *   Drops         prismarine shards and wet sponges (MobDrops).
 */
public final class ElderGuardianKit implements MobKit {

    private static final String LASER = "laser_beam";
    private static final String ELDER_CURSE = "elder_curse";
    private static final String GUARDIANS = "call_guardians";
    private static final double LASER_RANGE = 24;
    private static final int LASER_CHARGE = 40;
    private static final float LASER_DAMAGE = 6.0f;
    private static final double CURSE_RANGE = 20;
    private static final int MAX_GUARDIANS = 6;
    private static final int STILL_TICKS = 10;
    private static final double CONDUIT_RANGE = 8;
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.GUARDIAN, EntityTypes.ELDER_GUARDIAN, EntityTypes.DROWNED);

    private record Charge(UUID target, int ticksLeft) {}

    private static final Map<UUID, Charge> CHARGING = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Integer> STILL = new HashMap<>();
    private static final KitUtil.Flags IN_MONUMENT = new KitUtil.Flags();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    // --- Passives ------------------------------------------------------------------

    @Override
    public void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean cursed = PlayerMob.isCursed(player);
        boolean inWater = player.isInWater();

        KitUtil.modifier(player, Attributes.SCALE, "elder_guardian_scale", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        KitUtil.passive(player, MobEffects.WATER_BREATHING, 0, true);
        KitUtil.passive(player, MobEffects.DOLPHINS_GRACE, 0, inWater && !cursed);
        KitUtil.passive(player, MobEffects.NIGHT_VISION, 0, player.isUnderWater());
        KitUtil.passive(player, MobEffects.SLOWNESS, 0, !inWater);
        if (!inWater && player.isSprinting()) player.setSprinting(false);

        if (player.tickCount % 40 == 0) {
            IN_MONUMENT.set(player, KitUtil.inStructure(player, BuiltinStructures.OCEAN_MONUMENT));
            conduitDamage(player);
        }
        KitUtil.passive(player, MobEffects.RESISTANCE, 0, IN_MONUMENT.is(player));

        // Standing still, for the spikes.
        Vec3 pos = player.position();
        Vec3 last = LAST_POS.put(id, pos);
        boolean moved = last == null || pos.distanceToSqr(last) > 1.0E-4;
        STILL.put(id, moved ? 0 : STILL.getOrDefault(id, 0) + 1);

        KitUtil.calmAllies(player, 32, ALLIES);
        tickLaser(player);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.SCALE, "elder_guardian_scale", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.passive(player, MobEffects.WATER_BREATHING, 0, false);
        KitUtil.passive(player, MobEffects.DOLPHINS_GRACE, 0, false);
        KitUtil.passive(player, MobEffects.NIGHT_VISION, 0, false);
        KitUtil.passive(player, MobEffects.SLOWNESS, 0, false);
        KitUtil.passive(player, MobEffects.RESISTANCE, 0, false);
        forget(player);
    }

    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        return KitUtil.doubledAtHome(IN_MONUMENT.is(player), type);
    }

    /** Spikes: while still, melee attackers take 1 heart back. */
    @Override
    public void afterHurt(ServerPlayer player, DamageSource source, float damage) {
        if (PlayerMob.isCursed(player) || source.is(DamageTypes.THORNS) || source.is(DamageTypeTags.IS_PROJECTILE)) return;
        if (STILL.getOrDefault(player.getUUID(), 0) < STILL_TICKS) return;
        if (source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity() && attacker != player) {
            attacker.hurtServer(player.level(), player.damageSources().thorns(player), 2.0f);
            player.level().playSound(null, attacker.blockPosition(), SoundEvents.THORNS_HIT, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    /** An active conduit nearby hurts it while it's wet, like conduits attack guardians. */
    private static void conduitDamage(ServerPlayer player) {
        if (!player.isInWaterOrRain()) return;
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();
        int r = (int) CONDUIT_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (!level.getBlockState(pos).is(Blocks.CONDUIT)) continue;
            if (level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit && conduit.isActive()
                    && new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).distanceTo(player.position()) <= CONDUIT_RANGE) {
                player.hurtServer(level, player.damageSources().magic(), 4.0f);
                return;
            }
        }
    }

    // --- Abilities -----------------------------------------------------------------

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (PlayerMob.isCursed(player)) {
                KitUtil.actionBar(player, "You're beached: no Laser Beam.");
                return;
            }
            if (CHARGING.containsKey(player.getUUID())) return;
            Player target = lookTarget(player);
            if (target == null) {
                KitUtil.actionBar(player, "Look at a player to lock on.");
                return;
            }
            if (!KitUtil.tryUse(player, LASER, "Laser Beam", 30, 300)) return;
            CHARGING.put(player.getUUID(), new Charge(target.getUUID(), LASER_CHARGE));
            player.level().playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.0f, 1.0f);
            KitUtil.actionBar(player, "Locked on to " + target.getName().getString() + ". Keep them in sight!");
        } else if (slot == ABILITY_2) {
            elderCurse(player);
        } else {
            callGuardians(player);
        }
    }

    /** The player closest to where you're looking, in range and in sight. */
    private static Player lookTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Player best = null;
        double bestDot = 0.97;
        for (Player other : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(LASER_RANGE),
                p -> p != player && p.isAlive() && !p.isSpectator() && !p.isAlliedTo(player))) {
            Vec3 to = other.getEyePosition().subtract(eye);
            if (to.length() > LASER_RANGE) continue;
            double dot = look.dot(to.normalize());
            if (dot > bestDot && player.hasLineOfSight(other)) {
                bestDot = dot;
                best = other;
            }
        }
        return best;
    }

    private static void tickLaser(ServerPlayer player) {
        Charge charge = CHARGING.get(player.getUUID());
        if (charge == null) return;
        ServerLevel level = player.level();
        Player target = level.getPlayerByUUID(charge.target());
        if (target == null || !target.isAlive() || target.distanceTo(player) > LASER_RANGE + 6
                || !player.hasLineOfSight(target) || PlayerMob.isCursed(player)) {
            CHARGING.remove(player.getUUID());
            KitUtil.actionBar(player, "Laser Beam lost its target.");
            return;
        }
        // The beam: a line of glowing particles that thickens as it charges.
        Vec3 from = player.getEyePosition().subtract(0, 0.3, 0);
        Vec3 to = target.getEyePosition().subtract(0, 0.4, 0);
        Vec3 step = to.subtract(from);
        int points = (int) (step.length() * 2);
        if (player.tickCount % 2 == 0) {
            for (int i = 1; i <= points; i++) {
                Vec3 p = from.add(step.scale((double) i / points));
                level.sendParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        int left = charge.ticksLeft() - 1;
        if (left > 0) {
            CHARGING.put(player.getUUID(), new Charge(charge.target(), left));
            return;
        }
        CHARGING.remove(player.getUUID());
        target.hurtServer(level, player.damageSources().indirectMagic(player, player), LASER_DAMAGE);
        level.playSound(null, target.blockPosition(), SoundEvents.GUARDIAN_HURT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 12, 0.3, 0.3, 0.3, 0.2);
    }

    private static void elderCurse(ServerPlayer player) {
        boolean monument = IN_MONUMENT.is(player);
        double range = monument ? CURSE_RANGE * 2 : CURSE_RANGE;
        if (!KitUtil.tryUse(player, ELDER_CURSE, "Elder Curse", 40, 2400)) return;
        int hit = 0;
        for (ServerPlayer other : player.level().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(range),
                p -> p != player && p.isAlive() && !p.isSpectator() && !p.isAlliedTo(player) && p.distanceTo(player) <= range)) {
            other.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0f));
            KitUtil.effect(other, MobEffects.MINING_FATIGUE, 600, 2);
            hit++;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.0f, 1.0f);
        KitUtil.actionBar(player, hit == 0 ? "No enemies close enough." : "Cursed " + hit + (hit == 1 ? " player." : " players."));
    }

    private static void callGuardians(ServerPlayer player) {
        if (!player.isInWater()) {
            KitUtil.actionBar(player, "The guardians only answer in water.");
            return;
        }
        List<Mob> guardians = KitUtil.ownedMobs(player, 64, mob -> mob.getType() == EntityTypes.GUARDIAN);
        if (guardians.size() >= MAX_GUARDIANS) {
            KitUtil.actionBar(player, "You already have " + MAX_GUARDIANS + " guardians.");
            return;
        }
        if (!KitUtil.tryUse(player, GUARDIANS, "Call the Guardians", 35, 900)) return;
        int room = Math.min(2, MAX_GUARDIANS - guardians.size());
        for (int i = 0; i < room; i++) {
            double angle = Math.PI * i;
            KitUtil.summon(player, "minecraft:guardian", player.getX() + Math.cos(angle) * 1.5, player.getY(),
                    player.getZ() + Math.sin(angle) * 1.5, "");
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // --- Death ---------------------------------------------------------------------

    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        CHARGING.remove(player.getUUID());
    }

    static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        CHARGING.remove(id);
        LAST_POS.remove(id);
        STILL.remove(id);
        IN_MONUMENT.forget(player);
    }

    // --- Ultimate: Abyssal Gaze ---------------------------------------------------------------
    /**
     * Every enemy within 32 blocks (48 in a monument) sees the ghost face,
     * takes 4 hearts of magic damage, and gets Mining Fatigue III for 1:00
     * and Slowness II for 6s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Abyssal Gaze";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        double range = IN_MONUMENT.is(player) ? 48 : 32;
        int hit = 0;
        for (LivingEntity enemy : UltimateFx.enemies(player, range)) {
            if (enemy instanceof ServerPlayer other) {
                other.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0f));
            }
            KitUtil.effect(enemy, MobEffects.MINING_FATIGUE, 1200, 2);
            KitUtil.effect(enemy, MobEffects.SLOWNESS, 120, 1);
            enemy.hurtServer(player.level(), player.damageSources().indirectMagic(player, player), 8.0f);
            hit++;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 2.0f, 0.8f);
        KitUtil.actionBar(player, "Abyssal Gaze! " + hit + (hit == 1 ? " enemy" : " enemies") + " cursed.");
        return true;
    }
}
