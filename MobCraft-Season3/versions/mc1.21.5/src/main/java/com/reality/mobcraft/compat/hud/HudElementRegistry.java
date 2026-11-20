package com.reality.mobcraft.compat.hud;

import java.util.function.Function;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.5: the newer Fabric HUD registry, built on this version's layered HUD.
 * Each call adds its layer when the game builds the HUD.
 */
public final class HudElementRegistry {

    private HudElementRegistry() {}

    /** Under everything else on the HUD. */
    public static void addFirst(ResourceLocation id, HudElement element) {
        HudLayerRegistrationCallback.EVENT.register(drawer ->
                drawer.attachLayerBefore(IdentifiedLayer.MISC_OVERLAYS, id, element::render));
    }

    /** Over everything else on the HUD. */
    public static void addLast(ResourceLocation id, HudElement element) {
        HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.addLayer(IdentifiedLayer.of(id, element::render)));
    }

    public static void attachElementBefore(ResourceLocation before, ResourceLocation id, HudElement element) {
        HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerBefore(before, id, element::render));
    }

    public static void attachElementAfter(ResourceLocation after, ResourceLocation id, HudElement element) {
        HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.attachLayerAfter(after, id, element::render));
    }

    /** Swaps a vanilla layer for one built from it. */
    public static void replaceElement(ResourceLocation id, Function<HudElement, HudElement> replacer) {
        HudLayerRegistrationCallback.EVENT.register(drawer -> drawer.replaceLayer(id, old -> {
            HudElement replaced = replacer.apply(old::render);
            return IdentifiedLayer.of(id, replaced::render);
        }));
    }
}
