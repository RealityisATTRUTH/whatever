package com.reality.mobcraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Stands in for the game's SubmitNodeCollector from 1.21.9 on. 1.21.8 has no draw queue: everything is drawn
 * right away into a MultiBufferSource, so each call here draws straight into the buffers it was made with.
 */
public final class SubmitNodeCollector {
    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) { this.buffers = buffers; }

    public MultiBufferSource buffers() { return buffers; }

    /** 1.21.9 sorts submissions into ordered groups; drawn right away, the order is the call order. */
    public SubmitNodeCollector order(int order) { return this; }

    @FunctionalInterface
    public interface CustomGeometryRenderer { void render(PoseStack.Pose pose, VertexConsumer buffer); }

    public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer renderer) {
        renderer.render(pose.last(), buffers.getBuffer(type));
    }

    public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite) {
        VertexConsumer buffer = buffers.getBuffer(type);
        part.render(pose, sprite == null ? buffer : sprite.wrap(buffer), light, overlay);
    }

    public void submitModelPart(ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite,
                                int color, Object crumbling) {
        VertexConsumer buffer = buffers.getBuffer(type);
        part.render(pose, sprite == null ? buffer : sprite.wrap(buffer), light, overlay, color);
    }

    /** The model, posed for the state when it's an entity model, tinted by color. */
    public <S> void submitModel(Model model, S state, PoseStack pose, RenderType type, int light, int overlay, int color,
                                TextureAtlasSprite sprite, int outlineColor, Object crumbling) {
        if (model instanceof EntityModel<?> entityModel) pose(entityModel, state);
        VertexConsumer buffer = buffers.getBuffer(type);
        model.renderToBuffer(pose, sprite == null ? buffer : sprite.wrap(buffer), light, overlay, color);
    }

    public <S> void submitModel(Model model, S state, PoseStack pose, RenderType type, int light, int overlay, int outlineColor, Object crumbling) {
        submitModel(model, state, pose, type, light, overlay, -1, null, outlineColor, crumbling);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void pose(EntityModel<?> model, Object state) {
        ((EntityModel) model).setupAnim((net.minecraft.client.renderer.entity.state.EntityRenderState) state);
    }

    public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tints,
                           List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil) {
        ItemRenderer.renderItem(context, pose, buffers, light, overlay, tints, quads, type, foil);
    }
}
