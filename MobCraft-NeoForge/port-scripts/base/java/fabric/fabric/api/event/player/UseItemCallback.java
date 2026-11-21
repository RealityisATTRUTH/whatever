package com.reality.mobcraft.neo.fabric.fabric.api.event.player;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public interface UseItemCallback {
    Event<UseItemCallback> EVENT = new Event<>(l -> (p, w, h) -> {
        for (UseItemCallback c : l) { InteractionResult r = c.interact(p, w, h); if (r != InteractionResult.PASS) return r; }
        return InteractionResult.PASS;
    });
    InteractionResult interact(Player player, Level world, InteractionHand hand);
}
