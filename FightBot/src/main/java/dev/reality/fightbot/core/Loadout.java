package dev.reality.fightbot.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What the bot is carrying: the 9 hotbar items, item totals across the whole inventory,
 * and what is worn in the chest slot and held in the offhand. Item ids have no "minecraft:" prefix.
 */
public final class Loadout {
    public static final Loadout EMPTY = new Loadout(new String[9], new int[9], Map.of(), "air", "air");

    private final String[] hotbar;
    private final int[] hotbarCounts;
    private final Map<String, Integer> totals;
    public final String chest;
    public final String offhand;
    /** Hotbar slot of the spear with the highest Lunge level, or -1, and that level. */
    public int lungeSlot = -1, lungeLevel;
    /** Hotbar slot of the mace with the highest Wind Burst level, or -1, and that level. */
    public int windBurstSlot = -1, windBurstLevel;
    /** Hunger bar, 0 to 20. Lunge needs more than 6. */
    public int food = 20;
    /** Active effects by name, like "strength", with ticks left. -1 means it never runs out. */
    public Map<String, Integer> effects = Map.of();
    /** Burning right now. */
    public boolean onFire;
    /** Touching water, and with the eyes under it. */
    public boolean inWater, underWater;
    /** In water or rain, which is where a Riptide trident works. */
    public boolean wet;
    /** Standing in lava. */
    public boolean inLava;
    /**
     * The bot's eye height and hitbox height, and the target's hitbox, read from the game each tick. They change
     * with pose: standing is 1.62 and 1.8, sneaking is 1.27 and 1.5, and gliding or swimming is 0.4 and 0.6.
     */
    public double eyeHeight = 1.62, height = 1.8, oHeight = 1.8, oWidth = 0.6;
    /** The target is flying with an elytra, and where they look up or down. NaN pitch when unknown. */
    public boolean oGliding;
    public float oPitch = Float.NaN;
    /** Reach for hitting entities and for blocks, from the game's attributes. Survival defaults are 3 and 4.5. */
    public double entityReach = 3.0, blockReach = 4.5;
    /** Attack speed of the held item, in full swings a second: 1.6 for a sword, 1.0 for a netherite axe, 0.6 for a mace. */
    public double attackSpeed = 1.6;
    /**
     * Durability left, 0 to 1: the most worn piece of armor with Mending that it is wearing, and its elytra
     * wherever it is. 1 when there is nothing to repair.
     */
    public double armorWorst = 1, elytraLeft = 1;
    /** Filled slots in the bot's ender chest. */
    public int enderStock;
    /** Breath left in ticks, out of maxAir. */
    public int air = 300, maxAir = 300;

    public Loadout(String[] hotbar, int[] hotbarCounts, Map<String, Integer> totals, String chest, String offhand) {
        this.hotbar = new String[9];
        for (int i = 0; i < 9; i++) this.hotbar[i] = i < hotbar.length && hotbar[i] != null ? hotbar[i] : "air";
        this.hotbarCounts = Arrays.copyOf(hotbarCounts, 9);
        this.totals = Collections.unmodifiableMap(new HashMap<>(totals));
        this.chest = chest == null ? "air" : chest;
        this.offhand = offhand == null ? "air" : offhand;
    }

    /** Builder for tests and the simulator: hotbar items in order, with counts like "golden_apple*6". */
    public static Loadout of(String chest, String offhand, String... hotbarItems) {
        String[] h = new String[9];
        int[] c = new int[9];
        Map<String, Integer> totals = new HashMap<>();
        for (int i = 0; i < hotbarItems.length && i < 9; i++) {
            String[] parts = hotbarItems[i].split("\\*");
            h[i] = parts[0];
            c[i] = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            if (!h[i].equals("air")) totals.merge(h[i], c[i], Integer::sum);
        }
        if (chest != null && !chest.equals("air")) totals.merge(chest, 1, Integer::sum);
        if (offhand != null && !offhand.equals("air")) totals.merge(offhand, 1, Integer::sum);
        return new Loadout(h, c, totals, chest, offhand);
    }

    public String hotbar(int slot) {
        return hotbar[slot];
    }

    public int hotbarCount(int slot) {
        return hotbarCounts[slot];
    }

    /** Total of an item anywhere: hotbar, main inventory, armor, and offhand. */
    public int count(String id) {
        return totals.getOrDefault(id, 0);
    }

    public int count(Predicate<String> match) {
        int n = 0;
        for (var e : totals.entrySet()) if (match.test(e.getKey())) n += e.getValue();
        return n;
    }

    /** First hotbar slot whose item matches, or -1. */
    public int slot(Predicate<String> match) {
        for (int i = 0; i < 9; i++) if (match.test(hotbar[i])) return i;
        return -1;
    }

    public int slot(String id) {
        return slot(id::equals);
    }

    /** The id of an owned item that matches, from anywhere in the inventory, or null. Swords before axes is up to the caller. */
    public String ownedId(Predicate<String> match) {
        String best = null;
        for (var e : totals.entrySet()) {
            if (e.getValue() <= 0 || !match.test(e.getKey())) continue;
            // Netherite before diamond before the rest, by name.
            if (best == null || rank(e.getKey()) > rank(best)) best = e.getKey();
        }
        return best;
    }

    private static int rank(String id) {
        return id.startsWith("netherite") ? 3 : id.startsWith("diamond") ? 2 : id.startsWith("iron") ? 1 : 0;
    }

    public static boolean isMelee(String id) {
        return isSword(id) || isAxe(id) || id.equals("mace") || isSpear(id);
    }

    public static boolean isChestplate(String id) {
        return id.endsWith("_chestplate");
    }

    public static boolean isSword(String id) {
        return id.endsWith("_sword");
    }

    public static boolean isAxe(String id) {
        return id.endsWith("_axe");
    }

    public static boolean isSpear(String id) {
        return id.endsWith("_spear");
    }

    public boolean wearingElytra() {
        return chest.equals("elytra");
    }

    /** An elytra the bot can fly with: worn, or in the hotbar ready to swap on. */
    public boolean hasElytra() {
        return wearingElytra() || slot("elytra") >= 0;
    }

    public int fireworks() {
        return count("firework_rocket");
    }

    public int windCharges() {
        return count("wind_charge");
    }

    public int shulkers() {
        return count(id -> id.endsWith("shulker_box"));
    }

    public int totems() {
        return count("totem_of_undying");
    }

    public int apples() {
        return count("golden_apple") + count("enchanted_golden_apple");
    }

    /** A Lunge jab can fire: a Lunge spear in the hotbar and enough food. */
    public boolean canLunge() {
        return lungeSlot >= 0 && lungeLevel > 0 && food > 6;
    }

    /**
     * Potions are named by kind and contents, like "splash_potion:strong_strength". This finds the first
     * hotbar slot of the given kind ("splash_potion" or "potion") whose contents mention the effect word.
     */
    public int potionSlot(String kind, String effectWord) {
        return slot(id -> id.startsWith(kind + ":") && id.contains(effectWord));
    }

    public int potions(String kind, String effectWord) {
        return count(id -> id.startsWith(kind + ":") && id.contains(effectWord));
    }

    /** Ticks left on an effect, 0 when it is not active. */
    public int effectTicks(String name) {
        Integer t = effects.get(name);
        if (t == null) return 0;
        return t < 0 ? Integer.MAX_VALUE : t;
    }

    public boolean has(String id) {
        return count(id) > 0;
    }

    @Override
    public String toString() {
        return "chest " + chest + ", offhand " + offhand + ", hotbar " + String.join(" ", hotbar);
    }
}
