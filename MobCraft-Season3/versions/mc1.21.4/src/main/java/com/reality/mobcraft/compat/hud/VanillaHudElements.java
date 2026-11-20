package com.reality.mobcraft.compat.hud;

import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.resources.ResourceLocation;

/** 1.21.5: the vanilla HUD layers MobCraft places its own around, under the newer Fabric names. */
public final class VanillaHudElements {
    public static final ResourceLocation CHAT = IdentifiedLayer.CHAT;
    public static final ResourceLocation CROSSHAIR = IdentifiedLayer.CROSSHAIR;

    private VanillaHudElements() {}
}
