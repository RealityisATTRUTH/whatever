package com.reality.mobcraft.mixin.client;

import com.mojang.serialization.MapCodec;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.client.GrubRenderer;
import com.reality.mobcraft.client.LensChain;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds MobCraft's own special item renderers: mobcraft:lens_chain (LensChain) and mobcraft:grub (GrubRenderer). */
@Mixin(SpecialModelRenderers.class)
public abstract class SpecialModelRenderersMixin {

    @Shadow @Final
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void mobcraft$renderers(CallbackInfo ci) {
        ID_MAPPER.put(MobCraft.id("lens_chain"), LensChain.MAP_CODEC);
        ID_MAPPER.put(MobCraft.id("grub"), GrubRenderer.MAP_CODEC);
        ID_MAPPER.put(MobCraft.id("tome"), com.reality.mobcraft.client.TomeRenderer.MAP_CODEC);
    }
}
