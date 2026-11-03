package com.reality.menutrim;

import com.reality.menutrim.core.Rows;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** The Menu Trim settings screen: every row is a button, labels are buttons you can't click. */
public class TrimScreen extends Screen {

    private static final int WIDTH = 220;
    private static final int ROW = 22;

    private final Screen parent;
    private final int page;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Rows.Row> rows = new ArrayList<>();

    public TrimScreen(Screen parent, int page) {
        super(Ui.text(Rows.title(page)));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        buttons.clear();
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
        int perColumn = (toggles.size() + columns - 1) / columns;
        int total = columns * WIDTH + (columns - 1) * 8;
        int left = center - total / 2;
        for (int i = 0; i < toggles.size(); i++) {
            int x = left + (i / Math.max(1, perColumn)) * (WIDTH + 8);
            place(toggles.get(i).text(), toggles.get(i), x, y + (i % Math.max(1, perColumn)) * ROW, WIDTH);
        }
        List<Rows.Row> footer = Rows.footer(page);
        int footerWidth = 150;
        int footerLeft = center - (footer.size() * footerWidth + (footer.size() - 1) * 8) / 2;
        for (int i = 0; i < footer.size(); i++) {
            place(footer.get(i).text(), footer.get(i), footerLeft + i * (footerWidth + 8), footerTop, footerWidth);
        }
    }

    private void place(String text, Rows.Row row, int x, int y, int width) {
        Button button = Ui.button(x, y, width, 20, text, () -> pressed(row));
        if (row == null || row.kind == Rows.LABEL) button.active = false;
        buttons.add(button);
        rows.add(row);
        this.addRenderableWidget(button);
    }

    private void pressed(Rows.Row row) {
        if (row == null) return;
        if (row.kind == Rows.OPEN_EXTRAS) {
            Ui.open(new TrimScreen(this, 1));
        } else if (row.kind == Rows.BACK) {
            this.onClose();
        } else if (row.kind == Rows.TOGGLE && row.click()) {
            for (int i = 0; i < buttons.size(); i++) {
                Rows.Row r = rows.get(i);
                if (r != null) Ui.setLabel(buttons.get(i), r.text());
            }
        }
    }

    @Override
    public void onClose() {
        Ui.open(parent);
    }
}
