package com.shumsic.autodrinkominousbottles;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

final class RaidMessageDetector {
    private static final String RAID_VICTORY_TRANSLATION_KEY = "event.minecraft.raid.victory.full";

    private RaidMessageDetector() {
    }

    static boolean isRaidVictory(Component message) {
        return message != null
            && message.getContents() instanceof TranslatableContents translatable
            && RAID_VICTORY_TRANSLATION_KEY.equals(translatable.getKey());
    }
}
