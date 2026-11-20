package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client switches one of its own mob's abilities on or off. */
public record ToggleAbilityPayload(String abilityId, boolean enabled) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("toggle_ability"));

    public static final StreamCodec<ByteBuf, ToggleAbilityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ToggleAbilityPayload::abilityId,
            ByteBufCodecs.BOOL, ToggleAbilityPayload::enabled,
            ToggleAbilityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
