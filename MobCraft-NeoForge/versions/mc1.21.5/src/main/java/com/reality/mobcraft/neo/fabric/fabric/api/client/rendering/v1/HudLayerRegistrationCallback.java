package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import java.util.function.Function;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public interface HudLayerRegistrationCallback {
    Event<HudLayerRegistrationCallback> EVENT = new Event<>(l -> d -> { for (HudLayerRegistrationCallback c : l) c.register(d); });
    void register(LayeredDrawerWrapper drawer);
    interface LayeredDrawerWrapper {
        LayeredDrawerWrapper addLayer(IdentifiedLayer layer);
        LayeredDrawerWrapper attachLayerBefore(ResourceLocation before, IdentifiedLayer layer);
        default LayeredDrawerWrapper attachLayerBefore(ResourceLocation before, ResourceLocation id, LayeredDraw.Layer layer) { return attachLayerBefore(before, IdentifiedLayer.of(id, layer)); }
        LayeredDrawerWrapper attachLayerAfter(ResourceLocation after, IdentifiedLayer layer);
        default LayeredDrawerWrapper attachLayerAfter(ResourceLocation after, ResourceLocation id, LayeredDraw.Layer layer) { return attachLayerAfter(after, IdentifiedLayer.of(id, layer)); }
        LayeredDrawerWrapper replaceLayer(ResourceLocation id, Function<IdentifiedLayer, IdentifiedLayer> replacer);
    }
}
