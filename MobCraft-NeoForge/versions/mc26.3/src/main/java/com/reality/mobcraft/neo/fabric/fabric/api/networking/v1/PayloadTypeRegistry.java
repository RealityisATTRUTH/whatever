package com.reality.mobcraft.neo.fabric.fabric.api.networking.v1;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Remembers payload types; MobCraftNeo registers them with NeoForge's network. */
public final class PayloadTypeRegistry {
    public static final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> C2S = new LinkedHashMap<>();
    public static final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> S2C = new LinkedHashMap<>();
    private static final PayloadTypeRegistry PLAY_C2S = new PayloadTypeRegistry(C2S);
    private static final PayloadTypeRegistry PLAY_S2C = new PayloadTypeRegistry(S2C);
    private final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> map;
    private PayloadTypeRegistry(Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> map) { this.map = map; }
    public static PayloadTypeRegistry serverboundPlay() { return PLAY_C2S; }
    public static PayloadTypeRegistry clientboundPlay() { return PLAY_S2C; }
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        map.put(type, codec);
    }
}
