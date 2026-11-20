package com.reality.mobcraft.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider from 1 to 64 for how many items to give. */
public class AmountSlider extends AbstractSliderButton {

    public static final int MIN = 1;
    public static final int MAX = 64;

    public AmountSlider(int x, int y, int width, int height, int initialAmount) {
        super(x, y, width, height, Component.empty(), toValue(initialAmount));
        updateMessage();
    }

    /** The amount the slider is set to, 1 to 64. */
    public int amount() {
        return MIN + (int) Math.round(this.value * (MAX - MIN));
    }

    private static double toValue(int amount) {
        int clamped = Math.max(MIN, Math.min(MAX, amount));
        return (clamped - MIN) / (double) (MAX - MIN);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal("Amount: " + amount()));
    }

    @Override
    protected void applyValue() {
        // Nothing to do while sliding. The menu reads amount() when you press Give.
    }
}
