package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mixin.AbstractArrowAccessor;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The MobCraft gear enchants (Unseen Hand has its own class, and the mace ones are MaceEnchants).
 *
 * Swords:      Phantom Edge, Executioner, Bloodmoon, Duelist
 * Axes:        Earthsplitter, Ricochet, Shieldbreaker
 * Bows:        Homing, Split Shot, Rift Arrow
 * Crossbows:   Grapple, Chain Lightning, Cluster Rocket
 * Tridents:    Maelstrom, Thunderlord
 * Helmets:     Duskvision, Danger Sense (both drawn on the client: client/GearEnchantsClient)
 * Chestplates: Thorn Aura (and Unseen Hand)
 * Leggings:    Dash, Lurk
 * Boots:       Double Jump (the client sends the jump), Magnetic, Lava Walker
 *
 * They come from Ancient City chests, ominous vaults, and the enchanting table only when Duskvein is in the slot.
 */
public final class GearEnchants {

    private GearEnchants() {}

    public static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, MobCraft.id(name));
    }

    public static final ResourceKey<Enchantment> PHANTOM_EDGE = key("phantom_edge"), EXECUTIONER = key("executioner"),
            BLOODMOON = key("bloodmoon"), DUELIST = key("duelist"), EARTHSPLITTER = key("earthsplitter"), RICOCHET = key("ricochet"),
            SHIELDBREAKER = key("shieldbreaker"), HOMING = key("homing"), SPLIT_SHOT = key("split_shot"), RIFT_ARROW = key("rift_arrow"),
            GRAPPLE = key("grapple"), CHAIN_LIGHTNING = key("chain_lightning"), CLUSTER_ROCKET = key("cluster_rocket"),
            MAELSTROM = key("maelstrom"), THUNDERLORD = key("thunderlord"), DUSKVISION = key("duskvision"),
            DANGER_SENSE = key("danger_sense"), THORN_AURA = key("thorn_aura"), DASH = key("dash"), LURK = key("lurk"),
            DOUBLE_JUMP = key("double_jump"), MAGNETIC = key("magnetic"), LAVA_WALKER = key("lava_walker");

    public static final List<ResourceKey<Enchantment>> ALL = List.of(PHANTOM_EDGE, EXECUTIONER, BLOODMOON, DUELIST, EARTHSPLITTER,
            RICOCHET, SHIELDBREAKER, HOMING, SPLIT_SHOT, RIFT_ARROW, GRAPPLE, CHAIN_LIGHTNING, CLUSTER_ROCKET, MAELSTROM, THUNDERLORD,
            DUSKVISION, DANGER_SENSE, UnseenHand.KEY, THORN_AURA, DASH, LURK, DOUBLE_JUMP, MAGNETIC, LAVA_WALKER);

    private static final DustParticleOptions PURPLE = new DustParticleOptions(0xA05CFF, 1.0f);
    private static final DustParticleOptions BLUE = new DustParticleOptions(0x7FD8FF, 0.8f);

    static int lv(ItemStack stack, ResourceKey<Enchantment> k, ServerLevel level) {
        return stack == null || stack.isEmpty() ? 0 : MaceEnchants.level(stack, k, level);
    }

    static int lv(LivingEntity e, EquipmentSlot slot, ResourceKey<Enchantment> k) {
        return e.level() instanceof ServerLevel level ? lv(e.getItemBySlot(slot), k, level) : 0;
    }

    /** Our own extra hits shouldn't set off more enchants. */
    private static boolean busy;
    private static void quietly(Runnable r) { boolean was = busy; busy = true; try { r.run(); } finally { busy = was; } }

    private static boolean enemy(LivingEntity e, LivingEntity of) {
        return e != of && e.isAlive() && !e.isSpectator() && MaceEnchants.affects(e, of, null);
    }

    // ================================================================ where they come from

    private static String booksPool(double chance) {
        return "{\"rolls\":1,\"condition\":{\"type\":\"minecraft:random_chance\",\"chance\":" + chance + "},"
                + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:book\","
                + "\"modifier\":{\"type\":\"minecraft:enchant_randomly\",\"options\":\"#mobcraft:gear\"}}]}";
    }

    private static net.minecraft.world.level.storage.loot.LootPool pool(String json, net.minecraft.core.HolderLookup.Provider registries) {
        return net.minecraft.world.level.storage.loot.LootPool.CODEC.parse(
                net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries),
                com.google.gson.JsonParser.parseString(json)).getOrThrow(msg -> new IllegalStateException("MobCraft gear loot: " + msg));
    }

    /** Duskvein at the enchanting table can awaken one of these on what it enchants (maces have their own). */
    public static void awaken(ItemStack result, Player player, net.minecraft.util.RandomSource random) {
        if (result.is(ItemTags.MACE_ENCHANTABLE) || random.nextFloat() >= 0.3f) return;
        var reg = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<net.minecraft.core.Holder<Enchantment>> options = new ArrayList<>();
        var current = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(result).keySet();
        for (ResourceKey<Enchantment> k : ALL) {
            reg.get(k).ifPresent(h -> {
                if (h.value().isSupportedItem(result) && net.minecraft.world.item.enchantment.EnchantmentHelper.isEnchantmentCompatible(current, h)) options.add(h);
            });
        }
        if (options.isEmpty()) return;
        var pick = options.get(random.nextInt(options.size()));
        int lvl = 1 + random.nextInt(Math.min(2, pick.value().getMaxLevel()));
        net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(result, e -> e.set(pick, lvl));
        player.sendSystemMessage(Component.literal("The Duskvein awakened ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Enchantment.getFullname(pick, lvl)).append(Component.literal(".").withStyle(ChatFormatting.DARK_AQUA)));
    }

    public static void register() {
        net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!source.isBuiltin()) return;
            if (key.equals(net.minecraft.world.level.storage.loot.BuiltInLootTables.ANCIENT_CITY)) table.pool(pool(booksPool(0.15), registries));
            if (key.equals(net.minecraft.world.level.storage.loot.BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS)) table.pool(pool(booksPool(0.10), registries));
        });
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
                com.reality.mobcraft.net.BootsJumpPayload.TYPE, com.reality.mobcraft.net.BootsJumpPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(com.reality.mobcraft.net.BootsJumpPayload.TYPE,
                (payload, context) -> context.server().execute(() -> doubleJump(context.player())));
        ServerLivingEntityEvents.AFTER_DAMAGE.register(GearEnchants::afterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(GearEnchants::afterDeath);
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (level instanceof ServerLevel server && hand == InteractionHand.MAIN_HAND) earthsplitter(server, player);
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND
                    && sp.isShiftKeyDown() && sp.getMainHandItem().is(ItemTags.AXES) && lv(sp.getMainHandItem(), RICOCHET, server) > 0) {
                return ricochet(server, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return InteractionResult.PASS;
        });
        ServerEntityEvents.ENTITY_LOAD.register(GearEnchants::onSpawn);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) playerTick(p);
            projectileTick();
            axeTick();
            vortexTick();
            lavaTick(server.getTickCount());
        });
    }

    // ================================================================ damage hooks

    /** Called from the damage hook before damage lands: Executioner and Duelist. */
    public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
        if (busy || !(source.getEntity() instanceof Player p) || source.getDirectEntity() != p || !(p.level() instanceof ServerLevel level)) return amount;
        ItemStack sword = p.getMainHandItem();
        int ex = lv(sword, EXECUTIONER, level);
        if (ex > 0 && victim.getHealth() < victim.getMaxHealth() * 0.25f) {
            amount *= 1 + 0.15f * ex;
            level.playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 0.55f);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 20, 0.3, 0.3, 0.3, 0.3);
        }
        int du = lv(sword, DUELIST, level);
        if (du > 0) {
            boolean alone = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8),
                    e -> e != victim && e != p && e.isAlive() && (e instanceof Enemy || e instanceof Player) && !e.isSpectator()).isEmpty();
            if (alone) amount *= 1 + 0.10f * du;
        }
        return amount;
    }

    private static final Map<UUID, Integer> SWINGS = new HashMap<>();
    private record Hits(int target, int count, long last) {}
    private static final Map<UUID, Hits> THUNDER = new HashMap<>();

    private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
        if (busy || !(victim.level() instanceof ServerLevel level)) return;
        Entity direct = source.getDirectEntity();
        // Chain Lightning: a crossbow bolt arcs on to the mobs around what it hit.
        if (direct instanceof AbstractArrow arrow && !(direct instanceof ThrownTrident) && source.getEntity() instanceof LivingEntity shooter) {
            int cl = lv(arrow.getWeaponItem(), CHAIN_LIGHTNING, level);
            if (cl > 0) chain(level, victim, shooter, cl);
        }
        if (!(source.getEntity() instanceof ServerPlayer p)) return;
        ItemStack held = p.getMainHandItem();
        // Thunderlord: three trident hits on the same target, close together, call down lightning.
        ItemStack trident = direct instanceof ThrownTrident tt ? tt.getWeaponItem() : (direct == p ? held : ItemStack.EMPTY);
        if (trident != null && trident.is(Items.TRIDENT) && lv(trident, THUNDERLORD, level) > 0) {
            long now = level.getGameTime();
            Hits h = THUNDER.get(p.getUUID());
            int count = h != null && h.target() == victim.getId() && now - h.last() < 100 ? h.count() + 1 : 1;
            if (count >= 3) {
                count = 0;
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
                if (bolt != null) { bolt.snapTo(victim.getX(), victim.getY(), victim.getZ()); bolt.setCause(p); level.addFreshEntity(bolt); }
            }
            THUNDER.put(p.getUUID(), new Hits(victim.getId(), count, now));
        }
        if (direct != p) return;
        // Shieldbreaker: an axe that knocks a shield down shatters it.
        // The axe has already knocked the shield down by now, so find the shield in either hand.
        if (blocked && held.is(ItemTags.AXES) && victim instanceof Player vp) {
            int sb = lv(held, SHIELDBREAKER, level);
            InteractionHand hand = null;
            for (InteractionHand h : InteractionHand.values())
                if (vp.getItemInHand(h).has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) { hand = h; break; }
            if (sb > 0 && hand != null) {
                ItemStack shield = vp.getItemInHand(hand);
                vp.getCooldowns().addCooldown(shield, 100 + 40 * sb);
                vp.stopUsingItem();
                shield.hurtAndBreak(15 * sb, vp, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, shield.getItem()), vp.getX(), vp.getY() + 1.1, vp.getZ(), 30, 0.3, 0.3, 0.3, 0.25);
                level.playSound(null, vp.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
            }
        }
        // Phantom Edge: every third sword hit, a ghost of the sword slashes again.
        int pe = lv(held, PHANTOM_EDGE, level);
        if (pe > 0 && !blocked) {
            int n = SWINGS.merge(p.getUUID(), 1, Integer::sum);
            if (n >= 3) {
                SWINGS.put(p.getUUID(), 0);
                phantom(level, p, victim, held, taken * (0.2f + 0.1f * pe));
            }
        }
    }

    private static void phantom(ServerLevel level, ServerPlayer p, LivingEntity target, ItemStack sword, float damage) {
        Display.ItemDisplay ghost = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (ghost != null) {
            ItemStack shown = sword.copy();
            ghost.setItemStack(shown);
            ghost.setItemTransform(ItemDisplayContext.FIXED);
            ghost.setGlowingTag(true);
            ghost.setGlowColorOverride(0xB98CFF);
            Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
            ghost.setPos(at.x, at.y, at.z);
            ghost.setYRot(p.getYRot());
            ghost.setTransformation(slash(-70));
            level.addFreshEntity(ghost);
            MaceEnchants.later(level, 6, () -> { ghost.setTransformationInterpolationDelay(0); ghost.setTransformationInterpolationDuration(5); ghost.setTransformation(slash(70)); });
            MaceEnchants.later(level, 14, ghost::discard);
        }
        // Lands just after the real hit's cooldown runs out, as the ghost finishes its swing.
        MaceEnchants.later(level, 11, () -> {
            if (!target.isAlive()) return;
            quietly(() -> {
                target.setInvulnerableTime(0);
                target.hurtServer(level, level.damageSources().mobAttack(p), damage);
            });
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(PURPLE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.4, 0.4, 0.4, 0);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.6f);
        });
    }

    private static com.mojang.math.Transformation slash(float deg) {
        return new com.mojang.math.Transformation(new org.joml.Vector3f(0, 0, 0),
                new org.joml.Quaternionf().rotateZ((float) Math.toRadians(deg)), new org.joml.Vector3f(1.3f, 1.3f, 1.3f), null);
    }

    private static void chain(ServerLevel level, LivingEntity first, LivingEntity shooter, int lvl) {
        Set<LivingEntity> hit = new HashSet<>();
        hit.add(first);
        LivingEntity from = first;
        for (int jumps = 0; jumps < 1 + lvl; jumps++) {
            LivingEntity cur = from;
            LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, cur.getBoundingBox().inflate(6),
                            e -> !hit.contains(e) && enemy(e, shooter)).stream()
                    .min((a, b) -> Double.compare(a.distanceToSqr(cur), b.distanceToSqr(cur))).orElse(null);
            if (next == null) break;
            hit.add(next);
            Vec3 a = cur.position().add(0, cur.getBbHeight() / 2, 0), b = next.position().add(0, next.getBbHeight() / 2, 0);
            for (int i = 0; i <= 12; i++) {
                Vec3 q = a.lerp(b, i / 12.0).add(level.getRandom().nextGaussian() * 0.12, level.getRandom().nextGaussian() * 0.12, level.getRandom().nextGaussian() * 0.12);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 2, 0.02, 0.02, 0.02, 0);
            }
            LivingEntity target = next;
            quietly(() -> {
                target.setInvulnerableTime(0);
                target.hurtServer(level, level.damageSources().indirectMagic(shooter, shooter), 3f + 1.5f * lvl);
            });
            level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4f, 1.8f);
            from = next;
        }
    }

    // ---- Bloodmoon: night kills stack speed and strength until you die ----
    private static final Map<UUID, Integer> BLOOD = new HashMap<>();

    private static void afterDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayer dead) BLOOD.remove(dead.getUUID());
        if (!(source.getEntity() instanceof ServerPlayer p) || source.getDirectEntity() != p || !(p.level() instanceof ServerLevel level)) return;
        int bm = lv(p.getMainHandItem(), BLOODMOON, level);
        if (bm <= 0 || !level.isDarkOutside()) return;
        int stacks = Math.min(2 * bm, BLOOD.getOrDefault(p.getUUID(), 0) + 1);
        BLOOD.put(p.getUUID(), stacks);
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, (stacks - 1) / 2, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, (stacks - 1) / 2, false, false, true));
        p.sendOverlayMessage(Component.literal("Bloodmoon x" + stacks).withStyle(ChatFormatting.DARK_RED));
        level.playSound(null, p.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.4f, 1.6f);
        level.sendParticles(new DustParticleOptions(0xB0101E, 1.2f), p.getX(), p.getY() + 1, p.getZ(), 16, 0.4, 0.6, 0.4, 0);
    }

    // ================================================================ axes

    /** Earthsplitter: a critical hit sends a crack along the ground in front of you that throws mobs up. */
    private static void earthsplitter(ServerLevel level, Player p) {
        int es = lv(p.getMainHandItem(), EARTHSPLITTER, level);
        if (es <= 0 || !p.getMainHandItem().is(ItemTags.AXES)) return;
        boolean crit = p.getAttackStrengthScale(0.5f) > 0.9f && p.fallDistance > 0 && !p.onGround() && !p.onClimbable()
                && !p.isInWater() && !p.isPassenger() && !p.isSprinting();
        if (!crit) return;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) return;
        look = look.normalize();
        int length = 5 + 3 * es;
        Set<LivingEntity> thrown = new HashSet<>();
        for (int i = 1; i <= length; i++) {
            Vec3 at = p.position().add(look.scale(i));
            BlockPos ground = BlockPos.containing(at.x, p.getY() - 0.5, at.z);
            int delay = i;
            MaceEnchants.later(level, delay, () -> {
                rise(level, ground);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(ground.above()).inflate(0.8, 1, 0.8), e -> enemy(e, p))) {
                    if (!thrown.add(e)) continue;
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6 + 0.2 * es, 0));
                    KitUtil.syncMotion(e);
                    quietly(() -> e.hurtServer(level, level.damageSources().mobAttack(p), 3f + es));
                }
            });
        }
        level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    private static void rise(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !level.getBlockState(pos.above()).isAir()) return;
        Display.BlockDisplay show = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (show == null) return;
        show.setBlockState(state);
        show.setPos(pos.getX(), pos.getY(), pos.getZ());
        show.setTransformation(MaceEnchants.lift(0));
        level.addFreshEntity(show);
        MaceEnchants.later(level, 1, () -> { show.setTransformationInterpolationDelay(0); show.setTransformationInterpolationDuration(2); show.setTransformation(MaceEnchants.lift(0.7f)); });
        MaceEnchants.later(level, 12, () -> { show.setTransformationInterpolationDelay(0); show.setTransformationInterpolationDuration(5); show.setTransformation(MaceEnchants.lift(0)); });
        MaceEnchants.later(level, 18, show::discard);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0.15);
    }

    /** Ricochet: a spinning copy of the axe bounces between up to three enemies and flies back. */
    private static final class Thrown {
        final ServerPlayer by; final Display.ItemDisplay show; final Set<LivingEntity> hit = new HashSet<>();
        Vec3 pos; LivingEntity target; Vec3 aim; boolean back; int age; final float damage;
        Thrown(ServerPlayer by, Display.ItemDisplay show, Vec3 pos, float damage) { this.by = by; this.show = show; this.pos = pos; this.damage = damage; }
    }
    private static final List<Thrown> AXES = new ArrayList<>();
    private static final Map<UUID, Long> AXE_READY = new HashMap<>();

    private static boolean ricochet(ServerLevel level, ServerPlayer p) {
        long now = level.getGameTime();
        if (now < AXE_READY.getOrDefault(p.getUUID(), 0L)) return false;
        AXE_READY.put(p.getUUID(), now + 60);
        Display.ItemDisplay show = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (show == null) return false;
        show.setItemStack(p.getMainHandItem().copy());
        show.setItemTransform(ItemDisplayContext.FIXED);
        Vec3 start = p.getEyePosition().add(p.getLookAngle().scale(0.8));
        show.setPos(start.x, start.y, start.z);
        show.setPosRotInterpolationDuration(1);
        level.addFreshEntity(show);
        float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8f;
        Thrown t = new Thrown(p, show, start, dmg);
        t.target = Shadow.lookTarget(p, 16);
        t.aim = p.getEyePosition().add(p.getLookAngle().scale(12));
        AXES.add(t);
        level.playSound(null, p.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    private static void axeTick() {
        for (Iterator<Thrown> it = AXES.iterator(); it.hasNext(); ) {
            Thrown t = it.next();
            if (!(t.show.level() instanceof ServerLevel level) || t.by.isRemoved() || ++t.age > 120) { t.show.discard(); it.remove(); continue; }
            Vec3 goal = t.back ? t.by.getEyePosition().add(0, -0.4, 0)
                    : t.target != null && t.target.isAlive() ? t.target.position().add(0, t.target.getBbHeight() * 0.6, 0) : t.aim;
            Vec3 d = goal.subtract(t.pos);
            double step = 1.3;
            if (d.length() <= step) {
                t.pos = goal;
                if (t.back) { t.show.discard(); it.remove(); level.playSound(null, t.by.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1f, 1.2f); continue; }
                if (t.target != null && t.target.isAlive()) {
                    LivingEntity victim = t.target;
                    t.hit.add(victim);
                    quietly(() -> { victim.setInvulnerableTime(0); victim.hurtServer(level, level.damageSources().mobAttack(t.by), t.damage); });
                    level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 1.1f);
                    level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                }
                LivingEntity next = t.hit.size() >= 3 ? null : level.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(t.pos)).inflate(8),
                                e -> !t.hit.contains(e) && enemy(e, t.by)).stream()
                        .min((a, b) -> Double.compare(a.distanceToSqr(t.pos), b.distanceToSqr(t.pos))).orElse(null);
                if (next != null) t.target = next; else t.back = true;
            } else {
                t.pos = t.pos.add(d.normalize().scale(step));
            }
            t.show.setPos(t.pos.x, t.pos.y, t.pos.z);
            t.show.setTransformationInterpolationDelay(0);
            t.show.setTransformationInterpolationDuration(1);
            t.show.setTransformation(new com.mojang.math.Transformation(null,
                    new org.joml.Quaternionf().rotateY((float) Math.toRadians(t.by.getYRot())).rotateX(t.age * 0.9f), null, null));
        }
    }

    // ================================================================ projectiles

    private enum Kind { HOMING, SPLIT, RIFT, GRAPPLE, CLUSTER, MAELSTROM }
    private static final class Flying {
        final Entity e; final Kind kind; final int level; final long born; boolean done; int pull;
        Flying(Entity e, Kind kind, int level, long born) { this.e = e; this.kind = kind; this.level = level; this.born = born; }
    }
    private static final List<Flying> FLYING = new ArrayList<>();
    private static final Map<UUID, Long> RIFT_READY = new HashMap<>();

    private static void onSpawn(Entity entity, ServerLevel level) {
        if (entity.tickCount > 0) return;
        long now = level.getGameTime();
        if (entity instanceof FireworkRocketEntity rocket && rocket.getOwner() instanceof LivingEntity owner) {
            for (ItemStack held : new ItemStack[]{owner.getMainHandItem(), owner.getOffhandItem()}) {
                if (held.is(Items.CROSSBOW) && lv(held, CLUSTER_ROCKET, level) > 0) { FLYING.add(new Flying(rocket, Kind.CLUSTER, 1, now)); break; }
            }
            return;
        }
        if (!(entity instanceof AbstractArrow arrow) || ((AbstractArrowAccessor) arrow).mobcraft$inGround()) return;
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null || weapon.isEmpty()) return;
        if (arrow instanceof ThrownTrident) {
            int m = lv(weapon, MAELSTROM, level);
            if (m > 0) FLYING.add(new Flying(arrow, Kind.MAELSTROM, m, now));
            return;
        }
        int h = lv(weapon, HOMING, level), s = lv(weapon, SPLIT_SHOT, level), r = lv(weapon, RIFT_ARROW, level), g = lv(weapon, GRAPPLE, level);
        if (h > 0) FLYING.add(new Flying(arrow, Kind.HOMING, h, now));
        if (s > 0 && !SPLITTING) FLYING.add(new Flying(arrow, Kind.SPLIT, s, now));
        if (g > 0) { arrow.pickup = AbstractArrow.Pickup.DISALLOWED; FLYING.add(new Flying(arrow, Kind.GRAPPLE, g, now)); }
        if (r > 0 && arrow.getOwner() instanceof ServerPlayer owner && now >= RIFT_READY.getOrDefault(owner.getUUID(), 0L)) {
            RIFT_READY.put(owner.getUUID(), now + 160);
            FLYING.add(new Flying(arrow, Kind.RIFT, r, now));
        }
    }

    private static boolean SPLITTING;

    private static void projectileTick() {
        List<Flying> added = new ArrayList<>();
        for (Iterator<Flying> it = FLYING.iterator(); it.hasNext(); ) {
            Flying f = it.next();
            if (!(f.e.level() instanceof ServerLevel level)) { it.remove(); continue; }
            long age = level.getGameTime() - f.born;
            boolean landed = f.e.isRemoved() || (f.e instanceof AbstractArrow a && ((AbstractArrowAccessor) a).mobcraft$inGround());
            if (age > 400) { it.remove(); continue; }
            switch (f.kind) {
                case HOMING -> {
                    if (landed) { it.remove(); continue; }
                    Entity owner = f.e instanceof AbstractArrow a ? a.getOwner() : null;
                    Vec3 v = f.e.getDeltaMovement();
                    double speed = v.length();
                    if (speed < 0.2) continue;
                    Vec3 dir = v.normalize();
                    LivingEntity best = null; double bestScore = 0;
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, f.e.getBoundingBox().inflate(18),
                            e -> e != owner && e.isAlive() && !e.isSpectator() && (owner == null || !e.isAlliedTo(owner)))) {
                        Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(f.e.position());
                        double dot = to.normalize().dot(dir);
                        if (dot < 0.5) continue;
                        double score = dot / Math.max(1, to.length());
                        if (score > bestScore) { bestScore = score; best = e; }
                    }
                    if (best != null) {
                        Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0).subtract(f.e.position()).normalize();
                        f.e.setDeltaMovement(dir.scale(0.45).add(to.scale(0.55)).normalize().scale(speed));
                        KitUtil.syncMotion(f.e);
                    }
                }
                case SPLIT -> {
                    if (landed) { it.remove(); continue; }
                    if (age == 5 && f.e instanceof AbstractArrow a) {
                        it.remove();
                        int extra = 1 + f.level;
                        Vec3 v = a.getDeltaMovement();
                        for (int k = 0; k < extra; k++) {
                            double ang = Math.toRadians((k - (extra - 1) / 2.0) * 9 + (k >= extra / 2 ? 9 : 0) * 0);
                            double side = (k % 2 == 0 ? 1 : -1) * (k / 2 + 1) * 8;
                            Vec3 nv = rotateY(v, Math.toRadians(side));
                            AbstractArrow copy = (AbstractArrow) a.getType().create(level, EntitySpawnReason.TRIGGERED);
                            if (copy == null) continue;
                            copy.setOwner(a.getOwner());
                            copy.setPos(a.getX(), a.getY(), a.getZ());
                            copy.setDeltaMovement(nv);
                            copy.setBaseDamage(((AbstractArrowAccessor) a).mobcraft$baseDamage());
                            copy.pickup = AbstractArrow.Pickup.DISALLOWED;
                            copy.setCritArrow(a.isCritArrow());
                            SPLITTING = true;
                            try { level.addFreshEntity(copy); } finally { SPLITTING = false; }
                        }
                        level.playSound(null, a.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.7f, 1.6f);
                        level.sendParticles(ParticleTypes.CRIT, a.getX(), a.getY(), a.getZ(), 8, 0.1, 0.1, 0.1, 0.1);
                    }
                }
                case RIFT -> {
                    Vec3 at = f.e.position();
                    if (!landed) {
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0);
                        continue;
                    }
                    it.remove();
                    if (!(f.e instanceof AbstractArrow a) || !(a.getOwner() instanceof ServerPlayer owner)) continue;
                    level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.5, at.z, 60, 0.3, 0.6, 0.3, 0.6);
                    level.playSound(null, BlockPos.containing(at), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.8f);
                    MaceEnchants.later(level, 20, () -> {
                        if (owner.isRemoved() || !owner.isAlive() || owner.level() != level || owner.distanceToSqr(at) > 96 * 96) return;
                        Vec3 safe = at.add(0, 0.1, 0);
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, owner.getX(), owner.getY() + 1, owner.getZ(), 40, 0.3, 0.6, 0.3, 0.2);
                        owner.teleportTo(safe.x, safe.y, safe.z);
                        owner.fallDistance = 0;
                        level.playSound(null, owner.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.3f);
                    });
                }
                case GRAPPLE -> {
                    if (!(f.e instanceof AbstractArrow a) || !(a.getOwner() instanceof ServerPlayer owner) || owner.level() != level) { it.remove(); continue; }
                    Vec3 hook = a.position();
                    // the line from the hook back to you
                    Vec3 from = owner.position().add(0, 1.2, 0);
                    for (int i = 0; i <= 10; i++) {
                        Vec3 q = from.lerp(hook, i / 10.0);
                        level.sendParticles(new DustParticleOptions(0x9A9AA2, 0.6f), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                    }
                    if (a.isRemoved()) { it.remove(); continue; }
                    if (!((AbstractArrowAccessor) a).mobcraft$inGround()) continue;
                    if (f.pull == 0) level.playSound(null, BlockPos.containing(hook), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
                    Vec3 to = hook.subtract(owner.position());
                    if (++f.pull > 30 || to.length() < 2.0) { a.discard(); it.remove(); continue; }
                    owner.setDeltaMovement(to.normalize().scale(Math.min(1.3, 0.4 + to.length() * 0.12)).add(0, 0.08, 0));
                    owner.fallDistance = 0;
                    KitUtil.syncMotion(owner);
                }
                case CLUSTER -> {
                    if (!f.e.isRemoved()) continue;
                    it.remove();
                    Vec3 at = f.e.position();
                    Entity owner = ((FireworkRocketEntity) f.e).getOwner();
                    for (int k = 0; k < 5; k++) {
                        double ang = Math.PI * 2 * k / 5;
                        Vec3 q = at.add(Math.cos(ang) * 2.6, level.getRandom().nextDouble() - 0.3, Math.sin(ang) * 2.6);
                        MaceEnchants.later(level, 3 + k * 2, () -> {
                            level.sendParticles(ParticleTypes.FIREWORK, q.x, q.y, q.z, 25, 0.1, 0.1, 0.1, 0.12);
                            level.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                            level.playSound(null, BlockPos.containing(q), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 1.3f);
                            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(q)).inflate(1.6),
                                    e -> e != owner && e.isAlive())) {
                                quietly(() -> e.hurtServer(level, level.damageSources().fireworks((FireworkRocketEntity) f.e, owner), 4f));
                            }
                        });
                    }
                }
                case MAELSTROM -> {
                    if (!landed && !(f.e instanceof ThrownTrident t && t.isNoPhysics())) continue;
                    it.remove();
                    added.add(new Flying(f.e, Kind.MAELSTROM, f.level, level.getGameTime()));
                }
            }
        }
        for (Flying f : added) VORTEX.add(new Vortex((ServerLevel) f.e.level(), f.e.position(), f.level, f.born,
                f.e instanceof ThrownTrident t ? t.getOwner() : null));
    }

    private static Vec3 rotateY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    // ---- Maelstrom: a whirlpool where the trident lands ----
    private record Vortex(ServerLevel level, Vec3 at, int lvl, long born, Entity owner) {}
    private static final List<Vortex> VORTEX = new ArrayList<>();

    private static void vortexTick() {
        VORTEX.removeIf(v -> {
            long age = v.level().getGameTime() - v.born();
            if (age > 60) return true;
            double radius = 4 + 1.5 * v.lvl();
            for (int k = 0; k < 3; k++) {
                double a = age * 0.45 + k * 2.1;
                double r = radius * (1 - (age % 20) / 20.0 * 0.7);
                v.level().sendParticles(ParticleTypes.BUBBLE_POP, v.at().x + Math.cos(a) * r, v.at().y + 0.3 + k * 0.4, v.at().z + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0);
                v.level().sendParticles(ParticleTypes.SPLASH, v.at().x + Math.cos(a + 1) * r * 0.6, v.at().y + 0.2, v.at().z + Math.sin(a + 1) * r * 0.6, 3, 0.1, 0.05, 0.1, 0);
            }
            if (age % 20 == 0) v.level().playSound(null, BlockPos.containing(v.at()), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.PLAYERS, 1f, 0.8f);
            for (LivingEntity e : v.level().getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(v.at())).inflate(radius),
                    e -> e != v.owner() && e.isAlive() && !e.isSpectator())) {
                Vec3 to = v.at().subtract(e.position());
                Vec3 flat = new Vec3(to.x, 0, to.z);
                double d = flat.length();
                if (d > radius || d < 0.3) continue;
                Vec3 in = flat.normalize(), around = new Vec3(-in.z, 0, in.x);
                e.setDeltaMovement(in.scale(0.18).add(around.scale(0.22)).add(0, e.getDeltaMovement().y, 0));
                if (age % 2 == 0) KitUtil.syncMotion(e);
            }
            return false;
        });
    }

    // ================================================================ armor

    private static final Map<UUID, int[]> SNEAK = new HashMap<>();      // {was sneaking, tick of last press, dash ready, still ticks}
    private static final Map<ServerLevel, Map<BlockPos, Long>> COOLED = new HashMap<>();
    private static final Map<UUID, Boolean> AIR_JUMPED = new HashMap<>();

    private static void playerTick(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || p.isSpectator()) return;
        int tick = p.tickCount;
        // Thorn Aura: a ring of purple spikes circles you and cuts anyone who steps in.
        int ta = lv(p, EquipmentSlot.CHEST, THORN_AURA);
        if (ta > 0 && tick % 2 == 0) {
            double r = 1.6 + 0.4 * ta;
            for (int k = 0; k < 10; k++) {
                double a = tick * 0.12 + Math.PI * 2 * k / 10;
                level.sendParticles(PURPLE, p.getX() + Math.cos(a) * r, p.getY() + 0.5 + (k % 2) * 0.5, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (tick % 20 == 0) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 1, r), e -> enemy(e, p))) {
                    if (e.distanceTo(p) > r + 0.4) continue;
                    quietly(() -> e.hurtServer(level, level.damageSources().thorns(p), 1f + 0.5f * ta));
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 1), p);
                    level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 6, 0.2, 0.3, 0.2, 0.1);
                }
            }
        }
        // Dash: double-tap sneak. Lurk: stand still sneaking for three seconds to vanish.
        int dash = lv(p, EquipmentSlot.LEGS, DASH), lurk = lv(p, EquipmentSlot.LEGS, LURK);
        if (dash > 0 || lurk > 0) {
            int[] s = SNEAK.computeIfAbsent(p.getUUID(), k -> new int[]{0, -100, 0, 0});
            boolean sneaking = p.isShiftKeyDown();
            if (dash > 0 && sneaking && s[0] == 0) {
                if (tick - s[1] <= 7 && tick >= s[2]) {
                    Vec3 look = p.getLookAngle().multiply(1, 0, 1);
                    if (look.lengthSqr() > 1e-4) {
                        p.setDeltaMovement(look.normalize().scale(1.1 + 0.4 * dash).add(0, 0.18, 0));
                        KitUtil.syncMotion(p);
                        s[2] = tick + 40;
                        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
                        level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8f, 1.3f);
                    }
                    s[1] = -100;
                } else s[1] = tick;
            }
            s[0] = sneaking ? 1 : 0;
            if (lurk > 0) {
                boolean still = sneaking && p.getDeltaMovement().horizontalDistanceSqr() < 1e-4;
                s[3] = still ? s[3] + 1 : 0;
                if (s[3] >= 60) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 6, 0, false, false, false));
                if (s[3] == 60) level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.5, 0.3, 0.01);
            }
        }
        // Magnetic: items and XP fly to you from far away.
        int mg = lv(p, EquipmentSlot.FEET, MAGNETIC);
        if (mg > 0 && tick % 2 == 0) {
            double r = mg == 1 ? 12 : mg == 2 ? 24 : 40;
            Vec3 to = p.position().add(0, 0.5, 0);
            for (Entity e : level.getEntities((Entity) null, p.getBoundingBox().inflate(r), e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
                Vec3 d = to.subtract(e.position());
                double len = d.length();
                if (len > r || len < 0.6) continue;
                e.setDeltaMovement(d.normalize().scale(Math.min(1.2, 0.35 + len * 0.04)));
                if (tick % 4 == 0) KitUtil.syncMotion(e);
            }
        }
        // Lava Walker: lava under your feet cools into basalt for a few seconds.
        int lw = lv(p, EquipmentSlot.FEET, LAVA_WALKER);
        if (lw > 0 && p.onGround()) {
            int r = 1 + lw;
            BlockPos feet = p.blockPosition().below();
            for (BlockPos q : BlockPos.betweenClosed(feet.offset(-r, 0, -r), feet.offset(r, 0, r))) {
                if (q.distSqr(feet) > (r + 0.5) * (r + 0.5)) continue;
                var fs = level.getFluidState(q);
                if (!fs.is(Fluids.LAVA) || !fs.isSource() || !level.getBlockState(q.above()).isAir()) continue;
                level.setBlockAndUpdate(q, Blocks.BASALT.defaultBlockState());
                COOLED.computeIfAbsent(level, k -> new HashMap<>()).put(q.immutable(), level.getGameTime() + 120 + level.getRandom().nextInt(40));
                level.sendParticles(ParticleTypes.LARGE_SMOKE, q.getX() + 0.5, q.getY() + 1, q.getZ() + 0.5, 1, 0.2, 0, 0.2, 0);
            }
        }
        if (p.onGround()) AIR_JUMPED.remove(p.getUUID());
    }

    /** Cooled basalt turns back into lava once nobody's standing on it. */
    private static void lavaTick(int tick) {
        if (tick % 10 != 0 || COOLED.isEmpty()) return;
        COOLED.forEach((level, cooled) -> {
            long now = level.getGameTime();
            cooled.entrySet().removeIf(en -> {
                if (now < en.getValue()) return false;
                BlockPos q = en.getKey();
                if (!level.isLoaded(q) || !level.getBlockState(q).is(Blocks.BASALT)) return true;
                if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(q.above()).inflate(0.6)).isEmpty()) return false;
                level.setBlockAndUpdate(q, Blocks.LAVA.defaultBlockState());
                return true;
            });
        });
        COOLED.values().removeIf(Map::isEmpty);
    }

    /** Double Jump: the client says you pressed jump in the air. */
    public static void doubleJump(ServerPlayer p) {
        if (p.onGround() || p.isInWater() || p.isPassenger() || p.getAbilities().flying) return;
        if (lv(p, EquipmentSlot.FEET, DOUBLE_JUMP) <= 0 || AIR_JUMPED.containsKey(p.getUUID())) return;
        AIR_JUMPED.put(p.getUUID(), true);
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x * 1.1, 0.55, v.z * 1.1);
        p.fallDistance = 0;
        KitUtil.syncMotion(p);
        if (p.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
            level.playSound(null, p.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5f, 1.5f);
        }
    }
}
