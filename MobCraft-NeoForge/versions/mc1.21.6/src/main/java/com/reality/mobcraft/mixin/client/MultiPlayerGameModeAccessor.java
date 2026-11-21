package com.reality.mobcraft.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For the Unseenhush: a sound nearby restarts your mining. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyBlockPos") BlockPos mobcraft$destroyBlockPos();
    @Accessor("destroyProgress") void mobcraft$setDestroyProgress(float progress);
    @Accessor("destroyTicks") void mobcraft$setDestroyTicks(float ticks);
}
