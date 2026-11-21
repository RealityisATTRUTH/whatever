package com.reality.mobcraft.neo.fabric.fabric.api.networking.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public final class ServerPlayConnectionEvents {
    public static final Event<Join> JOIN = new Event<>(l -> (h, s, m) -> { for (Join c : l) c.onPlayReady(h, s, m); });
    public static final Event<Disconnect> DISCONNECT = new Event<>(l -> (h, m) -> { for (Disconnect c : l) c.onPlayDisconnect(h, m); });
    public interface Join { void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server); }
    public interface Disconnect { void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server); }
}
