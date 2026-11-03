package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Client tells the server which mobs it has selected in RTS, so selected endermen stay put. */
public record RtsSelectionPayload(List<Integer> entityIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RtsSelectionPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("rts_selection"));

    public static final StreamCodec<ByteBuf, RtsSelectionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RtsSelectionPayload::entityIds,
            RtsSelectionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
