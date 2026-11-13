package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.reality.mobcraft.item.TrollTiers;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Quick and Legendary bows count each tick of drawing as a little more, so they reach full power sooner. */
@Mixin(BowItem.class)
public abstract class BowTierMixin {

    @WrapOperation(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float mobcraft$quickDraw(int ticks, Operation<Float> original, @Local(argsOnly = true) ItemStack bow) {
        return original.call(TrollTiers.drawTicks(bow, ticks));
    }
}
