package com.reality.mobcraft.mob;

import java.util.Locale;

/**
 * Every mob role in Season 3.
 *
 * Energy numbers come from the finished sheets. Mobs whose kit isn't built
 * yet use 100 energy at 1 per second until their kit sets the real values.
 */
public enum MobType {
    // Easy
    RABBIT("Rabbit", 100, 1.0f),
    IRON_GOLEM("Iron Golem", 100, 1.0f),
    SKELETON("Skeleton", 100, 1.0f),
    AXOLOTL("Axolotl", 100, 1.0f),
    PIGLIN("Piglin", 100, 1.0f),
    // Medium
    GHAST("Ghast", 100, 1.0f),
    BAT("Bat", 100, 1.0f),
    CREEPER("Creeper", 100, 1.0f),
    SILVERFISH("Silverfish", 100, 1.0f),
    EVOKER("Evoker", 100, 1.0f),
    ENDERMAN("Enderman", 100, 1.0f),
    // Hard
    VEX("Vex", 100, 1.0f),
    SHULKER("Shulker", 100, 1.0f),
    WITHER("Wither", 100, 1.5f),
    ILLUSIONER("Illusioner", 100, 1.0f),
    WARDEN("Warden", 100, 1.0f),
    // Already balanced, kit not written down yet
    VILLAGER("Villager", 100, 1.0f),
    ENDER_DRAGON("Ender Dragon", 100, 1.0f),
    BLAZE("Blaze", 100, 1.0f),
    ELDER_GUARDIAN("Elder Guardian", 100, 1.0f),
    BREEZE("Breeze", 100, 1.0f);

    public final String displayName;
    public final int maxEnergy;
    public final float regenPerSecond;

    MobType(String displayName, int maxEnergy, float regenPerSecond) {
        this.displayName = displayName;
        this.maxEnergy = maxEnergy;
        this.regenPerSecond = regenPerSecond;
    }

    /** Lowercase id used in commands and saved data, like "iron_golem". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Looks up a mob by id. Returns null if there's no such mob. */
    public static MobType byId(String id) {
        if (id == null) return null;
        for (MobType type : values()) {
            if (type.id().equals(id.toLowerCase(Locale.ROOT))) return type;
        }
        return null;
    }
}
