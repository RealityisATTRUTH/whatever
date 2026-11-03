package com.reality.mobcraft.item;

import com.mojang.serialization.Codec;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.effect.ModEffects;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * MobCraft's own items. Textures are 16x16 pixel art in
 * assets/mobcraft/textures/item, with item models next to them.
 *
 *   Bat Ear            left and right. The Bat drops one of them when he dies.
 *                      Each goes on its side of a helmet.
 *   Warden Tendril     left and right. The Warden drops one of them when he
 *                      dies. Each goes on its side of a helmet. Their names
 *                      are swapped from their ids: warden_tendril_left is
 *                      called the Right Warden Tendril, as seen facing the
 *                      wearer, and goes right of the helmet when crafting.
 *   Ender Pearl Shard  dropped when the Enderman dies. Goes on a chestplate.
 *   Creaking Splinter  dropped by creakings.
 *   Stalker Shard      crafted from an Ender Pearl Shard and a Creaking
 *                      Splinter. Goes on a helmet or a chestplate.
 *   Star Fragment      a broken point of the star. A player who kills the
 *                      Wither player gets 2.
 *   Fracture Nether Star  crafted from 4 Star Fragments, a nether star and 4
 *                      echo shards. Goes on a netherite sword in a crafting
 *                      grid, with a netherite upgrade template: see StarBlade.
 *   Curse Antidote     a bowl you eat to lift a curse. See CurseAntidoteItem.
 *
 * See ArmorUpgrades for what each one does.
 */
public final class ModItems {

    public static final Item BAT_EAR_LEFT = register("bat_ear_left");
    public static final Item BAT_EAR_RIGHT = register("bat_ear_right");
    public static final Item WARDEN_TENDRIL_LEFT = register("warden_tendril_left");
    public static final Item WARDEN_TENDRIL_RIGHT = register("warden_tendril_right");
    public static final Item ENDER_PEARL_SHARD = register("ender_pearl_shard");
    public static final Item CREAKING_SPLINTER = register("creaking_splinter");
    public static final Item STALKER_SHARD = register("stalker_shard");
    public static final Item NETHER_STAR_SHARD = register("nether_star_shard");
    public static final Item STAR_FRAGMENT = register("star_fragment");
    public static final Item CURSE_ANTIDOTE = registerAntidote();

    /** On a helmet or chestplate: which upgrade is attached, like "bat_ear_left". */
    public static final DataComponentType<String> ARMOR_UPGRADE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, MobCraft.id("armor_upgrade"),
            DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private ModItems() {}

    /** Eaten like stew: always edible, one at a time, gives the bowl back. */
    private static Item registerAntidote() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id("curse_antidote"));
        Item.Properties properties = new Item.Properties().setId(key).stacksTo(1)
                .food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6f).alwaysEdible().build())
                .usingConvertsTo(Items.BOWL);
        return Registry.register(BuiltInRegistries.ITEM, key, new CurseAntidoteItem(properties));
    }

    private static Item register(String name) {
        Identifier id = MobCraft.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    /** The MobCraft tab in the creative inventory. */
    public static final ResourceKey<CreativeModeTab> TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, MobCraft.id("items"));

    /** Called once at startup. Touching the class registers the items; this adds the creative tab. */
    public static void init() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mobcraft"))
                .icon(() -> new ItemStack(STALKER_SHARD))
                .displayItems((context, output) -> {
                    output.accept(BAT_EAR_LEFT);
                    output.accept(BAT_EAR_RIGHT);
                    output.accept(WARDEN_TENDRIL_RIGHT); // shows as the left one
                    output.accept(WARDEN_TENDRIL_LEFT);  // shows as the right one
                    output.accept(ENDER_PEARL_SHARD);
                    output.accept(CREAKING_SPLINTER);
                    output.accept(STALKER_SHARD);
                    output.accept(STAR_FRAGMENT);
                    output.accept(NETHER_STAR_SHARD);
                    output.accept(CURSE_ANTIDOTE);
                    output.accept(PotionContents.createItemStack(Items.SPLASH_POTION, ModEffects.CURSE_POTION));
                    output.accept(PotionContents.createItemStack(Items.LINGERING_POTION, ModEffects.CURSE_POTION));
                })
                .build());
    }
}
