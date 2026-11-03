package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pings a spot from RTS mode. The server passes it on to the player's team. */
public record PingPayload(double x, double y, double z) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PingPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("ping"));

    public static final StreamCodec<ByteBuf, PingPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, PingPayload::x,
            ByteBufCodecs.DOUBLE, PingPayload::y,
            ByteBufCodecs.DOUBLE, PingPayload::z,
            PingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
