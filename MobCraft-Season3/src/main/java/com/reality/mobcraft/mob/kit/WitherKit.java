package com.reality.mobcraft.mob.kit;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import com.mojang.datafixers.util.Pair;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wither. The Nether buffs and allies are from Reality's notes. The abilities
 * are draft numbers, waiting on his sheet.
 *
 *   Flight          double-tap jump to fly, a bit faster than walking
 *   Nether          Speed I anywhere in the Nether, Speed II within 200 blocks
 *                   of a fortress, Strength I only inside a fortress
 *   Undead          immune to Wither and poison, Instant Damage heals and
 *                   Instant Health hurts
 *   R  Black Skull  a wither skull where you look. 10 energy, 1s cooldown.
 *   Sneak+R Blue Skull  a dangerous blue skull that breaks blocks it hits.
 *                   25 energy, 8s cooldown.
 *   G  Charge       dash forward. Anything you ram takes 6 damage and Wither II
 *                   for 5s. 25 energy, 15s cooldown.
 *   V  Block Destruction  for 10 seconds every block you touch breaks, like the
 *                   Wither breaking out: walk, fly or charge through walls at any
 *                   speed, or stand still and it keeps clearing round you. Sneak to
 *                   dig down too. Bedrock and other wither-proof blocks stay.
 *                   30 energy, 30s cooldown.
 *   Allies          wither skeletons
 *   Curse           no skulls
 */
