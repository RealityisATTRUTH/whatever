package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client tells the server it opened or closed RTS mode. */
public record RtsStatePayload(boolean active) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RtsStatePayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("rts_state"));

    public static final StreamCodec<ByteBuf, RtsStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RtsStatePayload::active,
            RtsStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
