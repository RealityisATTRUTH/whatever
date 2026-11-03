package com.reality.mobcraft.client.rts;

import com.reality.mobcraft.client.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client-side particle arrows showing where your mobs are going. Only your own
 * game draws them, so nobody else sees where you're sending your army.
 *
 *   Move          a green dotted line from each mob to its spot, with an
 *                 arrowhead and a ring on the spot. Queued spots chain on.
 *   Attack-move   the same in orange.
 *   Attack        a red line from each mob to its target, while it lives.
 *
 * A spot disappears once its mob gets there. Everything fades after a minute.
 * Drawn as always-visible particles, so they still show with the RTS camera
 * zoomed far out.
 */
public final class RtsMarkers {

    private static final DustParticleOptions MOVE = new DustParticleOptions(0xFF40FF40, 0.8f);
    private static final DustParticleOptions ATTACK_MOVE = new DustParticleOptions(0xFFFF9020, 0.8f);
    private static final DustParticleOptions ATTACK = new DustParticleOptions(0xFFFF3030, 0.8f);

    private static final double DOT_SPACING = 0.8;
    private static final int MAX_DOTS_PER_LINE = 40;
    private static final double ARRIVED = 1.8;
    private static final int LIFETIME_TICKS = 1200;    // a minute
    private static final int ATTACK_LINE_TICKS = 120;  // 6 seconds
    private static final int DRAW_EVERY = 4;

    private record Spot(double x, double y, double z, boolean attackMove) {}

    private static final class Path {
        final List<Spot> spots = new ArrayList<>();
        int ticksLeft = LIFETIME_TICKS;
    }

    private static final class AttackLine {
        final int targetId;
        int ticksLeft = ATTACK_LINE_TICKS;

        AttackLine(int targetId) {
            this.targetId = targetId;
        }
    }

    /** Mob entity id -> its spots. */
    private static final Map<Integer, Path> PATHS = new HashMap<>();
    /** Mob entity id -> what it was told to attack. */
    private static final Map<Integer, AttackLine> ATTACKS = new HashMap<>();
    private static int tickCounter;

    private RtsMarkers() {}

    /** A move or attack-move order for one mob. Queued spots add on, others replace. */
    public static void addSpot(int mobId, double x, double z, boolean attackMove, boolean queue) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        Path path = queue ? PATHS.computeIfAbsent(mobId, id -> new Path()) : new Path();
        path.spots.add(new Spot(x, y, z, attackMove));
        path.ticksLeft = LIFETIME_TICKS;
        PATHS.put(mobId, path);
        ATTACKS.remove(mobId);
    }

    public static void attack(int mobId, int targetId) {
        PATHS.remove(mobId);
        ATTACKS.put(mobId, new AttackLine(targetId));
    }

    /** Stop or Hold: nothing left to show. */
    public static void clear(int mobId) {
        PATHS.remove(mobId);
        ATTACKS.remove(mobId);
    }

    /** Called every client tick. */
    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            PATHS.clear();
            ATTACKS.clear();
            return;
        }
        boolean draw = ++tickCounter % DRAW_EVERY == 0 && HudConfig.rts(HudConfig.RtsPart.ARROWS);

        Iterator<Map.Entry<Integer, Path>> paths = PATHS.entrySet().iterator();
        while (paths.hasNext()) {
            Map.Entry<Integer, Path> entry = paths.next();
            Path path = entry.getValue();
            Entity mob = level.getEntity(entry.getKey());
            if (mob == null || !mob.isAlive() || --path.ticksLeft <= 0) {
                paths.remove();
                continue;
            }
            Spot first = path.spots.get(0);
            double dx = first.x() - mob.getX();
            double dz = first.z() - mob.getZ();
            if (dx * dx + dz * dz < ARRIVED * ARRIVED) {
                path.spots.remove(0);
                if (path.spots.isEmpty()) {
                    paths.remove();
                    continue;
                }
            }
            if (!draw) continue;
            Vec3 from = new Vec3(mob.getX(), mob.getY() + 0.2, mob.getZ());
            for (int i = 0; i < path.spots.size(); i++) {
                Spot spot = path.spots.get(i);
                Vec3 to = new Vec3(spot.x(), spot.y() + 0.2, spot.z());
                DustParticleOptions color = spot.attackMove() ? ATTACK_MOVE : MOVE;
                line(level, from, to, color);
                arrowhead(level, from, to, color);
                if (i == path.spots.size() - 1) ring(level, to, color);
                from = to;
            }
        }

        Iterator<Map.Entry<Integer, AttackLine>> attacks = ATTACKS.entrySet().iterator();
        while (attacks.hasNext()) {
            Map.Entry<Integer, AttackLine> entry = attacks.next();
            AttackLine line = entry.getValue();
            Entity mob = level.getEntity(entry.getKey());
            Entity target = level.getEntity(line.targetId);
            if (mob == null || target == null || !mob.isAlive() || !target.isAlive() || --line.ticksLeft <= 0) {
                attacks.remove();
                continue;
            }
            if (!draw) continue;
            Vec3 from = new Vec3(mob.getX(), mob.getY() + 0.5, mob.getZ());
            double height = target instanceof LivingEntity living ? living.getBbHeight() * 0.5 : 0.5;
            Vec3 to = new Vec3(target.getX(), target.getY() + height, target.getZ());
            line(level, from, to, ATTACK);
            arrowhead(level, from, to, ATTACK);
        }
    }

    private static void line(ClientLevel level, Vec3 from, Vec3 to, DustParticleOptions color) {
        Vec3 step = to.subtract(from);
        double length = step.length();
        if (length < 0.01) return;
        int dots = Math.min(MAX_DOTS_PER_LINE, (int) (length / DOT_SPACING));
        // The dots slide along the line over time, so it reads as moving toward the spot.
        double offset = (tickCounter % 12) / 12.0;
        for (int i = 0; i < dots; i++) {
            double t = (i + offset) / Math.max(1, dots);
            Vec3 p = from.add(step.scale(t));
            level.addAlwaysVisibleParticle(color, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    /** Two short strokes angled back from the spot, like the head of an arrow. */
    private static void arrowhead(ClientLevel level, Vec3 from, Vec3 to, DustParticleOptions color) {
        Vec3 dir = new Vec3(to.x - from.x, 0, to.z - from.z);
        if (dir.lengthSqr() < 0.01) return;
        dir = dir.normalize();
        for (double side : new double[]{-1, 1}) {
            double angle = Math.toRadians(150) * side;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 back = new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
            for (int i = 1; i <= 4; i++) {
                Vec3 p = to.add(back.scale(i * 0.25));
                level.addAlwaysVisibleParticle(color, p.x, p.y, p.z, 0, 0, 0);
            }
        }
    }

    private static void ring(ClientLevel level, Vec3 at, DustParticleOptions color) {
        double spin = tickCounter * 0.15;
        for (int i = 0; i < 10; i++) {
            double angle = spin + i * (Math.PI * 2.0 / 10);
            level.addAlwaysVisibleParticle(color, at.x + Math.cos(angle) * 0.6, at.y, at.z + Math.sin(angle) * 0.6, 0, 0, 0);
        }
    }
}
