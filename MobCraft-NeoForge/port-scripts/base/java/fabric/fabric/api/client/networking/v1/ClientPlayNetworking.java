package com.reality.mobcraft.neo.fabric.fabric.api.client.networking.v1;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientPlayNetworking {
    public static final Map<CustomPacketPayload.Type<?>, PlayPayloadHandler<?>> HANDLERS = new HashMap<>();
    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return HANDLERS.putIfAbsent(type, handler) == null;
    }
    public static void send(CustomPacketPayload payload) {
        if (canSend(payload.type())) PacketDistributor.sendToServer(payload);
    }
    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        var c = Minecraft.getInstance().getConnection();
        return c != null && c.hasChannel(type);
    }
    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void dispatch(T payload) {
        PlayPayloadHandler<T> h = (PlayPayloadHandler<T>) HANDLERS.get(payload.type());
        Minecraft mc = Minecraft.getInstance();
        if (h != null) h.receive(payload, new Context(mc, mc.player));
    }
    public interface PlayPayloadHandler<T extends CustomPacketPayload> { void receive(T payload, Context context); }
    public record Context(Minecraft client, LocalPlayer player) {}
}
