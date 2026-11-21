package com.reality.mobcraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reality.mobcraft.client.TomeRenderer;
import com.reality.mobcraft.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reading the Stronghold Tome in first person: like holding a map, both your hands come up and hold
 * the book open in front of you, the pages facing you. Uses the game's own two-handed map pose for the
 * hands, so they look like your normal hands, and draws the book between them (TomeRenderer.submitHeld).
 * While the book is shut it's just held in one hand as usual.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class TomeHandsMixin {

    @Invoker("renderMapHand")
    abstract void mobcraft$mapHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm, PlayerRenderState player);

    @Invoker("calculateMapTilt")
    abstract float mobcraft$mapTilt(float pitch);

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void mobcraft$readTome(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, float partialTick,
                                   float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
                                   PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (!stack.is(ModItems.STRONGHOLD_TOME)) return;
        LivingEntity me = Minecraft.getInstance().player;
        if (me == null) return;
        float open = TomeRenderer.openness(me);
        if (open <= 0f) return;
        ci.cancel();
        if (hand != InteractionHand.MAIN_HAND && TomeRenderer.reading(me) && me.getUsedItemHand() != hand) return;
        poseStack.pushPose();
        // The two-handed map pose, rising in as the book opens.
        float s = Mth.sqrt(swing);
        float y = -0.2f * Mth.sin(swing * Mth.PI);
        float z = -0.4f * Mth.sin(s * Mth.PI);
        poseStack.translate(0f, -y / 2f, z);
        float tilt = mobcraft$mapTilt(pitch);
        poseStack.translate(0f, 0.16f + equip * -1.2f + tilt * -0.5f - (1f - open) * 0.6f, -0.72f);
        poseStack.rotateDegrees(Axis.XP, tilt * -85f);
        if (!player.avatarRenderState.isInvisible) {
            poseStack.pushPose();
            poseStack.rotateDegrees(Axis.YP, 90f);
            mobcraft$mapHand(poseStack, collector, light, HumanoidArm.RIGHT, player);
            mobcraft$mapHand(poseStack, collector, light, HumanoidArm.LEFT, player);
            poseStack.popPose();
        }
        // The book between the hands, where a map's page would be, tipped back so you look down into it.
        float sway = Mth.sin(s * Mth.PI);
        poseStack.rotateDegrees(Axis.XP, sway * 20f);
        poseStack.translate(0f, -0.2f, 0.02f);
        poseStack.rotateDegrees(Axis.YP, 180f);
        poseStack.rotateDegrees(Axis.XP, -15f);
        poseStack.scale(0.78f, 0.78f, 0.78f);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        TomeRenderer.submitHeld(me, poseStack, collector, light);
        poseStack.popPose();
    }
}
