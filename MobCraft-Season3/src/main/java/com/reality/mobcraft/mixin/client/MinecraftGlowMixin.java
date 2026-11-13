package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.ClientGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Decides which entities get an outline on this player's screen.
 *
 * Vanilla asks Minecraft.shouldEntityAppearGlowing for every entity it draws.
 * We say yes for anything the server told us to glow, without
 * touching the entity's real glowing flag, so nobody else sees it.
 *
 * Verified in 26.1.2 as: boolean shouldEntityAppearGlowing(Entity entity)
 */
@Mixin(Minecraft.class)
public class MinecraftGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void mobcraft$clientGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ClientGlow.isGlowing(entity.getId()) || com.reality.mobcraft.client.GearEnchantsClient.duskvision(entity)) {
            cir.setReturnValue(true);
        }
    }
}
