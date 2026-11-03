package com.reality.mobcraft.rts;

import com.reality.mobcraft.mob.kit.KitUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.tags.BlockTags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server side: each commanded mob's list of RTS orders, worked through in turn.
 *
 *   Move          walk to the spot. Fights are dropped on the way, the way a
 *                 retreat order works in RTS games.
 *   Attack-move   walk to the spot, but stop to fight any enemy within 10
 *                 blocks along the way, then carry on.
 *   Queued        shift-click adds a spot after the current ones, so mobs
 *                 follow a route.
 *   Hold          stay on this spot. Fights back against what comes close,
 *                 but won't chase more than 6 blocks.
 *   Stop          drop every order and stand still.
 *   Attack        chase one target however far away it is, until it dies.
 *                 Shift-clicking more enemies splits the mobs evenly between
 *                 them (worked out on the client). Each mob keeps the other
 *                 targets as backups: when its own dies, it goes for the
 *                 closest one left.
 *                 Far off, the mob walks toward it in long steps; close up
 *                 it fights with its own AI, and it never loses interest.
 *
 * Endermen teleport instead of walking: up to 32 blocks a jump toward the
 * spot you sent them to, or next to their target. They also teleport if
 * walking close up gets them nowhere. Their own random teleports are turned
 * off while you command them (see RtsHeld).
 *
 * While a mob has any order, its follow range goes up a lot, so it can find
 * paths to far spots and doesn't drop a target just for being far away.
 *
 * After the last spot, or once every attack target is dead, a mob stays on
 * that block until you give it another order. Selecting a mob that has no
 * orders does the same. Only Stop lets Follow Me bring it back. Kept in
 * memory only.
 */
public final class RtsMobOrders {

    public static final int MOVE = 0;
    public static final int ATTACK_MOVE = 1;

    private static final double WALK_SPEED = 1.0;
    private static final double ARRIVED = 1.5;
    private static final double ENEMY_RANGE = 10.0;
    private static final double HOLD_LEASH = 6.0;
    private static final int REPATH_TICKS = 30;
    private static final int GIVE_UP_TRIES = 10;
    private static final double SEGMENT = 24.0;          // long walks are pathed this far at a time
    private static final int STUCK_CHECK_TICKS = 20;
    private static final double STUCK_MOVE = 0.3;        // moved less than this in a second: stuck

    private static final double ATTACK_CLOSE = 12.0;   // inside this, the mob's own AI fights
    private static final double STEP = 32.0;           // far off, walk toward the target this far at a time
    private static final double EXTRA_FOLLOW_RANGE = 96.0;
    private static final String FOLLOW_MODIFIER = "rts_follow_range";
    private static final double ENDER_RANGE = 32.0;

    /** y is the height of the clicked block, or NaN when there isn't one. */
    private record Waypoint(double x, double y, double z, int mode) {}

    private static final class Orders {
        final Mob mob;
        UUID commander;
        final Deque<Waypoint> queue = new ArrayDeque<>();
        boolean hold;
        LivingEntity attackTarget;
        final List<LivingEntity> attackList = new ArrayList<>();
        double holdX;
        double holdZ;
        int lastPath = -1000;
        int tries;
        double lastX = Double.NaN;
        double lastZ;
        int lastStuckCheck;
        int stuck;
        double bestDistance = Double.MAX_VALUE;

        Orders(Mob mob) {
            this.mob = mob;
        }
    }

    private static final Map<UUID, Orders> ORDERS = new HashMap<>();

    private RtsMobOrders() {}

    /** Whether this mob is following RTS orders, so Follow Me leaves it alone. */
    public static boolean isBusy(Mob mob) {
        Orders orders = ORDERS.get(mob.getUUID());
        return orders != null && (orders.hold || orders.attackTarget != null || !orders.queue.isEmpty());
    }

    /** Opens a mob's order list, raising its follow range for as long as it has orders. */
    private static Orders open(Mob mob, ServerPlayer commander) {
        Orders orders = ORDERS.computeIfAbsent(mob.getUUID(), id -> new Orders(mob));
        orders.commander = commander.getUUID();
        KitUtil.modifier(mob, Attributes.FOLLOW_RANGE, FOLLOW_MODIFIER, EXTRA_FOLLOW_RANGE,
                AttributeModifier.Operation.ADD_VALUE, true);
        return orders;
    }

