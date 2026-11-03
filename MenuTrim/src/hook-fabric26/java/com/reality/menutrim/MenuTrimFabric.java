package com.reality.menutrim;

import com.reality.menutrim.core.TrimCore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/** Fabric: a Menu button on the title screen and pause menu, and hiding every tick while they're open. */
public class MenuTrimFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TrimCore.init(FabricLoader.getInstance().getConfigDir().toFile());
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;
            Screens.getWidgets(screen).add(Ui.button(4, 4, 44, 20, "Menu", () -> Ui.open(new TrimScreen(screen, 0))));
            apply(screen);
            ScreenEvents.afterTick(screen).register(MenuTrimFabric::apply);
        });
    }

    private static void apply(Screen screen) {
        TrimCore.apply(screen, Screens.getWidgets(screen), Ui::setVisible);
    }
}
