package com.reality.mobcraft.item;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The traits Gloamite tools can have on top of being stronger than netherite. Each tool rolls two from its list
 * (Tiers rolls them and the Duskvein Anvil rerolls them with a Dusk Binding).
 *
 * Sword:   Sweep Hiss, Soul Siphon, Brittle Armor, Last Crack
 * Pickaxe: Vein Sense, Ore Echo, Shard Bonus, Self Mend, Glow Trail
 * Axe:     Splinter, Crystal Bite, Rooted, Sap Mend, Dusk Cleave
 * Shovel:  Excavate, Ground Sense, Soul Dig, Buried Echo, Dust Cloud
 * Hoe:     Harvest Sweep, Auto Replant, Till Wave, Crystal Bloom, Wither Touch
 *
 * Rooted and Soul Dig live in mixins (knockback, block slowdown and dig speed); Sweep Hiss's wider sweep is a mixin
 * on the player's sweep attack. Everything else is here.
 */
public final class GloamiteTraits {

    private GloamiteTraits() {}

    public static final DustParticleOptions PURPLE = new DustParticleOptions(0xB06CFF, 1.1f);
    private static final DustParticleOptions PURPLE_SMALL = new DustParticleOptions(0xB06CFF, 0.7f);

    /** The trait names under each tool, shown in its tooltip. */
    public static final Map<String, List<String>> TRAITS = Map.of(
            "sword", List.of("Sweep Hiss", "Soul Siphon", "Brittle Armor", "Last Crack"),
            "pickaxe", List.of("Vein Sense", "Ore Echo", "Shard Bonus", "Self Mend", "Glow Trail"),
            "axe", List.of("Splinter", "Crystal Bite", "Rooted", "Sap Mend", "Dusk Cleave"),
            "shovel", List.of("Excavate", "Ground Sense", "Soul Dig", "Buried Echo", "Dust Cloud"),
            "hoe", List.of("Harvest Sweep", "Auto Replant", "Till Wave", "Crystal Bloom", "Wither Touch"));

    /** What each trait does, one short line for the tooltip. */
    public static final Map<String, String> WHAT = Map.ofEntries(
            Map.entry("Sweep Hiss", "Sweeps reach farther"),
            Map.entry("Soul Siphon", "Kills heal a heart"),
            Map.entry("Brittle Armor", "Wears down their armor faster"),
            Map.entry("Last Crack", "Hits harder under 3 hearts"),
            Map.entry("Vein Sense", "Sneak to see Duskvein through walls"),
            Map.entry("Ore Echo", "Chimes when more ore touches it"),
            Map.entry("Shard Bonus", "Extra amethyst and quartz"),
            Map.entry("Self Mend", "Repairs while mining in the dark"),
            Map.entry("Glow Trail", "Mined spots glow for a while"),
            Map.entry("Splinter", "Chopping can splinter nearby mobs"),
            Map.entry("Crystal Bite", "Hits crack mobs for damage over time"),
            Map.entry("Rooted", "Half knockback while held"),
            Map.entry("Sap Mend", "Repairs while chopping logs"),
            Map.entry("Dusk Cleave", "Night crits hit everything in front"),
            Map.entry("Excavate", "Sneak to dig 3 by 3"),
            Map.entry("Ground Sense", "Warns of caves and lava below"),
            Map.entry("Soul Dig", "Fast on soul sand, no slowdown"),
            Map.entry("Buried Echo", "Can uncover suspicious sand or gravel"),
            Map.entry("Dust Cloud", "Digging fast blinds mobs nearby"),
            Map.entry("Harvest Sweep", "Harvests the ripe crops around"),
            Map.entry("Auto Replant", "Replants with the seeds it drops"),
            Map.entry("Till Wave", "Tills 3 by 3"),
            Map.entry("Crystal Bloom", "Bone meal can grow rare flowers or amethyst"),
            Map.entry("Wither Touch", "Hits wither mobs"));

    /** "sword", "pickaxe", "axe", "shovel" or "hoe" for a Gloamite tool, otherwise null. */
    public static String kind(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Identifier model = stack.get(DataComponents.ITEM_MODEL);
        if (model == null || !Gloamite.isGloamite(stack)) return null;
        String k = model.getPath().substring("gloamite_".length());
        return TRAITS.containsKey(k) ? k : null;
    }

