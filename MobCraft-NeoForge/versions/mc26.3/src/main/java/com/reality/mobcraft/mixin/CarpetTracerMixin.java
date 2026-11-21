package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Carpet fake players ("/player Bot attack").
 *
 * Carpet bots don't use vanilla's crosshair pick. They aim with Carpet's own
 * copy of the ray trace in carpet.script.utils.Tracer, which loops over
 * nearby entities and clips against current.getBoundingBox() directly.
 * That's why the bot couldn't hit the top half of the green box.
 *
 * @Pseudo means this is skipped cleanly when Carpet isn't installed.
 * Only the 6-argument overload is wrapped, so the bot's own search box
 * is left alone.
 */
@Pseudo
@Mixin(targets = "carpet.script.utils.Tracer")
public class CarpetTracerMixin {

    @WrapOperation(
            method = "rayTraceEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;")
    )
    private static AABB mobcraft$carpetBotAim(Entity target, Operation<AABB> original) {
        return SmallMobHitbox.hitboxFor(target, original.call(target), "Carpet bot aim");
    }
}
