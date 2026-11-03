package com.reality.menutrim.core;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Menu Trim settings screen shows, as rows, so every version's
 * screen only has to lay out buttons. Page 0 is the Essential buttons list,
 * page 1 the Essential Extras.
 */
public final class Rows {

    public static final int LABEL = 0;
    public static final int TOGGLE = 1;
    public static final int OPEN_EXTRAS = 2;
    public static final int BACK = 3;

    /** One row: a label (a button you can't click), a toggle or a navigation button. */
    public static final class Row {
        public final int kind;
        private final String fixed;
        private final String id;
        private final EssentialExtras.Feature feature;
        private final boolean trimAll;

        private Row(int kind, String fixed, String id, EssentialExtras.Feature feature, boolean trimAll) {
            this.kind = kind;
            this.fixed = fixed;
            this.id = id;
            this.feature = feature;
            this.trimAll = trimAll;
        }

        /** The text to show right now. */
        public String text() {
            if (id != null) return TrimCore.label(id);
            if (feature != null) return EssentialExtras.label(feature);
            return fixed;
        }

        /** Runs a toggle. Returns true if every row's text should be refreshed. */
        public boolean click() {
            if (id != null) {
                TrimCore.setHidden(id, !TrimCore.isHidden(id));
            } else if (feature != null) {
                Boolean on = EssentialExtras.isOn(feature);
                if (on != null) EssentialExtras.setOn(feature, !on);
            } else if (trimAll) {
                EssentialExtras.trimAll();
            }
            return true;
        }
    }

    private Rows() {}

    public static String title(int page) {
        return page == 0 ? "Menu Trim" : "Essential Extras";
    }

    /** The top label rows. */
    public static List<Row> header(int page) {
        List<Row> rows = new ArrayList<Row>();
        if (page == 0) {
            rows.add(new Row(LABEL, TrimCore.anySeen()
                    ? "Click to show or hide Essential's buttons"
                    : "No Essential buttons found yet", null, null, false));
        } else {
            rows.add(new Row(LABEL, EssentialExtras.available()
                    ? "Essential's own settings. Friends and hosting stay"
                    : "Essential isn't installed", null, null, false));
        }
        return rows;
    }

    /** The toggle rows. */
    public static List<Row> toggles(int page) {
        List<Row> rows = new ArrayList<Row>();
        if (page == 0) {
            for (String id : TrimCore.listedIds()) rows.add(new Row(TOGGLE, null, id, null, false));
        } else if (EssentialExtras.available()) {
            for (EssentialExtras.Feature feature : EssentialExtras.FEATURES) {
                rows.add(new Row(TOGGLE, null, null, feature, false));
            }
            rows.add(new Row(TOGGLE, "Trim All: turn every extra off", null, null, true));
        }
        return rows;
    }

    /** The bottom navigation rows. */
    public static List<Row> footer(int page) {
        List<Row> rows = new ArrayList<Row>();
        if (page == 0) rows.add(new Row(OPEN_EXTRAS, "Essential Extras...", null, null, false));
        rows.add(new Row(BACK, page == 0 ? "Done" : "Back", null, null, false));
        return rows;
    }
}
