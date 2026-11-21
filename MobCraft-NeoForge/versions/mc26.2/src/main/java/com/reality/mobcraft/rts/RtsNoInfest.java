package com.reality.mobcraft.rts;

import com.reality.mobcraft.MobCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Silverfish under RTS command don't hide inside stone. While a player is in
 * RTS mode, every silverfish they can command near them has its "merge into
 * stone" goal taken away, so it can't turn a block into an infested block and
 * disappear. The goal is put back once they leave RTS or the silverfish is no
 * longer theirs to command.
 */
public final class RtsNoInfest {

    private static final double RANGE = 128.0;

    private record Taken(Mob mob, int priority, Goal goal) {}

    private static final Map<UUID, List<Taken>> TAKEN = new HashMap<>();
    private static Field goalSelectorField;
    private static boolean warned;
    private static boolean warnedName;

    private RtsNoInfest() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        Set<UUID> keep = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!RtsState.isActive(player)) continue;
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                    m -> m.isAlive() && m.getType() == EntityTypes.SILVERFISH && RtsControl.canCommand(m, player))) {
                keep.add(mob.getUUID());
                if (!TAKEN.containsKey(mob.getUUID())) take(mob);
            }
        }
        // Give the goal back to silverfish that aren't commanded in RTS any more.
        TAKEN.entrySet().removeIf(entry -> {
            if (keep.contains(entry.getKey())) return false;
            for (Taken taken : entry.getValue()) {
                if (taken.mob().isRemoved()) continue;
                GoalSelector goals = goals(taken.mob());
                if (goals != null) goals.addGoal(taken.priority(), taken.goal());
            }
            return true;
        });
    }

    private static void take(Mob mob) {
        GoalSelector goals = goals(mob);
        if (goals == null) return;
        List<Taken> taken = new ArrayList<>();
        for (WrappedGoal wrapped : new ArrayList<>(goals.getAvailableGoals())) {
            Goal goal = wrapped.getGoal();
            String name = goal.getClass().getName();
            if (name.contains("MergeWithStone") || name.contains("Infest")) {
                taken.add(new Taken(mob, wrapped.getPriority(), goal));
                goals.removeGoal(goal);
            }
        }
        // A silverfish always has this goal. Not finding it means its name changed in this version.
        if (taken.isEmpty() && !warnedName) {
            warnedName = true;
            List<String> names = new ArrayList<>();
            for (WrappedGoal wrapped : goals.getAvailableGoals()) names.add(wrapped.getGoal().getClass().getName());
            MobCraft.LOGGER.warn("RTS: couldn't find the silverfish's hide-in-stone goal, so commanded silverfish "
                    + "can still hide. Its goals are: {}", names);
        }
        TAKEN.put(mob.getUUID(), taken);
    }

    /** A mob's goal list. It's not public, so it's read by name. */
    private static GoalSelector goals(Mob mob) {
        try {
            if (goalSelectorField == null) {
                goalSelectorField = Mob.class.getDeclaredField("goalSelector");
                goalSelectorField.setAccessible(true);
            }
            return (GoalSelector) goalSelectorField.get(mob);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                MobCraft.LOGGER.warn("RTS: couldn't reach mob goals, so commanded silverfish can still hide in stone.", e);
            }
            return null;
        }
    }
}
