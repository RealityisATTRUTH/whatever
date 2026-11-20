package com.reality.mobcraft.mixin;

import com.reality.mobcraft.mob.MobAttachments;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mobs a player owns, like pets and RTS mobs, don't catch fire in sunlight.
 * Without this, a zombie or skeleton pet burned to death the first sunny day.
 * Fire, lava and fire attacks still burn them.
 *
 * require = 0: if the method is renamed the game still starts, pets just burn again.
 */
@Mixin(Mob.class)
public abstract class PetSunMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void mobcraft$petsDontBurn(CallbackInfoReturnable<Boolean> cir) {
        if (((Mob) (Object) this).hasAttached(MobAttachments.OWNER)) cir.setReturnValue(false);
    }
}
