package com.reality.mobcraft.mixin;

import com.reality.mobcraft.mob.kit.GhastKit;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Ghast player's fireball going off near them throws them away from the blast,
 * like a rocket jump. Runs just before the explosion. See GhastKit.fireballBlast.
 */
@Mixin(LargeFireball.class)
public abstract class FireballJumpMixin {

    @Inject(method = "onHit", at = @At("HEAD"))
    private void mobcraft$rocketJump(HitResult hitResult, CallbackInfo ci) {
        GhastKit.fireballBlast((LargeFireball) (Object) this);
    }
}
