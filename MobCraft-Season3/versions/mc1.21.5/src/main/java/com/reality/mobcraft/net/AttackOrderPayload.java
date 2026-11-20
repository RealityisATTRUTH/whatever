package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Client orders some mobs to attack a target. The server checks the player
 * can command every mob and that the target is a fair one. With queue set
 * (shift-click), the target is added to the mobs' list instead of replacing it.
 */
public record AttackOrderPayload(List<Integer> entityIds, int targetId, boolean queue) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttackOrderPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("attack_order"));

    public static final StreamCodec<ByteBuf, AttackOrderPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), AttackOrderPayload::entityIds,
            ByteBufCodecs.VAR_INT, AttackOrderPayload::targetId,
            ByteBufCodecs.BOOL, AttackOrderPayload::queue,
            AttackOrderPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
