package com.reality.mobcraft.neo.fabric.fabric.api.client.networking.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import com.reality.mobcraft.neo.fabric.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class ClientPlayConnectionEvents {
    public static final Event<Join> JOIN = new Event<>(l -> (h, s, m) -> { for (Join c : l) c.onPlayReady(h, s, m); });
    public static final Event<Disconnect> DISCONNECT = new Event<>(l -> (h, m) -> { for (Disconnect c : l) c.onPlayDisconnect(h, m); });
    public interface Join { void onPlayReady(ClientPacketListener handler, PacketSender sender, Minecraft client); }
    public interface Disconnect { void onPlayDisconnect(ClientPacketListener handler, Minecraft client); }
}
