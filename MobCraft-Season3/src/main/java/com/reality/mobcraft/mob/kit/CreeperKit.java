package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobLook;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * Creeper. Draft numbers, waiting on Reality's sheet.
 *
 *   R  Gunpowder Blast  a 1.5s fuse like a real creeper's, swelling and
 *                       flashing white, then explode where you are without
 *                       dying: power 3, or 6 while charged. Your own blast
 *                       never hurts you. 40 energy, 20s cooldown.
 *   G  Self Charge      become a charged creeper for 30s: your next blast is
 *                       double power, you glow, and the charged-creeper energy
 *                       swirls around you. 30 energy, 1:00 cooldown.
 *   V  Leaf Hide        while touching leaves, turn invisible for up to 10s.
 *                       Ends if you hit something or leave the leaves.
 *                       20 energy, 30s cooldown.
 *   Cats                within 6 blocks of a cat or ocelot you're slowed and can't blast
 *   Allies              creepers
 *   Curse               no Gunpowder Blast
 */
public final class CreeperKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.CREEPER);

    private static final String BLAST = "gunpowder_blast";
    private static final String CHARGE = "self_charge";
    private static final String HIDE = "leaf_hide";
    private static final float BLAST_POWER = 3.0f;
    /** A real creeper's fuse: 1.5 seconds. */
    private static final int FUSE_TICKS = 30;

    private static final KitUtil.Timers CHARGED = new KitUtil.Timers();
    private static final KitUtil.Timers HIDING = new KitUtil.Timers();
    private static final KitUtil.Flags NEAR_CAT = new KitUtil.Flags();
    private static final KitUtil.Timers FUSING = new KitUtil.Timers();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        if (player.tickCount % 10 == 0) {
            AABB area = player.getBoundingBox().inflate(6);
            boolean cat = !player.level().getEntitiesOfClass(Mob.class, area,
                    m -> m.getType() == EntityTypes.CAT || m.getType() == EntityTypes.OCELOT).isEmpty();
            NEAR_CAT.set(player, cat);
        }
        KitUtil.passive(player, MobEffects.SLOWNESS, 0, NEAR_CAT.is(player));
        KitUtil.passive(player, MobEffects.GLOWING, 0, CHARGED.active(player));
        MobLook.set(player, MobLook.CHARGED, CHARGED.active(player));
        MobLook.set(player, MobLook.FUSING, FUSING.active(player));

        boolean hiding = HIDING.active(player);
        if (hiding && !touchingLeaves(player)) {
            HIDING.stop(player);
            hiding = false;
            KitUtil.actionBar(player, "You left the leaves.");
        }
        KitUtil.passive(player, MobEffects.INVISIBILITY, 0, hiding);

        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.SLOWNESS, 0, false);
        KitUtil.passive(player, MobEffects.GLOWING, 0, false);
        KitUtil.passive(player, MobEffects.INVISIBILITY, 0, false);
        MobLook.clear(player);
        forget(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        switch (slot) {
            case ABILITY_1 -> blast(player);
            case ABILITY_2 -> {
                if (!KitUtil.tryUse(player, CHARGE, "Self Charge", 30, 1200)) return;
                CHARGED.start(player, 600);
                player.level().playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.4f);
                KitUtil.actionBar(player, "Charged for 30 seconds.");
            }
            default -> {
                if (!touchingLeaves(player)) {
                    KitUtil.actionBar(player, "You need to be in leaves to hide.");
                    return;
                }
                if (!KitUtil.tryUse(player, HIDE, "Leaf Hide", 20, 600)) return;
                HIDING.start(player, 200);
                player.level().playSound(null, player.blockPosition(), SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 0.8f, 0.8f);
                KitUtil.actionBar(player, "Hidden in the leaves.");
            }
        }
    }

    private static void blast(ServerPlayer player) {
        if (PlayerMob.isCursed(player)) {
            KitUtil.actionBar(player, "You can't blast while cursed.");
            return;
        }
        if (NEAR_CAT.is(player)) {
            KitUtil.actionBar(player, "A cat is too close. You're too scared to blast.");
            return;
        }
        if (FUSING.active(player)) return;
        if (!KitUtil.tryUse(player, BLAST, "Gunpowder Blast", 40, 400)) return;
        boolean charged = CHARGED.active(player);
        CHARGED.stop(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1.0f, 1.0f);
        // Hiss and swell first, like a real creeper. The blast goes off wherever you are by then.
        FUSING.start(player, FUSE_TICKS);
        MobLook.set(player, MobLook.FUSING, true);
        UltimateFx.later(player, FUSE_TICKS, p -> {
            FUSING.stop(p);
            MobLook.set(p, MobLook.FUSING, false);
            if (!PlayerMob.has(p, MobType.CREEPER) || !p.isAlive()) return;
            p.level().explode(p, p.getX(), p.getY(), p.getZ(), charged ? BLAST_POWER * 2 : BLAST_POWER, Level.ExplosionInteraction.MOB);
        });
    }

    /** Leaves at your feet or head, or right next to either. */
    private static boolean touchingLeaves(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        for (BlockPos pos : new BlockPos[]{feet, feet.above()}) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES)) return true;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.relative(side)).is(BlockTags.LEAVES)) return true;
            }
        }
        return level.getBlockState(feet.below()).is(BlockTags.LEAVES);
    }

    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        // Your own blast never hurts you.
        return !(source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == player);
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        if (HIDING.active(player)) HIDING.stop(player); // attacking gives you away
    }

    static void forget(ServerPlayer player) {
        CHARGED.forget(player.getUUID());
        HIDING.forget(player.getUUID());
        FUSING.forget(player.getUUID());
        NEAR_CAT.forget(player);
    }

    // --- Ultimate: Supernova ---------------------------------------------------------------
    /**
     * You glow and hiss for 1.5s, then blow up at power 7.5, over twice a
     * charged Gunpowder Blast. It never hurts you. A cat nearby stops it.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Supernova";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        if (NEAR_CAT.is(player)) {
            KitUtil.actionBar(player, "A cat is too close. You're too scared to blow.");
            return false;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 2.0f, 0.5f);
        KitUtil.effect(player, MobEffects.GLOWING, 40, 0);
        KitUtil.actionBar(player, "Supernova in 1.5 seconds!");
        FUSING.start(player, FUSE_TICKS);
        MobLook.set(player, MobLook.FUSING, true);
        UltimateFx.later(player, FUSE_TICKS, p -> {
            FUSING.stop(p);
            MobLook.set(p, MobLook.FUSING, false);
            ServerLevel level = p.level();
            level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 0.8f);
            level.explode(p, p.getX(), p.getY(), p.getZ(), BLAST_POWER * 2.5f, Level.ExplosionInteraction.MOB);
        });
        return true;
    }
}
