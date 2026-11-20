package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client hit the Binding Word chains round its own legs (looked down and attacked). See item/TomeBinding. */
public record TomeChainHitPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TomeChainHitPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("tome_chain_hit"));

    public static final StreamCodec<ByteBuf, TomeChainHitPayload> CODEC = StreamCodec.unit(new TomeChainHitPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
