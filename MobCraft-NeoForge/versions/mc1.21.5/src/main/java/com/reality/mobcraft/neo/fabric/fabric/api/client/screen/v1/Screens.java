package com.reality.mobcraft.neo.fabric.fabric.api.client.screen.v1;

import com.reality.mobcraft.neo.mixin.ScreenInvoker;
import java.util.AbstractList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

public final class Screens {
    /** The screen's widgets. Adding one adds it to the screen, the way Fabric's list works. */
    public static List<AbstractWidget> getButtons(Screen screen) {
        ScreenInvoker inv = (ScreenInvoker) screen;
        return new AbstractList<>() {
            private List<AbstractWidget> now() {
                return screen.children().stream().filter(c -> c instanceof AbstractWidget).map(c -> (AbstractWidget) c).toList();
            }
            @Override public AbstractWidget get(int i) { return now().get(i); }
            @Override public int size() { return now().size(); }
            @Override public boolean add(AbstractWidget w) { inv.mobcraft$addRenderableWidget(w); return true; }
            @Override public void add(int i, AbstractWidget w) { inv.mobcraft$addRenderableWidget(w); }
            @Override public boolean remove(Object o) { if (o instanceof AbstractWidget w) { inv.mobcraft$removeWidget(w); return true; } return false; }
        };
    }
    public static Font getTextRenderer(Screen screen) { return Minecraft.getInstance().font; }
    public static Minecraft getClient(Screen screen) { return Minecraft.getInstance(); }
}
