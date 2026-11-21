package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pressed jump in midair. The server checks the mob, energy and curse. */
public record DoubleJumpPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DoubleJumpPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("double_jump"));

    public static final StreamCodec<ByteBuf, DoubleJumpPayload> CODEC = StreamCodec.unit(new DoubleJumpPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
