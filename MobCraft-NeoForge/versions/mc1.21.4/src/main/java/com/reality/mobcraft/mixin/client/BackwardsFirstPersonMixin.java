package com.reality.mobcraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reality.mobcraft.item.Tiers;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.reality.mobcraft.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Backwards troll tier in first person: you see your own tool upside down too. */
@Mixin(ItemInHandRenderer.class)
public abstract class BackwardsFirstPersonMixin {

    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V"))
    private void mobcraft$backwards(net.minecraft.client.renderer.entity.ItemRenderer renderer, net.minecraft.world.entity.LivingEntity entity,
                                    ItemStack stack, net.minecraft.world.item.ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                    net.minecraft.client.renderer.MultiBufferSource buffers, net.minecraft.world.level.Level level,
                                    int light, int overlay, int seed, Operation<Void> original) {
        if (!Tiers.is(stack, Tiers.Kind.TOOL, "backwards")) {
            original.call(renderer, entity, stack, context, leftHand, pose, buffers, level, light, overlay, seed);
            return;
        }
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(180f));   // a reverse grip: the blade points down
        original.call(renderer, entity, stack, context, leftHand, pose, buffers, level, light, overlay, seed);
        pose.popPose();
    }
}
