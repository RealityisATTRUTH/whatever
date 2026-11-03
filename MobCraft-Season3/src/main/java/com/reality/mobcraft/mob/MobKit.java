package com.reality.mobcraft.mob;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * One mob's abilities and passives. Each mob gets its own class implementing
 * this, registered in MobKits. Every method has a default, so a kit only
 * overrides what it uses. KitEvents calls the combat and item hooks.
 */
public interface MobKit {

    /** Ability key slots. Players can rebind the keys in Controls. */
    int ABILITY_1 = 0;
    int ABILITY_2 = 1;
    int ABILITY_3 = 2;

    /**
     * Mobs that never attack this mob: the kit keeps them calm near you. If you
     * switch to another mob, these are the ones that turn on you (FormerMob).
     */
    default Set<EntityType<?>> allies() {
        return Set.of();
    }

    /** Runs when /setmob gives the player this mob. Apply scale, attributes and so on. */
    default void onAssigned(ServerPlayer player) {}

    /** Runs when the player loses this mob. Undo everything onAssigned and tick did. */
    default void onRemoved(ServerPlayer player) {}

    /**
     * Runs every server tick for each player with this mob. Passives are
     * re-applied here, so they come back after death and relogging.
     */
    default void tick(ServerPlayer player) {}

    /** Energy regenerated per second right now. Override for home buffs or drains. */
    default float energyRegenPerSecond(ServerPlayer player, MobType type) {
        return type.regenPerSecond;
    }

    /**
     * Runs when the player presses an ability key. Kits should check
     * MobAbilities.isEnabled(player, "ability_id") first, so abilities switched
     * off in /mobcraftsettings don't fire.
     */
    default void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        player.sendSystemMessage(Component.literal("No ability on that key yet."), true);
    }

    /**
     * Whether the player may give themselves this item right now. Use it for
     * rules like the Rabbit's curse blocking raw rabbit, or the Evoker's totems
     * only working out of combat.
     */
    default boolean canSelfGive(ServerPlayer player, String itemId) {
        return true;
    }

    /** Lets a kit change a self-given item, like enchanting the Skeleton's bow. */
    default void customizeSelfGive(ServerPlayer player, String itemId, ItemStack stack) {}

    /** Runs when the player's curse starts and ends. */
    default void onCursed(ServerPlayer player) {}
    default void onCurseEnded(ServerPlayer player) {}

    // --- Combat hooks ---------------------------------------------------------

    /** Before this player damages something. Return false to cancel the hit. */
    default boolean allowAttack(ServerPlayer player, LivingEntity victim, DamageSource source) {
        return true;
    }

    /** After this player damaged something. */
    default void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {}

    /** After this player took damage from anything. */
    default void afterHurt(ServerPlayer player, DamageSource source, float damage) {}

    /** Before this player takes damage. Return false to cancel it, like the Iron Golem's fall damage. */
    default boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        return true;
    }

    /** Changes damage this player deals. */
    default float modifyOutgoingDamage(ServerPlayer player, LivingEntity victim, DamageSource source, float amount) {
        return amount;
    }

    /** Changes damage this player takes. */
    default float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        return amount;
    }

    /** The player died. Runs after their normal death drops. */
    default void onDeath(ServerPlayer player, DamageSource source) {}

    /** The player right-clicked a block. Return PASS to let vanilla handle it. */
    default InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** A projectile this player fired just entered the world. */
    default void onProjectileFired(ServerPlayer player, Projectile projectile) {}

    /** The player right-clicked with an item. Return PASS to let vanilla handle it. */
    default InteractionResult onUseItem(ServerPlayer player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // --- Ultimate -------------------------------------------------------------

    /** Whether this mob has an ultimate ability. See Ultimate. */
    default boolean hasUltimate() {
        return false;
    }

    /** The ultimate's name, for messages. */
    default String ultimateName() {
        return "Ultimate";
    }

    /** Fires the ultimate. Return true if it went off, which uses up the charge. */
    default boolean onUltimate(ServerPlayer player) {
        return false;
    }

    // --- Traits ---------------------------------------------------------------

    /** Undead mobs are healed by Instant Damage and hurt by Instant Health. */
    default boolean isUndead() {
        return false;
    }

    /** Skips the death screen. Read on the client too. */
    default boolean instantRespawn() {
        return false;
    }
}
