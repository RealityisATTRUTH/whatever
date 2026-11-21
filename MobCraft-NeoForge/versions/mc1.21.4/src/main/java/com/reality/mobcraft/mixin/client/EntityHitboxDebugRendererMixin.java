package com.reality.mobcraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the dual-hitbox attack box next to the vanilla F3+B box (1.21.4).
 * After the game draws an entity's white box, the normal-sized attack box is drawn in green.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityHitboxDebugRendererMixin {
    @Inject(method = "renderHitbox", at = @At("TAIL"))
    private static void mobcraft$addAttackBox(PoseStack pose, VertexConsumer buffer, Entity entity, float partialTick,
                                             float r, float g, float b, CallbackInfo ci) {
        if (!SmallMobHitbox.isSmall(entity)) return;
        AABB box = SmallMobHitbox.inflateToNormal(entity, entity.getBoundingBox()).move(-entity.getX(), -entity.getY(), -entity.getZ());
        ShapeRenderer.renderLineBox(pose, buffer, box, 0f, 1f, 0f, 1f);
    }
}
