package com.reality.mobcraft.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.compat.world.WorldExtractionContext;
import com.reality.mobcraft.compat.world.WorldRenderContext;
import com.reality.mobcraft.compat.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires MobCraft's own world render events (compat.world) at the places Fabric API fires its own from 1.21.10 on. */
@Mixin(LevelRenderer.class)
public abstract class WorldRenderEventsMixin {
    @Shadow private ClientLevel level;
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private SubmitNodeStorage submitNodeStorage;
    @Unique private DeltaTracker mobcraft$deltaTracker;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void mobcraft$frame(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera,
                                Matrix4f a, Matrix4f b, Matrix4f c, GpuBufferSlice fog, Vector4f color, boolean sky, CallbackInfo ci) {
        mobcraft$deltaTracker = deltaTracker;
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/WorldBorderRenderer;extract(Lnet/minecraft/world/level/border/WorldBorder;Lnet/minecraft/world/phys/Vec3;DLnet/minecraft/client/renderer/state/WorldBorderRenderState;)V"))
    private void mobcraft$endExtraction(CallbackInfo ci) {
        if (level != null) WorldRenderEvents.END_EXTRACTION.invoker().endExtraction(
                new WorldExtractionContext(mobcraft$deltaTracker, level, levelRenderState));
    }

    @Inject(method = "lambda$addMainPass$1", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=submitEntities"))
    private void mobcraft$beforeEntities(GpuBufferSlice fog, LevelRenderState state, ProfilerFiller profiler, Matrix4f matrix,
                                         ResourceHandle<?> h1, ResourceHandle<?> h2, boolean b, Frustum frustum,
                                         ResourceHandle<?> h3, ResourceHandle<?> h4, CallbackInfo ci, @Local PoseStack pose) {
        if (level != null) WorldRenderEvents.BEFORE_ENTITIES.invoker().beforeEntities(
                new WorldRenderContext(submitNodeStorage, pose, mobcraft$deltaTracker, level, state));
    }
}
