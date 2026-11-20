// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import com.reality.hafnium.social.FriendsScreen;
import com.reality.hafnium.host.HostScreen;
import com.reality.hafnium.cosmetics.Cosmetics;
import com.reality.hafnium.cosmetics.WardrobeScreen;
import com.reality.hafnium.host.Hosting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.gui.screens.PauseScreen;
import com.reality.hafnium.social.Social;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hafnium: a cleaner Minecraft menu you can make your own.
 *
 * This first version runs only on your computer and needs no servers. It
 * gives buttons and sliders a flat gray look with colours you can pick, and
 * lets you drag or hide any button on the title screen and pause menu,
 * Essential's included. The small "Hf" button at the top left opens the
 * settings.
 *
 * Hafnium is free software under the GNU GPL, version 3 or later.
 */
public final class Hafnium implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("hafnium");

    /** "Friends", with how many new messages and requests are waiting. */
    private static Component friendsLabel() {
        int waiting = Social.unreadTotal();
        return waiting > 0 ? Component.literal("Friends (" + waiting + ")") : Component.literal("Friends");
    }

    private static Component hostLabel() {
        return Component.literal(Hosting.state == Hosting.State.HOSTING ? "Hosting" : "Host");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        HafniumConfig.load();
        // Draws 3D cosmetics on every player.
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
                helper.register(new com.reality.hafnium.cosmetics.ModelLayer((net.minecraft.client.renderer.entity.RenderLayerParent) avatar));
            }
        });
        // A private hosted world only lets in Hafnium friends and players the Hafnium server sent.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!Hosting.lets(handler.player, server)) {
                handler.disconnect(Component.literal("This world is private. Ask the host to add you as a Hafnium friend."));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Hosting.stop());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && client.level.getGameTime() % 20 == 0) Hosting.syncLists();
            if (client.level != null && client.level.getGameTime() % 10 == 0) Cosmetics.tick();
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (Layout.screenKey(screen) == null) return;
            // Sits next to Menu Trim's "Menu" button when both mods are installed.
            int x = FabricLoader.getInstance().isModLoaded("menutrim") ? 52 : 4;
            Button open = Button.builder(Component.literal("Hf"), b -> client.gui.setScreen(new HafniumScreen(screen)))
                    .bounds(x, 4, 20, 20)
                    .tooltip(Tooltip.create(Component.literal("Hafnium settings")))
                    .build();
            Layout.markOwn(open);
            Screens.getWidgets(screen).add(open);
            Button friends = Button.builder(friendsLabel(), b -> client.gui.setScreen(new FriendsScreen(screen)))
                    .bounds(x + 24, 4, 64, 20)
                    .tooltip(Tooltip.create(Component.literal("Hafnium friends and chat")))
                    .build();
            Layout.markOwn(friends);
            Screens.getWidgets(screen).add(friends);
            if (screen instanceof TitleScreen) Social.start();
            Button host = null;
            if (screen instanceof PauseScreen && Hosting.canHost()) {
                host = Button.builder(hostLabel(), b -> client.gui.setScreen(new HostScreen(screen)))
                        .bounds(x + 92, 4, 54, 20)
                        .tooltip(Tooltip.create(Component.literal("Host this world for friends")))
                        .build();
                Layout.markOwn(host);
                Screens.getWidgets(screen).add(host);
            }
            Button hostButton = host;
            Button wardrobe = Button.builder(Component.literal("Wardrobe"), b -> client.gui.setScreen(new WardrobeScreen(screen)))
                    .bounds(host != null ? x + 150 : x + 92, 4, 60, 20)
                    .tooltip(Tooltip.create(Component.literal("Capes, skins and 3D models made by players")))
                    .build();
            Layout.markOwn(wardrobe);
            Screens.getWidgets(screen).add(wardrobe);

            Layout.afterInit(screen);
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, delta) -> {
                Layout.apply(s);
                friends.setMessage(friendsLabel());
                if (hostButton != null) hostButton.setMessage(hostLabel());
            });
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> Layout.drawEditOverlay(s, g, mx, my));
            ScreenMouseEvents.allowMouseClick(screen).register(Layout::onClick);
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> Layout.onDrag(s, event));
            ScreenMouseEvents.allowMouseRelease(screen).register(Layout::onRelease);
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> !Layout.isEditing(s));
            ScreenKeyboardEvents.allowKeyPress(screen).register(Layout::onKey);
            ScreenEvents.remove(screen).register(s -> {
                if (Layout.isEditing(s)) Layout.stopEditing();
            });
        });
    }
}
