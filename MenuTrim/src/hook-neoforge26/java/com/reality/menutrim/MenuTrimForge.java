package com.reality.menutrim;

import com.reality.menutrim.core.TrimCore;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.common.Mod;

/** NeoForge 1.21.11 and 26.x: a Menu button on the title screen and pause menu, hiding every frame. */
@Mod("menutrim")
public class MenuTrimForge {

    public MenuTrimForge() {
        if (FMLEnvironment.getDist() != Dist.CLIENT) return; // client only
        TrimCore.init(FMLPaths.CONFIGDIR.get().toFile());
        NeoForge.EVENT_BUS.addListener(MenuTrimForge::onInit);
        NeoForge.EVENT_BUS.addListener(MenuTrimForge::onDraw);
    }

    private static boolean wanted(Screen screen) {
        return screen instanceof TitleScreen || screen instanceof PauseScreen;
    }

    private static void onInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!wanted(screen)) return;
        event.addListener(Ui.button(4, 4, 44, 20, "Menu", () -> Ui.open(new TrimScreen(screen, 0))));
        TrimCore.apply(screen, event.getListenersList(), Ui::setVisible);
    }

    private static void onDraw(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (wanted(screen)) TrimCore.apply(screen, screen.children(), Ui::setVisible);
    }
}
