package dev.reality.fightbot;

import java.io.InputStream;
import java.util.Properties;

/**
 * Which build of Fight Bot this is. Both builds come from the same code:
 *
 *   learning build  (fightbot-VERSION-learning.jar) scores every round, saves the scores and
 *                   opponent reads in fightbot/learning/, and has /fightbot learning.
 *   plain build     (fightbot-VERSION.jar) fights exactly the same but keeps no learning data,
 *                   and has no /fightbot learning.
 *
 * The build sets this in fightbot-build.properties: gradlew build makes both jars.
 */
public final class Build {

    public static final boolean LEARNING = read();

    private Build() {}

    private static boolean read() {
        try (InputStream in = Build.class.getResourceAsStream("/fightbot-build.properties")) {
            if (in == null) return true;
            Properties props = new Properties();
            props.load(in);
            return Boolean.parseBoolean(props.getProperty("learning", "true"));
        } catch (Exception e) {
            return true;
        }
    }
}
