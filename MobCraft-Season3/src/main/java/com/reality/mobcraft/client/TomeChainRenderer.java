package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.TomeBinding;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Binding Word (item/TomeBinding): lavender chains wound square and tight round each leg of a bound entity, shrink-wrapped
 * to the leg from ankle to knee, with a chain from each ankle down into the ground. Glowing runes ride the links,
 * flickering from one letter to the next. The coils are written link by link over half a second when it lands.
 * Every hit that cracks them breaks one of the ground chains away, so you can see how close they are to breaking.
 * Uses the Evoker chain's links, small, in lavender, with a soft glow drifting over them.
 */
public final class TomeChainRenderer {

    private static final Identifier TEXTURE = MobCraft.id("textures/entity/lavender_chain.png");
    private static final Identifier GLOW = MobCraft.id("textures/entity/lavender_glow.png");
    private static final Identifier RUNES = MobCraft.id("textures/entity/tome_runes.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float LINK = 0.15f;          // small links, wrapped tight
    private static final Map<Integer, Float> SINCE = new HashMap<>();

    private TomeChainRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(TomeChainRenderer::submit);
    }

    private static void submit(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float now = client.level.getGameTime() + partial;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        RenderType type = RenderTypes.entityTranslucent(TEXTURE);
        float drift = now * 0.03f;
        RenderType glow = RenderTypes.energySwirl(GLOW, drift % 1f, (drift * 0.7f) % 1f);
        java.util.List<float[]> runes = new java.util.ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            Integer packed = entity.getAttached(TomeBinding.BOUND);
            if (packed == null) {
                SINCE.remove(entity.getId());
                continue;
            }
            float born = SINCE.computeIfAbsent(entity.getId(), k -> now);
            float grow = Mth.clamp((now - born) / 10f, 0f, 1f);    // written in over half a second
            int total = packed >> 4, left = packed & 15;
            int anchors = Math.max(1, Math.round(4f * left / Math.max(1, total)));
            Vec3 feet = entity.getPosition(partial);
            float yaw = (entity instanceof net.minecraft.world.entity.LivingEntity l
                    ? Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) : entity.getYRot()) * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));     // the body's right, from its yaw
            // Two legs, sized from the body: a player's are 4 px square, 2 px either side of the middle, 12 px tall.
            float w = entity.getBbWidth(), legHalf = w * 0.21f, gap = w * 0.21f, legTop = entity.getBbHeight() * 0.36f;
            float h = legHalf + 0.025f;                                  // the chain sits flat on the leg's four sides
            Vec3 fwd = new Vec3(-right.z, 0, right.x);
            int seed = entity.getId();
            for (int side = -1; side <= 1; side += 2) {
                Vec3 leg = feet.add(right.scale(side * gap));
                // A square coil hugging the leg from ankle to knee: two links along each side, corner to corner,
                // two and a half times round, climbing as it goes.
                int perTurn = 8;
                int n = perTurn * 5 / 2;
                Vec3 prev = null;
                int drawn = (int) (n * grow);
                for (int i = 0; i <= drawn; i++) {
                    float t = i / (float) n;
                    int k = Math.floorMod(side > 0 ? i : -i, perTurn);
                    // corners at even steps, middles of the sides at odd steps, going round the square
                    float[][] sq = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
                    float cx = sq[k][0] * h, cz = sq[k][1] * h;
                    Vec3 p = leg.add(right.scale(cx)).add(fwd.scale(cz)).add(0, 0.06 + t * (legTop - 0.1), 0);
                    if (prev != null) {
                        segment(context, type, glow, prev.subtract(camera), p.subtract(prev));
                        if (k % 2 == 1) {
                            // a rune in the middle of each side, flat on it, flickering through the letters
                            Vec3 out = right.scale(sq[k][0]).add(fwd.scale(sq[k][1])).normalize();
                            Vec3 at = p.add(out.scale(0.03)).subtract(camera);
                            int glyph = Math.floorMod((int) (now / 6f) + i * 3 + side + seed, 8);
                            float pulse = 0.55f + 0.45f * Mth.sin(now * 0.25f + i);
                            runes.add(new float[]{(float) at.x, (float) at.y, (float) at.z, (float) out.x, (float) out.z, glyph, pulse});
                        }
                    }
                    prev = p;
                }
                // Two chains from this ankle down into the ground, front and back. One breaks per hit.
                for (int k = 0; k < 2; k++) {
                    int index = (side < 0 ? 0 : 2) + k;
                    if (index >= anchors) continue;
                    Vec3 corner = right.scale(side * h).add(fwd.scale(k == 0 ? h : -h));
                    Vec3 dir = corner.normalize();
                    Vec3 from = leg.add(corner).add(0, 0.06, 0);
                    Vec3 to = leg.add(corner).add(dir.scale(0.5)).add(0, -0.1, 0);
                    segment(context, type, glow, from.subtract(camera), to.subtract(from).scale(grow));
                }
            }
        }
        if (!runes.isEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(RUNES),
                    (pose, buffer) -> {
                        for (float[] q : runes) rune(pose, buffer, q);
                    });
        }
        if (SINCE.size() > 256) SINCE.clear();
    }

    /** One glowing rune, a little square standing out from the leg. */
    private static void rune(com.mojang.blaze3d.vertex.PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, float[] q) {
        float x = q[0], y = q[1], z = q[2], ox = q[3], oz = q[4];
        float half = 0.055f;
        float rx = -oz * half, rz = ox * half;              // sideways along the leg's surface
        float u0 = q[5] / 8f, u1 = (q[5] + 1) / 8f;
        int alpha = (int) (255 * q[6]);
        int color = alpha << 24 | 0xFFFFFF;
        org.joml.Vector3f p = new org.joml.Vector3f();
        float[][] c = {{-1, 1, u1, 0}, {1, 1, u0, 0}, {1, -1, u0, 1}, {-1, -1, u1, 1}};
        for (int pass = 0; pass < 2; pass++) {               // both faces, so it shows from any side
            for (int k = 0; k < 4; k++) {
                float[] v = c[pass == 0 ? k : 3 - k];
                pose.pose().transformPosition(x + rx * v[0], y + half * v[1], z + rz * v[0], p);
                buffer.addVertex(p.x, p.y, p.z, color, v[2], v[3], OverlayTexture.NO_OVERLAY, FULL_BRIGHT, ox, 0, oz);
            }
        }
    }

    /** A run of small links from start along a vector. */
    static void segment(LevelRenderContext context, RenderType type, RenderType glow, Vec3 start, Vec3 along) {
        double length = along.length();
        if (length < 0.02) return;
        int pieces = Math.max(1, (int) Math.round(length / LINK));
        float stretch = (float) (length / pieces);
        Vec3 dir = along.scale(1 / length);
        Quaternionf turn = new Quaternionf().rotationTo(0f, 1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z);
        ModelPart model = EvokerChainRenderer.linkModel();
        PoseStack pose = context.poseStack();
        for (int i = 0; i < pieces; i++) {
            pose.pushPose();
            Vec3 at = start.add(dir.scale(i * stretch));
            pose.translate(at.x, at.y, at.z);
            pose.rotate(turn);
            pose.scale(LINK, stretch, LINK);
            context.submitNodeCollector().submitModelPart(model, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.scale(1.18f, 1f, 1.18f);
            context.submitNodeCollector().submitModelPart(model, pose, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
        }
    }
}
