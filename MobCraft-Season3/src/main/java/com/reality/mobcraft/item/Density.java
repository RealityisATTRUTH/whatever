package com.reality.mobcraft.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How dense an item is, for the Creeper Essence: denser things blow up harder.
 *
 * Minecraft logic: density = mass / volume, and anything you hold takes up one block, 1 m³. So an item's density is
 * its mass in kg, and its mass is what went into making it:
 *   A block of a real material is that material:   stone 2,500, an oak log 600, an iron block 7,874.
 *   Split it and it splits the mass:                an iron ingot is 1/9 of the block, 874.9; a plank 1/4 log, 150.
 *   Craft something and it weighs its ingredients:  a bucket is 3 ingots, 2,624.7; an iron sword 2 ingots + a stick.
 *   Water is 0.875 m tall, so a source is 875 kg:   ice is 875. Packed ice is 9 ice, 7,875. Blue ice 9 packed.
 *   A water bucket is the bucket and the water:     2,624.7 + 875 = 3,499.7.
 *   Two water buckets make an infinite source, infinite mass in 4 m³: infinite density. Carry two into a last
 *   stand and you're a singularity.
 * Anything not worked out is 1,000, like water.
 *
 * Packed ice, 7,875, is a creeper's blast. Lighter is weaker, denser is stronger. Every blast breaks blocks as far
 * as its power reaches, against each block's own blast resistance, like TNT.
 */
public final class Density {

    public static final double ICE = 875;
    public static final double PACKED_ICE = 9 * ICE;            // 7,875: the line
    public static final double BLUE_ICE = 9 * PACKED_ICE;       // 70,875
    public static final double HUMAN = 985;                     // an empty inventory: just you
    public static final double WATER = 875;                     // one source block, 0.875 m tall

    // Blocks of real materials, kg in 1 m³
    private static final double IRON_BLOCK = 7874, GOLD_BLOCK = 19300, COPPER_BLOCK = 8960, DIAMOND_BLOCK = 3510,
            EMERALD_BLOCK = 2760, LAPIS_BLOCK = 2800, REDSTONE_BLOCK = 5300, COAL_BLOCK = 1350, RAW_IRON_BLOCK = 5150,
            RAW_GOLD_BLOCK = 12000, RAW_COPPER_BLOCK = 4600, DEBRIS = 2400, LOG = 600, STONE = 2500, GLASS = 2500,
            SAND = 1600, QUARTZ_BLOCK = 2650, AMETHYST_BLOCK = 2650, CLAY_BLOCK = 1800, BRICKS = 1900,
            BONE_BLOCK = 1900, SLIME_BLOCK = 1100, HONEY_BLOCK = 1420, HAY = 200, KELP_BLOCK = 300, SNOW_BLOCK = 400,
            LAVA = 3100 * 0.875, MILK = 1030 * 0.875;
    // What they split into
    private static final double IRON = IRON_BLOCK / 9, GOLD = GOLD_BLOCK / 9, COPPER = COPPER_BLOCK / 9,
            DIAMOND = DIAMOND_BLOCK / 9, EMERALD = EMERALD_BLOCK / 9, PLANK = LOG / 4, STICK = 2 * PLANK / 4,
            SCRAP = DEBRIS, NETHERITE = 4 * GOLD + 4 * SCRAP, BUCKET = 3 * IRON, LEATHER = 5;

    private static final Map<String, Double> EXACT = new HashMap<>();
    /** Then the first of these whose word is in the id. Order matters: "netherite" before "nether", etc. */
    private static final Map<String, Double> WORDS = new LinkedHashMap<>();

