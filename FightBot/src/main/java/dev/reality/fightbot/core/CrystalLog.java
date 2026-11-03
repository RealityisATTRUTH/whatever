package dev.reality.fightbot.core;

import dev.reality.fightbot.core.place.Placer;

import java.util.ArrayList;
import java.util.List;

/**
 * One line per crystal or anchor action: tick, action, item, target position and health, own health,
 * the predicted damage both ways, then what really happened. Placement results come from the placer's
 * check of the world. Blast results are measured from both players' health over the next few ticks.
 * Each line also names the action that came next.
 */
public final class CrystalLog {
    private static final int MEASURE_TICKS = 3;

    private static final class Line {
        long tick;
        String action, item;
        double tx, ty, tz, targetHealth, selfHealth, predictedTarget, predictedSelf;
        String placement = null;
        double dealt = Double.NaN, taken = Double.NaN;
        String next = null;
        boolean blast;
        long measureUntil;
    }

    private final List<Line> open = new ArrayList<>();
    private final List<Line> done = new ArrayList<>();
    private Line last;
    private int breaks, placements, failedPlacements;
    private double totalDealt, totalTaken;

    public void action(Observation o, String action, String item, double predictedTarget, double predictedSelf) {
        Line l = new Line();
        l.tick = o.tick();
        l.action = action;
        l.item = item;
        l.tx = o.ox();
        l.ty = o.oy();
        l.tz = o.oz();
        l.targetHealth = o.oEffectiveHealth();
        l.selfHealth = o.effectiveHealth();
        l.predictedTarget = predictedTarget;
        l.predictedSelf = predictedSelf;
        l.blast = action.equals("break") || action.equals("detonate");
        l.measureUntil = o.tick() + MEASURE_TICKS;
        if (action.equals("break")) breaks++;
        if (last != null && last.next == null) last.next = action;
        last = l;
        open.add(l);
        if (!l.blast && !isClick(action)) close(l);
    }

    private static boolean isClick(String action) {
        return switch (action) {
            case "obsidian", "crystal", "anchor", "charge", "detonate" -> true;
            default -> false;
        };
    }

    /** The placer's verdict on the last click. */
    public void placementResult(Observation o, Placer.Result r) {
        placements++;
        if (!r.ok()) failedPlacements++;
        for (Line l : open) {
            if (l.placement == null && isClick(l.action) && l.tick == r.request().tick()) {
                l.placement = r.ok() ? "confirmed" : "failed: " + r.reason();
                if (!l.blast) close(l);
                break;
            }
        }
    }

    /** Measures blast results from health, once the measuring window is over. */
    public void measure(Observation o) {
        for (Line l : new ArrayList<>(open)) {
            if (!l.blast || o.tick() < l.measureUntil) continue;
            l.dealt = Math.max(0, l.targetHealth - o.oEffectiveHealth());
            l.taken = Math.max(0, l.selfHealth - o.effectiveHealth());
            totalDealt += l.dealt;
            totalTaken += l.taken;
            close(l);
        }
    }

    private void close(Line l) {
        open.remove(l);
        done.add(l);
    }

    private static String json(Line l) {
        return new Json().put("tick", l.tick).put("action", l.action).put("activeItem", l.item)
                .put("targetX", l.tx, 2).put("targetY", l.ty, 2).put("targetZ", l.tz, 2)
                .put("targetHealth", l.targetHealth, 2).put("selfHealth", l.selfHealth, 2)
                .put("predictedTargetDamage", l.predictedTarget, 2).put("predictedSelfDamage", l.predictedSelf, 2)
                .put("placementResult", l.placement).put("damageDealt", l.dealt, 2).put("damageTaken", l.taken, 2)
                .put("nextAction", l.next).build();
    }

    /** Finished lines as JSON. Lines still waiting on a result are added when flushed. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (Line l : done) out.add(json(l));
        return out;
    }

    public List<String> flush() {
        for (Line l : new ArrayList<>(open)) close(l);
        return lines();
    }

    public int breaks() {
        return breaks;
    }

    public int placements() {
        return placements;
    }

    public int failedPlacements() {
        return failedPlacements;
    }

    public double totalDealt() {
        return totalDealt;
    }

    public double totalTaken() {
        return totalTaken;
    }
}
