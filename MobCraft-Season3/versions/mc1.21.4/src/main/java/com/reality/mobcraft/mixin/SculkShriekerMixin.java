package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sculk shriekers ignore the Warden player. tryGetPlayer finds the player
 * responsible for a vibration; returning null means the shrieker doesn't
 * shriek, doesn't raise the warning level and never summons a warden.
 * This also stops the Warden spawning wardens with self-given shriekers
 * and catalysts.
 */
@Mixin(SculkShriekerBlockEntity.class)
public abstract class SculkShriekerMixin {

    @ModifyReturnValue(method = "tryGetPlayer", at = @At("RETURN"))
    private static ServerPlayer mobcraft$ignoreWarden(ServerPlayer original) {
        if (original != null && PlayerMob.has(original, MobType.WARDEN)) return null;
        return original;
    }
}
