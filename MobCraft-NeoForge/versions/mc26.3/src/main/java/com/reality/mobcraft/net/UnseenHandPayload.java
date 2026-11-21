package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client sneak-right-clicked with an empty hand while wearing an Unseen Hand chestplate. */
public record UnseenHandPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnseenHandPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("unseen_hand"));

    public static final StreamCodec<ByteBuf, UnseenHandPayload> CODEC = StreamCodec.unit(new UnseenHandPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
