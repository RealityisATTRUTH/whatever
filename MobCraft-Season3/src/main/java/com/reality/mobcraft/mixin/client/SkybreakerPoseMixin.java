package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.SkybreakerClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skybreaker's arm, leg and head poses, seen by everyone. */
@Mixin(HumanoidModel.class)
public abstract class SkybreakerPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void mobcraft$skybreaker(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) SkybreakerClient.pose((HumanoidModel<?>) (Object) this, avatar);
    }
}
