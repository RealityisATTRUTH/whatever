package dev.reality.fightbot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test kits with maxed netherite gear, given with the game's own item command so the enchantments
 * and potion contents use the normal item syntax. After giving, it checks each slot and reports any
 * item the game refused, for example an enchantment name this version does not have.
 */
final class Kits {
    private Kits() {
    }

    private static final String ARMOR = "protection:4,unbreaking:3,mending:1";
    private static final Map<String, String> GEAR = Map.ofEntries(
            Map.entry("helmet", "netherite_helmet[enchantments={" + ARMOR + ",respiration:3,aqua_affinity:1}]"),
            Map.entry("chestplate", "netherite_chestplate[enchantments={" + ARMOR + "}]"),
            Map.entry("leggings", "netherite_leggings[enchantments={" + ARMOR + ",swift_sneak:3}]"),
            Map.entry("boots", "netherite_boots[enchantments={" + ARMOR + ",feather_falling:4,depth_strider:3}]"),
            Map.entry("sword", "netherite_sword[enchantments={sharpness:5,fire_aspect:2,sweeping_edge:3,looting:3,unbreaking:3,mending:1}]"),
            Map.entry("axe", "netherite_axe[enchantments={sharpness:5,efficiency:5,unbreaking:3,mending:1}]"),
            Map.entry("mace", "mace[enchantments={density:5,wind_burst:3,fire_aspect:2,unbreaking:3,mending:1}]"),
            Map.entry("mace_wind1", "mace[enchantments={density:5,wind_burst:1,fire_aspect:2,unbreaking:3,mending:1}]"),
            Map.entry("spear", "netherite_spear[enchantments={lunge:3,sharpness:5,unbreaking:3,mending:1}]"),
            // Silk touch, so a mined ender chest drops itself instead of obsidian.
            Map.entry("pickaxe", "netherite_pickaxe[enchantments={efficiency:5,silk_touch:1,unbreaking:3,mending:1}]"),
            Map.entry("enderchest", "ender_chest 1"),
            Map.entry("bow", "bow[enchantments={power:5,punch:2,flame:1,unbreaking:3,mending:1}]"),
            Map.entry("crossbow", "crossbow[enchantments={quick_charge:3,multishot:1,unbreaking:3,mending:1}]"),
            Map.entry("arrows", "arrow 64"),
            Map.entry("trident_loyalty", "trident[enchantments={loyalty:3,impaling:5,unbreaking:3,mending:1}]"),
            Map.entry("trident_riptide", "trident[enchantments={riptide:3,impaling:5,unbreaking:3,mending:1}]"),
            Map.entry("elytra", "elytra[enchantments={unbreaking:3,mending:1}]"),
            Map.entry("shield", "shield[enchantments={unbreaking:3,mending:1}]"),
            Map.entry("gapples", "golden_apple 64"),
            Map.entry("egaps", "enchanted_golden_apple 8"),
            Map.entry("totems", "totem_of_undying 1"),
            Map.entry("fireworks", "firework_rocket 64"),
            Map.entry("wind", "wind_charge 64"),
            // Potions go in through the game's own code, since this version refused them in the item command.
            Map.entry("heal", "POTION:strong_healing"),
            Map.entry("strength", "POTION:strong_strength"),
            Map.entry("speed", "POTION:strong_swiftness"),
            Map.entry("fireres", "POTION:long_fire_resistance"),
            Map.entry("pearls", "ender_pearl 16"),
            Map.entry("obsidian", "obsidian 64"),
            Map.entry("crystals", "end_crystal 64"),
            Map.entry("anchors", "respawn_anchor 64"),
            Map.entry("glowstone", "glowstone 64"),
            Map.entry("water", "water_bucket 1"),
            Map.entry("cobble", "cobblestone 64"),
            Map.entry("xp", "experience_bottle 64"));

