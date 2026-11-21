package com.reality.mobcraft.compat.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** 1.21.5: one thing drawn on the HUD, shaped like the newer Fabric HudElement. */
@FunctionalInterface
public interface HudElement {
    void render(GuiGraphics graphics, DeltaTracker deltaTracker);
}
