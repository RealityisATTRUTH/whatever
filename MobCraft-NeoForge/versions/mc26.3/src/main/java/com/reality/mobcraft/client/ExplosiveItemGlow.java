package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reality.mobcraft.MobCraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Creeper Essence: an item charged with explosive energy is wrapped in an orange glow that swirls over it, hugging
 * its shape like shrink wrap. The same glow as the one on the player (explosive_glow.png, MobLookLayer), drawn over
 * the item's own faces a hair outside them. It shows wherever the item is drawn: in your hand, in your inventory,
 * on the ground, and flying through the air once thrown. It flickers like a creeper about to blow.
 *
 * Which items: the ones with CreeperEssence.CHARGE, marked when the item's model is worked out (ItemGlowMixins).
 */
public final class ExplosiveItemGlow {

    private static final Identifier TEXTURE = MobCraft.id("textures/entity/explosive_glow.png");
    private static final float OUT = 0.004f;      // how far outside the faces, in blocks
    private static final int FULL_BRIGHT = 0xF000F0;

    private ExplosiveItemGlow() {}

    /** From the item layer's submit, with its own pose already applied. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, ItemQuads quads) {
        if (quads == null || quads.isEmpty()) return;
        List<BakedQuad> all = quads.all();
        float t = (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
        float scroll = t * 0.6f;
        float flicker = 0.72f + 0.28f * (float) Math.sin(t * 9f) * (float) Math.sin(t * 3.3f);
        int v = Math.round(255 * flicker);
        int color = 0xFF000000 | v << 16 | v << 8 | v;
        collector.submitCustomGeometry(poseStack, RenderTypes.energySwirl(TEXTURE, scroll % 1f, (scroll * 0.7f) % 1f),
                (pose, buffer) -> draw(pose.pose(), pose.normal(), buffer, all, color));
    }

    private static void draw(Matrix4f pose, Matrix3f normal, VertexConsumer buffer, List<BakedQuad> quads, int color) {
        Vector3f n = new Vector3f();
        for (BakedQuad quad : quads) {
            Direction d = quad.direction();
            normal.transform(d.getStepX(), d.getStepY(), d.getStepZ(), n).normalize();
            TextureAtlasSprite sprite = quad.materialInfo().sprite();
            boolean[] solid = sprite == null ? null : solidMask(sprite.contents());
            if (solid == null) {
                face(pose, buffer, quad, d, n, color, 0, 0, 1, 1);
                continue;
            }
            // A flat item's face is one big square with see-through pixels round the picture. Wrap only the
            // pixels that are really there, a row of them at a time, so the glow fits the item's own outline.
            int w = sprite.contents().width(), h = sprite.contents().height();
            float su0 = sprite.getU0(), su1 = sprite.getU1(), sv0 = sprite.getV0(), sv1 = sprite.getV1();
            float[] uv0 = texel(quad.packedUV(0), su0, su1, sv0, sv1, w, h);
            float[] uv1 = texel(quad.packedUV(1), su0, su1, sv0, sv1, w, h);
            float[] uv3 = texel(quad.packedUV(3), su0, su1, sv0, sv1, w, h);
            // texel = uv0 + s (uv1 - uv0) + t (uv3 - uv0); solve for s and t at any texel corner
            float a = uv1[0] - uv0[0], b = uv3[0] - uv0[0], c = uv1[1] - uv0[1], e = uv3[1] - uv0[1];
            float det = a * e - b * c;
            if (Math.abs(det) < 1e-6f) continue;
            int x0 = (int) Math.floor(Math.min(Math.min(uv0[0], uv1[0]), uv3[0]) + 1e-3f);
            int x1 = (int) Math.ceil(Math.max(Math.max(uv0[0], uv1[0]), Math.max(uv3[0], uv0[0] + a + b)) - 1e-3f);
            int y0 = (int) Math.floor(Math.min(Math.min(uv0[1], uv1[1]), uv3[1]) + 1e-3f);
            int y1 = (int) Math.ceil(Math.max(Math.max(uv0[1], uv1[1]), Math.max(uv3[1], uv0[1] + c + e)) - 1e-3f);
            for (int ty = Math.max(0, y0); ty < Math.min(h, y1); ty++) {
                int tx = Math.max(0, x0);
                while (tx < Math.min(w, x1)) {
                    if (!solid[ty * w + tx]) { tx++; continue; }
                    int run = tx;
                    while (run < Math.min(w, x1) && solid[ty * w + run]) run++;
                    float[] st0 = solve(tx, ty, uv0, a, b, c, e, det), st1 = solve(run, ty + 1, uv0, a, b, c, e, det);
                    face(pose, buffer, quad, d, n, color, st0[0], st0[1], st1[0], st1[1]);
                    tx = run;
                }
            }
        }
    }

    private static float[] texel(long packed, float u0, float u1, float v0, float v1, int w, int h) {
        return new float[]{(UVPair.unpackU(packed) - u0) / (u1 - u0) * w, (UVPair.unpackV(packed) - v0) / (v1 - v0) * h};
    }

    private static float[] solve(float tx, float ty, float[] uv0, float a, float b, float c, float e, float det) {
        float du = tx - uv0[0], dv = ty - uv0[1];
        return new float[]{(du * e - b * dv) / det, (a * dv - c * du) / det};
    }

    /** The part of a quad between (s0, t0) and (s1, t1), a hair outside it, with the swirl laid flat across it. */
    private static void face(Matrix4f pose, VertexConsumer buffer, BakedQuad quad, Direction d, Vector3f n, int color,
                             float s0, float t0, float s1, float t1) {
        Vector3fc p0 = quad.position(0), p1 = quad.position(1), p3 = quad.position(3);
        float[][] st = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
        Vector3f p = new Vector3f();
        for (float[] c : st) {
            float x = p0.x() + c[0] * (p1.x() - p0.x()) + c[1] * (p3.x() - p0.x()) + d.getStepX() * OUT;
            float y = p0.y() + c[0] * (p1.y() - p0.y()) + c[1] * (p3.y() - p0.y()) + d.getStepY() * OUT;
            float z = p0.z() + c[0] * (p1.z() - p0.z()) + c[1] * (p3.z() - p0.z()) + d.getStepZ() * OUT;
            float u, w;
            switch (d.getAxis()) {
                case X -> { u = z; w = y; }
                case Y -> { u = x; w = z; }
                default -> { u = x; w = y; }
            }
            pose.transformPosition(x, y, z, p);
            buffer.addVertex(p.x, p.y, p.z, color, u * 0.5f, w * 0.5f, OverlayTexture.NO_OVERLAY, FULL_BRIGHT, n.x, n.y, n.z);
        }
    }

    /** Which pixels of a see-through sprite are really there, worked out once per sprite. Null if it has no holes. */
    private static final Map<SpriteContents, boolean[]> MASKS = new WeakHashMap<>();

    private static boolean[] solidMask(SpriteContents contents) {
        if (contents.transparency().isOpaque()) return null;
        return MASKS.computeIfAbsent(contents, k -> {
            int w = k.width(), h = k.height();
            boolean[] mask = new boolean[w * h];
            try {
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) mask[y * w + x] = !k.isTransparent(0, x, y);
            } catch (RuntimeException ex) {
                java.util.Arrays.fill(mask, true);     // can't read it: wrap the whole face
            }
            return mask;
        });
    }
}
