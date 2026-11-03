package com.reality.menutrim;

import com.reality.menutrim.core.TrimCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Forge 1.8.9: a Menu button on the title screen and pause menu, hiding every frame. */
@Mod(modid = "menutrim", name = "Menu Trim", version = "1.2.1", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class MenuTrimLegacy {

    /** An id no vanilla or Essential button uses. */
    private static final int MENU_ID = 0x4D54;
    private static final TrimCore.Visibility VISIBILITY = new TrimCore.Visibility() {
        @Override
        public void set(Object widget, boolean visible) {
            if (!(widget instanceof GuiButton)) return;
            GuiButton button = (GuiButton) widget;
            button.visible = visible;
            button.enabled = visible;
        }
    };
    /** Each open menu's button list, from when it opened, for hiding again every frame. */
    private final Map<GuiScreen, List<GuiButton>> lists = new WeakHashMap<GuiScreen, List<GuiButton>>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        TrimCore.init(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static boolean wanted(GuiScreen screen) {
        return screen instanceof GuiMainMenu || screen instanceof GuiIngameMenu;
    }

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen screen = event.gui;
        if (!wanted(screen)) return;
        List<GuiButton> buttons = event.buttonList;
        buttons.add(new GuiButton(MENU_ID, 4, 4, 44, 20, "Menu"));
        lists.put(screen, buttons);
        TrimCore.apply(screen, buttons, VISIBILITY);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        GuiScreen screen = event.gui;
        if (!wanted(screen)) return;
        TrimCore.apply(screen, lists.get(screen), VISIBILITY);
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiScreen screen = event.gui;
        if (!wanted(screen) || event.button.id != MENU_ID) return;
        Minecraft.getMinecraft().displayGuiScreen(new TrimScreen(screen, 0));
        event.setCanceled(true);
    }
}
