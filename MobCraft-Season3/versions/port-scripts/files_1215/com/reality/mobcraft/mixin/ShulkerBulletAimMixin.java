package com.reality.mobcraft.mixin;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.rts.RtsMobOrders;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shulker bullets on your side.
 *
 * They only hit the enemy they're homing on. A bullet fired by a shulker you command
 * in RTS, or by a Shulker player, flies past everything else in its way: the other
 * shulkers in your group, your other mobs, you. Blocks still stop bullets. Wild
 * shulkers' bullets work as normal.
 *
 * When the target dies, the bullets still flying at it pick the next enemy and home in
 * on that instead of dropping to the ground. See RtsMobOrders.nextBulletTarget.
 *
 * 1.21.5: the bullet keeps its target as an entity plus its UUID for saving.
 */
@Mixin(ShulkerBullet.class)
public abstract class ShulkerBulletAimMixin {

    @Shadow private Entity finalTarget;
    @Shadow private UUID targetId;

    @Shadow
    private void selectNextMoveDirection(Direction.Axis avoidAxis) {}

    @Unique
    private static boolean mobcraft$ourSide(Entity owner) {
        return owner instanceof Player
                || (owner instanceof Mob mob && (mob.hasAttached(MobAttachments.OWNER) || RtsMobOrders.isBusy(mob)));
    }

    @Unique
    private UUID mobcraft$targetUuid() {
        return finalTarget != null ? finalTarget.getUUID() : targetId;
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void mobcraft$onlyItsTarget(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Entity owner = ((ShulkerBullet) (Object) this).getOwner();
        if (!mobcraft$ourSide(owner)) return;
        UUID target = mobcraft$targetUuid();
        if (target == null || !target.equals(entity.getUUID())) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mobcraft$retarget(CallbackInfo ci) {
        ShulkerBullet self = (ShulkerBullet) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) return;
        Entity owner = self.getOwner();
        if (!mobcraft$ourSide(owner)) return;
        Entity current = finalTarget != null ? finalTarget : targetId == null ? null : level.getEntity(targetId);
        boolean gone = current == null || !current.isAlive() || (current instanceof Player p && p.isSpectator());
        if (!gone) return;
        // Looking for a new enemy costs a search, so a bullet with nobody left looks every 4 ticks.
        if (targetId == null && self.tickCount % 4 != 0) return;
        LivingEntity next = RtsMobOrders.nextBulletTarget(owner, self);
        if (next == null) {
            finalTarget = null;
            targetId = null;
            return;
        }
        finalTarget = next;
        targetId = next.getUUID();
        selectNextMoveDirection(null);
    }
}
