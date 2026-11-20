package com.reality.mobcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Mob Guide: every mob's passives, abilities, keys, home, allies, curse
 * and ultimate, in one book.
 *
 *   Open      the Mob Guide button at the top right of the title screen and
 *             the pause menu.
 *   Mobs      listed on the left, A to Z. It opens on the mob you are, marked
 *             "(you)". From the title screen it opens on the first mob.
 *   Read      click a mob to read it. Scroll the page with the mouse wheel.
 *
 * The text comes from assets/mobcraft/mob_guide.txt, so it can be edited
 * without touching code. "# TYPE" starts a mob, "* " is a small heading and
 * "- " an entry. An entry like "Golem Toss, R: your next hit..." shows
 * "Golem Toss, R" in gold above the text.
 */
public class MobGuideScreen extends Screen {

    private static final int LIST_WIDTH = 116;
    private static final int ROW = 12;
    private static final int TOP = 30;
    private static final int PAGE_PADDING = 8;

    private static final int PANEL = 0xB0000000;
    private static final int BORDER = 0xFF5A5A5A;
    private static final int SELECTED = 0x60FFD84A;
    private static final int HOVER = 0x30FFFFFF;
    private static final int TITLE = 0xFFFFD84A;
    private static final int NAME = 0xFFE0E0E0;
    private static final int YOU = 0xFF7FE07F;
    private static final int LABEL = 0xFFFFC040;
    private static final int ULTIMATE = 0xFFFF8040;
    private static final int HEADING = 0xFF7FE0C8;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int HINT = 0xFF909090;

    private record Line(String text, int color, int indent) {}

    private static Map<MobType, List<String>> guide;

    /** Every mob, A to Z by name. */
    private static final MobType[] SORTED = sortedMobs();

    private static MobType[] sortedMobs() {
        MobType[] mobs = MobType.values().clone();
        java.util.Arrays.sort(mobs, java.util.Comparator.comparing((MobType t) -> t.displayName, String.CASE_INSENSITIVE_ORDER));
        return mobs;
    }

    private final Screen parent;
    private final MobType you;
    private MobType shown;
    private int listScroll, pageScroll, pageMaxScroll;

    public MobGuideScreen(Screen parent, MobType you) {
        super(Component.literal("Mob Guide"));
        this.parent = parent;
        this.you = you;
        this.shown = you != null ? you : SORTED[0];
    }

