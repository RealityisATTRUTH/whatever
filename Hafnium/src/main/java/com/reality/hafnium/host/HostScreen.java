// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.host;

import com.reality.hafnium.HafniumConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hosting this world for friends: who can join, start and stop, and the code
 * to share.
 */
public class HostScreen extends Screen {

    private static final int W = 220;
    private final Screen parent;
    private Hosting.State shown;
    private String shownCode;
    private boolean copied;

    public HostScreen(Screen parent) {
        super(Component.literal("Host this world"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shown = Hosting.state;
        shownCode = Hosting.code;
        int left = this.width / 2 - W / 2;
        boolean isPublic = "public".equals(HafniumConfig.hostVisibility);
        this.addRenderableWidget(Button.builder(Component.literal(isPublic ? "Who can join: Anyone with the code" : "Who can join: Friends only"),
                b -> {
                    Hosting.setVisibility(isPublic ? "private" : "public");
                    rebuildWidgets();
                }).bounds(left, 40, W, 20).build());

        int y = this.height / 2 + 10;
        switch (Hosting.state) {
            case OFF, FAILED -> this.addRenderableWidget(Button.builder(Component.literal("Start hosting"), b -> {
                Hosting.start();
                rebuildWidgets();
            }).bounds(left, y, W, 20).build());
            case STARTING -> {
                Button wait = Button.builder(Component.literal("Starting..."), b -> { }).bounds(left, y, W, 20).build();
                wait.active = false;
                this.addRenderableWidget(wait);
            }
            case HOSTING -> {
                this.addRenderableWidget(Button.builder(Component.literal(copied ? "Copied!" : "Copy code"), b -> {
                    this.minecraft.keyboardHandler.setClipboard(Hosting.code);
                    copied = true;
                    rebuildWidgets();
                }).bounds(left, y, W / 2 - 2, 20).build());
                this.addRenderableWidget(Button.builder(Component.literal("Stop hosting"), b -> {
                    Hosting.stop();
                    copied = false;
                    rebuildWidgets();
                }).bounds(left + W / 2 + 2, y, W / 2 - 2, 20).build());
            }
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (shown != Hosting.state || (shownCode == null ? Hosting.code != null : !shownCode.equals(Hosting.code))) rebuildWidgets();
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = this.width / 2;
        g.centeredText(this.font, "Host this world", cx, 12, 0xFFFFFFFF);
        g.centeredText(this.font, "Your computer runs the world. Friends join with Hafnium.", cx, 24, 0xFFAAAAB4);
        boolean isPublic = "public".equals(HafniumConfig.hostVisibility);
        g.centeredText(this.font, isPublic ? "Anyone you give the code to can join." : "Only your Hafnium friends can join.", cx, 66, 0xFFAAAAB4);

        int y = this.height / 2 - 30;
        switch (Hosting.state) {
            case OFF -> g.centeredText(this.font, "Not hosting.", cx, y, 0xFFAAAAB4);
            case STARTING -> g.centeredText(this.font, "Asking your router to let friends in...", cx, y, 0xFFAAAAB4);
            case FAILED -> {
                g.centeredText(this.font, this.font.plainSubstrByWidth(Hosting.problem == null ? "Hosting didn't start." : Hosting.problem, this.width - 20), cx, y - 6, 0xFFFF8080);
                g.centeredText(this.font, "Friends on your Wi-Fi can still join through LAN.", cx, y + 6, 0xFFAAAAB4);
            }
            case HOSTING -> {
                g.centeredText(this.font, "Your world's code:", cx, y - 8, 0xFFAAAAB4);
                g.pose().pushMatrix();
                g.pose().translate(cx, y + 6);
                g.pose().scale(2f, 2f);
                g.centeredText(this.font, Hosting.code == null ? "" : Hosting.code, 0, 0, 0xFF7CE07C);
                g.pose().popMatrix();
                g.centeredText(this.font, isPublic ? "Anyone with the code can see your internet address."
                        : "Friends who join can see your internet address.", cx, y + 30, 0xFF8A8A92);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
