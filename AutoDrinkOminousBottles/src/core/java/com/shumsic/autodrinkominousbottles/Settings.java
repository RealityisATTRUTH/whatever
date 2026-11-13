package com.shumsic.autodrinkominousbottles;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/** Remembers whether the mod is on, in config/auto_drink_ominous_bottles.properties. */
public final class Settings {

    private final File file;
    private boolean enabled;

    public Settings(File configDir) {
        this.file = new File(configDir, "auto_drink_ominous_bottles.properties");
        Properties p = new Properties();
        if (file.isFile()) {
            try (FileReader r = new FileReader(file)) {
                p.load(r);
            } catch (IOException ignored) {
                // A broken file just means the default.
            }
        }
        enabled = Boolean.parseBoolean(p.getProperty("enabled", "false"));
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(on));
        file.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(file)) {
            p.store(w, "Auto-Drink Ominous Bottles");
        } catch (IOException ignored) {
            // Not being able to save is not worth crashing over.
        }
    }
}
