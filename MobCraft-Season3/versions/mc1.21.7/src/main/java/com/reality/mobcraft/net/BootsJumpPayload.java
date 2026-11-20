package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pressed jump in mid-air while wearing Double Jump boots. */
public record BootsJumpPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BootsJumpPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("boots_double_jump"));

    public static final StreamCodec<ByteBuf, BootsJumpPayload> CODEC = StreamCodec.unit(new BootsJumpPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
