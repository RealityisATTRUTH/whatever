package com.reality.mobcraft.mob.kit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The Bat's Confuse, also used for the Iron Golem's 2 second Golem Toss stun.
 *
 *   A confused player can't move, jump or attack, and can't give RTS orders.
 *   A confused mob keeps doing what it was told, but can't get new RTS orders.
 *
 * Lasts until the timer runs out, even across death and relogging.
 * Kept in memory only, so a server restart clears it.
 */
public final class Confusion {

    private static final Map<UUID, Integer> UNTIL = new HashMap<>();

    private Confusion() {}

    /** Stuns for this long, unless they're already stunned for longer. */
    public static void apply(LivingEntity target, int ticks, int now) {
        UNTIL.merge(target.getUUID(), now + ticks, Math::max);
        if (target instanceof ServerPlayer player) freeze(player, true);
    }

    public static boolean isConfused(Entity entity) {
        return UNTIL.containsKey(entity.getUUID());
    }

    public static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> it = UNTIL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            boolean over = now >= entry.getValue();
            if (player != null) freeze(player, !over); // re-applied after respawn too
            if (over) {
                it.remove();
                if (player != null) KitUtil.actionBar(player, "You can move again.");
            }
        }
    }

    private static void freeze(ServerPlayer player, boolean on) {
        KitUtil.modifier(player, Attributes.MOVEMENT_SPEED, "confused_still", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
        KitUtil.modifier(player, Attributes.JUMP_STRENGTH, "confused_jump", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
    }
}
