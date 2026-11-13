package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.item.CreeperEssence;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.reality.mobcraft.client.ExplosiveGlowState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creeper Essence: a charged item's layers are marked for the orange swirl (client/ExplosiveItemGlow). */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverGlowMixin {

    @Inject(method = "appendItemLayers", at = @At("TAIL"))
    private void mobcraft$explosive(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
                                    ItemOwner owner, int seed, CallbackInfo ci) {
        if (stack.has(CreeperEssence.CHARGE)) ((ExplosiveGlowState) state).mobcraft$markExplosive();
    }
}
