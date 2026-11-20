// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.mixin;

import com.reality.hafnium.cosmetics.Cosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts a player's Hafnium skin and cape into what Minecraft draws for them.
 * Minecraft's own renderers draw them, so the skin uses the right arm width
 * and the cape waves like a normal cape and shows on elytra too.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void hafnium$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Identifier cape = Cosmetics.capeFor(self);
        Cosmetics.Skin body = Cosmetics.skinFor(self);
        if (cape == null && body == null) return;
        PlayerSkin skin = cir.getReturnValue();
        ClientAsset.Texture bodyTex = skin.body();
        PlayerModelType model = skin.model();
        if (body != null) {
            bodyTex = new ClientAsset.ResourceTexture(body.texture(), body.texture());
            model = body.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        }
        ClientAsset.Texture capeTex = skin.cape();
        ClientAsset.Texture elytraTex = skin.elytra();
        if (cape != null) {
            boolean elytraFollowsCape = skin.elytra() == null || skin.elytra().equals(skin.cape());
            capeTex = new ClientAsset.ResourceTexture(cape, cape);
            if (elytraFollowsCape) elytraTex = capeTex;
        }
        cir.setReturnValue(new PlayerSkin(bodyTex, capeTex, elytraTex, model, skin.secure()));
    }
}
