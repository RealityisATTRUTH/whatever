package com.reality.mobcraft.mixin.client;

import com.mojang.serialization.MapCodec;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.client.HeldTicks;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds MobCraft's own item model number properties: mobcraft:held_ticks (HeldTicks). */
@Mixin(RangeSelectItemModelProperties.class)
public abstract class RangeSelectPropertiesMixin {

    @Shadow @Final
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends RangeSelectItemModelProperty>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void mobcraft$properties(CallbackInfo ci) {
        ID_MAPPER.put(MobCraft.id("held_ticks"), HeldTicks.MAP_CODEC);
    }
}
