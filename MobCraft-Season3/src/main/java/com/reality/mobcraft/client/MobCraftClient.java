package com.reality.mobcraft.client;

import com.reality.mobcraft.client.rts.RtsMarkers;
import com.reality.mobcraft.client.rts.TeamPings;
import com.reality.mobcraft.net.GhostSyncPayload;
import com.reality.mobcraft.net.GlowPayload;
import com.reality.mobcraft.net.ModListPayload;
import com.reality.mobcraft.net.OpenJeffMenuPayload;
import com.reality.mobcraft.net.OpenSettingsPayload;
import com.reality.mobcraft.net.TeamPingPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** MobCraft Season 3, client entrypoint. */
public class MobCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudConfig.load();
        AbilityKeys.register();
        EnergyHud.register();
        SenseMarkers.register();
        BlindOverlay.register();
        HelmetUpgradeLayer.register();
        ArmorIconOverlay.register();
        UpdateLogPanel.register();
        MobGuideScreen.register();


        ClientPlayNetworking.registerGlobalReceiver(TeamPingPayload.TYPE, (payload, context) ->
                TeamPings.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(GlowPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientGlow.apply(payload)));

        ClientPlayNetworking.registerGlobalReceiver(GhostSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientGhosts.sync(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(OpenSettingsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().gui.setScreen(new MobCraftSettingsScreen())));

        ClientPlayNetworking.registerGlobalReceiver(OpenJeffMenuPayload.TYPE, (payload, context) ->
                context.client().execute(() -> context.client().gui.setScreen(new JeffMenuScreen(null))));

        InventoryMobButton.register();
        com.reality.mobcraft.client.rts.RtsView.register();
        KitClient.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientGlow.tick();
            AbilityKeys.tick(client);
            RtsMarkers.tick(client);
            TeamPings.tick(client);
            BlindOverlay.tick(client);
            ClientGhosts.tick(client);
            sendModList(client);
            KitClient.tick(client);
        });

        // Tell the server which mods we have, so it can turn away anti-blindness mods.
        // Sent from the tick, so it goes as soon as the server's channel is known.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> modListSent = false);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientGlow.clear();
            ClientGhosts.reset();
        });
    }

    private static boolean modListSent = true;

    private static void sendModList(Minecraft client) {
        if (modListSent || client.player == null || !ClientPlayNetworking.canSend(ModListPayload.TYPE)) return;
        List<String> mods = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) mods.add(mod.getMetadata().getId());
        ClientPlayNetworking.send(new ModListPayload(mods));
        modListSent = true;
    }
}
