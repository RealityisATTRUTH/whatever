package com.reality.mobcraft.mixin.client;


import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.client.ExplosiveItemGlow;
import com.reality.mobcraft.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import java.util.List;
import com.reality.mobcraft.client.ExplosiveGlowState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A charged item layer: after drawing the item, draws the orange swirl over the same faces (ExplosiveItemGlow). */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemLayerGlowMixin implements ExplosiveGlowState {

    @Shadow @org.spongepowered.asm.mixin.Final private List<BakedQuad> quads;
    @Unique private boolean mobcraft$explosive;
    @Shadow @org.spongepowered.asm.mixin.Final ItemStackRenderState field_55345;

    @Override
    public void mobcraft$markExplosive() {
        mobcraft$explosive = true;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void mobcraft$clear(CallbackInfo ci) {
        mobcraft$explosive = false;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void mobcraft$glow(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (mobcraft$explosive && ExplosiveItemGlow.shows(((ExplosiveGlowState) (Object) field_55345).mobcraft$displayContext()))
            ExplosiveItemGlow.submit(poseStack, new SubmitNodeCollector(buffers), quads);
    }
}
