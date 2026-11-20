package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The Duskvein Anvil: the stronger anvil the greater enchants need.
 *
 *  - A normal anvil can't work anything that has an enchant past its max level. Use this one instead.
 *  - It keeps levels past the max when you combine or repair.
 *  - Two Wind Burst III merge into Wind Burst IV here, and two IV into V.
 *  - It's never "Too Expensive": the cost tops out at 39 levels.
 *  - It never chips or breaks from use.
 *
 * Recipe: 3 Duskvein on top, an anvil in the middle, 3 Rose Gold Ingots on the bottom.
 * Its look is the game's own anvil, tinted twilight purple as the game loads its textures.
 */
public final class DuskveinAnvil {

    public static final int WIND_BURST_CAP = 5;

    private DuskveinAnvil() {}

    public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, MobCraft.id("duskvein_anvil"));
    public static final Block BLOCK = Registry.register(BuiltInRegistries.BLOCK, KEY,
            new AnvilBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ANVIL).setId(KEY)));
    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, MobCraft.id("duskvein_anvil"));
    public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
            new BlockItem(BLOCK, new Item.Properties().setId(ITEM_KEY).useBlockDescriptionPrefix()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static void register() {}
}
