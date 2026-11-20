// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * Hafnium's settings: the theme switch, colour presets, a colour picker for
 * each part of a button, and the menu layout editor.
 */
public class HafniumScreen extends Screen {

    private static final int COLUMN = 150;
    private static final int GAP = 10;
    private static final int ROW = 24;
    private static final String[] CHANNELS = {"Red", "Green", "Blue", "Opacity"};

    /** The colour being picked. Kept between visits. */
    private static Theme.Slot slot = Theme.Slot.BUTTON;

    private final Screen parent;

    public HafniumScreen(Screen parent) {
        super(Component.literal("Hafnium"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - COLUMN - GAP / 2;
        int right = this.width / 2 + GAP / 2;
        int top = 36;

        // Left column: theme, which colour, presets and a preview.
        this.addRenderableWidget(Button.builder(themeLabel(), b -> {
            HafniumConfig.themeOn = !HafniumConfig.themeOn;
            this.rebuildWidgets();
        }).bounds(left, top, COLUMN, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Colour: " + slot.label), b -> {
            Theme.Slot[] all = Theme.Slot.values();
            slot = all[(slot.ordinal() + 1) % all.length];
            this.rebuildWidgets();
        }).bounds(left, top + ROW, COLUMN, 20).build());
        Theme.Preset[] presets = Theme.Preset.values();
        int half = (COLUMN - 4) / 2;
        for (int i = 0; i < presets.length; i++) {
            Theme.Preset preset = presets[i];
            int x = left + (i % 2) * (half + 4);
            int y = top + ROW * (2 + i / 2);
            this.addRenderableWidget(Button.builder(Component.literal(preset.label), b -> {
                Theme.applyPreset(preset);
                HafniumConfig.themeOn = true;
                this.rebuildWidgets();
            }).bounds(x, y, half, 20).build());
        }
        // The normal Minecraft stone buttons. The Hafnium colours are kept for when you switch back.
        int vanilla = presets.length;
        this.addRenderableWidget(Button.builder(Component.literal("Minecraft"), b -> {
            HafniumConfig.themeOn = false;
            this.rebuildWidgets();
        }).bounds(left + (vanilla % 2) * (half + 4), top + ROW * (2 + vanilla / 2), half, 20).build());
        int previewRow = 2 + (presets.length + 2) / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Preview"), b -> { })
                .bounds(left, top + ROW * previewRow, half, 20).build());
        Button greyed = Button.builder(Component.literal("Greyed out"), b -> { })
                .bounds(left + half + 4, top + ROW * previewRow, half, 20).build();
        greyed.active = false;
        this.addRenderableWidget(greyed);

        // Right column: the colour picker.
        for (int c = 0; c < CHANNELS.length; c++) {
            this.addRenderableWidget(new ChannelSlider(right, top + ROW * c, c));
        }
        this.addRenderableWidget(Button.builder(Component.literal("Back to Graphite"), b -> {
            Theme.applyPreset(Theme.Preset.GRAPHITE);
            this.rebuildWidgets();
        }).bounds(right, top + ROW * 4, COLUMN, 20).build());

        // Bottom: the layout editor for the menu this was opened from.
        String layoutKey = Layout.screenKey(parent);
        if (layoutKey != null) {
            this.addRenderableWidget(Button.builder(Component.literal("Move or hide buttons"), b -> {
                HafniumConfig.save();
                Layout.startEditing(parent);
                this.minecraft.gui.setScreen(parent);
            }).bounds(left, this.height - 52, COLUMN, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Reset this menu"), b -> {
                Layout.reset(layoutKey);
                b.setMessage(Component.literal("Menu reset"));
                b.active = false;
            }).bounds(right, this.height - 52, COLUMN, 20).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
    }

    private static Component themeLabel() {
        return Component.literal("Button style: " + (HafniumConfig.themeOn ? "Hafnium" : "Minecraft"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, "Hafnium", this.width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(this.font, "Make the menus yours. Changes show right away.", this.width / 2, 22, 0xFFAAAAB0);
        // A swatch of the colour being picked, over a checkerboard so see-through colours show.
        int right = this.width / 2 + GAP / 2;
        int y = 36 + ROW * 5 + 2;
        if (y + 16 < this.height - 56) {
            for (int i = 0; i < COLUMN / 8; i++) {
                for (int j = 0; j < 2; j++) {
                    int shade = (i + j) % 2 == 0 ? 0xFF2A2A2A : 0xFF4A4A4A;
                    graphics.fill(right + i * 8, y + j * 8, right + i * 8 + 8, y + j * 8 + 8, shade);
                }
            }
            graphics.fill(right, y, right + COLUMN - COLUMN % 8, y + 16, Theme.get(slot));
            graphics.text(this.font, String.format("#%08X", Theme.get(slot)), right + 4, y + 4, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        HafniumConfig.save();
        this.minecraft.gui.setScreen(parent);
    }

    /** One slider of the colour picker: red, green, blue or opacity. */
    private static final class ChannelSlider extends AbstractSliderButton {
        private final int channel;

        ChannelSlider(int x, int y, int channel) {
            super(x, y, COLUMN, 20, Component.empty(), read(channel) / 255.0);
            this.channel = channel;
            updateMessage();
        }

        private static int read(int channel) {
            int c = Theme.get(slot);
            return switch (channel) {
                case 0 -> ARGB.red(c);
                case 1 -> ARGB.green(c);
                case 2 -> ARGB.blue(c);
                default -> ARGB.alpha(c);
            };
        }

        private int amount() {
            return (int) Math.round(this.value * 255);
        }

        @Override
        protected void updateMessage() {
            String text = channel == 3
                    ? CHANNELS[channel] + ": " + Math.round(amount() / 2.55) + "%"
                    : CHANNELS[channel] + ": " + amount();
            this.setMessage(Component.literal(text));
        }

        @Override
        protected void applyValue() {
            int c = Theme.get(slot);
            int a = ARGB.alpha(c), r = ARGB.red(c), g = ARGB.green(c), b = ARGB.blue(c);
            switch (channel) {
                case 0 -> r = amount();
                case 1 -> g = amount();
                case 2 -> b = amount();
                default -> a = amount();
            }
            Theme.set(slot, ARGB.color(a, r, g, b));
        }
    }
}
