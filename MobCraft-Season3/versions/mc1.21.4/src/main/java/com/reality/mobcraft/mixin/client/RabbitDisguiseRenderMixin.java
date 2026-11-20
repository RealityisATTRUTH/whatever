package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.RabbitDisguise;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Rabbit Ears: where the game draws a disguised player, it draws their rabbit instead (client/RabbitDisguise). */
@Mixin(LevelRenderer.class)
public abstract class RabbitDisguiseRenderMixin {
    @ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true)
    private Entity mobcraft$rabbitInstead(Entity entity) {
        if (entity instanceof Player player) {
            Rabbit rabbit = RabbitDisguise.rabbitFor(player);
            if (rabbit != null) return rabbit;
        }
        return entity;
    }
}
