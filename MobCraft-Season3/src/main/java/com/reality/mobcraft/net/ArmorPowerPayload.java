package com.reality.mobcraft.net;

import com.reality.mobcraft.MobCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client pressed the Armor Power key. */
public record ArmorPowerPayload(boolean sneaking) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ArmorPowerPayload> TYPE =
            new CustomPacketPayload.Type<>(MobCraft.id("armor_power"));

    public static final StreamCodec<ByteBuf, ArmorPowerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ArmorPowerPayload::sneaking,
            ArmorPowerPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
