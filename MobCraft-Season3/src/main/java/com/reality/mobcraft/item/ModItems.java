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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;

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
 * Mob items: one for each mob that didn't have one, dropped when another player
 * kills that mob. Some go on armor, some are eaten, some are brewed. See MobRelics.
 *
 * See ArmorUpgrades for what each armor one does.
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

    // --- Mob items: one per mob, dropped when another player kills that mob. See MobRelics. ---
    // Armor: attach like the other armor upgrades. See ArmorUpgrades.
    public static final Item LUCKY_PAW = registerMobItem("lucky_paw", "Rabbit", "Put it on a helmet in a crafting grid", "Armor Power: turn into a rabbit. Mobs ignore you");           // Rabbit Ears (id from the old Lucky Paw), helmet. See RabbitEars.
    public static final Item BREEZE_CORE = registerMobItem("breeze_core", "Breeze", "Put it on boots in a crafting grid", "No fall damage");       // Breeze, boots
    public static final Item IRON_HEART = registerMobItem("iron_heart", "Iron Golem", "Put it on a chestplate in a crafting grid", "Half knockback");         // Iron Golem Head (id kept as iron_heart), chestplate
    public static final Item CREEPER_FUSE = registerEssence("creeper_fuse", "Creeper", CreeperEssence.CREEPER,
            "Right-click to absorb it", "B: charge what you hold and throw it. Sneak+B: blow yourself up");     // Creeper Essence
    public static final Item SKULL_VISOR = registerSkullVisor();   // Skeleton: a helmet of its own. See SkullVisor.
    public static final Item ILLUSION_LENS = registerMobItem("illusion_lens", "Illusioner", "Put it on a helmet in a crafting grid", "Blindness and Darkness can't affect you");   // Illusioner, helmet
    public static final Item ELDER_SCALE = registerMobItem("elder_scale", "Elder Guardian", "Put it on a chestplate in a crafting grid", "Spikes: hit back anyone who hits you up close");       // Elder Guardian, chestplate
    // Eaten.
    public static final Item PIGLIN_TRUFFLE = registerFood("piglin_truffle", "Piglin", "Strength and Absorption II for 2:00",
            new MobEffectInstance(MobEffects.STRENGTH, 2400, 0), new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
    public static final Item SILVERFISH_GRUB = registerFood("silverfish_grub", "Silverfish", "Haste II for 3:00",
            new MobEffectInstance(MobEffects.HASTE, 3600, 1));
    public static final Item VILLAGER_BREAD = registerFood("villager_bread", "Villager", "Hero of the Village for 5:00",
            new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, 0));
    public static final Item AXOLOTL_ROE = registerFood("axolotl_roe", "Axolotl", "Water Breathing, Regeneration, Dolphin's Grace",
            new MobEffectInstance(MobEffects.WATER_BREATHING, 6000, 0), new MobEffectInstance(MobEffects.REGENERATION, 600, 0),
            new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 1200, 0));
    // Brewed: Awkward Potion + item. See MobRelics for the potions, data/mobcraft/recipe/brewing for the recipes.
    /** Thrown like a wind charge, or brewed. See GhastWisp. */
    public static final Item GHAST_WISP = registerGhastWisp();
    public static final Item BLAZE_EMBER = registerMobItem("blaze_ember", "Blaze", "Brew it into an Awkward Potion", "Potion of Blazing: Strength, Fire Resistance");
    public static final Item VEX_WING = registerMobItem("vex_wing", "Vex", "Put it on a chestplate in a crafting grid. Not with Mending",
            "Fly like the Vex for 3 energy a second. Kill allays to repair it");
    // Absorbed: right-click it, then use it with the Essence Power key. See ShulkerEssence.
    public static final Item SHULKER_ESSENCE = registerEssence("shulker_essence", "Shulker", ShulkerEssence.SHULKER,
            "Right-click to absorb it", "Then press B: a shell that blocks all damage");
    public static final Item DRAGON_ESSENCE = registerEssence("dragon_essence", "Ender Dragon", DragonEssence.DRAGON,
            "Right-click to absorb it", "Then press B: Absorption IV, Regeneration II, Resistance");
    /** The Ender Dragon's old mob item, eaten before. Kept so worlds that have one still load; now a Dragon Essence. */
    public static final Item LEGACY_DRAGON_HEART = registerEssence("dragon_heart", "Ender Dragon", DragonEssence.DRAGON,
            "Right-click to absorb it", "Then press B: Absorption IV, Regeneration II, Resistance");
    /**
     * The Shulker's old mob item, from 0.2.19. Kept so worlds that have one still
     * load. It works exactly like a Shulker Essence and isn't in the creative tab.
     */
    public static final Item LEGACY_SHULKER_SCALE = registerEssence("shulker_scale", "Shulker", ShulkerEssence.SHULKER,
            "Right-click to absorb it", "Then press B: a shell that blocks all damage");
    public static final Item BROKEN_TOTEM = registerEssence("broken_totem", "Evoker", EvokerEssence.EVOKER,
            "Right-click to absorb it. 2 uses", "B: swap places. Sneak + B: chain your next hit");
    /** The Evoker's old mob item, brewed before. Kept so worlds that have one still load; now a Broken Totem. */
    public static final Item LEGACY_EVOKER_FANG = registerEssence("evoker_fang", "Evoker", EvokerEssence.EVOKER,
            "Right-click to absorb it. 2 uses", "B: swap places. Sneak + B: chain your next hit");

    /** On a chestplate with a Vex Wing: the wing's own durability, 0 when broken. See VexWing. */
    public static final DataComponentType<Integer> VEX_WING_DURABILITY = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, MobCraft.id("vex_wing_durability"),
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

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

    /** The two tooltip lines every mob item has: whose it is, and what to do with it. */
    private static ItemLore mobItemLore(String mob, String how, String does) {
        return new ItemLore(List.of(
                Component.literal(mob + "'s mob item").withStyle(ChatFormatting.GOLD),
                Component.literal(how).withStyle(ChatFormatting.GRAY),
                Component.literal(does).withStyle(ChatFormatting.DARK_AQUA)));
    }

    /** A mob essence you absorb by right-clicking it. */
    private static Item registerEssence(String name, String mob, String essence, String how, String does) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new ShulkerEssence.EssenceItem(new Item.Properties().setId(key)
                .stacksTo(16).component(DataComponents.LORE, mobItemLore(mob, how, does)), essence));
    }

    /** A mob item for armor or brewing. */
    private static Item registerMobItem(String name, String mob, String how, String does) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
                .component(DataComponents.LORE, mobItemLore(mob, how, does))));
    }

    /** The Skull Visor: a helmet you wear, with its own look and a view through the skull's eye holes. */
    private static Item registerSkullVisor() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id("skull_visor"));
        ItemLore lore = new ItemLore(List.of(
                Component.literal("Skeleton's mob item").withStyle(ChatFormatting.GOLD),
                Component.literal("Wear it as a helmet").withStyle(ChatFormatting.GRAY),
                Component.literal("Skeletons, strays, bogged and wither skeletons can't hurt you").withStyle(ChatFormatting.DARK_AQUA),
                Component.literal("Projectiles hurt 40% less. Bows draw 25% faster").withStyle(ChatFormatting.DARK_AQUA),
                Component.literal("You burn in sunlight and see only through the slits").withStyle(ChatFormatting.RED)));
        Item.Properties properties = new Item.Properties().setId(key)
                .humanoidArmor(SkullVisor.MATERIAL, net.minecraft.world.item.equipment.ArmorType.HELMET)
                .component(DataComponents.EQUIPPABLE, net.minecraft.world.item.equipment.Equippable.builder(net.minecraft.world.entity.EquipmentSlot.HEAD)
                        .setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC)
                        // No armor layer: worn, the game draws the item's own 3D model on the head, like a carved pumpkin.
                        .setCameraOverlay(MobCraft.id("misc/skull_visor_overlay"))
                        .build())
                .component(DataComponents.LORE, lore);
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
    }

    private static Item registerGhastWisp() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id("ghast_wisp"));
        ItemLore lore = new ItemLore(List.of(
                Component.literal("Ghast's mob item").withStyle(ChatFormatting.GOLD),
                Component.literal("Throw it: a little fireball that bursts into wind").withStyle(ChatFormatting.GRAY),
                Component.literal("Sets what it hits on fire and pushes everything away").withStyle(ChatFormatting.DARK_AQUA),
                Component.literal("Or brew it: Potion of Drifting").withStyle(ChatFormatting.GRAY)));
        return Registry.register(BuiltInRegistries.ITEM, key, new GhastWisp.WispItem(new Item.Properties().setId(key)
                .stacksTo(16).component(DataComponents.LORE, lore)));
    }

    /** A mob item you eat: always edible, gives its effects, stacks to 16. */
    private static Item registerFood(String name, String mob, String does, MobEffectInstance... effects) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id(name));
        Item.Properties properties = new Item.Properties().setId(key).stacksTo(16)
                .component(DataComponents.LORE, mobItemLore(mob, "Eat it", does))
                .food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6f).alwaysEdible().build(),
                        Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(effects))).build());
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
    }

    private static Item register(String name) {
        Identifier id = MobCraft.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    /** A rare book from stronghold libraries that opens with an animation. See StrongholdTome. */
    public static final Item STRONGHOLD_TOME = StrongholdTome.create();
    /** Lapis from the Deep Dark: pushes enchants past their max in an enchanting table. See Duskvein. */
    public static final Item DUSKVEIN = Duskvein.create();

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
                    output.accept(STRONGHOLD_TOME);
                    output.accept(DUSKVEIN);
                    output.accept(Duskvein.ORE_ITEM);
                    Gloamite.addToTab(output);
                    output.accept(DuskveinAnvil.ITEM);
                    output.accept(PotionContents.createItemStack(Items.SPLASH_POTION, ModEffects.CURSE_POTION));
                    output.accept(PotionContents.createItemStack(Items.LINGERING_POTION, ModEffects.CURSE_POTION));
                    for (Item item : List.of(LUCKY_PAW, BREEZE_CORE, IRON_HEART, CREEPER_FUSE, SHULKER_ESSENCE, SKULL_VISOR,
                            ILLUSION_LENS, ELDER_SCALE, PIGLIN_TRUFFLE, SILVERFISH_GRUB, VILLAGER_BREAD, AXOLOTL_ROE,
                            DRAGON_ESSENCE, GHAST_WISP, BLAZE_EMBER, VEX_WING, BROKEN_TOTEM)) output.accept(item);
                    for (var potion : MobRelics.POTIONS) output.accept(PotionContents.createItemStack(Items.POTION, potion));
                })
                .build());
    }
}
