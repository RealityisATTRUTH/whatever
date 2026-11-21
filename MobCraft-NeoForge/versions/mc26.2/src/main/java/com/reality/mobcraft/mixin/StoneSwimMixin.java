package com.reality.mobcraft.mixin;

import com.reality.mobcraft.mob.kit.SilverfishKit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Silverfish's Stone Swim: stone has no collision for a Silverfish swimming through it, and every
 * other block stays solid. Before, swimming turned off collision with everything and the server pushed
 * him back out of anything that wasn't stone. Standing on sandstone under stone, he'd sink into it on his
 * own screen and get snapped back up every tick: a flicker. Now he just stands on it.
 * Runs on both sides, so his game and the server agree on where he can go.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class StoneSwimMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void mobcraft$stoneSwim(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && SilverfishKit.stoneSwimming(player)
                && SilverfishKit.swimmable((BlockState) (Object) this)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
