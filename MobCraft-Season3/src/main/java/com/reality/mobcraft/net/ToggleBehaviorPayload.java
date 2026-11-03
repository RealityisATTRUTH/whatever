package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client switches one of its mob behaviors (follow, defend, attack) on or off. */
public record ToggleBehaviorPayload(String behavior, boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleBehaviorPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("toggle_behavior"));

    public static final StreamCodec<ByteBuf, ToggleBehaviorPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ToggleBehaviorPayload::behavior,
            ByteBufCodecs.BOOL, ToggleBehaviorPayload::enabled,
            ToggleBehaviorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
