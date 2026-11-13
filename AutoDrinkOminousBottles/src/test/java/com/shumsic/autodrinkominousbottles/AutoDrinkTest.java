package com.shumsic.autodrinkominousbottles;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the drinking logic against a pretend game, without Minecraft. Run with tests.sh.
 */
public final class AutoDrinkTest {

    /** A pretend game: an inventory of bottle levels, a boss bar, and a log of what the mod did. */
    static final class FakeGame implements Game {
        final int[] levels = new int[36];
        final boolean[] filled = new boolean[36];
        boolean victory, screen, using;
        int selected, useTicks;
        final List<String> log = new ArrayList<>();

        @Override public boolean inWorld() { return true; }
        @Override public boolean screenOpen() { return screen; }
        @Override public boolean raidVictoryShowing() { return victory; }
        @Override public int bottleLevel(int slot) { return levels[slot]; }
        @Override public boolean slotEmpty(int slot) { return !filled[slot] && levels[slot] == 0; }
        @Override public int selectedSlot() { return selected; }
        @Override public void selectSlot(int s) { selected = s; log.add("select " + s); }
        @Override public void swapWithHotbar(int inv, int hot) {
            int l = levels[inv]; levels[inv] = levels[hot]; levels[hot] = l;
            boolean f = filled[inv]; filled[inv] = filled[hot]; filled[hot] = f;
            log.add("swap " + inv + " " + hot);
        }
        @Override public void startUsing() { using = true; useTicks = 32; log.add("drink level " + levels[selected]); }
        @Override public void stopUsing() { using = false; log.add("stop"); }
        @Override public boolean usingItem() { return using; }
        @Override public void actionBar(String m) { log.add("say " + m); }

        void tickWorld() { if (using && --useTicks <= 0) { using = false; levels[selected] = 0; } }
    }

    static int failures;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static AutoDrink on(FakeGame g) throws Exception {
        File dir = Files.createTempDirectory("adob").toFile();
        Settings s = new Settings(dir);
        s.setEnabled(true);
        return new AutoDrink(g, s);
    }

    static void run(AutoDrink mod, FakeGame g, int ticks) {
        for (int i = 0; i < ticks; i++) { mod.tick(); g.tickWorld(); }
    }

    public static void main(String[] args) throws Exception {
        // Picks the highest level in the hotbar, drinks it, and goes back to the slot you had.
        FakeGame g = new FakeGame();
        g.levels[2] = 1; g.levels[5] = 4; g.levels[7] = 2; g.selected = 0;
        AutoDrink mod = on(g);
        g.victory = true;
        run(mod, g, 60);
        check(g.log.contains("drink level 4"), "drinks the highest level in the hotbar");
        check(g.selected == 0, "puts the selected slot back");
        check(g.log.stream().filter(l -> l.startsWith("drink")).count() == 1, "drinks only once per victory");

        // The bar flickers off and on: no second drink until it has been gone a while.
        g.victory = false; run(mod, g, 10); g.victory = true; run(mod, g, 60);
        check(g.log.stream().filter(l -> l.startsWith("drink")).count() == 1, "a flicker doesn't drink again");

        // Next raid, after the bar has been gone long enough.
        g.victory = false; run(mod, g, 60); g.victory = true; run(mod, g, 60);
        check(g.log.stream().filter(l -> l.startsWith("drink")).count() == 2, "the next raid drinks again");

        // Nothing in the hotbar: pulls the best one from the inventory into an empty hotbar slot.
        g = new FakeGame();
        g.levels[12] = 2; g.levels[30] = 5; g.filled[0] = true; g.selected = 0;
        mod = on(g); g.victory = true; run(mod, g, 60);
        check(g.log.contains("swap 30 1"), "pulls the level 5 bottle into the first empty hotbar slot");
        check(g.log.contains("drink level 5"), "then drinks it");

        // A menu is open when the raid ends: waits, then drinks once it closes.
        g = new FakeGame();
        g.levels[3] = 3; g.screen = true;
        mod = on(g); g.victory = true; run(mod, g, 30);
        check(g.log.stream().noneMatch(l -> l.startsWith("drink")), "doesn't drink with a menu open");
        g.screen = false; run(mod, g, 60);
        check(g.log.contains("drink level 3"), "drinks once the menu closes");

        // No bottles at all.
        g = new FakeGame();
        mod = on(g); g.victory = true; run(mod, g, 10);
        check(g.log.stream().anyMatch(l -> l.contains("no Ominous Bottles")), "says so when there are no bottles");

        // Turned off: nothing happens, and the setting is remembered.
        File dir = Files.createTempDirectory("adob").toFile();
        Settings s = new Settings(dir);
        check(!s.enabled(), "off by default");
        s.setEnabled(true);
        check(new Settings(dir).enabled(), "remembers being turned on");
        g = new FakeGame(); g.levels[0] = 1;
        s.setEnabled(false);
        mod = new AutoDrink(g, s); g.victory = true; run(mod, g, 60);
        check(g.log.stream().noneMatch(l -> l.startsWith("drink")), "does nothing while off");

        System.out.println(failures == 0 ? "all passed" : failures + " failed");
        if (failures > 0) System.exit(1);
    }
}