    /** The mob has no orders left: back to its normal follow range. */
    private static void release(Mob mob) {
        KitUtil.modifier(mob, Attributes.FOLLOW_RANGE, FOLLOW_MODIFIER, 0, AttributeModifier.Operation.ADD_VALUE, false);
    }

    /**
     * Chase this target until it dies, however far it is. Replaces every other
     * order, or with queue, adds the target to the mob's attack list.
     */
    public static void attack(Mob mob, ServerPlayer commander, LivingEntity target, boolean queue) {
        Orders orders = open(mob, commander);
        orders.queue.clear();
        orders.hold = false;
        if (queue && orders.attackTarget != null && orders.attackTarget.isAlive()) {
            if (!orders.attackList.contains(target) && orders.attackTarget != target) orders.attackList.add(target);
            return; // keep fighting the current one first
        }
        if (!queue) orders.attackList.clear();
        orders.attackTarget = target;
        orders.tries = 0;
        orders.lastPath = -1000;
        KitUtil.provoke(mob, target);
        chase(orders, KitUtil.now(commander));
    }

    public static void move(Mob mob, ServerPlayer commander, double x, double y, double z, int mode, boolean queue) {
        Orders orders = open(mob, commander);
        if (!queue || orders.hold || orders.attackTarget != null) {
            orders.queue.clear();
            orders.hold = false;
            orders.attackTarget = null;
            orders.attackList.clear();
        }
        boolean wasIdle = orders.queue.isEmpty();
        orders.queue.add(new Waypoint(x, y, z, mode));
        if (wasIdle) {
            if (mode == MOVE) disengage(mob);
            startPath(orders, KitUtil.now(commander));
        }
    }

    /** An attack order replaces whatever the mob was doing. */
    public static void clear(Mob mob) {
        if (ORDERS.remove(mob.getUUID()) != null) release(mob);
    }

    public static void stop(Mob mob) {
        if (ORDERS.remove(mob.getUUID()) != null) release(mob);
        mob.getNavigation().stop();
        disengage(mob);
    }

    public static void hold(Mob mob, ServerPlayer commander) {
        Orders orders = open(mob, commander);
        orders.queue.clear();
        orders.attackTarget = null;
        orders.attackList.clear();
        orders.hold = true;
        orders.holdX = mob.getX();
        orders.holdZ = mob.getZ();
        mob.getNavigation().stop();
        disengage(mob);
    }

