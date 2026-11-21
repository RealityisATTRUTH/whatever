package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.GloamiteTraits;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gloamite traits that change how a holder moves: Rooted (axe, half knockback) and Soul Dig (shovel, no soul sand slowdown). */
@Mixin(LivingEntity.class)
public abstract class GloamiteLivingMixin {

    @ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double mobcraft$rooted(double strength) {
        return GloamiteTraits.has((LivingEntity) (Object) this, "Rooted") ? strength * 0.5 : strength;
    }

    @Inject(method = "getBlockSpeedFactor", at = @At("RETURN"), cancellable = true)
    private void mobcraft$soulWalk(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player) || cir.getReturnValue() >= 1f || !GloamiteTraits.has(self, "Soul Dig")) return;
        var below = self.level().getBlockState(self.getBlockPosBelowThatAffectsMyMovement());
        if (below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL) || self.level().getBlockState(self.blockPosition()).is(Blocks.SOUL_SAND)) {
            cir.setReturnValue(1f);
        }
    }
}
