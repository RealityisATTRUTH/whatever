// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.mixin;

import com.reality.hafnium.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the stone button texture for the Hafnium look. Only the background
 * changes. The label, clicks and everything else stay vanilla, so other
 * mods' buttons that use the normal background get the theme too.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void hafnium$themedBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!Theme.on()) return;
        AbstractWidget self = (AbstractWidget) (Object) this;
        Theme.drawButton(graphics, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                self.active, self.isHoveredOrFocused(), self.getAlpha());
        ci.cancel();
    }
}
