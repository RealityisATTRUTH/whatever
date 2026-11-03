package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Silverfish. Draft numbers, waiting on Reality's sheet.
 *
 *   Passive            tiny, Haste II
 *   R  Stone Swim      toggle: sink into the stone you're standing on or facing
 *                      and swim through it in any direction. You can pass
 *                      through every kind of stone, gravel and stone ores, plus
 *                      air, nothing else. Inside stone you float, so jump and
 *                      sneak move you up and down. In open air you fall and walk
 *                      like normal until you're back in stone. You're
 *                      hidden inside the rock, can't attack or use items, and
 *                      don't regenerate energy. 10 energy to enter. Press R
 *                      again to climb out at the nearest open spot.
 *   Sneak+R  Infest          stone-type blocks within 3 blocks turn into infested
 *                      blocks that release silverfish when broken. Up to 24
 *                      blocks. 20 energy, 20s cooldown.
 *   G  Summon Silverfish  3 silverfish you can command in RTS. 12 alive at
 *                      most. 25 energy, 30s cooldown.
 *   V  Curse Attack    primes your next hit: Mining Fatigue II and Weakness I
 *                      for 15s. 20 energy, 20s cooldown.
 *   Allies             silverfish and endermites
 *   Curse              no Stone Swim, Infest or summoning
 */
