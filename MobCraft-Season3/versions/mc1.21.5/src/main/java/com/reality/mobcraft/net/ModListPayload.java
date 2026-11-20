package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Client tells the server which mods it has, right after joining. */
public record ModListPayload(List<String> modIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ModListPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("mod_list"));

    public static final StreamCodec<ByteBuf, ModListPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ModListPayload::modIds,
            ModListPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
