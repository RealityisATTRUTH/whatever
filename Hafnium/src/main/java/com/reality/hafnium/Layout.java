// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Moving and hiding things on the title screen and the pause menu.
 *
 * Buttons are remembered by their text key, like "menu.singleplayer", and
 * saved as how far they moved from where the game put them. That way a moved
 * button stays in the right spot when the window changes size. Essential's
 * buttons are matched by Essential's own button ids, so they can be moved and
 * hidden too. The title screen's logo, splash text and version text aren't
 * buttons, so the title screen tells Hafnium where it drew them each frame.
 *
 * Edit mode: drag to move, right click to hide or show, R to put this menu
 * back the way it was, Esc or Enter to finish.
 */
public final class Layout {

    /** Where one thing goes, relative to where the game put it. */
    public static final class Place {
        public int dx;
        public int dy;
        public boolean hidden;

        public boolean isDefault() {
            return dx == 0 && dy == 0 && !hidden;
        }

        @Override
        public String toString() {
            return dx + "," + dy + "," + (hidden ? 1 : 0);
        }

        static Place parse(String text) {
            if (text == null) return null;
            String[] parts = text.split(",");
            if (parts.length < 2) return null;
            try {
                Place p = new Place();
                p.dx = Integer.parseInt(parts[0].trim());
                p.dy = Integer.parseInt(parts[1].trim());
                p.hidden = parts.length > 2 && parts[2].trim().equals("1");
                return p;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** Something drawn straight onto the screen rather than a button, like the splash text. */
    private static final class Drawn {
        final String label;
        int x, y, w, h; // where the game drew it, before Hafnium moved it
        long frame;

        Drawn(String label) {
            this.label = label;
        }
    }

    /** One thing the editor can move: a button or something drawn. */
    private record Target(String key, AbstractWidget widget, Drawn drawn) {
        int x(Place p) { return widget != null ? widget.getX() : drawn.x + (p == null ? 0 : p.dx); }
        int y(Place p) { return widget != null ? widget.getY() : drawn.y + (p == null ? 0 : p.dy); }
        int w() { return widget != null ? widget.getWidth() : drawn.w; }
        int h() { return widget != null ? widget.getHeight() : drawn.h; }
        String label() { return widget != null ? widget.getMessage().getString() : drawn.label; }
    }

    private static final String ESSENTIAL_PROXY = "gg.essential.gui.proxies.EssentialProxyElement";
    private static final int SNAP = 4;
    private static final int BAR = 24;

    /** Where the game put each button, before Hafnium moved it. */
    private static final Map<AbstractWidget, int[]> HOME = new WeakHashMap<>();
    /** What a hidden button's visible and active flags were before Hafnium hid it. */
    private static final Map<AbstractWidget, boolean[]> BEFORE_HIDING = new WeakHashMap<>();
    /** Hafnium's own buttons, which are never moved. */
    private static final Set<AbstractWidget> OWN = Collections.newSetFromMap(new WeakHashMap<>());
    /** The title screen's drawn things, by key. */
    private static final Map<String, Drawn> DRAWN = new LinkedHashMap<>();

    private static Screen editing;
    private static Target dragged;
    private static int grabX;
    private static int grabY;
    private static long frame;
    private static Method essentialIdGetter;
    private static boolean essentialIdFailed;

    private Layout() {}

    /** The name a screen's layout is saved under, or null if it can't be edited. */
    public static String screenKey(Screen screen) {
        if (screen instanceof TitleScreen) return "title";
        if (screen instanceof PauseScreen) return "pause";
        return null;
    }

    public static void markOwn(AbstractWidget widget) {
        OWN.add(widget);
    }

    public static boolean isEditing(Screen screen) {
        return editing != null && editing == screen;
    }

    public static void startEditing(Screen screen) {
        editing = screen;
        dragged = null;
    }

    public static void stopEditing() {
        editing = null;
        dragged = null;
    }

    /** Called after a screen sets up its buttons: forget old homes so they're measured fresh. */
    public static void afterInit(Screen screen) {
        for (AbstractWidget w : Screens.getWidgets(screen)) {
            HOME.remove(w);
            BEFORE_HIDING.remove(w);
        }
        apply(screen);
    }

    private static Map<String, Place> places(Screen screen) {
        String key = screenKey(screen);
        return key == null ? Map.of() : HafniumConfig.LAYOUTS.getOrDefault(key, Map.of());
    }

    /** Where the player put one button or drawn thing, or null if it was never moved or hidden. */
    public static Place placeOf(Screen screen, String key) {
        return places(screen).get(key);
    }

    private static Place place(Screen screen, String key) {
        return HafniumConfig.LAYOUTS.computeIfAbsent(screenKey(screen), k -> new HashMap<>())
                .computeIfAbsent(key, k -> new Place());
    }

    /** Puts every button where the player moved it. Runs every frame, for buttons added late. */
    public static void apply(Screen screen) {
        frame++;
        if (screenKey(screen) == null) return;
        Map<String, Place> places = places(screen);
        boolean edit = isEditing(screen);
        for (Map.Entry<AbstractWidget, String> e : keyed(screen).entrySet()) {
            AbstractWidget w = e.getKey();
            Place place = places.get(e.getValue());
            if (!HOME.containsKey(w)) {
                HOME.put(w, new int[]{w.getX(), w.getY()});
                if (place != null) {
                    w.setX(w.getX() + place.dx);
                    w.setY(w.getY() + place.dy);
                }
            }
            boolean hide = place != null && place.hidden && !edit;
            if (hide) {
                if (!BEFORE_HIDING.containsKey(w)) BEFORE_HIDING.put(w, new boolean[]{w.visible, w.active});
                w.visible = false;
                w.active = false;
            } else if (BEFORE_HIDING.containsKey(w)) {
                boolean[] was = BEFORE_HIDING.remove(w);
                w.visible = was[0];
                w.active = was[1];
            }
        }
    }

    // --- Drawn things on the title screen -----------------------------------------------

    /**
     * The title screen calls this just before drawing its logo, splash or
     * version text. Returns false if it's hidden. Otherwise the drawing is
     * shifted to where the player moved it, until {@link #endDrawn} is called.
     */
    public static boolean beginDrawn(Screen screen, GuiGraphicsExtractor g, String key, String label, int x, int y, int w, int h) {
        Drawn d = DRAWN.computeIfAbsent(key, k -> new Drawn(label));
        d.x = x;
        d.y = y;
        d.w = w;
        d.h = h;
        d.frame = frame;
        Place p = places(screen).get(key);
        if (p != null && p.hidden && !isEditing(screen)) return false;
        g.pose().pushMatrix();
        if (p != null) g.pose().translate(p.dx, p.dy);
        return true;
    }

    public static void endDrawn(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    // --- What can be edited ---------------------------------------------------------

    /** Every movable button on the screen with the name it's saved under. */
    private static Map<AbstractWidget, String> keyed(Screen screen) {
        Map<AbstractWidget, String> out = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (AbstractWidget w : new ArrayList<>(Screens.getWidgets(screen))) {
            if (OWN.contains(w)) continue;
            String base = baseKey(w);
            int n = counts.merge(base, 1, Integer::sum);
            out.put(w, n == 1 ? base : base + "#" + n);
        }
        return out;
    }

    /** Everything the editor can grab, top-most first. */
    private static List<Target> targets(Screen screen) {
        List<Target> out = new ArrayList<>();
        if (screen instanceof TitleScreen) {
            // Drawn after the buttons, so they're on top. Only ones drawn recently still exist.
            for (Map.Entry<String, Drawn> e : DRAWN.entrySet()) {
                if (frame - e.getValue().frame <= 2) out.add(new Target(e.getKey(), null, e.getValue()));
            }
            Collections.reverse(out); // the splash is drawn after the logo, so it's grabbed first
        }
        Map<String, Place> places = places(screen);
        List<Target> buttons = new ArrayList<>();
        for (Map.Entry<AbstractWidget, String> e : keyed(screen).entrySet()) {
            Place p = places.get(e.getValue());
            boolean hiddenByUs = p != null && p.hidden;
            // Buttons the game itself keeps invisible aren't shown, unless Hafnium is the one hiding them.
            if (!e.getKey().visible && !hiddenByUs) continue;
            buttons.add(new Target(e.getValue(), e.getKey(), null));
        }
        Collections.reverse(buttons); // the one drawn last is on top
        out.addAll(buttons);
        return out;
    }

    private static String baseKey(AbstractWidget w) {
        String essential = essentialId(w);
        if (essential != null) return "essential:" + essential;
        Component message = w.getMessage();
        if (message != null && message.getContents() instanceof TranslatableContents t) return t.getKey();
        String text = message == null ? "" : message.getString().trim();
        if (!text.isEmpty()) return "text:" + text;
        return "widget:" + w.getClass().getSimpleName();
    }

    private static String essentialId(AbstractWidget w) {
        if (essentialIdFailed) return null;
        boolean proxy = false;
        for (Class<?> c = w.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(ESSENTIAL_PROXY)) {
                proxy = true;
                break;
            }
        }
        if (!proxy) return null;
        try {
            if (essentialIdGetter == null) essentialIdGetter = w.getClass().getMethod("getEssentialId");
            Object id = essentialIdGetter.invoke(w);
            return id instanceof String s ? s : null;
        } catch (Throwable e) {
            essentialIdFailed = true;
            Hafnium.LOGGER.warn("Couldn't read Essential's button ids, so its buttons can't be moved", e);
            return null;
        }
    }

    /** Puts everything on this screen back where the game put it. */
    public static void reset(Screen screen) {
        String key = screenKey(screen);
        if (key == null) return;
        for (AbstractWidget w : keyed(screen).keySet()) {
            int[] home = HOME.get(w);
            if (home != null) {
                w.setX(home[0]);
                w.setY(home[1]);
            }
        }
        HafniumConfig.LAYOUTS.remove(key);
        HafniumConfig.save();
        apply(screen);
    }

    /** Forgets a screen's layout without it being open. */
    public static void reset(String screenKey) {
        HafniumConfig.LAYOUTS.remove(screenKey);
        HafniumConfig.save();
    }

    // --- Edit mode input ---------------------------------------------------------

    private static Target under(Screen screen, double mx, double my) {
        Map<String, Place> places = places(screen);
        for (Target t : targets(screen)) {
            Place p = places.get(t.key());
            int x = t.x(p), y = t.y(p);
            if (mx >= x && mx < x + t.w() && my >= y && my < y + t.h()) return t;
        }
        return null;
    }

    /** Returns false when edit mode used the click, so the button underneath isn't pressed. */
    public static boolean onClick(Screen screen, MouseButtonEvent event) {
        if (!isEditing(screen)) return true;
        Target t = under(screen, event.x(), event.y());
        if (t == null) return false;
        Place p = place(screen, t.key());
        // Minecraft 26.x reads the mouse through SDL: left is 1 and right is 3.
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragged = t;
            grabX = (int) event.x() - t.x(p);
            grabY = (int) event.y() - t.y(p);
        } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            p.hidden = !p.hidden;
            HafniumConfig.save();
        }
        return false;
    }