    /** Four times a second: walk, fight on attack-moves, hold ground. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0 || ORDERS.isEmpty()) return;
        int now = server.getTickCount();
        Iterator<Orders> it = ORDERS.values().iterator();
        while (it.hasNext()) {
            Orders orders = it.next();
            Mob mob = orders.mob;
            if (mob.isRemoved() || !mob.isAlive()) {
                it.remove();
                continue;
            }
            if (orders.hold) {
                tickHold(orders);
                continue;
            }
            if (orders.attackTarget != null) {
                LivingEntity target = orders.attackTarget;
                if (target.isRemoved() || !target.isAlive() || target.level() != mob.level()) {
                    LivingEntity next = nextTarget(orders);
                    if (next != null) {
                        orders.attackTarget = next;
                        orders.lastPath = -1000;
                        chase(orders, now);
                        continue;
                    }
                    // Everything on the list is gone: stay there until the next order.
                    orders.attackTarget = null;
                    holdHere(orders);
                    continue;
                }
                if (now - orders.lastPath >= 20) chase(orders, now);
                continue;
            }
            Waypoint next = orders.queue.peek();
            if (next == null) {
                // Arrived: stay on this spot until the next order.
                holdHere(orders);
                continue;
            }
            LivingEntity target = mob.getTarget();
            boolean fighting = target != null && target.isAlive();
            if (next.mode() == ATTACK_MOVE || RtsStances.isAggressive(mob)) {
                if (fighting) continue; // finish the fight, then carry on
                ServerPlayer commander = server.getPlayerList().getPlayer(orders.commander);
                LivingEntity enemy = commander == null ? null : findEnemy(mob, commander);
                if (enemy != null) {
                    KitUtil.provoke(mob, enemy);
                    continue;
                }
            } else if (fighting) {
                disengage(mob); // a move order means move
            }

            double dx = next.x() - mob.getX();
            double dz = next.z() - mob.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < ARRIVED) {
                orders.queue.poll();
                orders.tries = 0;
                orders.bestDistance = Double.MAX_VALUE;
                if (!orders.queue.isEmpty()) startPath(orders, now);
                continue;
            }
            // Hardly moved for a second while it should be walking: hop, and step round the side.
            if (now - orders.lastStuckCheck >= STUCK_CHECK_TICKS) {
                orders.lastStuckCheck = now;
                boolean moved = Double.isNaN(orders.lastX)
                        || Math.abs(mob.getX() - orders.lastX) + Math.abs(mob.getZ() - orders.lastZ) > STUCK_MOVE;
                orders.lastX = mob.getX();
                orders.lastZ = mob.getZ();
                if (!moved && mob.getType() != EntityTypes.ENDERMAN) {
                    orders.stuck++;
                    unstick(orders, dx / distance, dz / distance, now);
                    continue;
                }
                orders.stuck = 0;
            }
            if (mob.getNavigation().isDone() || now - orders.lastPath >= REPATH_TICKS) {
                // No closer than last time: count a try, and give up on a spot it can't reach.
                if (distance >= orders.bestDistance - 0.5) orders.tries++;
                orders.bestDistance = Math.min(orders.bestDistance, distance);
                if (mob.getType() == EntityTypes.ENDERMAN && orders.tries >= 2) {
                    // Walking isn't working: teleport there, or 32 blocks closer.
                    double step = Math.min(distance, ENDER_RANGE);
                    if (enderTeleport(mob, mob.getX() + dx / distance * step, mob.getZ() + dz / distance * step)) {
                        orders.tries = 0;
                        orders.bestDistance = Double.MAX_VALUE;
                        startPath(orders, now);
                        continue;
                    }
                }
                if (orders.tries >= GIVE_UP_TRIES) {
                    orders.queue.poll();
                    orders.tries = 0;
                    orders.bestDistance = Double.MAX_VALUE;
                    if (!orders.queue.isEmpty()) startPath(orders, now);
                    continue;
                }
                startPath(orders, now);
            }
        }
    }

    /** The closest target still alive on the mob's attack list, taken off the list. */
    private static LivingEntity nextTarget(Orders orders) {
        Mob mob = orders.mob;
        orders.attackList.removeIf(t -> t.isRemoved() || !t.isAlive() || t.level() != mob.level());
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : orders.attackList) {
            double d = candidate.distanceToSqr(mob);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        if (best != null) orders.attackList.remove(best);
        return best;
    }

    /**
     * Once a second for an attack order. Close up, keeps the mob locked on and
     * lets its own AI fight. Far off, walks it a long step toward the target,
     * since a path all the way there may be too long to find in one go.
     */
    private static void chase(Orders orders, int now) {
        Mob mob = orders.mob;
        LivingEntity target = orders.attackTarget;
        orders.lastPath = now;
        KitUtil.provoke(mob, target);
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        // Commanded endermen teleport next to a far target instead of walking to it.
        if (mob.getType() == EntityTypes.ENDERMAN && distance > ATTACK_CLOSE) {
            double step = Math.min(distance - 2.0, ENDER_RANGE);
            if (enderTeleport(mob, mob.getX() + dx / distance * step, mob.getZ() + dz / distance * step)) return;
        }
        // Close up, endermen that stop getting closer teleport to the target.
        if (mob.getType() == EntityTypes.ENDERMAN) {
            if (distance >= orders.bestDistance - 0.5) {
                orders.tries++;
            } else {
                orders.tries = 0;
            }
            orders.bestDistance = Math.min(orders.bestDistance, distance);
            if (orders.tries >= 3 && distance > 2.5) {
                double step = Math.min(distance - 1.5, ENDER_RANGE);
                if (enderTeleport(mob, mob.getX() + dx / distance * step, mob.getZ() + dz / distance * step)) {
                    orders.tries = 0;
                    orders.bestDistance = Double.MAX_VALUE;
                }
            }
        }
        if (distance <= ATTACK_CLOSE) {
            if (mob.getNavigation().isDone()) mob.getNavigation().moveTo(target, WALK_SPEED);
            return;
        }
        if (distance <= STEP) {
            mob.getNavigation().moveTo(target, WALK_SPEED);
        } else {
            walkTo(mob, mob.getX() + dx / distance * STEP, mob.getZ() + dz / distance * STEP);
        }
    }

