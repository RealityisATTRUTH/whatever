package com.reality.mobcraft.mixin;

import com.reality.mobcraft.mob.kit.VexKit;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Vex's Phase and the Silverfish's Stone Swim. Every tick vanilla sets noPhysics = isSpectator(), which
 * is what lets spectators fly through blocks. Right after that line, a
 * phasing Vex gets noPhysics too. Runs on both the player's own game (so they
 * can move through walls) and the server (so it accepts the movement).
 */
@Mixin(Player.class)
public abstract class PlayerPhaseMixin {

    @Inject(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void mobcraft$phase(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (VexKit.isPhasing(self)) self.noPhysics = true;
    }
}
