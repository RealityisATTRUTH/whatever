package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.DuskveinAnvil;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Duskvein Anvil never chips or breaks from use or from falling. */
@Mixin(AnvilBlock.class)
public abstract class AnvilBlockDamageMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private static void mobcraft$neverChips(BlockState state, CallbackInfoReturnable<BlockState> cir) {
        if (state.is(DuskveinAnvil.BLOCK)) cir.setReturnValue(state);
    }
}
