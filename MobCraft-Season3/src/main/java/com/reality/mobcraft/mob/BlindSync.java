package com.reality.mobcraft.mob;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

/**
 * Tells each player's game how blind the server says they are, so the
 * mod's own overlay (BlindOverlay) can darken the screen. Mods that hide the
 * vanilla Blindness or Darkness effect can't hide this, since it isn't an
 * effect and the server keeps setting it.
 */
public final class BlindSync {

    private BlindSync() {}

    /** Every tick for every player. */
    public static void tick(ServerPlayer player) {
        int level = 0;
        // Read straight from the effect list, not hasEffect, which mods like No Blind
        // hook. In singleplayer those mods run in the same game as the server.
        var effects = player.getActiveEffectsMap();
        if (effects.containsKey(MobEffects.BLINDNESS)) level = 2;
        else if (effects.containsKey(MobEffects.DARKNESS)) level = 1;
        // A cursed Bat is blind no matter what happens to the effect.
        if (PlayerMob.has(player, MobType.BAT) && PlayerMob.isCursed(player)) level = 2;
        if (player.isSpectator() || player.isCreative()) level = 0;

        Integer current = player.getAttached(MobAttachments.BLIND_LEVEL);
        int now = current == null ? 0 : current;
        if (now == level) return;
        if (level == 0) {
            player.removeAttached(MobAttachments.BLIND_LEVEL);
        } else {
            player.setAttached(MobAttachments.BLIND_LEVEL, level);
        }
    }
}
