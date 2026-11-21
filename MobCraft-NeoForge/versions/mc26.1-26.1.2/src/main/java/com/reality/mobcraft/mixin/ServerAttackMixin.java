package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Player melee.
 *
 * The client picks its target with the crosshair, which already uses the
 * green box. The server then re-checks reach before it applies damage. If
 * that check used the small box, hits on the top of the green box would be
 * thrown out as too far away.
 *
 * If the reach check uses getHitbox(), LivingEntityHitboxMixin already covers
 * it. If it reads getBoundingBox() directly, these wrappers cover it.
 * require = 0 because only one path will exist; the log says which fired.
 *
 * Verified present in 26.1.2 as: public void handleAttack(ServerboundAttackPacket packet)
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerAttackMixin {

    @WrapOperation(method = "handleAttack", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB mobcraft$attackEntity(Entity target, Operation<AABB> original) {
        return SmallMobHitbox.hitboxFor(target, original.call(target), "handleAttack (player melee)");
    }

    @WrapOperation(method = "handleAttack", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB mobcraft$attackLiving(LivingEntity target, Operation<AABB> original) {
        return SmallMobHitbox.hitboxFor(target, original.call(target), "handleAttack (player melee)");
    }
}
