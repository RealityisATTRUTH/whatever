package com.reality.mobcraft.neo.fabric.fabric.api.client.screen.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** Fabric's screen mouse events as of 1.21.9, where clicks carry a MouseButtonEvent. */
public final class ScreenMouseEvents {
    public interface AllowMouseClick { boolean allowMouseClick(Screen screen, MouseButtonEvent click); }
    public interface AllowMouseRelease { boolean allowMouseRelease(Screen screen, MouseButtonEvent click); }
    public interface AllowMouseDrag { boolean allowMouseDrag(Screen screen, MouseButtonEvent click, double horizontalAmount, double verticalAmount); }
    public interface AllowMouseScroll { boolean allowMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount); }
    public static final Map<Screen, Event<AllowMouseClick>> CLICK = new WeakHashMap<>();
    public static final Map<Screen, Event<AllowMouseRelease>> RELEASE = new WeakHashMap<>();
    public static final Map<Screen, Event<AllowMouseDrag>> DRAG = new WeakHashMap<>();
    public static final Map<Screen, Event<AllowMouseScroll>> SCROLL = new WeakHashMap<>();
    public static Event<AllowMouseClick> allowMouseClick(Screen screen) {
        return CLICK.computeIfAbsent(screen, k -> new Event<>(l -> (s, c) -> { for (AllowMouseClick x : l) if (!x.allowMouseClick(s, c)) return false; return true; }));
    }
    public static Event<AllowMouseRelease> allowMouseRelease(Screen screen) {
        return RELEASE.computeIfAbsent(screen, k -> new Event<>(l -> (s, c) -> { for (AllowMouseRelease x : l) if (!x.allowMouseRelease(s, c)) return false; return true; }));
    }
    public static Event<AllowMouseDrag> allowMouseDrag(Screen screen) {
        return DRAG.computeIfAbsent(screen, k -> new Event<>(l -> (s, c, h, v) -> { for (AllowMouseDrag x : l) if (!x.allowMouseDrag(s, c, h, v)) return false; return true; }));
    }
    public static Event<AllowMouseScroll> allowMouseScroll(Screen screen) {
        return SCROLL.computeIfAbsent(screen, k -> new Event<>(l -> (s, x, y, h, v) -> { for (AllowMouseScroll c : l) if (!c.allowMouseScroll(s, x, y, h, v)) return false; return true; }));
    }
    static void clear(Screen screen) { CLICK.remove(screen); RELEASE.remove(screen); DRAG.remove(screen); SCROLL.remove(screen); }
}
