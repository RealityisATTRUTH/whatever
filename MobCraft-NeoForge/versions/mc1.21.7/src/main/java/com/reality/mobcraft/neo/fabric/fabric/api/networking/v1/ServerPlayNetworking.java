package com.reality.mobcraft.neo.fabric.fabric.api.networking.v1;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ServerPlayNetworking {
    public static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> HANDLERS = new HashMap<>();
    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return HANDLERS.putIfAbsent(type, handler) == null;
    }
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && player.connection.hasChannel(type);
    }
    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void dispatch(T payload, ServerPlayer player) {
        PlayPayloadHandler<T> h = (PlayPayloadHandler<T>) HANDLERS.get(payload.type());
        if (h != null) h.receive(payload, new Context(player));
    }
    public interface PlayPayloadHandler<T extends CustomPacketPayload> { void receive(T payload, Context context); }
    public record Context(ServerPlayer player) {
        public MinecraftServer server() { return player.getServer(); }
        public PacketSender responseSender() { return p -> send(player, p); }
    }
}
