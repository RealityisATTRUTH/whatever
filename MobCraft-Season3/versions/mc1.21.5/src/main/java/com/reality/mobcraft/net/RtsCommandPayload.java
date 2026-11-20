package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Client tells some of its mobs to Stop (0) or Hold their spot (1). */
public record RtsCommandPayload(List<Integer> entityIds, int command) implements CustomPacketPayload {

    public static final int STOP = 0;
    public static final int HOLD = 1;

    public static final CustomPacketPayload.Type<RtsCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("rts_command"));

    public static final StreamCodec<ByteBuf, RtsCommandPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RtsCommandPayload::entityIds,
            ByteBufCodecs.VAR_INT, RtsCommandPayload::command,
            RtsCommandPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
