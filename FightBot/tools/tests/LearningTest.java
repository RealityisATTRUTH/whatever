import dev.reality.fightbot.core.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Deterministic checks for the post-fight scoring and persistent learning summary. */
public final class LearningTest {
    private static int failures;

    private static void check(boolean condition, String name) {
        System.out.println((condition ? "PASS " : "FAIL ") + name);
        if (!condition) failures++;
    }

    private static Observation observation(long tick, float opponentHealth) {
        return new Observation(tick, true,
                0, 64, 0, 0, 0, 0, 0, 0, true, false, false,
                20, 0, "netherite_sword", 1.0, 0, -1, 0,
                0, 64, 2.5, 0, 0, 0, 180, true, true, false, false,
                opponentHealth, 0, "netherite_sword", true, 0);
    }

    private static Episode sampleEpisode() {
        Episode episode = new Episode("Learner", "PracticeTarget", "default", "sword");
        for (int tick = 0; tick < 40; tick++) {
            float opponentHealth = tick < 5 ? 20 : 15;
            Observation o = observation(tick, opponentHealth);
            BotAction action = BotAction.idle(0, 0);
            if (tick == 3) {
                action.attack = true;
                action.technique = "test_hit";
            }
            episode.add(o, action);
        }
        episode.outcome = "win";
        return episode;
    }

    public static void main(String[] args) throws Exception {
        Episode cleanEpisode = sampleEpisode();
        ActionValidator cleanValidator = new ActionValidator(new BotProfile());
        LearningScore clean = LearningScore.calculate(cleanEpisode, cleanValidator);

        check(clean.hitRate() > 0.99, "confirmed damage produces a full hit-rate score");
        check(clean.opponentStyle().equals("AGGRESSIVE"), "close-range sprinting opponent is classified as aggressive");
        check(clean.overall() > 70, "successful clean episode earns a strong overall score");
        check(clean.controlQuality() <= 100 && clean.cis() <= 100, "scores stay within the 0-100 range");

        Episode jabEpisode = new Episode("Learner", "PracticeTarget", "default", "sword");
        for (int tick = 0; tick < 8; tick++) {
            Observation o = observation(tick, tick < 3 ? 20 : 16);
            BotAction action = BotAction.idle(0, 0);
            if (tick == 2) {
                action.jabSlot = 2;
                action.swapBack = 1;
                action.technique = "test_lunge_jab";
            }
            jabEpisode.add(o, action);
        }
        check(jabEpisode.attacks() == 1 && jabEpisode.landed() == 1,
                "learning metrics count Lunge jabs and confirm their damage");

        ActionValidator penalizedValidator = new ActionValidator(new BotProfile());
        penalizedValidator.rejectedAttacks = 2;
        penalizedValidator.rejectedUses = 1;
        penalizedValidator.staleTicks = 2;
        LearningScore penalized = LearningScore.calculate(cleanEpisode, penalizedValidator);
        check(penalized.controlQuality() < clean.controlQuality(), "rejected and stale actions reduce control score");
        check(penalized.penaltyPoints() > clean.penaltyPoints(), "bad actions add explicit penalty points");

        Episode lossEpisode = sampleEpisode();
        lossEpisode.outcome = "loss";
        LearningScore loss = LearningScore.calculate(lossEpisode, cleanValidator);
        check(loss.cis() < clean.cis(), "losing reduces combat intelligence score");

        Path dir = Files.createTempDirectory("fightbot-learning-test");
        LearningProfile profile = LearningProfile.load(dir);
        profile.record(clean, "win");
        profile.save(dir);
        profile.appendHistory(dir, "episode-1", "Learner", "PracticeTarget", "win", clean);

        LearningProfile loaded = LearningProfile.load(dir);
        List<String> report = loaded.describe();
        check(report.stream().anyMatch(line -> line.contains("1 fights, 1 wins, 0 losses")),
                "aggregate profile saves and reloads");
        check(Files.readAllLines(dir.resolve("history.csv")).size() == 2,
                "per-fight learning history is appended");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures > 0) System.exit(1);
    }
}
