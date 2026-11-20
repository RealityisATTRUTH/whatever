// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.hafnium.Layout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the title screen's logo, splash text and version text be moved or
 * hidden like buttons. Each one is drawn shifted by however far the player
 * dragged it, or skipped if it's hidden.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    private static final float SPLASH_ANGLE = 0.34906584f; // 20 degrees, as the game tilts it

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
    private void hafnium$moveLogo(LogoRenderer logo, GuiGraphicsExtractor g, int width, float alpha, Operation<Void> original) {
        int x = width / 2 - LogoRenderer.LOGO_WIDTH / 2;
        int h = LogoRenderer.LOGO_HEIGHT + 14 - 7; // the logo plus the "Java Edition" strip under it
        if (!Layout.beginDrawn((Screen) (Object) this, g, "drawn:logo", "Minecraft logo",
                x, LogoRenderer.DEFAULT_HEIGHT_OFFSET, LogoRenderer.LOGO_WIDTH, h)) return;
        original.call(logo, g, width, alpha);
        Layout.endDrawn(g);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"))
    private void hafnium$moveSplash(SplashRenderer splash, GuiGraphicsExtractor g, int width, Font font, float alpha, Operation<Void> original) {
        // The splash is tilted and pulses, so this box is the space it covers at its biggest.
        float scale = 1.8f * 100f / (font.width(((SplashRendererAccessor) splash).hafnium$splash()) + 32);
        float textW = font.width(((SplashRendererAccessor) splash).hafnium$splash()) * scale;
        float textH = 9 * scale;
        float cos = (float) Math.cos(SPLASH_ANGLE), sin = (float) Math.sin(SPLASH_ANGLE);
        int w = Math.round(textW * cos + textH * sin);
        int h = Math.round(textW * sin + textH * cos);
        int cx = width / 2 + 123, cy = 69;
        if (!Layout.beginDrawn((Screen) (Object) this, g, "drawn:splash", "Splash text",
                cx - w / 2, cy - h / 2, w, h)) return;
        original.call(splash, g, width, font, alpha);
        Layout.endDrawn(g);
    }

    /**
     * The Realms news and invite icons sit next to the Minecraft Realms button,
     * but they're drawn separately. They follow that button: hidden with it, and
     * moved by the same amount.
     */
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void hafnium$followRealmsButton(RealmsNotificationsScreen icons, GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, Operation<Void> original) {
        Screen screen = (Screen) (Object) this;
        Layout.Place realms = Layout.placeOf(screen, "menu.online");
        if (realms != null && realms.hidden && !Layout.isEditing(screen)) return;
        g.pose().pushMatrix();
        if (realms != null) g.pose().translate(realms.dx, realms.dy);
        original.call(icons, g, mouseX, mouseY, delta);
        g.pose().popMatrix();
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void hafnium$moveVersion(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, Operation<Void> original) {
        if (!Layout.beginDrawn((Screen) (Object) this, g, "drawn:version", "Version text",
                x, y - 1, font.width(text), 10)) return;
        original.call(g, font, text, x, y, color);
        Layout.endDrawn(g);
    }
}
