package com.reality.mobcraft.rts;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of army stances, set per army in RTS.
 *
 *   Aggressive   35% faster. Move orders become attack-moves, so the army
 *                fights anything hostile it meets on the way. Standing about
 *                with nothing to do, it goes after enemies it can see within
 *                10 blocks by itself.
 *   Normal       vanilla speed, moves are plain moves.
 *
 * Read from each player's saved armies once a second.
 */
public final class RtsStances {

    private static final String SPEED_MODIFIER = "rts_aggressive_speed";
    private static final double SPEED_BONUS = 0.35;

    /** Aggressive mob -> its commander. */
    private static Map<UUID, UUID> aggressive = new HashMap<>();
    /** Every mob in anyone's army, aggressive or not. */
    private static Set<UUID> inArmy = new HashSet<>();
    private static final Map<UUID, Mob> SPED_UP = new HashMap<>();

    private RtsStances() {}

    public static boolean isInArmy(UUID mob) {
        return inArmy.contains(mob);
    }

    public static boolean isAggressive(Mob mob) {
        return aggressive.containsKey(mob.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        Map<UUID, UUID> now = new HashMap<>();
        Set<UUID> armyMobs = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<String> lines = player.getAttached(MobAttachments.RTS_GROUPS);
            if (lines == null) continue;
            for (RtsGroups.Army army : RtsGroups.fromLines(lines).armies()) {
                for (String uuid : army.mobs) {
                    try {
                        UUID mob = UUID.fromString(uuid);
                        armyMobs.add(mob);
                        if (army.aggressive) now.put(mob, player.getUUID());
                    } catch (IllegalArgumentException ignored) {
                        // not a UUID
                    }
                }
            }
        }
        aggressive = now;
        inArmy = armyMobs;

        // Speed: on for aggressive mobs, off for ones that aren't any more.
        Set<UUID> stillSped = new HashSet<>();
        for (Map.Entry<UUID, UUID> entry : now.entrySet()) {
            ServerPlayer commander = server.getPlayerList().getPlayer(entry.getValue());
            if (commander == null) continue;
            Entity entity = commander.level().getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive() || !RtsControl.canCommand(mob, commander)) continue;
            KitUtil.modifier(mob, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, SPEED_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true);
            SPED_UP.put(mob.getUUID(), mob);
            stillSped.add(mob.getUUID());
            seekEnemy(mob, commander);
        }
        SPED_UP.entrySet().removeIf(entry -> {
            if (stillSped.contains(entry.getKey())) return false;
            Mob mob = entry.getValue();
            if (!mob.isRemoved()) {
                KitUtil.modifier(mob, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, 0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
            }
            return true;
        });
    }

    /** An idle aggressive mob picks a fight with the closest enemy nearby. */
    private static void seekEnemy(Mob mob, ServerPlayer commander) {
        if (RtsMobOrders.isBusy(mob)) return;
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) return;
        LivingEntity enemy = RtsMobOrders.findEnemy(mob, commander);
        if (enemy != null) KitUtil.provoke(mob, enemy);
    }
}
