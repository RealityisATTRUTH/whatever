package com.reality.mobcraft.neo.fabric.fabric.api.event.client.player;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public interface ClientPreAttackCallback {
    Event<ClientPreAttackCallback> EVENT = new Event<>(l -> (c, p, n) -> { for (ClientPreAttackCallback x : l) if (x.onClientPlayerPreAttack(c, p, n)) return true; return false; });
    boolean onClientPlayerPreAttack(Minecraft client, LocalPlayer player, int clickCount);
}
