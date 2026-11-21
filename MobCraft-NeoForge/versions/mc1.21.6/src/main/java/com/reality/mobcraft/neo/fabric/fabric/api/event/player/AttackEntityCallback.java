package com.reality.mobcraft.neo.fabric.fabric.api.event.player;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public interface AttackEntityCallback {
    Event<AttackEntityCallback> EVENT = new Event<>(l -> (p, w, h, e, hit) -> {
        for (AttackEntityCallback c : l) { InteractionResult r = c.interact(p, w, h, e, hit); if (r != InteractionResult.PASS) return r; }
        return InteractionResult.PASS;
    });
    InteractionResult interact(Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hitResult);
}
