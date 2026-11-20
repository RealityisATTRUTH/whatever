package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client asks for some of one of its mob's self-give items.
 * The server checks the item really belongs to the player's mob.
 * With drop on, the items are thrown in front of the player like pressing Q,
 * so they can land straight in a hopper or auto crafter.
 */
public record GiveItemPayload(String itemId, int count, boolean drop) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GiveItemPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("give_item"));

    public static final StreamCodec<ByteBuf, GiveItemPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GiveItemPayload::itemId,
            ByteBufCodecs.VAR_INT, GiveItemPayload::count,
            ByteBufCodecs.BOOL, GiveItemPayload::drop,
            GiveItemPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
