package com.reality.menutrim;

import com.reality.menutrim.core.Rows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Minecraft 1.8.9 and 1.12.2: the Menu Trim settings screen. Labels are buttons you can't click. */
public class TrimScreen extends GuiScreen {

    private static final int WIDTH = 220;
    private static final int ROW = 22;

    private final GuiScreen parent;
    private final int page;
    private final List<Rows.Row> rows = new ArrayList<Rows.Row>();

    public TrimScreen(GuiScreen parent, int page) {
        this.parent = parent;
        this.page = page;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        rows.clear();
        int center = this.width / 2;
        int y = 8;
        place(Rows.title(page), null, center - WIDTH / 2, y, WIDTH);
        y += ROW;
        for (Rows.Row row : Rows.header(page)) {
            place(row.text(), row, center - WIDTH / 2, y, WIDTH);
            y += ROW;
        }
        List<Rows.Row> toggles = Rows.toggles(page);
        int footerTop = this.height - 28;
        int columns = y + toggles.size() * ROW > footerTop - 4 ? 2 : 1;
        int perColumn = Math.max(1, (toggles.size() + columns - 1) / columns);
        int total = columns * WIDTH + (columns - 1) * 8;
        int left = center - total / 2;
        for (int i = 0; i < toggles.size(); i++) {
            place(toggles.get(i).text(), toggles.get(i), left + (i / perColumn) * (WIDTH + 8), y + (i % perColumn) * ROW, WIDTH);
        }
        List<Rows.Row> footer = Rows.footer(page);
        int footerWidth = 150;
        int footerLeft = center - (footer.size() * footerWidth + (footer.size() - 1) * 8) / 2;
        for (int i = 0; i < footer.size(); i++) {
            place(footer.get(i).text(), footer.get(i), footerLeft + i * (footerWidth + 8), footerTop, footerWidth);
        }
    }

    private void place(String text, Rows.Row row, int x, int y, int width) {
        GuiButton button = new GuiButton(rows.size(), x, y, width, 20, text);
        if (row == null || row.kind == Rows.LABEL) button.enabled = false;
        rows.add(row);
        this.buttonList.add(button);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id < 0 || button.id >= rows.size()) return;
        Rows.Row row = rows.get(button.id);
        if (row == null) return;
        if (row.kind == Rows.OPEN_EXTRAS) {
            Minecraft.getMinecraft().displayGuiScreen(new TrimScreen(this, 1));
        } else if (row.kind == Rows.BACK) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
        } else if (row.kind == Rows.TOGGLE && row.click()) {
            for (GuiButton b : this.buttonList) {
                if (b.id >= 0 && b.id < rows.size() && rows.get(b.id) != null) b.displayString = rows.get(b.id).text();
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
