package com.reality.mobcraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.client.MobLookLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An Ender Dragon player's elytra is never drawn: it's merged into her own wings (MobLookLayer). */
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void mobcraft$dragonWings(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state,
                                      float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar && "ender_dragon".equals(avatar.getData(MobLookLayer.MOB))) ci.cancel();
    }
}
