package com.reality.mobcraft.client;

/** Added to ItemStackRenderState and its layers by ItemStackRenderStateGlowMixin: marks a Creeper Essence charge. */
public interface ExplosiveGlowState {
    void mobcraft$markExplosive();

    /** Where the item is being drawn (hand, inventory, ground). Only the whole item's state knows. */
    default net.minecraft.world.item.ItemDisplayContext mobcraft$displayContext() { return null; }
}
