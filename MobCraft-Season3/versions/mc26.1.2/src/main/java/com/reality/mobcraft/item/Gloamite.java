package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Gloamite: what Duskvein becomes when it reacts with the air. Mining Duskvein Ore without Silk Touch has a 1 in 20
 * chance to give Gloamite instead of Duskvein (the block's loot table); it hisses and smokes as it changes.
 *
 * Gloamite armor is the tier above netherite, and Gloamite can't go on armor by itself:
 *   Rose Gold Ingot   = gold ingot + copper ingot
 *   Dusk Binding      = Duskvein + 2 Rose Gold Ingots
 *   Bound Gloamite    = Gloamite + Dusk Binding
 *   Smithing table    = Gloamite Upgrade template + a netherite armor piece + Bound Gloamite
 * Credit: the Rose Gold Ingot texture, and the Dusk Binding texture built on it, are from Rose Gold Equipment by igobyjake
 * (https://modrinth.com/mod/rose-gold-equipment), used under the MIT License. See CREDITS-rose-gold-equipment.txt.
 *
 * The template is found in Ancient City chests (about 1 in 6) and copied with 7 Duskvein and cobbled deepslate.
 */
public final class Gloamite {

    private static final float TEMPLATE_CHANCE = 0.16f;

    private Gloamite() {}

    private static Item item(String name, Rarity rarity, String... lore) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id(name));
        List<Component> lines = new java.util.ArrayList<>();
        for (String l : lore) lines.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(rarity)
                .component(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lines))));
    }

    public static final Item GLOAMITE = item("gloamite", Rarity.EPIC,
            "Duskvein that reacted with the air", "Bind it with a Dusk Binding to put it on netherite armor");
    public static final Item ROSE_GOLD_INGOT = item("rose_gold_ingot", Rarity.COMMON, "Gold and copper, together");
    public static final Item DUSK_BINDING = item("dusk_binding", Rarity.RARE, "Holds Gloamite together");
    public static final Item BOUND_GLOAMITE = item("bound_gloamite", Rarity.EPIC,
            "Use it in a smithing table with the Gloamite Upgrade", "and a piece of netherite armor");
    public static final Item TEMPLATE = item("gloamite_upgrade_smithing_template", Rarity.EPIC,
            "Smithing template", "Netherite armor + Bound Gloamite");

    public static void register() {
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!source.isBuiltin() || !key.equals(BuiltInLootTables.ANCIENT_CITY)) return;
            table.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(TEMPLATE_CHANCE))
                    .add(LootItem.lootTableItem(TEMPLATE)));
        });
        // The reaction: when mined Duskvein Ore turned to Gloamite, it hisses and smokes where it broke.
        // The drop can appear just before or just after the break event, so both orders are handled.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel server) || !state.is(Duskvein.ORE)) return;
            boolean already = !server.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5),
                    e -> e.getAge() <= 2 && e.getItem().is(GLOAMITE)).isEmpty();
            if (already) react(server, pos, player);
            else PENDING.put(pos.immutable(), new Pending(server.getGameTime(), player));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (PENDING.isEmpty() || !(entity instanceof ItemEntity item) || !item.getItem().is(GLOAMITE)) return;
            PENDING.entrySet().removeIf(e -> level.getGameTime() - e.getValue().tick() > 2);
            net.minecraft.core.BlockPos hit = null;
            for (net.minecraft.core.BlockPos p : PENDING.keySet()) {
                if (p.closerToCenterThan(item.position(), 1.5)) { hit = p; break; }
            }
            if (hit != null) react(level, hit, PENDING.remove(hit).player());
        });
    }

    private record Pending(long tick, net.minecraft.world.entity.player.Player player) {}
    private static final java.util.Map<net.minecraft.core.BlockPos, Pending> PENDING = new java.util.HashMap<>();

    private static void react(ServerLevel server, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        server.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 14, 0.3, 0.3, 0.3, 0.02);
        server.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, 0.3, 0.3, 0.3, 0.05);
        server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.6f);
        server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.4f);
        player.sendSystemMessage(Component.literal("The Duskvein reacted with the air and turned to Gloamite!")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** True for armor, tools and weapons upgraded with Gloamite. */
    public static boolean isGloamite(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.resources.Identifier model = stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
        return model != null && model.getNamespace().equals(MobCraft.MOD_ID) && model.getPath().startsWith("gloamite_");
    }

    static void addToTab(net.minecraft.world.item.CreativeModeTab.Output output) {
        for (Item i : List.of(GLOAMITE, ROSE_GOLD_INGOT, DUSK_BINDING, BOUND_GLOAMITE, TEMPLATE)) output.accept(i);
        output.accept(Shadow.ESSENCE);
        output.accept(Shadow.SHARD);
    }
}
