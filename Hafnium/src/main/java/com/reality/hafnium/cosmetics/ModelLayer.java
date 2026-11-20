// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a player's 3D cosmetics: one on the head and one on the back. Each
 * follows its body part, so a hat turns with the head and a backpack moves
 * with the body.
 */
public class ModelLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public ModelLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isSpectator || Minecraft.getInstance().level == null) return;
        Entity entity = Minecraft.getInstance().level.getEntity(state.id);
        if (!(entity instanceof AbstractClientPlayer player) || player.isInvisible()) return;
        draw(poseStack, collector, light, Cosmetics.modelFor(player, "head"), getParentModel().head);
        draw(poseStack, collector, light, Cosmetics.modelFor(player, "back"), getParentModel().body);
    }

    private static void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, Cosmetics.LoadedModel model, ModelPart part) {
        if (model == null) return;
        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(model.texture()),
                (pose, out) -> emit(model.model(), pose, out, light));
        poseStack.popPose();
    }

    /**
     * Turns the Blockbench cubes into the game's own model space. Blockbench has
     * height going up with the neck at 24, and +X on the player's right. The
     * game's body parts have height going down from the neck, +X on the
     * player's left, and count in sixteenths of a block. So the model is turned
     * half a turn around the front-to-back line, which flips both.
     */
    private static void emit(Model3d model, PoseStack.Pose pose, VertexConsumer out, int light) {
        Matrix4f matrix = pose.pose();
        Vector3f p = new Vector3f(), n = new Vector3f();
        if (model.hasMesh()) emitMesh(model, pose, out, light);
        for (Model3d.Cube c : model.cubes) {
            float x0 = c.from()[0], y0 = c.from()[1], z0 = c.from()[2];
            float x1 = c.to()[0], y1 = c.to()[1], z1 = c.to()[2];
            for (var face : c.faces().entrySet()) {
                float[][] corners = corners(face.getKey(), x0, y0, z0, x1, y1, z1);
                float[] normal = normal(face.getKey());
                float[] uv = face.getValue();
                float[][] uvs = faceUvs(uv, model.texW, model.texH);
                rotate(c, normal, true);
                n.set(-normal[0], -normal[1], normal[2]);
                pose.transformNormal(n, n);
                for (int i = 0; i < 4; i++) {
                    float[] v = corners[i].clone();
                    rotate(c, v, false);
                    matrix.transformPosition(-v[0] / 16f, -(v[1] - 24f) / 16f, v[2] / 16f, p);
                    out.addVertex(p.x, p.y, p.z, 0xFFFFFFFF, uvs[i][0], uvs[i][1], OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
                }
            }
        }
    }

    /** Draws a mesh's triangles. Each one goes to the game as a four-cornered face with its last corner twice. */
    private static void emitMesh(Model3d model, PoseStack.Pose pose, VertexConsumer out, int light) {
        Matrix4f matrix = pose.pose();
        Vector3f p = new Vector3f(), n = new Vector3f();
        float[] pos = model.meshPos, uv = model.meshUv;
        int[] tris = model.meshTris;
        for (int t = 0; t + 2 < tris.length; t += 3) {
            int a = tris[t] * 3, b = tris[t + 1] * 3, c = tris[t + 2] * 3;
            float e1x = pos[b] - pos[a], e1y = pos[b + 1] - pos[a + 1], e1z = pos[b + 2] - pos[a + 2];
            float e2x = pos[c] - pos[a], e2y = pos[c + 1] - pos[a + 1], e2z = pos[c + 2] - pos[a + 2];
            n.set(e1y * e2z - e1z * e2y, e1z * e2x - e1x * e2z, e1x * e2y - e1y * e2x);
            if (n.lengthSquared() < 1e-12f) continue;
            n.normalize();
            n.set(-n.x, -n.y, n.z);
            pose.transformNormal(n, n);
            for (int k = 0; k < 4; k++) {
                int i = tris[t + Math.min(k, 2)];
                matrix.transformPosition(-pos[i * 3] / 16f, -(pos[i * 3 + 1] - 24f) / 16f, pos[i * 3 + 2] / 16f, p);
                out.addVertex(p.x, p.y, p.z, 0xFFFFFFFF, uv[i * 2], uv[i * 2 + 1], OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
            }
        }
    }

    /** A face's corners as seen from outside: top left, top right, bottom right, bottom left. */
    private static float[][] corners(String face, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (face) {
            case "north" -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
            case "south" -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
            case "east" -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case "west" -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
            case "up" -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            default -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
        };
    }

    private static float[] normal(String face) {
        return switch (face) {
            case "north" -> new float[]{0, 0, -1};
            case "south" -> new float[]{0, 0, 1};
            case "east" -> new float[]{1, 0, 0};
            case "west" -> new float[]{-1, 0, 0};
            case "up" -> new float[]{0, 1, 0};
            default -> new float[]{0, -1, 0};
        };
    }

    /** The texture spot for each corner, in the same order as the corners, turned by the face's rotation. */
    private static float[][] faceUvs(float[] uv, int texW, int texH) {
        float u1 = uv[0] / texW, v1 = uv[1] / texH, u2 = uv[2] / texW, v2 = uv[3] / texH;
        float[][] base = {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};
        int turns = Math.floorMod(Math.round(uv[4] / 90f), 4);
        float[][] out = new float[4][];
        for (int i = 0; i < 4; i++) out[i] = base[Math.floorMod(i - turns, 4)];
        return out;
    }

    /** Turns a point (or a direction) by the cube's rotation around its pivot, X first, then Y, then Z, as Blockbench does. */
    private static void rotate(Model3d.Cube c, float[] v, boolean direction) {
        float[] r = c.rot();
        if (r[0] == 0 && r[1] == 0 && r[2] == 0) return;
        float[] o = direction ? new float[3] : c.origin();
        double x = v[0] - o[0], y = v[1] - o[1], z = v[2] - o[2];
        double a = Math.toRadians(r[0]), b = Math.toRadians(r[1]), g = Math.toRadians(r[2]);
        double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
        double x2 = x * Math.cos(b) + z1 * Math.sin(b), z2 = -x * Math.sin(b) + z1 * Math.cos(b);
        double x3 = x2 * Math.cos(g) - y1 * Math.sin(g), y3 = x2 * Math.sin(g) + y1 * Math.cos(g);
        v[0] = (float) (x3 + o[0]);
        v[1] = (float) (y3 + o[1]);
        v[2] = (float) (z2 + o[2]);
    }
}
