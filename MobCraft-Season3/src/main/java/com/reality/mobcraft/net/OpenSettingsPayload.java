package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server tells a client to open the MobCraft Settings screen. Carries no data. */
public record OpenSettingsPayload() implements CustomPacketPayload {

    public static final OpenSettingsPayload INSTANCE = new OpenSettingsPayload();

    public static final CustomPacketPayload.Type<OpenSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("open_settings"));

    public static final StreamCodec<ByteBuf, OpenSettingsPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
