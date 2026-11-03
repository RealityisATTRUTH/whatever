package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/**
 * Ender Dragon (Aethalas). DRAFT numbers from the balance pass.
 *
 *   Energy        100, 1 per second. Double in the End.
 *   Flight        sprinting speed, 1 energy per second while flying (instead of
 *                 regenerating). Out of energy, she can't fly again until she
 *                 has 10. Taking damage grounds her for 3s.
 *   Immunities    Wither and Slow Falling. Fire Resistance and Water Breathing.
 *   Crystals      the nearest end crystal within 24 blocks beams at her and
 *                 heals her: Regeneration II in the End, Regeneration I
 *                 anywhere else
 *   Dragon Breath R: a dragon fireball where she looks, leaving a damaging
 *                 purple cloud that never hurts her. 30 energy, 20s cooldown.
 *   Wing Buffet   G: knocks back players within 6 blocks. 20 energy, 12s cooldown.
 *   The Egg       starts with the dragon egg. Self-give one only without one in
 *                 her inventory, 10:00 cooldown. Never drops it on death.
 *   Portals       self-give end portal frames, placeable only in the End or a
 *                 stronghold. Once she has been to the End, she can break end
 *                 portal frames by punching them, getting the frame back.
 *   Self-give     dragon's breath, 4 at a time, out of combat only
 *   Home          the End: Strength I and double energy regen
 *   Allies        endermen, endermites and shulkers
 *   Curse         can't fly, no crystal regen, can't spawn an egg
 *   Drops         64 dragon's breath and a dragon head, never the egg
 *
 * While someone is the Ender Dragon, no dragon spawns and all 20 gateways
 * open: see EndRules. Switching off the Ender Dragon turns that off again
 * and takes the egg away.
 */
public final class EnderDragonKit implements MobKit {

