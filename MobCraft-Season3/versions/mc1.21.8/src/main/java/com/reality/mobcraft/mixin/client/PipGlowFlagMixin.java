package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.ExplosiveItemGlow;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1: marks when a menu is drawing a small 3D picture (like your player in the inventory), so the glow stays out of it. */
@Mixin(PictureInPictureRenderer.class)
public abstract class PipGlowFlagMixin {
    @Inject(method = "prepare", at = @At("HEAD"))
    private void mobcraft$start(CallbackInfo ci) {
        ExplosiveItemGlow.inMenuPicture = true;
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void mobcraft$end(CallbackInfo ci) {
        ExplosiveItemGlow.inMenuPicture = false;
    }
}
