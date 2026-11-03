package dev.reality.fightbot.core.place;

/**
 * One right click on a block face, the same input a player sends: which hotbar slot to hold, which block
 * to click, which face, and the exact point on that face. What it should cause is in expect.
 */
public record PlaceRequest(
        /** "obsidian", "crystal", "anchor", "charge", or "detonate". */
        String what,
        int slot,
        /** The block clicked. */
        int cx, int cy, int cz,
        /** "up", "down", "north", "south", "west", or "east". */
        String face,
        /** The point on the face, used for aiming. */
        double hx, double hy, double hz,
        /** The block position the result should show up at. */
        int tx, int ty, int tz,
        long tick
) {
    public String key() {
        return what + "@" + tx + "," + ty + "," + tz;
    }
}
