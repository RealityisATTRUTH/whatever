package com.reality.mobcraft.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the bow tiers read an arrow's base damage and whether it has landed. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Accessor("baseDamage")
    double mobcraft$baseDamage();

    @Invoker("isInGround")
    boolean mobcraft$inGround();
}
