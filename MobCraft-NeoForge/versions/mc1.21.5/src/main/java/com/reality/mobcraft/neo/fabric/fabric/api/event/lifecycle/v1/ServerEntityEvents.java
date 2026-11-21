package com.reality.mobcraft.neo.fabric.fabric.api.event.lifecycle.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class ServerEntityEvents {
    public static final Event<Load> ENTITY_LOAD = new Event<>(l -> (e, w) -> { for (Load c : l) c.onLoad(e, w); });
    public static final Event<Unload> ENTITY_UNLOAD = new Event<>(l -> (e, w) -> { for (Unload c : l) c.onUnload(e, w); });
    public interface Load { void onLoad(Entity entity, ServerLevel world); }
    public interface Unload { void onUnload(Entity entity, ServerLevel world); }
}