    public static boolean onDrag(Screen screen, MouseButtonEvent event) {
        if (!isEditing(screen)) return true;
        if (dragged == null) return false;
        int x = (int) event.x() - grabX;
        int y = (int) event.y() - grabY;
        int w = dragged.w(), h = dragged.h();
        if (!event.hasShiftDown()) { // Shift turns snapping off
            x = Math.round(x / 2f) * 2;
            y = Math.round(y / 2f) * 2;
            int centered = screen.width / 2 - w / 2;
            if (Math.abs(x - centered) <= SNAP) x = centered;
        }
        x = Math.max(0, Math.min(screen.width - w, x));
        y = Math.max(0, Math.min(screen.height - h, y));
        Place p = place(screen, dragged.key());
        if (dragged.widget() != null) {
            dragged.widget().setX(x);
            dragged.widget().setY(y);
            int[] home = HOME.get(dragged.widget());
            if (home != null) {
                p.dx = x - home[0];
                p.dy = y - home[1];
            }
        } else {
            p.dx = x - dragged.drawn().x;
            p.dy = y - dragged.drawn().y;
        }
        return false;
    }

    public static boolean onRelease(Screen screen, MouseButtonEvent event) {
        if (!isEditing(screen)) return true;
        if (dragged != null) {
            HafniumConfig.save();
            dragged = null;
        }
        return false;
    }

