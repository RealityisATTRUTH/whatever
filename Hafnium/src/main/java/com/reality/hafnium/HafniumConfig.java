// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium;

import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Everything Hafnium remembers, saved in config/hafnium.properties: whether
 * the theme is on, its colours, and where each menu button was moved.
 */
public final class HafniumConfig {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hafnium.properties");

    public static boolean themeOn = true;
    /** Friends and chat through the Hafnium server. */
    public static boolean socialOn = true;
    /** Pop-ups for messages, friend requests and friends coming online. */
    public static boolean socialToasts = true;
    public static final String DEFAULT_SERVER = "wss://hafnium-server.rsc-hafnium.workers.dev/connect";
    /** The first address, which Cloudflare never put online. Saved settings that still have it are moved over. */
    private static final String OLD_SERVER = "wss://hafnium-server.realitystudios.workers.dev/connect";
    public static String socialServer = DEFAULT_SERVER;
    /** Who can join a hosted world: "private" for Hafnium friends only, or "public" for anyone with the code. */
    public static String hostVisibility = "private";
    /** The theme colours, by name. Each is 0xAARRGGBB. */
    public static final Map<Theme.Slot, Integer> COLORS = new LinkedHashMap<>();
    /** Moved or hidden buttons: screen, then button, then where it went. */
    public static final Map<String, Map<String, Layout.Place>> LAYOUTS = new HashMap<>();

    private HafniumConfig() {}

    public static void load() {
        Theme.applyPreset(Theme.Preset.GRAPHITE);
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        Properties props = new Properties();
        try (Reader in = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            props.load(in);
        } catch (Exception e) {
            Hafnium.LOGGER.warn("Couldn't read config/hafnium.properties, using the defaults", e);
            return;
        }
        themeOn = !"false".equals(props.getProperty("theme.enabled"));
        socialOn = !"false".equals(props.getProperty("social.enabled"));
        socialToasts = !"false".equals(props.getProperty("social.popups"));
        socialServer = props.getProperty("social.server", DEFAULT_SERVER).trim();
        if (socialServer.equals(OLD_SERVER)) socialServer = DEFAULT_SERVER;
        hostVisibility = "public".equals(props.getProperty("host.visibility")) ? "public" : "private";
        for (Theme.Slot slot : Theme.Slot.values()) {
            String value = props.getProperty("color." + slot.key);
            if (value == null) continue;
            try {
                COLORS.put(slot, (int) Long.parseLong(value.replace("#", ""), 16));
            } catch (NumberFormatException e) {
                Hafnium.LOGGER.warn("Bad colour for {}: {}", slot.key, value);
            }
        }
        for (String name : props.stringPropertyNames()) {
            if (!name.startsWith("layout.")) continue;
            int bar = name.indexOf('|');
            if (bar < 0) continue;
            String screen = name.substring("layout.".length(), bar);
            String widget = name.substring(bar + 1);
            Layout.Place place = Layout.Place.parse(props.getProperty(name));
            if (place != null) LAYOUTS.computeIfAbsent(screen, k -> new HashMap<>()).put(widget, place);
        }
    }

    public static void save() {
        Map<String, String> out = new TreeMap<>();
        out.put("theme.enabled", Boolean.toString(themeOn));
        out.put("social.enabled", Boolean.toString(socialOn));
        out.put("social.popups", Boolean.toString(socialToasts));
        out.put("social.server", socialServer);
        out.put("host.visibility", hostVisibility);
        for (Map.Entry<Theme.Slot, Integer> e : COLORS.entrySet()) {
            out.put("color." + e.getKey().key, String.format("%08X", e.getValue()));
        }
        for (Map.Entry<String, Map<String, Layout.Place>> screen : LAYOUTS.entrySet()) {
            for (Map.Entry<String, Layout.Place> widget : screen.getValue().entrySet()) {
                if (widget.getValue().isDefault()) continue;
                out.put("layout." + screen.getKey() + "|" + widget.getKey(), widget.getValue().toString());
            }
        }
        Properties props = new Properties() {
            @Override
            public java.util.Set<Map.Entry<Object, Object>> entrySet() {
                // Keeps the file in a steady, readable order.
                java.util.Set<Map.Entry<Object, Object>> sorted = new java.util.LinkedHashSet<>();
                for (Map.Entry<String, String> e : out.entrySet()) sorted.add(Map.entry(e.getKey(), e.getValue()));
                return sorted;
            }
        };
        props.putAll(out);
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                props.store(w, "Hafnium settings. Colours are AARRGGBB. Layout lines are dx,dy,hidden.");
            }
        } catch (Exception e) {
            Hafnium.LOGGER.warn("Couldn't save config/hafnium.properties", e);
        }
    }
}