    public static boolean holds(LivingEntity e, String kind) {
        return e != null && kind.equals(kind(e.getMainHandItem()));
    }

    /** The traits this Gloamite tool rolled, two of its kind's list. Empty until it has rolled. */
    public static List<String> rolled(ItemStack stack) {
        Tiers.Rolled r = stack.get(Tiers.GLOAMITE_TRAITS);
        return r == null ? List.of() : r.names();
    }

    public static boolean has(ItemStack stack, String trait) {
        return kind(stack) != null && rolled(stack).contains(trait);
    }

    public static boolean has(LivingEntity e, String trait) {
        return e != null && has(e.getMainHandItem(), trait);
    }

    /** True while this class is doing its own extra hits or breaks, so they don't set off more traits. */
    private static boolean busy;

    private static void quietly(Runnable r) {
        boolean was = busy;
        busy = true;
        try { r.run(); } finally { busy = was; }
    }

    public static boolean busy() { return busy; }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) afterBreak(server, sp, pos, state);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register(GloamiteTraits::afterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            // Soul Siphon: a kill with the sword gives back a heart.
            if (source.getEntity() instanceof Player p && source.getDirectEntity() == p && has(p, "Soul Siphon") && p.isAlive()) {
                p.heal(2f);
                if (p.level() instanceof ServerLevel s) s.sendParticles(PURPLE, entity.getX(), entity.getY() + entity.getBbHeight() / 2,
                        entity.getZ(), 10, 0.3, 0.4, 0.3, 0);
            }
        });
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (level instanceof ServerLevel server && hand == InteractionHand.MAIN_HAND) duskCleave(server, player, target);
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            ItemStack held = player.getItemInHand(hand);
            if (has(held, "Till Wave") && hit.getDirection() != Direction.DOWN) tillWave(server, player, pos, held);
            if (held.is(Items.BONE_MEAL)) {
                InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                if (has(player.getItemInHand(other), "Crystal Bloom")) return crystalBloom(server, player, pos, held);
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) veinSense(p);
            if (server.getTickCount() % 20 == 0) crackTick();
        });
    }

    // ---------------------------------------------------------------- damage

    /** Called from the damage hook before damage is applied. Last Crack lives here. */
    public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
        if (busy) return amount;
        if (source.getEntity() instanceof Player p && source.getDirectEntity() == p && has(p, "Last Crack") && p.getHealth() <= 6f) {
            if (p.level() instanceof ServerLevel s) {
                s.sendParticles(PURPLE, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 14, 0.35, 0.4, 0.35, 0);
                s.sendParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(), 10, 0.3, 0.4, 0.3, 0.15);
            }
            return amount * 1.35f;
        }
        return amount;
    }

    private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
        if (busy || blocked || !(source.getEntity() instanceof Player p) || source.getDirectEntity() != p) return;
        if (!(victim.level() instanceof ServerLevel level)) return;
        String kind = kind(p.getMainHandItem());
        if (kind == null) return;
        switch (kind) {
            case "sword" -> {
                // Brittle Armor: each hit wears the target's armor down two extra points.
                if (has(p, "Brittle Armor")) for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack armor = victim.getItemBySlot(slot);
                    if (!armor.isEmpty() && armor.isDamageableItem()) armor.hurtAndBreak(2, victim, slot);
                }
            }
            case "axe" -> { if (has(p, "Crystal Bite")) crack(victim, p); }
            case "hoe" -> {
                // Wither Touch.
                if (has(p, "Wither Touch")) victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), p);
            }
            default -> {}
        }
    }

    // Crystal Bite: three pulses of a heart over three seconds; another hit starts it over instead of stacking.
    private record Crack(LivingEntity target, Player by, int[] left) {}
    private static final Map<UUID, Crack> CRACKED = new HashMap<>();

    private static void crack(LivingEntity target, Player by) {
        CRACKED.put(target.getUUID(), new Crack(target, by, new int[]{3}));
        if (target.level() instanceof ServerLevel s) s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                Blocks.AMETHYST_BLOCK.defaultBlockState()), target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
    }

    private static void crackTick() {
        CRACKED.values().removeIf(c -> {
            LivingEntity t = c.target();
            if (!t.isAlive() || !(t.level() instanceof ServerLevel s)) return true;
            quietly(() -> {
                t.setInvulnerableTime(0);
                t.hurtServer(s, s.damageSources().magic(), 1.5f);
            });
            s.sendParticles(PURPLE, t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 8, 0.3, 0.4, 0.3, 0);
            s.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6f, 1.4f);
            return --c.left()[0] <= 0;
        });
    }

    /** Dusk Cleave: a critical hit with the axe at night hits everything in a small arc in front of you. */
    private static void duskCleave(ServerLevel level, Player p, Entity target) {
        if (busy || !has(p, "Dusk Cleave") || !level.isDarkOutside()) return;
        boolean crit = p.getAttackStrengthScale(0.5f) > 0.9f && p.fallDistance > 0 && !p.onGround() && !p.onClimbable()
                && !p.isInWater() && !p.hasEffect(MobEffects.BLINDNESS) && !p.isPassenger() && !p.isSprinting();
        if (!crit) return;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        float damage = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6f;
        List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4, 1, 4), e -> {
            if (e == target || !MaceEnchants.affects(e, p, null)) return false;
            Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
            return to.length() <= 4 && to.normalize().dot(look) > 0.5;
        });
        quietly(() -> {
            for (LivingEntity e : hit) e.hurtServer(level, level.damageSources().playerAttack(p), damage);
        });
        double base = Math.atan2(look.z, look.x);
        for (int i = -6; i <= 6; i++) {
            double a = base + i * 0.13;
            level.sendParticles(PURPLE, p.getX() + Math.cos(a) * 2.6, p.getY() + 1.0, p.getZ() + Math.sin(a) * 2.6, 2, 0.05, 0.1, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + look.x * 2, p.getY() + 1, p.getZ() + look.z * 2, 1, 0, 0, 0, 0);
        level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.6f);
    }

    /** Sweep Hiss: called after the sword's normal sweep. It also catches mobs a little farther out and draws a purple arc. */
    public static void sweepHiss(Player p, Entity target, float damage, DamageSource source) {
        if (busy || !has(p, "Sweep Hiss") || !(p.level() instanceof ServerLevel level)) return;
        AABB vanilla = target.getBoundingBox().inflate(1, 0.25, 1);
        List<LivingEntity> extra = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.3, 0.5, 2.3), e ->
                e != target && !vanilla.intersects(e.getBoundingBox()) && MaceEnchants.affects(e, p, null) && p.distanceToSqr(e) < 20);
        float sweep = 1 + damage * 0.5f;
        quietly(() -> {
            for (LivingEntity e : extra) {
                e.hurtServer(level, source, sweep);
            }
        });
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        double base = Math.atan2(look.z, look.x);
        for (int i = -8; i <= 8; i++) {
            double a = base + i * 0.12;
            level.sendParticles(PURPLE, p.getX() + Math.cos(a) * 2.8, p.getY() + 1.1 + i * 0.01, p.getZ() + Math.sin(a) * 2.8, 1, 0, 0, 0, 0);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.35f, 1.8f);
    }

    // ---------------------------------------------------------------- breaking blocks

    private static void afterBreak(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
        ItemStack tool = p.getMainHandItem();
        String kind = kind(tool);
        if (kind == null) return;
        switch (kind) {
            case "pickaxe" -> pickaxe(level, p, pos, state, tool);
            case "axe" -> axe(level, p, pos, state, tool);
            case "shovel" -> shovel(level, p, pos, state);
            case "hoe" -> hoe(level, p, pos, state);
            default -> {}
        }
    }

    private static boolean dark(ServerLevel level, BlockPos pos) {
        return level.getMaxLocalRawBrightness(pos) <= 7;
    }

    private static void mend(ItemStack tool) {
        if (tool.isDamaged()) tool.setDamageValue(tool.getDamageValue() - 1);
    }

    private static void pickaxe(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state, ItemStack tool) {
        // Ore Echo: a chime when more of the same ore is touching the one you broke.
        if (has(tool, "Ore Echo") && (state.is(ConventionalBlockTags.ORES) || state.is(Duskvein.ORE))) {
            for (Direction d : Direction.values()) {
                if (level.getBlockState(pos.relative(d)).is(state.getBlock())) {
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 1.6f);
                    level.sendParticles(PURPLE_SMALL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
                    break;
                }
            }
        }
        // Shard Bonus: crystals sometimes drop one more. Duskvein is left out so Gloamite stays rare.
        if (has(tool, "Shard Bonus") && level.getRandom().nextFloat() < 0.35f) {
            if (state.is(Blocks.AMETHYST_CLUSTER)) Block.popResource(level, pos, new ItemStack(Items.AMETHYST_SHARD));
            else if (state.is(Blocks.NETHER_QUARTZ_ORE)) Block.popResource(level, pos, new ItemStack(Items.QUARTZ));
        }
        // Self Mend: in the dark, mining sometimes fixes the point it just used.
        if (has(tool, "Self Mend") && dark(level, pos) && level.getRandom().nextBoolean()) mend(tool);
        // Glow Trail: the spot glows faintly purple for five seconds.
        if (!has(tool, "Glow Trail")) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        for (int i = 0; i < 10; i++) {
            MaceEnchants.later(level, i * 10, () -> level.sendParticles(PURPLE, x, y, z, 4, 0.25, 0.25, 0.25, 0));
        }
    }

    private static void axe(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state, ItemStack tool) {
        if (!state.is(BlockTags.LOGS)) return;
        // Sap Mend.
        if (has(tool, "Sap Mend") && level.getRandom().nextBoolean()) mend(tool);
        // Splinter: one in four logs throws splinters into whatever is close.
        if (has(tool, "Splinter") && level.getRandom().nextFloat() < 0.25f) {
            Vec3 c = Vec3.atCenterOf(pos);
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(3),
                    e -> MaceEnchants.affects(e, p, null) && e.position().distanceTo(c) <= 3.5);
            quietly(() -> { for (LivingEntity e : near) e.hurtServer(level, level.damageSources().playerAttack(p), 2f); });
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x, c.y, c.z, 30, 0.8, 0.5, 0.8, 0.3);
            level.playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.5f, 1.6f);
        }
    }

    private static final Map<UUID, Deque<Long>> DIGS = new HashMap<>();
    private static final Map<UUID, Long> DUST_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> SENSE_COOLDOWN = new HashMap<>();

    private static void shovel(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
        long now = level.getGameTime();
        ItemStack tool = p.getMainHandItem();
        // Buried Echo: digging sand or gravel sometimes turns a block next to it into a suspicious one with loot.
        if (has(tool, "Buried Echo") && (state.is(Blocks.SAND) || state.is(Blocks.GRAVEL)) && level.getRandom().nextFloat() < 0.03f) buriedEcho(level, p, pos);
        if (busy) return;
        // Excavate: sneaking digs the 3 by 3 around it.
        if (has(tool, "Excavate") && p.isShiftKeyDown() && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) excavate(level, p, pos);
        // Ground Sense: a hiss when there's a cave or lava just under where you dug.
        if (has(tool, "Ground Sense") && now - SENSE_COOLDOWN.getOrDefault(p.getUUID(), -100L) >= 40) {
            for (int dy = 1; dy <= 4; dy++) {
                BlockPos below = pos.below(dy);
                BlockState b = level.getBlockState(below);
                if (b.getFluidState().is(Fluids.LAVA) || b.getFluidState().is(Fluids.FLOWING_LAVA)) {
                    warn(level, p, "Lava below!", ChatFormatting.GOLD);
                    break;
                }
                if (b.isAir() && dy >= 2) {
                    warn(level, p, "Hollow ground below", ChatFormatting.LIGHT_PURPLE);
                    break;
                }
                if (!b.isSolidRender()) break;
            }
        }
        // Dust Cloud: digging fast kicks up purple dust that blinds and slows mobs close by.
        if (!has(tool, "Dust Cloud")) return;
        Deque<Long> digs = DIGS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        digs.addLast(now);
        while (!digs.isEmpty() && now - digs.peekFirst() > 40) digs.removeFirst();
        if (digs.size() >= 5 && now - DUST_COOLDOWN.getOrDefault(p.getUUID(), -100L) >= 60) {
            DUST_COOLDOWN.put(p.getUUID(), now);
            digs.clear();
            level.sendParticles(PURPLE, p.getX(), p.getY() + 0.6, p.getZ(), 60, 1.8, 0.5, 1.8, 0);
            level.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 0.6, p.getZ(), 40, 1.8, 0.5, 1.8, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(3.5),
                    e -> MaceEnchants.affects(e, p, null))) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), p);
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), p);
            }
        }
    }

    private static void warn(ServerLevel level, ServerPlayer p, String text, ChatFormatting color) {
        SENSE_COOLDOWN.put(p.getUUID(), level.getGameTime());
        p.sendOverlayMessage(Component.literal(text).withStyle(color));
        level.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 1.5f);
    }

    private static void excavate(ServerLevel level, ServerPlayer p, BlockPos center) {
        Direction.Axis axis = Math.abs(p.getXRot()) > 45 ? Direction.Axis.Y : p.getDirection().getAxis();
        List<BlockPos> around = new ArrayList<>();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                around.add(switch (axis) {
                    case Y -> center.offset(a, 0, b);
                    case X -> center.offset(0, a, b);
                    case Z -> center.offset(a, b, 0);
                });
            }
        }
        quietly(() -> {
            for (BlockPos q : around) {
                BlockState s = level.getBlockState(q);
                if (s.is(BlockTags.MINEABLE_WITH_SHOVEL) && s.getDestroySpeed(level, q) >= 0 && kind(p.getMainHandItem()) != null) {
                    p.gameMode.destroyBlock(q);
                }
            }
        });
    }

    private static void buriedEcho(ServerLevel level, ServerPlayer p, BlockPos pos) {
        List<Direction> dirs = new ArrayList<>(List.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        for (Direction d : dirs) {
            BlockPos q = pos.relative(d);
            BlockState s = level.getBlockState(q);
            boolean sand = s.is(Blocks.SAND), gravel = s.is(Blocks.GRAVEL);
            if (!sand && !gravel) continue;
            level.setBlockAndUpdate(q, (sand ? Blocks.SUSPICIOUS_SAND : Blocks.SUSPICIOUS_GRAVEL).defaultBlockState());
            if (level.getBlockEntity(q) instanceof BrushableBlockEntity brush) {
                brush.setLootTable(sand ? BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY : BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON,
                        level.getRandom().nextLong());
            }
            level.sendParticles(PURPLE, q.getX() + 0.5, q.getY() + 1.0, q.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0);
            level.playSound(null, q, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 0.8f);
            p.sendOverlayMessage(Component.literal("Something is buried here. Brush it.").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
    }

    private static boolean ripe(BlockState s) {
        if (s.getBlock() instanceof CropBlock crop) return crop.isMaxAge(s);
        if (s.getBlock() instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        return false;
    }

    private static void hoe(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
        if (!ripe(state)) return;
        ItemStack tool = p.getMainHandItem();
        // Auto Replant: plant it back with one of the seeds it just dropped. The break event comes before the drops,
        // so this waits for the end of the tick.
        net.minecraft.world.item.Item seed = state.getBlock().asItem();
        BlockPos at = pos.immutable();
        if (has(tool, "Auto Replant")) MaceEnchants.later(level, 0, () -> {
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(1),
                    e -> e.getItem().is(seed) && e.getAge() <= 2)) {
                BlockState young = state.getBlock().defaultBlockState();
                if (level.getBlockState(at).isAir() && young.canSurvive(level, at)) {
                    level.setBlockAndUpdate(at, young);
                    drop.getItem().shrink(1);
                    if (drop.getItem().isEmpty()) drop.discard();
                }
                break;
            }
        });
        if (busy || !has(tool, "Harvest Sweep")) return;
        // Harvest Sweep: the ripe crops around it come up too.
        quietly(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos q = pos.offset(dx, 0, dz);
                    if (ripe(level.getBlockState(q))) p.gameMode.destroyBlock(q);
                }
            }
        });
    }

    // ---------------------------------------------------------------- using items

    private static boolean tillable(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.DIRT_PATH)) && level.getBlockState(pos.above()).isAir();
    }

    /** Till Wave: the game tills the block you clicked, this does the eight around it. */
    private static void tillWave(ServerLevel level, Player p, BlockPos pos, ItemStack hoe) {
        if (!tillable(level, pos)) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos q = pos.offset(dx, 0, dz);
                if ((dx != 0 || dz != 0) && tillable(level, q)) {
                    level.setBlockAndUpdate(q, Blocks.FARMLAND.defaultBlockState());
                    level.sendParticles(PURPLE_SMALL, q.getX() + 0.5, q.getY() + 1.05, q.getZ() + 0.5, 2, 0.25, 0, 0.25, 0);
                }
            }
        }
    }

    private static final Block[] RARE_FLOWERS = {Blocks.TORCHFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.BLUE_ORCHID, Blocks.ALLIUM};

    /** Crystal Bloom: bone meal with the hoe in your other hand sometimes grows a rare flower, or an amethyst bud. */
    private static InteractionResult crystalBloom(ServerLevel level, Player p, BlockPos pos, ItemStack meal) {
        BlockState s = level.getBlockState(pos);
        var random = level.getRandom();
        if ((s.is(Blocks.AMETHYST_BLOCK) || s.is(Blocks.BUDDING_AMETHYST))) {
            if (random.nextFloat() >= 0.3f) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0);
                if (!p.hasInfiniteMaterials()) meal.shrink(1);
                return InteractionResult.SUCCESS;
            }
            List<Direction> open = new ArrayList<>();
            for (Direction d : Direction.values()) if (level.getBlockState(pos.relative(d)).isAir()) open.add(d);
            if (open.isEmpty()) return InteractionResult.PASS;
            Direction d = open.get(random.nextInt(open.size()));
            level.setBlockAndUpdate(pos.relative(d), Blocks.SMALL_AMETHYST_BUD.defaultBlockState().setValue(BlockStateProperties.FACING, d));
            bloom(level, pos.relative(d));
            if (!p.hasInfiniteMaterials()) meal.shrink(1);
            return InteractionResult.SUCCESS;
        }
        // Clicking the grass growing on a grass block counts too, since the first bone meal covers the block in grass.
        BlockPos ground = s.is(Blocks.GRASS_BLOCK) ? pos : level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) ? pos.below() : null;
        // Only when the bone meal is really used on it, so clicking a flower over and over can't grow free ones.
        boolean used = s.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b && b.isValidBonemealTarget(level, pos, s, net.minecraft.world.level.block.BonemealSource.INTERACTION);
        if (ground != null && used && random.nextFloat() < 0.2f) {
            for (int tries = 0; tries < 12; tries++) {
                BlockPos q = ground.offset(random.nextInt(5) - 2, 1, random.nextInt(5) - 2);
                BlockState at = level.getBlockState(q);
                if ((at.isAir() || at.is(Blocks.SHORT_GRASS)) && level.getBlockState(q.below()).is(Blocks.GRASS_BLOCK)) {
                    level.setBlockAndUpdate(q, RARE_FLOWERS[random.nextInt(RARE_FLOWERS.length)].defaultBlockState());
                    bloom(level, q);
                    break;
                }
            }
        }
        return InteractionResult.PASS;   // the bone meal still does its normal thing on grass
    }

    private static void bloom(ServerLevel level, BlockPos q) {
        level.sendParticles(PURPLE, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0);
        level.playSound(null, q, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
    }

    // ---------------------------------------------------------------- Vein Sense

    private static final Map<UUID, Integer> SNEAK = new HashMap<>();

    /** Sneaking with the pickaxe for a second makes Duskvein ore close by glow through walls for three seconds. */
    private static void veinSense(ServerPlayer p) {
        if (!p.isShiftKeyDown() || !has(p, "Vein Sense") || !(p.level() instanceof ServerLevel level)) {
            SNEAK.remove(p.getUUID());
            return;
        }
        int t = SNEAK.merge(p.getUUID(), 1, Integer::sum);
        if (t != 20 && (t < 20 || (t - 20) % 70 != 0)) return;
        BlockPos c = p.blockPosition();
        int found = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-10, -10, -10), c.offset(10, 10, 10))) {
            if (found >= 40) break;
            BlockState s = level.getBlockState(q);
            if (!s.is(Duskvein.ORE)) continue;
            found++;
            Display.BlockDisplay show = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
            if (show == null) continue;
            show.setBlockState(s);
            show.setPos(q.getX(), q.getY(), q.getZ());
            show.setGlowingTag(true);
            show.setGlowColorOverride(0xB06CFF);
            level.addFreshEntity(show);
            MaceEnchants.later(level, 60, show::discard);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, found > 0 ? 1.4f : 0.7f);
        p.sendOverlayMessage(Component.literal(found > 0 ? "Vein Sense: " + found + " Duskvein nearby" : "Vein Sense: no Duskvein nearby")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
