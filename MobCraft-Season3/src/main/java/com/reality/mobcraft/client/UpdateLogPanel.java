package com.reality.mobcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reality.mobcraft.MobCraft;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Update Log panel on the title screen. Its text comes from
 * assets/mobcraft/update_log.txt: "# " lines are version headings, "- " lines
 * are entries, "##" lines are notes that don't show. Newest goes at the top.
 *
 *   Scroll    the mouse wheel over the panel scrolls the rest into view.
 *   Close     the button on the right of the title bar folds the panel down
 *             to just its title bar. Click it again to open it.
 *   Move      drag the title bar to put the panel anywhere on the screen.
 *   Reset     right-click the title bar to put it back where it started.
 *
 * Open or closed, and where it is, are saved in config/mobcraft-update-log.txt,
 * so it stays the way you left it.
 */
public final class UpdateLogPanel {

    private static final int MARGIN = 8;
    private static final int PADDING = 6;
    private static final int MAX_WIDTH = 190;
    private static final int MIN_WIDTH = 110;
    private static final int BACKGROUND = 0xA0000000;
    private static final int TITLE_BAR = 0xC0202020;
    private static final int TITLE_BAR_HOVER = 0xC0303030;
    private static final int BORDER = 0xFF5A5A5A;
    private static final int BORDER_DRAG = 0xFFFFD84A;
    private static final int TITLE_COLOR = 0xFFFFD84A;
    private static final int HEADING_COLOR = 0xFF7FE0C8;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int BUTTON_COLOR = 0xFFC0C0C0;
    private static final int BUTTON_HOVER = 0xFFFFFFFF;
    private static final int SCROLLBAR = 0xFFAAAAAA;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mobcraft-update-log.txt");

    private record Line(String text, int color, int indent) {}

    private static List<String[]> entries; // {kind, text}, read once

    // Saved: open or closed, and the top left corner. -1 means the starting spot.
    private static boolean open = true;
    private static int savedX = -1, savedY = -1;
    private static boolean loaded;

    // Where the panel was last drawn, for the mouse, and how far it's scrolled.
    private static int panelLeft, panelTop, panelRight, panelBottom, barBottom;
    private static int buttonLeft, buttonRight;
    private static int scroll, maxScroll;

    // Dragging the title bar.
    private static boolean dragging;
    private static double grabX, grabY;

