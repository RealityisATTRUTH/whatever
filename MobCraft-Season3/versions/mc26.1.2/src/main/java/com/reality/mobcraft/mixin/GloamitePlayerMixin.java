package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.GloamiteTraits;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gloamite traits on the player: Sweep Hiss (a wider sword sweep) and Soul Dig (the shovel digs soul blocks faster). */
@Mixin(Player.class)
public abstract class GloamitePlayerMixin {

    @Inject(method = "doSweepAttack", at = @At("TAIL"))
    private void mobcraft$sweepHiss(Entity target, float damage, DamageSource source, float strength, CallbackInfo ci) {
        GloamiteTraits.sweepHiss((Player) (Object) this, target, damage, source);
    }

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void mobcraft$soulDig(BlockState state, CallbackInfoReturnable<Float> cir) {
        if ((state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)) && GloamiteTraits.has((Player) (Object) this, "Soul Dig")) {
            cir.setReturnValue(cir.getReturnValue() * 3f);
        }
    }
}