    /** All 27 ender chest slots for a kit. */
    private static List<String> enderChest(String kit) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) items.add(shulker(kit));
        for (int i = 0; i < 8; i++) items.add("totem_of_undying 1");
        for (int i = 0; i < 2; i++) items.add("golden_apple 64");
        items.add("experience_bottle 64");
        items.add("enchanted_golden_apple 16");
        for (int i = 0; i < 2; i++) items.add("ender_pearl 16");
        switch (kit) {
            case "crystal" -> {
                for (int i = 0; i < 3; i++) items.add("end_crystal 64");
                items.add("obsidian 64");
                items.add("obsidian 64");
                items.add("respawn_anchor 64");
                items.add("glowstone 64");
            }
            case "elytra_mace" -> {
                for (int i = 0; i < 6; i++) items.add("firework_rocket 64");
                items.add("elytra[enchantments={unbreaking:3,mending:1}]");
            }
            case "wind_mace" -> {
                for (int i = 0; i < 7; i++) items.add("wind_charge 64");
            }
            case "bow" -> {
                for (int i = 0; i < 7; i++) items.add("arrow 64");
            }
            default -> {
                for (int i = 0; i < 7; i++) items.add("POTION:strong_healing");
            }
        }
        return items;
    }

    /** A shulker box packed with the kit's refills, in the item command's container syntax. */
    private static String shulker(String kit) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) items.add("totem_of_undying 1");
        items.add("golden_apple 64");
        items.add("ender_pearl 16");
        items.add("cobblestone 64");
        items.add("experience_bottle 64");
        items.add("experience_bottle 64");
        switch (kit) {
            case "crystal" -> {
                for (int i = 0; i < 5; i++) items.add("end_crystal 64");
                for (int i = 0; i < 3; i++) items.add("obsidian 64");
                items.add("respawn_anchor 64");
                items.add("glowstone 64");
            }
            case "elytra_mace" -> {
                for (int i = 0; i < 4; i++) items.add("firework_rocket 64");
            }
            case "wind_mace" -> {
                for (int i = 0; i < 4; i++) items.add("wind_charge 64");
            }
            case "oreo" -> {
                for (int i = 0; i < 4; i++) items.add("wind_charge 64");
                items.add("water_bucket 1");
            }
            case "bow" -> {
                for (int i = 0; i < 4; i++) items.add("arrow 64");
            }
            default -> items.add("enchanted_golden_apple 8");
        }
        StringBuilder b = new StringBuilder("red_shulker_box[container=[");
        for (int i = 0; i < items.size() && i < 27; i++) {
            String[] parts = items.get(i).split(" ");
            if (i > 0) b.append(',');
            b.append("{slot:").append(i).append(",item:{id:\"minecraft:").append(parts[0]).append("\",count:").append(parts[1]).append("}}");
        }
        return b.append("]]").toString();
    }

    private static final Map<String, String> LAST = new java.util.HashMap<>();

    /** The kit last given to this player, so a respawned bot gets the same one. */
    static String lastKit(String name) {
        return LAST.get(name);
    }

    static String give(MinecraftServer server, String name, String kit) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p == null) return "No player called " + name + ".";
        if (!java.util.Set.of("sword", "axe_shield", "bow", "trident", "pots", "uhc", "elytra_mace", "wind_mace", "oreo", "crystal").contains(kit)) {
            return "Kits are sword, axe_shield, bow, trident, pots, uhc, elytra_mace, wind_mace, oreo, and crystal.";
        }
        LAST.put(name, kit);
        // Slot name, then what goes in it.
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("armor.head", GEAR.get("helmet"));
        slots.put("armor.chest", GEAR.get("chestplate"));
        slots.put("armor.legs", GEAR.get("leggings"));
        slots.put("armor.feet", GEAR.get("boots"));
        switch (kit) {
            case "sword" -> {
                slots.put("hotbar.0", GEAR.get("sword"));
                slots.put("hotbar.1", GEAR.get("axe"));
                slots.put("hotbar.2", GEAR.get("spear"));
                slots.put("hotbar.3", GEAR.get("gapples"));
                slots.put("hotbar.4", GEAR.get("heal"));
                slots.put("hotbar.5", GEAR.get("strength"));
                slots.put("hotbar.6", GEAR.get("speed"));
                slots.put("hotbar.7", GEAR.get("egaps"));
                slots.put("hotbar.8", GEAR.get("pearls"));
                slots.put("weapon.offhand", GEAR.get("shield"));
            }
            case "axe_shield" -> {
                // No sword, so the axe is the main weapon: every hit on a raised shield disables it.
                slots.put("hotbar.0", GEAR.get("axe"));
                slots.put("hotbar.1", GEAR.get("gapples"));
                slots.put("hotbar.2", GEAR.get("heal"));
                slots.put("hotbar.3", GEAR.get("strength"));
                slots.put("hotbar.4", GEAR.get("speed"));
                slots.put("hotbar.5", GEAR.get("egaps"));
                slots.put("hotbar.6", GEAR.get("pearls"));
                slots.put("hotbar.7", GEAR.get("water"));
                slots.put("hotbar.8", GEAR.get("cobble"));
                slots.put("weapon.offhand", GEAR.get("shield"));
            }
            case "pots" -> {
                // Pots only, also called no debuff: a sword, pearls, speed, and a full inventory of healing pots.
                // No apples and no totems.
                slots.put("hotbar.0", GEAR.get("sword"));
                slots.put("hotbar.1", GEAR.get("pearls"));
                slots.put("hotbar.2", GEAR.get("speed"));
                for (int i = 3; i < 9; i++) slots.put("hotbar." + i, GEAR.get("heal"));
                for (int i = 0; i < 24; i++) slots.put("inventory." + i, GEAR.get("heal"));
                slots.put("inventory.24", GEAR.get("speed"));
                slots.put("inventory.25", GEAR.get("speed"));
                slots.put("inventory.26", GEAR.get("fireres"));
            }
            case "trident" -> {
                // Two Loyalty tridents to throw and a Riptide trident to dash with in water or rain.
                slots.put("hotbar.0", GEAR.get("sword"));
                slots.put("hotbar.1", GEAR.get("trident_loyalty"));
                slots.put("hotbar.2", GEAR.get("trident_riptide"));
                slots.put("hotbar.3", GEAR.get("gapples"));
                slots.put("hotbar.4", GEAR.get("heal"));
                slots.put("hotbar.5", GEAR.get("strength"));
                slots.put("hotbar.6", GEAR.get("speed"));
                slots.put("hotbar.7", GEAR.get("pearls"));
                // A second Loyalty trident: one can be in the air coming back while the other is thrown.
                slots.put("hotbar.8", GEAR.get("trident_loyalty"));
                slots.put("weapon.offhand", GEAR.get("totems"));
            }
            case "uhc" -> {
                // A UHC loadout: diamond gear at UHC enchantment levels, buckets, cobwebs, blocks, and apples. No
                // totems, potions, shulker box, or ender chest.
                slots.put("armor.head", "diamond_helmet[enchantments={protection:3,unbreaking:3}]");
                slots.put("armor.chest", "diamond_chestplate[enchantments={protection:2,unbreaking:3}]");
                slots.put("armor.legs", "diamond_leggings[enchantments={protection:3,unbreaking:3}]");
                slots.put("armor.feet", "diamond_boots[enchantments={protection:2,unbreaking:3}]");
                slots.put("hotbar.0", "diamond_sword[enchantments={sharpness:4,unbreaking:3}]");
                slots.put("hotbar.1", "diamond_axe[enchantments={sharpness:1,efficiency:3,unbreaking:3}]");
                slots.put("hotbar.2", "bow[enchantments={power:1,unbreaking:3}]");
                slots.put("hotbar.3", "water_bucket 1");
                slots.put("hotbar.4", "lava_bucket 1");
                slots.put("hotbar.5", "cobweb 8");
                slots.put("hotbar.6", "golden_apple 9");
                slots.put("hotbar.7", "cobblestone 64");
                slots.put("hotbar.8", "oak_planks 64");
                slots.put("weapon.offhand", "shield[enchantments={unbreaking:3}]");
                slots.put("inventory.0", "crossbow[enchantments={piercing:1,unbreaking:3}]");
                slots.put("inventory.1", "water_bucket 1");
                slots.put("inventory.2", "lava_bucket 1");
                slots.put("inventory.3", "arrow 16");
                slots.put("inventory.4", "cobblestone 64");
                slots.put("inventory.5", "diamond_pickaxe[enchantments={efficiency:3,unbreaking:3}]");
            }
            case "bow" -> {
                slots.put("hotbar.0", GEAR.get("sword"));
                slots.put("hotbar.1", GEAR.get("bow"));
                slots.put("hotbar.2", GEAR.get("crossbow"));
                slots.put("hotbar.3", GEAR.get("arrows"));
                slots.put("hotbar.4", GEAR.get("gapples"));
                slots.put("hotbar.5", GEAR.get("heal"));
                slots.put("hotbar.6", GEAR.get("strength"));
                slots.put("hotbar.7", GEAR.get("pearls"));
                slots.put("hotbar.8", GEAR.get("water"));
                slots.put("weapon.offhand", GEAR.get("totems"));
            }
            case "elytra_mace" -> {
                slots.put("hotbar.0", GEAR.get("sword"));
                slots.put("hotbar.1", GEAR.get("axe"));
                slots.put("hotbar.2", GEAR.get("mace"));
                slots.put("hotbar.3", GEAR.get("fireworks"));
                slots.put("hotbar.4", GEAR.get("elytra"));
                slots.put("hotbar.5", GEAR.get("spear"));
                slots.put("hotbar.6", GEAR.get("gapples"));
                slots.put("hotbar.7", GEAR.get("heal"));
                slots.put("hotbar.8", GEAR.get("strength"));
                slots.put("weapon.offhand", GEAR.get("totems"));
            }
            case "wind_mace" -> {
                slots.put("hotbar.0", GEAR.get("sword"));
                slots.put("hotbar.1", GEAR.get("axe"));
                slots.put("hotbar.2", GEAR.get("mace"));
                slots.put("hotbar.3", GEAR.get("wind"));
                slots.put("hotbar.4", GEAR.get("spear"));
                slots.put("hotbar.5", GEAR.get("gapples"));
                slots.put("hotbar.6", GEAR.get("heal"));
                slots.put("hotbar.7", GEAR.get("strength"));
                slots.put("hotbar.8", GEAR.get("speed"));
                slots.put("weapon.offhand", GEAR.get("totems"));
            }
            case "oreo" -> {
                // Dedicated Riptide -> Lunge III spear -> Wind Burst I mace kit.
                slots.put("hotbar.0", GEAR.get("sword"));
                slots.put("hotbar.1", GEAR.get("mace_wind1"));
                slots.put("hotbar.2", GEAR.get("spear"));
                slots.put("hotbar.3", GEAR.get("trident_riptide"));
                slots.put("hotbar.4", GEAR.get("water"));
                slots.put("hotbar.5", GEAR.get("wind"));
                slots.put("hotbar.6", GEAR.get("gapples"));
                slots.put("hotbar.7", GEAR.get("heal"));
                slots.put("hotbar.8", GEAR.get("strength"));
                slots.put("weapon.offhand", GEAR.get("totems"));
            }
            case "crystal" -> {
                // Blast Protection IV on the leggings. Protection and Blast Protection cannot share a piece, and the
                // game caps protection against explosions at 20 points: three Protection IV pieces give 12 and Blast
                // Protection IV gives 8, so this hits the cap exactly and keeps the most protection against hits.
                slots.put("armor.legs", "netherite_leggings[enchantments={blast_protection:4,unbreaking:3,mending:1,swift_sneak:3}]");
                slots.put("hotbar.0", GEAR.get("sword"));
                slots.put("hotbar.1", GEAR.get("obsidian"));
                slots.put("hotbar.2", GEAR.get("crystals"));
                slots.put("hotbar.3", GEAR.get("anchors"));
                slots.put("hotbar.4", GEAR.get("glowstone"));
                slots.put("hotbar.5", GEAR.get("gapples"));
                slots.put("hotbar.6", GEAR.get("heal"));
                slots.put("hotbar.7", GEAR.get("strength"));
                slots.put("hotbar.8", GEAR.get("pearls"));
                slots.put("weapon.offhand", GEAR.get("totems"));
            }
            default -> {
                return "Kits are sword, axe_shield, bow, trident, pots, uhc, elytra_mace, wind_mace, oreo, and crystal.";
            }
        }
        // Spares in the main inventory: totems, more pots, egaps, a pickaxe for shulker boxes. Not for UHC.
        int inv = 0;
        if (!kit.equals("uhc") && !kit.equals("pots")) {
        for (int i = 0; i < 6; i++) slots.put("inventory." + inv++, GEAR.get("totems"));
        for (int i = 0; i < 6; i++) slots.put("inventory." + inv++, GEAR.get("heal"));
        for (int i = 0; i < 2; i++) slots.put("inventory." + inv++, GEAR.get("strength"));
        for (int i = 0; i < 2; i++) slots.put("inventory." + inv++, GEAR.get("speed"));
        slots.put("inventory." + inv++, GEAR.get("fireres"));
        slots.put("inventory." + inv++, GEAR.get("xp"));
        if (!kit.equals("sword") && !kit.equals("axe_shield")) slots.put("inventory." + inv++, GEAR.get("egaps"));
        if (kit.equals("elytra_mace")) {
            slots.put("inventory." + inv++, GEAR.get("fireworks"));
            slots.put("inventory." + inv++, GEAR.get("speed"));
        }
        if (kit.equals("wind_mace") || kit.equals("oreo")) slots.put("inventory." + inv++, GEAR.get("wind"));
        if (kit.equals("crystal")) {
            slots.put("inventory." + inv++, GEAR.get("crystals"));
            slots.put("inventory." + inv++, GEAR.get("obsidian"));
            slots.put("inventory." + inv++, GEAR.get("glowstone"));
        }
        slots.put("inventory." + inv++, GEAR.get("pickaxe"));
        if (!kit.equals("axe_shield") && !kit.equals("bow")) slots.put("inventory." + inv++, GEAR.get("water"));
        if (kit.equals("bow")) for (int i = 0; i < 2; i++) slots.put("inventory." + inv++, GEAR.get("arrows"));
        if (!kit.equals("crystal") && !kit.equals("axe_shield")) slots.put("inventory." + inv++, GEAR.get("cobble"));
        if (kit.equals("elytra_mace") || kit.equals("wind_mace") || kit.equals("oreo")) slots.put("inventory." + inv++, GEAR.get("pearls"));
        if (inv < 27) slots.put("inventory." + inv++, GEAR.get("enderchest"));
        // The ender chest, filled to the brim: packed shulker boxes, loose totems, apples, pearls, and the kit's ammo.
        List<String> ender = enderChest(kit);
        for (int i = 0; i < ender.size() && i < 27; i++) slots.put("enderchest." + i, ender.get(i));
        slots.put("inventory." + inv++, shulker(kit));
        }

        p.getInventory().clearContent();
        p.getEnderChestInventory().clearContent();
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            p.setItemSlot(s, ItemStack.EMPTY);
        }
        var source = server.createCommandSourceStack().withSuppressedOutput();
        for (var e : slots.entrySet()) {
            if (e.getValue().startsWith("POTION:")) {
                putPotion(p, e.getKey(), e.getValue().substring(7));
                continue;
            }
            server.getCommands().performPrefixedCommand(source, "item replace entity " + name + " " + e.getKey() + " with " + e.getValue());
        }
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        // The item commands finish after this method returns, so the slots are checked a few ticks later.
        PENDING.add(new Check(name, slots, server.getTickCount() + 5));
        return "Gave " + name + " the maxed " + kit + " kit. Checking the slots in a moment.";
    }

    private record Check(String name, Map<String, String> slots, long at) {
    }

    private static final List<Check> PENDING = new ArrayList<>();

    /** Runs pending kit checks that are due, and reports what is missing. */
    static void tick(MinecraftServer server, java.util.function.Consumer<String> announce) {
        for (var it = PENDING.iterator(); it.hasNext(); ) {
            Check c = it.next();
            if (server.getTickCount() < c.at()) continue;
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayerByName(c.name());
            if (p == null) continue;
            List<String> missing = new ArrayList<>();
            for (var e : c.slots().entrySet()) {
                if (!empty(p, e.getKey())) continue;
                String v = e.getValue();
                missing.add(v.startsWith("POTION:") ? "splash potion of " + v.substring(7) : v.split("\\[")[0].split(" ")[0]);
            }
            announce.accept(missing.isEmpty() ? c.name() + "'s kit is all in place."
                    : c.name() + "'s kit is missing: " + String.join(", ", missing) + ". The game refused those items.");
        }
    }

    /** A splash potion straight into a hotbar or inventory slot. */
    private static void putPotion(ServerPlayer p, String slot, String potion) {
        net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> holder = switch (potion) {
            case "strong_healing" -> net.minecraft.world.item.alchemy.Potions.STRONG_HEALING;
            case "strong_strength" -> net.minecraft.world.item.alchemy.Potions.STRONG_STRENGTH;
            case "strong_swiftness" -> net.minecraft.world.item.alchemy.Potions.STRONG_SWIFTNESS;
            default -> net.minecraft.world.item.alchemy.Potions.LONG_FIRE_RESISTANCE;
        };
        ItemStack stack = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.SPLASH_POTION, holder);
        String[] parts = slot.split("\\.");
        if (parts[0].equals("enderchest")) {
            p.getEnderChestInventory().setItem(Integer.parseInt(parts[1]), stack);
            return;
        }
        int i = Integer.parseInt(parts[1]) + (parts[0].equals("inventory") ? 9 : 0);
        p.getInventory().setItem(i, stack);
    }

    private static boolean empty(ServerPlayer p, String slot) {
        return switch (slot) {
            case "armor.head" -> p.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
            case "armor.chest" -> p.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
            case "armor.legs" -> p.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
            case "armor.feet" -> p.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            case "weapon.offhand" -> p.getOffhandItem().isEmpty();
            default -> {
                String[] parts = slot.split("\\.");
                if (parts[0].equals("enderchest")) yield p.getEnderChestInventory().getItem(Integer.parseInt(parts[1])).isEmpty();
                int i = Integer.parseInt(parts[1]) + (parts[0].equals("inventory") ? 9 : 0);
                yield p.getInventory().getItem(i).isEmpty();
            }
        };
    }
}