    public static boolean onKey(Screen screen, KeyEvent event) {
        if (!isEditing(screen)) return true;
        // Minecraft 26.x reads keys through SDL, so its own key constants are used, not GLFW numbers.
        int key = event.key();
        if (event.isEscape() || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            stopEditing();
            apply(screen);
        } else if (key == InputConstants.KEY_R) {
            dragged = null;
            reset(screen);
        }
        return false;
    }

    // --- Edit mode drawing ---------------------------------------------------------

    public static void drawEditOverlay(Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!isEditing(screen)) return;
        Font font = Minecraft.getInstance().font;
        Map<String, Place> places = places(screen);
        Target hovered = dragged != null ? dragged : under(screen, mouseX, mouseY);
        for (Target t : targets(screen)) {
            Place p = places.get(t.key());
            boolean hidden = p != null && p.hidden;
            int x = t.x(p), y = t.y(p), wd = t.w(), ht = t.h();
            if (hidden) {
                g.fill(x, y, x + wd, y + ht, 0xE0401616);
                String label = font.plainSubstrByWidth("Hidden: " + t.label(), Math.max(0, wd - 6));
                g.text(font, label, x + 3, y + (ht - 8) / 2, 0xFFFF9C9C);
            }
            boolean on = hovered != null && hovered.key().equals(t.key());
            int color = on ? 0xFFFFFFFF : hidden ? 0xFFB04040 : 0xAAC4C4CC;
            g.outline(x - 1, y - 1, wd + 2, ht + 2, color);
        }
        if (dragged != null) {
            Place p = places.get(dragged.key());
            if (dragged.x(p) == screen.width / 2 - dragged.w() / 2) {
                g.fill(screen.width / 2, BAR, screen.width / 2 + 1, screen.height, 0x806C93FF);
            }
        }
        g.fill(0, 0, screen.width, BAR, 0xD8151517);
        g.centeredText(font, "Hafnium: editing this menu", screen.width / 2, 3, 0xFFFFFFFF);
        String help = "Drag to move. Right click hides or shows. Shift drags freely. R resets. Esc finishes.";
        g.centeredText(font, font.plainSubstrByWidth(help, screen.width - 8), screen.width / 2, 14, 0xFFC8C8D0);
    }
}
