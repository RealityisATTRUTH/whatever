package com.reality.mobcraft.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player's own projectiles never hit each other. Without this, the Ghast's
 * Barrage fireballs start so close together that they hit and blow each
 * other up right in front of him, and the Blaze's and Illusioner's volleys
 * can do the same.
 */
@Mixin(Projectile.class)
public abstract class ProjectileSiblingMixin {

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void mobcraft$skipSiblings(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof Projectile other)) return;
        Entity owner = ((Projectile) (Object) this).getOwner();
        if (owner != null && owner == other.getOwner()) cir.setReturnValue(false);
    }
}
