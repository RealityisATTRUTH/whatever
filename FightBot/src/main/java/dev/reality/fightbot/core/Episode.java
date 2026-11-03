package dev.reality.fightbot.core;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One bot fight from start to finish. It keeps every tick of what the bot saw and did, and on the
 * last tick writes training records in the same layout as the fight recorder, plus one results row.
 */
public final class Episode {
    private record Step(Observation o, BotAction a) {
    }

    public final String id;
    public final String bot, opponent, profile, kit;
    private final List<Step> steps = new ArrayList<>();
    private final Map<String, Long> techniques = new TreeMap<>();
    private long opponentSamples;
    private long closeRangeSamples, midRangeSamples, farRangeSamples;
    private long opponentBlockingSamples, opponentUsingSamples, opponentSprintingSamples;
    private long opponentAirborneSamples, opponentMovingSamples, opponentFacingSamples;
    private long aimSamples, conflictingInputs;
    private double aimErrorTotal;
    private double initialEffectiveHealth = Double.NaN;
    public String outcome = "unfinished";

    public Episode(String bot, String opponent, String profile, String kit) {
        this.bot = bot;
        this.opponent = opponent;
        this.profile = profile;
        this.kit = kit;
        this.id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + "_" + bot + "_vs_" + opponent;
    }

    public void add(Observation o, BotAction a) {
        if (!o.hasOpponent()) return;
        steps.add(new Step(o, a));
        if (!a.technique.isEmpty()) techniques.merge(a.technique, 1L, Long::sum);
        if (!Double.isFinite(initialEffectiveHealth)) initialEffectiveHealth = o.effectiveHealth();
        observeOpponent(o);

        if (a.attack || a.swapAttack != null || a.jabSlot >= 0) {
            aimErrorTotal += Angles.diff(a.yaw, o.yawToOpponent());
            aimSamples++;
        }
        if ((a.attack && (a.use || a.block)) || (a.use && a.block)
                || (a.sprint && (a.use || a.block))
                || Math.abs(a.forward) > 1 || Math.abs(a.strafe) > 1) {
            conflictingInputs++;
        }
    }

    private void observeOpponent(Observation o) {
        opponentSamples++;
        if (o.distance() <= 3.5) closeRangeSamples++;
        else if (o.distance() <= 7.0) midRangeSamples++;
        else farRangeSamples++;
        if (o.oBlocking()) opponentBlockingSamples++;
        if (o.oUsingItem()) opponentUsingSamples++;
        if (o.oSprinting()) opponentSprintingSamples++;
        if (!o.oOnGround()) opponentAirborneSamples++;
        if (Math.hypot(o.ovx(), o.ovz()) > 0.18 || Math.abs(o.ovy()) > 0.12) opponentMovingSamples++;
        if (o.oFacingError() <= 30.0) opponentFacingSamples++;
    }

    public double averageAimError() {
        return aimSamples == 0 ? Double.NaN : aimErrorTotal / aimSamples;
    }

    public long aimSamples() { return aimSamples; }

    public long conflictingInputs() { return conflictingInputs; }

    public double initialEffectiveHealth() {
        return Double.isFinite(initialEffectiveHealth) ? initialEffectiveHealth : 20.0;
    }

    /** Fraction of the episode with enough varied observations to form an opponent read. */
    public double opponentReadCoverage() {
        if (opponentSamples == 0) return 0.0;
        long[] signals = {closeRangeSamples, midRangeSamples, farRangeSamples,
                opponentBlockingSamples, opponentUsingSamples, opponentSprintingSamples,
                opponentAirborneSamples, opponentMovingSamples, opponentFacingSamples};
        int observed = 0;
        long threshold = Math.max(2L, (long) Math.ceil(opponentSamples * 0.05));
        for (long signal : signals) if (signal >= threshold) observed++;
        double sampleCoverage = Math.min(1.0, opponentSamples / 40.0);
        double signalCoverage = Math.min(1.0, observed / 4.0);
        return sampleCoverage * signalCoverage;
    }

    /** Coarse aggregate pattern label; UNKNOWN means too few samples to draw a conclusion. */
    public String opponentStyle() {
        if (opponentSamples < 5) return "UNKNOWN";
        double n = opponentSamples;
        double close = closeRangeSamples / n;
        double sprint = opponentSprintingSamples / n;
        double facing = opponentFacingSamples / n;
        double blocking = opponentBlockingSamples / n;
        double using = opponentUsingSamples / n;
        double moving = opponentMovingSamples / n;
        double airborne = opponentAirborneSamples / n;
        double aggressive = close * 0.45 + sprint * 0.35 + facing * 0.20;
        double defensive = blocking * 0.60 + using * 0.40;
        double mobile = moving * 0.65 + airborne * 0.35;
        double best = Math.max(aggressive, Math.max(defensive, mobile));
        if (best < 0.22) return "MIXED";
        if (best == aggressive) return "AGGRESSIVE";
        if (best == defensive) return "DEFENSIVE";
        return "MOBILE";
    }

    public int ticks() {
        return steps.size();
    }

    /** Health plus absorption lost by the opponent over the fight. Health gained back is ignored. */
    public double damageDealt() {
        double sum = 0;
        for (int i = 1; i < steps.size(); i++) {
            double drop = steps.get(i - 1).o.oEffectiveHealth() - steps.get(i).o.oEffectiveHealth();
            if (drop > 0) sum += drop;
        }
        return sum;
    }

    public double damageTaken() {
        double sum = 0;
        for (int i = 1; i < steps.size(); i++) {
            double drop = steps.get(i - 1).o.effectiveHealth() - steps.get(i).o.effectiveHealth();
            if (drop > 0) sum += drop;
        }
        return sum;
    }

