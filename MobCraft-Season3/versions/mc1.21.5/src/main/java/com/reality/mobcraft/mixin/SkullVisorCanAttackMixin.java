package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.SkullVisor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skull Visor and the rabbit disguise: mobs won't attack you. 1.21.11 keeps canAttack on LivingEntity, not Mob. */
@Mixin(LivingEntity.class)
public abstract class SkullVisorCanAttackMixin {
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mobcraft$visorCanAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Mob mob)) return;
        if (SkullVisor.ignores(mob, target) || com.reality.mobcraft.item.RabbitEars.hidden(target)) cir.setReturnValue(false);
    }
}
