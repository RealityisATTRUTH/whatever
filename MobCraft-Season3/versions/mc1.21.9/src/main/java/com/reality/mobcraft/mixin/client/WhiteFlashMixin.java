package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.MobLookLayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Creeper player flashes white during the fuse, like a real creeper. */
@Mixin(LivingEntityRenderer.class)
public abstract class WhiteFlashMixin {

    @Inject(method = "getWhiteOverlayProgress", at = @At("HEAD"), cancellable = true)
    private void mobcraft$creeperFlash(LivingEntityRenderState state, CallbackInfoReturnable<Float> cir) {
        if (!(state instanceof AvatarRenderState avatar)) return;
        float flash = MobLookLayer.whiteFlash(avatar);
        if (flash >= 0f) cir.setReturnValue(flash);
    }
}
