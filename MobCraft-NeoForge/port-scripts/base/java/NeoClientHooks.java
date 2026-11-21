package com.reality.mobcraft.neo;

import com.reality.mobcraft.neo.fabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.reality.mobcraft.neo.fabric.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.IdentifiedLayer;
import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.client.screen.v1.ScreenEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.client.screen.v1.ScreenMouseEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.event.client.player.ClientPreAttackCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.networking.v1.PacketSender;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client half of the event bridge. */
final class NeoClientHooks {
    static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> KeyBindingHelper.KEYS.forEach(e::register));
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) ->
                EntityModelLayerRegistry.LAYERS.forEach((loc, p) -> e.registerLayerDefinition(loc, p::createModelData)));
        modBus.addListener(NeoClientHooks::addLayers);
        modBus.addListener(NeoClientHooks::renderers);
        modBus.addListener(NeoClientHooks::guiLayers);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Pre e) -> ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(Minecraft.getInstance()));
        bus.addListener((ClientTickEvent.Post e) -> ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(Minecraft.getInstance()));
        bus.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            PacketSender sender = com.reality.mobcraft.neo.fabric.fabric.api.client.networking.v1.ClientPlayNetworking::send;
            ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(e.getPlayer().connection, sender, Minecraft.getInstance());
        });
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) ->
                ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(Minecraft.getInstance().getConnection(), Minecraft.getInstance()));

        bus.addListener((ScreenEvent.Init.Pre e) -> {
            ScreenEvents.clear(e.getScreen());
            Minecraft mc = Minecraft.getInstance();
            ScreenEvents.BEFORE_INIT.invoker().beforeInit(mc, e.getScreen(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
        bus.addListener((ScreenEvent.Init.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            ScreenEvents.AFTER_INIT.invoker().afterInit(mc, e.getScreen(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
        bus.addListener((ScreenEvent.Render.Pre e) -> {
            var ev = ScreenEvents.BEFORE_RENDER.get(e.getScreen());
            if (ev != null) ev.invoker().beforeRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick());
        });
        bus.addListener((ScreenEvent.Render.Post e) -> {
            var ev = ScreenEvents.AFTER_RENDER.get(e.getScreen());
            if (ev != null) ev.invoker().afterRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick());
        });
        bus.addListener((ScreenEvent.Closing e) -> {
            var ev = ScreenEvents.REMOVE.get(e.getScreen());
            if (ev != null) ev.invoker().onRemove(e.getScreen());
        });
        bus.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            var ev = ScreenMouseEvents.CLICK.get(e.getScreen());
            if (ev != null && !ev.invoker().allowMouseClick(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.MouseButtonReleased.Pre e) -> {
            var ev = ScreenMouseEvents.RELEASE.get(e.getScreen());
            if (ev != null && !ev.invoker().allowMouseRelease(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.MouseDragged.Pre e) -> {
            var ev = ScreenMouseEvents.DRAG.get(e.getScreen());
            if (ev != null && !ev.invoker().allowMouseDrag(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getMouseButton(), e.getDragX(), e.getDragY())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.MouseScrolled.Pre e) -> {
            var ev = ScreenMouseEvents.SCROLL.get(e.getScreen());
            if (ev != null && !ev.invoker().allowMouseScroll(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getScrollDeltaX(), e.getScrollDeltaY())) e.setCanceled(true);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) ->
                com.reality.mobcraft.neo.fabric.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.invoker().getTooltip(e.getItemStack(), e.getContext(), e.getFlags(), e.getToolTip()));
        bus.addListener((net.neoforged.neoforge.client.event.RenderLevelStageEvent e) -> {
            if (e.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            var buffers = mc.renderBuffers().bufferSource();
            com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES.invoker().beforeEntities(
                    new com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.WorldRenderEvents.Context(buffers, mc.level, e.getPoseStack(), e.getPartialTick()));
            buffers.endLastBatch();
        });
        bus.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!e.isAttack() || mc.player == null) return;
            if (ClientPreAttackCallback.EVENT.invoker().onClientPlayerPreAttack(mc, mc.player, 1)) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayers(EntityRenderersEvent.AddLayers e) {
        var cb = LivingEntityFeatureRendererRegistrationCallback.EVENT.invoker();
        for (EntityType<?> type : e.getEntityTypes()) {
            if (e.getRenderer(type) instanceof LivingEntityRenderer r) cb.registerRenderers((EntityType<? extends LivingEntity>) type, r, helper(r), e.getContext());
        }
        for (var skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof LivingEntityRenderer r) cb.registerRenderers(EntityType.PLAYER, r, helper(r), e.getContext());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper helper(LivingEntityRenderer r) {
        return new LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper() {
            @Override public void register(RenderLayer layer) { r.addLayer(layer); }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.EntityRendererRegistry.RENDERERS.forEach((t, p) -> e.registerEntityRenderer((EntityType) t, (net.minecraft.client.renderer.entity.EntityRendererProvider) p));
    }

    private static void guiLayers(RegisterGuiLayersEvent e) {
        HudLayerRegistrationCallback.EVENT.invoker().register(new HudLayerRegistrationCallback.LayeredDrawerWrapper() {
            public HudLayerRegistrationCallback.LayeredDrawerWrapper addLayer(IdentifiedLayer layer) { e.registerAboveAll(layer.id(), layer); return this; }
            public HudLayerRegistrationCallback.LayeredDrawerWrapper attachLayerBefore(ResourceLocation before, IdentifiedLayer layer) { e.registerBelow(before, layer.id(), layer); return this; }
            public HudLayerRegistrationCallback.LayeredDrawerWrapper attachLayerAfter(ResourceLocation after, IdentifiedLayer layer) { e.registerAbove(after, layer.id(), layer); return this; }
            public HudLayerRegistrationCallback.LayeredDrawerWrapper replaceLayer(ResourceLocation id, Function<IdentifiedLayer, IdentifiedLayer> replacer) {
                e.wrapLayer(id, orig -> replacer.apply(IdentifiedLayer.of(id, orig))); return this;
            }
        });
    }
}
