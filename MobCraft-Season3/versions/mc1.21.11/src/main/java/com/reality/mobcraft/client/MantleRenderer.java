package com.reality.mobcraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Strongholder's Mantle: while someone reads the Stronghold Tome they're wrapped in a grey trench-coat robe
 * with a deep hood, popped gold-rimmed collar, lapels, a belt with a gold buckle, a long rune-marked cloak flaring
 * behind and its own sleeves, and a halo of runes turning over their head. Everyone sees it.
 *
 * Opening the book writes it on: a purple shimmer sweeps up from the feet and the mantle appears behind it.
 * Closing the book wipes it off from the top down the same way. The hood follows the head, the sleeves follow the
 * arms (so they come up with the book), and the rest follows the body.
 *
 * The mesh is the approved low-detail concept (167 faces), exported to assets/mobcraft/mantle/mesh.json with its
 * texture atlas; all of its pixels are the mod's own.
 */
public final class MantleRenderer {

    public static final RenderStateDataKey<Float> SHOWN = RenderStateDataKey.create(() -> "mobcraft mantle shown");
    public static final RenderStateDataKey<Boolean> WRITING = RenderStateDataKey.create(() -> "mobcraft mantle writing on");
    public static final RenderStateDataKey<Float> TIME = RenderStateDataKey.create(() -> "mobcraft mantle time");
    public static final RenderStateDataKey<Boolean> HELMET = RenderStateDataKey.create(() -> "mobcraft mantle helmet");

    private static final Identifier TEXTURE = MobCraft.id("textures/entity/strongholder_mantle.png");
    private static final Identifier RUNES = MobCraft.id("textures/entity/tome_runes.png");
    private static final Identifier GLOW = MobCraft.id("textures/entity/lavender_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float ON_TICKS = 22f, OFF_TICKS = 16f;

    /** One quad: four corners (x, y, z in blocks, model space) with uv, its normal, and its height 0..1 for the sweep. */
    private record Quad(float[][] v, float nx, float ny, float nz, float h) {}

    private static Map<String, List<Quad>> mesh;
    private static boolean failed;
    private record Fade(float shown, float since, boolean on) {}
    private static final Map<Integer, Fade> FADE = new HashMap<>();

    private MantleRenderer() {}

    private static Map<String, List<Quad>> mesh() {
        if (mesh != null || failed) return mesh;
        try (var in = Minecraft.getInstance().getResourceManager().open(MobCraft.id("mantle/mesh.json"))) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
            Map<String, List<Quad>> m = new HashMap<>();
            for (String group : new String[]{"head", "body", "right_arm", "left_arm"}) {
                List<Quad> list = new ArrayList<>();
                for (JsonElement q : root.getAsJsonArray(group)) {
                    JsonArray vs = q.getAsJsonObject().getAsJsonArray("v");
                    float[][] v = new float[4][5];
                    for (int i = 0; i < 4; i++) for (int k = 0; k < 5; k++) v[i][k] = vs.get(i).getAsJsonArray().get(k).getAsFloat();
                    Vector3f a = new Vector3f(v[1][0] - v[0][0], v[1][1] - v[0][1], v[1][2] - v[0][2]);
                    Vector3f b = new Vector3f(v[3][0] - v[0][0], v[3][1] - v[0][1], v[3][2] - v[0][2]);
                    Vector3f n = a.cross(b);
                    if (n.lengthSquared() > 0) n.normalize().negate();   // the export mirrors y, which flips the winding
                    list.add(new Quad(v, n.x, n.y, n.z, q.getAsJsonObject().get("h").getAsFloat()));
                }
                m.put(group, list);
            }
            mesh = m;
        } catch (Exception e) {
            failed = true;
            MobCraft.LOGGER.warn("Couldn't load the Strongholder's Mantle mesh", e);
        }
        return mesh;
    }

    /** Reloading resources picks up a changed mesh. */
    public static void reload() { mesh = null; failed = false; }

    private static boolean reading(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().is(ModItems.STRONGHOLD_TOME);
    }

    /** Works out how much of the mantle is on, and whether it's being written on or wiped off. */
    public static void extract(Avatar entity, AvatarRenderState state, float partial) {
        float now = entity.tickCount + partial;
        boolean on = entity instanceof LivingEntity l && reading(l);
        Fade f = FADE.get(entity.getId());
        if (f == null) {
            if (!on) { state.setData(SHOWN, 0f); return; }
            f = new Fade(0f, now, true);
        }
        float shown = on ? Mth.clamp(f.shown() + (now - f.since()) / ON_TICKS, 0f, 1f)
                : Mth.clamp(f.shown() - (now - f.since()) / OFF_TICKS, 0f, 1f);
        if (!on && shown <= 0f) { FADE.remove(entity.getId()); state.setData(SHOWN, 0f); return; }
        FADE.put(entity.getId(), new Fade(shown, now, on));
        state.setData(SHOWN, shown);
        state.setData(WRITING, on);
        state.setData(TIME, now);
        // A helmet or pumpkin takes the hood's place, so the hood doesn't poke out around it.
        state.setData(HELMET, entity instanceof LivingEntity l2 && !l2.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty());
        if (FADE.size() > 256) FADE.clear();
    }

