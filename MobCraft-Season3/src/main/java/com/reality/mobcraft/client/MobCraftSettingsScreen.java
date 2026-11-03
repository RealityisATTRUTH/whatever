package com.reality.mobcraft.client;

import com.reality.mobcraft.mob.MobAbilities.Ability;
import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.net.ToggleAbilityPayload;
import com.reality.mobcraft.net.ToggleBehaviorPayload;
import com.reality.mobcraft.mob.kit.BlazeKit;
import com.reality.mobcraft.warden.Senses;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * MobCraft Settings, opened with /mobcraftsettings.
 *
 * One button per ability of the player's own mob. Clicking it switches the
 * ability on or off. The setting is saved on the server and kept through
 * death, and resets to all on when the player is given a different mob.
 *
 * Echolocation Markers switches the vibration markers and blue glow from the
 * Warden's senses and from bat ear or warden tendril helmets on or off.
 *
 * Mob Behavior... opens the second page, where you choose whether your mobs
 * follow you, defend you and attack what you hit.
 */
public class MobCraftSettingsScreen extends Screen {

    private static final int WIDTH = 220;

    private String youAre = "Normal";

    public MobCraftSettingsScreen() {
        super(Component.literal("MobCraft Settings"));
    }

    @Override
    protected void init() {
        MobType type = this.minecraft.player == null ? null : PlayerMob.get(this.minecraft.player);
        youAre = type == null ? "Normal" : type.displayName;

        int left = this.width / 2 - WIDTH / 2;
        int y = 50;
        List<Ability> abilities = MobAbilities.get(type);
        for (Ability ability : abilities) {
            this.addRenderableWidget(Button.builder(label(ability, isOn(ability)), button -> {
                boolean turnOn = !isOn(ability);
                ClientPlayNetworking.send(new ToggleAbilityPayload(ability.id(), turnOn));
                // Show the new state straight away. The server's copy syncs back a moment later.
                button.setMessage(label(ability, turnOn));
            }).bounds(left, y, WIDTH, 20).build());
            y += 24;
        }

        // Blaze only: Fireballs from a blaze rod as well as the key.
        if (type == MobType.BLAZE) {
            this.addRenderableWidget(Button.builder(rodLabel(rodOn()), button -> {
                boolean turnOn = !rodOn();
                ClientPlayNetworking.send(new ToggleBehaviorPayload(BlazeKit.ROD_SETTING, turnOn));
                button.setMessage(rodLabel(turnOn));
            }).bounds(left, y, WIDTH, 20).build());
            y += 24;
        }

        int bottom = Math.max(y + 10, 80);
        // For everyone: the Warden's senses and bat ear or warden tendril helmets.
        this.addRenderableWidget(Button.builder(sensesLabel(sensesOn()), button -> {
            boolean turnOn = !sensesOn();
            ClientPlayNetworking.send(new ToggleBehaviorPayload(Senses.SETTING, turnOn));
            button.setMessage(sensesLabel(turnOn));
        }).bounds(left, bottom, WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Mob Behavior..."),
                button -> this.minecraft.gui.setScreen(new MobBehaviorScreen(this))
        ).bounds(left, bottom + 24, WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(left, bottom + 48, WIDTH, 20).build());
    }

    private boolean isOn(Ability ability) {
        return this.minecraft.player == null || MobAbilities.isEnabled(this.minecraft.player, ability.id());
    }

    private boolean rodOn() {
        return this.minecraft.player != null && BlazeKit.rodFiring(this.minecraft.player);
    }

    private static Component rodLabel(boolean on) {
        return Component.literal("Blaze Rod Fireballs: " + (on ? "ON" : "OFF"));
    }

    private boolean sensesOn() {
        return this.minecraft.player == null || Senses.isOn(this.minecraft.player);
    }

    private static Component sensesLabel(boolean on) {
        return Component.literal("Echolocation Markers: " + (on ? "ON" : "OFF"));
    }

    private static Component label(Ability ability, boolean on) {
        return Component.literal(ability.displayName() + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int center = this.width / 2;
        graphics.centeredText(this.font, "MobCraft Settings", center, 12, 0xFFFFFFFF);
        graphics.centeredText(this.font, "You Are: " + youAre, center, 24, 0xFFAAAAAA);

        MobType type = this.minecraft.player == null ? null : PlayerMob.get(this.minecraft.player);
        if (type == null) {
            graphics.centeredText(this.font, "You don't have a mob, so there's nothing to set.", center, 54, 0xFFAAAAAA);
        } else if (MobAbilities.get(type).isEmpty()) {
            graphics.centeredText(this.font, "Your mob has no abilities to switch yet.", center, 54, 0xFFAAAAAA);
        } else {
            graphics.centeredText(this.font, "Click an ability to switch it on or off.", center, 36, 0xFF808080);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
