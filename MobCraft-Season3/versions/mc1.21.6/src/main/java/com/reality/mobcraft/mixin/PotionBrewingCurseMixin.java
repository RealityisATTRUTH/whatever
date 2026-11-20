package com.reality.mobcraft.mixin;

import com.reality.mobcraft.effect.ModEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** There's no drinkable Curse: brewing one gives the splash potion straight away. */
@Mixin(PotionBrewing.class)
public class PotionBrewingCurseMixin {
    @Inject(method = "mix", at = @At("RETURN"), cancellable = true)
    private void mobcraft$splashCurse(ItemStack ingredient, ItemStack potion, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack out = cir.getReturnValue();
        var contents = out.get(DataComponents.POTION_CONTENTS);
        if (out.is(Items.POTION) && contents != null && contents.is(ModEffects.CURSE_POTION)) {
            cir.setReturnValue(out.transmuteCopy(Items.SPLASH_POTION));
        }
    }
}
