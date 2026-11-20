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

    @Shadow net.minecraft.client.resources.model.BakedModel model;
    @Unique private boolean mobcraft$explosive;
    @Shadow @org.spongepowered.asm.mixin.Final ItemStackRenderState field_55345;

    /** Every face of the layer's model, as the game draws them. */
    @Unique
    private List<BakedQuad> mobcraft$quads() {
        if (model == null) return List.of();
        List<BakedQuad> out = new java.util.ArrayList<>();
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            random.setSeed(42L);
            out.addAll(model.getQuads(null, d, random));
        }
        random.setSeed(42L);
        out.addAll(model.getQuads(null, null, random));
        return out;
    }

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
            ExplosiveItemGlow.submit(poseStack, new SubmitNodeCollector(buffers), mobcraft$quads());
    }
}
