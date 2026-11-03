package com.reality.menutrim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minecraft 1.19 to 1.19.2: text, buttons and opening screens. */
public final class Ui {

    private Ui() {}

    public static Component text(String text) {
        return Component.literal(text);
    }

    public static Button button(int x, int y, int width, int height, String label, Runnable onPress) {
        return new Button(x, y, width, height, text(label), b -> onPress.run());
    }

    public static void setLabel(Button button, String label) {
        button.setMessage(text(label));
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /** Shows or hides one of Essential's stand-in buttons. */
    public static void setVisible(Object widget, boolean visible) {
        if (!(widget instanceof AbstractWidget)) return;
        AbstractWidget w = (AbstractWidget) widget;
        w.visible = visible;
        w.active = visible;
    }
}
