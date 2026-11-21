package com.reality.mobcraft.mixin;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.rts.RtsMobOrders;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
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
 * shulkers in your group, your other mobs, you. Without this, a group of shulkers
 * shooting at one target hit each other, and the shulker that got hit turned on the one
 * that shot it. Blocks still stop bullets. Wild shulkers' bullets work as normal.
 *
 * When the target dies, the bullets still flying at it pick the next enemy and home in
 * on that instead of dropping to the ground. The bullet's saved target is changed too,
 * so it keeps the new one through a save. See RtsMobOrders.nextBulletTarget.
 */
@Mixin(ShulkerBullet.class)
public abstract class ShulkerBulletAimMixin {

    @Shadow
    private EntityReference<Entity> finalTarget;

    @Shadow
    private void selectNextMoveDirection(Direction.Axis avoidAxis, Entity target) {}

    @Unique
    private static boolean mobcraft$ourSide(Entity owner) {
        return owner instanceof Player
                || (owner instanceof Mob mob && (mob.hasAttached(MobAttachments.OWNER) || RtsMobOrders.isBusy(mob)));
    }

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void mobcraft$onlyItsTarget(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Entity owner = ((ShulkerBullet) (Object) this).getOwner();
        if (!mobcraft$ourSide(owner)) return;
        if (finalTarget == null || !finalTarget.getUUID().equals(entity.getUUID())) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mobcraft$retarget(CallbackInfo ci) {
        ShulkerBullet self = (ShulkerBullet) (Object) this;
        if (self.level().isClientSide()) return;
        Entity owner = self.getOwner();
        if (!mobcraft$ourSide(owner)) return;
        Entity current = EntityReference.getEntity(finalTarget, self.level());
        boolean gone = current == null || !current.isAlive() || (current instanceof Player p && p.isSpectator());
        if (!gone) return;
        // Looking for a new enemy costs a search, so a bullet with nobody left looks every 4 ticks.
        if (finalTarget == null && self.tickCount % 4 != 0) return;
        LivingEntity next = RtsMobOrders.nextBulletTarget(owner, self);
        if (next == null) {
            finalTarget = null;
            return;
        }
        finalTarget = EntityReference.of(next);
        selectNextMoveDirection(null, next);
    }
}
