package com.reality.mobcraft.neo.fabric.fabric.api.event.lifecycle.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleEvents {
    public static final Event<ServerStarting> SERVER_STARTING = new Event<>(l -> s -> { for (ServerStarting c : l) c.onServerStarting(s); });
    public static final Event<ServerStarted> SERVER_STARTED = new Event<>(l -> s -> { for (ServerStarted c : l) c.onServerStarted(s); });
    public static final Event<ServerStopping> SERVER_STOPPING = new Event<>(l -> s -> { for (ServerStopping c : l) c.onServerStopping(s); });
    public static final Event<ServerStopped> SERVER_STOPPED = new Event<>(l -> s -> { for (ServerStopped c : l) c.onServerStopped(s); });
    public interface ServerStarting { void onServerStarting(MinecraftServer server); }
    public interface ServerStarted { void onServerStarted(MinecraftServer server); }
    public interface ServerStopping { void onServerStopping(MinecraftServer server); }
    public interface ServerStopped { void onServerStopped(MinecraftServer server); }
}