    public long attacks() {
        return steps.stream().filter(s -> isAttackAction(s.a)).count();
    }

    private static boolean isAttackAction(BotAction action) {
        return action.attack || action.swapAttack != null || action.jabSlot >= 0;
    }

    /** Attack actions followed by an opponent health drop within 3 ticks. */
    public long landed() {
        long landed = 0;
        boolean[] countedHealthDrops = new boolean[steps.size()];
        for (int i = 0; i < steps.size(); i++) {
            if (!isAttackAction(steps.get(i).a)) continue;
            for (int j = i + 1; j <= i + 3 && j < steps.size(); j++) {
                if (countedHealthDrops[j]) continue;
                double before = steps.get(j - 1).o.oEffectiveHealth();
                double after = steps.get(j).o.oEffectiveHealth();
                if (after < before - 0.01) {
                    countedHealthDrops[j] = true;
                    landed++;
                    break;
                }
            }
        }
        return landed;
    }

    public Map<String, Long> techniques() {
        return techniques;
    }

    /** Training records every 2 ticks, matching the fight recorder's state, action, result layout. */
    public List<String> trainingRecords() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < steps.size(); i += 2) {
            Step s = steps.get(i);
            Observation o = s.o;
            boolean hit = false, got = false;
            for (int j = i + 1; j <= i + 10 && j < steps.size(); j++) {
                if (steps.get(j).o.oEffectiveHealth() < steps.get(j - 1).o.oEffectiveHealth() - 0.01) hit = true;
                if (steps.get(j).o.effectiveHealth() < steps.get(j - 1).o.effectiveHealth() - 0.01) got = true;
            }
            Step later = i + 20 < steps.size() ? steps.get(i + 20) : null;
            boolean attack = isAttackAction(s.a) || (i + 1 < steps.size() && isAttackAction(steps.get(i + 1).a));
            Json state = new Json()
                    .put("distance", o.distance()).put("heightDiff", o.oy() - o.y())
                    .put("ownHp", o.effectiveHealth()).put("oppHp", o.oEffectiveHealth())
                    .put("weapon", o.mainHand()).put("oppWeapon", o.oMainHand())
                    .put("attackCharge", o.charge())
                    .put("airborne", !o.onGround()).put("oppAirborne", !o.oOnGround())
                    .put("sprinting", o.sprinting()).put("oppSprinting", o.oSprinting())
                    .put("oppBlocking", o.oBlocking()).put("elytra", false)
                    .put("facingErrorDeg", o.facingError()).put("oppFacingErrorDeg", o.oFacingError())
                    .put("speed", Math.hypot(o.vx(), o.vz()) * 20);
            Json action = new Json().put("move", s.a.moveName()).put("jump", s.a.jump).put("attack", attack)
                    .put("sneak", s.a.sneak).put("swapTo", s.a.hotbar >= 0 ? "slot " + (s.a.hotbar + 1) : null)
                    .put("use", s.a.use ? "eat" : null)
                    .put("botState", s.a.state).put("technique", s.a.technique.isEmpty() ? null : s.a.technique);
            Json result = new Json().put("hitWithin10Ticks", hit).put("gotHitWithin10Ticks", got)
                    .put("ownHpChange1s", later == null ? Double.NaN : later.o.effectiveHealth() - o.effectiveHealth())
                    .put("oppHpChange1s", later == null ? Double.NaN : later.o.oEffectiveHealth() - o.oEffectiveHealth());
            out.add(new Json().put("episode", id).put("kit", kit).put("tick", i).put("player", bot).put("opponent", opponent)
                    .put("mode", "bot duel").put("profile", profile)
                    .raw("state", state.build()).raw("action", action.build()).raw("result", result.build()).build());
        }
        return out;
    }

    public String summaryLine() {
        return "Bot " + bot + " " + outcome + " against " + opponent + " in " + String.format(java.util.Locale.ROOT, "%.1f", ticks() / 20.0)
                + " s: dealt " + String.format(java.util.Locale.ROOT, "%.1f", damageDealt()) + ", took "
                + String.format(java.util.Locale.ROOT, "%.1f", damageTaken()) + ", landed " + landed() + " of " + attacks() + " attacks";
    }

    /** Writes the training file and adds a row to results.csv. */
    public void save(Path root, ActionValidator v) throws IOException {
        Path dir = root.resolve("episodes");
        Files.createDirectories(dir);
        Files.write(dir.resolve(id + ".training.jsonl"), trainingRecords(), StandardCharsets.UTF_8);
        Path csv = root.resolve("results.csv");
        boolean fresh = !Files.exists(csv);
        try (Writer w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (fresh) w.write("episode,bot,profile,kit,opponent,outcome,seconds,damage dealt,damage taken,swings,landed,"
                    + "rejected attacks,rejected uses,stale ticks,techniques\n");
            StringBuilder tech = new StringBuilder();
            for (Map.Entry<String, Long> e : techniques.entrySet()) tech.append(e.getKey()).append(' ').append(e.getValue()).append("; ");
            w.write(String.join(",", id, bot, profile, kit, opponent, outcome,
                    String.format(java.util.Locale.ROOT, "%.1f", ticks() / 20.0),
                    String.format(java.util.Locale.ROOT, "%.1f", damageDealt()),
                    String.format(java.util.Locale.ROOT, "%.1f", damageTaken()),
                    String.valueOf(attacks()), String.valueOf(landed()),
                    String.valueOf(v.rejectedAttacks), String.valueOf(v.rejectedUses), String.valueOf(v.staleTicks),
                    "\"" + tech + "\"") + "\n");
        }
    }
}
