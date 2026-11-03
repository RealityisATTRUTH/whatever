package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps in the green attack box inside every ProjectileUtil ray trace.
 *
 * 26.1.2 has several separate tracers here, each reading getBoundingBox():
 *   getEntityHitResult(Entity, ...)          crosshair pick for real players
 *   getEntityHitResult(Level, Entity, ...)   arrows, tridents, other projectiles
 *   getEntityHitResult(Level, Projectile,..) projectile convenience overload
 *   getManyEntityHitResult(...)              piercing arrows and multi-hit traces
 *   getHitEntitiesAlong(...)                 spear-style attack ranges
 *
 * A name-only method selector matches every overload with that name.
 * Physics is untouched: only these hit traces see the green box.
 */
@Mixin(ProjectileUtil.class)
public class ProjectileUtilMixin {

    // The crosshair overload. Already proven to apply in 26.2.
    @WrapOperation(
            method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;")
    )
    private static AABB mobcraft$crosshair(Entity target, Operation<AABB> original) {
        return SmallMobHitbox.hitboxFor(target, original.call(target), "ProjectileUtil crosshair pick (player aim)");
    }

    // Every other tracer. require = 0 so a missing one can't crash the game;
    // the log line shows which actually fired.
    @WrapOperation(
            method = {
                    "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
                    "getManyEntityHitResult",
                    "getHitEntitiesAlong"
            },
            require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;")
    )
    private static AABB mobcraft$projectiles(Entity target, Operation<AABB> original) {
        return SmallMobHitbox.hitboxFor(target, original.call(target), "ProjectileUtil projectile trace (arrows, tridents)");
    }
}
