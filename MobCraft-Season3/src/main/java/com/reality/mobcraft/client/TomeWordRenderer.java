package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.TomePowers;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The models for two of the Stronghold Tome's powers (item/TomePowers), drawn on whoever they're on, for everyone.
 *
 * Word of Silence: the mouth is sealed. A square band of glowing runes is written round the head at mouth height,
 * one letter after another; two small lavender chains cross over the mouth to lock it; and a halo of runes turns
 * slowly above the head. The letters flicker from one to the next. It all fades out when the silence ends.
 *
 * Unwriting: the words come off. Rune letters lying flat on the body peel away, drift up and out, tumble, turn
 * grey and dissolve, in a rush at first and then a few at a time while the enchantments are gone. In the last
 * second they fly back in and settle onto the body again as the enchantments write themselves back.
 *
 * Uses the Tome's own rune strip and the lavender chain links from the Binding Word.
 */
public final class TomeWordRenderer {

    private static final Identifier RUNES = MobCraft.id("textures/entity/tome_runes.png");
    private static final Identifier CHAIN = MobCraft.id("textures/entity/lavender_chain.png");
    private static final Identifier GLOW = MobCraft.id("textures/entity/lavender_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    /** One rune quad: centre, half-width vector, half-height vector, glyph, colour (ARGB). */
    private record Rune(Vec3 at, Vec3 right, Vec3 up, int glyph, int color) {}

    private TomeWordRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(TomeWordRenderer::submit);
    }

