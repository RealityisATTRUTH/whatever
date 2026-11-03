package dev.reality.fightbot.core;

/**
 * Fighting styles the bot knows about, in the order it prefers them when the kit allows several.
 * Built styles have their own policy. The others are recognized so the bot can say what the kit is for,
 * but it fights them with the best built style it has the items for.
 */
public enum Style {
    RIPTIDE_LUNGE_MACE("Riptide Lunge mace", true),
    ELYTRA_MACE("elytra mace", true),
    WIND_MACE("wind charge mace", true),
    CRYSTAL("crystal", true),
    ANCHOR("respawn anchor", true),
    CART("minecart TNT", false),
    SPEAR("spear", false),
    TRIDENT("trident", true),
    RANGED("bow and crossbow", true),
    SWORD_SHIELD("sword and shield", true),
    AXE_SHIELD("axe and shield", true),
    SWORD("sword", true),
    AXE("axe", true),
    MACE_GROUND("mace on the ground", false),
    FIST("fists", true);

    public final String label;
    public final boolean built;

    Style(String label, boolean built) {
        this.label = label;
        this.built = built;
    }

    /** What the kit allows, best first: the style it will fight with, and the best style the kit is made for. */
    public record Choice(Style use, Style kitIsFor, String reason) {
        public String describe() {
            if (use == kitIsFor) return "Fighting " + use.label + ": " + reason + ".";
            return "Kit looks like " + kitIsFor.label + ", which is not built yet, so fighting " + use.label + ": " + reason + ".";
        }
    }

    public static Choice pick(Loadout l, BotProfile p) {
        Style kitIsFor = null, use = null;
        String reason = "";
        for (Style s : values()) {
            String why = s.fits(l, p);
            if (why == null) continue;
            if (kitIsFor == null) kitIsFor = s;
            if (s.built) {
                use = s;
                reason = why;
                break;
            }
        }
        if (use == null) use = FIST;
        if (kitIsFor == null) kitIsFor = use;
        return new Choice(use, kitIsFor, reason.isEmpty() ? "no weapon found" : reason);
    }

    /** Why the kit fits this style, or null when it does not. */
    String fits(Loadout l, BotProfile p) {
        boolean mace = l.slot("mace") >= 0;
        boolean shield = l.offhand.equals("shield");
        boolean sword = l.slot(Loadout::isSword) >= 0;
        boolean axe = l.slot(Loadout::isAxe) >= 0;
        return switch (this) {
            case RIPTIDE_LUNGE_MACE -> mace && l.windBurstSlot >= 0 && l.windBurstLevel >= 1
                    && l.slot("trident:riptide") >= 0 && l.canLunge() && p.useLunge == 1
                    && (l.has("water_bucket") || l.wet)
                    ? "Riptide trident, Lunge spear, Wind Burst mace" : null;
            case ELYTRA_MACE -> mace && l.hasElytra() && l.fireworks() >= p.minFireworks
                    && (l.wearingElytra() ? l.slot(Loadout::isChestplate) >= 0 : l.chest.endsWith("_chestplate") || l.slot(Loadout::isChestplate) >= 0)
                    && l.slot("firework_rocket") >= 0
                    ? "mace, elytra, " + l.fireworks() + " fireworks and a chestplate to swap to" : null;
            case WIND_MACE -> mace && l.slot("wind_charge") >= 0 ? "mace and " + l.windCharges() + " wind charges" : null;
            case CRYSTAL -> l.has("end_crystal") && l.has("obsidian") ? "end crystals and obsidian" : null;
            case ANCHOR -> l.has("respawn_anchor") && l.has("glowstone") ? "respawn anchors and glowstone" : null;
            case CART -> l.has("tnt_minecart") && l.has("rail") ? "TNT minecarts and rails" : null;
            // With a sword, axe or mace, a Lunge spear is a dash added to that style, not a style of its own.
            case SPEAR -> l.slot(Loadout::isSpear) >= 0 && !sword && !axe && !mace ? "only a spear" : null;
            case SWORD_SHIELD -> sword && shield ? "sword with a shield in the offhand" + (axe ? " and an axe for shields" : "") : null;
            case AXE_SHIELD -> axe && shield ? "axe with a shield in the offhand" : null;
            case SWORD -> sword ? "sword" : null;
            case AXE -> axe ? "axe" : null;
            case MACE_GROUND -> mace ? "mace with no way to get height" : null;
            case TRIDENT -> l.slot(id -> id.startsWith("trident")) >= 0 && l.slot("bow") < 0
                    ? (l.slot("trident") >= 0 ? "Loyalty trident" : "trident") + (l.slot("trident:riptide") >= 0 ? " and a Riptide trident" : "") : null;
            case RANGED -> (l.slot("bow") >= 0 || l.slot(id -> id.startsWith("crossbow")) >= 0) && (l.has("arrow") || l.has("spectral_arrow"))
                    ? (l.slot("bow") >= 0 ? "bow" : "crossbow") + " and " + (l.count("arrow") + l.count("spectral_arrow")) + " arrows" : null;
            case FIST -> "";
        };
    }

    public boolean isMace() {
        return this == RIPTIDE_LUNGE_MACE || this == ELYTRA_MACE || this == WIND_MACE;
    }
}
