package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client reports that something on it is hiding an effect the server gave it. */
public record TamperPayload(String what) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TamperPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("tamper"));

    public static final StreamCodec<ByteBuf, TamperPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TamperPayload::what,
            TamperPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
