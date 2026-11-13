package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pressed the Essence Power key. */
public record EssencePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EssencePayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("essence_power"));

    public static final StreamCodec<ByteBuf, EssencePayload> CODEC = StreamCodec.unit(new EssencePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
