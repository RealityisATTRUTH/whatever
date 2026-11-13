package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.Duskvein;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The enchanting table's lapis slot takes Duskvein too. */
@Mixin(targets = "net.minecraft.world.inventory.EnchantmentMenu$3")
public abstract class EnchantLapisSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void mobcraft$sculkLapis(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Duskvein.is(stack)) cir.setReturnValue(true);
    }
}
