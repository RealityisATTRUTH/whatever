package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client tells the server it pressed an ability key, and whether it was sneaking. */
public record AbilityPayload(int slot, boolean sneaking) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("ability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityPayload::slot,
            ByteBufCodecs.BOOL, AbilityPayload::sneaking,
            AbilityPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