    public static void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel model) {
        Float shown = state.getData(SHOWN);
        if (shown == null || shown <= 0f) return;
        Map<String, List<Quad>> m = mesh();
        if (m == null) return;
        boolean writing = Boolean.TRUE.equals(state.getData(WRITING));
        float time = state.getData(TIME) == null ? 0f : state.getData(TIME);
        // The sweep line: writing on it climbs from the feet to the top, wiping off it drops from the top down.
        float edge = writing ? shown * 1.25f - 0.1f : 1.15f - (1f - shown) * 1.25f;
        RenderType cloth = RenderTypes.entityTranslucent(TEXTURE);
        float drift = time * 0.02f;
        RenderType shimmer = RenderTypes.energySwirl(GLOW, drift % 1f, (drift * 0.7f) % 1f);
        if (!Boolean.TRUE.equals(state.getData(HELMET)))
            group(pose, collector, model.head, m.get("head"), cloth, shimmer, light, edge, writing);
        group(pose, collector, model.body, m.get("body"), cloth, shimmer, light, edge, writing);
        group(pose, collector, model.rightArm, m.get("right_arm"), cloth, shimmer, light, edge, writing);
        group(pose, collector, model.leftArm, m.get("left_arm"), cloth, shimmer, light, edge, writing);
        halo(pose, collector, time, shown);
    }

    /** How solid a quad at this height is, and how strongly the shimmer is on it, for the current sweep. */
    private static float[] sweep(float h, float edge, boolean writing) {
        // Writing on: everything below the edge is there. Wiping off: everything below the edge is still there.
        float solid = Mth.clamp((edge - h) / 0.12f, 0f, 1f);
        float glow = Mth.clamp(1f - Math.abs(edge - h) / 0.16f, 0f, 1f);
        return new float[]{solid, glow};
    }

    private static void group(PoseStack pose, SubmitNodeCollector collector, ModelPart part, List<Quad> quads,
                              RenderType cloth, RenderType shimmer, int light, float edge, boolean writing) {
        if (quads == null || quads.isEmpty()) return;
        pose.pushPose();
        part.translateAndRotate(pose);
        int lit = light;
        collector.submitCustomGeometry(pose, cloth, (p, buffer) -> {
            for (Quad q : quads) {
                float[] s = sweep(q.h(), edge, writing);
                if (s[0] <= 0.01f) continue;
                emit(p, buffer, q, (int) (255 * s[0]) << 24 | 0xFFFFFF, lit, 1.0f);
            }
        });
        collector.submitCustomGeometry(pose, shimmer, (p, buffer) -> {
            for (Quad q : quads) {
                float[] s = sweep(q.h(), edge, writing);
                if (s[1] <= 0.01f) continue;
                int c = (int) (255 * s[1]);
                emit(p, buffer, q, 0xFF000000 | (c * 0xC0 / 255) << 16 | (c * 0x78 / 255) << 8 | c, FULL_BRIGHT, 1.03f);
            }
        });
        pose.popPose();
    }

    private static void emit(PoseStack.Pose p, VertexConsumer buffer, Quad q, int color, int light, float grow) {
        Vector3f out = new Vector3f();
        Vector3f n = p.transformNormal(q.nx(), q.ny(), q.nz(), new Vector3f());
        for (float[] v : q.v()) {
            p.pose().transformPosition(v[0] * grow, v[1] * grow, v[2] * grow, out);
            buffer.addVertex(out.x, out.y, out.z, color, v[3], v[4], OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
        }
    }

    /** Eight runes turning slowly over the head, bobbing. */
    private static void halo(PoseStack pose, SubmitNodeCollector collector, float time, float shown) {
        float alpha = Mth.clamp((shown - 0.6f) / 0.4f, 0f, 1f);
        if (alpha <= 0f) return;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(RUNES), (p, buffer) -> {
            Vector3f out = new Vector3f();
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8 + time * 0.05;
                float cx = (float) Math.cos(a) * 0.47f, cz = (float) Math.sin(a) * 0.47f;
                float y = -0.66f + Mth.sin((float) a * 2 + time * 0.15f) * 0.04f;
                float rx = -cz / 0.47f * 0.09f, rz = cx / 0.47f * 0.09f, half = 0.09f;
                int glyph = Math.floorMod(k + (int) (time / 9f), 8);
                float u0 = glyph / 8f, u1 = (glyph + 1) / 8f;
                int color = (int) (255 * alpha * (0.7f + 0.3f * Mth.sin(time * 0.2f + k))) << 24 | 0xFFFFFF;
                float[][] c = {{-1, -1, u0, 0}, {1, -1, u1, 0}, {1, 1, u1, 1}, {-1, 1, u0, 1}};
                for (int pass = 0; pass < 2; pass++) {
                    for (int i = 0; i < 4; i++) {
                        float[] v = c[pass == 0 ? i : 3 - i];
                        p.pose().transformPosition(cx + rx * v[0], y + half * v[1], cz + rz * v[0], out);
                        buffer.addVertex(out.x, out.y, out.z, color, v[2], v[3], OverlayTexture.NO_OVERLAY, FULL_BRIGHT, 0, -1, 0);
                    }
                }
            }
        });
    }
}
