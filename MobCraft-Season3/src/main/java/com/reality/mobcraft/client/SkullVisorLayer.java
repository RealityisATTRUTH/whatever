package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.MobCraft;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The Skull Visor on a player's head: the shattered skull from the inventory model. Its two
 * goggle pieces sit fitted to the face, over the eyes. The crown, the jaw and two chips float
 * off it. The wearer's head glows with blue energy, the Enderman's teleport glow in blue,
 * showing through the cracks and gaps. Across every crack it stretches from piece to piece like
 * putty between two blocks pulled apart: wide where it holds on, pinched thin in the middle.
 * The pieces themselves don't glow.
 *
 * The pieces come from SkullVisorShape, generated with the texture sheet by art/visor/worn.py.
 * The item's own model is empty when worn (items/skull_visor.json), so only this is drawn.
 * Whether the player wears it is copied into the render state by AvatarHelmetMixin.
 */
public final class SkullVisorLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final RenderStateDataKey<Boolean> WORN = RenderStateDataKey.create(() -> "mobcraft skull visor");

    private static final Identifier TEXTURE = MobCraft.id("textures/entity/skull_visor_worn.png");
    private static final Identifier GLOW_TEXTURE = MobCraft.id("textures/entity/blue_glow.png");
    private static final ModelLayerLocation SKULL_LAYER = new ModelLayerLocation(MobCraft.id("skull_visor"), "main");
    private static final ModelLayerLocation GLOW_LAYER = new ModelLayerLocation(MobCraft.id("skull_visor"), "glow");
    private static final int FULL_BRIGHT = 0xF000F0;

    private final EntityModel<AvatarRenderState> skull;
    private final EntityModel<AvatarRenderState> glow;

    public SkullVisorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        skull = new EntityModel<AvatarRenderState>(context.bakeLayer(SKULL_LAYER)) {};
        glow = new EntityModel<AvatarRenderState>(context.bakeLayer(GLOW_LAYER)) {};
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        ModelLayerRegistry.registerModelLayer(SKULL_LAYER, () -> mesh(false));
        ModelLayerRegistry.registerModelLayer(GLOW_LAYER, () -> mesh(true));
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof AvatarRenderer) {
                helper.register(new SkullVisorLayer((AvatarRenderer) entityRenderer, context));
            }
        });
    }

    /** The pieces under a part named "head", so they follow the head. The glow is the head's core and the stretched bands. */
    private static LayerDefinition mesh(boolean glow) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition head = mesh.getRoot().addOrReplaceChild(PartNames.HEAD, CubeListBuilder.create(), PartPose.ZERO);
        float deg = (float) (Math.PI / 180);
        for (SkullVisorShape.Piece piece : SkullVisorShape.PIECES) {
            // The pieces themselves never glow: the energy is on the head.
            if (glow) continue;
            CubeListBuilder cubes = CubeListBuilder.create();
            for (SkullVisorShape.Cube c : piece.cubes()) {
                float grow = c.grow();
                cubes.texOffs(c.u(), c.v()).addBox(c.x(), c.y(), c.z(), c.w(), c.h(), c.d(), new CubeDeformation(grow));
            }
            head.addOrReplaceChild(piece.name(), cubes,
                    new PartPose(piece.px(), piece.py(), piece.pz(), piece.rx() * deg, piece.ry() * deg, piece.rz() * deg,
                            SkullVisorShape.TEXEL, SkullVisorShape.TEXEL, SkullVisorShape.TEXEL));
        }
        if (glow) {
            // The head itself glows, a hair bigger than it, showing through the cracks and the gaps.
            head.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-4.4f, -8.6f, -4.4f, 8.8f, 8.8f, 8.8f),
                    PartPose.ZERO);
            // Stretched across every crack like putty between two blocks pulled apart.
            int i = 0;
            for (SkullVisorShape.Stretch s : SkullVisorShape.STRETCH) {
                head.addOrReplaceChild("stretch_" + i++, CubeListBuilder.create().texOffs(0, 0)
                                .addBox(-s.width() / 2, 0f, -s.width() / 2, s.width(), s.length(), s.width()),
                        PartPose.offsetAndRotation(s.x(), s.y(), s.z(), s.rx(), 0f, s.rz()));
            }
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state,
                       float yRot, float xRot) {
        if (!Boolean.TRUE.equals(state.getData(WORN)) || state.isInvisible) return;
        ArmorRenderer.submitTransformCopyingModel(getParentModel(), state, skull, state, false, collector.order(1),
                poseStack, RenderTypes.armorCutoutNoCull(TEXTURE), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        float scroll = state.ageInTicks * 0.01f;
        ArmorRenderer.submitTransformCopyingModel(getParentModel(), state, glow, state, false, collector.order(2),
                poseStack, RenderTypes.energySwirl(GLOW_TEXTURE, scroll % 1f, (scroll * 0.6f) % 1f), FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
    }
}
