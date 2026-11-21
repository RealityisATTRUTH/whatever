package com.reality.mobcraft.neo.fabric.fabric.api.client.screen.v1;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenEvents {
    public static final Event<BeforeInit> BEFORE_INIT = new Event<>(l -> (c, s, w, h) -> { for (BeforeInit x : l) x.beforeInit(c, s, w, h); });
    public static final Event<AfterInit> AFTER_INIT = new Event<>(l -> (c, s, w, h) -> { for (AfterInit x : l) x.afterInit(c, s, w, h); });
    public interface BeforeInit { void beforeInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight); }
    public interface AfterInit { void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight); }
    public interface AfterExtract { void afterExtract(Screen screen, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta); }
    public interface BeforeExtract { void beforeExtract(Screen screen, GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta); }
    public interface Remove { void onRemove(Screen screen); }

    public static final Map<Screen, Event<AfterExtract>> AFTER_EXTRACT = new WeakHashMap<>();
    public static final Map<Screen, Event<BeforeExtract>> BEFORE_EXTRACT = new WeakHashMap<>();
    public static final Map<Screen, Event<Remove>> REMOVE = new WeakHashMap<>();
    public static Event<AfterExtract> afterExtract(Screen screen) {
        return AFTER_EXTRACT.computeIfAbsent(screen, k -> new Event<>(l -> (s, g, x, y, d) -> { for (AfterExtract r : l) r.afterExtract(s, g, x, y, d); }));
    }
    public static Event<BeforeExtract> beforeExtract(Screen screen) {
        return BEFORE_EXTRACT.computeIfAbsent(screen, k -> new Event<>(l -> (s, g, x, y, d) -> { for (BeforeExtract r : l) r.beforeExtract(s, g, x, y, d); }));
    }
    public static Event<Remove> remove(Screen screen) {
        return REMOVE.computeIfAbsent(screen, k -> new Event<>(l -> s -> { for (Remove r : l) r.onRemove(s); }));
    }
    public static void clear(Screen screen) {
        AFTER_EXTRACT.remove(screen); BEFORE_EXTRACT.remove(screen); REMOVE.remove(screen);
        ScreenMouseEvents.clear(screen);
    }
}