    private static void submit(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float now = client.level.getGameTime() + partial;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        boolean firstPerson = client.options.getCameraType().isFirstPerson();
        List<Rune> runes = new ArrayList<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            Long silenced = entity.getAttached(TomePowers.SILENCED);
            // Your own seal would sit right across your view in first person, so it's only drawn from outside.
            if (silenced != null && !(firstPerson && entity == client.getCameraEntity())) {
                silence(context, living, silenced - now, partial, now, camera, runes);
            }
            Long unwriting = entity.getAttached(TomePowers.UNWRITING);
            if (unwriting != null) unwrite(living, unwriting - now, partial, now, camera, runes);
        }
        if (!runes.isEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(RUNES),
                    (pose, buffer) -> { for (Rune r : runes) quad(pose, buffer, r); });
        }
    }

    private static int argb(float alpha, int rgb) {
        return (int) (255 * Mth.clamp(alpha, 0f, 1f)) << 24 | rgb;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255),
                bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    // ---- Word of Silence ----

    private static void silence(LevelRenderContext context, LivingEntity e, float left, float partial, float now, Vec3 camera, List<Rune> out) {
        if (left <= 0) return;
        float age = TomePowers.SILENCE_TICKS - left;
        float fade = Mth.clamp(left / 10f, 0f, 1f);
        Vec3 feet = e.getPosition(partial);
        float yaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) * Mth.DEG_TO_RAD;
        Vec3 front = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0, front.x);
        Vec3 up = new Vec3(0, 1, 0);
        float hw = e.getBbWidth() * 0.42f;                         // half the head's width; 0.25 on a player
        float s = hw / 0.25f;
        Vec3 mouth = feet.add(0, e.getEyeHeight() - 0.11 * s, 0);
        int seed = e.getId();

        // The band: three runes along each of the head's four sides, written in one after another.
        float r = hw + 0.03f, half = hw / 3f;
        Vec3[] normals = {front, right, front.scale(-1), right.scale(-1)};
        for (int side = 0; side < 4; side++) {
            Vec3 n = normals[side], along = normals[(side + 1) % 4];
            for (int k = 0; k < 3; k++) {
                int i = side * 3 + k;
                float written = Mth.clamp((age - i * 0.7f) / 3f, 0f, 1f);
                if (written <= 0f) continue;
                Vec3 at = mouth.add(n.scale(r)).add(along.scale((k - 1) * half * 2));
                float pulse = 0.7f + 0.3f * Mth.sin(now * 0.3f + i * 1.3f);
                int glyph = Math.floorMod((int) (now / 7f) + i * 3 + seed, 8);
                float grow = 0.6f + 0.4f * written;
                out.add(new Rune(at.subtract(camera), along.scale(-half * grow), up.scale(half * grow), glyph,
                        argb(written * pulse * fade, 0xE9D5FF)));
            }
        }

        // The lock: two small chains crossing over the mouth, drawn out from the corners as the band closes.
        float lock = Mth.clamp((age - 8f) / 6f, 0f, 1f);
        if (lock > 0f && fade > 0.3f) {
            Vec3 c = mouth.add(front.scale(r + 0.035));
            Vec3 a0 = c.add(right.scale(-hw)).add(0, hw * 0.55, 0), a1 = c.add(right.scale(hw)).add(0, -hw * 0.55, 0);
            Vec3 b0 = c.add(right.scale(hw)).add(0, hw * 0.55, 0), b1 = c.add(right.scale(-hw)).add(0, -hw * 0.55, 0);
            float drift = now * 0.03f;
            RenderType type = RenderTypes.entityTranslucent(CHAIN);
            RenderType glow = RenderTypes.energySwirl(GLOW, drift % 1f, (drift * 0.7f) % 1f);
            chain(context, type, glow, a0.subtract(camera), a1.subtract(a0).scale(lock), 0.085f * s);
            chain(context, type, glow, b0.subtract(camera), b1.subtract(b0).scale(lock), 0.085f * s);
        }

        // The halo: a ring of runes turning slowly above the head, bobbing a little.
        float halo = Mth.clamp((age - 4f) / 10f, 0f, 1f) * fade;
        if (halo > 0f) {
            Vec3 centre = feet.add(0, e.getBbHeight() + 0.22 * s + Mth.sin(now * 0.1f) * 0.03, 0);
            float radius = hw * 1.7f, size = hw * 0.36f;
            int count = 10;
            for (int i = 0; i < count; i++) {
                double a = now * 0.06 + Math.PI * 2 * i / count;
                Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 tangent = new Vec3(-o.z, 0, o.x);
                int glyph = Math.floorMod(i * 5 + seed + (int) (now / 11f), 8);
                float pulse = 0.6f + 0.4f * Mth.sin(now * 0.2f + i * 2.1f);
                out.add(new Rune(centre.add(o.scale(radius)).subtract(camera), tangent.scale(size), up.scale(size), glyph,
                        argb(halo * pulse, 0xD8B4FE)));
            }
        }
    }

    /** A run of small chain links from start along a vector. */
    private static void chain(LevelRenderContext context, RenderType type, RenderType glow, Vec3 start, Vec3 along, float link) {
        double length = along.length();
        if (length < 0.02) return;
        int pieces = Math.max(1, (int) Math.round(length / link));
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
            pose.scale(link, stretch, link);
            context.submitNodeCollector().submitModelPart(model, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.scale(1.18f, 1f, 1.18f);
            context.submitNodeCollector().submitModelPart(model, pose, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
        }
    }

    // ---- Unwriting ----

    private static void unwrite(LivingEntity e, float left, float partial, float now, Vec3 camera, List<Rune> out) {
        if (left <= -2) return;
        float age = TomePowers.UNWRITE_TICKS - left;
        Vec3 feet = e.getPosition(partial);
        float yaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 front = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-front.z, 0, front.x);
        Vec3[] normals = {front, right, front.scale(-1), right.scale(-1)};
        float w = e.getBbWidth() * 0.5f + 0.02f, h = e.getBbHeight();
        float size = Math.max(0.1f, e.getBbWidth() * 0.24f);
        RandomSource rng = RandomSource.create(e.getId() * 7919L);
        int count = 44;
        for (int i = 0; i < count; i++) {
            // Where this word sits on the body: one of the four sides, somewhere across it, somewhere up it.
            int side = rng.nextInt(4);
            float across = (rng.nextFloat() - 0.5f) * 1.5f, height = 0.12f + rng.nextFloat() * 0.8f;
            float delay = rng.nextFloat() * 28f, spin = (rng.nextFloat() - 0.5f) * 5f, drift = 0.35f + rng.nextFloat() * 0.5f;
            float cycle = 50f + rng.nextFloat() * 40f;
            int glyph = rng.nextInt(8);
            Vec3 n = normals[side], along = normals[(side + 1) % 4];
            Vec3 on = feet.add(n.scale(w)).add(along.scale(across * w)).add(0, height * h, 0);

            float p;         // 0 = flat on the body, 1 = gone
            float strength;  // how solid it is at its strongest
            if (left <= 22f) {
                // Writing back: the words fly in from outside and settle.
                p = Mth.clamp((left - (delay / 28f) * 6f) / 16f, 0f, 1f);
                strength = 1f;
            } else if (age < delay + 40f) {
                p = Mth.clamp((age - delay) / 40f, 0f, 1f);                  // the first rush
                strength = 1f;
            } else {
                p = ((age - delay) % cycle) / 30f;                           // a few still flaking off
                strength = 0.8f;
                if (p > 1f) continue;
            }
            float ease = p * p * (3 - 2 * p);
            Vec3 at = on.add(n.scale(ease * drift)).add(0, ease * (0.5 + drift * 0.6), 0)
                    .add(along.scale(Mth.sin(p * 3f + i) * 0.08 * ease));
            // It lies flat on the body, then tumbles as it lifts off.
            float turn = ease * spin;
            Vec3 r = along.scale(Mth.cos(turn)).add(n.scale(Mth.sin(turn)));
            Vec3 u = new Vec3(0, Mth.cos(turn * 0.6f), 0).add(n.scale(Mth.sin(turn * 0.6f) * 0.6));
            float alpha = strength * (1f - ease * ease) * Mth.clamp(p * 8f + (left <= 22f ? 1f : 0.6f), 0f, 1f);
            if (alpha <= 0.02f) continue;
            float shrink = size * (1f - 0.35f * ease);
            out.add(new Rune(at.subtract(camera), r.scale(-shrink), u.scale(shrink), glyph,
                    argb(alpha, mix(0xF3E8FF, 0x8A8A96, ease))));
        }
    }

    /** One rune, two-sided so it shows from any side. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Rune q) {
        float u0 = q.glyph() / 8f, u1 = (q.glyph() + 1) / 8f;
        Vec3 normal = q.right().cross(q.up()).normalize();
        Vector3f p = new Vector3f();
        float[][] c = {{-1, 1, u0, 0}, {1, 1, u1, 0}, {1, -1, u1, 1}, {-1, -1, u0, 1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                float[] v = c[pass == 0 ? k : 3 - k];
                pose.pose().transformPosition(
                        (float) (q.at().x + q.right().x * v[0] + q.up().x * v[1]),
                        (float) (q.at().y + q.right().y * v[0] + q.up().y * v[1]),
                        (float) (q.at().z + q.right().z * v[0] + q.up().z * v[1]), p);
                buffer.addVertex(p.x, p.y, p.z, q.color(), v[2], v[3], OverlayTexture.NO_OVERLAY, FULL_BRIGHT,
                        (float) normal.x, (float) normal.y, (float) normal.z);
            }
        }
    }
}
