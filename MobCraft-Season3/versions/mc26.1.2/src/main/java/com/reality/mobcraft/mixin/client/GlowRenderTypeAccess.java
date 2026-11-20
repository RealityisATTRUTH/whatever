package com.reality.mobcraft.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 26.1 keeps RenderType.create private; the charged-item glow needs its own render type (ExplosiveItemGlow). */
@Mixin(RenderType.class)
public interface GlowRenderTypeAccess {
    @Invoker("create")
    static RenderType mobcraft$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
