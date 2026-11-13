package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.SkybreakerClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A short camera shake when a Skybreaker lands. */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow public abstract float xRot();
    @Shadow public abstract float yRot();

    @Inject(method = "update", at = @At("TAIL"))
    private void mobcraft$shake(DeltaTracker delta, CallbackInfo ci) {
        int s = SkybreakerClient.shake;
        if (s <= 0) return;
        float amp = s * 0.35f;
        double time = System.nanoTime() / 2.0e7;
        setRotation(yRot() + (float) Math.sin(time * 1.7) * amp, xRot() + (float) Math.cos(time * 2.3) * amp);
    }
}
