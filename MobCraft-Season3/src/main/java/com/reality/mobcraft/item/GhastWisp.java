package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Ghast Wisp: the Ghast's mob item. A little ember wrapped in a gust of wind, the way a wind
 * charge's gusts swirl when it hits a wall (models/item/ghast_wisp.json). It drops when another
 * player kills the Ghast (see MobRelics).
 *
 *   Throw it     right-click: a small fireball that flies like a wind charge. 1 second cooldown.
 *   On a hit     1 damage and 3 seconds on fire to whoever it hits, then it bursts into wind.
 *   The burst    pushes everything near it away, like a wind charge but weaker: a smaller
 *                burst and about 70% of the push. Thrown at your feet it gives a small boost.
 *                It never breaks blocks or starts fires on them.
 *   Brewing      it still brews into the Potion of Drifting.
 */
public final class GhastWisp {

    private static final float SPEED = 1.3f;          // a wind charge is 1.5
    private static final int COOLDOWN = 20;           // a wind charge is 10
    private static final float BURST_RADIUS = 1.0f;   // a wind charge is 1.2
    private static final float PUSH = 0.85f;          // a wind charge is 1.22
    private static final int FIRE_SECONDS = 3;

    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, MobCraft.id("ghast_wisp"));
    public static final EntityType<Thrown> ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.<Thrown>of(Thrown::new, MobCategory.MISC).noLootTable()
                    .sized(0.3125f, 0.3125f).eyeHeight(0f).clientTrackingRange(4).updateInterval(10).build(KEY));

    /** Made on first use: block tags aren't loaded yet when the class loads. */
    private static ExplosionDamageCalculator burst;

    private static ExplosionDamageCalculator burst() {
        if (burst == null) {
            burst = new SimpleExplosionDamageCalculator(true, false, Optional.of(PUSH),
                    BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(set -> (HolderSet<Block>) set));
        }
        return burst;
    }

    private GhastWisp() {}

    /** Touching the class registers the entity. Called once at startup. */
    public static void register() {}

    /** The item: thrown on right-click. */
    public static final class WispItem extends Item {
        public WispItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                Projectile.spawnProjectileFromRotation((lvl, owner, item) ->
                                new Thrown(player, lvl, player.getX(), player.getEyePosition().y(), player.getZ()),
                        server, stack, player, 0f, SPEED, 1.0f);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.NEUTRAL,
                    0.5f, 0.6f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.NEUTRAL,
                    0.3f, 1.6f);
            player.getCooldowns().addCooldown(stack, COOLDOWN);
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
    }

    /** The thrown wisp: flies like a wind charge, trailing little flames. */
    public static final class Thrown extends AbstractWindCharge {
        public Thrown(EntityType<? extends AbstractWindCharge> type, Level level) {
            super(type, level);
        }

        Thrown(Player owner, Level level, double x, double y, double z) {
            super(ENTITY, level, owner, x, y, z);
        }

        @Override
        public ItemStack getItem() {
            return new ItemStack(ModItems.GHAST_WISP);
        }

        /** Like a wind charge: not drawn right in your face for its first moment after you throw it. */
        @Override
        public boolean shouldRenderAtSqrDistance(double distance) {
            return (tickCount >= 2 || distance >= 3.5 * 3.5) && super.shouldRenderAtSqrDistance(distance);
        }

        @Override
        protected ParticleOptions getTrailParticle() {
            return ParticleTypes.SMALL_FLAME;
        }

        @Override
        protected void onHitEntity(EntityHitResult hit) {
            // A wind charge's own hit: 1 damage, then the burst.
            super.onHitEntity(hit);
            if (level() instanceof ServerLevel && hit.getEntity() != getOwner()) {
                hit.getEntity().igniteForSeconds(FIRE_SECONDS);
            }
        }

        @Override
        protected void explode(Vec3 at) {
            level().explode(this, null, burst(), at.x(), at.y(), at.z(), BURST_RADIUS, false, Level.ExplosionInteraction.TRIGGER,
                    ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_SMALL, WeightedList.of(), SoundEvents.WIND_CHARGE_BURST);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.FLAME, at.x(), at.y(), at.z(), 14, 0.2, 0.2, 0.2, 0.06);
                server.sendParticles(ParticleTypes.SMALL_FLAME, at.x(), at.y(), at.z(), 10, 0.3, 0.3, 0.3, 0.02);
                server.playSound(null, at.x(), at.y(), at.z(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.4f, 1.4f);
            }
        }
    }
}
