package com.reality.mobcraft.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.5 keeps the entity pipeline's base private; the charged glow builds on it like vanilla's energy swirl. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("ENTITY_SNIPPET")
    static RenderPipeline.Snippet mobcraft$entitySnippet() {
        throw new AssertionError();
    }
}
