package com.reality.mobcraft.mob;

import com.reality.mobcraft.mob.kit.KitUtil;
import com.reality.mobcraft.rts.MobOwnership;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

import java.util.Set;

/**
 * Switching mobs is a betrayal. The kind you were before sees you as an
 * enemy: any of them within 16 blocks that can see you comes after you,
 * even ones that are normally neutral, like Iron Golems and Endermen.
 *
 * Only the mob you were right before counts. Going back to that mob makes
 * peace with it, but then the mob you just left holds the grudge. If your new mob shares an ally with the old one, like
 * Endermen for the Enderman and the Shulker, the new mob's peace wins.
 * Mobs you own through RTS never turn on you.
 *
 * Your old kind is the old kit's allies list. The Rabbit and the Bat have
 * none, so leaving them has no effect.
 */
public final class FormerMob {

    private static final double RANGE = 16;

    /** Kinds that turn on you, from your old kit's allies. Villagers can't fight, so their golems do. */
    private static Set<EntityType<?>> kin(MobType old) {
        if (old == MobType.VILLAGER) return Set.of(EntityTypes.IRON_GOLEM);
        return MobKits.get(old).allies();
    }

    private FormerMob() {}

    /** Called from MobTicker.assign when a player's mob changes. */
    static void onSwitch(ServerPlayer player, MobType old, MobType next) {
        MobType previous = former(player);
        if (old != null && old != next) {
            // The mob you just left is the one holding the grudge now.
            player.setAttached(MobAttachments.FORMER_MOB, old.id());
        } else if (next != null && next == previous) {
            player.removeAttached(MobAttachments.FORMER_MOB);
        }
        if (next != null && next == previous) {
            player.sendSystemMessage(Component.literal("You're a " + next.displayName + " again. Your kind forgives you."));
        }
        if (old != null && old != next && !kin(old).isEmpty()) {
            player.sendSystemMessage(Component.literal("You left the " + old.displayName + ". Its kind now sees you as an enemy."));
        }
    }

    public static MobType former(ServerPlayer player) {
        return MobType.byId(player.getAttached(MobAttachments.FORMER_MOB));
    }

    /** Once a second, every nearby member of your old kind that can see you comes after you. */
    static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || player.isCreative() || player.isSpectator()) return;
        MobType old = former(player);
        if (old == null) return;
        Set<EntityType<?>> kin = kin(old);
        if (kin.isEmpty()) return;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                m -> kin.contains(m.getType()) && m.isAlive() && !MobOwnership.isOwnedBy(m, player)
                        && m.distanceTo(player) <= RANGE && m.hasLineOfSight(player))) {
            KitUtil.provoke(mob, player);
        }
    }
}
