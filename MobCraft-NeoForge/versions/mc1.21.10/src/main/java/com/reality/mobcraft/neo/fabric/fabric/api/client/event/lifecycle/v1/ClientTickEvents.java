package com.reality.mobcraft.neo.fabric.fabric.api.client.event.lifecycle.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.client.Minecraft;

public final class ClientTickEvents {
    public static final Event<StartTick> START_CLIENT_TICK = new Event<>(l -> m -> { for (StartTick c : l) c.onStartTick(m); });
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>(l -> m -> { for (EndTick c : l) c.onEndTick(m); });
    public interface StartTick { void onStartTick(Minecraft client); }
    public interface EndTick { void onEndTick(Minecraft client); }
}
