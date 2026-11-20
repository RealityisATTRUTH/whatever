package com.reality.mobcraft.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unseen Hand: sneak and right-click with an empty hand. The game sends nothing to the server for an empty-handed
 * right-click in the air, so the click is caught here and sent on. The server checks the chestplate.
 */
@Mixin(Minecraft.class)
public abstract class UnseenHandClickMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void mobcraft$unseenHand(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player == null || !mc.player.isShiftKeyDown() || !mc.player.getMainHandItem().isEmpty()) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return;
        ClientPlayNetworking.send(new com.reality.mobcraft.net.UnseenHandPayload());
    }
}