    static {
        // ice and water
        put("ice", ICE); put("packed_ice", PACKED_ICE); put("blue_ice", BLUE_ICE); put("frosted_ice", ICE);
        put("bucket", BUCKET); put("water_bucket", BUCKET + WATER); put("lava_bucket", BUCKET + LAVA);
        put("milk_bucket", BUCKET + MILK); put("powder_snow_bucket", BUCKET + SNOW_BLOCK);
        for (String fish : new String[]{"cod", "salmon", "pufferfish", "tropical_fish", "axolotl", "tadpole"}) {
            put(fish + "_bucket", BUCKET + WATER + 5);
        }
        put("snow_block", SNOW_BLOCK); put("snowball", SNOW_BLOCK / 4); put("snow", SNOW_BLOCK / 8);
        // metals and gems: the block is the real thing, the rest are ninths of it
        metal("iron", IRON_BLOCK); metal("gold", GOLD_BLOCK); metal("copper", COPPER_BLOCK);
        put("diamond_block", DIAMOND_BLOCK); put("diamond", DIAMOND);
        put("emerald_block", EMERALD_BLOCK); put("emerald", EMERALD);
        put("lapis_block", LAPIS_BLOCK); put("lapis_lazuli", LAPIS_BLOCK / 9);
        put("redstone_block", REDSTONE_BLOCK); put("redstone", REDSTONE_BLOCK / 9);
        put("coal_block", COAL_BLOCK); put("coal", COAL_BLOCK / 9); put("charcoal", LOG / 4);
        put("raw_iron_block", RAW_IRON_BLOCK); put("raw_iron", RAW_IRON_BLOCK / 9);
        put("raw_gold_block", RAW_GOLD_BLOCK); put("raw_gold", RAW_GOLD_BLOCK / 9);
        put("raw_copper_block", RAW_COPPER_BLOCK); put("raw_copper", RAW_COPPER_BLOCK / 9);
        put("ancient_debris", DEBRIS); put("netherite_scrap", SCRAP); put("netherite_ingot", NETHERITE);
        put("netherite_block", 9 * NETHERITE);
        // squeezed into one block from several
        put("quartz_block", QUARTZ_BLOCK); put("quartz", QUARTZ_BLOCK / 4);
        put("amethyst_block", AMETHYST_BLOCK); put("amethyst_shard", AMETHYST_BLOCK / 4);
        put("clay", CLAY_BLOCK); put("clay_ball", CLAY_BLOCK / 4); put("bricks", BRICKS); put("brick", BRICKS / 4);
        put("bone_block", BONE_BLOCK); put("bone_meal", BONE_BLOCK / 9); put("bone", BONE_BLOCK / 3);
        put("slime_block", SLIME_BLOCK); put("slime_ball", SLIME_BLOCK / 9);
        put("honey_block", HONEY_BLOCK); put("glass_bottle", GLASS); put("honey_bottle", GLASS + HONEY_BLOCK / 4);
        put("potion", GLASS + WATER); put("splash_potion", GLASS + WATER + 50); put("lingering_potion", GLASS + WATER + 50);
        put("hay_block", HAY); put("wheat", HAY / 9); put("dried_kelp_block", KELP_BLOCK); put("dried_kelp", KELP_BLOCK / 9);
        put("glass", GLASS); put("glass_pane", 6 * GLASS / 16);
        // wood
        put("stick", STICK); put("bowl", 3 * PLANK / 4); put("crafting_table", 4 * PLANK); put("chest", 8 * PLANK);
        put("barrel", 6 * PLANK + 2 * PLANK / 2); put("ladder", 7 * STICK / 3); put("bow", 3 * STICK + 3);
        put("arrow", (STICK + 30 + 2) / 4); put("torch", (STICK + COAL_BLOCK / 9) / 4);
        // odds and ends
        put("obsidian", 2400); put("crying_obsidian", 2400); put("netherrack", 1500); put("end_stone", 2200);
        put("bedrock", 3000); put("feather", 2); put("string", 3); put("leather", LEATHER); put("paper", 30);
        put("book", 3 * 30 + LEATHER); put("gunpowder", 50); put("flint", 2600 / 9.0);
        put("tnt", 5 * 50 + 4 * SAND); put("sand", SAND); put("red_sand", SAND);
        put("anvil", 3 * IRON_BLOCK + 4 * IRON); put("chipped_anvil", 3 * IRON_BLOCK + 4 * IRON);
        put("damaged_anvil", 3 * IRON_BLOCK + 4 * IRON); put("cauldron", 7 * IRON); put("hopper", 5 * IRON + 8 * PLANK);
        put("chain", IRON + 2 * IRON / 9); put("iron_bars", 6 * IRON / 16); put("rail", (6 * IRON + STICK) / 16);
        put("shears", 2 * IRON); put("flint_and_steel", IRON + 2600 / 9.0); put("compass", 4 * IRON + REDSTONE_BLOCK / 9);
        put("clock", 4 * GOLD + REDSTONE_BLOCK / 9); put("shield", 6 * PLANK + IRON);
        put("heavy_core", 20000); put("mace", 20000 + 300); put("trident", 3000);
        // tools and armor: their material, plus sticks
        gear("wooden", PLANK); gear("stone", STONE); gear("iron", IRON); gear("golden", GOLD); gear("copper", COPPER);
        gear("diamond", DIAMOND); gear("leather", LEATHER); gear("chainmail", IRON + 2 * IRON / 9);
        for (String piece : new String[]{"sword", "pickaxe", "axe", "shovel", "hoe", "spear", "helmet", "chestplate",
                "leggings", "boots"}) {
            Double diamond = EXACT.get("diamond_" + piece);
            if (diamond != null) put("netherite_" + piece, diamond + NETHERITE);
        }
        put("iron_horse_armor", 7 * IRON); put("golden_horse_armor", 7 * GOLD); put("diamond_horse_armor", 7 * DIAMOND);

        // blocks by what they're made of, if not named above (slabs half, stairs one and a half)
        word("deepslate", 2900); word("blackstone", 2700); word("basalt", 3000); word("tuff", 2400);
        word("calcite", 2700); word("granite", 2700); word("diorite", 2800); word("andesite", 2600);
        word("prismarine", 2700); word("sandstone", 2300); word("stone", STONE); word("cobble", STONE);
        word("brick", BRICKS); word("terracotta", 2000); word("concrete_powder", SAND); word("concrete", 2400);
        word("glass", GLASS); word("sand", SAND); word("gravel", 1700); word("mud", 1700); word("dirt", 1300);
        word("grass", 1300); word("podzol", 1300); word("mycelium", 1300); word("soul", 1500); word("nether", 1500);
        word("end_", 2200); word("purpur", 2200); word("wool", 150); word("carpet", 150 * 2 / 3.0);
        word("_bed", 3 * 150 + 3 * PLANK); word("_log", LOG); word("_wood", LOG); word("stem", LOG); word("hyphae", LOG);
        word("planks", PLANK); word("bamboo", 100); word("sign", (6 * PLANK + STICK) / 3); word("boat", 5 * PLANK);
        word("_fence", (4 * PLANK + 2 * STICK) / 3); word("_door", 6 * PLANK / 3); word("leaves", 200);
        word("sapling", 20); word("seeds", 1);
    }

