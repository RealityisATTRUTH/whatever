package com.reality.mobcraft.rts;

import com.reality.mobcraft.mob.MobAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a player's own mobs behave, set on the Mob Behavior page of
 * /mobcraftsettings. All three start on.
 *
 *   Follow   mobs that aren't fighting walk back to you when they're more
 *            than 8 blocks away. A mob you gave an RTS order holds its spot
 *            for 60 seconds instead.
 *   Defend   whatever hurts you becomes your mobs' target
 *   Attack   whatever you hit becomes your mobs' target
 *
 * Saved on the player, kept through death and when switching mobs.
 */
public final class MobBehavior {

    public static final String FOLLOW = "follow";
    public static final String DEFEND = "defend";
    public static final String ATTACK = "attack";
    public static final List<String> ALL = List.of(FOLLOW, DEFEND, ATTACK);

    private static final double RANGE = 48;
    private static final double FOLLOW_START = 8;
    private static final double FOLLOW_SPEED = 1.2;
    private static final int HOLD_AFTER_ORDER = 1200; // 60 seconds

    /** Mob UUID -> server tick of its last RTS order. */
    private static final Map<UUID, Integer> LAST_ORDER = new HashMap<>();

    private MobBehavior() {}

    public static String displayName(String behavior) {
        return switch (behavior) {
            case FOLLOW -> "Follow Me";
            case DEFEND -> "Defend Me";
            case ATTACK -> "Attack What I Hit";
            default -> behavior;
        };
    }

    /** Works on both sides, so the settings page can show the current state. */
    public static boolean isOn(Player player, String behavior) {
        return !player.getAttachedOrElse(MobAttachments.BEHAVIOR_OFF, List.of()).contains(behavior);
    }

    /** Server only. */
    public static void set(Player player, String behavior, boolean on) {
        if (!ALL.contains(behavior)) return;
        List<String> off = new ArrayList<>(player.getAttachedOrElse(MobAttachments.BEHAVIOR_OFF, List.of()));
        off.remove(behavior);
        if (!on) off.add(behavior);
        if (off.isEmpty()) {
            player.removeAttached(MobAttachments.BEHAVIOR_OFF);
        } else {
            player.setAttached(MobAttachments.BEHAVIOR_OFF, List.copyOf(off));
        }
    }

    /** The owner hit something. */
    public static void ownerAttacked(ServerPlayer owner, LivingEntity victim) {
        if (!isOn(owner, ATTACK)) return;
        sendAfter(owner, victim);
    }

    /** Something hurt the owner. */
    public static void ownerHurt(ServerPlayer owner, LivingEntity attacker) {
        if (!isOn(owner, DEFEND)) return;
        if (attacker instanceof Player other && other.isAlliedTo(owner)) return; // never turn on teammates
        sendAfter(owner, attacker);
    }

    private static void sendAfter(ServerPlayer owner, LivingEntity target) {
        if (target == owner || MobOwnership.isOwnedBy(target, owner)) return;
        for (Mob mob : owned(owner)) {
            if (PetSystem.isSitting(mob)) continue; // a sitting pet stays out of it
            if (mob != target) mob.setTarget(target);
        }
    }

    /** Called about once a second for every player. */
    public static void tickFollow(ServerPlayer owner, int now) {
        if (!isOn(owner, FOLLOW)) return;
        for (Mob mob : owned(owner)) {
            if (PetSystem.isSitting(mob)) continue; // told to stay
            if (mob.getTarget() != null && mob.getTarget().isAlive()) continue; // busy fighting
            if (RtsMobOrders.isBusy(mob)) continue; // following RTS orders
            Integer ordered = LAST_ORDER.get(mob.getUUID());
            if (ordered != null) {
                if (now - ordered < HOLD_AFTER_ORDER) continue; // holding its RTS position
                LAST_ORDER.remove(mob.getUUID());
            }
            if (mob.distanceTo(owner) > FOLLOW_START) {
                mob.getNavigation().moveTo(owner, FOLLOW_SPEED);
            }
        }
    }

    /** An RTS order was given to this mob. */
    public static void markOrdered(Mob mob, int now) {
        LAST_ORDER.put(mob.getUUID(), now);
    }

    private static List<Mob> owned(ServerPlayer owner) {
        return owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(RANGE),
                mob -> mob.isAlive() && MobOwnership.isOwnedBy(mob, owner));
    }
}
