package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

public interface IdentifiedLayer extends LayeredDraw.Layer {
    ResourceLocation MISC_OVERLAYS = VanillaGuiLayers.CAMERA_OVERLAYS;
    ResourceLocation CROSSHAIR = VanillaGuiLayers.CROSSHAIR;
    ResourceLocation HOTBAR_AND_BARS = VanillaGuiLayers.HOTBAR;
    ResourceLocation EXPERIENCE_LEVEL = VanillaGuiLayers.EXPERIENCE_LEVEL;
    ResourceLocation STATUS_EFFECTS = VanillaGuiLayers.EFFECTS;
    ResourceLocation BOSS_BAR = VanillaGuiLayers.BOSS_OVERLAY;
    ResourceLocation SLEEP = VanillaGuiLayers.SLEEP_OVERLAY;
    ResourceLocation DEMO_TIMER = VanillaGuiLayers.DEMO_OVERLAY;
    ResourceLocation DEBUG = VanillaGuiLayers.DEBUG_OVERLAY;
    ResourceLocation SCOREBOARD = VanillaGuiLayers.SCOREBOARD_SIDEBAR;
    ResourceLocation OVERLAY_MESSAGE = VanillaGuiLayers.OVERLAY_MESSAGE;
    ResourceLocation TITLE_AND_SUBTITLE = VanillaGuiLayers.TITLE;
    ResourceLocation CHAT = VanillaGuiLayers.CHAT;
    ResourceLocation PLAYER_LIST = VanillaGuiLayers.TAB_LIST;
    ResourceLocation SUBTITLES = VanillaGuiLayers.SUBTITLE_OVERLAY;

    ResourceLocation id();
    static IdentifiedLayer of(ResourceLocation id, LayeredDraw.Layer layer) {
        return new IdentifiedLayer() {
            public ResourceLocation id() { return id; }
            public void render(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.DeltaTracker t) { layer.render(g, t); }
        };
    }
}
