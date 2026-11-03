package dev.reality.fightbot.core;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Persistent, dependency-free learning summary shared across rounds. The profile
 * uses an exponential moving average so recent results matter without erasing history.
 * It stores aggregate behaviour categories rather than a detailed dossier on players.
 */
public final class LearningProfile {
    private static final double EMA_ALPHA = 0.20;

    private long episodes;
    private long wins;
    private long losses;
    private long aggressiveReads;
    private long defensiveReads;
    private long mobileReads;
    private long mixedReads;
    private long unknownReads;
    private double averageCqs;
    private double averageCis;
    private double averageOverall;
    private double averageHitRate;
    private double averageDamageEfficiency;
    private double lastCqs;
    private double lastCis;
    private double lastOverall;
    private double lastPenaltyPoints;
    private String lastOpponentStyle = "UNKNOWN";

    private LearningProfile() {
    }

    public static LearningProfile load(Path directory) {
        LearningProfile profile = new LearningProfile();
        Path file = directory.resolve("profile.properties");
        if (!Files.isRegularFile(file)) return profile;
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(reader);
            profile.episodes = positive(p.getProperty("episodes"));
            profile.wins = positive(p.getProperty("wins"));
            profile.losses = positive(p.getProperty("losses"));
            profile.aggressiveReads = positive(p.getProperty("aggressiveReads"));
            profile.defensiveReads = positive(p.getProperty("defensiveReads"));
            profile.mobileReads = positive(p.getProperty("mobileReads"));
            profile.mixedReads = positive(p.getProperty("mixedReads"));
            profile.unknownReads = positive(p.getProperty("unknownReads"));
            profile.averageCqs = decimal(p.getProperty("averageCqs"));
            profile.averageCis = decimal(p.getProperty("averageCis"));
            profile.averageOverall = decimal(p.getProperty("averageOverall"));
            profile.averageHitRate = decimal(p.getProperty("averageHitRate"));
            profile.averageDamageEfficiency = decimal(p.getProperty("averageDamageEfficiency"));
            profile.lastCqs = decimal(p.getProperty("lastCqs"));
            profile.lastCis = decimal(p.getProperty("lastCis"));
            profile.lastOverall = decimal(p.getProperty("lastOverall"));
            profile.lastPenaltyPoints = decimal(p.getProperty("lastPenaltyPoints"));
            profile.lastOpponentStyle = p.getProperty("lastOpponentStyle", "UNKNOWN");
        } catch (Exception ignored) {
            // A malformed or partially written learning file must never stop the bot.
            return new LearningProfile();
        }
        return profile;
    }

    public void record(LearningScore score, String outcome) {
        if (score == null) return;
        boolean first = episodes == 0;
        episodes++;
        if ("win".equals(outcome)) wins++;
        if ("loss".equals(outcome)) losses++;

        averageCqs = ema(averageCqs, score.controlQuality(), first);
        averageCis = ema(averageCis, score.cis(), first);
        averageOverall = ema(averageOverall, score.overall(), first);
        averageHitRate = ema(averageHitRate, score.hitRate(), first);
        averageDamageEfficiency = ema(averageDamageEfficiency, score.damageEfficiency(), first);
        lastCqs = score.controlQuality();
        lastCis = score.cis();
        lastOverall = score.overall();
        lastPenaltyPoints = score.penaltyPoints();
        lastOpponentStyle = score.opponentStyle();

        switch (score.opponentStyle()) {
            case "AGGRESSIVE" -> aggressiveReads++;
            case "DEFENSIVE" -> defensiveReads++;
            case "MOBILE" -> mobileReads++;
            case "MIXED" -> mixedReads++;
            default -> unknownReads++;
        }
    }

    public void save(Path directory) throws IOException {
        Files.createDirectories(directory);
        Properties p = new Properties();
        p.setProperty("episodes", Long.toString(episodes));
        p.setProperty("wins", Long.toString(wins));
        p.setProperty("losses", Long.toString(losses));
        p.setProperty("aggressiveReads", Long.toString(aggressiveReads));
        p.setProperty("defensiveReads", Long.toString(defensiveReads));
        p.setProperty("mobileReads", Long.toString(mobileReads));
        p.setProperty("mixedReads", Long.toString(mixedReads));
        p.setProperty("unknownReads", Long.toString(unknownReads));
        p.setProperty("averageCqs", Double.toString(averageCqs));
        p.setProperty("averageCis", Double.toString(averageCis));
        p.setProperty("averageOverall", Double.toString(averageOverall));
        p.setProperty("averageHitRate", Double.toString(averageHitRate));
        p.setProperty("averageDamageEfficiency", Double.toString(averageDamageEfficiency));
        p.setProperty("lastCqs", Double.toString(lastCqs));
        p.setProperty("lastCis", Double.toString(lastCis));
        p.setProperty("lastOverall", Double.toString(lastOverall));
        p.setProperty("lastPenaltyPoints", Double.toString(lastPenaltyPoints));
        p.setProperty("lastOpponentStyle", lastOpponentStyle);

        Path target = directory.resolve("profile.properties");
        Path temporary = directory.resolve("profile.properties.tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            p.store(writer, "Fight Bot aggregate learning profile");
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void appendHistory(Path directory, String episodeId, String bot, String opponent,
                              String outcome, LearningScore score) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("history.csv");
        boolean fresh = !Files.exists(file) || Files.size(file) == 0;
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (fresh) writer.write("episode,bot,opponent,outcome,cqs,cis,overall,hit_rate_percent,damage_efficiency_percent,penalty_points,opponent_style\n");
            writer.write(score.csvRow(episodeId, bot, opponent, outcome));
            writer.write("\n");
        }
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        if (episodes == 0) {
            lines.add("Fight Bot has no completed learning records yet.");
            lines.add("Complete a fight, then run /fightbot learning again.");
            return lines;
        }
        lines.add(String.format(Locale.ROOT,
                "Learning profile: %d fights, %d wins, %d losses.", episodes, wins, losses));
        lines.add(String.format(Locale.ROOT,
                "Rolling averages: CQS %.1f/100, CIS %.1f/100, overall %.1f/100.",
                averageCqs, averageCis, averageOverall));
        lines.add(String.format(Locale.ROOT,
                "Recent fight: CQS %.1f, CIS %.1f, overall %.1f, %.1f penalty points.",
                lastCqs, lastCis, lastOverall, lastPenaltyPoints));
        lines.add(String.format(Locale.ROOT,
                "Rolling hit rate %.1f%%; damage efficiency %.1f%%.",
                averageHitRate * 100.0, averageDamageEfficiency * 100.0));
        lines.add("Opponent reads: aggressive " + aggressiveReads + ", defensive " + defensiveReads
                + ", mobile " + mobileReads + ", mixed " + mixedReads + ", insufficient data " + unknownReads + ".");
        lines.add("Last opponent pattern: " + lastOpponentStyle + ".");
        lines.add("Suggested review: " + recommendedFocus());
        lines.add("Learning is saved in fightbot/learning; these statistics do not alter combat policy automatically.");
        return lines;
    }

    private String recommendedFocus() {
        if (episodes < 3) return "collect at least three completed fights before drawing strong conclusions";
        if (averageHitRate < 0.45) return "review aim alignment and attack confirmation; many attack actions are not landing";
        if (averageDamageEfficiency < 0.45) return "review defensive positioning and damage trades; the bot is taking more damage than it deals";
        return switch (lastOpponentStyle) {
            case "AGGRESSIVE" -> "review spacing and defensive choices against close-range pressure";
            case "DEFENSIVE" -> "review decisions against opponents who block or use items frequently";
            case "MOBILE" -> "review target tracking against opponents who move or leave the ground frequently";
            case "MIXED" -> "review varied opponent behaviour and keep collecting observations";
            default -> "collect more opponent observations before changing combat decisions";
        };
    }

    private static double ema(double oldValue, double newValue, boolean first) {
        return first ? newValue : oldValue * (1.0 - EMA_ALPHA) + newValue * EMA_ALPHA;
    }

    private static long positive(String value) {
        try { return Math.max(0L, Long.parseLong(value)); }
        catch (Exception ignored) { return 0L; }
    }

    private static double decimal(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : 0.0;
        } catch (Exception ignored) { return 0.0; }
    }
}
