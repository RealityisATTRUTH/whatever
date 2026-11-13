package com.reality.mobcraft.item;

import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vex Wing: the Vex's mob item, attached to a chestplate in a crafting grid like the other
 * armor upgrades (ArmorUpgrades). Wearing it, you can fly like the Vex.
 *
 *   Flight      like the Vex: double-tap jump, about walking speed
 *   Energy      flying costs 3 energy a second, 50% more than the Vex's Phase (2 a
 *               second). With energy coming back at 1 a second that's about 50
 *               seconds of flight from full. At 0 you drop and can't take off again
 *               until you're back to 10. Players without a mob have no energy and fly free.
 *   Durability  216, half an elytra's. Flying uses 1 a second, like an elytra gliding.
 *               At 0 the wing is broken: it stays on the chestplate but you can't fly
 *               until it's repaired. Kept on the chestplate as its own number
 *               (ModItems.VEX_WING_DURABILITY), so the chestplate's own durability,
 *               Unbreaking and Mending don't touch it.
 *   Repair      only by killing allays while wearing it: 54 durability each, so four
 *               allays take a broken wing back to full
 *   No Mending  a chestplate with Mending can't take a Vex Wing
 *
 * Mobs that fly on their own (Bat, Blaze, Vex, Wither, Ghast, Ender Dragon) already fly,
 * so for them the wing does nothing: no flight from it, no energy cost, no wear.
 */
public final class VexWing {

    public static final int MAX_DURABILITY = 216;     // half an elytra's 432
    private static final int ALLAY_REPAIR = 54;
    private static final float FLY_SPEED = 0.02f;     // the Vex's own
    private static final float FLY_DRAIN = 3f;        // energy a second while flying
    private static final float TAKE_OFF = 10f;        // energy needed to fly again after running out
    private static final int LOW_WARNING = 30;

    private static final Set<MobType> NATURAL_FLYERS = EnumSet.of(MobType.BAT, MobType.BLAZE, MobType.VEX,
            MobType.WITHER, MobType.GHAST, MobType.ENDER_DRAGON);

    /** Players the wing is currently letting fly. */
    private static final Set<UUID> FLYING = new HashSet<>();
    /** Players who ran out of energy, until they're back to TAKE_OFF. */
    private static final Set<UUID> GROUNDED = new HashSet<>();

    private VexWing() {}

    public static void register() {
        // Killing an allay while wearing the wing repairs it.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getType() != EntityTypes.ALLAY || !(source.getEntity() instanceof ServerPlayer player)) return;
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!ArmorUpgrades.VEX_WING.equals(chest.get(ModItems.ARMOR_UPGRADE))) return;
            int before = durability(chest);
            if (before >= MAX_DURABILITY) {
                KitUtil.actionBar(player, "Your Vex Wing is already whole.");
                return;
            }
            setDurability(chest, Math.min(MAX_DURABILITY, before + ALLAY_REPAIR));
            player.level().playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1f, 0.8f);
            KitUtil.actionBar(player, "Vex Wing repaired: " + durability(chest) + " / " + MAX_DURABILITY
                    + (before == 0 ? ". You can fly again." : "."));
        });
    }

    /** Wearing a chestplate with a Vex Wing, broken or not. */
    public static boolean wearing(Player player) {
        return ArmorUpgrades.VEX_WING.equals(player.getItemBySlot(EquipmentSlot.CHEST).get(ModItems.ARMOR_UPGRADE));
    }

    public static int durability(ItemStack chest) {
        Integer d = chest.get(ModItems.VEX_WING_DURABILITY);
        return d == null ? MAX_DURABILITY : d;
    }

    /** Sets the wing's durability and the tooltip line that shows it. */
    public static void setDurability(ItemStack chest, int value) {
        chest.set(ModItems.VEX_WING_DURABILITY, Math.max(0, Math.min(MAX_DURABILITY, value)));
        ItemLore lore = chest.get(DataComponents.LORE);
        List<Component> lines = new ArrayList<>(lore == null ? List.of() : lore.lines());
        lines.removeIf(line -> line.getString().startsWith(" Wing: "));
        int d = durability(chest);
        lines.add(Component.literal(" Wing: " + (d == 0 ? "broken. Kill allays to repair it" : d + " / " + MAX_DURABILITY))
                .withStyle(d == 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
        chest.set(DataComponents.LORE, new ItemLore(lines));
    }

    /** A chestplate with Mending can't take the wing. */
    public static boolean hasMending(ItemStack armor) {
        ItemEnchantments enchantments = armor.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(Enchantments.MENDING)) return true;
        }
        return false;
    }

    /** Every tick, from ArmorUpgrades.tick. */
    public static void tick(ServerPlayer player) {
        MobType mob = PlayerMob.get(player);
        boolean flyer = mob != null && NATURAL_FLYERS.contains(mob);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wing = wearing(player) && !flyer;
        boolean working = wing && durability(chest) > 0;
        boolean usesEnergy = mob != null && !player.isCreative();

        // Out of energy: grounded until there's enough to take off again.
        if (working && usesEnergy) {
            float energy = PlayerMob.energy(player);
            // Regen tops it up a little each tick, so "empty" is under one tick of drain.
            if (energy < FLY_DRAIN / 20f && GROUNDED.add(player.getUUID())) {
                KitUtil.actionBar(player, "Out of energy. Your Vex Wing can't hold you.");
            } else if (energy >= TAKE_OFF) {
                GROUNDED.remove(player.getUUID());
            }
            if (GROUNDED.contains(player.getUUID())) working = false;
        } else {
            GROUNDED.remove(player.getUUID());
        }

        if (working) {
            if (usesEnergy && player.getAbilities().flying) {
                PlayerMob.setEnergy(player, PlayerMob.energy(player) - FLY_DRAIN / 20f);
            }
            FLYING.add(player.getUUID());
            KitUtil.flight(player, true, FLY_SPEED);
            // Flying wears it down, one a second, like an elytra.
            if (player.getAbilities().flying && !player.isCreative() && player.tickCount % 20 == 0) {
                int left = durability(chest) - 1;
                setDurability(chest, left);
                if (left == 0) {
                    KitUtil.flight(player, false, FLY_SPEED);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1f, 1.2f);
                    KitUtil.actionBar(player, "Your Vex Wing broke! Kill allays to repair it.");
                } else if (left == LOW_WARNING) {
                    KitUtil.actionBar(player, "Your Vex Wing is about to break.");
                }
            }
        } else if (FLYING.remove(player.getUUID()) && !flyer) {
            // Took it off, it broke, or out of energy: no more flying. A flying mob's own kit handles its flight.
            KitUtil.flight(player, false, FLY_SPEED);
        }
    }

    public static void forget(ServerPlayer player) {
        FLYING.remove(player.getUUID());
        GROUNDED.remove(player.getUUID());
    }
}
