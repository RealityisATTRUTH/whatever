package com.reality.mobcraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.mixin.client.ModelPartChildrenAccess;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

/**
 * Draws a model posed like another one: each part takes the matching part's position, turn and scale (by part name).
 * Fabric API's ArmorRenderer.submitTransformCopyingModel does this from 1.21.10 on; this is MobCraft's own.
 */
public final class TransformCopy {
    private TransformCopy() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <S, D> void submit(Model source, S sourceState, EntityModel<?> delegate, D delegateState,
                                     boolean setDelegateAngles, SubmitNodeCollector collector, PoseStack pose,
                                     RenderType type, int light, int overlay, int outlineColor, Object crumbling) {
        // The parent model has already been posed for this frame by the renderer that owns the layer.
        delegate.resetPose();
        copy(source.root(), delegate.root());
        if (setDelegateAngles) ((EntityModel) delegate).setupAnim((net.minecraft.client.renderer.entity.state.EntityRenderState) delegateState);
        delegate.renderToBuffer(pose, collector.buffers().getBuffer(type), light, overlay);
    }

    /** The root's pose, then every part of the source onto the part of the same name anywhere in the delegate. */
    private static void copy(ModelPart from, ModelPart to) {
        pose(from, to);
        Map<String, ModelPart> mine = new HashMap<>(), theirs = new HashMap<>();
        collect(to, mine);
        collect(from, theirs);
        theirs.forEach((name, part) -> {
            ModelPart match = mine.get(name);
            if (match != null) pose(part, match);
        });
    }

    private static void collect(ModelPart part, Map<String, ModelPart> out) {
        ((ModelPartChildrenAccess) (Object) part).mobcraft$children().forEach((name, child) -> {
            out.put(name, child);
            collect(child, out);
        });
    }

    private static void pose(ModelPart from, ModelPart to) {
        to.x = from.x; to.y = from.y; to.z = from.z;
        to.xRot = from.xRot; to.yRot = from.yRot; to.zRot = from.zRot;
        to.xScale = from.xScale; to.yScale = from.yScale; to.zScale = from.zScale;
    }
}
