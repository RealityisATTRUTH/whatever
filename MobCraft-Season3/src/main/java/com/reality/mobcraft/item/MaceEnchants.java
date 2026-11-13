package com.reality.mobcraft.item;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The four MobCraft mace enchants. Each one goes off when a mace smash attack lands (you hit while falling), and
 * a mace can only hold one of them at a time. All four go up to III, and Duskvein can push them past it.
 *
 *   Gravity Well  pulls nearby enemies in toward where you landed
 *   Aftershock    sends a shockwave along the ground that hurts enemies around the hit; bigger fall, bigger hit
 *   Skybreaker    an ability: right-click to launch into the sky, stand on it, shatter it and dive face-first
 *                 onto your enemy for a real mace smash (see Skybreaker.java)
 *   Echo Slam     blue sculk rings close in around the target with warden and sculk sounds, the target is
 *                 wrapped in Darkness, then a second hit lands a moment later
 *
 * Where to get them: Ancient City chests, ominous trial vaults, and the enchanting table when Duskvein is in the
 * lapis slot (it can awaken one on a mace). They never come from villagers or the normal enchanting table.
 */
public final class MaceEnchants {

    public static final ResourceKey<Enchantment> GRAVITY_WELL = key("gravity_well");
    public static final ResourceKey<Enchantment> AFTERSHOCK = key("aftershock");
    public static final ResourceKey<Enchantment> SKYBREAKER = key("skybreaker");
    public static final ResourceKey<Enchantment> ECHO_SLAM = key("echo_slam");
    private static final List<ResourceKey<Enchantment>> ALL = List.of(GRAVITY_WELL, AFTERSHOCK, SKYBREAKER, ECHO_SLAM);

    /** Chance that Duskvein awakens a custom mace enchant when enchanting a mace. */
    private static final float AWAKEN_CHANCE = 0.3f;
    private static final int ECHO_DELAY = 12;   // ticks, just past the target's hurt cooldown
    private static final DustParticleOptions SCULK_BLUE = new DustParticleOptions(0x29DFEB, 1.4f);

