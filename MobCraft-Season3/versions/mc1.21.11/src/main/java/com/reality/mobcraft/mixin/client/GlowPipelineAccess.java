package com.reality.mobcraft.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vanilla matrices-and-fog piece every entity pipeline starts from, for the charged-item glow's pipeline. */
@Mixin(RenderPipelines.class)
public interface GlowPipelineAccess {
    @Accessor("MATRICES_FOG_SNIPPET")
    static RenderPipeline.Snippet mobcraft$matricesFog() {
        throw new AssertionError();
    }
}
