package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.net.EssenceOrbPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.object.projectile.WindChargeModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The red essence orb that floats up out of a player when their Broken Totem crumbles
 * (EvokerEssence), sent by the server as an EssenceOrbPayload.
 *
 * It's shaped like a thrown wind charge, the game's own model, in red
 * (textures/entity/red_orb.png), with two differences: a glowing red core in the middle, and
 * a second, larger wind ring spinning the other way around it. It pops in, rises about two
 * and a half blocks over two seconds while spinning and leaving a trail of red dust, then
 * shrinks and fades away.
 */
public final class EssenceOrbs {

    private static final Identifier TEXTURE = MobCraft.id("textures/entity/red_orb.png");
    private static final Identifier CORE_TEXTURE = MobCraft.id("textures/entity/red_chain.png");
    private static final int LIFE = 40;          // ticks
    private static final int GROW = 5;           // ticks to pop in
    private static final int FADE = 12;          // ticks to fade out at the end
    private static final double RISE = 2.6;      // blocks
    private static final float SPIN = 16f;       // degrees a tick, like a wind charge
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int RED = 0xB0161C;

    private record Orb(Vec3 from, long born) {}

    private static final List<Orb> ORBS = new ArrayList<>();
    private static ClientLevel orbLevel;
    private static ModelPart windCharge;
    private static ModelPart core;

    private EssenceOrbs() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(EssenceOrbs::submit);
        ClientTickEvents.END_CLIENT_TICK.register(EssenceOrbs::tick);
    }

    public static void add(EssenceOrbPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (client.level != orbLevel) ORBS.clear();
        orbLevel = client.level;
        ORBS.add(new Orb(new Vec3(payload.x(), payload.y(), payload.z()), client.level.getGameTime()));
    }

    private static ModelPart windCharge() {
        if (windCharge == null) windCharge = WindChargeModel.createBodyLayer().bakeRoot();
        return windCharge;
    }

    /** A small cube in the middle that glows. */
    private static ModelPart core() {
        if (core == null) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1.5f, -1.5f, -1.5f, 3f, 3f, 3f), PartPose.ZERO);
            core = LayerDefinition.create(mesh, 32, 32).bakeRoot();
        }
        return core;
    }

    /** Where an orb is, `age` ticks after it appeared: easing to a stop as it rises. */
    private static Vec3 at(Orb orb, float age) {
        float t = Mth.clamp(age / LIFE, 0f, 1f);
        double up = RISE * (1 - (1 - t) * (1 - t));
        return orb.from().add(0, up, 0);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.level != orbLevel) {
            ORBS.clear();
            return;
        }
        long now = client.level.getGameTime();
        for (Iterator<Orb> it = ORBS.iterator(); it.hasNext(); ) {
            Orb orb = it.next();
            long age = now - orb.born();
            if (age > LIFE) {
                it.remove();
                continue;
            }
            // A trail of red dust behind it.
            Vec3 p = at(orb, age);
            for (int i = 0; i < 2; i++) {
                client.level.addParticle(new DustParticleOptions(RED, 0.9f),
                        p.x + (Math.random() - 0.5) * 0.4,
                        p.y - 0.1 + (Math.random() - 0.5) * 0.3,
                        p.z + (Math.random() - 0.5) * 0.4, 0, -0.02, 0);
            }
        }
    }

    private static void submit(LevelRenderContext context) {
        if (ORBS.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.level != orbLevel) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = client.gameRenderer.mainCamera().position();
        PoseStack pose = context.poseStack();
        for (Orb orb : ORBS) {
            float age = client.level.getGameTime() - orb.born() + partial;
            if (age < 0 || age > LIFE) continue;
            float grow = Mth.clamp(age / GROW, 0f, 1f);
            float fade = Mth.clamp((LIFE - age) / FADE, 0f, 1f);
            float size = 1.3f * grow * (0.3f + 0.7f * fade);
            int alpha = Math.round(255 * fade);
            int color = alpha << 24 | 0xFFFFFF;
            float scroll = (age * 0.05f) % 1f;
            Vec3 p = at(orb, age).subtract(camera);

            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.scale(size, size, size);
            // The wind charge, spinning like a thrown one.
            pose.pushPose();
            pose.rotate(Axis.YP.rotationDegrees(age * SPIN));
            pose.translate(0, -0.2, 0);
            context.submitNodeCollector().submitModelPart(windCharge(), pose,
                    RenderTypes.breezeWind(TEXTURE, scroll, 0f), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
            pose.popPose();
            // A larger ring of wind spinning the other way.
            pose.pushPose();
            pose.rotate(Axis.YP.rotationDegrees(-age * SPIN * 0.7f));
            pose.rotate(Axis.XP.rotationDegrees(18f));
            pose.scale(1.45f, 0.7f, 1.45f);
            pose.translate(0, -0.2, 0);
            context.submitNodeCollector().submitModelPart(windCharge().getChild("bone").getChild("wind"), pose,
                    RenderTypes.breezeWind(TEXTURE, -scroll, 0f), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, color);
            pose.popPose();
            // The glowing core.
            pose.pushPose();
            pose.rotate(Axis.YP.rotationDegrees(age * SPIN * 0.5f));
            pose.rotate(Axis.XP.rotationDegrees(45f));
            pose.scale(fade, fade, fade);
            context.submitNodeCollector().submitModelPart(core(), pose, RenderTypes.eyes(CORE_TEXTURE),
                    FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
            pose.popPose();
        }
    }
}
