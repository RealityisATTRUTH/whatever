package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Client orders some mobs to walk somewhere. Each mob gets its own spot, so
 * a group spreads into a formation instead of piling onto one block.
 *
 *   ys      the height of the block you clicked, so a spot in a cave or under
 *           a bridge means that spot, not the ground on top
 *   mode    0 move, 1 attack-move (fight anything on the way)
 *   queue   true adds the spot after the mob's current orders, false replaces them
 *
 * The server checks the player can command every mob before moving it.
 */
public record MoveOrderPayload(List<Integer> entityIds, List<Float> xs, List<Float> ys, List<Float> zs, int mode, boolean queue)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MoveOrderPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("move_order"));

    public static final StreamCodec<ByteBuf, MoveOrderPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), MoveOrderPayload::entityIds,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), MoveOrderPayload::xs,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), MoveOrderPayload::ys,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), MoveOrderPayload::zs,
            ByteBufCodecs.VAR_INT, MoveOrderPayload::mode,
            ByteBufCodecs.BOOL, MoveOrderPayload::queue,
            MoveOrderPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
