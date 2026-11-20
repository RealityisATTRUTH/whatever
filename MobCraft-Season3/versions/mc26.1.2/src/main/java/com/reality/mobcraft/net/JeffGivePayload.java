package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Jeff asks for an item from his menu. The server checks he's still powered up. */
public record JeffGivePayload(String itemId, int count) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<JeffGivePayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("jeff_give"));

    public static final StreamCodec<ByteBuf, JeffGivePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, JeffGivePayload::itemId,
            ByteBufCodecs.VAR_INT, JeffGivePayload::count,
            JeffGivePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
