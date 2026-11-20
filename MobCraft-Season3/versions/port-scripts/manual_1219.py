"""1.21.9 on top of the 1.21.10 copy. Fabric API for 1.21.9 has no world render events and no transform-copying
armor helper, so MobCraft brings its own small versions of both. Usage: manual_1219.py <src/main/java>"""
import sys, pathlib
java = pathlib.Path(sys.argv[1]); root = java / 'com/reality/mobcraft'
res = java.parent / 'resources'

def write(rel, text):
    p = root / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text)

write('compat/world/WorldRenderContext.java', '''package com.reality.mobcraft.compat.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;

/** What a world-drawing listener gets just before entities are submitted. Stands in for Fabric API's, which 1.21.9 lacks. */
public record WorldRenderContext(SubmitNodeCollector commandQueue, PoseStack matrices, DeltaTracker tickCounter,
                                 ClientLevel world, LevelRenderState worldState) {}
''')
write('compat/world/WorldExtractionContext.java', '''package com.reality.mobcraft.compat.world;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.LevelRenderState;

/** What a listener gets once the world has been gathered up to draw. Stands in for Fabric API's, which 1.21.9 lacks. */
public record WorldExtractionContext(DeltaTracker tickCounter, ClientLevel world, LevelRenderState worldState) {}
''')
write('compat/world/WorldRenderEvents.java', '''package com.reality.mobcraft.compat.world;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * The two world render events MobCraft uses, fired by WorldRenderEventsMixin. Fabric API has these from 1.21.10 on;
 * for 1.21.9 MobCraft fires them itself, at the same places in the game's drawing.
 */
public final class WorldRenderEvents {
    private WorldRenderEvents() {}

    /** Just before the game submits entities to draw. */
    public static final Event<BeforeEntities> BEFORE_ENTITIES = EventFactory.createArrayBacked(BeforeEntities.class,
            listeners -> context -> { for (BeforeEntities l : listeners) l.beforeEntities(context); });

    /** Once everything to draw this frame has been gathered, right after the world border. */
    public static final Event<EndExtraction> END_EXTRACTION = EventFactory.createArrayBacked(EndExtraction.class,
            listeners -> context -> { for (EndExtraction l : listeners) l.endExtraction(context); });

    @FunctionalInterface public interface BeforeEntities { void beforeEntities(WorldRenderContext context); }
    @FunctionalInterface public interface EndExtraction { void endExtraction(WorldExtractionContext context); }
}
''')
write('mixin/client/WorldRenderEventsMixin.java', '''package com.reality.mobcraft.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.compat.world.WorldExtractionContext;
import com.reality.mobcraft.compat.world.WorldRenderContext;
import com.reality.mobcraft.compat.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires MobCraft's own world render events (compat.world) at the places Fabric API fires its own from 1.21.10 on. */
@Mixin(LevelRenderer.class)
public abstract class WorldRenderEventsMixin {
    @Shadow private ClientLevel level;
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private SubmitNodeStorage submitNodeStorage;
    @Unique private DeltaTracker mobcraft$deltaTracker;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void mobcraft$frame(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera,
                                Matrix4f a, Matrix4f b, Matrix4f c, GpuBufferSlice fog, Vector4f color, boolean sky, CallbackInfo ci) {
        mobcraft$deltaTracker = deltaTracker;
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/WorldBorderRenderer;extract(Lnet/minecraft/world/level/border/WorldBorder;Lnet/minecraft/world/phys/Vec3;DLnet/minecraft/client/renderer/state/WorldBorderRenderState;)V"))
    private void mobcraft$endExtraction(CallbackInfo ci) {
        if (level != null) WorldRenderEvents.END_EXTRACTION.invoker().endExtraction(
                new WorldExtractionContext(mobcraft$deltaTracker, level, levelRenderState));
    }

    @Inject(method = "method_62214", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=submitEntities"))
    private void mobcraft$beforeEntities(GpuBufferSlice fog, LevelRenderState state, ProfilerFiller profiler, Matrix4f matrix,
                                         ResourceHandle<?> h1, ResourceHandle<?> h2, boolean b, Frustum frustum,
                                         ResourceHandle<?> h3, ResourceHandle<?> h4, CallbackInfo ci, @Local PoseStack pose) {
        if (level != null) WorldRenderEvents.BEFORE_ENTITIES.invoker().beforeEntities(
                new WorldRenderContext(submitNodeStorage, pose, mobcraft$deltaTracker, level, state));
    }
}
''')
write('mixin/client/ModelPartChildrenAccess.java', '''package com.reality.mobcraft.mixin.client;

import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface ModelPartChildrenAccess {
    @Accessor("children") Map<String, ModelPart> mobcraft$children();
}
''')
write('compat/TransformCopy.java', '''package com.reality.mobcraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.reality.mobcraft.mixin.client.ModelPartChildrenAccess;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

/**
 * Draws a model posed like another one: each part takes the matching part's position, turn and scale (by part name).
 * Fabric API's ArmorRenderer.submitTransformCopyingModel does this from 1.21.10 on; this is MobCraft's own for 1.21.9.
 */
public final class TransformCopy<S, D> extends Model<Pair<S, D>> {
    private final Model<? super S> source;
    private final Model<? super D> delegate;
    private final boolean setDelegateAngles;

    private TransformCopy(Model<? super S> source, Model<? super D> delegate, boolean setDelegateAngles) {
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

    public static <S, D> void submit(Model<? super S> source, S sourceState, Model<? super D> delegate, D delegateState,
                                     boolean setDelegateAngles, OrderedSubmitNodeCollector collector, PoseStack pose,
                                     RenderType type, int light, int overlay, int color, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        collector.submitModel(new TransformCopy<S, D>(source, delegate, setDelegateAngles), Pair.of(sourceState, delegateState),
                pose, type, light, overlay, color, crumbling);
    }
}
''')

client = res / 'mobcraft.client.mixins.json'
s = client.read_text()
anchor = '  "client": [\n'
assert anchor in s and 'WorldRenderEventsMixin' not in s
client.write_text(s.replace(anchor, anchor + '    "WorldRenderEventsMixin",\n    "ModelPartChildrenAccess",\n', 1))

n = 0
for f in root.rglob('*.java'):
    t = f.read_text(); t0 = t
    t = t.replace('net.fabricmc.fabric.api.client.rendering.v1.world.', 'com.reality.mobcraft.compat.world.')
    t = t.replace('ArmorRenderer.submitTransformCopyingModel(', 'com.reality.mobcraft.compat.TransformCopy.submit(')
    if t != t0: f.write_text(t); n += 1
print('1.21.9 edits in', n, 'files')

# 1.21.9 never had a Beta 5.11, so its port notes go under the 5.12 heading.
log = res / 'assets/mobcraft/update_log.txt'
s = log.read_text()
h = '# Season 3 Beta 5.11 for Minecraft 1.21.9\n'
if h in s:
    b = s.index(h); e = s.index('\n# ', b + 1) + 1
    notes = s[b + len(h):e].replace('The same mod as Beta 5.11, made', 'Now made')
    s = s[:b] + s[e:]
    h12 = '# Season 3 Beta 5.12 for Minecraft 1.21.9\n'
    s = s.replace(h12, h12 + notes, 1)
    log.write_text(s)
