package com.reality.menutrim.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menu Trim's shared logic, the same on every Minecraft version and loader.
 * It never touches Minecraft classes directly, so it compiles unchanged for
 * 1.8.9 through 26.x. Each version only supplies a thin layer: where screens
 * open, how to show or hide a button, and the settings screen.
 *
 * How hiding works: for every button it draws, Essential also puts a plain,
 * invisible game button on the screen with an id like "wardrobe" or "social".
 * Menu mods such as FancyMenu use those to move or remove Essential's buttons.
 * When that plain button is hidden, Essential hides its own button too. The
 * sale banner and NEW badge belong to Essential's left side bar, which is
 * hidden with the public hide() of Essential's UI library. Essential's own
 * code and files are never changed.
 *
 * Choices are saved in config/menutrim.txt.
 */
public final class TrimCore {

    public static final Logger LOGGER = LogManager.getLogger("menutrim");

    private static final String PROXY_CLASS = "gg.essential.gui.proxies.EssentialProxyElement";
    private static final String LEFT_SIDE_CLASS = "gg.essential.gui.menu.LeftSideBar";
    /** Not one of Essential's ids: the whole left side bar, which has no stand-in button of its own. */
    public static final String LEFT_SIDE = "left_side_bar";

    /** Essential's button ids and what they're called on screen, in menu order. */
    public static final Map<String, String> KNOWN = new LinkedHashMap<String, String>();

    static {
        KNOWN.put("invite_host", "Host");
        KNOWN.put("social", "Social (friends)");
        KNOWN.put("message", "Messages");
        KNOWN.put("wardrobe", "Wardrobe");
        KNOWN.put("pictures", "Pictures");
        KNOWN.put("settings", "Essential Settings");
        KNOWN.put("account", "Account");
        KNOWN.put("update", "Update button");
        KNOWN.put("beta", "Beta badge");
        KNOWN.put("player", "Player model");
        KNOWN.put(LEFT_SIDE, "Left side: sale banner, NEW badge");
    }

    /** Hidden until switched back on. Friends, hosting and settings stay. */
    private static final Set<String> DEFAULT_HIDDEN = new HashSet<String>(Arrays.asList(
            "wardrobe", "pictures", "account", "beta", "player", LEFT_SIDE));

    private static final Set<String> HIDDEN = new LinkedHashSet<String>();
    /** Every Essential button id seen this session, so new ones show up in the list. */
    private static final Set<String> SEEN = new LinkedHashSet<String>();
    private static File file = new File("config", "menutrim.txt");
    private static Method idGetter;
    private static boolean idFailed;
    private static boolean leftSideFailed;
    private static boolean loaded;

    /** How a version shows or hides one of Essential's stand-in buttons. */
    public interface Visibility {
        void set(Object widget, boolean visible);
    }

    private TrimCore() {}

    /** Optional: the loader's config folder. Defaults to "config" in the game folder. */
    public static void init(File configDir) {
        if (configDir != null) file = new File(configDir, "menutrim.txt");
        load();
    }

    // --- Hiding -------------------------------------------------------------------

    /** Hides every switched-off Essential button among these widgets, and the left side bar. */
    public static void apply(Object screen, Iterable<?> widgets, Visibility visibility) {
        load();
        if (widgets != null) {
            for (Object widget : new ArrayList<Object>(toCollection(widgets))) {
                String id = essentialId(widget);
                if (id == null) continue;
                if (SEEN.add(id)) LOGGER.info("Found Essential button: " + id);
                visibility.set(widget, !isHidden(id));
            }
        }
        if (HIDDEN.contains(LEFT_SIDE)) hideLeftSide(screen);
    }

    private static Collection<?> toCollection(Iterable<?> items) {
        if (items instanceof Collection) return (Collection<?>) items;
        List<Object> list = new ArrayList<Object>();
        for (Object item : items) list.add(item);
        return list;
    }

    public static boolean isHidden(String id) {
        load();
        if (HIDDEN.contains(id)) return true;
        // Extra copies, like the second Wardrobe button "wardrobe_2", follow the main one.
        int underscore = id.indexOf('_');
        return underscore > 0 && !KNOWN.containsKey(id) && HIDDEN.contains(id.substring(0, underscore));
    }

    public static void setHidden(String id, boolean hidden) {
        load();
        if (hidden) HIDDEN.add(id);
        else HIDDEN.remove(id);
        save();
    }

    public static boolean anySeen() {
        return !SEEN.isEmpty();
    }

    /** The ids to list on the settings screen: the known ones, then any new ones seen. */
    public static List<String> listedIds() {
        List<String> ids = new ArrayList<String>(KNOWN.keySet());
        for (String id : SEEN) if (!ids.contains(id)) ids.add(id);
        return ids;
    }

