package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.Shadow;
import com.reality.mobcraft.item.UnseenHand;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the Witch's shadow hands (the approved concept, art/hands/hands.py): near-black purple arms that rise out of
 * the back, long bony fingers with glowing claw tips, and a soft violet glow round every piece.
 *
 *   Unseen Hand  two, four or six hands around the user; one reaches out to whoever is held. Only the user and
 *                players with the Witch's Shadow see them.
 *   Shadow Crush a small hand inside someone's chest, closed round a red heart that shrinks with every squeeze.
 *                Everyone sees it.
 */
public final class ShadowHandRenderer {

    private static final Identifier CORE = MobCraft.id("textures/entity/shadow_hand.png");
    private static final Identifier WHITE = MobCraft.id("textures/entity/shadow_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int GLOW = 0x38B060FF, CLAW = 0xFFC9A0FF, HAND = 0xFF452874, KNUCKLE = 0xFF5B3594, HEART = 0xFFB0142C, HEART_DARK = 0xFF6A0A1A;
    private static final float PX = 1 / 16f;

    /** One box to draw: its transform (already camera-relative, in blocks), its size in pixels, and its tint. */
    private record Box(Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int color) {}

    private ShadowHandRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ShadowHandRenderer::submit);
    }

    private static void submit(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float now = client.level.getGameTime() + partial;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        boolean seer = Shadow.has(client.player);
        List<Box> core = new ArrayList<>(), glow = new ArrayList<>(), lit = new ArrayList<>();
        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof Player p) {
                Long packed = p.getAttached(UnseenHand.HAND);
                if (packed != null && (p == client.player || seer)) unseen(client, p, packed, now, partial, camera, core, glow, lit);
            }
            if (e instanceof LivingEntity l) {
                Long start = l.getAttached(Shadow.CRUSH);
                if (start != null) crush(l, now - start, partial, camera, core, glow, lit);
            }
        }
        if (core.isEmpty() && lit.isEmpty()) return;
        PoseStack pose = context.poseStack();
        var collector = context.submitNodeCollector();
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(CORE), (p, buf) -> { for (Box b : core) emit(p, buf, b); });
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, buf) -> { for (Box b : lit) emit(p, buf, b); });
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (p, buf) -> { for (Box b : glow) emit(p, buf, b); });
    }

    // ---------------------------------------------------------------- Unseen Hand

    private static void unseen(Minecraft client, Player p, long packed, float now, float partial, Vec3 camera,
                               List<Box> core, List<Box> glow, List<Box> lit) {
        int level = Math.max(1, Math.min(3, UnseenHand.level(packed)));
        int targetId = UnseenHand.target(packed);
        Entity target = targetId >= 0 ? client.level.getEntity(targetId) : null;
        float age = now - UnseenHand.start(packed);
        float total = UnseenHand.duration(level, targetId >= 0);
        // Grow out of the back over a third of a second, sink back in over the last third.
        float grow = Mth.clamp(age / 6f, 0f, 1f) * Mth.clamp((total - age) / 6f, 0f, 1f);
        if (grow <= 0.01f) return;
        Vec3 feet = p.getPosition(partial);
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        int count = level * 2;
        for (int k = 0; k < count; k++) {
            boolean reacher = k == 0 && target != null;
            double ang = Math.toRadians(count == 1 ? 0 : -150 + 300.0 * k / (count - 1)) + Math.sin(now * 0.1 + k) * 0.08;
            if (targetId < 0) ang *= 1.0 + 0.25 * Math.sin(Math.min(1, age / 8f) * Math.PI);   // a miss flares out wide
            int side = Math.sin(ang) >= 0 ? 1 : -1;
            double h = new double[]{34, 22, 12}[k % 3], r = 26 + (k % 2) * 6;
            // Concept space is pixels with the player facing -z; turn it into the world round the body.
            Vec3 start = local(feet, fwd, right, side * 2.0, 24 - (k % 3) * 3, 2.5);
            Vec3 ctrl = local(feet, fwd, right, Math.sin(ang) * r * 0.5 + side * 6, h + 14, 12);
            Vec3 tip = local(feet, fwd, right, Math.sin(ang) * r, h, -Math.cos(ang) * r);
            Vec3 out = fwd.scale(Math.cos(ang)).add(right.scale(-Math.sin(ang))).add(0, 0.15, 0);
            float curl = new float[]{20, 35, 50}[k % 3], size = 0.95f - 0.08f * (k % 3);
            if (reacher) {
                Vec3 at = target.getPosition(partial).add(0, target.getBbHeight() * 0.55, 0);
                float reach = Mth.clamp(age / 8f, 0f, 1f);
                tip = tip.add(at.subtract(tip).scale(reach));
                Vec3 mid = start.add(tip).scale(0.5);
                ctrl = mid.add(0, 1.2 + mid.distanceTo(start) * 0.25, 0);
                out = at.subtract(start).normalize();
                boolean squeezing = level >= 2 && age >= UnseenHand.DRAG;
                curl = squeezing ? 62 + 6 * Mth.sin(now * 0.8f) : 45;
                size = 1.05f;
            }
            // Your own hands in first person: skip the arm pieces right at your eye so they don't fill the screen.
            double clip = p == client.player && client.options.getCameraType().isFirstPerson() ? 1.4 : 0;
            arm(start, ctrl, start.add(tip.subtract(start).scale(grow)), out, size * grow, curl, camera, k, core, glow, lit, clip);
        }
    }

    private static Vec3 local(Vec3 feet, Vec3 fwd, Vec3 right, double x, double y, double z) {
        return feet.add(right.scale(-x * PX)).add(0, y * PX, 0).add(fwd.scale(-z * PX));
    }

    // ---------------------------------------------------------------- Shadow Crush

    private static void crush(LivingEntity t, float age, float partial, Vec3 camera, List<Box> core, List<Box> glow, List<Box> lit) {
        float total = Shadow.CRUSH_TICKS;
        float grow = Mth.clamp(age / 8f, 0f, 1f) * Mth.clamp((total - age) / 6f, 0f, 1f);
        if (grow <= 0.01f) return;
        Vec3 feet = t.getPosition(partial);
        float yaw = Mth.rotLerp(partial, t.yBodyRotO, t.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        float scale = t.getBbHeight() / 1.8f;
        Vec3 heart = feet.add(0, t.getBbHeight() * 0.68, 0).add(fwd.scale(t.getBbWidth() * 0.5 + 0.06));
        // Each squeeze (every half second) crushes the heart a little smaller, and it beats between them.
        float squeezed = Mth.clamp(age / total, 0f, 1f);
        float beat = 1f + 0.12f * Mth.sin(age * 0.6f);
        float hs = (1.6f - 0.7f * squeezed) * beat * scale * grow;
        Matrix4f hm = frame(heart.subtract(camera), new Vec3(0, 1, 0), fwd);
        hm.scale(hs);
        int c1 = HEART, c2 = HEART_DARK;
        lit.add(new Box(hm, -2.2f, 0.5f, -1.2f, -0.2f, 2.6f, 1.2f, c1));    // two lobes on top
        lit.add(new Box(hm, 0.2f, 0.5f, -1.2f, 2.2f, 2.6f, 1.2f, c1));
        lit.add(new Box(hm, -1.9f, -1.0f, -1.1f, 1.9f, 0.6f, 1.1f, c1));    // narrowing to the point
        lit.add(new Box(hm, -1.1f, -2.2f, -1.0f, 1.1f, -1.0f, 1.0f, c2));
        lit.add(new Box(hm, -0.4f, -3.0f, -0.6f, 0.4f, -2.2f, 0.6f, c2));
        // The hand comes out of the chest from behind the heart and closes round it.
        // It rises from low in the chest, so the last bit of arm points up and the fingers come up in front of
        // the heart with the palm facing it, then curl round it.
        Vec3 start = heart.subtract(fwd.scale(t.getBbWidth() * 0.55)).add(0, -0.5 * scale, 0);
        Vec3 tip = heart.add(fwd.scale(0.12)).add(0, -0.26 * scale, 0);
        Vec3 ctrl = tip.add(0, -0.25 * scale, 0).add(fwd.scale(-0.02));
        float curl = 50 + 25 * squeezed + 6 * Mth.sin(age * 0.6f);
        arm(start, ctrl, tip, fwd.scale(-1), 0.5f * scale * grow, curl, camera, 7, core, glow, lit, 0);
    }

    // ---------------------------------------------------------------- one shadow arm and hand

    /** A curved arm from start bending through ctrl to tip, then the hand with its palm facing out. Sizes in pixels. */
    private static void arm(Vec3 start, Vec3 ctrl, Vec3 tip, Vec3 out, float size, float curl, Vec3 camera, int seed,
                            List<Box> core, List<Box> glow, List<Box> lit, double clip) {
        if (size <= 0.02f) return;
        int n = 7;
        Vec3 prev = start, dir = null;
        for (int k = 1; k <= n; k++) {
            float u = k / (float) n;
            Vec3 pt = start.scale((1 - u) * (1 - u)).add(ctrl.scale(2 * (1 - u) * u)).add(tip.scale(u * u));
            dir = pt.subtract(prev);
            double len = dir.length();
            if (len > 1e-4 && (clip <= 0 || pt.distanceTo(camera) > clip)) {
                float w = (4.2f - 1.6f * (k - 1) / n) * size;
                Matrix4f m = frame(prev.subtract(camera), dir, out);
                float l = (float) (len / PX) + 0.6f * size;
                seg(m, l, w, w, core, glow);
            }
            prev = pt;
        }
        if (dir == null || dir.lengthSqr() < 1e-8) return;
        // The hand, exactly as in the approved concept: wrist, big palm, four long clawed fingers and a thumb.
        // Drawn in flat colour with a thin glow: the fingers are too thin for the arm texture, which turned them black,
        // and a thick glow merged them into one block.
        Matrix4f h = frame(tip.subtract(camera), dir, out);
        h.scale(size);
        shade(new Matrix4f(h), 2, 3.2f, 2.4f, lit, glow, 0.5f, HAND);
        h.translate(0, 2, 0);
        shade(new Matrix4f(h), 7, 7.5f, 2.2f, lit, glow, 0.5f, HAND);
        Matrix4f palm = new Matrix4f(h).translate(0, 7, 0);
        float spread = 12;
        for (int i = 0; i < 4; i++) {
            Matrix4f f = new Matrix4f(palm).translate((-2.7f + i * 1.8f), 0, 0).rotateZ((i - 1.5f) * -spread * Mth.DEG_TO_RAD);
            float[] lens = {5.5f + (i == 1 || i == 2 ? 1.2f : 0), 4.5f, 3.2f};
            for (int j = 0; j < 3; j++) {
                float w = 1.5f - j * 0.2f;
                shade(new Matrix4f(f), lens[j], w, w, lit, glow, 0.35f, j == 1 ? KNUCKLE : HAND);
                f.translate(0, lens[j], 0).rotateX(-curl * Mth.DEG_TO_RAD);
            }
            lit.add(new Box(f, -0.45f, 0, -0.45f, 0.45f, 2.2f, 0.45f, CLAW));
        }
        Matrix4f th = new Matrix4f(h).translate(-3.8f, 2.5f, 0).rotateZ(55 * Mth.DEG_TO_RAD).rotateX(-10 * Mth.DEG_TO_RAD);
        for (int j = 0; j < 2; j++) {
            float L = j == 0 ? 4 : 3.5f;
            shade(new Matrix4f(th), L, 1.6f - j * 0.2f, 1.6f - j * 0.2f, lit, glow, 0.35f, j == 1 ? KNUCKLE : HAND);
            th.translate(0, L, 0).rotateX(-curl * 0.8f * Mth.DEG_TO_RAD);
        }
        lit.add(new Box(th, -0.45f, 0, -0.45f, 0.45f, 2.0f, 0.45f, CLAW));
    }

    /** One piece of arm or finger along +y: the dark core and its glow shell. */
    private static void seg(Matrix4f m, float len, float w, float d, List<Box> core, List<Box> glow) {
        seg(m, len, w, d, core, glow, 0.5f);
    }

    /** A hand piece in a flat colour: fingers are too thin for the arm texture to show, it just turns black. */
    private static void shade(Matrix4f m, float len, float w, float d, List<Box> lit, List<Box> glow, float pad, int color) {
        lit.add(new Box(m, -w / 2, 0, -d / 2, w / 2, len, d / 2, color));
        glow.add(new Box(m, -w / 2 - pad, -0.3f, -d / 2 - pad, w / 2 + pad, len + 0.3f, d / 2 + pad, GLOW));
    }

    /** pad is how far the glow reaches past the core; fingers get a thin one so they don't blur into one block. */
    private static void seg(Matrix4f m, float len, float w, float d, List<Box> core, List<Box> glow, float pad) {
        core.add(new Box(m, -w / 2, 0, -d / 2, w / 2, len, d / 2, 0xFFFFFFFF));
        glow.add(new Box(m, -w / 2 - pad, -0.3f, -d / 2 - pad, w / 2 + pad, len + 0.3f, d / 2 + pad, GLOW));
    }

    /** A transform at pos whose +y runs along y and whose -z faces out (the palm side), scaled to pixels. */
    private static Matrix4f frame(Vec3 pos, Vec3 y, Vec3 out) {
        Vector3f yy = new Vector3f((float) y.x, (float) y.y, (float) y.z).normalize();
        Vector3f oo = new Vector3f((float) out.x, (float) out.y, (float) out.z);
        Vector3f xx = new Vector3f(yy).cross(oo);
        if (xx.lengthSquared() < 1e-6) xx = new Vector3f(yy).cross(new Vector3f(0, 0, 1));
        if (xx.lengthSquared() < 1e-6) xx = new Vector3f(1, 0, 0);
        xx.normalize();
        Vector3f zz = new Vector3f(xx).cross(yy).normalize();
        Matrix4f m = new Matrix4f(
                xx.x, xx.y, xx.z, 0,
                yy.x, yy.y, yy.z, 0,
                zz.x, zz.y, zz.z, 0,
                (float) pos.x, (float) pos.y, (float) pos.z, 1);
        return m.scale(PX);
    }

    private static final float[][][] FACES = {
            {{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}}, {{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}},
            {{0, 1, 1}, {0, 1, 0}, {0, 0, 0}, {0, 0, 1}}, {{1, 1, 0}, {1, 1, 1}, {1, 0, 1}, {1, 0, 0}},
            {{1, 1, 1}, {0, 1, 1}, {0, 1, 0}, {1, 1, 0}}, {{1, 0, 0}, {0, 0, 0}, {0, 0, 1}, {1, 0, 1}}};
    private static final float[][] UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    private static final float[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private static void emit(PoseStack.Pose pose, VertexConsumer buf, Box b) {
        Matrix4f m = new Matrix4f(pose.pose()).mul(b.m());
        Vector3f p = new Vector3f();
        for (int f = 0; f < 6; f++) {
            Vector3f n = pose.transformNormal(NORMALS[f][0], NORMALS[f][1], NORMALS[f][2], new Vector3f());
            for (int i = 0; i < 4; i++) {
                float[] c = FACES[f][i];
                m.transformPosition(c[0] == 0 ? b.x0() : b.x1(), c[1] == 0 ? b.y0() : b.y1(), c[2] == 0 ? b.z0() : b.z1(), p);
                buf.addVertex(p.x, p.y, p.z, b.color(), UV[i][0], UV[i][1], OverlayTexture.NO_OVERLAY, FULL_BRIGHT, n.x, n.y, n.z);
            }
        }
    }
}
