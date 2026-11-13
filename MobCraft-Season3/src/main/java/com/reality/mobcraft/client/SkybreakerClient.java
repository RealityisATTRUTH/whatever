package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reality.mobcraft.item.Skybreaker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The client side of Skybreaker.
 *  - Moves your own player through the ability (your client owns your movement): up, held still on the sky,
 *    a stumble as it breaks, then a speeding dive at the target, and a short freeze on impact.
 *  - Switches you to third person for the ability and back afterward, and shakes the camera on impact.
 *  - Gives every player's model the Skybreaker poses, which everyone sees: arms up on the way up, standing
 *    upside down with your soles planted on the underside of the sky, flailing as it shatters, then tipped forward head-first for the dive
 *    with the mace held out ahead and legs trailing.
 */
public final class SkybreakerClient {

    public static final RenderStateDataKey<Integer> PHASE = RenderStateDataKey.create(() -> "mobcraft skybreaker phase");
    public static final RenderStateDataKey<Float> TIME = RenderStateDataKey.create(() -> "mobcraft skybreaker time");
    public static final RenderStateDataKey<Float> DIVE_PITCH = RenderStateDataKey.create(() -> "mobcraft skybreaker dive pitch");

    private record Seen(int phase, float since) {}
    private static final Map<Integer, Seen> SEEN = new HashMap<>();

    private static int lastPhase;
    private static int phaseTicks;
    private static Vec3 hold;
    private static CameraType restoreCamera;
    public static int shake;