    public static String label(String id) {
        String name = KNOWN.containsKey(id) ? KNOWN.get(id) : id;
        return name + ": " + (isHidden(id) ? "Hidden" : "Shown");
    }

    /** The Essential id of one of its stand-in buttons, or null for any other button. */
    private static String essentialId(Object widget) {
        if (widget == null || idFailed || !isProxy(widget.getClass())) return null;
        try {
            if (idGetter == null) idGetter = findMethod(widget.getClass(), "getEssentialId");
            if (idGetter == null) throw new NoSuchMethodException("getEssentialId");
            Object id = idGetter.invoke(widget);
            return id instanceof String ? (String) id : null;
        } catch (Throwable e) {
            idFailed = true;
            LOGGER.warn("Couldn't read Essential's button ids. Essential may have changed, so nothing is hidden.", e);
            return null;
        }
    }

    private static boolean isProxy(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(PROXY_CLASS)) return true;
        }
        return false;
    }

    /**
     * Finds Essential's left side bar in its menu window and hides it, using
     * only the public hide() of Essential's UI library. Showing it again takes
     * effect the next time the menu opens.
     */
    private static void hideLeftSide(Object screen) {
        if (leftSideFailed || screen == null) return;
        try {
            Method getHandler = findMethod(screen.getClass(), "essential$getProxyHandler");
            if (getHandler == null) return; // Essential isn't on this screen
            Object handler = getHandler.invoke(screen);
            if (handler == null) return;
            Object layer = readField(handler, "layer");
            if (layer == null) return;
            Method getWindow = findMethod(layer.getClass(), "getWindow");
            if (getWindow == null) return;
            hideMatching(getWindow.invoke(layer), 0);
        } catch (Throwable e) {
            leftSideFailed = true;
            LOGGER.warn("Couldn't reach Essential's left side bar, so its sale banner stays. Essential may have changed.", e);
        }
    }

    private static boolean hideMatching(Object component, int depth) throws Exception {
        if (component == null || depth > 40) return false;
        if (component.getClass().getName().equals(LEFT_SIDE_CLASS)) {
            Method hide = findMethod(component.getClass(), "hide", boolean.class);
            if (hide == null) return false;
            hide.invoke(component, true);
            if (SEEN.add(LEFT_SIDE)) LOGGER.info("Hid Essential's left side bar");
            return true;
        }
        Method getChildren = findMethod(component.getClass(), "getChildren");
        if (getChildren == null) return false;
        Object children = getChildren.invoke(component);
        if (!(children instanceof Iterable)) return false;
        for (Object child : new ArrayList<Object>(toCollection((Iterable<?>) children))) {
            if (hideMatching(child, depth + 1)) return true;
        }
        return false;
    }

    static Method findMethod(Class<?> type, String name, Class<?>... args) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getMethod(name, args);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // keep looking up
            } catch (SecurityException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object readField(Object target, String name) throws IllegalAccessException {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep looking up
            }
        }
        return null;
    }

    // --- Saving ---------------------------------------------------------------

    private static void load() {
        if (loaded) return;
        loaded = true;
        HIDDEN.clear();
        Path path = file.toPath();
        if (!Files.exists(path)) {
            HIDDEN.addAll(DEFAULT_HIDDEN);
            save();
            return;
        }
        try {
            boolean v2 = false;
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.equals("format=2")) v2 = true;
                if (line.startsWith("hide=")) {
                    for (String id : line.substring(5).split(",")) {
                        if (!id.trim().isEmpty()) HIDDEN.add(id.trim());
                    }
                }
            }
            // Settings from 1.0.0 didn't have the left side bar yet: hide it like a new install would.
            if (!v2) {
                HIDDEN.add(LEFT_SIDE);
                save();
            }
        } catch (Exception e) {
            LOGGER.warn("Couldn't read config/menutrim.txt, using the defaults", e);
            HIDDEN.addAll(DEFAULT_HIDDEN);
        }
    }

    private static void save() {
        StringBuilder text = new StringBuilder();
        text.append("# Menu Trim: the Essential buttons to hide, by id. Change them in game with the Menu button.\n");
        text.append("# Ids: ").append(join(KNOWN.keySet())).append('\n');
        text.append("format=2\n");
        text.append("hide=").append(join(HIDDEN)).append('\n');
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) parent.mkdirs();
            Files.write(file.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOGGER.warn("Couldn't save config/menutrim.txt", e);
        }
    }

    private static String join(Collection<String> items) {
        StringBuilder out = new StringBuilder();
        for (String item : items) {
            if (out.length() > 0) out.append(',');
            out.append(item);
        }
        return out.toString();
    }
}
