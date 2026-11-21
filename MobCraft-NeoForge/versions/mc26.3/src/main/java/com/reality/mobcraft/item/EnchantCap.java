package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * How many MobCraft enchants one item can hold. Vanilla enchants don't count and stay unlimited.
 *
 *   Mace, trident, helmet, chestplate, leggings   1
 *   Sword, axe, bow, crossbow, boots              2
 *
 * Unbinding Severance doesn't count: it's a tool for cutting Stronghold Tome chains. Books have no cap.
 * The anvil won't add one past the cap, and Duskvein won't awaken one past it either. An item that already holds
 * more keeps them all; it just can't take another.
 */
public final class EnchantCap {
    private EnchantCap() {}

    public static int cap(ItemStack stack) {
        if (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK)) return Integer.MAX_VALUE;
        if (stack.is(ItemTags.MACE_ENCHANTABLE)) return 1;
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.BOW_ENCHANTABLE)
                || stack.is(ItemTags.CROSSBOW_ENCHANTABLE) || stack.is(ItemTags.FOOT_ARMOR_ENCHANTABLE)) return 2;
        return 1;
    }

    /** The MobCraft enchants on the item that count toward its cap. */
    public static int count(ItemStack stack) {
        int n = 0;
        for (ItemEnchantments e : new ItemEnchantments[]{stack.get(DataComponents.ENCHANTMENTS), stack.get(DataComponents.STORED_ENCHANTMENTS)}) {
            if (e == null) continue;
            for (Holder<Enchantment> h : e.keySet()) if (counts(h)) n++;
        }
        return n;
    }

    private static boolean counts(Holder<Enchantment> h) {
        return h.unwrapKey().map(k -> k.identifier().getNamespace().equals(MobCraft.MOD_ID)
                && !k.identifier().getPath().equals("unbinding_severance")).orElse(false);
    }

    /** True when the item has no room for another MobCraft enchant. */
    public static boolean full(ItemStack stack) {
        return count(stack) >= cap(stack);
    }
}
