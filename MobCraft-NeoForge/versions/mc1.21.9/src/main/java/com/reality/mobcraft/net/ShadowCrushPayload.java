package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pressed the Shadow Crush key. */
public record ShadowCrushPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShadowCrushPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("shadow_crush"));

    public static final StreamCodec<ByteBuf, ShadowCrushPayload> CODEC = StreamCodec.unit(new ShadowCrushPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