    private static final String BREATH = "dragon_breath";
    private static final String BUFFET = "wing_buffet";
    private static final String EGG_COOLDOWN = "dragon_egg_spawn";
    private static final float FLY_SPEED = 0.026f;      // about sprinting speed, like the Bat
    private static final int GROUNDED_TICKS = 60;       // 3 seconds
    private static final float FLY_AGAIN_AT = 10;
    private static final double CRYSTAL_RANGE = 24;
    private static final int EGG_COOLDOWN_TICKS = 12000; // 10:00
    private static final int BREATH_GIVE_MAX = 4;
    private static final int OUT_OF_COMBAT = 300;        // 15 seconds
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.ENDERMAN, EntityTypes.ENDERMITE, EntityTypes.SHULKER);
    private static final Identifier ENTERED_END = Identifier.withDefaultNamespace("story/enter_the_end");
    // Not end/root: MobAdvancements grants every vanilla tab root on join, so it no longer means she went there.

    private static final KitUtil.Timers GROUNDED = new KitUtil.Timers();
    private static final Set<UUID> TIRED = new HashSet<>();
    private static final Map<UUID, EndCrystal> LINKED_CRYSTAL = new HashMap<>();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    // --- Passives ------------------------------------------------------------------

    @Override
    public void onAssigned(ServerPlayer player) {
        if (!hasEgg(player)) give(player, new ItemStack(Items.DRAGON_EGG));
        EndRules.setDragonPlayer(player);
    }

    @Override
    public void tick(ServerPlayer player) {
        // Keeps the world's record right for anyone who was the Ender Dragon before it existed.
        if (player.tickCount % 100 == 0) EndRules.setDragonPlayer(player);
        UUID id = player.getUUID();
        boolean cursed = PlayerMob.isCursed(player);
        boolean inEnd = player.level().dimension() == Level.END;

        // Flight.
        if (player.getAbilities().flying && PlayerMob.energy(player) <= 0) TIRED.add(id);
        if (PlayerMob.energy(player) >= FLY_AGAIN_AT) TIRED.remove(id);
        boolean canFly = !cursed && !GROUNDED.active(player) && !TIRED.contains(id);
        KitUtil.flight(player, canFly, FLY_SPEED);

        // Immunities.
        if (player.hasEffect(MobEffects.WITHER)) player.removeEffect(MobEffects.WITHER);
        if (player.hasEffect(MobEffects.SLOW_FALLING)) player.removeEffect(MobEffects.SLOW_FALLING);
        KitUtil.passive(player, MobEffects.FIRE_RESISTANCE, 0, true);
        KitUtil.passive(player, MobEffects.WATER_BREATHING, 0, true);

        // Home.
        KitUtil.passive(player, MobEffects.STRENGTH, 0, inEnd);

        // End crystals: the nearest one heals her and shines its beam at her, like the real dragon.
        if (cursed) unlinkCrystal(player);
        EndCrystal crystal = cursed ? null : linkCrystal(player);
        KitUtil.passive(player, MobEffects.REGENERATION, inEnd ? 1 : 0, crystal != null);

        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.flight(player, false, FLY_SPEED);
        KitUtil.passive(player, MobEffects.FIRE_RESISTANCE, 0, false);
        KitUtil.passive(player, MobEffects.WATER_BREATHING, 0, false);
        KitUtil.passive(player, MobEffects.STRENGTH, 0, false);
        KitUtil.passive(player, MobEffects.REGENERATION, 0, false);
        removeEggs(player); // the egg belongs to the Ender Dragon
        EndRules.clearDragonPlayer(player);
        forget(player);
    }

    private static void removeEggs(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.DRAGON_EGG)) inventory.setItem(i, ItemStack.EMPTY);
        }
    }

    /** Flying costs 1 energy a second instead of regenerating. The End doubles regen. */
    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        if (player.getAbilities().flying) return -1.0f;
        return KitUtil.doubledAtHome(player.level().dimension() == Level.END, type);
    }

    /** Her own Dragon Breath cloud never hurts her. */
    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        return source.getEntity() != player;
    }

    @Override
    public void afterHurt(ServerPlayer player, DamageSource source, float damage) {
        if (damage <= 0) return;
        if (player.getAbilities().flying) KitUtil.actionBar(player, "Hit! You can't fly for 3 seconds.");
        GROUNDED.start(player, GROUNDED_TICKS);
    }

    // --- Abilities -----------------------------------------------------------------

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (!KitUtil.tryUse(player, BREATH, "Dragon Breath", 30, 400)) return;
            dragonBreath(player);
        } else if (slot == ABILITY_2) {
            if (!KitUtil.tryUse(player, BUFFET, "Wing Buffet", 20, 240)) return;
            wingBuffet(player);
        } else {
            KitUtil.actionBar(player, "The Ender Dragon has no third ability.");
        }
    }

    private static void dragonBreath(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        DragonFireball fireball = new DragonFireball(level, player, look);
        Vec3 eye = player.getEyePosition();
        fireball.setPos(eye.x + look.x * 1.5, eye.y + look.y * 1.5 - 0.3, eye.z + look.z * 1.5);
        level.addFreshEntity(fireball);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void wingBuffet(ServerPlayer player) {
        ServerLevel level = player.level();
        for (Player target : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(6),
                p -> p != player && p.isAlive() && !p.isSpectator() && !p.isAlliedTo(player))) {
            Vec3 away = target.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0, away.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(flat.x * 1.6, 0.6, flat.z * 1.6));
            KitUtil.syncMotion(target);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.5f, 0.8f);
    }

    // --- The egg, portal frames, self-give ---------------------------------------------

    @Override
    public boolean canSelfGive(ServerPlayer player, String itemId) {
        switch (itemId) {
            case "dragon_egg" -> {
                if (PlayerMob.isCursed(player)) {
                    KitUtil.actionBar(player, "You can't spawn an egg while cursed.");
                    return false;
                }
                if (hasEgg(player)) {
                    KitUtil.actionBar(player, "You already have the egg.");
                    return false;
                }
                int left = Cooldowns.remaining(player, EGG_COOLDOWN);
                if (left > 0) {
                    KitUtil.actionBar(player, "A new egg in " + KitUtil.seconds(left) + ".");
                    return false;
                }
                return true;
            }
            case "end_portal_frame" -> {
                if (!portalSpot(player)) {
                    KitUtil.actionBar(player, "Portal frames only come to you in the End or a stronghold.");
                    return false;
                }
                return true;
            }
            case "dragon_breath" -> {
                if (CombatTag.inCombat(player, OUT_OF_COMBAT)) {
                    KitUtil.actionBar(player, "Dragon's breath only when out of combat.");
                    return false;
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    @Override
    public void customizeSelfGive(ServerPlayer player, String itemId, ItemStack stack) {
        if (itemId.equals("dragon_egg")) {
            stack.setCount(1);
            Cooldowns.start(player, EGG_COOLDOWN, EGG_COOLDOWN_TICKS);
        } else if (itemId.equals("dragon_breath")) {
            stack.setCount(Math.min(stack.getCount(), BREATH_GIVE_MAX));
        }
    }

    /** Portal frames can only be placed in the End or inside a stronghold. */
    @Override
    public InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(Items.END_PORTAL_FRAME) && !portalSpot(player)) {
            KitUtil.actionBar(player, "Portal frames only go down in the End or a stronghold.");
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static boolean portalSpot(ServerPlayer player) {
        return player.level().dimension() == Level.END || KitUtil.inStructure(player, BuiltinStructures.STRONGHOLD);
    }

    /**
     * Called when she punches an end portal frame. Once she has been to the End,
     * the frame breaks and drops, and any portal it held closes.
     */
    public static boolean breakFrame(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.level().getServer();
        var advancement = server.getAdvancements().get(ENTERED_END);
        if (advancement == null || !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            KitUtil.actionBar(player, "You have to visit the End before you can break portal frames.");
            return false;
        }
        ServerLevel level = player.level();
        level.destroyBlock(pos, false);
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(Items.END_PORTAL_FRAME)));
        // Close the portal the frame was part of.
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 0, 4))) {
            if (level.getBlockState(near).is(Blocks.END_PORTAL)) level.removeBlock(near, false);
        }
        return true;
    }

    private static boolean hasEgg(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.DRAGON_EGG)) return true;
        }
        return false;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack));
        }
    }

    /** The egg never drops: remove any egg that just fell where she died. */
    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4),
                e -> e.getItem().is(Items.DRAGON_EGG) && e.tickCount < 5)) {
            item.discard();
        }
        GROUNDED.stop(player);
        TIRED.remove(player.getUUID());
        unlinkCrystal(player);
    }

    static void forget(ServerPlayer player) {
        GROUNDED.forget(player.getUUID());
        TIRED.remove(player.getUUID());
        unlinkCrystal(player);
    }

    // --- Crystal beam --------------------------------------------------------------

    /**
     * Keeps the nearest end crystal within range linked to her, beaming at her
     * chest. Looks for a nearer one every half second. Returns null when none.
     */
    private static EndCrystal linkCrystal(ServerPlayer player) {
        UUID id = player.getUUID();
        EndCrystal linked = LINKED_CRYSTAL.get(id);
        boolean stillGood = linked != null && linked.isAlive() && linked.level() == player.level()
                && linked.distanceTo(player) <= CRYSTAL_RANGE;
        if (!stillGood || player.tickCount % 10 == 0) {
            EndCrystal nearest = null;
            double best = Double.MAX_VALUE;
            for (EndCrystal crystal : player.level().getEntitiesOfClass(EndCrystal.class,
                    player.getBoundingBox().inflate(CRYSTAL_RANGE), EndCrystal::isAlive)) {
                // Leave alone crystals already beaming at something else, like a respawn pillar.
                if (crystal != linked && crystal.getBeamTarget() != null) continue;
                double distance = crystal.distanceTo(player);
                if (distance <= CRYSTAL_RANGE && distance < best) {
                    best = distance;
                    nearest = crystal;
                }
            }
            if (nearest != linked) {
                unlinkCrystal(player);
                if (nearest != null) LINKED_CRYSTAL.put(id, nearest);
                linked = nearest;
            }
        }
        if (linked != null) {
            BlockPos chest = BlockPos.containing(player.getX(), player.getY() + 0.8, player.getZ());
            if (!chest.equals(linked.getBeamTarget())) linked.setBeamTarget(chest);
        }
        return linked;
    }

    private static void unlinkCrystal(ServerPlayer player) {
        EndCrystal old = LINKED_CRYSTAL.remove(player.getUUID());
        if (old != null && old.isAlive()) old.setBeamTarget(null);
    }

    // --- Ultimate: Dragon's Fury ---------------------------------------------------------------
    /**
     * 5 dragon fireballs in a wide spread where she looks, and every enemy
     * within 10 blocks is blown far back.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Dragon's Fury";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        for (int i = -2; i <= 2; i++) {
            Vec3 dir = Vec3.directionFromRotation(player.getXRot(), player.getYRot() + i * 12);
            DragonFireball fireball = new DragonFireball(level, player, dir);
            fireball.setPos(eye.x + dir.x * 2, eye.y + dir.y * 2 - 0.3, eye.z + dir.z * 2);
            level.addFreshEntity(fireball);
        }
        for (LivingEntity enemy : UltimateFx.enemies(player, 10)) UltimateFx.throwFrom(enemy, player.position(), 2.0, 0.8);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0f, 1.0f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.5f, 0.8f);
        KitUtil.actionBar(player, "Dragon's Fury!");
        return true;
    }
}
