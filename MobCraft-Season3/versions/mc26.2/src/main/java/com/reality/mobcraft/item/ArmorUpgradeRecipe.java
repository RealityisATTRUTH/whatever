package com.reality.mobcraft.item;

import com.mojang.serialization.MapCodec;
import com.reality.mobcraft.MobCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Crafting armor upgrades on. The result is the same piece, with its
 * enchantments and durability, plus the upgrade.
 *
 *   Helmet with ears or tendrils   in one row: the left ear or tendril just
 *                                  left of the helmet, the right one just
 *                                  right of it. Either side alone works too.
 *                                  Needs a crafting table, since it's 3 wide.
 *   Chestplate + Ender Pearl Shard or Stalker Shard   anywhere in the grid,
 *                                  so it works in the inventory's 2x2 grid.
 *   Helmet + Stalker Shard          anywhere in the grid.
 *   Netherite sword + Fracture Nether Star + netherite upgrade template
 *                                  anywhere in the grid. All three are needed,
 *                                  and the template is used up. A smithing
 *                                  table works too (star_blade_smithing.json).
 */
public final class ArmorUpgradeRecipe extends CustomRecipe {

    public static final ArmorUpgradeRecipe INSTANCE = new ArmorUpgradeRecipe();
    public static final MapCodec<ArmorUpgradeRecipe> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorUpgradeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<ArmorUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    public static void register() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MobCraft.id("armor_upgrade"), SERIALIZER);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !result(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result(input);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }

    /** The upgraded piece, or EMPTY if the grid isn't a valid upgrade. */
    private static ItemStack result(CraftingInput input) {
        int width = input.width();
        int armorIndex = -1;
        EquipmentSlot slot = null;
        int upgrades = 0;
        int templates = 0;
        for (int i = 0; i < input.items().size(); i++) {
            ItemStack stack = input.items().get(i);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) {
                templates++;
                continue;
            }
            if (ArmorUpgrades.upgradeId(stack) != null) {
                upgrades++;
                continue;
            }
            EquipmentSlot found = ArmorUpgrades.armorSlot(stack);
            if (found == null || armorIndex != -1) return ItemStack.EMPTY; // anything else, or two pieces
            armorIndex = i;
            slot = found;
        }
        if (armorIndex == -1 || upgrades == 0) return ItemStack.EMPTY;
        if (templates > 1) return ItemStack.EMPTY;

        ItemStack armor = input.items().get(armorIndex);
        String current = armor.get(ModItems.ARMOR_UPGRADE);
        String result;

        int col = armorIndex % width;
        ItemStack leftOf = col > 0 ? input.items().get(armorIndex - 1) : ItemStack.EMPTY;
        ItemStack rightOf = col < width - 1 ? input.items().get(armorIndex + 1) : ItemStack.EMPTY;
        String leftId = ArmorUpgrades.upgradeId(leftOf);
        String rightId = ArmorUpgrades.upgradeId(rightOf);

        if (slot == EquipmentSlot.HEAD && (ArmorUpgrades.isLeftPiece(leftId) || ArmorUpgrades.isRightPiece(rightId))) {
            // Ears and tendrils: only beside the helmet, each on its own side.
            if (templates != 0) return ItemStack.EMPTY;
            int beside = (leftId != null ? 1 : 0) + (rightId != null ? 1 : 0);
            if (beside != upgrades) return ItemStack.EMPTY;
            if (leftId != null && !ArmorUpgrades.isLeftPiece(leftId)) return ItemStack.EMPTY;
            if (rightId != null && !ArmorUpgrades.isRightPiece(rightId)) return ItemStack.EMPTY;
            if (leftId != null && rightId != null
                    && ArmorUpgrades.isEar(leftId) != ArmorUpgrades.isEar(rightId)) return ItemStack.EMPTY; // one ear, one tendril
            result = current;
            if (leftId != null) {
                result = ArmorUpgrades.combine(result, leftId);
                if (result == null) return ItemStack.EMPTY;
            }
            if (rightId != null) {
                result = ArmorUpgrades.combine(result, rightId);
                if (result == null) return ItemStack.EMPTY;
            }
        } else {
            // Shards: one, anywhere in the grid.
            if (upgrades != 1) return ItemStack.EMPTY;
            String id = null;
            for (ItemStack stack : input.items()) {
                String found = ArmorUpgrades.upgradeId(stack);
                if (found != null) id = found;
            }
            if (id == null || ArmorUpgrades.isEar(id) || ArmorUpgrades.isTendril(id)) return ItemStack.EMPTY;
            if (!ArmorUpgrades.fits(id, slot)) return ItemStack.EMPTY;
            // No Mending with a Vex Wing: only killing allays repairs it.
            if (ArmorUpgrades.VEX_WING.equals(id) && VexWing.hasMending(armor)) return ItemStack.EMPTY;
            // The star sword costs a netherite upgrade template, and nothing else takes one.
            boolean star = ArmorUpgrades.STAR_SHARD.equals(id);
            if (templates != (star ? 1 : 0)) return ItemStack.EMPTY;
            result = ArmorUpgrades.combine(current, id);
            if (result == null) return ItemStack.EMPTY;
        }

        ItemStack out = armor.copyWithCount(1);
        ArmorUpgrades.apply(out, result);
        return out;
    }
}
