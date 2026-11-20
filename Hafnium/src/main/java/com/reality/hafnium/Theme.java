// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * The flat Hafnium look for buttons and sliders: a filled box with soft
 * corners, a thin border and a faint highlight along the top. Every colour
 * can be changed in the Hafnium screen.
 */
public final class Theme {

    /** One colour the player can change. */
    public enum Slot {
        BUTTON("button", "Button"),
        HOVER("hover", "Hovered button"),
        BORDER("border", "Border"),
        HOVER_BORDER("hover_border", "Hovered border"),
        DISABLED("disabled", "Greyed-out button"),
        HANDLE("slider_handle", "Slider handle");

        public final String key;
        public final String label;

        Slot(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /** Ready-made colour sets. */
    public enum Preset {
        GRAPHITE("Graphite", 0xE63A3A40, 0xF04C4C54, 0xFF55555E, 0xFFC4C4CC, 0xB0262629, 0xFFB4B4BC),
        SILVER("Silver", 0xE66A6A72, 0xF07E7E87, 0xFF8C8C95, 0xFFEDEDF2, 0xB0434347, 0xFFE2E2E8),
        MIDNIGHT("Midnight", 0xE61C1E26, 0xF0262A36, 0xFF30344A, 0xFF6C93FF, 0xB0121318, 0xFF6C93FF),
        EMBER("Ember", 0xE62B2522, 0xF03A302B, 0xFF4A3B33, 0xFFE58A4B, 0xB01B1714, 0xFFE58A4B);

        public final String label;
        final int[] colors;

        Preset(String label, int... colors) {
            this.label = label;
            this.colors = colors;
        }
    }

    private Theme() {}

    public static boolean on() {
        return HafniumConfig.themeOn;
    }

    public static int get(Slot slot) {
        return HafniumConfig.COLORS.getOrDefault(slot, Preset.GRAPHITE.colors[slot.ordinal()]);
    }

    public static void set(Slot slot, int argb) {
        HafniumConfig.COLORS.put(slot, argb);
    }

    public static void applyPreset(Preset preset) {
        for (Slot slot : Slot.values()) set(slot, preset.colors[slot.ordinal()]);
    }

    /** Draws a themed button in place of the vanilla stone texture. */
    public static void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        int fill = !active ? get(Slot.DISABLED) : hovered ? get(Slot.HOVER) : get(Slot.BUTTON);
        int border = active && hovered ? get(Slot.HOVER_BORDER) : get(Slot.BORDER);
        box(g, x, y, w, h, fade(fill, alpha), fade(border, alpha), active);
    }

    /** Draws a slider's track. */
    public static void drawTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        int fill = !active ? get(Slot.DISABLED) : get(Slot.BUTTON);
        int border = active && hovered ? get(Slot.HOVER_BORDER) : get(Slot.BORDER);
        box(g, x, y, w, h, fade(fill, alpha), fade(border, alpha), false);
    }

    /** Draws a slider's handle. */
    public static void drawHandle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha) {
        int fill = active ? get(Slot.HANDLE) : get(Slot.BORDER);
        if (hovered && active) fill = ARGB.scaleRGB(fill, 1.15f);
        box(g, x + 1, y + 1, w - 2, h - 2, fade(fill, alpha), fade(ARGB.scaleRGB(fill, 0.7f), alpha), true);
    }

    /** A box with the four corner pixels left out, which reads as slightly rounded. */
    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border, boolean shine) {
        if (w < 3 || h < 3) {
            g.fill(x, y, x + w, y + h, fill);
            return;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        if (shine) g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(0xFFFFFF, ARGB.alpha(fill) / 9));
        g.fill(x + 1, y, x + w - 1, y + 1, border);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }

    /** Multiplies a colour's own alpha by a widget's fade-in alpha. */
    public static int fade(int argb, float alpha) {
        if (alpha >= 1f) return argb;
        return withAlpha(argb, Math.round(ARGB.alpha(argb) * Math.max(0f, alpha)));
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }
}
