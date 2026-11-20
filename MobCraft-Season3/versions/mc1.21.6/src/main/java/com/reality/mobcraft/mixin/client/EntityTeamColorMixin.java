package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.ClientGlow;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sets the outline color for client-side glows.
 *
 * Vanilla colors an outline with the entity's team color. For entities the
 * server told us to glow, we return our color instead. Only the client copy
 * is changed: in singleplayer the integrated server shares entity IDs with the
 * client, so the client-side check keeps server logic untouched.
 *
 * Verified in 26.1.2 as: int getTeamColor()
 */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void mobcraft$clientGlowColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide()) return;
        Integer color = ClientGlow.colorFor(self.getId());
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
