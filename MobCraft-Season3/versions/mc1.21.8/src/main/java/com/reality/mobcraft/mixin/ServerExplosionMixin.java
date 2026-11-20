package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Explosions.
 *
 * Explosion damage scales with how much of an entity's box the blast can
 * see. getSeenPercent samples points across the box, so a tiny box behind a
 * single block is fully hidden. Swapping in the green box makes cover work
 * the same as for a full-size player.
 *
 * Verified present in 26.1.2 as: public static float getSeenPercent(Vec3 center, Entity entity)
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @WrapOperation(method = "getSeenPercent", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB mobcraft$explosionBox(Entity entity, Operation<AABB> original) {
        return SmallMobHitbox.hitboxFor(entity, original.call(entity), "ServerExplosion.getSeenPercent (explosions)");
    }
}
