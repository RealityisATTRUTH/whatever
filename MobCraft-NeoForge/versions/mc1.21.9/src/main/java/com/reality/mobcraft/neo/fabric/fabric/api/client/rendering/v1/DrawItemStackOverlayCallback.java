package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public interface DrawItemStackOverlayCallback {
    Event<DrawItemStackOverlayCallback> EVENT = new Event<>(l -> (g, f, s, x, y) -> { for (DrawItemStackOverlayCallback c : l) c.onDrawItemStackOverlay(g, f, s, x, y); });
    void onDrawItemStackOverlay(GuiGraphics context, Font textRenderer, ItemStack stack, int x, int y);
}
