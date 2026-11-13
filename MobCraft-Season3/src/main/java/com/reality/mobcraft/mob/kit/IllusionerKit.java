package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * Illusioner. Draft numbers, waiting on Reality's sheet.
 *
 *   R  Clones       turn invisible for 8s while 3 illusioner clones appear
 *                   around you and fight for you. They vanish after 15s.
 *                   30 energy, 30s cooldown.
 *   G  Arrow Burst  8 arrows fanned out where you look. 20 energy, 10s cooldown.
 *   V  Blindness    whatever you're looking at within 24 blocks is blinded
 *                   for 6s. 15 energy, 20s cooldown.
 *   Allies          illagers: pillagers, vindicators, evokers, illusioners
 *   Curse           no Clones
 */
public final class IllusionerKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.PILLAGER, EntityTypes.VINDICATOR, EntityTypes.EVOKER, EntityTypes.ILLUSIONER);

    private static final String CLONES = "clones";
    private static final String ARROW_BURST = "arrow_burst";
    private static final String BLINDNESS = "blindness";
    private static final int CLONE_TICKS = 300;

    /** Owner -> their clones and when they vanish. */
    private record Clones(List<UUID> ids, int endsAt) {}
    private static final Map<UUID, Clones> CLONES_ALIVE = new HashMap<>();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        Clones clones = CLONES_ALIVE.get(player.getUUID());
        if (clones != null && KitUtil.now(player) >= clones.endsAt()) removeClones(player);

        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        removeClones(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        switch (slot) {
            case ABILITY_1 -> clones(player);
            case ABILITY_2 -> {
                if (!KitUtil.tryUse(player, ARROW_BURST, "Arrow Burst", 20, 200)) return;
                arrowBurst(player);
            }
            default -> {
                LivingEntity target = targetAhead(player, 24);
                if (target == null) {
                    KitUtil.actionBar(player, "Look at someone to blind them.");
                    return;
                }
                if (!KitUtil.tryUse(player, BLINDNESS, "Blindness", 15, 400)) return;
                KitUtil.effect(target, MobEffects.BLINDNESS, 120, 0);
                SpellFx.cast(player, 20, SpellFx.BLINDNESS);
                SpellFx.burst(player, target.getX(), target.getEyeY(), target.getZ(), SpellFx.BLINDNESS, 16);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    }

    private static void clones(ServerPlayer player) {
        if (PlayerMob.isCursed(player)) {
            KitUtil.actionBar(player, "You can't make clones while cursed.");
            return;
        }
        if (!KitUtil.tryUse(player, CLONES, "Clones", 30, 600)) return;
        removeClones(player);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI * 2 * i / 3;
            Mob clone = KitUtil.summon(player, "minecraft:illusioner",
                    player.getX() + Math.cos(angle) * 2, player.getY(), player.getZ() + Math.sin(angle) * 2, "");
            if (clone != null) ids.add(clone.getUUID());
        }
        CLONES_ALIVE.put(player.getUUID(), new Clones(ids, KitUtil.now(player) + CLONE_TICKS));
        poofs(player, ids);
        KitUtil.effect(player, MobEffects.INVISIBILITY, 160, 0);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * The illusioner's mirror trick: a puff where you vanish and where each clone appears,
     * with its blue spell sparkling through it. Nothing stays on you, so you stay hidden.
     */
    private static void poofs(ServerPlayer player, List<UUID> ids) {
        float s = player.getScale();
        var level = player.level();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, player.getX(), player.getY() + 0.9 * s, player.getZ(),
                20, 0.3 * s, 0.6 * s, 0.3 * s, 0.02);
        SpellFx.burst(player, player.getX(), player.getY() + 1.0 * s, player.getZ(), SpellFx.DISAPPEAR, 14);
        for (UUID id : ids) {
            Entity clone = level.getEntity(id);
            if (clone == null) continue;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, clone.getX(), clone.getY() + 1.0, clone.getZ(),
                    16, 0.3, 0.6, 0.3, 0.02);
            SpellFx.burst(player, clone.getX(), clone.getY() + 1.1, clone.getZ(), SpellFx.DISAPPEAR, 10);
        }
    }

    private static void removeClones(ServerPlayer player) {
        Clones clones = CLONES_ALIVE.remove(player.getUUID());
        if (clones == null) return;
        for (UUID id : clones.ids()) {
            Entity clone = player.level().getEntity(id);
            if (clone != null) clone.discard();
        }
    }

    /** Fans 8 arrows in front of you. They can't be picked up. */
    private static void arrowBurst(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        String owner = KitUtil.uuidNbt(player.getUUID());
        for (int i = 0; i < 8; i++) {
            float yaw = player.getYRot() + (i - 3.5f) * 6f;
            Vec3 dir = Vec3.directionFromRotation(player.getXRot(), yaw).scale(2.5);
            Vec3 start = eye.add(dir.normalize());
            KitUtil.command(level, "summon minecraft:arrow %.3f %.3f %.3f {Motion:[%.3f,%.3f,%.3f],Owner:%s,pickup:0b}",
                    start.x, start.y, start.z, dir.x, dir.y, dir.z, owner);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /** The closest living thing roughly where the player is looking. */
    private static LivingEntity targetAhead(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB area = player.getBoundingBox().inflate(range);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > range || to.normalize().dot(look) < 0.95) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    static void forget(ServerPlayer player) {
        removeClones(player);
    }

    // --- Ultimate: Hall of Mirrors ---------------------------------------------------------------
    /**
     * 6 clones appear around you while you turn invisible for 10s, and every
     * enemy player within 20 blocks is blinded for 6s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Hall of Mirrors";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        removeClones(player);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2 * i / 6;
            Mob clone = KitUtil.summon(player, "minecraft:illusioner",
                    player.getX() + Math.cos(angle) * 3, player.getY(), player.getZ() + Math.sin(angle) * 3, "");
            if (clone != null) ids.add(clone.getUUID());
        }
        CLONES_ALIVE.put(player.getUUID(), new Clones(ids, KitUtil.now(player) + CLONE_TICKS));
        poofs(player, ids);
        KitUtil.effect(player, MobEffects.INVISIBILITY, 200, 0);
        for (LivingEntity enemy : UltimateFx.enemies(player, 20)) {
            if (enemy instanceof Player) KitUtil.effect(enemy, MobEffects.BLINDNESS, 120, 0);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.5f, 0.7f);
        KitUtil.actionBar(player, "Hall of Mirrors!");
        return true;
    }
}
