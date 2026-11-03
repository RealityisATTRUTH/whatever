package com.reality.mobcraft.client;

import com.reality.mobcraft.mob.MobItems;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.net.GiveItemPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mob Item Menu, opened from the M button in the inventory.
 *
 * Shows the current item, a Switch Item button to cycle through the player's
 * own mob's items, a slider for the amount, a Give Items button, and a
 * Drop Items ON/OFF toggle. With it on, Give Items throws the items on the
 * ground in front of you instead, for hoppers and auto crafters. The toggle
 * is saved in config/mobcraft-client.json.
 * The server double-checks every request, so a modified client can't ask
 * for another mob's items.
 */
public class MobItemMenuScreen extends Screen {

    private static final int WIDTH = 200;

    /** Remembered between openings so the menu comes back the way you left it. */
    private static int lastIndex = 0;
    private static int lastAmount = AmountSlider.MAX;

    private final Screen parent;
    private final List<String> items = new ArrayList<>();
    private String mobName = "";
    private int index = 0;

    private Button switchButton;
    private AmountSlider slider;

    public MobItemMenuScreen(Screen parent) {
        super(Component.literal("Mob Item Menu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        items.clear();
        MobType type = this.minecraft.player == null ? null : PlayerMob.get(this.minecraft.player);
        if (type != null) {
            mobName = type.displayName;
            items.addAll(MobItems.get(type));
        }
        index = items.isEmpty() ? 0 : Math.min(lastIndex, items.size() - 1);

        int left = this.width / 2 - WIDTH / 2;
        int y = 64;

        if (!items.isEmpty()) {
            switchButton = this.addRenderableWidget(Button.builder(switchLabel(), button -> {
                index = (index + 1) % items.size();
                lastIndex = index;
                button.setMessage(switchLabel());
            }).bounds(left, y, WIDTH, 20).build());
            y += 26;

            slider = this.addRenderableWidget(new AmountSlider(left, y, WIDTH, 20, lastAmount));
            y += 26;

            int half = (WIDTH - 4) / 2;
            this.addRenderableWidget(Button.builder(Component.literal("Give Items"), button -> {
                lastAmount = slider.amount();
                ClientPlayNetworking.send(new GiveItemPayload(items.get(index), slider.amount(), HudConfig.dropGivenItems()));
            }).bounds(left, y, half, 20).build());
            this.addRenderableWidget(Button.builder(dropLabel(), button -> {
                HudConfig.setDropGivenItems(!HudConfig.dropGivenItems());
                button.setMessage(dropLabel());
            }).bounds(left + half + 4, y, WIDTH - half - 4, 20).build());
            y += 34;
        } else {
            y += 26;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.onClose())
                .bounds(left, y, WIDTH, 20).build());
    }

    /** On: items are thrown on the ground, handy for hoppers and auto crafters. */
    private static Component dropLabel() {
        return Component.literal("Drop Items: " + (HudConfig.dropGivenItems() ? "ON" : "OFF"));
    }

    private Component switchLabel() {
        return Component.literal("Switch Item (" + (index + 1) + "/" + items.size() + ")");
    }

    @Override
    public void onClose() {
        if (slider != null) lastAmount = slider.amount();
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int center = this.width / 2;
        graphics.centeredText(this.font, "Mob Item Menu", center, 12, 0xFFFFFFFF);
        graphics.centeredText(this.font, mobName.isEmpty() ? "You don't have a mob." : mobName,
                center, 24, 0xFFAAAAAA);

        if (items.isEmpty()) {
            if (!mobName.isEmpty()) {
                graphics.centeredText(this.font, "Your mob has no items to give itself.", center, 46, 0xFFAAAAAA);
            }
            return;
        }
        graphics.centeredText(this.font, "Current Item: " + MobItems.prettyName(items.get(index)),
                center, 46, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
