package dev.reality.fightbot.flight;

import dev.reality.fightbot.core.Json;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One recorded episode for one player. Observation comes in as samples. State estimation, action
 * tracking, prediction, and analysis each live in their own class. This class only feeds them in
 * order and collects the lines to log. Nothing here touches the game, so it runs in tests.
 */
public final class FlightSession {
    public final String player, label, gameVersion;
    public final MechanicsConfig config;
    public final StateEstimator estimator;
    public final ActionTracker actions;
    public final Predictor predictor;
    public final FlightAnalyzer analyzer = new FlightAnalyzer();
    private final List<String> sampleLines = new ArrayList<>();
    private final List<String> actionLines = new ArrayList<>();
    private FlightSample previous;
    private int missingRun;
    private long firstTick = Long.MIN_VALUE, lastTick;
    public String endReason;
    public final String id;

    public FlightSession(String player, String label, MechanicsConfig config) {
        this.player = player;
        this.label = label == null ? "" : label;
        this.config = config;
        this.gameVersion = config.gameVersion;
        this.estimator = new StateEstimator(config);
        this.actions = new ActionTracker(config);
        this.predictor = new Predictor(config);
        this.id = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + "_" + player
                + (this.label.isEmpty() ? "" : "_" + this.label.replaceAll("[^A-Za-z0-9_-]", ""));
    }

    public boolean ended() {
        return endReason != null;
    }

    /** The bot sent an action this tick, before this tick's sample is taken. */
    public void commanded(String action, long tick) {
        if (ended()) return;
        actions.commanded(action, tick, estimator.state(), previous);
    }

    /**
     * Feeds one sample. Returns false once the episode has ended, for example after the player has been
     * missing longer than the disconnect limit.
     */
    public boolean add(FlightSample s) {
        if (ended()) return false;
        if (s == null) s = FlightSample.missing(lastTick + 1, System.currentTimeMillis());
        if (firstTick == Long.MIN_VALUE) firstTick = s.tick();
        lastTick = s.tick();
        missingRun = s.present() ? 0 : missingRun + 1;

        MovementState before = estimator.state();
        MovementState state = estimator.update(s);
        String interruption = estimator.interruptionSince(s.tick() - config.confirmWindow);
        for (ActionTracker.ActionRecord r : actions.update(s, state, interruption)) {
            analyzer.action(r);
            actionLines.add(r.toJson());
        }
        // The prediction from the previous sample is checked against this one, under the state it was made in.
        if (previous != null) {
            Predictor.Prediction p = predictor.predict(previous, s, actions.rocketStatus(s.tick()));
            analyzer.predictionError(before, p, s);
        }
        analyzer.sample(s, state);
        sampleLines.add(s.toJson(state));
        if (s.present()) previous = s;

        if (missingRun > config.disconnectTicks) {
            end("disconnect");
            return false;
        }
        return true;
    }

    public void end(String reason) {
        if (ended()) return;
        endReason = reason;
        for (ActionTracker.ActionRecord r : actions.closeAll(reason, estimator.state())) {
            analyzer.action(r);
            actionLines.add(r.toJson());
        }
    }

    public List<String> sampleLines() {
        return sampleLines;
    }

    public List<String> actionLines() {
        return actionLines;
    }

    public String summaryJson() {
        StringBuilder t = new StringBuilder("[");
        for (StateEstimator.Transition tr : estimator.transitions()) {
            if (t.length() > 1) t.append(',');
            t.append(new Json().put("tick", tr.tick()).put("from", tr.from().name()).put("to", tr.to().name()).put("reason", tr.reason()).build());
        }
        t.append(']');
        return new Json().put("episode", id).put("player", player).put("label", label).put("gameVersion", gameVersion)
                .put("configVerified", config.verified).put("startTick", firstTick).put("endTick", lastTick)
                .put("endReason", endReason).raw("config", Json.quoteAll(config.lines())).raw("analysis", analyzer.summaryJson())
                .raw("transitions", t.toString()).build();
    }

    /** Writes samples, actions, and the summary into a folder named after the episode. */
    public Path save(Path root) throws IOException {
        Path dir = root.resolve(id);
        Files.createDirectories(dir);
        write(dir.resolve("samples.jsonl"), sampleLines);
        write(dir.resolve("actions.jsonl"), actionLines);
        write(dir.resolve("summary.json"), List.of(summaryJson()));
        return dir;
    }

    private static void write(Path file, List<String> lines) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (String l : lines) w.write(l + "\n");
        }
    }

    public String shortReport() {
        return String.format("%s: %d ticks recorded, %d glides, %s.", player, lastTick - firstTick + 1, analyzer.glides(),
                endReason == null ? "still running" : "ended by " + endReason);
    }
}
