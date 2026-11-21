package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server tells one client to make one entity glow, for that client only.
 *
 * color  RGB, like 0xFF0000 for red. 0 means "stop glowing".
 * ticks  How long it lasts, 20 per second. -1 means until told to stop.
 */
public record GlowPayload(int entityId, int color, int ticks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GlowPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("glow"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GlowPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GlowPayload::entityId,
            ByteBufCodecs.INT, GlowPayload::color,
            ByteBufCodecs.VAR_INT, GlowPayload::ticks,
            GlowPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
