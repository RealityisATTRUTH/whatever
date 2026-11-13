package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.GrubRenderer;
import com.reality.mobcraft.client.LensChain;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the Illusion Lens's chain and the squirming grub how and by whom they're drawn, which a special renderer isn't told, and keeps them redrawn every frame. */
@Mixin(SpecialModelWrapper.class)
public abstract class SpecialModelWrapperMixin {

    @Shadow @Final
    private SpecialModelRenderer<?> specialRenderer;

    @Inject(method = "update", at = @At("HEAD"))
    private void mobcraft$lensChain(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                                    ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed, CallbackInfo ci) {
        if (specialRenderer instanceof LensChain || specialRenderer instanceof GrubRenderer
                || specialRenderer instanceof com.reality.mobcraft.client.TomeRenderer) LensChain.beginUpdate(state, context, owner);
    }
}