    private static void put(String id, double kg) {
        EXACT.put(id, kg);
    }

    private static void word(String part, double kg) {
        WORDS.put(part, kg);
    }

    /** A metal: its block, ingot (a ninth) and nugget (a ninth of that). */
    private static void metal(String name, double block) {
        put(name + "_block", block);
        put(name + "_ingot", block / 9);
        put(name + "_nugget", block / 81);
    }

    /** A set of tools and armor from a material that weighs `m` a piece. */
    private static void gear(String material, double m) {
        put(material + "_sword", 2 * m + STICK);
        put(material + "_pickaxe", 3 * m + 2 * STICK);
        put(material + "_axe", 3 * m + 2 * STICK);
        put(material + "_shovel", m + 2 * STICK);
        put(material + "_hoe", 2 * m + 2 * STICK);
        put(material + "_spear", m + 2 * STICK);
        put(material + "_helmet", 5 * m);
        put(material + "_chestplate", 8 * m);
        put(material + "_leggings", 7 * m);
        put(material + "_boots", 4 * m);
    }

    private Density() {}

    /** One item's density: its mass, in the 1 m³ it takes up. kg/m³. */
    public static double of(ItemStack stack) {
        return stack.isEmpty() ? 0 : of(stack.getItem());
    }

    public static double of(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        Double exact = EXACT.get(id);
        if (exact != null) return exact;
        for (Map.Entry<String, Double> word : WORDS.entrySet()) {
            if (!id.contains(word.getKey())) continue;
            double kg = word.getValue();
            if (id.endsWith("_slab")) return kg / 2;
            if (id.endsWith("_stairs")) return kg * 1.5;
            return kg;
        }
        return 1000;
    }

    /**
     * Everything a player carries, averaged by how many of each. An empty inventory is just the player. Two or more
     * water buckets make an infinite water source: infinite density.
     */
    public static double average(Inventory inventory) {
        double total = 0;
        int count = 0, water = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.WATER_BUCKET)) water += stack.getCount();
            total += of(stack) * stack.getCount();
            count += stack.getCount();
        }
        if (water >= 2) return Double.POSITIVE_INFINITY;
        return count == 0 ? HUMAN : total / count;
    }

    /** Denser than packed ice: it breaks blocks. */
    public static boolean breaksBlocks(double density) {
        return density > PACKED_ICE;
    }

    /** Blast power: `atPackedIce` for packed ice, growing with the cube root of density, within the limits. */
    public static float power(double density, float atPackedIce, float min, float max) {
        double p = atPackedIce * Math.cbrt(Math.max(density, 1) / PACKED_ICE);
        return (float) Math.max(min, Math.min(max, p));
    }

    /** Like "3,499.7 kg/m³", or infinite. */
    public static String describe(double density) {
        if (Double.isInfinite(density)) return "∞ kg/m³, a singularity";
        return String.format("%,.1f kg/m³", density);
    }
}