    /**
     * An enderman blinks to the ground near a spot, the way it teleports in
     * vanilla: finds safe footing there, with the teleport sound and particles.
     */
    private static boolean enderTeleport(Mob mob, double x, double z) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        BlockPos feet = new BlockPos(Mth.floor(x), top, Mth.floor(z));
        // Endermen are three blocks tall: they need two free blocks above their feet and one more.
        if (!KitUtil.standable(level, feet) || !level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty()) {
            feet = KitUtil.nearestFreeSpot(level, feet, 3);
            if (feet == null) return false;
        }
        double fromX = mob.getX();
        double fromY = mob.getY();
        double fromZ = mob.getZ();
        level.sendParticles(ParticleTypes.PORTAL, fromX, fromY + 1.5, fromZ, 32, 0.4, 1.0, 0.4, 0.2);
        level.playSound(null, fromX, fromY, fromZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
        mob.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        RtsHeld.sent(mob);
        level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1.5, mob.getZ(), 32, 0.4, 1.0, 0.4, 0.2);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
        mob.getNavigation().stop();
        return true;
    }

    /**
     * Keeps a holding mob on its block. It fights back against anything that
     * comes close, but won't chase more than 6 blocks. With nothing to fight
     * it doesn't wander: any stroll is cancelled, and if it's been pushed off
     * its block it walks straight back.
     */
    private static void tickHold(Orders orders) {
        Mob mob = orders.mob;
        LivingEntity target = mob.getTarget();
        if (target != null && target.distanceToSqr(orders.holdX, target.getY(), orders.holdZ) > HOLD_LEASH * HOLD_LEASH) {
            disengage(mob); // don't chase off the spot
            target = null;
        }
        if (target != null && target.isAlive()) return;
        double dx = orders.holdX - mob.getX();
        double dz = orders.holdZ - mob.getZ();
        if (dx * dx + dz * dz > 1.0) {
            if (mob.getNavigation().isDone()) walkTo(mob, orders.holdX, orders.holdZ);
        } else {
            mob.getNavigation().stop(); // no wandering off
        }
    }

    /** From now on the mob stays where it is, until it gets another order. */
    private static void holdHere(Orders orders) {
        orders.queue.clear();
        orders.attackTarget = null;
        orders.attackList.clear();
        orders.hold = true;
        orders.holdX = orders.mob.getX();
        orders.holdZ = orders.mob.getZ();
    }

    /** A mob just got selected: if it has no orders, it stays on its block until it gets one. */
    public static void holdIfIdle(Mob mob, ServerPlayer commander) {
        if (ORDERS.containsKey(mob.getUUID())) return;
        Orders orders = open(mob, commander);
        holdHere(orders);
        mob.getNavigation().stop();
    }

    private static void startPath(Orders orders, int now) {
        Waypoint next = orders.queue.peek();
        if (next == null) return;
        orders.lastPath = now;
        Mob mob = orders.mob;
        if (mob.getType() == EntityTypes.ENDERMAN) {
            // Commanded endermen teleport to the spot, up to 32 blocks a jump, instead of walking.
            double dx = next.x() - mob.getX();
            double dz = next.z() - mob.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < ARRIVED) return;
            double step = Math.min(distance, ENDER_RANGE);
            if (enderTeleport(mob, mob.getX() + dx / distance * step, mob.getZ() + dz / distance * step)) return;
        }
        walkTo(mob, next.x(), next.y(), next.z());
    }

    /**
     * A stuck mob jumps, then walks a few blocks off to one side, switching
     * sides each time, before going back to its real path. Gets mobs round
     * fences, corners and other mobs in the way.
     */
    private static void unstick(Orders orders, double dirX, double dirZ, int now) {
        Mob mob = orders.mob;
        mob.getJumpControl().jump();
        double side = (orders.stuck % 2 == 0 ? 1 : -1) * Math.min(5, 2 + orders.stuck);
        // Perpendicular to the way it's going, plus a little forward.
        double x = mob.getX() - dirZ * side + dirX;
        double z = mob.getZ() + dirX * side + dirZ;
        walkTo(mob, x, Double.NaN, z);
        orders.lastPath = now; // let the side step play out before the normal repath
    }

    private static void walkTo(Mob mob, double x, double z) {
        walkTo(mob, x, Double.NaN, z);
    }

    /**
     * Walks a mob toward a spot with the best path it can find:
     *   Far spots are pathed 24 blocks at a time along the way, since one
     *   path to something very far away is often too long to work out.
     *   The spot is the block you clicked, even in a cave or under a bridge,
     *   or the ground there (ignoring tree leaves) if there's no clicked height.
     *   If no path reaches the spot itself, it tries spots around it, and
     *   failing that takes the path that gets closest.
     */
    private static void walkTo(Mob mob, double x, double yHint, double z) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        double dx = x - mob.getX();
        double dz = z - mob.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double tx = x;
        double tz = z;
        double ty = yHint;
        if (distance > SEGMENT) {
            tx = mob.getX() + dx / distance * SEGMENT;
            tz = mob.getZ() + dz / distance * SEGMENT;
            ty = Double.NaN;
        }
        BlockPos goal = standableNear(level, tx, ty, tz);
        PathNavigation navigation = mob.getNavigation();
        Path direct = navigation.createPath(goal, 1);
        Path best = direct != null && direct.canReach() ? direct : null;
        if (best == null) {
            search:
            for (int r = 1; r <= 3; r++) {
                for (int[] dir : AROUND) {
                    BlockPos around = standableNear(level, goal.getX() + dir[0] * r + 0.5, goal.getY(), goal.getZ() + dir[1] * r + 0.5);
                    Path path = navigation.createPath(around, 1);
                    if (path != null && path.canReach()) {
                        best = path;
                        break search;
                    }
                }
            }
        }
        if (best == null) best = direct; // nothing reaches it: get as close as the path goes
        if (best != null) {
            navigation.moveTo(best, WALK_SPEED);
        } else {
            navigation.moveTo(tx, goal.getY(), tz, WALK_SPEED);
        }
    }

    /** Room to stand, on something that isn't a tree's leaves (a clicked treetop means the ground below). */
    private static boolean groundFor(ServerLevel level, BlockPos feet) {
        return KitUtil.standable(level, feet) && !level.getBlockState(feet.below()).is(BlockTags.LEAVES);
    }

    private static final int[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    /**
     * A block a mob can stand on at x, z: near the clicked height first (up to
     * 4 blocks up or down), otherwise the top ground there, not counting leaves.
     */
    private static BlockPos standableNear(ServerLevel level, double x, double yHint, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!Double.isNaN(yHint)) {
            int y0 = Mth.floor(yHint);
            for (int dy = 0; dy <= 4; dy++) {
                BlockPos up = new BlockPos(bx, y0 + dy, bz);
                if (groundFor(level, up)) return up;
                BlockPos down = new BlockPos(bx, y0 - dy, bz);
                if (dy > 0 && groundFor(level, down)) return down;
            }
        }
        return new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
    }

    /** Drops the mob's current fight. */
    private static void disengage(Mob mob) {
        mob.setTarget(null);
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    /** The closest enemy of the commander this mob can see: other teams' players and hostile mobs. */
    static LivingEntity findEnemy(Mob mob, ServerPlayer commander) {
        LivingEntity best = null;
        double bestDistance = ENEMY_RANGE * ENEMY_RANGE;
        for (LivingEntity entity : mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(ENEMY_RANGE), e -> e != mob && e.isAlive())) {
            if (entity == commander || RtsControl.commandsByOwner(entity, commander)
                    || RtsControl.isOwnKind(entity, commander)) continue; // your side, not an enemy
            if (entity instanceof Player player) {
                if (player.isSpectator() || player.isCreative() || player.isAlliedTo(commander)) continue;
            } else if (!(entity instanceof Enemy)) {
                continue; // leave animals and villagers alone
            }
            double distance = entity.distanceToSqr(mob);
            if (distance < bestDistance && mob.hasLineOfSight(entity)) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }
}
