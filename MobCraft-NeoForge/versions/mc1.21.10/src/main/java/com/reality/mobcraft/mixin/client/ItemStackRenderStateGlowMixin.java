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
    @Shadow net.minecraft.world.item.ItemDisplayContext displayContext;

    @Override
    public net.minecraft.world.item.ItemDisplayContext mobcraft$displayContext() {
        return displayContext;
    }
    @Shadow private ItemStackRenderState.LayerRenderState[] layers;

    @Shadow public abstract void setAnimated();

    @Unique private boolean mobcraft$backwards;

    @Override
    public void mobcraft$markBackwards() { mobcraft$backwards = true; }

    @Override
    public boolean mobcraft$backwards() { return mobcraft$backwards; }

    @org.spongepowered.asm.mixin.injection.Inject(method = "clear", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void mobcraft$clearBackwards(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        mobcraft$backwards = false;
    }

    @Unique private static final Object CHARGED = new Object();

    @Override
    @Unique
    public void mobcraft$markExplosive() {
        setAnimated();
        // 26.1 caches inventory icons by model, so a charged item would reuse the plain item's icon. Mark it apart.
        if ((Object) this instanceof net.minecraft.client.renderer.item.TrackingItemStackRenderState tracking)
            tracking.appendModelIdentityElement(CHARGED);
        for (int i = 0; i < activeLayerCount; i++) ((ExplosiveGlowState) layers[i]).mobcraft$markExplosive();
    }
}
