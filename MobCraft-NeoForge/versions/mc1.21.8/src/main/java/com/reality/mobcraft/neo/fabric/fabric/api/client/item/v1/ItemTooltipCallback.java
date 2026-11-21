package com.reality.mobcraft.neo.fabric.fabric.api.client.item.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public interface ItemTooltipCallback {
    Event<ItemTooltipCallback> EVENT = new Event<>(l -> (s, c, f, lines) -> { for (ItemTooltipCallback x : l) x.getTooltip(s, c, f, lines); });
    void getTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipFlag tooltipType, List<Component> lines);
}
