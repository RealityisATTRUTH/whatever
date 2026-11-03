package dev.reality.fightbot;

import dev.reality.fightbot.core.BotAction;
import net.minecraft.server.MinecraftServer;

/**
 * Sends inputs to a Carpet fake player with Carpet's own player command, so the server handles
 * every attack, jump, and eat exactly like a real player's. Only changes are sent.
 */
final class CarpetInputs {
    private final MinecraftServer server;
    private final String name;
    private int forward, strafe, hotbar = -1;
    private boolean sprint, sneak, using, jumpHeld;

    CarpetInputs(MinecraftServer server, String name) {
        this.server = server;
        this.name = name;
    }

    void apply(BotAction a) {
        apply(a, a.sprint);
    }

    /**
     * sprintingNow is whether the game has the bot sprinting. A sprint hit, a wall, or hunger turns
     * sprinting off on the server, and a real client presses sprint again, so this does too.
     */
    void apply(BotAction a, boolean sprintingNow) {
        // Right click covers eating and the shield. Release goes first because Carpet's stop also
        // clears movement, which is then sent again below in the same tick.
        boolean wantUse = a.use || a.block;
        // Carpet has no release for one held action, so letting go of jump or use goes through stop.
        if ((!wantUse && using) || (!a.holdJump && jumpHeld)) stopActionsKeepMovement();
        if (a.hotbar >= 0 && a.hotbar != hotbar) {
            run("hotbar " + (a.hotbar + 1));
            hotbar = a.hotbar;
        }
        if (a.forward != forward || a.strafe != strafe) {
            run("move");
            // Carpet's move with no direction also lets go of sprint and sneak, so both are sent again below.
            sprint = false;
            sneak = false;
            if (a.forward > 0) run("move forward");
            if (a.forward < 0) run("move backward");
            if (a.strafe > 0) run("move left");
            if (a.strafe < 0) run("move right");
            forward = a.forward;
            strafe = a.strafe;
        }
        if (a.sprint != sprint || (a.sprint && a.forward > 0 && !sprintingNow)) {
            run(a.sprint ? "sprint" : "unsprint");
            sprint = a.sprint;
        }
        if (a.sneak != sneak) {
            run(a.sneak ? "sneak" : "unsneak");
            sneak = a.sneak;
        }
        if (wantUse && !using) {
            run("use continuous");
            using = true;
        }
        // Fireworks and wind charges are a single right click.
        if (a.useOnce && !using) run("use once");
        if (a.holdJump && !jumpHeld) {
            run("jump continuous");
            jumpHeld = true;
        }
        if (a.jump && !jumpHeld) run("jump once");
        if (a.attack) run("attack once");
    }

    /** The hotbar was changed outside Carpet, so the next hotbar input is always sent. */
    void forgetHotbar() {
        hotbar = -1;
    }

    /** Carpet's stop clears every action and movement, so movement is sent again next tick. */
    private void stopActionsKeepMovement() {
        run("stop");
        using = false;
        jumpHeld = false;
        forward = 0;
        strafe = 0;
        sprint = false;
        sneak = false;
    }

    void stopAll() {
        run("stop");
        run("unsprint");
        run("unsneak");
        using = false;
        jumpHeld = false;
        forward = strafe = 0;
        sprint = sneak = false;
    }

    private void run(String args) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "player " + name + " " + args);
    }
}