public final class WitherKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.WITHER_SKELETON);

    private static final String BLACK_SKULL = "black_skull";
    private static final String BLUE_SKULL = "blue_skull";
    private static final String CHARGE = "charge";
    private static final String BLOCK_DESTRUCTION = "block_destruction";
    private static final double FORTRESS_RANGE = 200;
    private static final int CHARGE_TICKS = 10;
    private static final float FLY_SPEED = 0.03f; // a bit faster than walking

    private static final KitUtil.Flags NEAR_FORTRESS = new KitUtil.Flags();
    private static final KitUtil.Flags IN_FORTRESS = new KitUtil.Flags();
    private static final KitUtil.Timers CHARGING = new KitUtil.Timers();
    private static final KitUtil.Timers DESTROYING = new KitUtil.Timers();
    private static final int DESTROY_TICKS = 200;   // 10 seconds
    private static final Map<UUID, Integer> DESTROYED = new HashMap<>();
    private static final Map<UUID, Integer> DESTROY_END = new HashMap<>();
    private static final Map<UUID, Set<Integer>> RAMMED = new HashMap<>();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        boolean nether = player.level().dimension() == Level.NETHER;
        if (nether && player.tickCount % 20 == 0) {
            IN_FORTRESS.set(player, KitUtil.inStructure(player, BuiltinStructures.FORTRESS));
        }
        if (nether && player.tickCount % 100 == 0) {
            NEAR_FORTRESS.set(player, nearFortress(player));
        }
        if (!nether) {
            IN_FORTRESS.forget(player);
            NEAR_FORTRESS.forget(player);
        }
        boolean near = NEAR_FORTRESS.is(player);
        boolean inside = IN_FORTRESS.is(player);

        KitUtil.passive(player, MobEffects.SPEED, near || inside ? 1 : 0, nether);
        KitUtil.passive(player, MobEffects.STRENGTH, 0, inside);

        if (player.hasEffect(MobEffects.WITHER)) player.removeEffect(MobEffects.WITHER);
        if (player.hasEffect(MobEffects.POISON)) player.removeEffect(MobEffects.POISON);

        KitUtil.flight(player, true, FLY_SPEED);
        tickCharge(player);
        tickDestruction(player);

        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.SPEED, 0, false);
        KitUtil.passive(player, MobEffects.STRENGTH, 0, false);
        KitUtil.flight(player, false, FLY_SPEED);
        forget(player);
    }

    @Override
    public boolean isUndead() {
        return true;
    }

    /** Is a fortress within 200 blocks? Asks the world generator, so it works for unexplored fortresses too. */
    private static boolean nearFortress(ServerPlayer player) {
        ServerLevel level = player.level();
        Holder<Structure> fortress = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(BuiltinStructures.FORTRESS);
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(fortress), player.blockPosition(), 13, false);
        if (found == null) return false;
        double dx = found.getFirst().getX() - player.getX();
        double dz = found.getFirst().getZ() - player.getZ();
        return dx * dx + dz * dz <= FORTRESS_RANGE * FORTRESS_RANGE;
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        switch (slot) {
            case ABILITY_1 -> {
                if (PlayerMob.isCursed(player)) {
                    KitUtil.actionBar(player, "You can't shoot skulls while cursed.");
                    return;
                }
                if (sneaking) {
                    if (!KitUtil.tryUse(player, BLUE_SKULL, "Blue Skull", 25, 160)) return;
                    skull(player, true);
                } else {
                    if (!KitUtil.tryUse(player, BLACK_SKULL, "Black Skull", 10, 20)) return;
                    skull(player, false);
                }
            }
            case ABILITY_2 -> {
                if (!KitUtil.tryUse(player, CHARGE, "Charge", 25, 300)) return;
                Vec3 look = player.getLookAngle().normalize();
                player.setDeltaMovement(look.scale(1.8));
                KitUtil.syncMotion(player);
                CHARGING.start(player, CHARGE_TICKS);
                RAMMED.put(player.getUUID(), new HashSet<>());
                player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.2f);
            }
            default -> {
                if (DESTROYING.active(player)) {
                    KitUtil.actionBar(player, "Block Destruction is already going.");
                    return;
                }
                if (!KitUtil.tryUse(player, BLOCK_DESTRUCTION, "Block Destruction", 30, 600)) return;
                DESTROYING.start(player, DESTROY_TICKS);
                DESTROY_END.put(player.getUUID(), KitUtil.now(player) + DESTROY_TICKS);
                DESTROYED.put(player.getUUID(), 0);
                player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
                breakBlocksAround(player);
            }
        }
    }

    private static void skull(ServerPlayer player, boolean blue) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle().normalize();
        WitherSkull skull = new WitherSkull(level, player, look);
        Vec3 eye = player.getEyePosition();
        skull.setPos(eye.x + look.x * 1.2, eye.y + look.y * 1.2 - 0.2, eye.z + look.z * 1.2);
        if (blue) skull.setDangerous(true);
        level.addFreshEntity(skull);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void tickCharge(ServerPlayer player) {
        if (!CHARGING.active(player)) {
            RAMMED.remove(player.getUUID());
            return;
        }
        Set<Integer> hit = RAMMED.computeIfAbsent(player.getUUID(), u -> new HashSet<>());
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(1.5), e -> e != player && e.isAlive())) {
            if (!hit.add(target.getId())) continue; // each thing only once per charge
            target.hurtServer(player.level(), player.damageSources().playerAttack(player), 6.0f);
            KitUtil.effect(target, MobEffects.WITHER, 100, 1);
        }
    }

    /** Block Destruction: every few ticks for 10 seconds, the blocks you're touching break, whatever your speed. */
    private static void tickDestruction(ServerPlayer player) {
        if (!DESTROYED.containsKey(player.getUUID())) return;
        if (!DESTROYING.active(player)) {
            int total = DESTROYED.remove(player.getUUID());
            KitUtil.actionBar(player, "Block Destruction over. Broke " + total + " blocks.");
            return;
        }
        if (player.tickCount % 3 == 0) breakBlocksAround(player);
    }

    /** Breaks every block the Wither's body is in or right against, a block out each side and above. */
    private static void breakBlocksAround(ServerPlayer player) {
        ServerLevel level = player.level();
        AABB box = player.getBoundingBox().inflate(0.6, 0.0, 0.6).expandTowards(0, 0.6, 0);
        int down = player.isShiftKeyDown() ? 1 : 0;   // sneak to dig down
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY + 0.01) - down, Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.WITHER_IMMUNE) || state.getDestroySpeed(level, pos) < 0) continue;
            if (!state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) continue;
            if (level.destroyBlock(pos, true, player)) broken++;
        }
        if (broken > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 0.7f, 1.0f);
            DESTROYED.merge(player.getUUID(), broken, Integer::sum);
        }
        int left = 0;
        if (DESTROYING.active(player)) left = (int) Math.ceil(remaining(player) / 20.0);
        if (left > 0) KitUtil.actionBar(player, "Block Destruction: " + left + "s. Broke " + DESTROYED.getOrDefault(player.getUUID(), 0) + " blocks.");
    }

    private static int remaining(ServerPlayer player) {
        return DESTROY_END.getOrDefault(player.getUUID(), 0) - KitUtil.now(player);
    }

    /**
     * Killed by a player: a nether star rises out of the body, explodes and
     * splinters into 2 Star Fragments (StarDeath). It takes 4 to make a
     * Fracture Nether Star, so two Wither kills.
     */
    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer killer) || killer == player) return;
        StarDeath.start(player.level(), player.position());
        com.reality.mobcraft.MobAdvancements.award(killer, "wither_slayer");
    }

    static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        NEAR_FORTRESS.forget(player);
        IN_FORTRESS.forget(player);
        CHARGING.forget(id);
        DESTROYING.forget(id);
        DESTROYED.remove(id);
        DESTROY_END.remove(id);
        RAMMED.remove(id);
    }

    // --- Ultimate: Skull Storm ---------------------------------------------------------------
    /**
     * 16 black skulls fly out in a ring and 3 blue skulls go where you look.
     * Enemies within 10 blocks get Wither II for 10s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Skull Storm";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        for (int i = 0; i < 16; i++) {
            Vec3 dir = UltimateFx.around(i, 16, -0.15);
            launchSkull(level, player, eye.add(dir.scale(1.5)), dir, false);
        }
        for (int i = -1; i <= 1; i++) {
            Vec3 dir = Vec3.directionFromRotation(player.getXRot(), player.getYRot() + i * 8);
            launchSkull(level, player, eye.add(dir.scale(1.5)), dir, true);
        }
        for (LivingEntity enemy : UltimateFx.enemies(player, 10)) KitUtil.effect(enemy, MobEffects.WITHER, 200, 1);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 2.0f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.8f);
        KitUtil.actionBar(player, "Skull Storm!");
        return true;
    }

    private static void launchSkull(ServerLevel level, ServerPlayer player, Vec3 at, Vec3 dir, boolean blue) {
        WitherSkull skull = new WitherSkull(level, player, dir);
        skull.setPos(at.x, at.y, at.z);
        if (blue) skull.setDangerous(true);
        level.addFreshEntity(skull);
    }
}
