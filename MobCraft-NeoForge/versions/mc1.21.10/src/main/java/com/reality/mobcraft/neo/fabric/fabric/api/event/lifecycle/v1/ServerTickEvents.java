package com.reality.mobcraft.neo.fabric.fabric.api.event.lifecycle.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class ServerTickEvents {
    public static final Event<StartTick> START_SERVER_TICK = new Event<>(l -> s -> { for (StartTick c : l) c.onStartTick(s); });
    public static final Event<EndTick> END_SERVER_TICK = new Event<>(l -> s -> { for (EndTick c : l) c.onEndTick(s); });
    public static final Event<StartWorldTick> START_WORLD_TICK = new Event<>(l -> w -> { for (StartWorldTick c : l) c.onStartTick(w); });
    public static final Event<EndWorldTick> END_WORLD_TICK = new Event<>(l -> w -> { for (EndWorldTick c : l) c.onEndTick(w); });
    public interface StartTick { void onStartTick(MinecraftServer server); }
    public interface EndTick { void onEndTick(MinecraftServer server); }
    public interface StartWorldTick { void onStartTick(ServerLevel world); }
    public interface EndWorldTick { void onEndTick(ServerLevel world); }
}
