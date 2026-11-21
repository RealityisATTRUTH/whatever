package com.reality.mobcraft.neo.fabric.fabric.api.networking.v1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class PlayerLookup {
    public static Collection<ServerPlayer> all(MinecraftServer server) { return server.getPlayerList().getPlayers(); }
    public static Collection<ServerPlayer> world(ServerLevel level) { return level.players(); }
    public static Collection<ServerPlayer> tracking(Entity entity) {
        List<ServerPlayer> out = new ArrayList<>();
        if (entity.level() instanceof ServerLevel level) {
            level.getChunkSource().chunkMap.getPlayers(entity.chunkPosition(), false).forEach(p -> {
                if (p != entity) out.add(p);
            });
        }
        return out;
    }
}
