package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface HudElement {
    void extractRenderState(GuiGraphicsExtractor context, DeltaTracker tickCounter);
}
