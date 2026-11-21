package com.reality.mobcraft.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Move the energy bar anywhere on screen. Done saves, Reset puts it back in
 * the top-left corner, Escape leaves without saving.
 *
 * The bar itself is an EnergyBarWidget, so mouse input reaches it the same
 * way it reaches buttons.
 */
public class HudEditorScreen extends Screen {

    private static boolean open = false;

    private final float startX = HudConfig.energyBarX();
    private final float startY = HudConfig.energyBarY();

    private EnergyBarWidget bar;

    public HudEditorScreen() {
        super(Component.literal("Move Energy Bar"));
        open = true;
    }

    /** True while the editor is showing, so the normal HUD copy hides. */
    public static boolean isOpen() {
        return open;
    }

    @Override
    protected void init() {
        bar = this.addRenderableWidget(new EnergyBarWidget(this.width, this.height));

        int centerX = this.width / 2;
        int bottom = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> {
            HudConfig.save();
            open = false;
            this.minecraft.gui.setScreen(null);
        }).bounds(centerX - 102, bottom, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            HudConfig.setEnergyBar(HudConfig.DEFAULT_X, HudConfig.DEFAULT_Y);
            bar.snapToSaved();
        }).bounds(centerX + 2, bottom, 100, 20).build());
    }

    /** Escape: put the bar back where it was and leave without saving. */
    @Override
    public void onClose() {
        HudConfig.setEnergyBar(startX, startY);
        open = false;
        super.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, "Drag the energy bar, or click it once and click again to drop it.",
                this.width / 2, this.height / 2 - 10, 0xFFFFFFFF);
        graphics.centeredText(this.font, "Done saves. Escape cancels.",
                this.width / 2, this.height / 2 + 2, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
