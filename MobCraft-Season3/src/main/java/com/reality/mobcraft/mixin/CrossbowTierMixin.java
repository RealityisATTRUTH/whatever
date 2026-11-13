package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.TrollTiers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Quick and Legendary crossbows charge faster. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowTierMixin {

    @Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
    private static void mobcraft$quickCharge(ItemStack crossbow, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(TrollTiers.chargeTicks(crossbow, cir.getReturnValue()));
    }
}
