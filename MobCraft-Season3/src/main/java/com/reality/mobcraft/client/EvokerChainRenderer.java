package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobAttachments;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The red chain from the Evoker's Broken Totem: drawn from the hand of the player who threw
 * it to whoever they chained, while the server says it's there (MobAttachments.CHAIN_TARGET).
 *
 * It's made of one-block pieces, each two solid 3D links hooked through each other, the
 * second turned a quarter turn from the first, stretched a little so a whole number of
 * pieces fits. The links are see-through red (textures/entity/red_chain.png), with the
 * Enderman's teleport glow turned red drifting over them (textures/entity/red_glow.png).
 * Drawn in the world, so the player who threw it sees it in first person too.
 */
public final class EvokerChainRenderer {

    private static final Identifier TEXTURE = MobCraft.id("textures/entity/red_chain.png");
    private static final Identifier GLOW = MobCraft.id("textures/entity/red_glow.png");
    private static final float GLOW_GROW = 1.18f;  // the glow sits just outside the links
    private static final int FULL_BRIGHT = 0xF000F0; // it glows faintly, like the totem's fog
    private static ModelPart link;

    private EvokerChainRenderer() {}

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(EvokerChainRenderer::submit);
    }

    /**
     * One block of chain along +Y, in pixels: two oval links 5 wide and 10 tall made of bars
     * 1.5 thick, the first across X at the bottom, the second across Z halfway up so it hooks
     * through the first and the next piece's first link.
     */
    /** The two hooked links, shared with the Tome's binding chains. */
    static ModelPart linkModel() {
        return link();
    }

    private static ModelPart link() {
        if (link == null) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("across_x", ring(false), PartPose.ZERO);
            mesh.getRoot().addOrReplaceChild("across_z", ring(true), PartPose.offset(0f, 8f, 0f));
            link = LayerDefinition.create(mesh, 32, 32).bakeRoot();
        }
        return link;
    }

    private static CubeListBuilder ring(boolean alongZ) {
        float w = 5f, h = 10f, t = 1.5f;
        CubeListBuilder b = CubeListBuilder.create().texOffs(0, 0);
        // sides, then top and bottom
        box(b, alongZ, -w / 2, t, t, h - 2 * t);
        box(b, alongZ, w / 2 - t, t, t, h - 2 * t);
        box(b, alongZ, -w / 2, 0f, w, t);
        box(b, alongZ, -w / 2, h - t, w, t);
        return b;
    }

    /** A bar of the ring: `across` and `width` run along X, or along Z for the turned link. */
    private static void box(CubeListBuilder b, boolean alongZ, float across, float y, float width, float height) {
        float t = 1.5f;
        if (alongZ) b.addBox(-t / 2, y, across, t, height, width);
        else b.addBox(across, y, -t / 2, width, height, t);
    }

    private static void submit(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = client.gameRenderer.mainCamera().position();
        RenderType type = RenderTypes.entityTranslucent(TEXTURE);
        float drift = (client.level.getGameTime() + partial) * 0.03f;
        RenderType glow = RenderTypes.energySwirl(GLOW, drift % 1f, (drift * 0.7f) % 1f);
        for (Player player : client.level.players()) {
            Integer id = player.getAttached(MobAttachments.CHAIN_TARGET);
            if (id == null) continue;
            Entity target = client.level.getEntity(id);
            if (target == null) continue;
            Vec3 from = hand(player, partial, client);
            Vec3 to = target.getPosition(partial).add(0, target.getBbHeight() * 0.55, 0);
            draw(context.poseStack(), context, type, glow, from.subtract(camera), to.subtract(from));
        }
    }

    /** About where the right hand is. In first person, low and to the right of the view. */
    private static Vec3 hand(Player player, float partial, Minecraft client) {
        boolean firstPerson = player == client.player && client.options.getCameraType().isFirstPerson();
        float yaw = (firstPerson ? player.getViewYRot(partial) : Mth.lerp(partial, player.yBodyRotO, player.yBodyRot)) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 base = player.getPosition(partial);
        if (firstPerson) {
            return base.add(0, player.getEyeHeight() - 0.45, 0).add(forward.scale(0.5)).add(right.scale(0.35));
        }
        return base.add(0, player.getBbHeight() * 0.45, 0).add(forward.scale(0.25)).add(right.scale(0.35));
    }

    private static void draw(PoseStack pose, LevelRenderContext context, RenderType type, RenderType glow, Vec3 start, Vec3 along) {
        double length = along.length();
        if (length < 0.1) return;
        int pieces = Math.max(1, (int) Math.round(length));
        float stretch = (float) (length / pieces);
        Vec3 dir = along.scale(1 / length);
        Quaternionf turn = new Quaternionf().rotationTo(0f, 1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z);
        ModelPart model = link();
        for (int i = 0; i < pieces; i++) {
            pose.pushPose();
            Vec3 at = start.add(dir.scale(i * stretch));
            pose.translate(at.x, at.y, at.z);
            pose.rotate(turn);
            pose.scale(1f, stretch, 1f);
            context.submitNodeCollector().submitModelPart(model, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.scale(GLOW_GROW, 1f, GLOW_GROW);
            context.submitNodeCollector().submitModelPart(model, pose, glow, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null);
            pose.popPose();
        }
    }
}
