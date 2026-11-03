package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.RandomSource;

/**
 * Skeleton (MusicPlayer).
 *
 *   Self-give      bow with Power III and Curse of Vanishing, arrows, bones
 *   Biome arrows   where he fires decides the effect: Desert Weakness 30s,
 *                  snowy biomes Slowness 30s, swamps Poison 4s
 *   Lock On        R: your next 3 arrows home in. 25 energy, 20s cooldown.
 *   Skeleton Horse G: a saddled skeleton horse you ride at once. 40 energy,
 *                  2:00 cooldown. Calling a new one sends the old one away.
 *   Undead         poison immune, Instant Damage heals, Instant Health hurts,
 *                  Smite does bonus damage, burns in sunlight without a helmet,
 *                  Weakness I within 8 blocks of a wolf
 *   Allies         skeletons, strays, bogged
 *   Home           Soul Sand Valley: Speed I and double energy regen
 *   Curse          no Lock On, no self-given arrows
 */
public final class SkeletonKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED);

    private static final String LOCK_ON = "lock_on";
    private static final String HORSE = "skeleton_horse";
    private static final int HOMING_ARROWS = 3;
    private static final double HOMING_RANGE = 24;

    private enum BiomeArrow { NONE, WEAKNESS, SLOWNESS, POISON }

    /** How many Lock On arrows each skeleton has left. */
    private static final Map<UUID, Integer> LOCK_ON_LEFT = new HashMap<>();
    /** Arrows currently homing, by owner. */
    private static final Map<UUID, List<Projectile>> HOMING = new HashMap<>();
    /** The biome effect each arrow carries, decided when it was fired. */
    private static final Map<Entity, BiomeArrow> ARROW_BIOME = new WeakHashMap<>();
    /** Each skeleton's current horse. */
    private static final Map<UUID, UUID> HORSES = new HashMap<>();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        boolean home = KitUtil.inBiome(player, Biomes.SOUL_SAND_VALLEY);
        KitUtil.passive(player, MobEffects.SPEED, 0, home);

        // Undead: no poison.
        if (player.hasEffect(MobEffects.POISON)) player.removeEffect(MobEffects.POISON);

        if (player.tickCount % 10 == 0) {
            // Weakness near wolves.
            AABB area = player.getBoundingBox().inflate(8);
            boolean wolf = !player.level().getEntitiesOfClass(Mob.class, area, m -> m.getType() == EntityTypes.WOLF).isEmpty();
            KitUtil.passive(player, MobEffects.WEAKNESS, 0, wolf);

            // Sunlight burns without a helmet.
            if (inSunlight(player) && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                player.igniteForSeconds(8);
            }
        }

        KitUtil.calmAllies(player, 32, ALLIES);

        steerHomingArrows(player);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.SPEED, 0, false);
        KitUtil.passive(player, MobEffects.WEAKNESS, 0, false);
        forget(player);
    }

    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        return KitUtil.doubledAtHome(KitUtil.inBiome(player, Biomes.SOUL_SAND_VALLEY), type);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (PlayerMob.isCursed(player)) {
                KitUtil.actionBar(player, "You can't Lock On while cursed.");
                return;
            }
            if (!KitUtil.tryUse(player, LOCK_ON, "Lock On", 25, 400)) return;
            LOCK_ON_LEFT.put(player.getUUID(), HOMING_ARROWS);
            player.level().playSound(null, player.blockPosition(), SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.7f);
            KitUtil.actionBar(player, "Locked on. Your next 3 arrows will home in.");
        } else if (slot == ABILITY_2) {
            if (!KitUtil.tryUse(player, HORSE, "Skeleton Horse", 40, 2400)) return;
            summonHorse(player);
        } else {
            KitUtil.actionBar(player, "The Skeleton has no third ability.");
        }
    }

    private static void summonHorse(ServerPlayer player) {
        ServerLevel level = player.level();
        UUID old = HORSES.remove(player.getUUID());
        if (old != null) {
            Entity oldHorse = level.getEntity(old);
            if (oldHorse != null) oldHorse.discard();
        }
        // Both saddle formats, so it works whichever this version reads.
        Mob horse = KitUtil.summon(player, "minecraft:skeleton_horse", player.getX(), player.getY(), player.getZ(),
                "Tame:1b,SaddleItem:{id:\"minecraft:saddle\",count:1},equipment:{saddle:{id:\"minecraft:saddle\",count:1}}");
        if (horse == null) {
            KitUtil.actionBar(player, "The horse couldn't spawn here.");
            return;
        }
        HORSES.put(player.getUUID(), horse.getUUID());
        player.startRiding(horse);
        level.playSound(null, player.blockPosition(), SoundEvents.SKELETON_HORSE_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // --- Arrows -----------------------------------------------------------------

    @Override
    public void onProjectileFired(ServerPlayer player, Projectile projectile) {
        EntityType<?> type = projectile.getType();
        if (type != EntityTypes.ARROW && type != EntityTypes.SPECTRAL_ARROW) return;

        ARROW_BIOME.put(projectile, biomeArrow(player));

        int left = LOCK_ON_LEFT.getOrDefault(player.getUUID(), 0);
        if (left > 0) {
            if (left == 1) LOCK_ON_LEFT.remove(player.getUUID()); else LOCK_ON_LEFT.put(player.getUUID(), left - 1);
            HOMING.computeIfAbsent(player.getUUID(), u -> new ArrayList<>()).add(projectile);
        }
    }

    private static BiomeArrow biomeArrow(ServerPlayer player) {
        if (KitUtil.inBiome(player, Biomes.DESERT)) return BiomeArrow.WEAKNESS;
        if (KitUtil.inBiome(player, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.SNOWY_SLOPES, Biomes.SNOWY_BEACH,
                Biomes.ICE_SPIKES, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS, Biomes.GROVE, Biomes.FROZEN_RIVER,
                Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN)) return BiomeArrow.SLOWNESS;
        if (KitUtil.inBiome(player, Biomes.SWAMP, Biomes.MANGROVE_SWAMP)) return BiomeArrow.POISON;
        return BiomeArrow.NONE;
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        Entity arrow = source.getDirectEntity();
        if (arrow == null || arrow == player) return;
        BiomeArrow kind = ARROW_BIOME.remove(arrow);
        if (kind == null) return;
        switch (kind) {
            case WEAKNESS -> KitUtil.effect(victim, MobEffects.WEAKNESS, 600, 0);
            case SLOWNESS -> KitUtil.effect(victim, MobEffects.SLOWNESS, 600, 0);
            case POISON -> KitUtil.effect(victim, MobEffects.POISON, 80, 0);
            case NONE -> { }
        }
    }

    private static void steerHomingArrows(ServerPlayer player) {
        List<Projectile> arrows = HOMING.get(player.getUUID());
        if (arrows == null) return;
        Iterator<Projectile> it = arrows.iterator();
        while (it.hasNext()) {
            Projectile arrow = it.next();
            Vec3 motion = arrow.getDeltaMovement();
            double speed = motion.length();
            // Stop once it lands, hits, or has flown for 5 seconds.
            if (arrow.isRemoved() || speed < 0.2 || arrow.tickCount > 100) {
                it.remove();
                continue;
            }
            LivingEntity target = findTarget(player, arrow, motion);
            if (target == null) continue;
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
            Vec3 steered = motion.normalize().scale(0.6).add(toTarget.scale(0.4)).normalize().scale(speed);
            arrow.setDeltaMovement(steered);
            KitUtil.syncMotion(arrow);
        }
        if (arrows.isEmpty()) HOMING.remove(player.getUUID());
    }

    /** The closest living thing roughly ahead of the arrow that isn't the shooter or an ally. */
    private static LivingEntity findTarget(ServerPlayer owner, Projectile arrow, Vec3 motion) {
        Vec3 heading = motion.normalize();
        AABB area = arrow.getBoundingBox().inflate(HOMING_RANGE);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : arrow.level().getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != owner)) {
            EntityType<?> type = entity.getType();
            if (type == EntityTypes.ARMOR_STAND || type == EntityTypes.SKELETON || type == EntityTypes.STRAY
                    || type == EntityTypes.BOGGED) continue;
            Vec3 to = entity.getBoundingBox().getCenter().subtract(arrow.position());
            double distance = to.length();
            if (distance < 0.5 || to.normalize().dot(heading) < 0.5) continue; // must be in front
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    // --- Undead -----------------------------------------------------------------

    @Override
    public boolean isUndead() {
        return true;
    }

    @Override
    public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        // Smite: +2.5 damage per level against undead, like vanilla.
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            Holder<Enchantment> smite = KitUtil.enchantment(player.level(), Enchantments.SMITE);
            int level = EnchantmentHelper.getItemEnchantmentLevel(smite, weapon);
            if (level > 0) return amount + 2.5f * level;
        }
        return amount;
    }

    private static boolean inSunlight(ServerPlayer player) {
        ServerLevel level = player.level();
        if (level.getSkyDarken() >= 4) return false; // night
        if (level.isRainingAt(player.blockPosition()) || player.isInWaterOrRain()) return false;
        return level.canSeeSky(player.blockPosition().above());
    }

    // --- Items and curse -----------------------------------------------------------

    @Override
    public boolean canSelfGive(ServerPlayer player, String itemId) {
        return !(PlayerMob.isCursed(player) && itemId.equals("arrow"));
    }

    @Override
    public void customizeSelfGive(ServerPlayer player, String itemId, ItemStack stack) {
        if (!itemId.equals("bow")) return;
        KitUtil.enchant(player, stack, Enchantments.POWER, 3);
        KitUtil.enchant(player, stack, Enchantments.VANISHING_CURSE, 1);
    }

    @Override
    public void onCursed(ServerPlayer player) {
        LOCK_ON_LEFT.remove(player.getUUID());
    }

    static void forget(ServerPlayer player) {
        LOCK_ON_LEFT.remove(player.getUUID());
        HOMING.remove(player.getUUID());
    }

    // --- Ultimate: Arrow Rain ---------------------------------------------------------------
    /**
     * Arrows rain down where you look, up to 48 blocks away: 48 arrows over
     * 3 seconds across 5 blocks. They can't be picked up.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Arrow Rain";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        HitResult hit = player.pick(48, 1.0f, false);
        Vec3 center = hit.getType() == HitResult.Type.MISS
                ? player.getEyePosition().add(player.getLookAngle().scale(24)) : hit.getLocation();
        for (int wave = 0; wave < 6; wave++) {
            UltimateFx.later(player, 1 + wave * 10, p -> rainArrows(p, center));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.PLAYERS, 1.5f, 0.6f);
        KitUtil.actionBar(player, "Arrow Rain!");
        return true;
    }

    private static void rainArrows(ServerPlayer player, Vec3 center) {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        String owner = KitUtil.uuidNbt(player.getUUID());
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(random.nextDouble()) * 5;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            KitUtil.command(level, "summon minecraft:arrow %.3f %.3f %.3f {Motion:[0.0,-2.5,0.0],Owner:%s,pickup:0b}",
                    x, center.y + 14, z, owner);
        }
        level.playSound(null, BlockPos.containing(center), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f);
    }
}
