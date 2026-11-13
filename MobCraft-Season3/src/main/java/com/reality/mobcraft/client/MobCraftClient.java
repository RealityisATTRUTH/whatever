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

    private static boolean hushedLastTick;

    @Override
    public void onInitializeClient() {
        SkybreakerClient.register();
        // Gloamite tools list their traits in the tooltip.
        // Tiers whose effect isn't an attribute (bow and troll tiers) say what they do under the name.
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            com.reality.mobcraft.item.Tiers.Tier tier = com.reality.mobcraft.item.Tiers.tier(stack);
            if (tier == null || tier.desc() == null || lines.isEmpty()) return;
            lines.add(1, net.minecraft.network.chat.Component.literal(tier.desc()).withStyle(tier.color()));
        });
        // Itchy armor: now and then you stop to scratch, and the camera shakes a little.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var p = client.player;
            if (p == null || client.isPaused() || !com.reality.mobcraft.item.Tiers.wearing(p, "itchy")) return;
            if (p.getRandom().nextInt(1200) != 0) return;
            p.swing(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            SkybreakerClient.shake = Math.max(SkybreakerClient.shake, 8);
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.6f, false);
        });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            String kind = com.reality.mobcraft.item.GloamiteTraits.kind(stack);
            if (kind == null) return;
            int at = Math.min(1, lines.size());
            for (String t : com.reality.mobcraft.item.GloamiteTraits.rolled(stack)) {
                lines.add(at++, net.minecraft.network.chat.Component.literal(t).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)
                        .append(net.minecraft.network.chat.Component.literal(" - " + com.reality.mobcraft.item.GloamiteTraits.WHAT.get(t))
                                .withStyle(net.minecraft.ChatFormatting.GRAY)));
            }
        });
        // The Unseenhush: a sound nearby restarts your mining, and being silenced stops what's already playing.
        ClientPlayNetworking.registerGlobalReceiver(com.reality.mobcraft.net.HushResetPayload.TYPE, (payload, context) -> {
            var gm = (com.reality.mobcraft.mixin.client.MultiPlayerGameModeAccessor) context.client().gameMode;
            if (gm != null && payload.pos().equals(gm.mobcraft$destroyBlockPos())) {
                gm.mobcraft$setDestroyProgress(0);
                gm.mobcraft$setDestroyTicks(0);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean now = client.player != null && com.reality.mobcraft.item.Hush.hushed(client.player);
            if (now && !hushedLastTick) client.getSoundManager().stop();
            hushedLastTick = now;
        });
        // Silenced: say so on screen, so it doesn't look like your sound broke.
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(com.reality.mobcraft.MobCraft.id("hushed"), (graphics, tick) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            Long until = mc.player.getAttached(com.reality.mobcraft.item.Hush.HUSHED);
            if (until == null) return;
            long left = until - mc.player.level().getGameTime();
            if (left <= 0) return;
            graphics.centeredText(mc.font, "The world is silent  " + (left / 20 + 1) + "s",
                    graphics.guiWidth() / 2, 12, 0xFF9BE59B);
        });
        HudConfig.load();
        AbilityKeys.register();
        EnergyHud.register();
        SenseMarkers.register();
        BlindOverlay.register();
        HelmetUpgradeLayer.register();
        SkullVisorLayer.register();
        RabbitDisguise.register();
        // The thrown Ghast Wisp: its 3D model spinning and tumbling through the air.
        // Creeper Essence: the charged item you threw, drawn full bright.
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.reality.mobcraft.item.CreeperEssence.ENTITY,
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 1.0f, true));
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.reality.mobcraft.item.GhastWisp.ENTITY,
                GhastWispRenderer::new);
        MobLookLayer.register();
        EvokerChainRenderer.register();
        TomeChainRenderer.register();
        TomeWordRenderer.register();
        ShadowHandRenderer.register();
        GearEnchantsClient.register();
        // Binding Word: looking down and attacking while chained hits the chains, not the block.
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide() || !com.reality.mobcraft.item.TomeBinding.isBound(player)
                    || player.getXRot() < com.reality.mobcraft.item.TomeBinding.LOOK_DOWN_ANGLE) return net.minecraft.world.InteractionResult.PASS;
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.reality.mobcraft.net.TomeChainHitPayload());
            return net.minecraft.world.InteractionResult.FAIL;
        });
        EssenceOrbs.register();
        ArmorIconOverlay.register();
        UpdateLogPanel.register();
        MobGuideScreen.register();


        ClientPlayNetworking.registerGlobalReceiver(TeamPingPayload.TYPE, (payload, context) ->
                TeamPings.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(GlowPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientGlow.apply(payload)));

        ClientPlayNetworking.registerGlobalReceiver(com.reality.mobcraft.net.EssenceOrbPayload.TYPE, (payload, context) ->
                context.client().execute(() -> EssenceOrbs.add(payload)));

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
            // Charged items in flight glow orange.
            if (client.level != null) {
                for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
                    if (e instanceof com.reality.mobcraft.item.CreeperEssence.Thrown) {
                        ClientGlow.apply(new GlowPayload(e.getId(), 0xFF8A1E, 3));
                    }
                }
            }
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
