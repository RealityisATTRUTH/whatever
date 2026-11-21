package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.ModItems;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * The Silverfish Grub, alive: a fat beetle grub curled in a C that never stops squirming. Drawn in code every
 * frame, the same shape as art/grub/build.py made it, but moving:
 *   a wave of squeezing rolls down its body from tail to head, each segment swelling and shrinking in turn;
 *   its curl flexes open and shut, the tail swaying;
 *   the body writhes in and out a little;
 *   its head sways and its six little legs paddle fast;
 *   every few seconds it gives a sudden twitch.
 * While someone is eating it, it writhes much harder and faster.
 */
public final class GrubRenderer implements SpecialModelRenderer<GrubRenderer.Arg> {

    public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());
    private static final Identifier TEXTURE = MobCraft.id("textures/item/silverfish_grub_3d.png");

    // The shape, in model pixels: the same numbers as art/grub/build.py.
    private static final float CX = 8f, CY = 8.4f, R = 4.2f;
    private static final int N = 12;
    private static final float A0 = -105f, A1 = 160f, STEP = (A1 - A0) / (N - 1);

    public record Arg(float phase, boolean eaten) {}

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Arg> {
        @Override
        public SpecialModelRenderer<Arg> bake(SpecialModelRenderer.BakingContext context) {
            return new GrubRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    @Override
    public Arg extractArgument(ItemStack stack) {
        ItemOwner owner = LensChain.updatingOwner();
        ItemDisplayContext context = LensChain.updatingContext();
        Object who = owner != null && context != ItemDisplayContext.GUI ? owner : stack;
        float phase = (System.identityHashCode(who) % 997) / 37f;
        LivingEntity living = owner != null ? owner.asLivingEntity() : null;
        boolean eaten = living != null && context != ItemDisplayContext.GUI && living.isUsingItem()
                && living.getUseItem().is(ModItems.SILVERFISH_GRUB);
        return new Arg(phase, eaten);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        out.accept(new Vector3f(1 / 16f, 1 / 16f, 4 / 16f));
        out.accept(new Vector3f(15 / 16f, 15 / 16f, 12 / 16f));
    }

    private static float thick(int i) {
        float f = i / (float) (N - 1);
        return 2.9f + 1.9f * Math.min(1, f * 2.2f) + (i >= N - 2 ? 0.5f : 0);
    }

    @Override
    public void submit(Arg arg, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil,
                       int outlineColor) {
        if (arg == null) return;
        float speed = arg.eaten() ? 2.2f : 1f, amp = arg.eaten() ? 2.4f : 1f;
        float t = (System.nanoTime() % 1_000_000_000_000L) / 1e9f * speed + arg.phase();
        // Every few seconds, a sudden twitch.
        float twitch = (float) Math.pow(Math.max(0, Math.sin(t * 0.9f)), 10) * 2.5f;
        amp *= 1 + twitch;

        Quads q = new Quads();
        Vector3f u = new Vector3f(), v = new Vector3f(), w = new Vector3f(0, 0, 1), c = new Vector3f();
        float flex = 7f * (float) Math.sin(t * 1.7f) * amp;           // the curl opening and closing
        for (int i = 0; i < N; i++) {
            float along = i / (float) (N - 1);
            float deg = A0 + STEP * i + flex * along + 2.5f * amp * (float) Math.sin(t * 3.1f - i * 0.6f);
            float squeeze = 1 + 0.1f * amp * (float) Math.sin(t * 4f - i * 0.8f);   // the wave rolling down it
            float th = thick(i) * squeeze, depth = thick(i) * 0.95f * squeeze;
            float rad = (float) Math.toRadians(deg);
            u.set((float) Math.sin(rad), (float) -Math.cos(rad), 0);
            v.set((float) Math.cos(rad), (float) Math.sin(rad), 0);
            float writhe = 0.45f * amp * (float) Math.sin(t * 2.3f - i * 0.5f);
            c.set(CX + R * v.x, CY + R * v.y, 8 + writhe);
            float len = (R + th / 2) * (float) Math.toRadians(Math.abs(STEP)) + 0.15f;
            boolean tail = i >= N - 2;
            int sx = tail ? (i == N - 2 ? 12 : 8) : 0, bx = tail ? 8 : 0;
            int[] side = {sx, 0, sx + 4, 5}, back = {bx, 5, bx + 4, 10}, belly = tail ? new int[]{8, 5, 12, 10} : new int[]{4, 5, 8, 10},
                    end = {bx, 5, bx + 1, 10};
            q.box(c, u, v, w, len / 2, th / 2, depth * 0.36f, side, back, belly, end);
            q.box(c, u, v, w, len / 2, th * 0.36f, depth * 0.54f, side, back, belly, end);
            if (i == N - 1) {
                // the tail's round end
                float ta = (float) Math.toRadians(deg + STEP * 0.62f);
                Vector3f tu = new Vector3f((float) Math.sin(ta), (float) -Math.cos(ta), 0);
                Vector3f tv = new Vector3f((float) Math.cos(ta), (float) Math.sin(ta), 0);
                Vector3f tc = new Vector3f(CX + R * tv.x, CY + R * tv.y, 8 + writhe);
                float tt = th * 0.72f, tl = (R + tt / 2) * (float) Math.toRadians(Math.abs(STEP)) * 0.35f;
                int[] g = {8, 5, 12, 10};
                q.box(tc, tu, tv, w, tl, tt / 2, tt * 0.35f, new int[]{8, 0, 12, 5}, g, g, new int[]{8, 5, 9, 10});
                q.box(tc, tu, tv, w, tl, tt * 0.35f, tt / 2, new int[]{8, 0, 12, 5}, g, g, new int[]{8, 5, 9, 10});
            }
        }
        // The head sways at the front of the neck.
        float headDeg = A0 - STEP * 0.75f + 9f * amp * (float) Math.sin(t * 2.6f);
        float ha = (float) Math.toRadians(headDeg);
        Vector3f hv = new Vector3f((float) Math.cos(ha), (float) Math.sin(ha), 0);
        Vector3f hu = new Vector3f((float) Math.sin(ha), (float) -Math.cos(ha), 0);
        Vector3f hc = new Vector3f(CX + R * hv.x, CY + R * hv.y, 8 + 0.6f * amp * (float) Math.sin(t * 2.0f));
        int[] head = {0, 10, 4, 14};
        q.box(hc, hu, hv, w, 1.3f, 1.3f, 1.3f, head, head, head, head);
        // Six little legs paddling.
        int[] leg = {6, 10, 7, 12};
        for (int k = 0; k < 3; k++) {
            float la = (float) Math.toRadians(A0 + STEP * (0.3f + k * 0.75f) + flex * 0.05f);
            Vector3f lv = new Vector3f((float) Math.cos(la), (float) Math.sin(la), 0);
            float inner = R - thick(k) / 2 - 0.6f;
            for (int s = -1; s <= 1; s += 2) {
                float swing = (float) Math.toRadians(-15 + 35 * Math.sin(t * 13f * Math.min(speed, 1.6f) + k * 2.1f + s * 1.3f));
                // the leg points inward, swinging back and forth
                Vector3f in = new Vector3f(lv).negate();
                Vector3f dir = new Vector3f(in).rotateZ(swing);
                Vector3f across = new Vector3f(-dir.y, dir.x, 0);
                Vector3f base = new Vector3f(CX + inner * lv.x, CY + inner * lv.y, 8 + s * 1.0f);
                Vector3f mid = new Vector3f(dir).mul(0.85f).add(base);
                q.box(mid, dir, across, w, 0.85f, 0.3f, 0.3f, leg, leg, leg, leg);
            }
        }

        poseStack.pushPose();
        poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);
        float[] data = q.data();
        int count = q.count();
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE),
                (pose, buffer) -> emit(pose.pose(), pose.normal(), buffer, data, count, light, overlay));
        poseStack.popPose();
    }

    private static void emit(Matrix4f pose, Matrix3f normal, VertexConsumer buffer, float[] d, int count, int light, int overlay) {
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (int i = 0; i < count; i++) {
            int o = i * 8;
            pose.transformPosition(d[o], d[o + 1], d[o + 2], p);
            // Turned the way the entity lighting expects, or it comes out dull and grey.
            normal.transform(d[o + 5], d[o + 6], d[o + 7], n).normalize().negate();
            buffer.addVertex(p.x, p.y, p.z, -1, d[o + 3], d[o + 4], overlay, light, n.x, n.y, n.z);
        }
    }

    /** Vertices collected now and drawn later: x, y, z, u, v, nx, ny, nz each. */
    private static final class Quads {
        private float[] d = new float[8 * 4 * 6 * 48];
        private int n;

        float[] data() { return d; }
        int count() { return n; }

        private void vertex(Vector3f p, float u, float v, Vector3f nrm) {
            if ((n + 1) * 8 > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
            int o = n * 8;
            d[o] = p.x; d[o + 1] = p.y; d[o + 2] = p.z; d[o + 3] = u / 16f; d[o + 4] = v / 16f;
            d[o + 5] = nrm.x; d[o + 6] = nrm.y; d[o + 7] = nrm.z;
            n++;
        }

        private void face(Vector3f a, Vector3f b, Vector3f c, Vector3f e, int[] uv, Vector3f nrm) {
            vertex(a, uv[0], uv[1], nrm);
            vertex(b, uv[2], uv[1], nrm);
            vertex(c, uv[2], uv[3], nrm);
            vertex(e, uv[0], uv[3], nrm);
        }

        private static Vector3f at(Vector3f c, Vector3f u, float su, Vector3f v, float sv, Vector3f w, float sw) {
            return new Vector3f(c).add(u.x * su + v.x * sv + w.x * sw, u.y * su + v.y * sv + w.y * sw, u.z * su + v.z * sv + w.z * sw);
        }

        /** A box around c: hu along u (round the curl), hv along v (outward), hw along w (front to back). */
        void box(Vector3f c, Vector3f u, Vector3f v, Vector3f w, float hu, float hv, float hw,
                 int[] side, int[] back, int[] belly, int[] end) {
            Vector3f nu = new Vector3f(u).negate(), nv = new Vector3f(v).negate(), nw = new Vector3f(w).negate();
            face(at(c, u, -hu, v, hv, w, hw), at(c, u, hu, v, hv, w, hw), at(c, u, hu, v, -hv, w, hw), at(c, u, -hu, v, -hv, w, hw), side, w);
            face(at(c, u, hu, v, hv, w, -hw), at(c, u, -hu, v, hv, w, -hw), at(c, u, -hu, v, -hv, w, -hw), at(c, u, hu, v, -hv, w, -hw),
                    new int[]{side[2], side[1], side[0], side[3]}, nw);
            face(at(c, u, -hu, v, hv, w, -hw), at(c, u, hu, v, hv, w, -hw), at(c, u, hu, v, hv, w, hw), at(c, u, -hu, v, hv, w, hw), back, v);
            face(at(c, u, -hu, v, -hv, w, hw), at(c, u, hu, v, -hv, w, hw), at(c, u, hu, v, -hv, w, -hw), at(c, u, -hu, v, -hv, w, -hw), belly, nv);
            face(at(c, u, hu, v, hv, w, hw), at(c, u, hu, v, hv, w, -hw), at(c, u, hu, v, -hv, w, -hw), at(c, u, hu, v, -hv, w, hw), end, u);
            face(at(c, u, -hu, v, hv, w, -hw), at(c, u, -hu, v, hv, w, hw), at(c, u, -hu, v, -hv, w, hw), at(c, u, -hu, v, -hv, w, -hw), end, nu);
        }
    }
}
