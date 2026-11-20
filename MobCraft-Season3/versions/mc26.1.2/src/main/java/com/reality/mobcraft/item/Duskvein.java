package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Duskvein: a rare gem from the very bottom of the world. Duskvein Ore forms only in the last few layers above
 * bedrock (y -63 to -57), in small veins of 1 or 2 blocks, in about 1 chunk out of 3, and many tries land in bedrock
 * and place nothing, so it works out to about 5 veins in a 5 by 5 chunk square (tested: 20 blocks in 10 by 10 chunks at 1 in 4). It needs an iron pickaxe or better; Silk Touch keeps the ore, Fortune can give more.
 * Put it in an enchanting table's lapis slot instead of lapis and every enchant that comes out at its normal max
 * has a chance to go past it: one level more (Unbreaking IV), and now and then two (Unbreaking V). Not guaranteed.
 * Villagers never sell anything past the normal max, and anvils keep these levels when you combine items but never
 * raise anything past the max themselves (AnvilKeepLevelsMixin).
 */
public final class Duskvein {

    public static final String NAME = "duskvein";
    private static final float PLUS_ONE = 0.35f;         // chance a maxed enchant goes one past
    private static final float PLUS_TWO = 0.25f;         // of those, chance it goes two past
    // Gloamite gear takes the greater enchants more easily: it came from Duskvein.
    private static final float GLOAMITE_PLUS_ONE = 0.6f;
    private static final float GLOAMITE_PLUS_TWO = 0.45f;

    private Duskvein() {}

    static Item create() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id(NAME));
        ItemLore lore = new ItemLore(List.of(
                Component.literal("Mined just above bedrock").withStyle(ChatFormatting.GRAY),
                Component.literal("Use it in an enchanting table instead of lapis").withStyle(ChatFormatting.DARK_AQUA),
                Component.literal("Enchants can go past their normal max. Not guaranteed").withStyle(ChatFormatting.DARK_AQUA)));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
                .rarity(Rarity.RARE).component(net.minecraft.core.component.DataComponents.LORE, lore)));
    }

    /** The ore. Hardness 200, so even Efficiency V netherite with Haste III takes over 5 seconds; blast proof like obsidian, so TNT and explosions can't cheat it out. Needs an iron pickaxe. */
    public static final ResourceKey<Block> ORE_KEY = ResourceKey.create(Registries.BLOCK, MobCraft.id("duskvein_ore"));
    public static final Block ORE = Registry.register(BuiltInRegistries.BLOCK, ORE_KEY,
            new net.minecraft.world.level.block.DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(4, 8),
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().setId(ORE_KEY)
                            .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
                            .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASEDRUM)
                            .requiresCorrectToolForDrops().strength(200.0f, 1200.0f)
                            .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)));
    private static final ResourceKey<Item> ORE_ITEM_KEY = ResourceKey.create(Registries.ITEM, MobCraft.id("duskvein_ore"));
    public static final Item ORE_ITEM = Registry.register(BuiltInRegistries.ITEM, ORE_ITEM_KEY,
            new net.minecraft.world.item.BlockItem(ORE, new Item.Properties().setId(ORE_ITEM_KEY).useBlockDescriptionPrefix()));

    /** The ore's world-gen (data/mobcraft/worldgen): bottom few layers of every Overworld biome. */
    private static final ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, MobCraft.id("ore_duskvein"));

    public static void register() {
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES, PLACED);
    }

    /**
     * The enchanting table used a Duskvein: each enchant on the result that's at its normal max may go past it.
     * Curses and one-level enchants (Mending, Silk Touch) stay as they are.
     */
    public static void empower(ItemStack result, Player player, RandomSource random) {
        if (result.isEmpty()) return;
        boolean gloamite = Gloamite.isGloamite(result);
        int[] raised = {0};
        EnchantmentHelper.updateEnchantments(result, enchants -> {
            for (Holder<Enchantment> e : List.copyOf(enchants.keySet())) {
                int level = enchants.getLevel(e), max = e.value().getMaxLevel();
                if (max <= 1 || level < max || e.is(EnchantmentTags.CURSE) || e.is(net.minecraft.world.item.enchantment.Enchantments.BREACH)) continue;
                if (random.nextFloat() >= (gloamite ? GLOAMITE_PLUS_ONE : PLUS_ONE)) continue;
                int extra = random.nextFloat() < (gloamite ? GLOAMITE_PLUS_TWO : PLUS_TWO) ? 2 : 1;
                enchants.set(e, max + extra);
                raised[0]++;
            }
        });
        MaceEnchants.awaken(result, player, random);
        GearEnchants.awaken(result, player, random);
        if (raised[0] > 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.0f, 1.2f);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.8f);
            player.sendSystemMessage(Component.literal("The Duskvein pushed " + (raised[0] == 1 ? "an enchant" : raised[0] + " enchants")
                    + " past its limit.").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** After an anvil combine: any enchant that was already past its max on an input keeps that level. */
    /** True when any enchant on the item is past its normal max. */
    public static boolean hasGreater(ItemStack stack) {
        ItemEnchantments e = stack.getOrDefault(EnchantmentHelper.getComponentType(stack), ItemEnchantments.EMPTY);
        for (var entry : e.entrySet()) if (entry.getIntValue() > entry.getKey().value().getMaxLevel()) return true;
        return false;
    }

    /** At the Duskvein Anvil: two equal Wind Burst levels from III up merge one level higher, up to the cap. */
    public static void mergeWindBurst(ItemStack left, ItemStack right, ItemStack result, int cap) {
        ItemEnchantments a = left.getOrDefault(EnchantmentHelper.getComponentType(left), ItemEnchantments.EMPTY);
        ItemEnchantments b = right.getOrDefault(EnchantmentHelper.getComponentType(right), ItemEnchantments.EMPTY);
        EnchantmentHelper.updateEnchantments(result, enchants -> {
            for (Holder<Enchantment> e : List.copyOf(enchants.keySet())) {
                if (!e.is(net.minecraft.world.item.enchantment.Enchantments.WIND_BURST)) continue;
                int la = a.getLevel(e), lb = b.getLevel(e);
                if (la == lb && la >= e.value().getMaxLevel() && la < cap) enchants.set(e, la + 1);
            }
        });
    }

    public static void keepLevels(ItemStack left, ItemStack right, ItemStack result) {
        if (result.isEmpty()) return;
        ItemEnchantments a = left.getOrDefault(EnchantmentHelper.getComponentType(left), ItemEnchantments.EMPTY);
        ItemEnchantments b = right.getOrDefault(EnchantmentHelper.getComponentType(right), ItemEnchantments.EMPTY);
        EnchantmentHelper.updateEnchantments(result, enchants -> {
            for (Holder<Enchantment> e : List.copyOf(enchants.keySet())) {
                int best = Math.max(a.getLevel(e), b.getLevel(e));
                if (best > e.value().getMaxLevel() && best > enchants.getLevel(e)) enchants.set(e, best);
            }
        });
    }

    public static boolean is(ItemStack stack) {
        return stack.is(ModItems.DUSKVEIN);
    }
}
