package com.reality.mobcraft.client;

import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The energy bar inside the editor, as a real widget so it gets mouse clicks
 * the same way buttons do.
 *
 * Two ways to move it:
 *   click, drag, release      it follows the mouse while held
 *   click once, click again   it follows the mouse between the two clicks
 */
public class EnergyBarWidget extends AbstractWidget {

    private static final int HIGHLIGHT = 0xFFFFFF40;
    private static final int CARRY_BACK = 0x40FFFFFF;
    private static final int MOVE_THRESHOLD = 3;

    private final int screenWidth;
    private final int screenHeight;

    private boolean carrying = false;
    private double grabOffsetX;
    private double grabOffsetY;
    private int pickupX;
    private int pickupY;

    public EnergyBarWidget(int screenWidth, int screenHeight) {
        super(EnergyHud.screenX(screenWidth), EnergyHud.screenY(screenHeight),
                EnergyHud.WIDTH, EnergyHud.HEIGHT, Component.literal("Energy bar"));
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /** Re-reads the saved position, used after Reset. */
    public void snapToSaved() {
        carrying = false;
        setX(EnergyHud.screenX(screenWidth));
        setY(EnergyHud.screenY(screenHeight));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (carrying) {
            carrying = false; // second click drops it
            return;
        }
        carrying = true;
        grabOffsetX = event.x() - getX();
        grabOffsetY = event.y() - getY();
        pickupX = getX();
        pickupY = getY();
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        // Dragged and let go: drop it here. A plain click keeps carrying it.
        if (carrying && (Math.abs(getX() - pickupX) > MOVE_THRESHOLD || Math.abs(getY() - pickupY) > MOVE_THRESHOLD)) {
            carrying = false;
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (carrying) moveTo(mouseX - grabOffsetX, mouseY - grabOffsetY);

        int x = getX();
        int y = getY();
        if (carrying || isHovered()) {
            graphics.fill(x - 2, y - 2, x + this.width + 2, y + this.height + 2, CARRY_BACK);
        }
        graphics.outline(x - 2, y - 2, this.width + 4, this.height + 4, HIGHLIGHT);

        // Preview with the player's real mob if they have one.
        Minecraft client = Minecraft.getInstance();
        String name = "Mob";
        int color = EnergyHud.colorOf(null);
        float energy = 72;
        int max = 100;
        if (client.player != null) {
            MobType type = PlayerMob.get(client.player);
            if (type != null) {
                name = type.displayName;
                color = EnergyHud.colorOf(type);
                energy = PlayerMob.energy(client.player);
                max = type.maxEnergy;
            }
        }
        EnergyHud.draw(graphics, client.font, x, y, name, color, energy, max, 0);
    }

    /** Moves the bar, keeping it on screen, and stores the new spot as a fraction. */
    private void moveTo(double left, double top) {
        double freeX = Math.max(1, screenWidth - EnergyHud.WIDTH - 8);
        double freeY = Math.max(1, screenHeight - EnergyHud.HEIGHT - 8);
        HudConfig.setEnergyBar((float) ((left - 4) / freeX), (float) ((top - 4) / freeY));
        setX(EnergyHud.screenX(screenWidth));
        setY(EnergyHud.screenY(screenHeight));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        // No narration needed for a drag handle.
    }
}
