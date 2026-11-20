package com.reality.mobcraft.client.rts;

import com.reality.mobcraft.MobCraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Keeps RTS mode a clean top-down view: no crosshair, and no first-person
 * hand or held item in the corner of the screen.
 *
 *   Crosshair   the vanilla crosshair HUD element is wrapped so it skips
 *               drawing while RTS mode is open.
 *   Hand        the game never draws the first-person hand in third person,
 *               so RTS switches the camera to third person while it's open
 *               and puts back whatever view you had when it closes. Looking
 *               straight down, third person only lifts the camera a little.
 *   Hit         like Freecam: any damage to your body while RTS is open
 *               closes RTS and puts you back in the view you opened it from,
 *               first person, third person or front. It's a warning that you
 *               could die if you're not watching. Any damage counts: mobs,
 *               players, fire, poison, falling, starving, and hits your armor
 *               or absorption hearts soak up. Creative and spectator don't count.
 */
public final class RtsView {

    private static boolean warned = false;

    private RtsView() {}

    /** Health plus absorption last tick while RTS was open, or -1. */
    private static float lastHealth = -1;
    /** The hurt flash timer last tick, so a hit that took no health still counts. */
    private static int lastHurtTime = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LocalPlayer player = client.player;
            if (player == null || !(client.gui.screen() instanceof RtsScreen rts)) {
                lastHealth = -1;
                lastHurtTime = 0;
                return;
            }
            float health = player.getHealth() + player.getAbsorptionAmount();
            boolean lostHealth = lastHealth >= 0 && health < lastHealth;
            boolean flashed = lastHealth >= 0 && player.hurtTime > lastHurtTime;
            boolean hurt = (lostHealth || flashed) && !player.isCreative() && !player.isSpectator();
            lastHealth = health;
            lastHurtTime = player.hurtTime;
            if (!hurt) return;
            lastHealth = -1;
            lastHurtTime = 0;
            rts.onClose();
            player.sendSystemMessage(Component.literal("You took damage! You were pulled out of RTS. Watch out or you could die.")
                    .withStyle(ChatFormatting.RED));
            player.sendOverlayMessage(Component.literal("You took damage!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        });
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, deltaTracker) -> {
            if (!(Minecraft.getInstance().gui.screen() instanceof RtsScreen)) original.extractRenderState(graphics, deltaTracker);
        });
    }

    private static CameraType savedView = null;

    /** Shows or hides the first-person hand by switching to third person and back. */
    public static void setHandVisible(Minecraft client, boolean visible) {
        if (!visible) {
            if (savedView == null) savedView = client.options.getCameraType();
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (savedView != null) {
            client.options.setCameraType(savedView);
            savedView = null;
        }
        setRenderHand(client, visible);
    }

    /** The old way, kept as a backup: the game renderer's hand switch, found by name. */
    private static void setRenderHand(Minecraft client, boolean visible) {
        Object renderer = client.gameRenderer;
        try {
            Method setter = renderer.getClass().getMethod("setRenderHand", boolean.class);
            setter.invoke(renderer, visible);
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // try the field instead
        }
        for (Class<?> type = renderer.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("renderHand");
                field.setAccessible(true);
                field.setBoolean(renderer, visible);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // not on this class
            }
        }
        if (!warned) {
            warned = true;
            MobCraft.LOGGER.warn("RTS: couldn't find the game renderer's hand switch, so the hand stays visible.");
        }
    }
}
