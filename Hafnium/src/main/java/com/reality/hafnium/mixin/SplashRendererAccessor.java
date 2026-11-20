// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.mixin;

import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the splash text, so Hafnium knows how big it is. */
@Mixin(SplashRenderer.class)
public interface SplashRendererAccessor {
    @Accessor("splash")
    Component hafnium$splash();
}
