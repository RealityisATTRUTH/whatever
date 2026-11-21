package com.reality.mobcraft.neo;

import java.util.LinkedHashSet;
import java.util.Set;
import com.reality.mobcraft.neo.fabric.fabric.api.networking.v1.PayloadTypeRegistry;
import com.reality.mobcraft.neo.fabric.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers every payload MobCraft declared with NeoForge's network. */
final class NeoNetwork {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("mobcraft").versioned("1").optional();
        Set<CustomPacketPayload.Type<?>> all = new LinkedHashSet<>(PayloadTypeRegistry.C2S.keySet());
        all.addAll(PayloadTypeRegistry.S2C.keySet());
        for (CustomPacketPayload.Type type : all) {
            boolean toServer = PayloadTypeRegistry.C2S.containsKey(type), toClient = PayloadTypeRegistry.S2C.containsKey(type);
            StreamCodec codec = toServer ? PayloadTypeRegistry.C2S.get(type) : PayloadTypeRegistry.S2C.get(type);
            if (toServer && toClient) {
                r.playBidirectional(type, codec, (p, ctx) -> {
                    if (ctx.flow().isServerbound()) ServerPlayNetworking.dispatch((CustomPacketPayload) p, (ServerPlayer) ctx.player());
                    else ClientDispatch.dispatch((CustomPacketPayload) p);
                });
            } else if (toServer) {
                r.playToServer(type, codec, (p, ctx) -> ServerPlayNetworking.dispatch((CustomPacketPayload) p, (ServerPlayer) ctx.player()));
            } else {
                r.playToClient(type, codec, (p, ctx) -> ClientDispatch.dispatch((CustomPacketPayload) p));
            }
        }
        r.playToClient(AttachmentSync.Payload.TYPE, AttachmentSync.Payload.CODEC, (p, ctx) -> ClientDispatch.attachment(p));
        com.reality.mobcraft.MobCraft.LOGGER.info("MobCraft NeoForge: registered {} payload types", all.size() + 1);
    }
}
