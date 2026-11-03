package com.reality.mobcraft.rts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A player's armies, made by the player, the way HOI4 does it: select some
 * mobs, press New Army, and they become "Army 1". No pre-made slots.
 *
 *   A mob is in at most one army. Making a new army out of mobs takes them
 *   out of their old one, and an army left with no mobs goes away.
 *   Each army keeps its number and color until it's disbanded, and new armies
 *   take the lowest free number.
 *
 * Each army has a stance, like HOI4: Normal, or Aggressive (moves faster and
 * fights anything hostile it meets, see RtsStances).
 *
 * Saved on the server as text lines, "A|<number>|uuid,uuid|aggressive", and
 * synced back to the owner. Lines from the old divisions and army groups are
 * ignored.
 */
public final class RtsGroups {

    public static final int MAX_ARMIES = 12;

    /** Each army's color, by number. */
    public static final int[] COLORS = {
            0x55FFFF, 0xFFAA00, 0xFF55FF, 0x55FF55, 0xFF5555, 0x5555FF,
            0xFFFF55, 0xAAAAAA, 0x00AAAA, 0xAA00AA, 0xFF8080, 0x80FF80};

    public static final class Army {
        public final int number;
        public final Set<String> mobs = new LinkedHashSet<>();
        public boolean aggressive;

        Army(int number) {
            this.number = number;
        }

        public String name() {
            return "Army " + number;
        }

        public int color() {
            return COLORS[(number - 1) % COLORS.length];
        }
    }

    private final List<Army> armies = new ArrayList<>();

    /** Armies in number order. */
    public List<Army> armies() {
        return armies;
    }

    public Army army(int number) {
        for (Army army : armies) if (army.number == number) return army;
        return null;
    }

    /** The army this mob is in, or null. */
    public Army armyOf(String mobUuid) {
        for (Army army : armies) if (army.mobs.contains(mobUuid)) return army;
        return null;
    }

    /** Makes these mobs a new army. Returns it, or null if there are none or too many armies. */
    public Army create(Set<String> mobUuids) {
        if (mobUuids.isEmpty()) return null;
        for (Army army : armies) army.mobs.removeAll(mobUuids);
        armies.removeIf(army -> army.mobs.isEmpty());
        if (armies.size() >= MAX_ARMIES) return null;
        int number = 1;
        while (army(number) != null) number++;
        Army army = new Army(number);
        army.mobs.addAll(mobUuids);
        armies.add(army);
        armies.sort((a, b) -> Integer.compare(a.number, b.number));
        return army;
    }

    /** Puts these mobs in an existing army, taking them out of any other. */
    public void addTo(int number, Set<String> mobUuids) {
        Army target = army(number);
        if (target == null) return;
        for (Army army : armies) if (army != target) army.mobs.removeAll(mobUuids);
        target.mobs.addAll(mobUuids);
        armies.removeIf(army -> army.mobs.isEmpty());
    }

    /** The army goes away. Its mobs stay yours, just not in an army. */
    public void disband(int number) {
        armies.removeIf(army -> army.number == number);
    }

    // --- Saving ---------------------------------------------------------------

    public List<String> toLines() {
        List<String> lines = new ArrayList<>();
        for (Army army : armies) {
            lines.add("A|" + army.number + "|" + String.join(",", army.mobs) + "|" + (army.aggressive ? "aggressive" : "normal"));
        }
        return lines;
    }

    /** Reads saved lines, skipping anything malformed or from the old format. */
    public static RtsGroups fromLines(List<String> lines) {
        RtsGroups groups = new RtsGroups();
        if (lines == null) return groups;
        for (String line : lines) {
            String[] parts = line.split("\\|", 4);
            if (parts.length < 3 || !parts[0].equals("A")) continue;
            int number;
            try {
                number = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (number < 1 || number > 99 || groups.army(number) != null) continue;
            Army army = new Army(number);
            army.aggressive = parts.length == 4 && parts[3].trim().equals("aggressive");
            for (String uuid : parts[2].split(",")) if (!uuid.isBlank()) army.mobs.add(uuid.trim());
            if (!army.mobs.isEmpty() && groups.armies.size() < MAX_ARMIES) groups.armies.add(army);
        }
        groups.armies.sort((a, b) -> Integer.compare(a.number, b.number));
        return groups;
    }

    /** Server-side sanity limit on what a client can store. */
    public static boolean isReasonable(List<String> lines) {
        if (lines.size() > MAX_ARMIES) return false;
        for (String line : lines) if (line.length() > 40 * 300) return false; // about 300 mobs per army
        return true;
    }
}
