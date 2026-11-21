package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Client saves its divisions and army groups to the server. */
public record SetGroupsPayload(List<String> lines) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetGroupsPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("set_groups"));

    public static final StreamCodec<ByteBuf, SetGroupsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SetGroupsPayload::lines,
            SetGroupsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
