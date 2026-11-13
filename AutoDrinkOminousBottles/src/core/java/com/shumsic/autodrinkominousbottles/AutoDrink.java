package com.shumsic.autodrinkominousbottles;

/**
 * The whole mod, the same on every version and loader. Call tick() once per client tick.
 *
 * When the raid boss bar turns to "Raid - Victory", it drinks one Ominous Bottle: the highest level in the hotbar,
 * or, if the hotbar has none, the highest level from the rest of the inventory, swapped into the hotbar first.
 * Then it puts your selected slot back.
 */
public final class AutoDrink {

    /** Ticks the victory bar has to be gone before the next raid can trigger again. */
    private static final int REARM_TICKS = 40;
    /** An Ominous Bottle takes 32 ticks to drink. Give up holding "use" after this many, whatever happens. */
    private static final int MAX_HOLD_TICKS = 50;
    /** Wait after drinking before switching back, so the server finishes the drink first. */
    private static final int SWAP_BACK_TICKS = 3;
    /** If the victory showed while a menu was open, still drink once it closes, within this many ticks. */
    private static final int PENDING_TICKS = 200;

    private enum State { IDLE, STARTING, DRINKING, SWAP_BACK }

    private final Game game;
    private final Settings settings;

    private State state = State.IDLE;
    private int timer;
    private int returnSlot = -1;
    private boolean sawVictory;      // the bar showed victory last tick
    private boolean firedThisRaid;   // already drank for the victory on screen
    private int goneTicks;           // ticks since the victory bar was last seen
    private int pending;             // victory seen while a menu was open: drink when it closes

    public AutoDrink(Game game, Settings settings) {
        this.game = game;
        this.settings = settings;
    }

    public boolean enabled() {
        return settings.enabled();
    }

    /** The toggle key was pressed. */
    public void toggle() {
        settings.setEnabled(!settings.enabled());
        if (!settings.enabled()) cancel();
        firedThisRaid = false;
        if (game.inWorld()) game.actionBar("Auto-Drink Ominous Bottles: " + (settings.enabled() ? "On" : "Off"));
    }

    public void tick() {
        if (!game.inWorld()) {
            reset();
            return;
        }
        if (state != State.IDLE) {
            continueDrinking();
            return;
        }
        if (!settings.enabled()) return;

        boolean victory = game.raidVictoryShowing();
        if (victory) {
            goneTicks = 0;
            if (!sawVictory && !firedThisRaid) {
                firedThisRaid = true;
                pending = PENDING_TICKS;
            }
        } else if (++goneTicks >= REARM_TICKS) {
            firedThisRaid = false;
        }
        sawVictory = victory;

        if (pending > 0) {
            pending--;
            if (!game.screenOpen()) {
                pending = 0;
                drink();
            }
        }
    }

    private void drink() {
        int slot = bestSlot(0, 9);
        if (slot < 0) {
            int fromInventory = bestSlot(9, 36);
            if (fromInventory < 0) {
                game.actionBar("Raid won, but you have no Ominous Bottles");
                return;
            }
            slot = emptyHotbarSlot();
            if (slot < 0) slot = (game.selectedSlot() + 1) % 9;   // its item goes where the bottle was
            game.swapWithHotbar(fromInventory, slot);
        }
        returnSlot = game.selectedSlot();
        game.selectSlot(slot);
        state = State.STARTING;
        timer = 0;
    }

    private void continueDrinking() {
        timer++;
        switch (state) {
            case STARTING -> {
                // Wait a tick after switching slots so the server knows what we're holding, then drink.
                game.startUsing();
                state = State.DRINKING;
                timer = 0;
            }
            case DRINKING -> {
                boolean done = timer > 2 && !game.usingItem();
                if (done || timer >= MAX_HOLD_TICKS || game.screenOpen()) {
                    game.stopUsing();
                    state = State.SWAP_BACK;
                    timer = 0;
                }
            }
            case SWAP_BACK -> {
                if (timer >= SWAP_BACK_TICKS) {
                    if (returnSlot >= 0) game.selectSlot(returnSlot);
                    returnSlot = -1;
                    state = State.IDLE;
                }
            }
            default -> state = State.IDLE;
        }
    }

    /** The slot in [from, to) with the highest-level bottle, or -1. */
    private int bestSlot(int from, int to) {
        int best = -1, bestLevel = 0;
        for (int i = from; i < to; i++) {
            int level = game.bottleLevel(i);
            if (level > bestLevel) {
                best = i;
                bestLevel = level;
            }
        }
        return best;
    }

    private int emptyHotbarSlot() {
        for (int i = 0; i < 9; i++) if (game.slotEmpty(i)) return i;
        return -1;
    }

    private void cancel() {
        if (state == State.DRINKING) game.stopUsing();
        if (state != State.IDLE && returnSlot >= 0 && game.inWorld()) game.selectSlot(returnSlot);
        state = State.IDLE;
        returnSlot = -1;
        pending = 0;
    }

    private void reset() {
        state = State.IDLE;
        returnSlot = -1;
        pending = 0;
        sawVictory = false;
        firedThisRaid = false;
        goneTicks = 0;
    }
}
