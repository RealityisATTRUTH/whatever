package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.ArmorUpgrades;
import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.PlayerModel;
import com.reality.mobcraft.compat.SubmitNodeCollector;
import com.reality.mobcraft.compat.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws bat ears, rabbit ears and warden tendrils sticking out of a player's helmet when
 * the helmet has that upgrade. The pieces follow the head as it turns.
 *
 * Also draws the chestplate's shard on its front: an orange eye for the
 * Stalker Shard, sitting on top of any trim. The Ender Pearl Shard instead
 * gets a trim-like pattern in the pearl's teal colors.
 *
 * Bat ears use the mod's own texture, cut from the item art. Tendrils use
 * the vanilla warden texture, the same pixels the warden's own tendrils use,
 * at half size to fit a helmet.
 *
 * The helmet's upgrade is copied into the render state by AvatarHelmetMixin.
 */
public final class HelmetUpgradeLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    public static final RenderStateDataKey<String> UPGRADE = RenderStateDataKey.create(() -> "mobcraft helmet upgrade");
    public static final RenderStateDataKey<Boolean> FOIL = RenderStateDataKey.create(() -> "mobcraft helmet foil");
    public static final RenderStateDataKey<String> CHEST_UPGRADE = RenderStateDataKey.create(() -> "mobcraft chestplate upgrade");
    public static final RenderStateDataKey<Boolean> CHEST_FOIL = RenderStateDataKey.create(() -> "mobcraft chestplate foil");

    private static final ResourceLocation BAT_TEXTURE = MobCraft.id("textures/entity/bat_ears_3d.png");
    private static final ResourceLocation PEARL_TRIM_TEXTURE = MobCraft.id("textures/entity/pearl_trim.png");
    private static final ResourceLocation CHEST_TEXTURE = MobCraft.id("textures/entity/chest_emblems.png");
    private static final ResourceLocation WARDEN_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/warden/warden.png");

    private static final ModelLayerLocation EAR_LEFT_LAYER = layer("bat_ear_left");
    private static final ModelLayerLocation EAR_RIGHT_LAYER = layer("bat_ear_right");
    private static final ModelLayerLocation TENDRIL_LEFT_LAYER = layer("warden_tendril_left");
    private static final ModelLayerLocation TENDRIL_RIGHT_LAYER = layer("warden_tendril_right");
    private static final ModelLayerLocation STALKER_EYE_LAYER = layer("stalker_eye");
    private static final ModelLayerLocation PEARL_TRIM_LAYER = layer("pearl_trim");

    // Positions are in head pixels: the helmet is 10 wide, its top at y -9, up is negative y,
    // and +x is the player's left. The ears tilt slightly outward.
    private static final float EAR_TILT = 0.35f;
    private static final float TENDRIL_SCALE = 1.25f;
    // Chest emblems: centered 6 pixels below the shoulders, a fifth bigger than their 5-pixel art.
    private static final float EMBLEM_SCALE = 1.2f;
    private static final PartPose EMBLEM_POSE = new PartPose(0f, 6f, -3.1f, 0f, 0f, 0f, EMBLEM_SCALE, EMBLEM_SCALE, EMBLEM_SCALE);

    private final EntityModel<PlayerRenderState> earLeft;
    private final EntityModel<PlayerRenderState> earRight;
    private final EntityModel<PlayerRenderState> tendrilLeft;
    private final EntityModel<PlayerRenderState> tendrilRight;
    private final EntityModel<PlayerRenderState> stalkerEye;
    private final EntityModel<PlayerRenderState> pearlTrim;
    private final EntityModel<PlayerRenderState> rabbitEars;
    private static final ResourceLocation RABBIT_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/rabbit/brown.png");

    public HelmetUpgradeLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        earLeft = piece(context.bakeLayer(EAR_LEFT_LAYER));
        earRight = piece(context.bakeLayer(EAR_RIGHT_LAYER));
        tendrilLeft = piece(context.bakeLayer(TENDRIL_LEFT_LAYER));
        tendrilRight = piece(context.bakeLayer(TENDRIL_RIGHT_LAYER));
        stalkerEye = piece(context.bakeLayer(STALKER_EYE_LAYER));
        pearlTrim = piece(context.bakeLayer(PEARL_TRIM_LAYER));
        // Rabbit Ears: the rabbit's own ears, twitching like the Rabbit player's (MobLookLayer).
        ModelPart rabbitHead = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.RABBIT).getChild("head");
        rabbitEars = MobLookLayer.rabbitEarModel(MobLookLayer.rabbitEar(58), MobLookLayer.rabbitEar(52));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        // The bat ears are built in 3D, one small block per pixel of the bat ear picture (BatEarModel).
        EntityModelLayerRegistry.registerModelLayer(EAR_LEFT_LAYER, () -> earMesh(true,
                PartPose.offsetAndRotation(3.5f, -8.5f, 0.5f, 0f, 0f, EAR_TILT)));
        EntityModelLayerRegistry.registerModelLayer(EAR_RIGHT_LAYER, () -> earMesh(false,
                PartPose.offsetAndRotation(-3.5f, -8.5f, 0.5f, 0f, 0f, -EAR_TILT)));
        // warden.png is 128x128, declared as 64x64 so each model pixel covers two texture pixels.
        // The warden's own tendrils sit at texture 58,0 (left) and 52,32 (right).
        // They are 8 pixels, scaled up a quarter from the helmet's side.
        EntityModelLayerRegistry.registerModelLayer(TENDRIL_LEFT_LAYER, () -> mesh(64, 64,
                CubeListBuilder.create().texOffs(29, 0).addBox(0f, -6.5f, 0f, 8f, 8f, 0f),
                new PartPose(5f, -7f, 0f, 0f, 0f, 0f, TENDRIL_SCALE, TENDRIL_SCALE, TENDRIL_SCALE)));
        EntityModelLayerRegistry.registerModelLayer(TENDRIL_RIGHT_LAYER, () -> mesh(64, 64,
                CubeListBuilder.create().texOffs(26, 16).addBox(-8f, -6.5f, 0f, 8f, 8f, 0f),
                new PartPose(-5f, -7f, 0f, 0f, 0f, 0f, TENDRIL_SCALE, TENDRIL_SCALE, TENDRIL_SCALE)));

        // Chestplate emblems: a flat sheet just in front of the chestplate's front, so it covers
        // the armor and any trim. The chestplate's front is at z -3 on the body, and up is -y.
        // chest_emblems.png is 64x32, declared as 32x16 like the bat ears.
        EntityModelLayerRegistry.registerModelLayer(STALKER_EYE_LAYER, () -> mesh(PartNames.BODY, 32, 16,
                CubeListBuilder.create().texOffs(0, 0).addBox(-2.5f, -2.5f, 0f, 5f, 5f, 0f), EMBLEM_POSE));
        // Ender Pearl Shard: a trim-like pattern in the pearl's colors, drawn on a copy of the
        // chestplate's shape a hair bigger than the chestplate, the way vanilla trims are layered.
        EntityModelLayerRegistry.registerModelLayer(PEARL_TRIM_LAYER, HelmetUpgradeLayer::pearlTrimMesh);

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerRenderer) {
                helper.register(new HelmetUpgradeLayer((PlayerRenderer) entityRenderer, context));
            }
        });
    }

    /** One bat ear under a part named like the player's head, so it follows the head. */
    private static LayerDefinition earMesh(boolean left, PartPose pose) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition head = mesh.getRoot().addOrReplaceChild(PartNames.HEAD, CubeListBuilder.create(), PartPose.ZERO);
        BatEarModel.addEar(head.addOrReplaceChild("mobcraft_armor_piece", CubeListBuilder.create(), pose), left);
        return LayerDefinition.create(mesh, BatEarModel.TEXTURE_WIDTH, BatEarModel.TEXTURE_HEIGHT);
    }

    /**
     * The chestplate's body and arms, laid out like vanilla armor so pearl_trim.png uses the
     * same 64x32 layout as an armor texture. Part names match the player's so they follow it.
     */
    private static LayerDefinition pearlTrimMesh() {
        CubeDeformation size = new CubeDeformation(1.05f); // the chestplate is 1.0
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(PartNames.BODY,
                CubeListBuilder.create().texOffs(16, 16).addBox(-4f, 0f, -2f, 8f, 12f, 4f, size), PartPose.ZERO);
        root.addOrReplaceChild(PartNames.RIGHT_ARM,
                CubeListBuilder.create().texOffs(40, 16).addBox(-3f, -2f, -2f, 4f, 12f, 4f, size),
                PartPose.offset(-5f, 2f, 0f));
        root.addOrReplaceChild(PartNames.LEFT_ARM,
                CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1f, -2f, -2f, 4f, 12f, 4f, size),
                PartPose.offset(5f, 2f, 0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(MobCraft.id("helmet_" + name), "main");
    }

    private static LayerDefinition mesh(int textureWidth, int textureHeight, CubeListBuilder cubes, PartPose pose) {
        return mesh(PartNames.HEAD, textureWidth, textureHeight, cubes, pose);
    }

    /** One piece under a part named like the player's, "head" or "body", so it copies that part's pose. */
    private static LayerDefinition mesh(String part, int textureWidth, int textureHeight, CubeListBuilder cubes, PartPose pose) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parent = mesh.getRoot().addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
        parent.addOrReplaceChild("mobcraft_armor_piece", cubes, pose);
        return LayerDefinition.create(mesh, textureWidth, textureHeight);
    }

    private static EntityModel<PlayerRenderState> piece(ModelPart root) {
        // Posed by copying the player's head, so it has no animation of its own.
        return new EntityModel<PlayerRenderState>(root) {};
    }

    @Override
    public void render(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
        submit(poseStack, new SubmitNodeCollector(buffers), light, state, yRot, xRot);
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, PlayerRenderState state, float yRot, float xRot) {
        String chest = state.getData(CHEST_UPGRADE);
        if (chest != null) {
            boolean chestFoil = Boolean.TRUE.equals(state.getData(CHEST_FOIL));
            if (chest.equals(ArmorUpgrades.STALKER_SHARD)) {
                draw(stalkerEye, CHEST_TEXTURE, chestFoil, poseStack, collector, light, state);
            } else if (chest.equals(ArmorUpgrades.ENDER_SHARD)) {
                draw(pearlTrim, PEARL_TRIM_TEXTURE, chestFoil, poseStack, collector, light, state);
            }
        }

        String upgrade = state.getData(UPGRADE);
        if (upgrade == null) return;
        boolean foil = Boolean.TRUE.equals(state.getData(FOIL));
        switch (upgrade) {
            case ArmorUpgrades.EAR_LEFT -> draw(earLeft, BAT_TEXTURE, foil, poseStack, collector, light, state);
            case ArmorUpgrades.EAR_RIGHT -> draw(earRight, BAT_TEXTURE, foil, poseStack, collector, light, state);
            case ArmorUpgrades.EAR_BOTH -> {
                draw(earLeft, BAT_TEXTURE, foil, poseStack, collector, light, state);
                draw(earRight, BAT_TEXTURE, foil, poseStack, collector, light, state);
            }
            case ArmorUpgrades.TENDRIL_LEFT -> draw(tendrilLeft, WARDEN_TEXTURE, foil, poseStack, collector, light, state);
            case ArmorUpgrades.TENDRIL_RIGHT -> draw(tendrilRight, WARDEN_TEXTURE, foil, poseStack, collector, light, state);
            case ArmorUpgrades.TENDRIL_BOTH -> {
                draw(tendrilLeft, WARDEN_TEXTURE, foil, poseStack, collector, light, state);
                draw(tendrilRight, WARDEN_TEXTURE, foil, poseStack, collector, light, state);
            }
            case ArmorUpgrades.LUCKY_PAW -> {
                // A Rabbit player already has ears of their own.
                // Animated: the ears place themselves on the helmet and twitch (MobLookLayer.rabbitEarModel).
                if (!"rabbit".equals(state.getData(MobLookLayer.MOB))) {
                    com.reality.mobcraft.compat.TransformCopy.submit(getParentModel(), state, rabbitEars, state, true, collector.order(1),
                            poseStack, RenderType.entityCutout(RABBIT_TEXTURE), light, OverlayTexture.NO_OVERLAY, 0, null);
                }
            }
            default -> { }
        }
    }

    private void draw(EntityModel<PlayerRenderState> piece, ResourceLocation texture, boolean foil, PoseStack poseStack,
                      SubmitNodeCollector collector, int light, PlayerRenderState state) {
        SubmitNodeCollector ordered = collector.order(1);
        com.reality.mobcraft.compat.TransformCopy.submit(getParentModel(), state, piece, state, false, ordered,
                poseStack, RenderType.armorCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, 0, null);
        if (foil) {
            com.reality.mobcraft.compat.TransformCopy.submit(getParentModel(), state, piece, state, false, ordered,
                    poseStack, RenderType.armorEntityGlint(), light, OverlayTexture.NO_OVERLAY, 0, null);
        }
    }
}
