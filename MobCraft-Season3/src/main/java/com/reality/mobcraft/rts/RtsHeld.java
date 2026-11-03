package com.reality.mobcraft.rts;

import com.reality.mobcraft.MobCraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server side: endermen under your command only teleport where you send them.
 *
 * An enderman is held while any of these is true:
 *   it's selected in someone's RTS view,
 *   it's in one of someone's armies,
 *   it has RTS orders it's still carrying out.
 * While held, its own random teleports are refused (being hit, rain, water,
 * dodging arrows). Deselecting it, disbanding its army and its orders running
 * out let it teleport normally again. Its RTS teleports (RtsMobOrders) still
 * work, since they don't go through the random teleport.
 *
 * Two layers stop the random teleports:
 *   Mixin       HeldEndermanMixin refuses them before they happen.
 *   Safety net  every tick, a held enderman that jumped more than 6 blocks
 *               without an RTS order sending it is put straight back. If
 *               this ever catches one, the log says so once, which means the
 *               mixin isn't working on this version of the game.
 */
public final class RtsHeld {

    /** Player -> the mobs they have selected. */
    private static final Map<UUID, Set<UUID>> SELECTED = new HashMap<>();

    private static final double JUMP = 6.0;
    private static final double RANGE = 128.0;

    private record Spot(Level level, Vec3 pos) {}

    /** Where each held enderman was last tick. */
    private static final Map<Mob, Spot> LAST = new WeakHashMap<>();
    /** Endermen an RTS order teleported this tick, so the safety net lets them go. */
    private static final Set<Mob> SENT = new HashSet<>();
    private static boolean warned;

    private RtsHeld() {}

    /** RtsMobOrders calls this when it teleports an enderman on purpose. */
    public static void sent(Mob mob) {
        SENT.add(mob);
    }

    /** The safety net. Runs after RtsMobOrders each tick. */
    public static void tick(MinecraftServer server) {
        Set<Mob> seen = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.level();
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                    m -> m.getType() == EntityTypes.ENDERMAN && m.isAlive())) {
                if (!seen.add(mob) || !isHeldEnderman(mob)) continue;
                Spot last = LAST.get(mob);
                boolean jumped = last != null && last.level() == level && last.pos().distanceTo(mob.position()) > JUMP;
                if (jumped && !SENT.contains(mob)) {
                    level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1.5, mob.getZ(), 16, 0.4, 1.0, 0.4, 0.2);
                    mob.teleportTo(last.pos().x, last.pos().y, last.pos().z);
                    mob.getNavigation().stop();
                    if (!warned) {
                        warned = true;
                        MobCraft.LOGGER.warn("RTS: a held enderman teleported by itself and was put back. "
                                + "HeldEndermanMixin isn't stopping it on this version, the safety net is.");
                    }
                }
                LAST.put(mob, new Spot(level, mob.position()));
            }
        }
        LAST.keySet().retainAll(seen);
        SENT.clear();
    }

    /** The client's selection, as entity ids. Only mobs that player can command count. */
    public static void setSelection(ServerPlayer player, List<Integer> ids) {
        if (ids.size() > 300) return;
        Set<UUID> mobs = new HashSet<>();
        for (int id : ids) {
            Entity entity = player.level().getEntity(id);
            if (entity instanceof Mob mob && RtsControl.canCommand(mob, player)) {
                mobs.add(mob.getUUID());
                RtsMobOrders.holdIfIdle(mob, player); // selected: it stays on its block
            }
        }
        if (mobs.isEmpty()) {
            SELECTED.remove(player.getUUID());
        } else {
            SELECTED.put(player.getUUID(), mobs);
        }
    }

    /** RTS closed or the player left: nothing selected. */
    public static void clear(ServerPlayer player) {
        SELECTED.remove(player.getUUID());
    }

    private static boolean selected(UUID mob) {
        for (Set<UUID> mobs : SELECTED.values()) if (mobs.contains(mob)) return true;
        return false;
    }

    /** Whether this mob is an enderman someone is commanding, so it mustn't teleport by itself. */
    public static boolean isHeldEnderman(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.getType() != EntityTypes.ENDERMAN || mob.level().isClientSide()) return false;
        UUID id = mob.getUUID();
        return selected(id) || RtsStances.isInArmy(id) || RtsMobOrders.isBusy(mob);
    }
}
