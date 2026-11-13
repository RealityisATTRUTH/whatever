package com.reality.mobcraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.client.MobLookLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Creeper player swells during the fuse, like a real creeper. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarSwellMixin {

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void mobcraft$swell(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        MobLookLayer.swell(state, poseStack);
    }
}