    /** Adds the Mob Guide button to the title screen and the pause menu. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;
            Button button = Button.builder(Component.literal("Mob Guide"), b -> open(client, screen))
                    .bounds(width - 104, 6, 98, 20).build();
            Screens.getWidgets(screen).add(button);
        });
    }

    private static void open(Minecraft client, Screen parent) {
        MobType you = client.player == null ? null : PlayerMob.get(client.player);
        client.gui.setScreen(new MobGuideScreen(parent, you));
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.width / 2 - 75, this.height - 26, 150, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // --- Layout -------------------------------------------------------------------

    private int listLeft() {
        return 8;
    }

    private int listBottom() {
        return this.height - 44;
    }

    private int pageLeft() {
        return listLeft() + LIST_WIDTH + 6;
    }

    private int pageRight() {
        return this.width - 8;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - TOP - 4) / ROW);
    }

    // --- Drawing ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, "Mob Guide", this.width / 2, 10, TITLE);
        drawList(graphics, mouseX, mouseY);
        drawPage(graphics);
        graphics.centeredText(this.font, "R, G and V are ability keys, U is the ultimate. Change them in Controls.",
                this.width / 2, this.height - 39, HINT);
    }

    private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = listLeft();
        int bottom = listBottom();
        graphics.fill(left, TOP, left + LIST_WIDTH, bottom, PANEL);
        graphics.outline(left, TOP, LIST_WIDTH, bottom - TOP, BORDER);
        MobType[] mobs = SORTED;
        int rows = visibleRows();
        listScroll = Math.max(0, Math.min(listScroll, mobs.length - rows));
        for (int i = 0; i < rows && listScroll + i < mobs.length; i++) {
            MobType type = mobs[listScroll + i];
            int y = TOP + 2 + i * ROW;
            boolean hover = mouseX >= left && mouseX < left + LIST_WIDTH && mouseY >= y && mouseY < y + ROW;
            if (type == shown) graphics.fill(left + 1, y, left + LIST_WIDTH - 1, y + ROW, SELECTED);
            else if (hover) graphics.fill(left + 1, y, left + LIST_WIDTH - 1, y + ROW, HOVER);
            String name = type.displayName + (type == you ? " (you)" : "");
            graphics.text(this.font, name, left + 5, y + 2, type == you ? YOU : NAME, true);
        }
    }

    private void drawPage(GuiGraphicsExtractor graphics) {
        int left = pageLeft();
        int right = pageRight();
        int bottom = listBottom();
        if (right - left < 80) return;
        graphics.fill(left, TOP, right, bottom, PANEL);
        graphics.outline(left, TOP, right - left, bottom - TOP, BORDER);
        graphics.text(this.font, shown.displayName, left + PAGE_PADDING, TOP + PAGE_PADDING, TITLE, true);

        List<Line> lines = layout(right - left - PAGE_PADDING * 2);
        int lineHeight = this.font.lineHeight + 2;
        int listTop = TOP + PAGE_PADDING + lineHeight + 4;
        int fits = Math.max(0, (bottom - PAGE_PADDING - listTop) / lineHeight);
        pageMaxScroll = Math.max(0, lines.size() - fits);
        pageScroll = Math.max(0, Math.min(pageScroll, pageMaxScroll));
        int y = listTop;
        for (int i = pageScroll; i < Math.min(lines.size(), pageScroll + fits); i++) {
            Line line = lines.get(i);
            graphics.text(this.font, line.text(), left + PAGE_PADDING + line.indent(), y, line.color(), true);
            y += lineHeight;
        }
        if (pageMaxScroll > 0) {
            int track = fits * lineHeight;
            int thumb = Math.max(8, track * fits / lines.size());
            int thumbTop = listTop + (track - thumb) * pageScroll / pageMaxScroll;
            graphics.fill(right - 4, listTop, right - 2, listTop + track, 0x40FFFFFF);
            graphics.fill(right - 4, thumbTop, right - 2, thumbTop + thumb, 0xFFAAAAAA);
        }
    }

    /** The shown mob's entries, wrapped to the page. Labels go on their own line in gold. */
    private List<Line> layout(int width) {
        List<Line> out = new ArrayList<>();
        List<String> entries = guide().get(shown);
        if (entries == null || entries.isEmpty()) {
            out.add(new Line("No guide for this mob yet.", HINT, 0));
            return out;
        }
        for (String entry : entries) {
            if (entry.startsWith("* ")) {
                if (!out.isEmpty()) out.add(new Line("", TEXT, 0));
                out.add(new Line(entry.substring(2), HEADING, 0));
                continue;
            }
            if (!out.isEmpty()) out.add(new Line("", TEXT, 0));
            String text = entry;
            int colon = entry.indexOf(": ");
            if (colon > 0 && colon <= 32 && !entry.substring(0, colon).contains(".")) {
                String label = entry.substring(0, colon);
                text = entry.substring(colon + 2);
                out.add(new Line(label, label.startsWith("Ultimate") ? ULTIMATE : LABEL, 0));
            }
            if (!text.isEmpty()) text = Character.toUpperCase(text.charAt(0)) + text.substring(1);
            for (String part : wrap(text, width - 8)) out.add(new Line(part, TEXT, 8));
        }
        return out;
    }

    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (this.font.width(test) > width && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    // --- Input --------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        double x = event.x(), y = event.y();
        if (x < listLeft() || x >= listLeft() + LIST_WIDTH || y < TOP + 2 || y >= listBottom()) return false;
        int index = listScroll + (int) ((y - TOP - 2) / ROW);
        MobType[] mobs = SORTED;
        if (index < 0 || index >= mobs.length || (int) ((y - TOP - 2) / ROW) >= visibleRows()) return false;
        if (mobs[index] != shown) {
            shown = mobs[index];
            pageScroll = 0;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY);
        if (step == 0) return false;
        if (x < listLeft() + LIST_WIDTH) {
            listScroll -= step;
        } else {
            pageScroll = Math.max(0, Math.min(pageMaxScroll, pageScroll - step * 3));
        }
        return true;
    }

    // --- The guide text -------------------------------------------------------------

    private static Map<MobType, List<String>> guide() {
        if (guide != null) return guide;
        guide = new EnumMap<>(MobType.class);
        try (InputStream in = MobGuideScreen.class.getResourceAsStream("/assets/mobcraft/mob_guide.txt")) {
            if (in == null) return guide;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> current = null;
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("##")) continue;
                if (line.startsWith("# ")) {
                    current = null;
                    try {
                        current = new ArrayList<>();
                        guide.put(MobType.valueOf(line.substring(2).trim()), current);
                    } catch (IllegalArgumentException e) {
                        MobCraft.LOGGER.warn("Mob guide: no mob called {}", line.substring(2).trim());
                        current = null;
                    }
                } else if (current != null && line.startsWith("- ")) {
                    current.add(line.substring(2));
                } else if (current != null && line.startsWith("* ")) {
                    current.add(line);
                }
            }
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't read the mob guide", e);
        }
        return guide;
    }
}
