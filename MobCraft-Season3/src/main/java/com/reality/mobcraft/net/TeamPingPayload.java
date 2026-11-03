package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server tells a player that someone on their team pinged a spot. */
public record TeamPingPayload(String fromUuid, String fromName, double x, double y, double z)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TeamPingPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("team_ping"));

    public static final StreamCodec<ByteBuf, TeamPingPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TeamPingPayload::fromUuid,
            ByteBufCodecs.STRING_UTF8, TeamPingPayload::fromName,
            ByteBufCodecs.DOUBLE, TeamPingPayload::x,
            ByteBufCodecs.DOUBLE, TeamPingPayload::y,
            ByteBufCodecs.DOUBLE, TeamPingPayload::z,
            TeamPingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
