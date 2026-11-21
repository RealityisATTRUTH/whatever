package com.reality.mobcraft.neo.fabric.fabric.api.registry;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.world.item.alchemy.PotionBrewing;

public interface FabricPotionBrewingBuilder {
    Event<BuildCallback> BUILD = new Event<>(l -> b -> { for (BuildCallback c : l) c.build(b); });
    interface BuildCallback { void build(PotionBrewing.Builder builder); }
}
