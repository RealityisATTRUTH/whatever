package com.shumsic.autodrinkominousbottles;

/**
 * Everything the mod needs from the game, kept small so each Minecraft era only has to fill in a few lines
 * (see GameImpl in src/era-*). Slots are the player's inventory: 0 to 8 is the hotbar, 9 to 35 the rest.
 */
public interface Game {

    /** In a world, with a player, and able to act on the inventory. */
    boolean inWorld();

    /** A menu, the inventory or chat is open, so pressing "use" now would do nothing or something else. */
    boolean screenOpen();

    /** The raid boss bar currently reads "Raid - Victory". */
    boolean raidVictoryShowing();

    /** The Bad Omen level a bottle in this slot gives (1 to 5), or 0 if the slot has no Ominous Bottle. */
    int bottleLevel(int slot);

    boolean slotEmpty(int slot);

    int selectedSlot();

    /** Select a hotbar slot and tell the server. */
    void selectSlot(int hotbarSlot);

    /** Swap an inventory slot (9 to 35) with a hotbar slot (0 to 8), like pressing a number key over it. */
    void swapWithHotbar(int inventorySlot, int hotbarSlot);

    /** Start using the held item and keep holding "use". */
    void startUsing();

    /** Let go of "use". */
    void stopUsing();

    /** The player is in the middle of using an item (drinking). */
    boolean usingItem();

    /** A short message above the hotbar. */
    void actionBar(String message);
}
