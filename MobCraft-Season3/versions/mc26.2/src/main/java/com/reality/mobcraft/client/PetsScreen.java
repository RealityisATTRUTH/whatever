package com.reality.mobcraft.client;

import com.reality.mobcraft.mob.MobAttachments;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * My Pets: every pet you have, from the Team & Pets screen.
 *
 * Each row shows the pet's name, kills, and whether it's sitting or following and
 * how far away it is, or "far away" if it isn't loaded near you. Sit / Stand toggles
 * it, and Release lets it go after a second click to confirm. Both run /pets sit or
 * /pets release with the pet's id, so they only work on pets loaded near you.
 *
 * The list comes from the PET_LIST attachment, which the server updates every
 * second, and the screen redraws when it changes. Six pets per page.
 */
public class PetsScreen extends Screen {

    private static final int WIDTH = 300;
    private static final int ROW = 24;
    private static final int PER_PAGE = 6;
    private static final int TOP = 44;
    private static final int CONFIRM_TICKS = 60;

    private final Screen parent;
    private List<String> shown = List.of();
    private int page;
    private String armedRelease;
    private int armedTicks;

    public PetsScreen(Screen parent) {
        super(Component.literal("My Pets"));
        this.parent = parent;
    }

    private List<String> lines() {
        if (this.minecraft == null || this.minecraft.player == null) return List.of();
        return this.minecraft.player.getAttachedOrElse(MobAttachments.PET_LIST, List.of());
    }

    @Override
    protected void init() {
        shown = new ArrayList<>(lines());
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pages) page = pages - 1;
        int left = this.width / 2 - WIDTH / 2;

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= shown.size()) break;
            String[] pet = shown.get(index).split("\\|", -1);
            if (pet.length < 5) continue;
            String id = pet[0];
            boolean loaded = !pet[3].equals("-1");
            boolean sitting = pet[3].equals("1");
            int y = TOP + i * ROW;

            Button sit = this.addRenderableWidget(Button.builder(Component.literal(sitting ? "Stand" : "Sit"),
                    b -> run("pets sit " + id)).bounds(left + WIDTH - 126, y, 56, 20).build());
            sit.active = loaded;
            boolean armed = id.equals(armedRelease);
            Button release = this.addRenderableWidget(Button.builder(Component.literal(armed ? "Sure?" : "Release"), b -> {
                if (id.equals(armedRelease)) {
                    armedRelease = null;
                    run("pets release " + id);
                } else {
                    armedRelease = id;
                    armedTicks = CONFIRM_TICKS;
                    this.rebuildWidgets();
                }
            }).bounds(left + WIDTH - 66, y, 66, 20).build());
            release.active = loaded;
        }

        int bottom = TOP + PER_PAGE * ROW + 4;
        if (pages > 1) {
            this.addRenderableWidget(Button.builder(Component.literal("< Back"), b -> {
                page--;
                this.rebuildWidgets();
            }).bounds(left, bottom, 70, 20).build()).active = page > 0;
            this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
                page++;
                this.rebuildWidgets();
            }).bounds(left + WIDTH - 70, bottom, 70, 20).build()).active = page < pages - 1;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(left + WIDTH / 2 - 50, bottom + 26, 100, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (armedRelease != null && --armedTicks <= 0) {
            armedRelease = null;
            this.rebuildWidgets();
        }
        if (!lines().equals(shown)) this.rebuildWidgets();
    }

    private void run(String command) {
        if (this.minecraft.player != null) this.minecraft.player.connection.sendCommand(command);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int center = this.width / 2;
        int left = center - WIDTH / 2;
        graphics.centeredText(this.font, "My Pets", center, 12, 0xFFFFFFFF);

        if (shown.isEmpty()) {
            graphics.centeredText(this.font, "No pets yet.", center, 30, 0xFFAAAAAA);
            graphics.centeredText(this.font, "Claim your own kind: sneak + right-click, empty hand.", center, TOP, 0xFF888888);
            return;
        }
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        graphics.centeredText(this.font, shown.size() + (shown.size() == 1 ? " pet" : " pets")
                + (pages > 1 ? "   page " + (page + 1) + " of " + pages : ""), center, 28, 0xFFAAAAAA);

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= shown.size()) break;
            String[] pet = shown.get(index).split("\\|", -1);
            if (pet.length < 5) continue;
            int y = TOP + i * ROW;
            String kills = pet[2] + (pet[2].equals("1") ? " kill" : " kills");
            String where = pet[3].equals("-1") ? "far away"
                    : (pet[3].equals("1") ? "sitting, " : "following, ") + pet[4] + " blocks";
            graphics.text(this.font, pet[1], left, y + 1, pet[3].equals("-1") ? 0xFF808080 : 0xFF55FF55, true);
            graphics.text(this.font, kills + ", " + where, left, y + 11, 0xFFAAAAAA, false);
        }
    }
}
