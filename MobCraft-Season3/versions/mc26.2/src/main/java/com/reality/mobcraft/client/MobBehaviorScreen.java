package com.reality.mobcraft.client;

import com.reality.mobcraft.net.ToggleBehaviorPayload;
import com.reality.mobcraft.rts.MobBehavior;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Second page of MobCraft Settings: how your own mobs behave.
 * Follow Me, Defend Me and Attack What I Hit, each ON or OFF.
 */
public class MobBehaviorScreen extends Screen {

    private static final int WIDTH = 220;

    private final Screen parent;

    public MobBehaviorScreen(Screen parent) {
        super(Component.literal("Mob Behavior"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - WIDTH / 2;
        int y = 50;
        for (String behavior : MobBehavior.ALL) {
            this.addRenderableWidget(Button.builder(label(behavior, isOn(behavior)), button -> {
                boolean turnOn = !isOn(behavior);
                ClientPlayNetworking.send(new ToggleBehaviorPayload(behavior, turnOn));
                // Show the new state straight away. The server's copy syncs back a moment later.
                button.setMessage(label(behavior, turnOn));
            }).bounds(left, y, WIDTH, 20).build());
            y += 24;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.onClose())
                .bounds(left, y + 10, WIDTH, 20).build());
    }

    private boolean isOn(String behavior) {
        return this.minecraft.player == null || MobBehavior.isOn(this.minecraft.player, behavior);
    }

    private static Component label(String behavior, boolean on) {
        return Component.literal(MobBehavior.displayName(behavior) + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int center = this.width / 2;
        graphics.centeredText(this.font, "Mob Behavior", center, 12, 0xFFFFFFFF);
        graphics.centeredText(this.font, "How the mobs you own act: escorts, vexes, your pod and more.", center, 24, 0xFFAAAAAA);
        graphics.centeredText(this.font, "Mobs you give an RTS order hold that spot for 60 seconds.", center, 36, 0xFF808080);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
