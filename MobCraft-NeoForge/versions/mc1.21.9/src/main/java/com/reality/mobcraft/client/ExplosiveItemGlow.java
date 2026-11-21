package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reality.mobcraft.MobCraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
 * the item's own faces a hair outside them. It shows everywhere the item is drawn (see shows()). It flickers like a creeper about to blow.
 *
 * Which items: the ones with CreeperEssence.CHARGE, marked when the item's model is worked out (ItemGlowMixins).
 */
public final class ExplosiveItemGlow {

    private static final ResourceLocation TEXTURE = MobCraft.id("textures/entity/explosive_glow.png");
    private static final float OUT = 0.004f;      // how far outside the faces, in blocks
    private static final int FULL_BRIGHT = 0xF000F0;

    /**
     * Vanilla's energy swirl, but it doesn't write depth. 26.1 can draw the glow before the item it wraps, and the
     * vanilla swirl then hides the item behind it. Without depth writes, the order doesn't matter.
     */
    private static com.mojang.blaze3d.pipeline.RenderPipeline pipeline;

    private static net.minecraft.client.renderer.RenderType swirl(float u, float v) {
        if (pipeline == null) {
            pipeline = com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(MobCraft.id("pipeline/explosive_glow"))
                    .withVertexShader("core/entity")
                    .withFragmentShader("core/entity")
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    .withShaderDefine("EMISSIVE")
                    .withShaderDefine("NO_OVERLAY")
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withShaderDefine("APPLY_TEXTURE_MATRIX")
                    .withSampler("Sampler0")
                    .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE)
                    .withCull(false)
                    .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY, com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                    .withDepthWrite(false)
                    .build();
        }
        return net.minecraft.client.renderer.RenderType.create("mobcraft_explosive_glow", 1536, false, true, pipeline,
                net.minecraft.client.renderer.RenderType.CompositeState.builder()
                        .setTextureState(new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(TEXTURE, false))
                        .setTexturingState(new net.minecraft.client.renderer.RenderStateShard.OffsetTexturingStateShard(u, v))
                        .setLightmapState(net.minecraft.client.renderer.RenderStateShard.LIGHTMAP)
                        .setOverlayState(net.minecraft.client.renderer.RenderStateShard.OVERLAY)
                        .createCompositeState(false));
    }

    /** Set while a menu draws a small 3D picture (PipGlowFlagMixin). */
    public static boolean inMenuPicture;

    private ExplosiveItemGlow() {}

    /** The glow shows wherever the item is drawn: held, on the ground, thrown, in menus and on inventory icons. */
    public static boolean shows(net.minecraft.world.item.ItemDisplayContext context) {
        return context != null;
    }

    /** From the item layer's submit, with its own pose already applied. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) return;
        // 26.1 builds the vertices later in the frame, after the item's own state may be reused, so keep copies.
        List<BakedQuad> all = List.copyOf(quads);
        Matrix4f fixedPose = new Matrix4f(poseStack.last().pose());
        Matrix3f fixedNormal = new Matrix3f(poseStack.last().normal());
        float t = (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
        float scroll = t * 0.6f;
        float flicker = 0.72f + 0.28f * (float) Math.sin(t * 9f) * (float) Math.sin(t * 3.3f);
        int v = Math.round(255 * flicker);
        int color = 0xFF000000 | v << 16 | v << 8 | v;
        collector.submitCustomGeometry(poseStack, swirl(scroll % 1f, (scroll * 0.7f) % 1f),
                (pose, buffer) -> {
                    // 26.1 holds the item's own faces back until the end of the frame. Draw them now, so the glow
                    // lands on top of the item instead of under it.
                    var source = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
                    java.util.Set<net.minecraft.client.renderer.RenderType> done = new java.util.HashSet<>();
                    for (var t2 : java.util.List.of(net.minecraft.client.renderer.Sheets.translucentItemSheet(),
                            net.minecraft.client.renderer.Sheets.cutoutBlockSheet(),
                            net.minecraft.client.renderer.Sheets.solidBlockSheet()))
                        if (done.add(t2)) source.endBatch(t2);
                    draw(fixedPose, fixedNormal, buffer, all, color);
                });
    }

    private static void draw(Matrix4f pose, Matrix3f normal, VertexConsumer buffer, List<BakedQuad> quads, int color) {
        Vector3f n = new Vector3f();
        for (BakedQuad quad : quads) {
            Direction d = quad.direction();
            normal.transform(d.getStepX(), d.getStepY(), d.getStepZ(), n).normalize();
            TextureAtlasSprite sprite = quad.sprite();
            boolean[] solid = sprite == null ? null : solidMask(sprite.contents());
            if (solid == null) {
                face(pose, buffer, quad, d, n, color, 0, 0, 1, 1);
                continue;
            }
            // A flat item's face is one big square with see-through pixels round the picture. Wrap only the
            // pixels that are really there, a row of them at a time, so the glow fits the item's own outline.
            int w = sprite.contents().width(), h = sprite.contents().height();
            float su0 = sprite.getU0(), su1 = sprite.getU1(), sv0 = sprite.getV0(), sv1 = sprite.getV1();
            float[] uv0 = texel(quad, 0, su0, su1, sv0, sv1, w, h);
            float[] uv1 = texel(quad, 1, su0, su1, sv0, sv1, w, h);
            float[] uv3 = texel(quad, 3, su0, su1, sv0, sv1, w, h);
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

    private static float[] texel(BakedQuad quad, int i, float u0, float u1, float v0, float v1, int w, int h) {
        int[] v = quad.vertices();
        float u = Float.intBitsToFloat(v[i * 8 + 4]), t = Float.intBitsToFloat(v[i * 8 + 5]);
        return new float[]{(u - u0) / (u1 - u0) * w, (t - v0) / (v1 - v0) * h};
    }

    private static Vector3fc position(BakedQuad quad, int i) {
        int[] v = quad.vertices();
        return new Vector3f(Float.intBitsToFloat(v[i * 8]), Float.intBitsToFloat(v[i * 8 + 1]), Float.intBitsToFloat(v[i * 8 + 2]));
    }

    private static float[] solve(float tx, float ty, float[] uv0, float a, float b, float c, float e, float det) {
        float du = tx - uv0[0], dv = ty - uv0[1];
        return new float[]{(du * e - b * dv) / det, (a * dv - c * du) / det};
    }

    /** The part of a quad between (s0, t0) and (s1, t1), a hair outside it, with the swirl laid flat across it. */
    private static void face(Matrix4f pose, VertexConsumer buffer, BakedQuad quad, Direction d, Vector3f n, int color,
                             float s0, float t0, float s1, float t1) {
        Vector3fc p0 = position(quad, 0), p1 = position(quad, 1), p3 = position(quad, 3);
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
