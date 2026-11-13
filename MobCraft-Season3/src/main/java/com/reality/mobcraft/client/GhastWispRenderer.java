package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reality.mobcraft.item.GhastWisp;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * The thrown Ghast Wisp in flight: its 3D model (an ember wrapped in wind) spins fast round and
 * round like a wind charge, tumbling as it goes, and the ember pulses. Full bright: it's on fire.
 */
public final class GhastWispRenderer extends EntityRenderer<GhastWisp.Thrown, ThrownItemRenderState> {

    private static final float SPIN = 24f;      // degrees a tick round its middle
    private static final float TUMBLE = 9f;     // degrees a tick end over end
    private final ItemModelResolver items;

    public GhastWispRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemModelResolver();
    }

    @Override
    protected int getBlockLightLevel(GhastWisp.Thrown entity, BlockPos pos) {
        return 15;
    }

    @Override
    public ThrownItemRenderState createRenderState() {
        return new ThrownItemRenderState();
    }

    @Override
    public void extractRenderState(GhastWisp.Thrown entity, ThrownItemRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        items.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.GROUND, entity);
    }

    @Override
    public void submit(ThrownItemRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float age = state.ageInTicks;
        float pulse = 1f + 0.08f * Mth.sin(age * 0.9f);
        pose.pushPose();
        pose.translate(0f, 0.15f, 0f);
        pose.rotate(Axis.YP.rotationDegrees(age * SPIN));
        pose.rotate(Axis.XP.rotationDegrees(age * TUMBLE));
        pose.rotate(Axis.ZP.rotationDegrees(Mth.sin(age * 0.35f) * 25f));
        pose.scale(pulse, pulse, pulse);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
