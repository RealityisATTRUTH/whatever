package com.reality.mobcraft.mob;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every mob's abilities that players can switch off in /mobcraftsettings,
 * from the finished sheets. Kits check isEnabled before running an ability.
 */
public final class MobAbilities {

    public record Ability(String id, String displayName) {}

    private static final Map<MobType, List<Ability>> ABILITIES = new EnumMap<>(MobType.class);

    static {
        add(MobType.RABBIT, "summon_escorts", "Summon Escorts");
        add(MobType.IRON_GOLEM, "golem_toss", "Golem Toss", "iron_repair", "Iron Repair");
        add(MobType.SKELETON, "lock_on", "Lock On", "skeleton_horse", "Skeleton Horse");
        add(MobType.AXOLOTL, "play_dead", "Play Dead", "call_the_pod", "Call the Pod",
                "hunting_partner", "Hunting Partner");
        add(MobType.PIGLIN, "gold_rush", "Gold Rush", "crossbow", "Crossbow");
        add(MobType.GHAST, "fireball", "Fireball", "barrage", "Barrage", "ferry", "Ferry");
        add(MobType.BAT, "confuse", "Confuse", "echolocation", "Echolocation", "lifesteal", "Lifesteal");
        add(MobType.CREEPER, "self_charge", "Self Charge", "gunpowder_blast", "Gunpowder Blast",
                "leaf_hide", "Leaf Hide");
        add(MobType.SILVERFISH, "stone_swim", "Stone Swim", "curse_attack", "Curse Attack", "infest", "Infest",
                "summon_silverfish", "Summon Silverfish");
        add(MobType.EVOKER, "fang_attack", "Fang Attack", "fang_surround", "Fang Surround",
                "summon_vexes", "Summon Vexes", "raid_captain", "Raid Captain");
        add(MobType.VILLAGER, "warden_guards", "Warden Bodyguards", "jeff_menu", "Jeff's Menu");
        add(MobType.ELDER_GUARDIAN, "laser_beam", "Laser Beam", "elder_curse", "Elder Curse",
                "call_guardians", "Call the Guardians");
        add(MobType.BREEZE, "gust", "Gust", "leap", "Leap", "whirlwind", "Whirlwind");
        add(MobType.ENDER_DRAGON, "dragon_breath", "Dragon Breath", "wing_buffet", "Wing Buffet");
        add(MobType.ENDERMAN, "teleport", "Teleport", "auto_dodge", "Auto Dodge");
        add(MobType.VEX, "phase", "Phase", "charge", "Charge");
        add(MobType.SHULKER, "blocking", "Blocking", "teleport", "Teleport", "shulker_bullet", "Shulker Bullet",
                "elytra_gamble", "Elytra Gamble", "end_city_teleport", "RTS Teleport");
        add(MobType.WITHER, "black_skull", "Black Skull", "blue_skull", "Blue Skull",
                "charge", "Charge", "block_destruction", "Block Destruction");
        add(MobType.ILLUSIONER, "clones", "Clones", "arrow_burst", "Arrow Burst", "blindness", "Blindness");
        add(MobType.BLAZE, "fireballs", "Fireballs", "fire_shield", "Fire Shield", "blaze_burst", "Blaze Burst");
        add(MobType.WARDEN, "sonic_shriek", "Sonic Shriek", "rage", "Rage",
                "echolocation", "Echolocation", "sniffing", "Sniffing",
                "burrow", "Burrow", "sculk_teleport", "Sculk Teleport");
        // Villager: kit not written down yet. Ender Dragon, Elder Guardian: not designed yet.
    }

    private MobAbilities() {}

    /** Pairs of id, display name. */
    private static void add(MobType type, String... idsAndNames) {
        List<Ability> list = new ArrayList<>();
        for (int i = 0; i + 1 < idsAndNames.length; i += 2) {
            list.add(new Ability(idsAndNames[i], idsAndNames[i + 1]));
        }
        ABILITIES.put(type, List.copyOf(list));
    }

    public static List<Ability> get(MobType type) {
        return type == null ? List.of() : ABILITIES.getOrDefault(type, List.of());
    }

    public static boolean belongsTo(MobType type, String abilityId) {
        for (Ability ability : get(type)) {
            if (ability.id().equals(abilityId)) return true;
        }
        return false;
    }

    /** True unless the player switched this ability off. Works on both sides. */
    public static boolean isEnabled(Player player, String abilityId) {
        return !player.getAttachedOrElse(MobAttachments.DISABLED_ABILITIES, List.of()).contains(abilityId);
    }

    /** Server only. Switches one ability on or off for this player. */
    public static void setEnabled(Player player, String abilityId, boolean enabled) {
        List<String> disabled = new ArrayList<>(player.getAttachedOrElse(MobAttachments.DISABLED_ABILITIES, List.of()));
        disabled.remove(abilityId);
        if (!enabled) disabled.add(abilityId);
        if (disabled.isEmpty()) {
            player.removeAttached(MobAttachments.DISABLED_ABILITIES);
        } else {
            player.setAttached(MobAttachments.DISABLED_ABILITIES, List.copyOf(disabled));
        }
    }
}
