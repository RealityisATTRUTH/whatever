package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;

import java.util.Set;

/**
 * Piglin (Coolclubvr).
 *
 *   Passive       Strength I. Inside a Bastion Remnant: Strength II and Resistance I.
 *   Allies        piglins and piglin brutes, so mining gold never angers them
 *   Gold Rush     R: Speed II for 8s. 20 energy, 30s cooldown.
 *   Crossbow      G: a crossbow for 1:00 with Piercing V, Multishot I and Curse
 *                 of Vanishing. It vanishes if you die. 2:00 cooldown after it's gone.
 *   Curse         Zombification: no Strength, can't self-give gold or crying obsidian
 */
public final class PiglinKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.PIGLIN, EntityTypes.PIGLIN_BRUTE);

    private static final String GOLD_RUSH = "gold_rush";
    private static final String CROSSBOW = "crossbow";
    private static final String CROSSBOW_NAME = "Piglin's Crossbow";
    private static final int CROSSBOW_TICKS = 1200;   // 1:00
    private static final int CROSSBOW_COOLDOWN = 2400; // 2:00 after it vanishes

    private static final KitUtil.Flags IN_BASTION = new KitUtil.Flags();
    private static final KitUtil.Timers CROSSBOW_TIME = new KitUtil.Timers();
    private static final KitUtil.Flags HAS_CROSSBOW = new KitUtil.Flags();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        boolean cursed = PlayerMob.isCursed(player);
        if (player.tickCount % 20 == 0) {
            IN_BASTION.set(player, KitUtil.inStructure(player, BuiltinStructures.BASTION_REMNANT));
        }
        boolean bastion = IN_BASTION.is(player);

        KitUtil.passive(player, MobEffects.STRENGTH, bastion ? 1 : 0, !cursed);
        KitUtil.passive(player, MobEffects.RESISTANCE, 0, bastion && !cursed);

        KitUtil.calmAllies(player, 32, ALLIES);

        // The crossbow's minute is up.
        if (HAS_CROSSBOW.is(player) && !CROSSBOW_TIME.active(player)) {
            HAS_CROSSBOW.forget(player);
            removeCrossbows(player);
            Cooldowns.start(player, CROSSBOW, CROSSBOW_COOLDOWN);
            KitUtil.actionBar(player, "Your crossbow crumbled away.");
        }
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.STRENGTH, 0, false);
        KitUtil.passive(player, MobEffects.RESISTANCE, 0, false);
        removeCrossbows(player);
        HAS_CROSSBOW.forget(player);
        CROSSBOW_TIME.forget(player.getUUID());
        forget(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (!KitUtil.tryUse(player, GOLD_RUSH, "Gold Rush", 20, 600)) return;
            KitUtil.effect(player, MobEffects.SPEED, 160, 1);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else if (slot == ABILITY_2) {
            if (HAS_CROSSBOW.is(player)) {
                KitUtil.actionBar(player, "You already have your crossbow.");
                return;
            }
            // Cooldown is set when the crossbow vanishes, so start none here.
            if (!KitUtil.tryUse(player, CROSSBOW, "Crossbow", 0, 0)) return;
            ItemStack crossbow = new ItemStack(Items.CROSSBOW, 1);
            crossbow.set(DataComponents.CUSTOM_NAME, Component.literal(CROSSBOW_NAME));
            KitUtil.enchant(player, crossbow, Enchantments.PIERCING, 5);
            KitUtil.enchant(player, crossbow, Enchantments.MULTISHOT, 1);
            KitUtil.enchant(player, crossbow, Enchantments.VANISHING_CURSE, 1);
            if (!player.getInventory().add(crossbow)) {
                KitUtil.actionBar(player, "Make room in your inventory first.");
                return;
            }
            HAS_CROSSBOW.set(player, true);
            CROSSBOW_TIME.start(player, CROSSBOW_TICKS);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.PLAYERS, 1.0f, 1.0f);
            KitUtil.actionBar(player, "Crossbow for 1 minute.");
        } else {
            KitUtil.actionBar(player, "The Piglin has no third ability.");
        }
    }

    @Override
    public boolean canSelfGive(ServerPlayer player, String itemId) {
        return !PlayerMob.isCursed(player);
    }

    private static void removeCrossbows(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.CROSSBOW) && CROSSBOW_NAME.equals(stack.getHoverName().getString())) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    static void forget(ServerPlayer player) {
        IN_BASTION.forget(player);
        // Keep HAS_CROSSBOW so a relog still takes the crossbow away.
    }

    // --- Ultimate: Bastion Rally ---------------------------------------------------------------
    /**
     * Strength II, Resistance I and Speed II for 20s, and 3 piglin brutes
     * fight for you for 45s. They don't zombify and take RTS orders.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Bastion Rally";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        KitUtil.effect(player, MobEffects.STRENGTH, 400, 1);
        KitUtil.effect(player, MobEffects.RESISTANCE, 400, 0);
        KitUtil.effect(player, MobEffects.SPEED, 400, 1);
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI * 2 * i / 3;
            UltimateFx.summonFor(player, "minecraft:piglin_brute", player.getX() + Math.cos(angle) * 2, player.getY(),
                    player.getZ() + Math.sin(angle) * 2, "IsImmuneToZombification:1b", 900);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.PLAYERS, 1.5f, 1.0f);
        KitUtil.actionBar(player, "Bastion Rally!");
        return true;
    }
}
