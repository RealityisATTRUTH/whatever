// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.reality.hafnium.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives sliders the Hafnium look: the track like a button and the handle in
 * the handle colour. Both sprite draws go through here.
 */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {

    @WrapOperation(method = "extractWidgetRenderState", require = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"))
    private void hafnium$themedSlider(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                      int x, int y, int w, int h, int color, Operation<Void> original) {
        if (!Theme.on()) {
            original.call(graphics, pipeline, sprite, x, y, w, h, color);
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        if (sprite.getPath().contains("handle")) {
            Theme.drawHandle(graphics, x, y, w, h, self.active, self.isHoveredOrFocused(), self.getAlpha());
        } else {
            Theme.drawTrack(graphics, x, y, w, h, self.active, self.isHoveredOrFocused(), self.getAlpha());
        }
    }
}
