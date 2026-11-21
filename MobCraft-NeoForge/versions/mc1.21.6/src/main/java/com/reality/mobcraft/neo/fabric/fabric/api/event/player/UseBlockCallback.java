package com.reality.mobcraft.neo.fabric.fabric.api.event.player;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public interface UseBlockCallback {
    Event<UseBlockCallback> EVENT = new Event<>(l -> (p, w, h, hit) -> {
        for (UseBlockCallback c : l) { InteractionResult r = c.interact(p, w, h, hit); if (r != InteractionResult.PASS) return r; }
        return InteractionResult.PASS;
    });
    InteractionResult interact(Player player, Level world, InteractionHand hand, BlockHitResult hitResult);
}
