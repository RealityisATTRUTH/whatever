package com.reality.mobcraft.mob;

import com.reality.mobcraft.MobCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What each mob drops when the player dies, from the finished sheets.
 *
 * Items are looked up by name, like "rabbit_foot", so the table keeps working
 * even if Mojang moves item constants around between versions.
 * Mobs with no entry drop nothing extra. Their own inventory drops as normal.
 */
public final class MobDrops {

    public record Drop(String itemId, int count) {}

    private static final Map<MobType, List<Drop>> DROPS = new EnumMap<>(MobType.class);

    static {
        DROPS.put(MobType.RABBIT, List.of(new Drop("rabbit_foot", 16), new Drop("rabbit_hide", 64)));
        DROPS.put(MobType.IRON_GOLEM, List.of(new Drop("iron_ingot", 32), new Drop("poppy", 4)));
        DROPS.put(MobType.SKELETON, List.of(new Drop("bone", 64)));
        DROPS.put(MobType.PIGLIN, List.of(new Drop("gold_ingot", 64), new Drop("crying_obsidian", 192)));
        DROPS.put(MobType.GHAST, List.of(new Drop("ghast_tear", 128), new Drop("gunpowder", 64)));
        DROPS.put(MobType.CREEPER, List.of(new Drop("gunpowder", 32)));
        DROPS.put(MobType.EVOKER, List.of(new Drop("totem_of_undying", 8), new Drop("emerald", 64)));
        DROPS.put(MobType.SHULKER, List.of(new Drop("shulker_shell", 8)));
        DROPS.put(MobType.WITHER, List.of(new Drop("nether_star", 2)));
        // Never the egg: the Ender Dragon kit removes it.
        DROPS.put(MobType.ELDER_GUARDIAN, List.of(new Drop("prismarine_shard", 64), new Drop("wet_sponge", 8)));
        DROPS.put(MobType.BLAZE, List.of(new Drop("blaze_rod", 16), new Drop("blaze_powder", 32)));
        DROPS.put(MobType.BREEZE, List.of(new Drop("breeze_rod", 4), new Drop("wind_charge", 32)));
        DROPS.put(MobType.ENDER_DRAGON, List.of(new Drop("dragon_breath", 64), new Drop("dragon_head", 1)));
        // Axolotl, Bat and Vex drop nothing by design.
        // Silverfish, Enderman, Illusioner, Warden, Villager: not on the sheets yet.
    }

    private MobDrops() {}

    public static List<Drop> get(MobType type) {
        return DROPS.getOrDefault(type, List.of());
    }

    /** Drops a mob's items at the player's feet. Returns how many items were dropped. */
    public static int dropAt(ServerPlayer player, MobType type) {
        Level level = player.level();
        int total = 0;
        for (Drop drop : get(type)) {
            Identifier id = Identifier.fromNamespaceAndPath("minecraft", drop.itemId());
            Item item = BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
            if (item == null) {
                MobCraft.LOGGER.warn("Unknown drop item {} for {}", drop.itemId(), type.id());
                continue;
            }
            // Split into normal stack sizes so 192 crying obsidian becomes 3 stacks.
            int left = drop.count();
            while (left > 0) {
                ItemStack stack = new ItemStack(item, 1);
                int size = Math.min(left, stack.getMaxStackSize());
                stack.setCount(size);
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack));
                left -= size;
                total += size;
            }
        }
        return total;
    }

    /** Short description for the test menu, like "16 rabbit_foot, 64 rabbit_hide". */
    public static String describe(MobType type) {
        List<Drop> drops = get(type);
        if (drops.isEmpty()) return "nothing";
        StringBuilder out = new StringBuilder();
        for (Drop drop : drops) {
            if (!out.isEmpty()) out.append(", ");
            out.append(drop.count()).append(' ').append(drop.itemId());
        }
        return out.toString();
    }
}
