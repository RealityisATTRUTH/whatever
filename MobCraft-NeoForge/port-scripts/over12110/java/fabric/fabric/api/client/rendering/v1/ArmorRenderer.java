package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.reality.mobcraft.neo.mixin.ModelPartChildrenAccess;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

/**
 * Draws a model posed like another one: each part takes the matching part's position, turn and scale (by part name).
 * Fabric API's ArmorRenderer.submitTransformCopyingModel does this from 1.21.10 on; this is MobCraft's own for 1.21.9.
 */
public final class ArmorRenderer<S, D> extends Model<Pair<S, D>> {
    private final Model<? super S> source;
    private final Model<? super D> delegate;
    private final boolean setDelegateAngles;

    private ArmorRenderer(Model<? super S> source, Model<? super D> delegate, boolean setDelegateAngles) {
        super(delegate.root(), delegate::renderType);
        this.source = source; this.delegate = delegate; this.setDelegateAngles = setDelegateAngles;
    }

    @Override
    public void setupAnim(Pair<S, D> states) {
        resetPose();
        source.setupAnim(states.getFirst());
        copy(source.root(), delegate.root());
        if (setDelegateAngles) delegate.setupAnim(states.getSecond());
    }

    /** The root's pose, then every part of the source onto the part of the same name anywhere in the delegate. */
    private static void copy(ModelPart from, ModelPart to) {
        pose(from, to);
        java.util.Map<String, ModelPart> mine = new java.util.HashMap<>();
        collect(to, mine);
        java.util.Map<String, ModelPart> theirs = new java.util.HashMap<>();
        collect(from, theirs);
        theirs.forEach((name, part) -> {
            ModelPart match = mine.get(name);
            if (match != null) pose(part, match);
        });
    }

    private static void collect(ModelPart part, java.util.Map<String, ModelPart> out) {
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

    public static <S, D> void submitTransformCopyingModel(Model<? super S> source, S sourceState, Model<? super D> delegate, D delegateState,
                                     boolean setDelegateAngles, OrderedSubmitNodeCollector collector, PoseStack pose,
                                     RenderType type, int light, int overlay, int color, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        collector.submitModel(new ArmorRenderer<S, D>(source, delegate, setDelegateAngles), Pair.of(sourceState, delegateState),
                pose, type, light, overlay, color, crumbling);
    }
}
