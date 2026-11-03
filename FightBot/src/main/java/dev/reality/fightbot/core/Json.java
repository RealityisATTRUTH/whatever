package dev.reality.fightbot.core;

/** Tiny dependency-free JSON object writer, used for the fight log lines. */
public final class Json {
    private final StringBuilder sb = new StringBuilder("{");
    private boolean first = true;

    private void key(String k) {
        if (!first) sb.append(',');
        first = false;
        quote(sb, k);
        sb.append(':');
    }

    public Json put(String k, String v) {
        key(k);
        if (v == null) sb.append("null"); else quote(sb, v);
        return this;
    }

    public Json put(String k, long v) {
        key(k);
        sb.append(v);
        return this;
    }

    public Json put(String k, double v) {
        key(k);
        if (Double.isFinite(v)) sb.append(Math.round(v * 1000.0) / 1000.0); else sb.append("null");
        return this;
    }

    /** A number with a chosen number of decimals. Missing values, NaN or infinite, are written as null. */
    public Json put(String k, double v, int decimals) {
        key(k);
        double scale = Math.pow(10, decimals);
        if (Double.isFinite(v)) sb.append(Math.round(v * scale) / scale); else sb.append("null");
        return this;
    }

    /** A yes, no, or unknown value. Unknown is written as null. */
    public Json put(String k, Boolean v) {
        key(k);
        sb.append(v == null ? "null" : v.toString());
        return this;
    }

    public Json put(String k, boolean v) {
        key(k);
        sb.append(v);
        return this;
    }

    public Json raw(String k, String rawJson) {
        key(k);
        sb.append(rawJson);
        return this;
    }

    public String build() {
        return sb + "}";
    }

    public static String quoteAll(Iterable<String> values) {
        StringBuilder out = new StringBuilder("[");
        boolean f = true;
        for (String v : values) {
            if (!f) out.append(',');
            f = false;
            quote(out, v);
        }
        return out.append(']').toString();
    }

    public static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
}
