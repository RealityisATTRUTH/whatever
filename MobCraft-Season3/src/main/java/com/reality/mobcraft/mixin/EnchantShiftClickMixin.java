package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.mobcraft.item.Duskvein;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shift-clicking Duskvein in the enchanting table moves it into the lapis slot, the same as lapis. */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantShiftClickMixin {
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z", ordinal = 0))
    private boolean mobcraft$duskveinShiftClick(ItemStack stack, Object item, Operation<Boolean> original) {
        return original.call(stack, item) || Duskvein.is(stack);
    }
}
