package com.reality.mobcraft.rts;

import com.reality.mobcraft.mob.kit.Confusion;
import com.reality.mobcraft.mob.kit.KitUtil;
import com.reality.mobcraft.net.AttackOrderPayload;
import com.reality.mobcraft.net.MoveOrderPayload;
import com.reality.mobcraft.net.RtsCommandPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Server side of RTS orders: moving, attacking, Stop and Hold. RtsMobOrders carries them out. */
public final class RtsOrders {

    // No distance limit: you can order any mob you command, anywhere your game has it loaded.
    private static final int MAX_MOBS_PER_ORDER = 200;

    private RtsOrders() {}

    /** Each mob walks to its own spot of the formation the client worked out. */
    public static void move(ServerPlayer player, MoveOrderPayload order) {
        if (Confusion.isConfused(player)) {
            KitUtil.actionBar(player, "You're confused. Your orders don't get through.");
            return;
        }
        int count = order.entityIds().size();
        if (order.xs().size() != count || order.ys().size() != count || order.zs().size() != count
                || count > MAX_MOBS_PER_ORDER) return;
        int mode = order.mode() == RtsMobOrders.ATTACK_MOVE ? RtsMobOrders.ATTACK_MOVE : RtsMobOrders.MOVE;
        Level level = player.level();
        int moved = 0;
        for (int i = 0; i < count; i++) {
            double x = order.xs().get(i);
            double z = order.zs().get(i);
            Entity entity = level.getEntity(order.entityIds().get(i));
            if (!(entity instanceof Mob mob)) continue;
            if (!RtsControl.canCommand(mob, player)) continue; // never move a mob you can't command
            if (Confusion.isConfused(mob)) continue; // confused mobs keep their last order
            RtsMobOrders.move(mob, player, x, order.ys().get(i), z, mode, order.queue());
            MobBehavior.markOrdered(mob, KitUtil.now(player));
            moved++;
        }
        if (moved >= 20) com.reality.mobcraft.MobAdvancements.award(player, "legion");
    }

    /** Stop or Hold. */
    public static void command(ServerPlayer player, RtsCommandPayload command) {
        if (Confusion.isConfused(player)) {
            KitUtil.actionBar(player, "You're confused. Your orders don't get through.");
            return;
        }
        if (command.entityIds().size() > MAX_MOBS_PER_ORDER) return;
        Level level = player.level();
        for (int id : command.entityIds()) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || !RtsControl.canCommand(mob, player)) continue;
            if (Confusion.isConfused(mob)) continue;
            if (command.command() == RtsCommandPayload.HOLD) {
                RtsMobOrders.hold(mob, player);
            } else {
                RtsMobOrders.stop(mob);
                MobBehavior.markOrdered(mob, KitUtil.now(player));
            }
        }
    }

    /**
     * Sends mobs after a target. Not yourself, not a mob on your side, and not
     * a teammate. The mobs chase it however far it is, until it dies.
     */
    public static void attack(ServerPlayer player, AttackOrderPayload order) {
        if (Confusion.isConfused(player)) {
            KitUtil.actionBar(player, "You're confused. Your orders don't get through.");
            return;
        }
        Level level = player.level();
        if (!(level.getEntity(order.targetId()) instanceof LivingEntity target) || !target.isAlive()) return;
        if (target == player || RtsControl.canCommand(target, player)) return;
        if (target instanceof Player other && other.isAlliedTo(player)) {
            KitUtil.actionBar(player, "You can't send your mobs after a teammate.");
            return;
        }
        int sent = 0;
        for (int id : order.entityIds()) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob) || mob == target) continue;
            if (!RtsControl.canCommand(mob, player)) continue;
            if (Confusion.isConfused(mob)) continue;
            RtsMobOrders.attack(mob, player, target, order.queue());
            MobBehavior.markOrdered(mob, KitUtil.now(player));
            sent++;
        }
        // Backup targets from a split attack come as queued orders: no message for those.
        if (sent >= 20) com.reality.mobcraft.MobAdvancements.award(player, "legion");
        if (sent > 0 && !order.queue()) {
            KitUtil.actionBar(player, sent + (sent == 1 ? " mob is" : " mobs are") + " attacking.");
        }
    }
}
