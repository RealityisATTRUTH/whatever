package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.Tiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts a tool's tier in front of its name, like "Keen Iron Sword". */
@Mixin(ItemStack.class)
public abstract class TierNameMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void mobcraft$tierName(CallbackInfoReturnable<Component> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.has(Tiers.TIER)) cir.setReturnValue(Tiers.name(self, cir.getReturnValue()));
    }
}
