package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobKits;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.rts.RtsControl;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared pieces for the ultimates:
 *
 *   later      runs something a few ticks from now, for ultimates that come in
 *              waves. Skipped if the player has died or left by then.
 *   temporary  mobs an ultimate summons go away on their own after a while.
 *              If the server stops first, they're removed then.
 *   enemies    who an ultimate hits: other players not on your team, hostile
 *              mobs, and mobs other players own. Never your teammates, your
 *              own mobs, your mob's allies, or players in creative.
 *   allies     you and your teammates nearby, for ultimates that buff.
 */
public final class UltimateFx {

    private record Task(int at, UUID player, Consumer<ServerPlayer> run) {}

    private record Temp(Entity entity, int at) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Temp> TEMPS = new ArrayList<>();

    private UltimateFx() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UltimateFx::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (Temp temp : TEMPS) temp.entity().discard();
            TEMPS.clear();
            TASKS.clear();
        });
    }

    /** Runs this in the given ticks with the player, if they're still alive and online. */
    public static void later(ServerPlayer player, int ticks, Consumer<ServerPlayer> run) {
        TASKS.add(new Task(KitUtil.now(player) + Math.max(1, ticks), player.getUUID(), run));
    }

    /** This entity goes away after the given ticks. */
    public static void temporary(Entity entity, int ticks) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return;
        TEMPS.add(new Temp(entity, level.getServer().getTickCount() + ticks));
    }

    private static void tick(MinecraftServer s) {
        int now = s.getTickCount();
        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task task = it.next();
                if (now < task.at()) continue;
                it.remove();
                due.add(task);
            }
            for (Task task : due) {
                ServerPlayer player = s.getPlayerList().getPlayer(task.player());
                if (player == null || !player.isAlive()) continue;
                task.run().accept(player);
            }
        }
        if (!TEMPS.isEmpty()) {
            Iterator<Temp> it = TEMPS.iterator();
            while (it.hasNext()) {
                Temp temp = it.next();
                if (temp.entity().isRemoved()) {
                    it.remove();
                } else if (now >= temp.at()) {
                    temp.entity().discard();
                    it.remove();
                }
            }
        }
    }

    // --- Who gets hit ------------------------------------------------------------------

    public static boolean isEnemy(ServerPlayer player, Entity entity) {
        if (entity == player || !(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        if (entity instanceof ArmorStand || entity.isAlliedTo(player)) return false;
        if (entity instanceof Player other) return !other.isCreative() && !other.isSpectator();
        if (!(entity instanceof Mob mob)) return false;
        if (RtsControl.canCommand(mob, player)) return false;
        String owner = mob.getAttached(MobAttachments.OWNER);
        if (owner != null) {
            ServerPlayer ownerPlayer = ownerOf(player, owner);
            return ownerPlayer == null || !ownerPlayer.isAlliedTo(player);
        }
        MobType type = PlayerMob.get(player);
        if (type != null && MobKits.get(type).allies().contains(mob.getType())) return false;
        return mob instanceof Enemy;
    }

    private static ServerPlayer ownerOf(ServerPlayer player, String uuid) {
        try {
            return player.level().getServer().getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Enemies within this many blocks of the player. */
    public static List<LivingEntity> enemies(ServerPlayer player, double radius) {
        return enemiesAround(player, player.position(), radius);
    }

    /** Enemies within this many blocks of a spot. */
    public static List<LivingEntity> enemiesAround(ServerPlayer player, Vec3 center, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius),
                e -> isEnemy(player, e) && e.position().distanceTo(center) <= radius);
    }

    /** The player and teammates within this many blocks. */
    public static List<ServerPlayer> allies(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(radius),
                p -> p.isAlive() && !p.isSpectator() && (p == player || p.isAlliedTo(player))
                        && p.distanceTo(player) <= radius);
    }

    /** Throws something away from a spot, flat speed outward plus some lift. */
    public static void throwFrom(LivingEntity target, Vec3 from, double outward, double up) {
        Vec3 away = target.position().subtract(from);
        Vec3 flat = new Vec3(away.x, 0, away.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
        target.setDeltaMovement(flat.x * outward, up, flat.z * outward);
        KitUtil.syncMotion(target);
    }

    /** A flat direction around a circle, for rings of projectiles. */
    public static Vec3 around(int i, int count, double dy) {
        double angle = Math.PI * 2 * i / count;
        return new Vec3(Math.cos(angle), dy, Math.sin(angle)).normalize();
    }

    /** Summons a mob for an ultimate that goes away after the given ticks. */
    public static Mob summonFor(ServerPlayer player, String id, double x, double y, double z, String nbt, int ticks) {
        Mob mob = KitUtil.summon(player, id, x, y, z, nbt);
        temporary(mob, ticks);
        return mob;
    }
}
