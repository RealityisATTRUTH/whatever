package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Skybreaker, the mace ability. Right-click with a Skybreaker mace:
 *
 *  1. ASCEND   you launch straight up into the sky with wind trailing behind you
 *  2. STAND    you flip over and land upside down on the underside of the sky as if it were glass, soles
 *              planted against it and head toward the ground, looking down at your enemy, while cracks spread
 *              out from under your feet
 *  3. SHATTER  the sky breaks: big glass shards fly off and you lose your footing for a moment
 *  4. DIVE     you tip forward and dive face-first at the enemy, speeding up as you fall
 *  5. IMPACT   a real mace smash (the game's own mace attack, so its damage and enchants apply), a ground
 *              shockwave with blocks rising out of the ground, debris, a camera shake and a short pause
 *
 * The target is the enemy in your crosshair, or the closest one in front of you. With no enemy you dive back
 * down where you started and the shockwave still goes off. Cooldown: 20 seconds at I, 17 at II, 14 at III.
 *
 * The server runs the timing and the hit. Each player's phase and target are synced to everyone, so all
 * players see the poses (SkybreakerClient) and the client of the player using it moves them. Dying, logging
 * out, changing dimension, or anything running too long ends it cleanly.
 */
public final class Skybreaker {

    public static final int NONE = 0, ASCEND = 1, STAND = 2, SHATTER = 3, DIVE = 4, IMPACT = 5;

    /** The phase, seen by everyone. */
    public static final AttachmentType<Integer> PHASE = AttachmentRegistry.create(MobCraft.id("skybreaker_phase"),
            b -> b.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
    /** The target's entity id, or -1. Seen by everyone. */
    public static final AttachmentType<Integer> TARGET = AttachmentRegistry.create(MobCraft.id("skybreaker_target"),
            b -> b.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private static final int ASCEND_MAX = 26, STAND_TICKS = 30, SHATTER_TICKS = 8, DIVE_MAX = 80, IMPACT_TICKS = 6;
    private static final DustParticleOptions CRACK_WHITE = new DustParticleOptions(0xF4F0FF, 1.3f);
    private static final DustParticleOptions CRACK_BLUE = new DustParticleOptions(0x9FD8FF, 1.1f);

    private static final class State {
        final ServerLevel level; final int lv; final double startY, height; final ItemStack mace;
        int phase = ASCEND, ticks; Vec3 hold, sky; LivingEntity target;
        State(ServerLevel level, int lv, double startY, LivingEntity target, ItemStack mace) {
            this.level = level; this.lv = lv; this.startY = startY; this.height = 14 + 2 * lv; this.target = target; this.mace = mace;
        }
    }
    private static final Map<ServerPlayer, State> ACTIVE = new HashMap<>();

    private Skybreaker() {}

    public static int phase(net.minecraft.world.entity.Entity e) {
        Integer p = e.getAttached(PHASE);
        return p == null ? NONE : p;
    }

    private static int level(ItemStack stack, net.minecraft.world.level.Level world) {
        return world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .get(MaceEnchants.SKYBREAKER).map(h -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof MaceItem) || player.isSpectator()) return InteractionResult.PASS;
            int lv = level(stack, world);
            if (lv <= 0) return InteractionResult.PASS;
            if (player.getCooldowns().isOnCooldown(stack) || phase(player) != NONE) return InteractionResult.FAIL;
            if (world instanceof ServerLevel level && player instanceof ServerPlayer sp) start(sp, level, stack, lv);
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (!ACTIVE.isEmpty()) tick(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> stop(handler.getPlayer())));
    }

    private static void start(ServerPlayer player, ServerLevel level, ItemStack mace, int lv) {
        LivingEntity target = pickTarget(player);
        player.getCooldowns().addCooldown(mace, 400 - 60 * (lv - 1));
        ACTIVE.put(player, new State(level, lv, player.getY(), target, mace));
        player.setAttached(PHASE, ASCEND);
        player.setAttached(TARGET, target == null ? -1 : target.getId());
        player.setDeltaMovement(0, 1.4, 0);
        KitUtil.syncMotion(player);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** The enemy in the crosshair, or the closest one in front. */
    private static LivingEntity pickTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        LivingEntity aimed = null, near = null;
        double bestAngle = 0.12, bestDist = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(40),
                e -> e != player && e.isAlive() && !e.isSpectator() && !e.isAlliedTo(player) && player.hasLineOfSight(e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            double angle = Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look))));
            if (angle < bestAngle) { bestAngle = angle; aimed = e; }
            if (dist <= 24 && angle < Math.toRadians(45) && dist < bestDist) { bestDist = dist; near = e; }
        }
        return aimed != null ? aimed : near;
    }

    private static void setPhase(ServerPlayer player, State s, int phase) {
        s.phase = phase; s.ticks = 0;
        player.setAttached(PHASE, phase);
    }

    public static void stop(ServerPlayer player) {
        if (player == null) return;
        ACTIVE.remove(player);
        player.removeAttached(PHASE);
        player.removeAttached(TARGET);
        player.setNoGravity(false);
        player.resetFallDistance();
    }

    private static void tick() {
        for (Iterator<Map.Entry<ServerPlayer, State>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ServerPlayer, State> entry = it.next();
            ServerPlayer p = entry.getKey(); State s = entry.getValue();
            if (!p.isAlive() || p.isRemoved() || p.level() != s.level || p.isSpectator()) {
                it.remove(); p.removeAttached(PHASE); p.removeAttached(TARGET); p.setNoGravity(false); continue;
            }
            s.ticks++;
            p.fallDistance = 0;
            switch (s.phase) {
                case ASCEND -> {
                    s.level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() - 0.3, p.getZ(), 2, 0.15, 0.1, 0.15, 0.01);
                    if (p.getY() >= s.startY + s.height || s.ticks >= ASCEND_MAX) {
                        s.hold = p.position();
                        s.sky = p.position().add(0, p.getBbHeight() + 0.05, 0);   // the sky surface their feet press on
                        setPhase(p, s, STAND);
                        s.level.playSound(null, p.blockPosition(), SoundEvents.GLASS_STEP, SoundSource.PLAYERS, 2.0f, 0.5f);
                        s.level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.8f);
                    }
                }
                case STAND -> {
                    if (s.ticks % 2 == 1) drawCrack(s.level, s.sky, s.ticks);
                    if (s.ticks % 7 == 0) {
                        s.level.playSound(null, p.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f - s.ticks * 0.02f);
                        s.level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
                    }
                    if (s.ticks >= STAND_TICKS) {
                        setPhase(p, s, SHATTER);
                        shatter(s.level, s.sky);
                    }
                }
                case SHATTER -> { if (s.ticks >= SHATTER_TICKS) setPhase(p, s, DIVE); }
                case DIVE -> {
                    s.level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 3, 0.2, 0.3, 0.2, 0.0);
                    s.level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
                    LivingEntity t = s.target;
                    boolean reached = t != null && t.isAlive() && p.getBoundingBox().inflate(1.2).intersects(t.getBoundingBox());
                    if (reached) { strike(p, s, t); setPhase(p, s, IMPACT); }
                    else if (p.onGround() && s.ticks > 2) { shockwave(p, s, null); setPhase(p, s, IMPACT); }
                    else if (s.ticks >= DIVE_MAX) { it.remove(); stop0(p); }
                }
                case IMPACT -> { if (s.ticks >= IMPACT_TICKS) { it.remove(); stop0(p); } }
                default -> { it.remove(); stop0(p); }
            }
        }
    }

    private static void stop0(ServerPlayer p) {
        p.removeAttached(PHASE); p.removeAttached(TARGET); p.setNoGravity(false); p.resetFallDistance();
    }

    /** The real mace attack, as if you'd fallen onto them: the game's smash damage, knockback and enchants all apply. */
    private static void strike(ServerPlayer p, State s, LivingEntity target) {
        p.setDeltaMovement(Vec3.ZERO);
        KitUtil.syncMotion(p);
        p.fallDistance = 8 + 2 * s.lv;
        if (p.getMainHandItem().getItem() instanceof MaceItem) p.attack(target);
        p.resetFallDistance();
        shockwave(p, s, target);
    }

    /** Ground shockwave: hurts and lifts enemies around (less than the strike itself), blocks rise out of the ground. */
    private static void shockwave(ServerPlayer p, State s, LivingEntity struck) {
        ServerLevel level = s.level;
        Vec3 at = struck != null ? struck.position() : p.position();
        double radius = 3 + s.lv;
        float wave = 3 + s.lv;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 2, radius),
                e -> MaceEnchants.affects(e, p, struck) && e.position().distanceTo(at) <= radius)) {
            e.hurtServer(level, level.damageSources().playerAttack(p), wave);
            Vec3 push = e.position().subtract(at).multiply(1, 0, 1);
            e.setDeltaMovement((push.lengthSqr() > 0.01 ? push.normalize().scale(0.5) : Vec3.ZERO).add(0, 0.5, 0));
            KitUtil.syncMotion(e);
        }
        BlockPos ground = BlockPos.containing(at.x, at.y - 0.5, at.z);
        MaceEnchants.risingBlocks(level, ground, (int) radius);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40;
            level.sendParticles(ParticleTypes.DUST_PLUME, at.x + Math.cos(a) * 1.5, at.y + 0.1, at.z + Math.sin(a) * 1.5, 1,
                    Math.cos(a) * 0.3, 0.05, Math.sin(a) * 0.3, 0.3);
        }
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(ground)),
                at.x, at.y + 0.3, at.z, 60, radius / 2, 0.3, radius / 2, 0.4);
        level.playSound(null, BlockPos.containing(at), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.6f, 0.7f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** Jagged cracks spreading out from under the player's feet across the sky, like glass breaking. */
    private static void drawCrack(ServerLevel level, Vec3 at, int tick) {
        double y = at.y - 0.02;
        double reach = Math.min(4.5, 0.3 + tick * 0.16);
        RandomSource rng = RandomSource.create(Double.doubleToLongBits(at.x * 31 + at.z));
        for (int arm = 0; arm < 9; arm++) {
            double a = Math.PI * 2 * arm / 9 + rng.nextDouble() * 0.4;
            double x = at.x, z = at.z, len = reach * (0.6 + rng.nextDouble() * 0.4);
            for (double d = 0; d < len; d += 0.24) {
                a += (rng.nextDouble() - 0.5) * 0.45;
                x += Math.cos(a) * 0.24; z += Math.sin(a) * 0.24;
                level.sendParticles(rng.nextInt(4) == 0 ? CRACK_BLUE : CRACK_WHITE, x, y, z, 1, 0, 0, 0, 0);
                if (d > 1 && rng.nextInt(14) == 0) {   // little side cracks
                    double b = a + (rng.nextBoolean() ? 1 : -1) * 0.9;
                    for (int k = 1; k < 4; k++) level.sendParticles(CRACK_WHITE, x + Math.cos(b) * 0.14 * k, y, z + Math.sin(b) * 0.14 * k, 1, 0, 0, 0, 0);
                }
            }
        }
        if (tick % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, at.x, y, at.z, 3, reach / 3, 0.01, reach / 3, 0.01);
    }

    /** The sky breaks: glass shards fly off and tumble down, with a burst of glass particles. */
    private static void shatter(ServerLevel level, Vec3 at) {
        RandomSource rng = level.getRandom();
        for (int i = 0; i < 18; i++) {
            double a = rng.nextDouble() * Math.PI * 2, r = 0.3 + rng.nextDouble() * 3.8;
            double x = at.x + Math.cos(a) * r, z = at.z + Math.sin(a) * r;
            Display.BlockDisplay shard = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
            if (shard == null) continue;
            shard.setBlockState((i % 3 == 0 ? Blocks.STAINED_GLASS.white() : Blocks.STAINED_GLASS.lightBlue()).defaultBlockState());
            shard.setPos(x, at.y - 0.05, z);
            float w = 0.5f + rng.nextFloat() * 0.9f, d = 0.4f + rng.nextFloat() * 0.8f;
            shard.setTransformation(new com.mojang.math.Transformation(new Vector3f(-w / 2, 0, -d / 2), null, new Vector3f(w, 0.06f, d), null));
            level.addFreshEntity(shard);
            float ox = (float) Math.cos(a) * (1.5f + rng.nextFloat() * 2.5f), oz = (float) Math.sin(a) * (1.5f + rng.nextFloat() * 2.5f);
            float drop = -(3 + rng.nextFloat() * 5);
            Quaternionf spin = new Quaternionf().rotateXYZ(rng.nextFloat() * 4 - 2, rng.nextFloat() * 4 - 2, rng.nextFloat() * 4 - 2);
            MaceEnchants.later(level, 1, () -> {
                shard.setTransformationInterpolationDelay(0);
                shard.setTransformationInterpolationDuration(24);
                shard.setTransformation(new com.mojang.math.Transformation(new Vector3f(ox - w / 2, drop, oz - d / 2), spin, new Vector3f(w * 0.7f, 0.06f, d * 0.7f), null));
            });
            MaceEnchants.later(level, 26, shard::discard);
        }
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.STAINED_GLASS.lightBlue().defaultBlockState()),
                at.x, at.y, at.z, 120, 2.0, 0.1, 2.0, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 2.0, 0.2, 2.0, 0.08);
        level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 40, 1.5, 0.1, 1.5, 0.15);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.5f, 0.4f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.5f, 0.7f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.7f, 1.5f);
    }

    /** True while the player is mid-Skybreaker; other code can use it to stay out of the way. */
    public static boolean active(Player p) { return phase(p) != NONE; }
}
