package com.shumsic.autodrinkominousbottles;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidMessageDetectorTest {
    private final Language originalLanguage = Language.getInstance();

    @AfterEach
    void restoreLanguage() {
        Language.inject(originalLanguage);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Raid - Victory",
        "袭击 - 胜利",
        "襲擊 - 勝利",
        "襲撃 - 勝利",
        "습격 - 승리",
        "Überfall - Sieg",
        "Incursión - Victoria",
        "Raid - Victoire",
        "Набег - Победа",
        "Invasão - Vitória"
    })
    void recognizesRaidVictoryRegardlessOfDisplayedLanguage(String displayedText) {
        Language.inject(languageWithTranslations(Map.of(
            "event.minecraft.raid.victory.full", displayedText,
            "event.minecraft.raid.defeat.full", "not victory"
        )));

        assertTrue(RaidMessageDetector.isRaidVictory(
            Component.translatable("event.minecraft.raid.victory.full")
        ));
        assertFalse(RaidMessageDetector.isRaidVictory(
            Component.translatable("event.minecraft.raid.defeat.full")
        ));
    }

    private Language languageWithTranslations(Map<String, String> translations) {
        return new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return translations.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return translations.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return originalLanguage.getVisualOrder(text);
            }
        };
    }
}
