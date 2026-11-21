package com.reality.mobcraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reality.mobcraft.item.Tiers;
import com.reality.mobcraft.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Backwards troll tier: other players (and you in third person) see the tool held upside down. */
@Mixin(ItemInHandLayer.class)
public abstract class BackwardsItemMixin {

    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void mobcraft$backwards(ItemStackRenderState item, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay,
                                    Operation<Void> original) {
        if (!((com.reality.mobcraft.client.ExplosiveGlowState) item).mobcraft$backwards()) {
            original.call(item, pose, buffers, light, overlay);
            return;
        }
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(180f));   // a reverse grip: the blade points back and down
        original.call(item, pose, buffers, light, overlay);
        pose.popPose();
    }
}
