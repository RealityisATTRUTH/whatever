package com.reality.mobcraft.rts;

import com.reality.mobcraft.mob.MobAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Which player owns which mob. Owned mobs can be commanded in RTS mode.
 * Summoning abilities (escorts, vexes, the Axolotl's pod) will set the owner
 * when they spawn mobs. For now /rtstest claim sets it for testing.
 */
public final class MobOwnership {

    private MobOwnership() {}

    public static boolean isOwnedBy(Entity entity, Player player) {
        String owner = entity.getAttached(MobAttachments.OWNER);
        return owner != null && owner.equals(player.getUUID().toString());
    }

    /** Server only. */
    public static void setOwner(Entity entity, Player player) {
        entity.setAttached(MobAttachments.OWNER, player.getUUID().toString());
    }

    /** Server only. */
    public static void clearOwner(Entity entity) {
        entity.removeAttached(MobAttachments.OWNER);
    }
}
