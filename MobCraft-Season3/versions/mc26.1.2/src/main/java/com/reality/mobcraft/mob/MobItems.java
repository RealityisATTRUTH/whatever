package com.reality.mobcraft.mob;

import com.reality.mobcraft.MobCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Items each mob can give itself, from the finished sheets. The Mob Item
 * Menu (the M button in the inventory) shows only the player's own mob's list.
 *
 * Items are looked up by name so the list keeps working if Mojang moves
 * item constants between versions.
 */
public final class MobItems {

    private static final Map<MobType, List<String>> ITEMS = new EnumMap<>(MobType.class);

    static {
        ITEMS.put(MobType.RABBIT, List.of("rabbit", "rabbit_foot", "rabbit_hide"));
        ITEMS.put(MobType.IRON_GOLEM, List.of("iron_ingot"));
        ITEMS.put(MobType.SKELETON, List.of("bone", "bow", "arrow"));
        ITEMS.put(MobType.PIGLIN, List.of("gold_ingot", "crying_obsidian"));
        ITEMS.put(MobType.GHAST, List.of("ghast_tear", "fire_charge", "gunpowder"));
        ITEMS.put(MobType.CREEPER, List.of("gunpowder"));
        ITEMS.put(MobType.SILVERFISH, List.of("iron_nugget"));
        ITEMS.put(MobType.EVOKER, List.of("totem_of_undying", "emerald"));
        ITEMS.put(MobType.VILLAGER, List.of("emerald"));
        ITEMS.put(MobType.ENDERMAN, List.of("ender_pearl"));
        ITEMS.put(MobType.VEX, List.of("iron_sword"));
        ITEMS.put(MobType.SHULKER, List.of("end_rod", "shulker_shell"));
        ITEMS.put(MobType.WITHER, List.of("nether_star"));
        ITEMS.put(MobType.BLAZE, List.of("blaze_rod"));
        ITEMS.put(MobType.ILLUSIONER, List.of("bow"));
        // Every sculk block except plain sculk.
        ITEMS.put(MobType.WARDEN, List.of("sculk_catalyst", "sculk_sensor", "calibrated_sculk_sensor",
                "sculk_shrieker", "sculk_vein"));
        ITEMS.put(MobType.ELDER_GUARDIAN, List.of("prismarine_shard", "prismarine_crystals", "sponge"));
        ITEMS.put(MobType.BREEZE, List.of("wind_charge"));
        ITEMS.put(MobType.ENDER_DRAGON, List.of("dragon_egg", "end_portal_frame", "dragon_breath"));
        // Axolotl and Villager have no self-give items. Bat has none yet.
    }

    private MobItems() {}

    public static List<String> get(MobType type) {
        return ITEMS.getOrDefault(type, List.of());
    }

    /**
     * Server only. Gives the player up to one stack of a self-give item.
     * Refuses items that aren't on the player's own mob list, and anything the
     * mob's kit blocks right now (for example the Rabbit's raw rabbit while
     * cursed). Anything that doesn't fit drops at the player's feet. With drop
     * on, the whole amount is thrown forward instead, like pressing Q.
     * Returns a message for the player.
     */
    public static String give(ServerPlayer player, String itemId, int count, boolean drop) {
        MobType type = PlayerMob.get(player);
        if (type == null) return "You don't have a mob.";
        if (!get(type).contains(itemId)) return "Your mob can't give itself that.";
        if (!MobKits.get(type).canSelfGive(player, itemId)) return "You can't give yourself that right now.";

        // Plain names are vanilla items. "mobcraft:..." are this mod's own items.
        Identifier id = itemId.contains(":") ? Identifier.parse(itemId) : Identifier.fromNamespaceAndPath("minecraft", itemId);
        Item item = BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
        if (item == null) {
            MobCraft.LOGGER.warn("Unknown self-give item {} for {}", itemId, type.id());
            return "That item doesn't exist in this version.";
        }

        ItemStack stack = new ItemStack(item, 1);
        int amount = Math.max(1, Math.min(count, stack.getMaxStackSize()));
        stack.setCount(amount);
        MobKits.get(type).customizeSelfGive(player, itemId, stack);
        if (drop) {
            throwForward(player, stack);
            return "Dropped " + amount + " " + prettyName(itemId) + ".";
        }
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            Level level = player.level();
            level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack));
        }
        return "Gave you " + amount + " " + prettyName(itemId) + ".";
    }

    /** Throws a stack from the player's eyes the way they're looking, like pressing Q. */
    private static void throwForward(ServerPlayer player, ItemStack stack) {
        Level level = player.level();
        ItemEntity item = new ItemEntity(level, player.getX(), player.getEyeY() - 0.3, player.getZ(), stack);
        Vec3 look = player.getLookAngle();
        item.setDeltaMovement(look.x * 0.3, look.y * 0.3 + 0.1, look.z * 0.3);
        item.setPickUpDelay(40); // 2 seconds, so you don't grab it straight back
        level.addFreshEntity(item);
    }

    /** "rabbit_foot" becomes "Rabbit Foot". */
    public static String prettyName(String itemId) {
        StringBuilder out = new StringBuilder();
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** Short description for messages, like "rabbit, rabbit_foot, rabbit_hide". */
    public static String describe(MobType type) {
        List<String> items = get(type);
        return items.isEmpty() ? "nothing" : String.join(", ", items);
    }
}
