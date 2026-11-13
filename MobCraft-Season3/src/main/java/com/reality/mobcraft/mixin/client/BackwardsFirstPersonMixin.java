package com.reality.mobcraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reality.mobcraft.item.Tiers;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Backwards troll tier in first person: you see your own tool upside down too. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class BackwardsFirstPersonMixin {

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void mobcraft$backwards(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline,
                                    Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        if (!Tiers.is(stack, Tiers.Kind.TOOL, "backwards")) {
            original.call(item, pose, collector, light, overlay, outline);
            return;
        }
        pose.pushPose();
        pose.rotateDegrees(Axis.XP, 180f);   // a reverse grip: the blade points down
        original.call(item, pose, collector, light, overlay, outline);
        pose.popPose();
    }
}
