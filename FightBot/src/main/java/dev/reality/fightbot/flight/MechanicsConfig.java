package dev.reality.fightbot.flight;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Every game number the flight module depends on, kept in one file per game version so a new version
 * can be checked and corrected without touching code. The defaults are vanilla's movement rules as the
 * author understood them. They are marked unverified until someone measures them in that version and
 * sets verified to true. Prediction errors in the flight summaries show which numbers are wrong.
 */
public final class MechanicsConfig {
    public String gameVersion = "unknown";
    /** True only after the numbers below were measured in this game version. */
    public boolean verified = false;
    public String note = "Defaults from vanilla movement code, not yet measured in this version.";

    // Falling without an elytra, per tick.
    public double gravity = 0.08;
    public double verticalDrag = 0.98;
    public double airDrag = 0.91;

    // Elytra gliding, per tick.
    public double glideLift = 0.75;
    public double glideDiveTransfer = 0.1;
    public double glideClimbTransfer = 0.04;
    public double glideClimbLift = 3.2;
    public double glideTurnBlend = 0.1;
    public double glideDragHorizontal = 0.99;
    public double glideDragVertical = 0.98;
    /** Gliding keeps the fall distance at 1 while falling slower than this. */
    public double glideSafeFallSpeed = -0.5;

    // Firework boost while gliding.
    public double fireworkPush = 0.1;
    public double fireworkTargetSpeed = 1.5;
    public double fireworkBlend = 0.5;
    /** Shortest and longest a flight duration 1 rocket pushes, in ticks. The burn time is random in between. */
    public int fireworkMinTicks = 20;
    public int fireworkMaxTicks = 31;

    // Thresholds the estimator and action tracker use.
    /** Falling faster than this, in blocks per tick, counts as descending. */
    public double descendingVy = -0.15;
    /** Ticks after touching down that count as landing. */
    public int landingTicks = 3;
    /** Ticks of recovery after a crash, a hard landing, or a hit. */
    public int recoveryTicks = 10;
    /** Air ticks needed before a touchdown counts as a landing rather than a step. */
    public int minAirTicksForLanding = 3;
    /** Speed lost in one tick that, with a collision flag, counts as a crash. */
    public double crashSpeedLoss = 0.3;
    /** Speed gained within the window that confirms a firework fired. */
    public double fireworkConfirmGain = 0.15;
    /** Upward speed gained within the window that confirms a wind charge launch. */
    public double windConfirmVy = 0.3;
    /** Ticks after an action to look for its effect. */
    public int confirmWindow = 4;
    /** Player missing longer than this ends the episode as a disconnect. */
    public int disconnectTicks = 40;
    /** How far down to look for the ground under the player. */
    public int groundScan = 64;

    public static MechanicsConfig load(Path dir, String version) {
        MechanicsConfig c = new MechanicsConfig();
        c.gameVersion = version;
        Path file = dir.resolve(version + ".properties");
        if (Files.exists(file)) {
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(file)) {
                props.load(r);
            } catch (IOException e) {
                return c;
            }
            for (Field f : MechanicsConfig.class.getFields()) {
                String v = props.getProperty(f.getName());
                if (v == null || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    if (f.getType() == double.class) f.setDouble(c, Double.parseDouble(v));
                    else if (f.getType() == int.class) f.setInt(c, Integer.parseInt(v));
                    else if (f.getType() == boolean.class) f.setBoolean(c, Boolean.parseBoolean(v));
                    else if (f.getType() == String.class) f.set(c, v);
                } catch (Exception ignored) {
                }
            }
            c.gameVersion = version;
        } else {
            try {
                Files.createDirectories(dir);
                c.save(file);
            } catch (IOException ignored) {
            }
        }
        return c;
    }

    public void save(Path file) throws IOException {
        try (Writer w = Files.newBufferedWriter(file)) {
            w.write("# Flight mechanics for Minecraft " + gameVersion + ". Set verified=true only after measuring.\n");
            for (String line : lines()) w.write(line + "\n");
        }
    }

    /** name=value lines, for the file and for dataset headers. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        for (Field f : MechanicsConfig.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                out.add(f.getName() + "=" + f.get(this));
            } catch (IllegalAccessException ignored) {
            }
        }
        return out;
    }
}
