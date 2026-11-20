package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mob melee.
 *
 * LivingEntity.getHitbox() is the "box you can be hit in" method, separate
 * from getBoundingBox(), which stays the collision box. Vanilla's melee reach
 * check tests the attacker's reach box against the target's getHitbox(), so
 * returning the green box here makes zombies, wardens, golems and every other
 * melee mob hit a small player as if they were full size.
 *
 * Collision is untouched: movement and block physics use getBoundingBox().
 * Verified present in 26.1.2 as: AABB getHitbox()
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHitboxMixin {

    @ModifyReturnValue(method = "getHitbox", at = @At("RETURN"))
    private AABB mobcraft$attackHitbox(AABB original) {
        return SmallMobHitbox.hitboxFor((Entity) (Object) this, original, "LivingEntity.getHitbox (mob melee)");
    }
}