    private MaceEnchants() {}

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, MobCraft.id(name));
    }

    // ---- loot ----

    private static String booksPool(double chance) {
        StringBuilder entries = new StringBuilder();
        for (ResourceKey<Enchantment> k : ALL) {
            if (entries.length() > 0) entries.append(',');
            entries.append("{\"type\":\"minecraft:item\",\"name\":\"minecraft:book\",\"modifier\":{\"type\":\"minecraft:set_enchantments\","
                    + "\"enchantments\":{\"" + k.identifier() + "\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":3}}}}");
        }
        return "{\"rolls\":1,\"condition\":{\"type\":\"minecraft:random_chance\",\"chance\":" + chance + "},\"entries\":[" + entries + "]}";
    }

    private static final String WIND_BURST_IV = "{\"rolls\":1,\"condition\":{\"type\":\"minecraft:random_chance\",\"chance\":0.04},"
            + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:book\",\"modifier\":{\"type\":\"minecraft:set_enchantments\","
            + "\"enchantments\":{\"minecraft:wind_burst\":4}}}]}";

    private static LootPool pool(String json, HolderLookup.Provider registries) {
        return LootPool.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), JsonParser.parseString(json))
                .getOrThrow(msg -> new IllegalStateException("MobCraft mace loot: " + msg));
    }

    // ---- smash effects ----

    private record Pending(ServerLevel level, Runnable action, long at) {}
    private static final List<Pending> PENDING = new ArrayList<>();

    public static void register() {
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!source.isBuiltin()) return;
            if (key.equals(BuiltInLootTables.ANCIENT_CITY)) table.pool(pool(booksPool(0.10), registries));
            if (key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS)) {
                table.pool(pool(booksPool(0.08), registries));
                table.pool(pool(WIND_BURST_IV, registries));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) return;
            List<Pending> due = new ArrayList<>();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending p = it.next();
                if (p.level().getGameTime() >= p.at()) { due.add(p); it.remove(); }
            }
            due.forEach(p -> p.action().run());
        });
    }

    static void later(ServerLevel level, int ticks, Runnable action) {
        PENDING.add(new Pending(level, action, level.getGameTime() + ticks));
    }

    static int level(ItemStack mace, ResourceKey<Enchantment> key, ServerLevel world) {
        Holder<Enchantment> h = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
        return h == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(h, mace);
    }

    /** The mace's smash bonus damage for a fall, the same steps the game uses. */
    static float smashBonus(double fall) {
        if (fall <= 3) return (float) (4 * fall);
        if (fall <= 8) return (float) (12 + 2 * (fall - 3));
        return (float) (22 + (fall - 8));
    }

    static boolean affects(LivingEntity e, LivingEntity attacker, LivingEntity target) {
        if (e == attacker || e == target || e.isSpectator() || !e.isAlive()) return false;
        if (e instanceof ArmorStand stand && stand.isMarker()) return false;
        if (e.isAlliedTo(attacker)) return false;
        if (e instanceof OwnableEntity own && own.getOwner() == attacker) return false;
        String owner = e.getAttached(MobAttachments.OWNER);
        return owner == null || !owner.equals(attacker.getStringUUID());
    }

    /** Called when a mace smash lands, before the game resets the fall. */
    public static void onSmash(ItemStack mace, LivingEntity target, LivingEntity attacker) {
        if (!(attacker.level() instanceof ServerLevel level)) return;
        double fall = attacker.fallDistance;
        Vec3 impact = target.position();

        int well = level(mace, GRAVITY_WELL, level);
        if (well > 0) later(level, 1, () -> gravityWell(level, impact, attacker, target, well));
        int shock = level(mace, AFTERSHOCK, level);
        // Right away, while the mobs around are still standing: the mace's own knockback lifts them a moment later.
        if (shock > 0) aftershock(level, impact, attacker, target, shock, fall);
        int echo = level(mace, ECHO_SLAM, level);
        if (echo > 0) echoSlam(level, attacker, target, echo, fall);
    }

    private static void gravityWell(ServerLevel level, Vec3 impact, LivingEntity attacker, LivingEntity target, int lv) {
        double radius = 4 + lv;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(radius),
                e -> affects(e, attacker, target) && e.position().distanceTo(impact) <= radius)) {
            Vec3 pull = impact.subtract(e.position()).multiply(1, 0, 1);
            if (pull.lengthSqr() < 0.01) continue;
            // Scaled to the distance so they land in a ring around the impact instead of flying past it.
            double strength = Math.min(0.45 + 0.15 * lv, Math.max(0, pull.length() - 1.2) * 0.17);
            if (strength < 0.05) continue;
            e.setDeltaMovement(pull.normalize().scale(strength).add(0, 0.25, 0));
            KitUtil.syncMotion(e);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, impact.x, impact.y + 0.5, impact.z, 60, radius / 2, 0.4, radius / 2, 0.05);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    private static void aftershock(ServerLevel level, Vec3 impact, LivingEntity attacker, LivingEntity target, int lv, double fall) {
        double radius = 2 + lv;
        float damage = (float) ((1 + lv) * Math.min(fall, 10) / 5 + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(radius, 1.5, radius),
                e -> affects(e, attacker, target) && e.onGround() && e.position().distanceTo(impact) <= radius)) {
            e.hurtServer(level, level.damageSources().mobAttack(attacker), damage);
            Vec3 push = e.position().subtract(impact).multiply(1, 0, 1);
            if (push.lengthSqr() > 0.01) e.setDeltaMovement(push.normalize().scale(0.4).add(0, 0.3, 0));
            KitUtil.syncMotion(e);
        }
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32;
            level.sendParticles(ParticleTypes.DUST_PLUME, impact.x + Math.cos(a) * radius * 0.8, impact.y + 0.1,
                    impact.z + Math.sin(a) * radius * 0.8, 2, 0.1, 0.05, 0.1, 0.02);
        }
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    /** Copies of the ground blocks pop up and settle back in a wave going outward. Nothing in the world changes. */
    static void risingBlocks(ServerLevel level, net.minecraft.core.BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius || d < 0.8) continue;
                net.minecraft.core.BlockPos pos = null;
                for (int dy = 1; dy >= -2; dy--) {
                    net.minecraft.core.BlockPos p = center.offset(dx, dy, dz);
                    if (!level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                            && level.getBlockState(p).isSolidRender()) { pos = p; break; }
                }
                if (pos == null) continue;
                net.minecraft.core.BlockPos fp = pos;
                int delay = (int) (d * 2);
                double rise = 0.45 + level.getRandom().nextDouble() * 0.5 + 0.5 * (1 - d / (radius + 1));
                later(level, delay, () -> {
                    net.minecraft.world.entity.Display.BlockDisplay show = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY
                            .create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                    if (show == null) return;
                    show.setBlockState(level.getBlockState(fp));
                    show.setPos(fp.getX(), fp.getY(), fp.getZ());
                    show.setTransformation(lift(0));
                    level.addFreshEntity(show);
                    later(level, 2, () -> { show.setTransformationInterpolationDelay(0); show.setTransformationInterpolationDuration(3); show.setTransformation(lift((float) rise)); });
                    later(level, 16, () -> { show.setTransformationInterpolationDelay(0); show.setTransformationInterpolationDuration(6); show.setTransformation(lift(0)); });
                    later(level, 23, show::discard);
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(fp)),
                            fp.getX() + 0.5, fp.getY() + 1, fp.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.1);
                });
            }
        }
    }

    static com.mojang.math.Transformation lift(float y) {
        return new com.mojang.math.Transformation(new org.joml.Vector3f(0.01f, y + 0.01f, 0.01f), null,
                new org.joml.Vector3f(0.98f, 0.98f, 0.98f), null);
    }

    private static void echoSlam(ServerLevel level, LivingEntity attacker, LivingEntity target, int lv, double fall) {
        float echo = Math.min(20f, (float) ((0.2 + 0.1 * lv) * (6 + smashBonus(fall))));
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60 + 20 * lv, 0, false, false, true), attacker);
        level.playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 1.0f);
        // Blue rings close in on the spot where the mace hit, then the echo lands. They stay at the impact instead of
        // following the target, so a target knocked away doesn't smear them into a trail.
        double cx = target.getX(), cy = target.getY(), cz = target.getZ(), h = target.getBbHeight();
        for (int step = 0; step < 4; step++) {
            int s = step;
            later(level, step * 3, () -> {
                // Each wave is a ring of blue soul flames, which stay where they're put (and show from any angle), plus
                // sculk charge bubbles that show from the side.
                double r = 1.9 - s * 0.45;
                for (int ring = 0; ring < 2; ring++) {
                    double y = cy + 0.3 + ring * h * 0.55;
                    for (int i = 0; i < 28; i++) {
                        double a = Math.PI * 2 * i / 28 + s * 0.3;
                        double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, x, y, z, 0, 0, 0, 0, 0);
                        if (i % 2 == 0) level.sendParticles(new net.minecraft.core.particles.SculkChargeParticleOptions(0f), true, false,
                                x, y, z, 1, 0, 0, 0, 0);
                    }
                }
                level.playSound(null, cx, cy, cz, SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0f, 0.8f + s * 0.15f);
            });
        }
        later(level, ECHO_DELAY, () -> {
            if (!target.isAlive()) return;
            target.setInvulnerableTime(0);
            target.hurtServer(level, level.damageSources().mobAttack(attacker), echo);
            level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 16, 0.4, 0.5, 0.4, 0.03);
            level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 1.6f);
            level.playSound(null, target.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0f, 1.0f);
        });
    }

    // ---- enchanting table with Duskvein ----

    /** Duskvein can awaken one custom mace enchant on a mace it enchants. Returns true when it did. */
    public static boolean awaken(ItemStack result, Player player, RandomSource random) {
        if (!result.is(ItemTags.MACE_ENCHANTABLE) || random.nextFloat() >= AWAKEN_CHANCE) return false;
        HolderLookup.RegistryLookup<Enchantment> reg = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> options = new ArrayList<>();
        for (ResourceKey<Enchantment> k : ALL) {
            reg.get(k).ifPresent(h -> {
                if (EnchantmentHelper.isEnchantmentCompatible(EnchantmentHelper.getEnchantmentsForCrafting(result).keySet(), h)) options.add(h);
            });
        }
        if (options.isEmpty()) return false;
        Holder<Enchantment> pick = options.get(random.nextInt(options.size()));
        int lv = 1 + random.nextInt(2);
        EnchantmentHelper.updateEnchantments(result, e -> e.set(pick, lv));
        player.sendSystemMessage(Component.literal("The Duskvein awakened ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Enchantment.getFullname(pick, lv)).append(Component.literal(" on your mace.").withStyle(ChatFormatting.DARK_AQUA)));
        return true;
    }
}