    private SkybreakerClient() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (shake > 0) shake--;
            LocalPlayer me = client.player;
            if (me == null) { lastPhase = 0; return; }
            int phase = Skybreaker.phase(me);
            if (phase != lastPhase) {
                phaseTicks = 0;
                if (lastPhase == Skybreaker.NONE && client.options.getCameraType() == CameraType.FIRST_PERSON) {
                    restoreCamera = CameraType.FIRST_PERSON;
                    client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
                if (phase == Skybreaker.NONE && restoreCamera != null) {
                    client.options.setCameraType(restoreCamera);
                    restoreCamera = null;
                }
                if (phase == Skybreaker.STAND) hold = me.position();
                if (phase == Skybreaker.IMPACT) shake = 10;
                lastPhase = phase;
            }
            phaseTicks++;
            move(me, phase);
        });
    }

    private static void move(LocalPlayer me, int phase) {
        switch (phase) {
            case Skybreaker.ASCEND -> me.setDeltaMovement(0, Math.max(0.5, 1.5 - phaseTicks * 0.02), 0);
            case Skybreaker.STAND -> {
                if (hold == null) hold = me.position();
                me.setDeltaMovement(Vec3.ZERO);
                me.setPos(hold.x, hold.y, hold.z);
                me.setXRot(Mth.lerp(0.2f, me.getXRot(), -25f));   // camera drops below the sky to show you standing on it
            }
            case Skybreaker.SHATTER -> me.setDeltaMovement(0, -0.12, 0);
            case Skybreaker.DIVE -> {
                Integer id = me.getAttached(Skybreaker.TARGET);
                Entity target = id == null || id < 0 ? null : me.level().getEntity(id);
                Vec3 dir = target == null ? new Vec3(0, -1, 0)
                        : target.getBoundingBox().getCenter().subtract(me.position().add(0, 0.9, 0)).normalize();
                double speed = Math.min(3.0, 0.8 + phaseTicks * 0.16);
                me.setDeltaMovement(dir.scale(speed));
                if (target != null) {
                    me.setYRot((float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
                    me.setXRot((float) (-Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG));
                }
            }
            case Skybreaker.IMPACT -> me.setDeltaMovement(0, Math.min(0, me.getDeltaMovement().y), 0);
            default -> { hold = null; }
        }
    }

    /** Fills in the pose data for any player's model. */
    public static void extract(Avatar entity, AvatarRenderState state, float partial) {
        int phase = Skybreaker.phase(entity);
        float now = entity.tickCount + partial;
        Seen seen = SEEN.get(entity.getId());
        if (phase == Skybreaker.NONE) { SEEN.remove(entity.getId()); state.setData(PHASE, 0); return; }
        if (seen == null || seen.phase() != phase) { seen = new Seen(phase, now); SEEN.put(entity.getId(), seen); }
        state.setData(PHASE, phase);
        state.setData(TIME, now - seen.since());
        double dx = entity.getX() - entity.xo, dy = entity.getY() - entity.yo, dz = entity.getZ() - entity.zo;
        float pitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);   // 90 = straight down
        state.setData(DIVE_PITCH, Mth.clamp(pitch, 20f, 90f));
    }

    /** Arms, legs and head for each phase. */
    public static void pose(HumanoidModel<?> m, AvatarRenderState state) {
        Integer phase = state.getData(PHASE);
        if (phase == null || phase == 0) return;
        float t = state.getData(TIME) == null ? 0 : state.getData(TIME);
        float in = Mth.clamp(t / 4f, 0f, 1f);
        switch (phase) {
            case Skybreaker.ASCEND -> {
                set(m.rightArm, in, -2.9f, 0f, 0.25f);  set(m.leftArm, in, -2.9f, 0f, -0.25f);
                set(m.rightLeg, in, 0.15f, 0f, 0.03f);  set(m.leftLeg, in, 0.15f, 0f, -0.03f);
                m.head.xRot = Mth.lerp(in, m.head.xRot, -0.6f);
            }
            case Skybreaker.STAND -> {
                // Upside down on the sky: legs straight up with the soles flat on it, arms hanging toward the
                // ground with the mace ready, head tipped to look down at the enemy.
                float sway = Mth.sin(t * 0.25f) * 0.05f;
                set(m.rightArm, in, -2.7f + sway, 0f, 0.35f);  set(m.leftArm, in, -2.6f - sway, 0f, -0.45f);
                set(m.rightLeg, in, 0f, 0f, 0.12f);  set(m.leftLeg, in, 0f, 0f, -0.12f);
                m.head.xRot = Mth.lerp(in, m.head.xRot, -0.7f);
            }
            case Skybreaker.SHATTER -> {
                float f = Mth.sin(t * 1.6f);
                set(m.rightArm, 1, -2.4f + f * 0.4f, 0f, 1.1f);  set(m.leftArm, 1, -2.4f - f * 0.4f, 0f, -1.1f);
                set(m.rightLeg, 1, f * 0.5f, 0f, 0.1f);  set(m.leftLeg, 1, -f * 0.5f, 0f, -0.1f);
                m.head.xRot = -0.7f;
            }
            case Skybreaker.DIVE -> {
                // Body is tipped head-first by rotate(); arms reach over the head toward the target, legs trail.
                set(m.rightArm, in, -3.0f, 0f, 0.12f);  set(m.leftArm, in, -3.0f, 0f, -0.12f);
                set(m.rightLeg, in, 0.05f, 0f, 0.04f);  set(m.leftLeg, in, 0.05f, 0f, -0.04f);
                m.head.xRot = Mth.lerp(in, m.head.xRot, -1.1f);
            }
            case Skybreaker.IMPACT -> {
                set(m.rightArm, 1, -0.6f, 0f, 0.2f);  set(m.leftArm, 1, -0.4f, 0f, -0.5f);
            }
            default -> {}
        }
    }

    private static void set(net.minecraft.client.model.geom.ModelPart part, float amount, float x, float y, float z) {
        part.xRot = Mth.lerp(amount, part.xRot, x);
        part.yRot = Mth.lerp(amount, part.yRot, y);
        part.zRot = Mth.lerp(amount, part.zRot, z);
    }

    private static float ease(float x) { return x * x * (3 - 2 * x); }

    /** Tips the whole body forward for the dive (and a little as the sky gives way), around the body's middle. */
    public static void rotate(AvatarRenderState state, PoseStack pose) {
        Integer phase = state.getData(PHASE);
        if (phase == null) return;
        float t = state.getData(TIME) == null ? 0 : state.getData(TIME);
        float angle;
        if (phase == Skybreaker.STAND) {
            // Flip over and plant the soles on the underside of the sky: upside down, feet up, head toward the ground.
            angle = -Mth.lerp(ease(Mth.clamp(t / 6f, 0f, 1f)), 0f, 180f);
        } else if (phase == Skybreaker.SHATTER) {
            angle = -180f + Mth.sin(t * 1.4f) * 6f;
        } else if (phase == Skybreaker.DIVE) {
            float pitch = state.getData(DIVE_PITCH) == null ? 90f : state.getData(DIVE_PITCH);
            angle = -Mth.lerp(ease(Mth.clamp(t / 5f, 0f, 1f)), 180f, 90f + pitch);
        } else return;
        pose.translate(0, 0.9, 0);
        pose.rotateDegrees(Axis.XP, angle);
        pose.translate(0, -0.9, 0);
    }
}
