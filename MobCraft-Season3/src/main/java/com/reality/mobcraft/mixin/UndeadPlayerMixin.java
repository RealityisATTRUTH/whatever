package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.reality.mobcraft.mob.kit.KitEvents;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Undead players (the Skeleton, later the Wither) are healed by Instant
 * Damage and hurt by Instant Health, like undead mobs.
 *
 * require = 0: if 26.3 renamed this method the game still starts, the swap
 * just doesn't happen. A warning in the log says so.
 */
@Mixin(LivingEntity.class)
public abstract class UndeadPlayerMixin {

    @ModifyReturnValue(method = "isInvertedHealAndHarm", at = @At("RETURN"), require = 0)
    private boolean mobcraft$undeadPlayer(boolean original) {
        return original || KitEvents.isUndeadPlayer((LivingEntity) (Object) this);
    }
}
