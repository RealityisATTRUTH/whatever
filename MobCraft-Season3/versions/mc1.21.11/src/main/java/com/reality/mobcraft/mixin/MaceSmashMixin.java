package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.MaceEnchants;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the MobCraft mace enchants when a smash attack lands, while the fall distance is still known. */
@Mixin(MaceItem.class)
public abstract class MaceSmashMixin {
    @Inject(method = "hurtEnemy", at = @At("HEAD"))
    private void mobcraft$smash(ItemStack mace, LivingEntity target, LivingEntity attacker, CallbackInfo ci) {
        if (MaceItem.canSmashAttack(attacker)) MaceEnchants.onSmash(mace, target, attacker);
    }
}
