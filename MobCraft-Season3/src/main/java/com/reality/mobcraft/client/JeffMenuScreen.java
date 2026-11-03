package com.reality.mobcraft.client;

import com.reality.mobcraft.mob.Jeff;
import com.reality.mobcraft.mob.kit.VillagerKit;
import com.reality.mobcraft.net.JeffGivePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Jeff's Menu: every item in the game, his own creative menu in survival.
 * Type to search by name or id, click an item to get it. The amount button
 * switches between 1, 16 and 64 (capped at a full stack). The server checks
 * he's still powered up before giving anything.
 */
public class JeffMenuScreen extends Screen {

    private static final int BUTTON_W = 120;
    private static final int BUTTON_H = 18;
    private static final int GAP = 2;
    private static final int[] AMOUNTS = {1, 16, 64};

    private static String lastSearch = "";
    private static int amountIndex = 2;

    private record Entry(String id, String name) {}

    private static List<Entry> allItems;

    private final Screen parent;
    private final List<Entry> shown = new ArrayList<>();
    private final List<Button> slots = new ArrayList<>();
    private EditBox search;
    private Button prevButton;
    private Button nextButton;
    private int page;
    private int perPage;

    public JeffMenuScreen(Screen parent) {
        super(Component.literal("Jeff's Menu"));
        this.parent = parent;
    }

    private static List<Entry> allItems() {
        if (allItems == null) {
            List<Entry> list = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
                if (id.getPath().equals("air") || !VillagerKit.inJeffsMenu(id)) continue;
                String name = new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString();
                list.add(new Entry(id.toString(), name));
            }
            list.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
            allItems = List.copyOf(list);
        }
        return allItems;
    }

    @Override
    protected void init() {
        slots.clear();
        int columns = Math.max(1, Math.min(4, (this.width - 20) / (BUTTON_W + GAP)));
        int top = 62;
        int rows = Math.max(1, (this.height - top - 34) / (BUTTON_H + GAP));
        perPage = columns * rows;
        int gridWidth = columns * (BUTTON_W + GAP) - GAP;
        int left = this.width / 2 - gridWidth / 2;

        search = new EditBox(this.font, left, 36, gridWidth - 84, 20, Component.literal("Search"));
        search.setMaxLength(64);
        search.setValue(lastSearch);
        search.setResponder(text -> {
            lastSearch = text;
            page = 0;
            refresh();
        });
        this.addRenderableWidget(search);
        this.setInitialFocus(search);

        this.addRenderableWidget(Button.builder(amountLabel(), button -> {
            amountIndex = (amountIndex + 1) % AMOUNTS.length;
            button.setMessage(amountLabel());
        }).bounds(left + gridWidth - 80, 36, 80, 20).build());

        for (int i = 0; i < perPage; i++) {
            int x = left + (i % columns) * (BUTTON_W + GAP);
            int y = top + (i / columns) * (BUTTON_H + GAP);
            int slot = i;
            Button button = Button.builder(Component.empty(), b -> give(slot)).bounds(x, y, BUTTON_W, BUTTON_H).build();
            slots.add(this.addRenderableWidget(button));
        }

        int bottom = this.height - 26;
        prevButton = this.addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
            page--;
            refresh();
        }).bounds(left, bottom, 60, 20).build());
        nextButton = this.addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            page++;
            refresh();
        }).bounds(left + gridWidth - 60, bottom, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(this.width / 2 - 40, bottom, 80, 20).build());

        refresh();
    }

    private void refresh() {
        String query = lastSearch.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Entry entry : allItems()) {
            if (query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query) || entry.id().contains(query)) {
                shown.add(entry);
            }
        }
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < slots.size(); i++) {
            int index = page * perPage + i;
            Button button = slots.get(i);
            button.visible = index < shown.size();
            if (button.visible) button.setMessage(Component.literal(shorten(shown.get(index).name())));
        }
        prevButton.active = page > 0;
        nextButton.active = page < pages - 1;
    }

    private String shorten(String name) {
        if (this.font.width(name) <= BUTTON_W - 8) return name;
        while (!name.isEmpty() && this.font.width(name + "...") > BUTTON_W - 8) name = name.substring(0, name.length() - 1);
        return name + "...";
    }

    private void give(int slot) {
        int index = page * perPage + slot;
        if (index >= shown.size()) return;
        ClientPlayNetworking.send(new JeffGivePayload(shown.get(index).id(), AMOUNTS[amountIndex]));
    }

    private static Component amountLabel() {
        return Component.literal("Amount: " + AMOUNTS[amountIndex]);
    }

    @Override
    public void tick() {
        // Shut down while the menu is open: close it.
        if (this.minecraft.player != null && !Jeff.isPowered(this.minecraft.player)) this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int center = this.width / 2;
        graphics.centeredText(this.font, "Jeff's Menu", center, 10, 0xFFFFFFFF);
        String money = this.minecraft.player == null ? "" : "Money: " + Jeff.formatMoney(Jeff.money(this.minecraft.player));
        graphics.centeredText(this.font, money + "   " + shown.size() + " items", center, 22, 0xFF17D46B);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
