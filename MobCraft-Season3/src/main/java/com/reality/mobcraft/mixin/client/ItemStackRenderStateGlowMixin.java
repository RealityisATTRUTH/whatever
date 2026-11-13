package com.reality.mobcraft.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import com.reality.mobcraft.client.ExplosiveGlowState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Marks every layer drawn so far as charged, and keeps the item redrawn every frame so its swirl moves. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateGlowMixin implements ExplosiveGlowState {

    @Shadow private int activeLayerCount;
    @Shadow private ItemStackRenderState.LayerRenderState[] layers;

    @Shadow public abstract void setAnimated();

    @Override
    @Unique
    public void mobcraft$markExplosive() {
        setAnimated();
        for (int i = 0; i < activeLayerCount; i++) ((ExplosiveGlowState) layers[i]).mobcraft$markExplosive();
    }
}
