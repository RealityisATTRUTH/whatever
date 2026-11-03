package com.reality.menutrim;

import com.reality.menutrim.core.TrimCore;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.common.Mod;

/** Forge 1.21.6 and newer, with its new event buses: a Menu button on the title screen and pause menu, hiding every frame. */
@Mod("menutrim")
public class MenuTrimForge {

    public MenuTrimForge() {
        if (FMLEnvironment.dist != Dist.CLIENT) return; // client only
        TrimCore.init(FMLPaths.CONFIGDIR.get().toFile());
        ScreenEvent.Init.Post.BUS.addListener(MenuTrimForge::onInit);
        ScreenEvent.Render.Pre.BUS.addListener(MenuTrimForge::onDraw);
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
