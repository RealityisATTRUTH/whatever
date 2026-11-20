package com.reality.mobcraft.mixin.client;

import net.minecraft.world.entity.animal.rabbit.Rabbit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** For the rabbit drawn in place of a disguised player (client/RabbitDisguise): its hop and its fur. */
@Mixin(Rabbit.class)
public interface RabbitAccessor {
    @Accessor("jumpTicks") int mobcraft$jumpTicks();
    @Accessor("jumpTicks") void mobcraft$setJumpTicks(int ticks);
    @Accessor("jumpDuration") int mobcraft$jumpDuration();
    @Accessor("jumpDuration") void mobcraft$setJumpDuration(int ticks);
    @Invoker("setVariant") void mobcraft$setVariant(Rabbit.Variant variant);
}
