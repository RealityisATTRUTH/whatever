package dev.reality.fightbot.core;

import java.util.Locale;

/**
 * A transparent post-fight score. It measures valid input, observation quality,
 * confirmed results, resource discipline, and the amount of opponent behaviour
 * successfully observed. It is not an anti-cheat or human-impersonation score.
 */
public record LearningScore(
        double controlQuality,
        double cis,
        double overall,
        double hitRate,
        double damageEfficiency,
        double validationQuality,
        double observationFreshness,
        double aimQuality,
        double opponentReadCoverage,
        double penaltyPoints,
        long missedAttacks,
        String opponentStyle
) {
    public LearningScore {
        controlQuality = clamp100(controlQuality);
        cis = clamp100(cis);
        overall = clamp100(overall);
        hitRate = clamp01(hitRate);
        damageEfficiency = clamp01(damageEfficiency);
        validationQuality = clamp01(validationQuality);
        observationFreshness = clamp01(observationFreshness);
        aimQuality = clamp01(aimQuality);
        opponentReadCoverage = clamp01(opponentReadCoverage);
        penaltyPoints = Math.max(0.0, finiteOrZero(penaltyPoints));
        missedAttacks = Math.max(0L, missedAttacks);
        opponentStyle = opponentStyle == null ? "UNKNOWN" : opponentStyle;
    }

    /** Build the score from a completed episode and the validator counters for that round. */
    public static LearningScore calculate(Episode episode, ActionValidator validator) {
        if (episode == null) throw new NullPointerException("episode");
        if (validator == null) throw new NullPointerException("validator");

        double ticks = Math.max(1, episode.ticks());
        long attacks = episode.attacks();
        long landed = episode.landed();
        long missed = Math.max(0, attacks - landed);
        double hitRate = attacks == 0 ? 0.0 : clamp01((double) landed / attacks);

        double dealt = Math.max(0.0, episode.damageDealt());
        double taken = Math.max(0.0, episode.damageTaken());
        double damageEfficiency = dealt + taken <= 1.0e-9 ? 0.5 : clamp01(dealt / (dealt + taken));

        double rejectedUnits = validator.rejectedAttacks * 2.0
                + validator.rejectedUses
                + validator.rejectedBlocks
                + validator.rejectedJumps * 0.5;
        double validationQuality = 1.0 - clamp01(rejectedUnits / Math.max(1.0, ticks * 0.10));
        double observationFreshness = 1.0 - clamp01((double) validator.staleTicks / ticks * 5.0);
        double averageAimError = episode.averageAimError();
        double aimQuality = episode.aimSamples() == 0
                ? 0.5
                : 1.0 - clamp01(averageAimError / 90.0);
        double inputCoherence = 1.0 - clamp01((double) episode.conflictingInputs() / Math.max(1.0, ticks * 0.10));

        // Control Quality Score measures valid, deliberate inputs rather than impersonation.
        double controlQuality = 35.0 * validationQuality
                + 20.0 * observationFreshness
                + 25.0 * aimQuality
                + 20.0 * inputCoherence;

        double outcomeQuality = switch (episode.outcome) {
            case "win" -> 1.0;
            case "loss" -> 0.0;
            default -> 0.5;
        };
        double initialHealth = Math.max(1.0, episode.initialEffectiveHealth());
        double survivalQuality = 1.0 - clamp01(taken / initialHealth);
        double resourceQuality = 1.0 - clamp01(validator.rejectedUses / Math.max(1.0, ticks * 0.03));
        double opponentReadCoverage = episode.opponentReadCoverage();

        double cis = 25.0 * hitRate
                + 20.0 * damageEfficiency
                + 20.0 * outcomeQuality
                + 15.0 * opponentReadCoverage
                + 10.0 * survivalQuality
                + 10.0 * resourceQuality;
        double overall = 0.40 * controlQuality + 0.60 * cis;

        double penalties = validator.rejectedAttacks * 2.0
                + validator.rejectedUses
                + validator.rejectedBlocks
                + validator.rejectedJumps * 0.5
                + validator.staleTicks * 0.25
                + missed;

        return new LearningScore(controlQuality, cis, overall, hitRate, damageEfficiency,
                validationQuality, observationFreshness, aimQuality, opponentReadCoverage,
                penalties, missed, episode.opponentStyle());
    }

    public String grade() {
        if (overall >= 90) return "A";
        if (overall >= 80) return "B";
        if (overall >= 70) return "C";
        if (overall >= 60) return "D";
        return "E";
    }

    public String summary(String botName) {
        return String.format(Locale.ROOT,
                "%s learning score: CQS %.1f/100, CIS %.1f/100, overall %.1f/100 (grade %s). "
                        + "Hit rate %.0f%%, opponent read %s, penalties %.1f points.",
                botName, controlQuality, cis, overall, grade(), hitRate * 100.0, opponentStyle, penaltyPoints);
    }

    public String csvRow(String episodeId, String bot, String opponent, String outcome) {
        return String.join(",", csv(episodeId), csv(bot), csv(opponent), csv(outcome),
                number(controlQuality), number(cis), number(overall), number(hitRate * 100.0),
                number(damageEfficiency * 100.0), number(penaltyPoints), csv(opponentStyle));
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clamp100(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}
