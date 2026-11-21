package com.reality.mobcraft.mixin;

import com.reality.mobcraft.rts.RtsHeld;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses an enderman's own random teleports while it's under RTS command
 * (see RtsHeld). Endermen teleport through LivingEntity's random teleport,
 * so both versions of it are covered. Players and other mobs aren't touched.
 */
@Mixin(LivingEntity.class)
public abstract class HeldEndermanMixin {

    @Inject(method = {
            "randomTeleport(DDDZLnet/minecraft/tags/TagKey;)Z",
            "randomTeleport(DDDZLjava/util/function/Predicate;)Z"
    }, at = @At("HEAD"), cancellable = true, require = 0)
    private void mobcraft$holdEnderman(CallbackInfoReturnable<Boolean> cir) {
        if (RtsHeld.isHeldEnderman((LivingEntity) (Object) this)) cir.setReturnValue(false);
    }
}
