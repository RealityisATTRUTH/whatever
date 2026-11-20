package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.item.Hush;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Silenced by the Hush: no sound reaches you at all. */
@Mixin(SoundManager.class)
public abstract class HushSoundMixin {

    private static boolean mobcraft$silenced() {
        var p = Minecraft.getInstance().player;
        return p != null && Hush.hushed(p);
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void mobcraft$hushPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (mobcraft$silenced()) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }

    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void mobcraft$hushDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        if (mobcraft$silenced()) ci.cancel();
    }
}
