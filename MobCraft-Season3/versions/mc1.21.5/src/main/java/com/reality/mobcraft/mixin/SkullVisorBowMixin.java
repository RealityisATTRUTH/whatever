package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.SkullVisor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skull Visor: bows draw 25% faster. Every fourth tick of drawing counts twice. Runs on the
 * client and the server alike, so the pull you see matches the shot you get.
 */
@Mixin(LivingEntity.class)
public abstract class SkullVisorBowMixin {

    @Shadow protected int useItemRemaining;

    @Inject(method = "updateUsingItem", at = @At("TAIL"))
    private void mobcraft$fasterBow(ItemStack using, CallbackInfo ci) {
        if (useItemRemaining > 1 && SkullVisor.drawsFaster((LivingEntity) (Object) this, using)) {
            useItemRemaining--;
        }
    }
}
