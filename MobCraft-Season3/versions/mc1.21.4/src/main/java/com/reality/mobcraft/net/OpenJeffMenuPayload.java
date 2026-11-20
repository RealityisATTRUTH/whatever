package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server tells Jeff's client to open his item menu. Carries no data. */
public record OpenJeffMenuPayload() implements CustomPacketPayload {

    public static final OpenJeffMenuPayload INSTANCE = new OpenJeffMenuPayload();

    public static final CustomPacketPayload.Type<OpenJeffMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("open_jeff_menu"));

    public static final StreamCodec<ByteBuf, OpenJeffMenuPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
