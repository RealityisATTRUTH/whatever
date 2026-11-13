package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a Broken Totem just crumbled here, so show its red essence orb floating away. */
public record EssenceOrbPayload(double x, double y, double z) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EssenceOrbPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("essence_orb"));

    public static final StreamCodec<ByteBuf, EssenceOrbPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, EssenceOrbPayload::x,
            ByteBufCodecs.DOUBLE, EssenceOrbPayload::y,
            ByteBufCodecs.DOUBLE, EssenceOrbPayload::z,
            EssenceOrbPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
