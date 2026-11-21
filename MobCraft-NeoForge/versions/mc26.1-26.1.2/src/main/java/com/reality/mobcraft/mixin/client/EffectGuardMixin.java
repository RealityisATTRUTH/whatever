package com.reality.mobcraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.reality.mobcraft.client.BlindOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Blindness and Darkness working on your own screen, whatever other mods
 * do to them. Client only, and only for your own player.
 *
 *   No Blind            makes hasEffect answer "no" for Blindness and Darkness.
 *                       This wraps the whole method, their change included, and
 *                       answers "yes" again whenever the effect really is in
 *                       your effect list. So the game's own blindness fog and
 *                       darkness come back.
 *   No Darkness Effect  calls removeEffect on Darkness every tick. While the
 *                       server says you're blind or in Darkness, removing
 *                       either one from your own game is refused. The server
 *                       still clears them normally when they run out or you
 *                       drink milk, since that goes through a different path.
 */
@Mixin(LivingEntity.class)
public abstract class EffectGuardMixin {

    @WrapMethod(method = "hasEffect")
    private boolean mobcraft$realEffects(Holder<MobEffect> effect, Operation<Boolean> original) {
        boolean answer = original.call(effect);
        if (answer || !isBlindingEffect(effect)) return answer;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != Minecraft.getInstance().player) return answer;
        // Only when it's really there, so anything that then reads the effect finds it.
        return self.getActiveEffectsMap().containsKey(effect);
    }

    @Inject(method = "removeEffect", at = @At("HEAD"), cancellable = true)
    private void mobcraft$keepEffects(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (!isBlindingEffect(effect)) return;
        Minecraft client = Minecraft.getInstance();
        if ((Object) this != client.player) return;
        if (BlindOverlay.serverLevel(client) > 0) cir.setReturnValue(false);
    }

    private static boolean isBlindingEffect(Holder<MobEffect> effect) {
        return effect.is(MobEffects.BLINDNESS) || effect.is(MobEffects.DARKNESS);
    }
}
