package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a sound broke the hush, so start mining this Unseenhush over. */
public record HushResetPayload(BlockPos pos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HushResetPayload> TYPE = new CustomPacketPayload.Type<>(MobCraft.id("hush_reset"));

    public static final StreamCodec<FriendlyByteBuf, HushResetPayload> CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, HushResetPayload::pos, HushResetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