public final class SilverfishKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.SILVERFISH, EntityTypes.ENDERMITE);

    private static final String INFEST = "infest";
    private static final String SUMMON = "summon_silverfish";
    private static final String CURSE_ATTACK = "curse_attack";
    private static final int INFEST_RADIUS = 3;
    private static final int INFEST_MAX = 24;
    private static final int MAX_SUMMONED = 12;

    private static final Map<Block, Block> INFESTED = Map.of(
            Blocks.STONE, Blocks.INFESTED_STONE,
            Blocks.COBBLESTONE, Blocks.INFESTED_COBBLESTONE,
            Blocks.STONE_BRICKS, Blocks.INFESTED_STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS, Blocks.INFESTED_CHISELED_STONE_BRICKS,
            Blocks.DEEPSLATE, Blocks.INFESTED_DEEPSLATE);

    private static final Set<UUID> PRIMED = new HashSet<>();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.SCALE, "silverfish_scale", -0.7, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        KitUtil.passive(player, MobEffects.HASTE, 1, true);
        tickStone(player);
        KitUtil.calmAllies(player, 32, ALLIES);
    }

    // --- Stone Swim -------------------------------------------------------------------

    private static final String STONE_SWIM = "stone_swim";
    private static final float SWIM_SPEED = 0.03f;

    /** Last spot where the player was only overlapping stone or air. */
    private static final Map<UUID, Vec3> LAST_VALID = new HashMap<>();

    private static boolean inStone(ServerPlayer player) {
        return VexKit.isPhasing(player); // shares the Vex's pass-through-blocks state
    }

    /**
     * Blocks the Silverfish can swim through: every kind of stone, gravel and
     * the ores set in stone. Listed by name so the list keeps working if
     * Mojang renames a block constant.
     */
    private static final Set<String> SWIMMABLE = Set.of(
            // Stone
            "stone", "cobblestone", "mossy_cobblestone", "smooth_stone",
            "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks",
            "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
            // Deepslate
            "deepslate", "cobbled_deepslate", "polished_deepslate", "chiseled_deepslate",
            "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "cracked_deepslate_tiles",
            // Tuff, calcite, dripstone
            "tuff", "polished_tuff", "chiseled_tuff", "tuff_bricks", "chiseled_tuff_bricks",
            "calcite", "dripstone_block",
            // Gravel
            "gravel",
            // Blackstone and basalt
            "blackstone", "polished_blackstone", "polished_blackstone_bricks", "cracked_polished_blackstone_bricks",
            "chiseled_polished_blackstone", "gilded_blackstone", "basalt", "polished_basalt", "smooth_basalt",
            // Infested blocks
            "infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks",
            "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks", "infested_deepslate",
            // Ores in stone and deepslate
            "coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore",
            "deepslate_coal_ore", "deepslate_iron_ore", "deepslate_copper_ore", "deepslate_gold_ore",
            "deepslate_redstone_ore", "deepslate_lapis_ore", "deepslate_diamond_ore", "deepslate_emerald_ore");

    private static boolean swimmable(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("minecraft") && SWIMMABLE.contains(id.getPath());
    }

    private static void toggleStone(ServerPlayer player) {
        if (inStone(player)) {
            leaveStone(player);
            return;
        }
        if (PlayerMob.isCursed(player)) {
            KitUtil.actionBar(player, "You can't swim through stone while cursed.");
            return;
        }
        BlockPos entry = stoneToEnter(player);
        if (entry == null) {
            KitUtil.actionBar(player, "Stand on or face stone to burrow in.");
            return;
        }
        if (!KitUtil.tryUse(player, STONE_SWIM, "Stone Swim", 10, 20)) return;
        player.setAttached(MobAttachments.PHASING, true);
        player.teleportTo(entry.getX() + 0.5, entry.getY() + 0.1, entry.getZ() + 0.5);
        LAST_VALID.put(player.getUUID(), player.position());
        player.level().playSound(null, entry, SoundEvents.SILVERFISH_STEP, SoundSource.PLAYERS, 1.0f, 0.8f);
        KitUtil.actionBar(player, "In the stone. Press R to climb out.");
    }

    /** Floating in stone: flight that's switched on for you, so you never have to double-tap jump. */
    private static void setSwimming(ServerPlayer player, boolean on) {
        if (player.isCreative() || player.isSpectator()) return;
        Abilities abilities = player.getAbilities();
        float speed = on ? SWIM_SPEED : KitUtil.DEFAULT_FLY_SPEED;
        if (abilities.mayfly == on && abilities.flying == on && abilities.getFlyingSpeed() == speed) return;
        abilities.mayfly = on;
        abilities.flying = on;
        abilities.setFlyingSpeed(speed);
        player.onUpdateAbilities();
    }

    /** The stone block under you, or the one in front of you. */
    private static BlockPos stoneToEnter(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos below = player.blockPosition().below();
        if (swimmable(level.getBlockState(below))) return below;
        BlockPos ahead = player.blockPosition().relative(player.getDirection());
        if (swimmable(level.getBlockState(ahead))) return ahead;
        if (swimmable(level.getBlockState(ahead.above()))) return ahead.above();
        return null;
    }

    private static void leaveStone(ServerPlayer player) {
        player.removeAttached(MobAttachments.PHASING);
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        BlockPos free = KitUtil.standable(level, feet) ? feet : KitUtil.nearestFreeSpot(level, feet, 8);
        if (free != null) player.teleportTo(free.getX() + 0.5, free.getY(), free.getZ() + 0.5);
        setSwimming(player, false);
        player.resetFallDistance();
        LAST_VALID.remove(player.getUUID());
        level.playSound(null, player.blockPosition(), SoundEvents.SILVERFISH_STEP, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** Keeps the Silverfish inside stone and air. Anything else pushes it back. */
    private static void tickStone(ServerPlayer player) {
        if (!inStone(player)) {
            setSwimming(player, false); // e.g. after dying inside stone
            return;
        }
        if (PlayerMob.isCursed(player)) {
            leaveStone(player);
            KitUtil.actionBar(player, "The curse forced you out of the stone.");
            return;
        }
        ServerLevel level = player.level();
        AABB box = player.getBoundingBox().deflate(1.0E-4);
        boolean valid = true;
        boolean touchingStone = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.getCollisionShape(level, pos).isEmpty()) continue;
            if (swimmable(state)) {
                touchingStone = true;
            } else {
                valid = false;
                break;
            }
        }
        // Inside stone you float and swim in any direction without jumping.
        // Out in the open you walk and fall like normal.
        setSwimming(player, touchingStone);
        if (valid) {
            LAST_VALID.put(player.getUUID(), player.position());
        } else {
            Vec3 back = LAST_VALID.get(player.getUUID());
            if (back != null) player.teleportTo(back.x, back.y, back.z);
        }
        if (player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.1, 0.1, 0.1, 0);
        }
    }

    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        return inStone(player) ? 0 : type.regenPerSecond;
    }

    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        return !(inStone(player) && source.is(DamageTypes.IN_WALL)); // no suffocating in your own tunnels
    }

    @Override
    public InteractionResult onUseItem(ServerPlayer player, InteractionHand hand) {
        return inStone(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    @Override
    public InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand) {
        return inStone(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        if (inStone(player)) leaveStone(player);
        KitUtil.modifier(player, Attributes.SCALE, "silverfish_scale", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.passive(player, MobEffects.HASTE, 1, false);
        forget(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        switch (slot) {
            case ABILITY_1 -> {
                if (sneaking) infest(player); else toggleStone(player);
            }
            case ABILITY_2 -> summon(player);
            default -> {
                if (PRIMED.contains(player.getUUID())) {
                    KitUtil.actionBar(player, "Curse Attack is already primed.");
                    return;
                }
                if (!KitUtil.tryUse(player, CURSE_ATTACK, "Curse Attack", 20, 400)) return;
                PRIMED.add(player.getUUID());
                player.level().playSound(null, player.blockPosition(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.7f);
                KitUtil.actionBar(player, "Curse Attack primed. Your next hit curses.");
            }
        }
    }

    private static void infest(ServerPlayer player) {
        if (PlayerMob.isCursed(player)) {
            KitUtil.actionBar(player, "You can't infest while cursed.");
            return;
        }
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();
        // Find the blocks first, so energy is only spent if there's stone around.
        List<BlockPos> targets = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-INFEST_RADIUS, -INFEST_RADIUS, -INFEST_RADIUS),
                center.offset(INFEST_RADIUS, INFEST_RADIUS, INFEST_RADIUS))) {
            if (targets.size() >= INFEST_MAX) break;
            if (INFESTED.containsKey(level.getBlockState(pos).getBlock())) targets.add(pos.immutable());
        }
        if (targets.isEmpty()) {
            KitUtil.actionBar(player, "There's no stone nearby to infest.");
            return;
        }
        if (!KitUtil.tryUse(player, INFEST, "Infest", 20, 400)) return;
        for (BlockPos pos : targets) {
            Block infested = INFESTED.get(level.getBlockState(pos).getBlock());
            if (infested != null) level.setBlock(pos, infested.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.playSound(null, center, SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.8f);
        KitUtil.actionBar(player, "Infested " + targets.size() + " blocks.");
    }

    private static void summon(ServerPlayer player) {
        if (PlayerMob.isCursed(player)) {
            KitUtil.actionBar(player, "You can't summon while cursed.");
            return;
        }
        int alive = KitUtil.ownedMobs(player, 64, mob -> mob.getType() == EntityTypes.SILVERFISH).size();
        int room = Math.min(3, MAX_SUMMONED - alive);
        if (room <= 0) {
            KitUtil.actionBar(player, "You already have " + MAX_SUMMONED + " silverfish.");
            return;
        }
        if (!KitUtil.tryUse(player, SUMMON, "Summon Silverfish", 25, 600)) return;
        for (int i = 0; i < room; i++) {
            double angle = Math.PI * 2 * i / room;
            KitUtil.summon(player, "minecraft:silverfish",
                    player.getX() + Math.cos(angle), player.getY(), player.getZ() + Math.sin(angle), "");
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        if (!PRIMED.remove(player.getUUID())) return;
        KitUtil.effect(victim, MobEffects.MINING_FATIGUE, 300, 1);
        KitUtil.effect(victim, MobEffects.WEAKNESS, 300, 0);
        player.level().playSound(null, victim.blockPosition(), SoundEvents.SILVERFISH_HURT, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    static void forget(ServerPlayer player) {
        if (inStone(player)) leaveStone(player); // never log out inside a wall
        PRIMED.remove(player.getUUID());
    }

    // --- Ultimate: Infestation ---------------------------------------------------------------
    /**
     * 8 silverfish swarm out for 1:00, on top of your usual 12, and take RTS
     * orders. Enemies within 8 blocks get Mining Fatigue II and Weakness I for 15s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Infestation";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            UltimateFx.summonFor(player, "minecraft:silverfish", player.getX() + Math.cos(angle) * 1.5, player.getY(),
                    player.getZ() + Math.sin(angle) * 1.5, "", 1200);
        }
        for (LivingEntity enemy : UltimateFx.enemies(player, 8)) {
            KitUtil.effect(enemy, MobEffects.MINING_FATIGUE, 300, 1);
            KitUtil.effect(enemy, MobEffects.WEAKNESS, 300, 0);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.6f);
        KitUtil.actionBar(player, "Infestation!");
        return true;
    }
}
