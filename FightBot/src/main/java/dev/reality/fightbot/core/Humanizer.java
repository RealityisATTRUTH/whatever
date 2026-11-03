package dev.reality.fightbot.core;

/**
 * The last step before inputs go to the game, so the bot looks like a person at a mouse and keyboard
 * without fighting worse:
 * <ul>
 * <li>A turn is capped at a fast human flick per tick, instead of snapping to any angle.</li>
 * <li>A click that needs the aim waits a tick or two for the crosshair to get there, then goes off.
 * The click is kept, never lost, so nothing the bot meant to do is skipped.</li>
 * <li>Turns move in whole steps of a real mouse at the chosen sensitivity, the way every player's do.</li>
 * <li>Clicks are capped at a fast human rate.</li>
 * </ul>
 * Block clicks and mining use exact block positions, so the turn cap only changes how they look.
 */
public final class Humanizer {
    private final BotProfile p;

    /** A click held back while the crosshair turns onto its target. */
    private static final class Held {
        float yaw, pitch;
        boolean attack, useOnce, releaseUse, exactAim;
        int hotbar = -1, attackEntity = -1, jabSlot = -1, swapBack = -1;
        int[] swapAttack;
        String useItem, technique;
        long since;
    }

    private Held held;
    private double rightTokens, leftTokens;
    /** Counts for the status line. */
    public int clicksHeld, clicksReleased;

    public Humanizer(BotProfile p) {
        this.p = p;
        this.rightTokens = 2;
        this.leftTokens = 2;
    }

    public void apply(Observation o, BotAction a) {
        if (p.humanize != 1) return;
        double perTick = p.maxClicksPerSecond / 20.0;
        rightTokens = Math.min(2, rightTokens + perTick);
        leftTokens = Math.min(2, leftTokens + perTick);

        // A held click goes first: keep turning toward it, and fire once the aim is there.
        if (held != null) {
            if (o.tick() - held.since > p.holdClickTicks + 2) {
                held = null;
            } else if (!hasAimedClick(a)) {
                a.yaw = held.yaw;
                a.pitch = held.pitch;
                if (turn(o, a) <= p.clickAimTolerance) {
                    restore(a, held);
                    held = null;
                    clicksReleased++;
                } else if (held.releaseUse) {
                    a.use = true;
                }
                spend(a);
                quantize(o, a);
                return;
            } else {
                held = null;
            }
        }

        // A click that needs the aim, with the crosshair still far from it: turn now, click when there.
        if (hasAimedClick(a)) {
            float wantYaw = a.yaw, wantPitch = a.pitch;
            double err = turn(o, a);
            if (err > p.clickAimTolerance) {
                held = hold(o, a, wantYaw, wantPitch);
                clicksHeld++;
            }
        } else {
            turn(o, a);
        }
        spend(a);
        quantize(o, a);
    }

    /** Clicks whose result depends on where the crosshair points. */
    private static boolean hasAimedClick(BotAction a) {
        return a.attack || a.useOnce || a.useItem != null || a.attackEntity >= 0 || a.exactAim
                || a.swapAttack != null || a.jabSlot >= 0;
    }

    /** Caps this tick's turn at a human flick. Returns how far the aim still is from where the action wanted it. */
    private double turn(Observation o, BotAction a) {
        double dy = Angles.wrap(a.yaw - o.yaw()), dp = a.pitch - o.pitch();
        double total = Math.hypot(dy, dp);
        if (total <= p.maxFlickPerTick) return 0;
        double k = p.maxFlickPerTick / total;
        a.yaw = (float) (o.yaw() + dy * k);
        a.pitch = (float) (o.pitch() + dp * k);
        return total - p.maxFlickPerTick;
    }

    /** Takes the clicks off this tick's action and keeps them for when the aim gets there. */
    private Held hold(Observation o, BotAction a, float yaw, float pitch) {
        Held h = new Held();
        h.yaw = yaw;
        h.pitch = pitch;
        h.since = o.tick();
        h.attack = a.attack;
        h.useOnce = a.useOnce;
        h.useItem = a.useItem;
        h.attackEntity = a.attackEntity;
        h.exactAim = a.exactAim;
        h.swapAttack = a.swapAttack;
        h.swapBack = a.swapBack;
        h.jabSlot = a.jabSlot;
        h.hotbar = a.hotbar;
        h.technique = a.technique;
        // Letting go of a drawn bow or a charged trident is the shot itself, so it keeps drawing meanwhile.
        h.releaseUse = a.exactAim && !a.use;
        a.attack = false;
        a.useOnce = false;
        a.useItem = null;
        a.attackEntity = -1;
        a.exactAim = false;
        a.swapAttack = null;
        a.jabSlot = -1;
        if (h.releaseUse) a.use = true;
        if (a.technique.isEmpty() || a.technique.equals(h.technique)) a.technique = "aiming";
        return h;
    }

    private static void restore(BotAction a, Held h) {
        a.attack |= h.attack;
        a.useOnce |= h.useOnce;
        if (h.useItem != null) a.useItem = h.useItem;
        if (h.attackEntity >= 0) a.attackEntity = h.attackEntity;
        a.exactAim |= h.exactAim;
        if (h.swapAttack != null) {
            a.swapAttack = h.swapAttack;
            a.swapBack = h.swapBack;
        }
        if (h.jabSlot >= 0) {
            a.jabSlot = h.jabSlot;
            a.swapBack = h.swapBack;
        }
        if (h.hotbar >= 0) a.hotbar = h.hotbar;
        if (h.releaseUse) a.use = false;
        a.technique = h.technique;
    }

    /** Spends click tokens. With none left, a left click waits a tick, since the style asks again anyway. */
    private void spend(BotAction a) {
        boolean right = a.useOnce || a.useItem != null || a.place != null;
        boolean left = a.attack || a.attackEntity >= 0;
        if (right) rightTokens -= 1;
        if (left) {
            if (leftTokens < 1 && a.swapAttack == null) {
                a.attack = false;
                a.attackEntity = -1;
            } else {
                leftTokens -= 1;
            }
        }
    }

    /**
     * Moves the turn onto whole mouse steps. At a given sensitivity a mouse can only turn the view in
     * multiples of one step, and a turn that is not a multiple of it does not come from a mouse.
     */
    private void quantize(Observation o, BotAction a) {
        double f = p.mouseSensitivity * 0.6 + 0.2;
        double step = f * f * f * 8 * 0.15;
        double dy = Angles.wrap(a.yaw - o.yaw()), dp = a.pitch - o.pitch();
        a.yaw = (float) (o.yaw() + Math.round(dy / step) * step);
        a.pitch = (float) Math.max(-90, Math.min(90, o.pitch() + Math.round(dp / step) * step));
    }
}
