package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server sends one client the full list of ghost armor stands it should see
 * right now. The client creates, moves and removes its local stands to match.
 *
 * Sending the full list each time means a lost or late packet can never leave
 * a stale ghost behind. An empty list clears every ghost.
 */
public record GhostSyncPayload(List<Ghost> ghosts) implements CustomPacketPayload {

    /** One ghost. key stays the same while the same ghost keeps moving. */
    public record Ghost(int key, double x, double y, double z, float yRot, int color) {
        public static final StreamCodec<ByteBuf, Ghost> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Ghost::key,
                ByteBufCodecs.DOUBLE, Ghost::x,
                ByteBufCodecs.DOUBLE, Ghost::y,
                ByteBufCodecs.DOUBLE, Ghost::z,
                ByteBufCodecs.FLOAT, Ghost::yRot,
                ByteBufCodecs.INT, Ghost::color,
                Ghost::new
        );
    }

    public static final CustomPacketPayload.Type<GhostSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("ghost_sync"));

    public static final StreamCodec<ByteBuf, GhostSyncPayload> CODEC = StreamCodec.composite(
            Ghost.CODEC.apply(ByteBufCodecs.list()), GhostSyncPayload::ghosts,
            GhostSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
