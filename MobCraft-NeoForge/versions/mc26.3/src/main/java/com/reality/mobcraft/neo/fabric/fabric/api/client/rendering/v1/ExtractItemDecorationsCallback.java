package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public interface ExtractItemDecorationsCallback {
    Event<ExtractItemDecorationsCallback> EVENT = new Event<>(l -> (g, f, s, x, y) -> { for (ExtractItemDecorationsCallback c : l) c.onExtractItemDecorations(g, f, s, x, y); });
    void onExtractItemDecorations(GuiGraphicsExtractor context, Font textRenderer, ItemStack stack, int x, int y);
}
