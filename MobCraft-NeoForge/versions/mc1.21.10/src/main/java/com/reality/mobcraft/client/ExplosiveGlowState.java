package com.reality.mobcraft.client;

/** Added to ItemStackRenderState and its layers by ItemStackRenderStateGlowMixin: marks a Creeper Essence charge. */
public interface ExplosiveGlowState {
    void mobcraft$markExplosive();

    /** Where the item is being drawn (hand, inventory, ground). Only the whole item's state knows. */
    default net.minecraft.world.item.ItemDisplayContext mobcraft$displayContext() { return null; }

    /** The Backwards troll tier (1.21.10 and older, where the held-item layer can't see the stack). */
    default void mobcraft$markBackwards() {}
    default boolean mobcraft$backwards() { return false; }
}
