package com.reality.mobcraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.client.SkybreakerClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tips the player head-first for the Skybreaker dive. */
@Mixin(AvatarRenderer.class)
public abstract class SkybreakerRotationMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void mobcraft$dive(AvatarRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
        SkybreakerClient.rotate(state, pose);
    }
}
