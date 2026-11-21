package com.reality.mobcraft.client.rts;

import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;

/**
 * The RTS camera. An invisible armor stand that only exists on this client.
 * Minecraft draws the world from its eyes while RTS mode is open, so the
 * player's real body stays where it is and shows up below.
 */
public class RtsCameraStand extends ArmorStand {

    public RtsCameraStand(Level level) {
        super(level, 0.0, 0.0, 0.0);
        setInvisible(true);
        setNoGravity(true);
        setNoBasePlate(true);
        setSilent(true);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
