package com.reality.menutrim.core;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Switches for Essential's own settings, the same ones in Essential Settings.
 * Menu Trim only flips them through Essential's settings object, so Essential
 * saves them itself and they can be changed back in Essential Settings too.
 * Nothing in Essential is patched. Settings an older Essential doesn't have
 * show as "?".
 */
public final class EssentialExtras {

    private static final String CONFIG_CLASS = "gg.essential.config.EssentialConfig";

    /**
     * One Essential feature. property is Essential's setting name. inverted
     * means Essential stores it as "disable", so the feature is on when the
     * setting is false.
     */
    public static final class Feature {
        public final String label;
        final String property;
        final boolean inverted;

        Feature(String label, String property, boolean inverted) {
            this.label = label;
            this.property = property;
            this.inverted = inverted;
        }
    }

    public static final List<Feature> FEATURES = Arrays.asList(
            new Feature("Other players' cosmetics", "DisableCosmetics", true),
            new Feature("Cosmetics in your inventory", "DisableCosmeticsInInventory", true),
            new Feature("Emotes", "DisableEmotes", true),
            new Feature("Essential icon on nametags", "ShowEssentialIndicatorOnNametag", false),
            new Feature("Essential icon in the tab list", "ShowEssentialIndicatorOnTab", false),
            new Feature("Essential screenshot pop-up", "EssentialScreenshots", false),
            new Feature("Quick action bar", "ShowQuickActionBar", false));

    private static Object config;
    private static boolean missing;

    private EssentialExtras() {}

    /** Essential's settings object, or null without Essential. */
    private static Object config() {
        if (config != null || missing) return config;
        try {
            Class<?> type = Class.forName(CONFIG_CLASS, true, EssentialExtras.class.getClassLoader());
            config = type.getField("INSTANCE").get(null);
        } catch (Throwable e) {
            missing = true;
            TrimCore.LOGGER.info("Essential's settings weren't found, so the Essential Extras page is empty.");
        }
        return config;
    }

    public static boolean available() {
        return config() != null;
    }

    /** Whether the feature is on right now, or null if it can't be read. */
    public static Boolean isOn(Feature feature) {
        Object cfg = config();
        if (cfg == null) return null;
        try {
            Method getter = cfg.getClass().getMethod("get" + feature.property);
            boolean value = (Boolean) getter.invoke(cfg);
            return feature.inverted != value;
        } catch (Throwable e) {
            return null;
        }
    }

    public static void setOn(Feature feature, boolean on) {
        Object cfg = config();
        if (cfg == null) return;
        try {
            Method setter = cfg.getClass().getMethod("set" + feature.property, boolean.class);
            setter.invoke(cfg, feature.inverted != on);
        } catch (Throwable e) {
            TrimCore.LOGGER.warn("Couldn't change Essential setting " + feature.property, e);
        }
    }

    public static String label(Feature feature) {
        Boolean on = isOn(feature);
        return feature.label + ": " + (on == null ? "?" : on ? "On" : "Off");
    }

    /** Turns every extra off. Friends, chat, hosting and notifications are left alone. */
    public static void trimAll() {
        for (Feature feature : FEATURES) setOn(feature, false);
    }
}
