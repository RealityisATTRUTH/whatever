package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the dual-hitbox attack box next to the vanilla F3+B box.
 *
 * Since 1.21.11, vanilla draws entity hitboxes in EntityHitboxDebugRenderer
 * (not EntityRenderDispatcher), using the Gizmos system. We hook the end of
 * showHitboxes and add one more cuboid with the same Gizmos call vanilla uses,
 * so the green box has the exact same line style as the standard hitbox.
 *
 * With F3+B on you see:
 *   white = the real small collision box (vanilla, untouched)
 *   green = the normal-sized attack box (what hit detection uses)
 *
 * The player model is not touched. Only a wireframe is added.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {

    /** Opaque green, ARGB. */
    private static final int ATTACK_BOX_COLOR = 0xFF00FF00;

    @Inject(method = "showHitboxes(Lnet/minecraft/world/entity/Entity;FZ)V", at = @At("TAIL"))
    private void mobcraft$drawAttackBox(Entity entity, float partialTick, boolean serverEntity, CallbackInfo ci) {
        // Vanilla can also draw the integrated server's copy of an entity.
        // Only draw on the client copy so the green box isn't doubled.
        if (serverEntity) return;
        if (!SmallMobHitbox.isSmall(entity)) return;

        // Same smoothing vanilla uses: shift by how far the entity has moved
        // since the last tick, so the box doesn't jitter behind the model.
        Vec3 offset = entity.getPosition(partialTick).subtract(entity.position());
        AABB attackBox = SmallMobHitbox.inflateToNormal(entity, entity.getBoundingBox()).move(offset);

        Gizmos.cuboid(attackBox, GizmoStyle.stroke(ATTACK_BOX_COLOR));
    }
}
