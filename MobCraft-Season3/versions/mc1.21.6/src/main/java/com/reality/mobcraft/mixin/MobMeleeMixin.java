package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mob melee, backup path.
 *
 * If 26.2's Mob.isWithinMeleeAttackRange reads the target's raw
 * getBoundingBox() instead of getHitbox(), these wrappers swap in the green
 * box there too. require = 0 because only one of the two paths will exist;
 * the log line says which one fired.
 *
 * Verified present in 26.1.2 as: boolean isWithinMeleeAttackRange(LivingEntity target)
 */
@Mixin(Mob.class)
public abstract class MobMeleeMixin {

    @WrapOperation(method = "isWithinMeleeAttackRange", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB mobcraft$meleeLiving(LivingEntity target, Operation<AABB> original) {
        return SmallMobHitbox.hitboxFor(target, original.call(target), "Mob.isWithinMeleeAttackRange (mob melee)");
    }

    @WrapOperation(method = "isWithinMeleeAttackRange", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB mobcraft$meleeEntity(Entity target, Operation<AABB> original) {
        return SmallMobHitbox.hitboxFor(target, original.call(target), "Mob.isWithinMeleeAttackRange (mob melee)");
    }
}
