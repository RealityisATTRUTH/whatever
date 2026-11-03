package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.reality.mobcraft.mob.kit.KitEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets mob kits change damage before it's applied: the Iron Golem's half-power
 * mace, the Skeleton taking Smite bonus damage, and later resistances.
 * Players' hurtServer calls this one through super, so it covers everyone.
 */
@Mixin(LivingEntity.class)
public abstract class KitDamageMixin {

    @WrapMethod(method = "hurtServer")
    private boolean mobcraft$kitDamage(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        float changed = KitEvents.modifyDamage((LivingEntity) (Object) this, source, amount);
        return original.call(level, source, changed);
    }
}
