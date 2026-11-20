package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.SkullVisor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skull Visor: skeletons never pick its wearer as a target, so they don't aim, draw or shoot at
 * them at all. Rabbit Ears: while you're disguised as a rabbit, no mob picks you. canAttack keeps them from choosing you; setTarget catches every other way a mob
 * gets a target, like being hit.
 */
@Mixin(Mob.class)
public abstract class SkullVisorTargetMixin {

    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
    private LivingEntity mobcraft$visorSetTarget(LivingEntity target) {
        boolean ignore = SkullVisor.ignores((Mob) (Object) this, target)
                || (target != null && com.reality.mobcraft.item.RabbitEars.hidden(target));
        return ignore ? null : target;
    }
}