    private UpdateLogPanel() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen)) return;
            load();
            scroll = 0;
            dragging = false;
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
                    draw(s, graphics, mouseX, mouseY));
            // Backup for UpdateLogScrollMixin, which normally handles the wheel first.
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
                    !scrollAt(s, mouseX, mouseY, vertical));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> click(event));
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> {
                if (!dragging) return true;
                savedX = (int) Math.round(event.x() - grabX);
                savedY = (int) Math.round(event.y() - grabY);
                return false;
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
                if (!dragging) return true;
                dragging = false;
                save();
                return false;
            });
        });
    }

    /**
     * Scrolls the log if the mouse is over it on the title screen. Returns
     * whether it used the wheel. Called first thing by UpdateLogScrollMixin,
     * so mods drawing under the panel (like Essential's player model) can't
     * take the scroll.
     */
    public static boolean scrollAt(Screen screen, double x, double y, double vertical) {
        if (!(screen instanceof TitleScreen) || !open || !overPanel(x, y)) return false;
        if (maxScroll > 0 && vertical != 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(vertical) * 3));
        }
        return true;
    }

    /** Clicks on the panel are the panel's, so the menu buttons under it don't get them. */
    private static boolean click(MouseButtonEvent event) {
        double x = event.x(), y = event.y();
        if (!overPanel(x, y)) return true;
        boolean onBar = y < barBottom;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && onBar) {
            if (x >= buttonLeft && x < buttonRight) {
                open = !open;
                scroll = 0;
                save();
                playClick();
            } else {
                dragging = true;
                grabX = x - panelLeft;
                grabY = y - panelTop;
            }
        } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && onBar) {
            savedX = -1;
            savedY = -1;
            save();
            playClick();
        }
        return false;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f));
    }

    private static boolean overPanel(double x, double y) {
        return x >= panelLeft && x < panelRight && y >= panelTop && y < panelBottom;
    }

    private static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Font font = Screens.getFont(screen);
        // Starts left of the centered 200-wide menu buttons.
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, screen.width / 2 - 100 - MARGIN * 2));
        if (screen.width < width + MARGIN * 2) return;
        int lineHeight = font.lineHeight + 2;
        int barHeight = font.lineHeight + PADDING * 2;

        int left = savedX < 0 ? MARGIN : savedX;
        int top = savedY < 0 ? Math.max(MARGIN, screen.height / 4 + 8) : savedY;
        left = Math.max(0, Math.min(screen.width - width, left));
        top = Math.max(0, Math.min(screen.height - barHeight, top));

        List<Line> lines = open ? layout(font, width - PADDING * 2) : List.of();
        int bottom = savedY < 0 ? screen.height - 24 : screen.height - MARGIN;
        int fits = Math.max(0, (bottom - top - barHeight - PADDING * 2) / lineHeight);
        int shown = Math.min(fits, lines.size());
        int height = barHeight + (open && shown > 0 ? PADDING * 2 + shown * lineHeight : 0);
        maxScroll = Math.max(0, lines.size() - shown);
        scroll = Math.min(scroll, maxScroll);

        panelLeft = left;
        panelTop = top;
        panelRight = left + width;
        panelBottom = top + height;
        barBottom = top + barHeight;
        String button = open ? "-" : "+";
        int buttonWidth = font.width(button) + 8;
        buttonRight = panelRight - 2;
        buttonLeft = buttonRight - buttonWidth;

        boolean overBar = mouseX >= left && mouseX < panelRight && mouseY >= top && mouseY < barBottom;
        boolean overButton = overBar && mouseX >= buttonLeft && mouseX < buttonRight;

        graphics.fill(left, top, panelRight, panelBottom, BACKGROUND);
        graphics.fill(left, top, panelRight, barBottom, overBar && !overButton || dragging ? TITLE_BAR_HOVER : TITLE_BAR);
        graphics.outline(left, top, width, height, dragging ? BORDER_DRAG : BORDER);
        graphics.text(font, "Update Log", left + PADDING, top + PADDING, TITLE_COLOR, true);
        graphics.text(font, button, buttonLeft + 4, top + PADDING, overButton ? BUTTON_HOVER : BUTTON_COLOR, true);
        if (!open || shown == 0) return;

        int listTop = barBottom + PADDING;
        int y = listTop;
        for (int i = scroll; i < scroll + shown; i++) {
            Line line = lines.get(i);
            graphics.text(font, line.text(), left + PADDING + line.indent(), y, line.color(), true);
            y += lineHeight;
        }

        // Scrollbar on the right edge when there's more than fits.
        if (maxScroll > 0) {
            int trackHeight = shown * lineHeight;
            int thumb = Math.max(8, trackHeight * shown / lines.size());
            int thumbTop = listTop + (trackHeight - thumb) * scroll / maxScroll;
            int x = panelRight - 4;
            graphics.fill(x, listTop, x + 2, listTop + trackHeight, 0x40FFFFFF);
            graphics.fill(x, thumbTop, x + 2, thumbTop + thumb, SCROLLBAR);
        }
    }

    // --- Saving ---------------------------------------------------------------

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try {
            for (String raw : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] parts = raw.split("=", 2);
                if (parts.length != 2) continue;
                String value = parts[1].trim();
                switch (parts[0].trim()) {
                    case "open" -> open = !value.equals("false");
                    case "x" -> savedX = parseOr(value, -1);
                    case "y" -> savedY = parseOr(value, -1);
                    default -> { }
                }
            }
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't read the update log settings", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "open=" + open + "\nx=" + savedX + "\ny=" + savedY + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't save the update log settings", e);
        }
    }

    private static int parseOr(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Wraps every entry to the panel's width. */
    private static List<Line> layout(Font font, int width) {
        List<Line> out = new ArrayList<>();
        for (String[] entry : entries()) {
            if (entry[0].equals("#")) {
                if (!out.isEmpty()) out.add(new Line("", TEXT_COLOR, 0));
                out.add(new Line(entry[1], HEADING_COLOR, 0));
            } else {
                boolean first = true;
                for (String part : wrap(font, entry[1], width - 8)) {
                    out.add(new Line((first ? "- " : "  ") + part, TEXT_COLOR, 0));
                    first = false;
                }
            }
        }
        return out;
    }

    private static List<String> wrap(Font font, String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (font.width(test) > width && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private static List<String[]> entries() {
        if (entries != null) return entries;
        entries = new ArrayList<>();
        try (InputStream in = UpdateLogPanel.class.getResourceAsStream("/assets/mobcraft/update_log.txt")) {
            if (in == null) return entries;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("##")) continue;
                if (line.startsWith("# ")) {
                    entries.add(new String[] {"#", line.substring(2)});
                } else if (line.startsWith("- ")) {
                    entries.add(new String[] {"-", line.substring(2)});
                }
            }
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't read the update log", e);
        }
        return entries;
    }
}
