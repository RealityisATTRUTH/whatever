package com.reality.mobcraft.neo.fabric.fabric.api.client.screen.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenMouseEvents {
    public interface AllowMouseClick { boolean allowMouseClick(Screen screen, double mouseX, double mouseY, int button); }
    public interface AllowMouseRelease { boolean allowMouseRelease(Screen screen, double mouseX, double mouseY, int button); }
    public interface AllowMouseScroll { boolean allowMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount); }
    public static final Map<Screen, Event<AllowMouseClick>> CLICK = new WeakHashMap<>();
    public static final Map<Screen, Event<AllowMouseRelease>> RELEASE = new WeakHashMap<>();
    public static final Map<Screen, Event<AllowMouseScroll>> SCROLL = new WeakHashMap<>();
    public static Event<AllowMouseClick> allowMouseClick(Screen screen) {
        return CLICK.computeIfAbsent(screen, k -> new Event<>(l -> (s, x, y, b) -> { for (AllowMouseClick c : l) if (!c.allowMouseClick(s, x, y, b)) return false; return true; }));
    }
    public static Event<AllowMouseRelease> allowMouseRelease(Screen screen) {
        return RELEASE.computeIfAbsent(screen, k -> new Event<>(l -> (s, x, y, b) -> { for (AllowMouseRelease c : l) if (!c.allowMouseRelease(s, x, y, b)) return false; return true; }));
    }
    public static Event<AllowMouseScroll> allowMouseScroll(Screen screen) {
        return SCROLL.computeIfAbsent(screen, k -> new Event<>(l -> (s, x, y, h, v) -> { for (AllowMouseScroll c : l) if (!c.allowMouseScroll(s, x, y, h, v)) return false; return true; }));
    }
    static void clear(Screen screen) { CLICK.remove(screen); RELEASE.remove(screen); SCROLL.remove(screen); }
}
