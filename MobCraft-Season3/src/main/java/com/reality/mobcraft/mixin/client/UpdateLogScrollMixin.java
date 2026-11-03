package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.UpdateLogPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scrolling the Update Log comes first, before any other mod sees the wheel.
 *
 * Mods like Essential put a player model and buttons on the left of the title
 * screen, under the Update Log. They catch the mouse wheel in spots over
 * their model, so scrolling the log stopped in those spots. This runs at the
 * very start of the game's scroll handling, with an early priority, so a
 * scroll over the Update Log always scrolls the log.
 */
@Mixin(value = MouseHandler.class, priority = 100)
public abstract class UpdateLogScrollMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void mobcraft$scrollUpdateLogFirst(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window != client.getWindow().handle()) return;
        MouseHandler mouse = (MouseHandler) (Object) this;
        double x = mouse.getScaledXPos(client.getWindow());
        double y = mouse.getScaledYPos(client.getWindow());
        if (UpdateLogPanel.scrollAt(client.gui.screen(), x, y, vertical)) ci.cancel();
    }
}
