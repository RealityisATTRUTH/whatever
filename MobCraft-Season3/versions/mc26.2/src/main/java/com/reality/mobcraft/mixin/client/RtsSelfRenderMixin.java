package com.reality.mobcraft.mixin.client;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.reality.mobcraft.client.rts.RtsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shows your own body while RTS mode is open.
 *
 * RTS draws the world from an invisible camera stand, not from you. When the
 * camera is something other than you, vanilla skips drawing your own player,
 * the way spectating a mob hides you. Your body was still in the world, and
 * other players saw it, but you didn't. Vanilla's check is "the entity is the
 * local player and the camera isn't on it", so while RTS is open this tells
 * that check you're not the local player, and you get drawn like anyone else.
 */
@Mixin(LevelExtractor.class)
public abstract class RtsSelfRenderMixin {

    @Definition(id = "LocalPlayer", type = LocalPlayer.class)
    @Expression("? instanceof LocalPlayer")
    @ModifyExpressionValue(
            method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
            at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean mobcraft$showSelfInRts(boolean isLocalPlayer) {
        return isLocalPlayer && !(Minecraft.getInstance().gui.screen() instanceof RtsScreen);
    }
}
