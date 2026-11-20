package com.reality.mobcraft.client;

import com.reality.mobcraft.mob.Jeff;
import com.reality.mobcraft.mob.PlayerMob;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The M button in the survival inventory. It sits in the empty space to the
 * right of the recipe book button, under the crafting output, and opens the
 * Mob Item Menu.
 *
 * Season 2's button was placed once when the inventory opened, so it got left
 * behind when the recipe book slid the inventory over. This one re-reads the
 * recipe book button's position every frame and follows it.
 */
public final class InventoryMobButton {

    private static final int WIDTH = 20;
    private static final int HEIGHT = 18;      // same height as the recipe book button
    private static final int OFFSET_X = 40;    // from the recipe book button's left edge

    private InventoryMobButton() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen)) return;
            if (client.player == null || PlayerMob.get(client.player) == null) return;

            Button mobButton = Button.builder(Component.literal("M"),
                    button -> client.gui.setScreen(Jeff.isPowered(client.player)
                            ? new JeffMenuScreen(screen) : new MobItemMenuScreen(screen))
            ).bounds(0, 0, WIDTH, HEIGHT).build();

            List<AbstractWidget> widgets = Screens.getWidgets(screen);
            widgets.add(mobButton);
            followRecipeButton(widgets, mobButton, scaledWidth, scaledHeight);

            // Every frame, line back up with the recipe book button in case it moved.
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
                    followRecipeButton(widgets, mobButton, s.width, s.height));
        });
    }

    private static void followRecipeButton(List<AbstractWidget> widgets, Button mobButton, int width, int height) {
        for (AbstractWidget widget : widgets) {
            // The recipe book toggle is the inventory's only image button.
            if (widget instanceof ImageButton && widget != mobButton) {
                mobButton.setX(widget.getX() + OFFSET_X);
                mobButton.setY(widget.getY());
                return;
            }
        }
        // No recipe book button found: use where it normally sits with the book closed.
        mobButton.setX(width / 2 - 88 + 104 + OFFSET_X);
        mobButton.setY(height / 2 - 22);
    }
}
