package com.reality.mobcraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For the Unseenhush: lets a nearby sound restart someone's mining. */
@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeAccessor {
    @Accessor("isDestroyingBlock") boolean mobcraft$isDestroying();
    @Accessor("destroyPos") BlockPos mobcraft$destroyPos();
    @Accessor("hasDelayedDestroy") boolean mobcraft$hasDelayedDestroy();
    @Accessor("delayedDestroyPos") BlockPos mobcraft$delayedDestroyPos();
    @Accessor("gameTicks") int mobcraft$gameTicks();
    @Accessor("destroyProgressStart") void mobcraft$setDestroyProgressStart(int ticks);
    @Accessor("delayedTickStart") void mobcraft$setDelayedTickStart(int ticks);
}
