package com.reality.mobcraft.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.reality.mobcraft.MobCraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only settings, saved to config/mobcraft-client.json.
 *
 * The energy bar position is stored as a fraction of the free space on screen
 * (0 = left/top edge, 1 = right/bottom edge), so it stays in the same place
 * when the window is resized or the GUI scale changes.
 */
public final class HudConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mobcraft-client.json");

    public static final float DEFAULT_X = 0.0f;
    public static final float DEFAULT_Y = 0.0f;

    /** Saved fields. Gson reads and writes these directly. */
    private float energyBarX = DEFAULT_X;
    private float energyBarY = DEFAULT_Y;
    /** Mob Item Menu: throw given items on the ground instead of into the inventory. */
    private boolean dropGivenItems = false;

    /** RTS mode: which parts of the screen show. Changed with the Settings button in RTS. */
    private boolean rtsMinimap = true;
    private boolean rtsArmyBar = true;
    private boolean rtsSidePanel = true;
    private boolean rtsHealthBars = true;
    private boolean rtsArrows = true;
    private boolean rtsAllyMarkers = true;
    private boolean rtsPings = true;
    private boolean rtsDebug = false;

    private static HudConfig instance = new HudConfig();

    /** One RTS screen part that can be switched on and off. */
    public enum RtsPart {
        MINIMAP("Minimap"),
        ARMY_BAR("Army Bar"),
        SIDE_PANEL("Order Buttons and Army List"),
        HEALTH_BARS("Health Bars"),
        ARROWS("Order Arrows"),
        ALLY_MARKERS("Teammate Markers"),
        PINGS("Team Pings"),
        DEBUG("Selection Debug");

        public final String label;

        RtsPart(String label) {
            this.label = label;
        }
    }

    public static boolean rts(RtsPart part) {
        return switch (part) {
            case MINIMAP -> instance.rtsMinimap;
            case ARMY_BAR -> instance.rtsArmyBar;
            case SIDE_PANEL -> instance.rtsSidePanel;
            case HEALTH_BARS -> instance.rtsHealthBars;
            case ARROWS -> instance.rtsArrows;
            case ALLY_MARKERS -> instance.rtsAllyMarkers;
            case PINGS -> instance.rtsPings;
            case DEBUG -> instance.rtsDebug;
        };
    }

    public static void toggleRts(RtsPart part) {
        boolean on = !rts(part);
        switch (part) {
            case MINIMAP -> instance.rtsMinimap = on;
            case ARMY_BAR -> instance.rtsArmyBar = on;
            case SIDE_PANEL -> instance.rtsSidePanel = on;
            case HEALTH_BARS -> instance.rtsHealthBars = on;
            case ARROWS -> instance.rtsArrows = on;
            case ALLY_MARKERS -> instance.rtsAllyMarkers = on;
            case PINGS -> instance.rtsPings = on;
            case DEBUG -> instance.rtsDebug = on;
        }
        save();
    }

    private HudConfig() {}

    public static float energyBarX() { return instance.energyBarX; }
    public static float energyBarY() { return instance.energyBarY; }

    public static boolean dropGivenItems() { return instance.dropGivenItems; }

    public static void setDropGivenItems(boolean drop) {
        instance.dropGivenItems = drop;
        save();
    }

    public static void setEnergyBar(float x, float y) {
        instance.energyBarX = clamp(x);
        instance.energyBarY = clamp(y);
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            HudConfig loaded = GSON.fromJson(Files.readString(FILE), HudConfig.class);
            if (loaded != null) {
                loaded.energyBarX = clamp(loaded.energyBarX);
                loaded.energyBarY = clamp(loaded.energyBarY);
                instance = loaded;
            }
        } catch (IOException | RuntimeException e) {
            MobCraft.LOGGER.warn("Couldn't read {}, using defaults.", FILE, e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException e) {
            MobCraft.LOGGER.warn("Couldn't save {}.", FILE, e);
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
