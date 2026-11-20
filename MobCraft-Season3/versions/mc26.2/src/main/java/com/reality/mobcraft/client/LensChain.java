package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.reality.mobcraft.MobCraft;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Illusion Lens's chain: a little gold chain hanging from the monocle's eyelet that swings with real physics.
 * The monocle itself is a normal model (models/item/illusion_lens.json); items/illusion_lens.json draws this on top.
 *
 * The chain is a row of points joined by links of fixed length (verlet physics), hung from the eyelet. It's
 * simulated in the world: turn, walk or jump and it lags behind and swings back, then settles under gravity.
 * In the inventory it hangs straight down the screen, and swings as you drag the lens around on the cursor.
 *
 * Each item that's drawn keeps its own chain, by who holds it and how (in which hand, first or third person),
 * or by the stack itself in the inventory. Chains nobody has drawn for a few seconds are forgotten.
 */
public final class LensChain implements SpecialModelRenderer<LensChain.Arg> {

    public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());
    private static final Identifier TEXTURE = MobCraft.id("textures/item/illusion_lens_chain.png");

    /** Where the chain hangs from, in the model's block units: the eyelet (art/lens/build.py prints it). */
    private static final Vector3fc ANCHOR = new Vector3f(11.664f / 16f, 5.034f / 16f, 0.5f);
    private static final int LINKS = 6;
    private static final float LINK = 1.45f / 16f;        // each link's length, in block units of the model
    private static final float GRAVITY = 260f;            // in link lengths per second squared
    private static final float DRAG = 2.2f;               // how quickly a swing dies down, per second
    private static final float STEP = 1f / 240f;

    /** Set while the item model is updated (SpecialModelWrapperMixin): how and by whom this one is drawn. */
    private static ItemDisplayContext updatingContext = ItemDisplayContext.NONE;
    private static ItemOwner updatingOwner;

    private static final Map<Object, Sim> SIMS = new HashMap<>();
    private static long lastSweep;

    public record Arg(ItemDisplayContext context, Object key, boolean carried) {}

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Arg> {
        @Override
        public SpecialModelRenderer<Arg> bake(SpecialModelRenderer.BakingContext context) {
            return new LensChain();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    /** From SpecialModelWrapperMixin, before the argument is made. Always redrawn, so the chain can move. */
    public static void beginUpdate(ItemStackRenderState state, ItemDisplayContext context, ItemOwner owner) {
        updatingContext = context;
        updatingOwner = owner;
        state.setAnimated();
    }

    /** How and by whom the item being updated right now is drawn (GrubRenderer reads these too). */
    public static ItemDisplayContext updatingContext() { return updatingContext; }
    public static ItemOwner updatingOwner() { return updatingOwner; }

    @Override
    public Arg extractArgument(ItemStack stack) {
        ItemDisplayContext context = updatingContext;
        boolean gui = context == ItemDisplayContext.GUI;
        Object key = gui || updatingOwner == null
                ? new Key(System.identityHashCode(stack), context)
                : new Key(System.identityHashCode(updatingOwner), context);
        Minecraft mc = Minecraft.getInstance();
        boolean carried = gui && mc.player != null && mc.player.containerMenu != null
                && mc.player.containerMenu.getCarried() == stack;
        return new Arg(context, key, carried);
    }

    private record Key(int who, ItemDisplayContext context) {}

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        // Hanging straight down, as it is most of the time.
        float reach = LINKS * LINK + 0.05f;
        out.accept(new Vector3f(ANCHOR.x() - 0.1f, ANCHOR.y() - reach, 0.45f));
        out.accept(new Vector3f(ANCHOR.x() + 0.1f, ANCHOR.y(), 0.55f));
    }

    /** One chain's state: the points now and a moment ago, in the space it's simulated in. */
    private static final class Sim {
        final Vector3f[] now = new Vector3f[LINKS + 1];
        final Vector3f[] before = new Vector3f[LINKS + 1];
        final Vector3f anchor = new Vector3f();
        long lastNanos;
        long lastUsed;
        boolean started;
        float leftover;
        Sim() {
            for (int i = 0; i <= LINKS; i++) { now[i] = new Vector3f(); before[i] = new Vector3f(); }
        }
    }

    @Override
    public void submit(Arg arg, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil,
                       int outlineColor) {
        if (arg == null) return;
        long nanos = System.nanoTime();
        Matrix4f model = new Matrix4f(poseStack.last().pose());

        // The space the chain lives in, and which way is down there.
        Matrix4f toWorld = new Matrix4f();
        Vector3f down = new Vector3f(0, -1, 0);
        if (arg.context() == ItemDisplayContext.GUI) {
            // The screen: down is the model's own down, flat on the screen.
            model.transformDirection(0, -1, 0, down);
            down.z = 0;
            if (down.lengthSquared() < 1e-8f) down.set(0, 1, 0);
            if (arg.carried()) {
                // On the cursor: the chain feels the mouse move.
                Minecraft mc = Minecraft.getInstance();
                double scale = mc.getWindow().getGuiScale();
                toWorld.translate((float) (mc.mouseHandler.xpos() / scale), (float) (mc.mouseHandler.ypos() / scale), 0);
            }
        } else {
            Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            Vec3 cam = camera.position();
            toWorld.translate((float) cam.x, (float) cam.y, (float) cam.z);
            if (arg.context().firstPerson()) {
                // Hands are drawn in the camera's own space.
                toWorld.rotate(camera.rotation());
            }
        }
        down.normalize();
        Matrix4f modelToWorld = new Matrix4f(toWorld).mul(model);
        float unit = modelToWorld.transformDirection(new Vector3f(0, 1, 0)).length();
        if (unit < 1e-6f) return;
        float link = LINK * unit;
        Vector3f anchor = modelToWorld.transformPosition(new Vector3f(ANCHOR));

        Sim sim = SIMS.computeIfAbsent(arg.key(), k -> new Sim());
        if (!sim.started || nanos - sim.lastNanos > 500_000_000L || sim.anchor.distance(anchor) > link * 40) {
            // New, or not drawn for a while: hang straight down.
            for (int i = 0; i <= LINKS; i++) {
                sim.now[i].set(down).mul(link * i).add(anchor);
                sim.before[i].set(sim.now[i]);
            }
            sim.anchor.set(anchor);
            sim.started = true;
            sim.leftover = 0;
        } else {
            float dt = Math.min((nanos - sim.lastNanos) / 1e9f, 0.1f) + sim.leftover;
            int steps = (int) (dt / STEP);
            sim.leftover = dt - steps * STEP;
            Vector3f from = new Vector3f(sim.anchor);
            for (int s = 1; s <= steps; s++) {
                Vector3f a = new Vector3f(from).lerp(anchor, (float) s / steps);
                step(sim, a, down, link);
            }
            if (steps > 0) sim.anchor.set(anchor);
        }
        sim.lastNanos = nanos;
        sim.lastUsed = nanos;
        sweep(nanos);

        // Back into the model's space to draw it.
        Matrix4f worldToModel = new Matrix4f(modelToWorld).invert();
        Vector3f[] p = new Vector3f[LINKS + 1];
        for (int i = 0; i <= LINKS; i++) p[i] = worldToModel.transformPosition(new Vector3f(sim.now[i]));
        p[0].set(ANCHOR);

        RenderType type = RenderTypes.entityCutout(TEXTURE);
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> draw(pose.pose(), pose.normal(), buffer, p, light, overlay));
    }

    /** One small step of verlet physics: move, fall, then pull every link back to its length. */
    private static void step(Sim sim, Vector3f anchor, Vector3f down, float link) {
        float keep = (float) Math.exp(-DRAG * STEP);
        Vector3f fall = new Vector3f(down).mul(GRAVITY * link * STEP * STEP);
        for (int i = 1; i <= LINKS; i++) {
            Vector3f now = sim.now[i];
            Vector3f velocity = new Vector3f(now).sub(sim.before[i]).mul(keep);
            sim.before[i].set(now);
            now.add(velocity).add(fall);
        }
        sim.now[0].set(anchor);
        sim.before[0].set(anchor);
        Vector3f d = new Vector3f();
        for (int pass = 0; pass < 6; pass++) {
            for (int i = 0; i < LINKS; i++) {
                Vector3f a = sim.now[i], b = sim.now[i + 1];
                d.set(b).sub(a);
                float len = d.length();
                if (len < 1e-7f) continue;
                float off = (len - link) / len;
                if (i == 0) {
                    b.sub(d.mul(off));
                } else {
                    d.mul(off * 0.5f);
                    a.add(d);
                    b.sub(d);
                }
            }
        }
    }

    private static void sweep(long nanos) {
        if (nanos - lastSweep < 2_000_000_000L) return;
        lastSweep = nanos;
        for (Iterator<Sim> it = SIMS.values().iterator(); it.hasNext(); ) {
            if (nanos - it.next().lastUsed > 5_000_000_000L) it.remove();
        }
    }

    /**
     * Each link is a solid oval loop of gold wire, built from short square bars around the oval. Every other
     * link is turned a quarter, so each one threads through the next like a real chain.
     */
    private static void draw(Matrix4f pose, Matrix3f normal, VertexConsumer buffer, Vector3f[] p, int light, int overlay) {
        Vector3f dir = new Vector3f(), side = new Vector3f(), face = new Vector3f();
        Vector3f lastDir = new Vector3f(0, -1, 0);
        Vector3f center = new Vector3f(), q0 = new Vector3f(), q1 = new Vector3f();
        Vector3f u = new Vector3f(), v = new Vector3f(), mid = new Vector3f();
        float a = LINK * 0.8f, b = LINK * 0.36f;          // the oval's half length and half width
        for (int i = 0; i < LINKS; i++) {
            dir.set(p[i + 1]).sub(p[i]);
            if (dir.lengthSquared() < 1e-10f) dir.set(lastDir); else dir.normalize();
            lastDir.set(dir);
            dir.cross(0, 0, 1, side);
            if (side.lengthSquared() < 1e-6f) dir.cross(1, 0, 0, side);
            side.normalize();
            if (i % 2 == 1) dir.cross(side, side).normalize();
            side.cross(dir, face).normalize();
            center.set(p[i]).add(p[i + 1]).mul(0.5f);
            for (int k = 0; k < RING; k++) {
                ovalPoint(center, dir, side, a, b, k, q0);
                ovalPoint(center, dir, side, a, b, k + 1, q1);
                u.set(q1).sub(q0);
                float half = u.length() / 2 + WIRE * 0.45f;
                u.normalize();
                face.cross(u, v).normalize();
                mid.set(q0).add(q1).mul(0.5f);
                box(pose, normal, buffer, mid, u, v, face, half, WIRE / 2, WIRE / 2, light, overlay);
            }
        }
        // A little bar at the end, the kind that hooks through a buttonhole, hung from the last link.
        Vector3f bar = new Vector3f(dir).mul(a).add(p[LINKS]);
        Vector3f across = new Vector3f(side).cross(face).normalize();
        box(pose, normal, buffer, bar, side, across, face, 1.4f / 16f, 0.32f / 16f, 0.32f / 16f, light, overlay);
    }

    private static final int RING = 10;               // bars around each link
    private static final float WIRE = 0.3f / 16f;      // how thick the wire is

    private static void ovalPoint(Vector3f center, Vector3f dir, Vector3f side, float a, float b, int k, Vector3f out) {
        double t = Math.PI * 2 * k / RING;
        out.set(dir).mul((float) (a * Math.cos(t))).add(side.x * (float) (b * Math.sin(t)),
                side.y * (float) (b * Math.sin(t)), side.z * (float) (b * Math.sin(t))).add(center);
    }

    /** A solid box around c: hu along u, hv along v, hw along w (all three at right angles). */
    private static void box(Matrix4f pose, Matrix3f normal, VertexConsumer buffer, Vector3f c, Vector3f u, Vector3f v,
                            Vector3f w, float hu, float hv, float hw, int light, int overlay) {
        Vector3f du = new Vector3f(u).mul(hu), dv = new Vector3f(v).mul(hv), dw = new Vector3f(w).mul(hw);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            // the two big sides, the two thin sides, and the two ends
            Vector3f o = new Vector3f(dw).mul(sgn).add(c);
            quad(pose, normal, buffer, new Vector3f(o).sub(du), new Vector3f(o).add(du), v, new Vector3f(w).mul(sgn), hv, light, overlay);
            o = new Vector3f(dv).mul(sgn).add(c);
            quad(pose, normal, buffer, new Vector3f(o).sub(du), new Vector3f(o).add(du), w, new Vector3f(v).mul(sgn), hw, light, overlay);
            o = new Vector3f(du).mul(sgn).add(c);
            quad(pose, normal, buffer, new Vector3f(o).sub(dv), new Vector3f(o).add(dv), w, new Vector3f(u).mul(sgn), hw, light, overlay);
        }
    }

    /** A quad from start to end, w wide across "side", facing "face": a patch of the solid gold in the chain texture. */
    private static void quad(Matrix4f pose, Matrix3f normal, VertexConsumer buffer, Vector3f start, Vector3f end,
                             Vector3f side, Vector3f face, float w, int light, int overlay) {
        Vector3f s = new Vector3f(side).mul(w);
        Vector3f n = normal.transform(new Vector3f(face)).normalize();
        float u0 = 9 / 16f, v0 = 6 / 16f, u1 = 15 / 16f, v1 = 10 / 16f;
        vertex(pose, buffer, new Vector3f(start).sub(s), u0, v0, light, overlay, n);
        vertex(pose, buffer, new Vector3f(start).add(s), u1, v0, light, overlay, n);
        vertex(pose, buffer, new Vector3f(end).add(s), u1, v1, light, overlay, n);
        vertex(pose, buffer, new Vector3f(end).sub(s), u0, v1, light, overlay, n);
    }

    private static void vertex(Matrix4f pose, VertexConsumer buffer, Vector3f at, float u, float v, int light, int overlay, Vector3f n) {
        Vector3f w = pose.transformPosition(at);
        buffer.addVertex(w.x, w.y, w.z, -1, u, v, overlay, light, n.x, n.y, n.z);
    }
}
