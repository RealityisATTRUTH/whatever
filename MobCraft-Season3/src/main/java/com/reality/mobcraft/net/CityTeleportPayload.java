package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * RTS: a Shulker clicked to teleport. shulkerId is a vanilla shulker that was
 * clicked, or -1 for a spot inside an End City.
 */
public record CityTeleportPayload(double x, double z, int shulkerId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CityTeleportPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("city_teleport"));

    public static final StreamCodec<ByteBuf, CityTeleportPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, CityTeleportPayload::x,
            ByteBufCodecs.DOUBLE, CityTeleportPayload::z,
            ByteBufCodecs.INT, CityTeleportPayload::shulkerId,
            CityTeleportPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
