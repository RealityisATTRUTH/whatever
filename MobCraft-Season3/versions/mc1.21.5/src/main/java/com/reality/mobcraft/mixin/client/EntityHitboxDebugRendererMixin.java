package com.reality.mobcraft.mixin.client;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws the dual-hitbox attack box next to the vanilla F3+B box (1.21.9 and 1.21.10).
 *
 * These versions collect each entity's F3+B boxes into a list when the entity is gathered up to draw. One more box is
 * added to that list: the normal-sized attack box, in green. The real small box stays white.
 */
@Mixin(EntityRenderer.class)
public class EntityHitboxDebugRendererMixin {
    @ModifyReturnValue(method = "extractHitboxes(Lnet/minecraft/world/entity/Entity;FZ)Lnet/minecraft/client/renderer/entity/state/HitboxesRenderState;", at = @At("RETURN"))
    private HitboxesRenderState mobcraft$addAttackBox(HitboxesRenderState boxes, Entity entity, float partialTick, boolean green) {
        if (boxes == null || !SmallMobHitbox.isSmall(entity)) return boxes;
        AABB box = SmallMobHitbox.inflateToNormal(entity, entity.getBoundingBox());
        ImmutableList<HitboxRenderState> more = ImmutableList.<HitboxRenderState>builder().addAll(boxes.hitboxes())
                .add(new HitboxRenderState(box.minX - entity.getX(), box.minY - entity.getY(), box.minZ - entity.getZ(),
                        box.maxX - entity.getX(), box.maxY - entity.getY(), box.maxZ - entity.getZ(), 0f, 1f, 0f))
                .build();
        return new HitboxesRenderState(boxes.viewX(), boxes.viewY(), boxes.viewZ(), more);
    }
}
