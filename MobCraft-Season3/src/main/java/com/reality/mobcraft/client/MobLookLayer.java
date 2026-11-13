package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.ArmorUpgrades;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobLook;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

/**
 * Mob parts on players, drawn with the real mobs' own textures, so resource
 * packs change them too. Every player sees them, since everyone has the mod.
 *
 *   Warden          the warden's tendrils on the sides of the head. When a vibration
 *                   reaches them they twitch and glow, the way a real warden's do, and
 *                   they keep going while Echolocation or Sniffing is on.
 *   Creeper         swells and flashes white during the fuse (see the scale and
 *                   white-flash mixins), and the charged-creeper energy swirls
 *                   around it while charged.
 *   Elder Guardian  spikes shoot out of the back and shoulders when it's hit up
 *                   close, then go back in.
 *   Ender Dragon    the dragon's wings on the back, flapping in flight and
 *                   folded on the ground. Full size once she feeds them an elytra.
 *   Bat             the bat's wings on the back and its ears on the head. The
 *                   wings flap in flight and fold up otherwise.
 *   Blaze           the blaze's twelve rods spinning around the body in three
 *                   rings, always lit. They spin faster during Fire Shield and
 *                   the combat phase.
 *
 *   Rabbit          the rabbit's ears on the head and its tail. The ears twitch now and
 *                   then, flop back when running, and lie flat while cursed. The fur
 *                   matches the biome: white in snow, gold in the desert, brown elsewhere.
 *   Vex             the vex's wings, fluttering, and beating hard in flight. See-through
 *                   while phasing, and red glowing eyes during a Charge.
 *   Breeze          the breeze's wind swirl around the legs, growing during Whirlwind.
 *   Anyone          an orange glow swirling over the whole body while holding a Creeper Essence charge.
 *   Enderman        a purple glow swirling over the whole body just before a teleport,
 *                   fading out where it lands. The server adds the portal particles.
 *   Axolotl         the axolotl's pink gills around the head. They fan out and flutter
 *                   in water or rain, and droop back slowly when dry.
 *   Shulker         the shulker's shell while Blocking: the base appears at the feet and
 *                   the lid above the head, then the lid comes down and shuts them in. It
 *                   sits square on the block and doesn't turn with the player.
 *   Piglin          the piglin's ears, flapping as it walks and flopping when hit. A
 *                   zombified (cursed) Piglin has zombified ears.
 *   Wither          the wither's two side heads on the shoulders. Each turns to look at
 *                   someone nearby, like the boss's. Below half health the wither's blue
 *                   armor swirls over the body.
 *   Silverfish      the silverfish's body as a tail, wiggling the way a silverfish does.
 *   Skeleton        a leather quiver slung across the back, with an arrow poking out for
 *                   each kind it carries: feathers in a tipped arrow's potion color, gold for
 *                   spectral, white for plain. The arrows drop in and the lid shuts while
 *                   flying with an elytra. Tipped arrows' colors also come out of it as
 *                   particles (sent by SkeletonKit).
 *   Ghast           wisps of cloud drift off the body. In flight it rides a puffy cloud
 *                   under its feet, shedding wisps behind it. In the Nether it's an ash
 *                   cloud instead: dark smoke with glowing embers, raining ash below it.
 *
 * The dragon, bat, blaze, rabbit, vex, breeze, axolotl, shulker, piglin, wither and silverfish parts are the vanilla models' own parts, baked from
 * the game's model layers, so they match the real mobs exactly.
 *
 * What each player's mob is doing comes from MobLook, synced from the server.
 * AvatarHelmetMixin copies it into the render state each frame through extract().
 */
public final class MobLookLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public static final RenderStateDataKey<String> MOB = RenderStateDataKey.create(() -> "mobcraft mob");
    public static final RenderStateDataKey<Float> FUSE = RenderStateDataKey.create(() -> "mobcraft creeper fuse");
    public static final RenderStateDataKey<Boolean> CHARGED = RenderStateDataKey.create(() -> "mobcraft creeper charged");
    public static final RenderStateDataKey<Float> SPIKES = RenderStateDataKey.create(() -> "mobcraft elder spikes");
    public static final RenderStateDataKey<Float> SHAKE = RenderStateDataKey.create(() -> "mobcraft tendril shake");
    public static final RenderStateDataKey<Boolean> HELMET = RenderStateDataKey.create(() -> "mobcraft wears helmet");
    public static final RenderStateDataKey<Float> WINGS_OPEN = RenderStateDataKey.create(() -> "mobcraft wings open");
    public static final RenderStateDataKey<Boolean> FULL_WINGS = RenderStateDataKey.create(() -> "mobcraft dragon full wings");
    public static final RenderStateDataKey<Boolean> SHIELD = RenderStateDataKey.create(() -> "mobcraft blaze shield");
    // The wingbeat, worked out each frame by flap().
    public static final RenderStateDataKey<Float> FLAP_UP = RenderStateDataKey.create(() -> "mobcraft wing up");
    public static final RenderStateDataKey<Float> FLAP_TIP = RenderStateDataKey.create(() -> "mobcraft wing tip");
    public static final RenderStateDataKey<Float> FLAP_SWEEP = RenderStateDataKey.create(() -> "mobcraft wing sweep");
    public static final RenderStateDataKey<Float> FLAP_TWIST = RenderStateDataKey.create(() -> "mobcraft wing twist");
    public static final RenderStateDataKey<Float> SPEED = RenderStateDataKey.create(() -> "mobcraft speed");
    /** Rabbit ears: how far a jump has thrown them, back while rising, up and out while falling, bouncing on landing. */
    /** How open this player's Stronghold Tome is, 0 to 1: raises both arms to hold it (TomeArmsMixin). */
    public static final RenderStateDataKey<Float> READING = RenderStateDataKey.create(() -> "mobcraft reading tome");
    public static final RenderStateDataKey<Float> READING_COVER = RenderStateDataKey.create(() -> "mobcraft tome covers");
    public static final RenderStateDataKey<Integer> READING_BURNS = RenderStateDataKey.create(() -> "mobcraft tome burns");
    public static final RenderStateDataKey<Float> READING_FLIP = RenderStateDataKey.create(() -> "mobcraft tome pages");
    public static final RenderStateDataKey<Float> EAR_JUMP = RenderStateDataKey.create(() -> "mobcraft ear jump");
    public static final RenderStateDataKey<Boolean> CURSED = RenderStateDataKey.create(() -> "mobcraft cursed");
    public static final RenderStateDataKey<Integer> FUR = RenderStateDataKey.create(() -> "mobcraft rabbit fur");
    public static final RenderStateDataKey<Boolean> PHASING = RenderStateDataKey.create(() -> "mobcraft vex phasing");
    public static final RenderStateDataKey<Boolean> CHARGING = RenderStateDataKey.create(() -> "mobcraft vex charging");
    public static final RenderStateDataKey<Float> WHIRL = RenderStateDataKey.create(() -> "mobcraft breeze whirl");
    public static final RenderStateDataKey<Float> GLOW = RenderStateDataKey.create(() -> "mobcraft ender glow");
    public static final RenderStateDataKey<Float> EXPLOSIVE = RenderStateDataKey.create(() -> "mobcraft explosive glow");
    public static final RenderStateDataKey<Float> EXPLOSIVE_HELD = RenderStateDataKey.create(() -> "mobcraft explosive held");
    public static final RenderStateDataKey<Float> SHELL = RenderStateDataKey.create(() -> "mobcraft shulker shell");
    /** A Shulker Essence shell that has been hit once: cracks show over it. */
    public static final RenderStateDataKey<Boolean> CRACKED = RenderStateDataKey.create(() -> "mobcraft shell cracked");
    public static final RenderStateDataKey<Float> WET = RenderStateDataKey.create(() -> "mobcraft axolotl wet");
    /** The wither's side heads: left yaw, left pitch, right yaw, right pitch, in degrees. */
    public static final RenderStateDataKey<float[]> WITHER_LOOK = RenderStateDataKey.create(() -> "mobcraft wither heads");
    public static final RenderStateDataKey<Boolean> WITHER_ARMOR = RenderStateDataKey.create(() -> "mobcraft wither armor");
    public static final RenderStateDataKey<Float> CLOUD = RenderStateDataKey.create(() -> "mobcraft ghast cloud");
    public static final RenderStateDataKey<Boolean> ASH = RenderStateDataKey.create(() -> "mobcraft ghast ash cloud");
    public static final RenderStateDataKey<Float> QUIVER_SHUT = RenderStateDataKey.create(() -> "mobcraft quiver shut");
    /** The feather color of each arrow in the Skeleton's quiver, or empty for no arrows. */
    public static final RenderStateDataKey<int[]> QUIVER_ARROW_COLORS = RenderStateDataKey.create(() -> "mobcraft quiver arrows");
    public static final RenderStateDataKey<Boolean> REALLY_INVISIBLE = RenderStateDataKey.create(() -> "mobcraft really invisible");

    private static final Identifier WARDEN_TEXTURE = Identifier.withDefaultNamespace("textures/entity/warden/warden.png");
    private static final Identifier SWIRL_TEXTURE = Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
    private static final Identifier ELDER_TEXTURE = Identifier.withDefaultNamespace("textures/entity/guardian/guardian_elder.png");
    private static final Identifier DRAGON_TEXTURE = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final Identifier BAT_TEXTURE = Identifier.withDefaultNamespace("textures/entity/bat/bat.png");
    private static final Identifier BLAZE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/blaze/blaze.png");
    private static final Identifier[] RABBIT_TEXTURES = {
            Identifier.withDefaultNamespace("textures/entity/rabbit/rabbit_brown.png"),
            Identifier.withDefaultNamespace("textures/entity/rabbit/rabbit_white.png"),
            Identifier.withDefaultNamespace("textures/entity/rabbit/rabbit_gold.png"),
    };
    private static final Identifier VEX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/illager/vex.png");
    private static final Identifier VEX_CHARGING_TEXTURE = Identifier.withDefaultNamespace("textures/entity/illager/vex_charging.png");
    private static final Identifier VEX_EYES_TEXTURE = MobCraft.id("textures/entity/vex_eyes.png");
    private static final Identifier BREEZE_WIND_TEXTURE = Identifier.withDefaultNamespace("textures/entity/breeze/breeze_wind.png");
    private static final Identifier ENDER_GLOW_TEXTURE = MobCraft.id("textures/entity/ender_glow.png");
    private static final Identifier EXPLOSIVE_GLOW_TEXTURE = MobCraft.id("textures/entity/explosive_glow.png");
    private static final Identifier AXOLOTL_TEXTURE = Identifier.withDefaultNamespace("textures/entity/axolotl/axolotl_lucy.png");
    private static final Identifier SHULKER_TEXTURE = Identifier.withDefaultNamespace("textures/entity/shulker/shulker.png");
    /** Cracks laid over the shulker shell, in the same layout as the shulker texture. */
    private static final Identifier SHELL_CRACKS_TEXTURE = MobCraft.id("textures/entity/shell_cracks.png");
    private static final Identifier PIGLIN_TEXTURE = Identifier.withDefaultNamespace("textures/entity/piglin/piglin.png");
    private static final Identifier ZOMBIFIED_PIGLIN_TEXTURE = Identifier.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png");
    private static final Identifier WITHER_TEXTURE = Identifier.withDefaultNamespace("textures/entity/wither/wither.png");
    private static final Identifier WITHER_ARMOR_TEXTURE = Identifier.withDefaultNamespace("textures/entity/wither/wither_armor.png");
    private static final Identifier SILVERFISH_TEXTURE = Identifier.withDefaultNamespace("textures/entity/silverfish/silverfish.png");
    private static final Identifier GHAST_CLOUD_TEXTURE = MobCraft.id("textures/entity/ghast_cloud.png");
    private static final Identifier GHAST_ASH_CLOUD_TEXTURE = MobCraft.id("textures/entity/ghast_ash_cloud.png");
    private static final Identifier QUIVER_TEXTURE = MobCraft.id("textures/entity/quiver.png");
    private static final ModelLayerLocation QUIVER_LAYER = layer("quiver");
    private static final ModelLayerLocation QUIVER_ARROW_LAYER = layer("quiver_arrow");
    private static final ModelLayerLocation GHAST_CLOUD_LAYER = layer("ghast_cloud");
    private static final ModelLayerLocation VEX_EYES_LAYER = layer("vex_eyes");

    private static final ModelLayerLocation TENDRIL_LEFT_LAYER = layer("tendril_left");
    private static final ModelLayerLocation TENDRIL_RIGHT_LAYER = layer("tendril_right");
    private static final ModelLayerLocation SWIRL_LAYER = layer("creeper_swirl");
    private static final ModelLayerLocation SPIKES_LAYER = layer("elder_spikes");
    private static final String PIECE = "mobcraft_look_piece";

    /**
     * Elder Guardian spikes on the body, in body pixels: x across, y down from the
     * shoulders, z front (-2) to back (2). Each points out along its tilt.
     * {x, y, z, x tilt, z tilt}
     */
    private static final float[][] SPIKE_SPOTS = {
            {-2.5f, 2.5f, 2f, -1.15f, -0.35f}, {2.5f, 2.5f, 2f, -1.15f, 0.35f},
            {0f, 6.5f, 2f, -1.35f, 0f},
            {-2.5f, 10f, 2f, -1.55f, -0.3f}, {2.5f, 10f, 2f, -1.55f, 0.3f},
            {-3.5f, 0f, 0f, 0f, -0.55f}, {3.5f, 0f, 0f, 0f, 0.55f},
    };
    private static final float SPIKE_SIZE = 0.6f;
    private static final float TENDRIL_SCALE = 1.25f;
    /** Twice the shulker's size is one block on the Shulker player, a bit more to clear a helmet. */
    private static final float SHELL_SIZE = 2.1f;
    /** How far the silverfish tail tips down from straight back. */
    private static final float TAIL_DROOP = -0.45f;
    /** How big the Ghast's cloud is next to its mesh. */
    private static final float CLOUD_SIZE = 1.25f;

    private final EntityModel<AvatarRenderState> tendrilLeft;
    private final EntityModel<AvatarRenderState> tendrilRight;
    private final EntityModel<AvatarRenderState> swirl;
    private final EntityModel<AvatarRenderState> spikes;
    private final EntityModel<AvatarRenderState> dragonWings;
    private final EntityModel<AvatarRenderState> batWings;
    private final EntityModel<AvatarRenderState> batEarLeft;
    private final EntityModel<AvatarRenderState> batEarRight;
    private final EntityModel<AvatarRenderState> blazeRods;
    private final EntityModel<AvatarRenderState> rabbitEars;
    private final EntityModel<AvatarRenderState> rabbitTail;
    private final EntityModel<AvatarRenderState> vexWings;
    private final EntityModel<AvatarRenderState> vexEyes;
    private final EntityModel<AvatarRenderState> breezeWind;
    private final EntityModel<AvatarRenderState> axolotlGills;
    private final EntityModel<AvatarRenderState> shulkerShell;
    /** A copy of the shell a hair bigger, for the cracks to sit on without flickering. */
    private final EntityModel<AvatarRenderState> shellCracks;
    private final EntityModel<AvatarRenderState> piglinEars;
    private final EntityModel<AvatarRenderState> witherHeads;
    private final EntityModel<AvatarRenderState> silverfishTail;
    private final EntityModel<AvatarRenderState> ghastCloud;
    private final EntityModel<AvatarRenderState> quiver;
    /** The arrows in the quiver: shafts, and their feathers drawn again in each arrow's color. */
    private final EntityModel<AvatarRenderState>[] quiverShafts;
    private final EntityModel<AvatarRenderState>[] quiverFeathers;

    public MobLookLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        tendrilLeft = tendril(context.bakeLayer(TENDRIL_LEFT_LAYER), 1);
        tendrilRight = tendril(context.bakeLayer(TENDRIL_RIGHT_LAYER), -1);
        swirl = new EntityModel<AvatarRenderState>(context.bakeLayer(SWIRL_LAYER)) {};
        spikes = spikeModel(context.bakeLayer(SPIKES_LAYER));
        ModelPart dragon = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ENDER_DRAGON).getChild("body");
        dragonWings = dragonWingModel(dragon.getChild("left_wing"), dragon.getChild("right_wing"));
        ModelPart bat = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.BAT);
        ModelPart batBody = bat.getChild("body"), batHead = bat.getChild("head");
        batWings = batWingModel(batBody.getChild("left_wing"), batBody.getChild("right_wing"));
        batEarLeft = batEarModel(batHead.getChild("left_ear"), 1);
        batEarRight = batEarModel(batHead.getChild("right_ear"), -1);
        ModelPart blaze = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.BLAZE);
        ModelPart[] rods = new ModelPart[12];
        for (int i = 0; i < rods.length; i++) rods[i] = blaze.getChild("part" + i);
        blazeRods = blazeRodModel(rods);
        ModelPart rabbitBody = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.RABBIT).getChild("body");
        ModelPart rabbitHead = rabbitBody.getChild("head");
        rabbitEars = rabbitEarModel(rabbitHead.getChild("left_ear"), rabbitHead.getChild("right_ear"));
        rabbitTail = rabbitTailModel(rabbitBody.getChild("tail"));
        ModelPart vexBody = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.VEX).getChild("root").getChild("body");
        vexWings = vexWingModel(vexBody.getChild("left_wing"), vexBody.getChild("right_wing"));
        vexEyes = new EntityModel<AvatarRenderState>(context.bakeLayer(VEX_EYES_LAYER)) {};
        breezeWind = breezeWindModel(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.BREEZE_WIND).getChild("wind_body"));
        ModelPart axolotlHead = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.AXOLOTL).getChild("body").getChild("head");
        axolotlGills = axolotlGillModel(axolotlHead.getChild("top_gills"), axolotlHead.getChild("left_gills"), axolotlHead.getChild("right_gills"));
        ModelPart shulker = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SHULKER);
        shulkerShell = shulkerShellModel(shulker.getChild("base"), shulker.getChild("lid"), 1f);
        ModelPart shulkerAgain = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SHULKER);
        shellCracks = shulkerShellModel(shulkerAgain.getChild("base"), shulkerAgain.getChild("lid"), 1.015f);
        ModelPart piglinHead = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PIGLIN).getChild("head");
        piglinEars = piglinEarModel(piglinHead.getChild("left_ear"), piglinHead.getChild("right_ear"));
        ModelPart wither = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.WITHER);
        witherHeads = witherHeadModel(wither.getChild("left_head"), wither.getChild("right_head"));
        ModelPart silverfish = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.SILVERFISH);
        ModelPart[] tail = new ModelPart[5];
        for (int i = 0; i < tail.length; i++) tail[i] = silverfish.getChild("segment" + (i + 2));
        silverfishTail = silverfishTailModel(tail, silverfish.getChild("layer1"));
        ghastCloud = ghastCloudModel(context.bakeLayer(GHAST_CLOUD_LAYER));
        quiver = quiverModel(context.bakeLayer(QUIVER_LAYER));
        quiverShafts = newModels(QUIVER_ARROWS);
        quiverFeathers = newModels(QUIVER_ARROWS);
        for (int i = 0; i < QUIVER_ARROWS; i++) {
            quiverShafts[i] = quiverArrowModel(context.bakeLayer(QUIVER_ARROW_LAYER), i, false);
            quiverFeathers[i] = quiverArrowModel(context.bakeLayer(QUIVER_ARROW_LAYER), i, true);
        }
    }

    @SuppressWarnings("unchecked")
    private static EntityModel<AvatarRenderState>[] newModels(int n) {
        return (EntityModel<AvatarRenderState>[]) new EntityModel[n];
    }

    /** How many arrows show in the quiver, and where each sits in its mouth: {x, z, lean}. */
    private static final int QUIVER_ARROWS = 3;
    private static final float[][] ARROW_SPOTS = {{-1.1f, 0.4f, -0.14f}, {0.2f, -0.6f, 0.04f}, {1.2f, 0.6f, 0.16f}};

    /** Where the quiver hangs, so the quiver and its arrows line up. */
    private static void sling(ModelPart sling) {
        place(sling, 0f, 6.5f, 4.5f, 0f, 0f, 0.45f);
    }

    /**
     * The Skeleton's quiver, slung across the back from the right hip to the left shoulder.
     * While flying with an elytra a lid swings shut over the mouth (the arrows drop in, see
     * quiverArrowModel).
     */
    private static EntityModel<AvatarRenderState> quiverModel(ModelPart root) {
        ModelPart sling = root.getChild(PartNames.BODY).getChild("quiver");
        ModelPart lid = sling.getChild("lid");
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                sling(sling);
                // The lid only shows when it's needed: it swings up from the back edge and down
                // over the mouth as the quiver shuts, and back up and away as it opens.
                float shut = data(state, QUIVER_SHUT);
                lid.visible = shut > 0.02f;
                lid.xRot = lerp(shut, -1.6f, 0f);
            }
        };
    }

    /**
     * One arrow standing in the quiver, point down, fletching up: a wooden shaft with a nock
     * and two crossed feathers. Drawn twice, the shaft (feathers=false) and then the feathers
     * alone (feathers=true) in the arrow's color, white feathers taking the color.
     */
    private static EntityModel<AvatarRenderState> quiverArrowModel(ModelPart root, int slot, boolean feathers) {
        ModelPart sling = root.getChild(PartNames.BODY).getChild("quiver");
        ModelPart arrow = sling.getChild("arrow");
        arrow.getChild("shaft").visible = !feathers;
        arrow.getChild("feather_a").visible = feathers;
        arrow.getChild("feather_b").visible = feathers;
        float[] spot = ARROW_SPOTS[slot];
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                sling(sling);
                // Shut, the arrows drop down inside, under the lid.
                place(arrow, spot[0], -8f + data(state, QUIVER_SHUT) * 7.5f, spot[1], 0f, slot * 0.7f, spot[2]);
            }
        };
    }

    private static LayerDefinition quiverArrowMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild(PartNames.BODY, CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition sling = body.addOrReplaceChild("quiver", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition arrow = sling.addOrReplaceChild("arrow", CubeListBuilder.create(), PartPose.ZERO);
        // Standing out of the rim, 7 pixels of shaft with the nock on top.
        arrow.addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(40, 0).addBox(-0.5f, -7f, -0.5f, 1f, 7f, 1f), PartPose.ZERO);
        // Two feathers crossed round the shaft near the top.
        arrow.addOrReplaceChild("feather_a", CubeListBuilder.create().texOffs(40, 8).addBox(-1.5f, -6.5f, 0f, 3f, 4f, 0f), PartPose.ZERO);
        arrow.addOrReplaceChild("feather_b", CubeListBuilder.create().texOffs(48, 8).addBox(0f, -6.5f, -1.5f, 0f, 4f, 3f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition quiverMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild(PartNames.BODY, CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition sling = body.addOrReplaceChild("quiver", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2f, -7f, -2f, 4f, 12f, 4f)          // the leather tube
                .texOffs(16, 0).addBox(-2.5f, -8f, -2.5f, 5f, 1f, 5f),     // the rim
                PartPose.ZERO);
        // Hinged on the rim's outer edge.
        sling.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(16, 8).addBox(-2.5f, -1f, -5f, 5f, 1f, 5f), PartPose.offset(0f, -8f, 2.5f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** The Ghast's cloud: it puffs up under the feet when flying, bobs and drifts a little, and shrinks away on landing. */
    private static EntityModel<AvatarRenderState> ghastCloudModel(ModelPart root) {
        ModelPart cloud = root.getChild("mobcraft_cloud");
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                float c = data(state, CLOUD);
                float t = state.ageInTicks;
                place(cloud, 0f, 24f + Mth.sin(t * 0.08f) * 0.6f, 0f, Mth.sin(t * 0.05f) * 0.03f, Mth.sin(t * 0.03f) * 0.15f, Mth.cos(t * 0.06f) * 0.03f);
                // Grows out from the middle, a little overshoot, then settles.
                float grow = (c < 1f ? c * (1.15f - 0.15f * c) : 1f) * CLOUD_SIZE;
                cloud.xScale = cloud.zScale = grow;
                cloud.yScale = grow * (0.85f + 0.15f * c);
            }
        };
    }

    /** A blocky cloud like the sky's: a wide body with puffs round the sides, its top just above the feet. */
    private static LayerDefinition ghastCloudMesh() {
        MeshDefinition mesh = new MeshDefinition();
        CubeListBuilder cubes = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8f, -1f, -7f, 16f, 4f, 14f)       // body
                .texOffs(0, 0).addBox(-6f, 3f, -5f, 12f, 2f, 10f)        // underside
                .texOffs(0, 0).addBox(-12f, 0f, -4f, 4f, 3f, 8f)         // puffs round the edge
                .texOffs(0, 0).addBox(8f, 0f, -5f, 4f, 3f, 9f)
                .texOffs(0, 0).addBox(-5f, 0f, -10f, 9f, 3f, 3f)
                .texOffs(0, 0).addBox(-4f, 0f, 7f, 10f, 3f, 3f)
                .texOffs(0, 0).addBox(-10f, -3f, 1f, 5f, 3f, 5f)         // a few bumps on top, clear of the feet
                .texOffs(0, 0).addBox(5f, -3f, -7f, 5f, 3f, 4f)
                .texOffs(0, 0).addBox(-3f, -2f, 4f, 6f, 2f, 4f);
        mesh.getRoot().addOrReplaceChild("mobcraft_cloud", cubes, PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** The piglin's ears on the sides of the head, flapping like PiglinModel's as it walks, and flopping when hit. */
    private static EntityModel<AvatarRenderState> piglinEarModel(ModelPart left, ModelPart right) {
        ModelPart root = under(PartNames.HEAD, Map.of("left", left, "right", right));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                boolean helmet = Boolean.TRUE.equals(state.getData(HELMET));
                // The pivot sits just inside the side of the head, so the top of each ear is tucked
                // against it and only the lower part flares out, like on a real piglin.
                float out = helmet ? 4.7f : 3.7f;
                float t = state.ageInTicks * 0.1f + state.walkAnimationPos * 0.5f;
                float amount = 0.08f + state.walkAnimationSpeed * 0.4f;
                // A hit makes them flop out hard for a moment.
                float hit = state.hasRedOverlay ? 0.45f + Mth.sin(state.ageInTicks * 2.2f) * 0.25f : 0f;
                place(left, out, helmet ? -6.5f : -6f, 0f, 0f, 0f, -Mth.PI / 6f - Mth.cos(t * 1.2f) * amount - hit);
                place(right, -out, helmet ? -6.5f : -6f, 0f, 0f, 0f, Mth.PI / 6f + Mth.cos(t) * amount + hit);
            }
        };
    }

    /**
     * The wither's two side heads, one on each shoulder. Each turns to look at someone nearby,
     * worked out in witherLook(), and looks around when there's no one.
     */
    private static EntityModel<AvatarRenderState> witherHeadModel(ModelPart left, ModelPart right) {
        ModelPart root = under(PartNames.BODY, Map.of("left", left, "right", right));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                float[] look = state.getData(WITHER_LOOK);
                if (look == null) look = new float[4];
                // Each head's box sits a pixel off its pivot, so the two pivots differ by a pixel to
                // line the heads up evenly. They rest on the shoulders, beside the player's head.
                place(left, 8f, -2f, 0.5f, look[1] * Mth.DEG_TO_RAD, look[0] * Mth.DEG_TO_RAD, 0f);
                place(right, -6f, -2f, 0.5f, look[3] * Mth.DEG_TO_RAD, look[2] * Mth.DEG_TO_RAD, 0f);
            }
        };
    }

    /**
     * The silverfish's back half as a tail from the lower back: its five tail segments, biggest
     * first, and the fuzzy layer over the middle. It wiggles the way SilverfishModel does, more
     * when moving.
     */
    private static EntityModel<AvatarRenderState> silverfishTailModel(ModelPart[] segments, ModelPart fuzz) {
        Map<String, ModelPart> parts = new HashMap<>();
        for (int i = 0; i < segments.length; i++) parts.put("segment" + i, segments[i]);
        parts.put("fuzz", fuzz);
        ModelPart tail = new ModelPart(java.util.List.of(), parts);
        ModelPart root = under(PartNames.BODY, Map.of("tail", tail));
        // Where each segment sits along the tail, and how tall it is, in pixels.
        float[] along = {1.5f, 4.5f, 7.5f, 10f, 12f};
        float[] height = {4f, 3f, 2f, 1f, 1f};
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                place(tail, 0f, 10.5f, 1.6f, TAIL_DROOP, 0f, 0f);
                float wiggle = 0.35f + 0.65f * Mth.clamp(data(state, SPEED) / 0.1f, 0f, 1f);
                for (int i = 0; i < segments.length; i++) {
                    int n = i + 2;   // the segment's number in the silverfish
                    float a = state.ageInTicks * 0.9f + n * 0.15f * Mth.PI;
                    float side = Mth.sin(a) * Mth.PI * 0.2f * i * wiggle;
                    float turn = Mth.cos(a) * Mth.PI * 0.05f * (1 + i) * wiggle;
                    place(segments[i], side, -height[i] / 2f, along[i], 0f, turn, 0f);
                    if (i == 2) place(fuzz, side, -2.5f, along[i], 0f, turn, 0f);
                }
            }
        };
    }

    /**
     * The axolotl's three gills: one fan on top of the head and one on each side, toward the back
     * like a real axolotl's. Wet, they fan out and flutter. Dry, they droop back and barely move.
     */
    private static EntityModel<AvatarRenderState> axolotlGillModel(ModelPart top, ModelPart left, ModelPart right) {
        ModelPart root = under(PartNames.HEAD, Map.of("top", top, "left", left, "right", right));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                boolean helmet = Boolean.TRUE.equals(state.getData(HELMET));
                float out = helmet ? 1f : 0f;
                float wet = data(state, WET);
                float t = state.ageInTicks;
                // Wet: quick flutter, fanned out. Dry: slow breathing, laid back.
                float flutter = lerp(wet, Mth.sin(t * 0.09f) * 0.05f, Mth.sin(t * 0.33f) * 0.2f);
                float lay = lerp(wet, 0.95f, 0.45f);
                // The top fan stands on the head and leans back. The side fans (each box reaches
                // outward from its edge) sweep back and droop down when dry.
                place(top, 0f, -8f - out, 2f, -lay - flutter, 0f, 0f);
                place(left, -4f - out, -3.5f - out, 2f, 0f, lay + flutter, lerp(wet, -0.35f, 0f));
                place(right, 4f + out, -3.5f - out, 2f, 0f, -lay - flutter, lerp(wet, 0.35f, 0f));
                for (ModelPart p : new ModelPart[]{top, left, right}) scale(p, 1.2f);
            }
        };
    }

    /** The shulker shell while it's showing, with cracks over it once a Shulker Essence shell is hit. */
    private void drawShell(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state) {
        if (data(state, SHELL) <= 0.01f) return;
        draw(shulkerShell, true, RenderTypes.entityCutout(SHULKER_TEXTURE), poseStack, collector, light, state);
        if (Boolean.TRUE.equals(state.getData(CRACKED))) {
            draw(shellCracks, true, RenderTypes.entityCutout(SHELL_CRACKS_TEXTURE), poseStack, collector, light, state);
        }
    }

    /**
     * The shulker's shell, at a real shulker's size: one block, which is the whole Shulker player.
     * It stays put at the feet (the root isn't named like any player part). Opening the ability:
     * the base grows up from the feet while the lid appears above the head, then the lid comes
     * down, turning a little like a real shulker's, and shuts the player in. Ending runs it backward.
     */
    private static EntityModel<AvatarRenderState> shulkerShellModel(ModelPart base, ModelPart lid, float grow) {
        ModelPart shell = new ModelPart(java.util.List.of(), Map.of("base", base, "lid", lid));
        ModelPart root = new ModelPart(java.util.List.of(), Map.of("mobcraft_shell", shell));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                float c = data(state, SHELL);
                float appear = Mth.clamp(c / 0.35f, 0f, 1f);
                float close = Mth.clamp((c - 0.35f) / 0.65f, 0f, 1f);
                close = close * close;                     // speeds up and snaps shut
                // Lined up with the world, not the player, so it sits square on its block like a real
                // shulker's and doesn't turn when the player looks around inside it.
                place(shell, 0f, 24f, 0f, 0f, -state.bodyRot * Mth.DEG_TO_RAD, 0f);
                scale(shell, SHELL_SIZE * grow);
                place(base, 0f, 0f, 0f, 0f, 0f, 0f);
                base.xScale = base.zScale = 1f;
                base.yScale = appear;
                // Above the head when open, down on the base when shut.
                place(lid, 0f, lerp(close, -13f, 0f), 0f, 0f, (1f - close) * 0.6f, 0f);
                scale(lid, appear);
            }
        };
    }

    /** The rabbit's two ears on top of the head. */
    static EntityModel<AvatarRenderState> rabbitEarModel(ModelPart left, ModelPart right) {
        ModelPart leftMount = mount(left), rightMount = mount(right);
        ModelPart root = under(PartNames.HEAD, Map.of("left", leftMount, "right", rightMount));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                boolean helmet = Boolean.TRUE.equals(state.getData(HELMET));
                float y = helmet ? -9f : -8f;
                place(leftMount, 2f, y, 0f, 0f, 0f, 0f);
                place(rightMount, -2f, y, 0f, 0f, 0f, 0f);
                scale(leftMount, 1.6f);
                scale(rightMount, 1.6f);
                float run = Mth.clamp(data(state, SPEED) / 0.25f, 0f, 1f);
                boolean cursed = Boolean.TRUE.equals(state.getData(CURSED));
                // Now and then one ear twitches, the other a moment later.
                float t = state.ageInTicks;
                float twitchL = Math.max(0f, Mth.sin(t * 0.07f) - 0.93f) * 6f * Mth.sin(t * 1.7f);
                float twitchR = Math.max(0f, Mth.sin(t * 0.07f - 0.6f) - 0.93f) * 6f * Mth.sin(t * 1.9f);
                // Walking: the ears bob with each step and swing a little side to side, out of step with each other.
                float stride = state.walkAnimationPos * 0.6662f;
                float walk = Mth.clamp(state.walkAnimationSpeed, 0f, 1f);
                float bob = Mth.cos(stride * 2f) * 0.14f * walk;
                float swingL = Mth.sin(stride) * 0.1f * walk, swingR = Mth.sin(stride + 1.2f) * 0.1f * walk;
                // Jumping: back as you rise, up and out as you fall, a bounce on landing (MobLookLayer.earSpring).
                float jump = data(state, EAR_JUMP);
                float flare = Math.max(0f, -jump) * 0.45f;
                // Up and a little back, flopping back when running, flat back when cursed.
                float back = cursed ? 1.35f : 0.15f + run * 0.6f + bob + jump;
                float spread = (cursed ? 0.5f : 0.12f) + flare;
                place(left, 0f, 0f, 0f, back, 0f, spread + twitchL + swingL);
                place(right, 0f, 0f, 0f, back + bob * 0.5f, 0f, -spread - twitchR - swingR);
            }
        };
    }

    /** The rabbit's puffy tail on the lower back, wagging a little when moving. */
    private static EntityModel<AvatarRenderState> rabbitTailModel(ModelPart tail) {
        ModelPart tailMount = mount(tail);
        ModelPart root = under(PartNames.BODY, Map.of("tail", tailMount));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                float run = Mth.clamp(data(state, SPEED) / 0.25f, 0f, 1f);
                place(tailMount, 0f, 9.5f, 2.4f, 0f, Mth.sin(state.ageInTicks * 0.8f) * 0.3f * run, 0f);
                scale(tailMount, 1.05f);
                place(tail, 0f, 0f, 0f, 0f, 0f, 0f);
            }
        };
    }

    /** The vex's wings: always fluttering a little, like a vex, and beating hard in flight. */
    private static EntityModel<AvatarRenderState> vexWingModel(ModelPart left, ModelPart right) {
        ModelPart leftMount = mount(left), rightMount = mount(right);
        ModelPart root = under(PartNames.BODY, Map.of("left", leftMount, "right", rightMount));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                place(leftMount, 0.8f, 1.5f, 2.4f, 0f, 0f, 0f);
                place(rightMount, -0.8f, 1.5f, 2.4f, 0f, 0f, 0f);
                scale(leftMount, 1.5f);
                scale(rightMount, 1.5f);
                float o = open(state);
                // The wings stick out behind. Turning them spreads them to the sides.
                float flutter = 0.75f + Mth.sin(state.ageInTicks * 0.9f) * 0.12f;
                float spread = lerp(o, flutter, 0.6f + data(state, FLAP_UP) * 0.55f);
                place(right, 0f, 0f, 0f, 0.3f, -spread, 0.35f);
                place(left, 0f, 0f, 0f, 0.3f, spread, -0.35f);
            }
        };
    }

    /** The breeze's wind, a spinning funnel from the feet up, bigger during Whirlwind. */
    private static EntityModel<AvatarRenderState> breezeWindModel(ModelPart windBody) {
        ModelPart bottom = windBody.getChild("wind_bottom");
        ModelPart mid = bottom.getChild("wind_mid");
        ModelPart top = mid.getChild("wind_top");
        // Not named like any player part, so it stays put at the feet instead of following the legs.
        ModelPart windMount = mount(windBody);
        ModelPart root = new ModelPart(java.util.List.of(), Map.of("mobcraft_wind", windMount));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                float w = data(state, WHIRL);
                place(windMount, 0f, 24f, 0f, 0f, 0f, 0f);
                scale(windMount, lerp(w, 0.72f, 1.05f));
                bottom.y = 0f;
                float spin = state.ageInTicks * (0.25f + w * 0.5f);
                bottom.yRot = spin * 0.6f;
                mid.yRot = spin * 0.4f;
                top.yRot = spin * 0.5f;
            }
        };
    }

    // --- Vanilla parts on the player --------------------------------------------------------------

    /** A part to hang vanilla parts from, named like one of the player's parts so it follows that part. */
    private static ModelPart under(String playerPart, Map<String, ModelPart> children) {
        return new ModelPart(java.util.List.of(), Map.of(playerPart, new ModelPart(java.util.List.of(), children)));
    }

    private static ModelPart mount(ModelPart child) {
        return new ModelPart(java.util.List.of(), Map.of("part", child));
    }

    private static void place(ModelPart p, float x, float y, float z, float xRot, float yRot, float zRot) {
        p.x = x; p.y = y; p.z = z;
        p.xRot = xRot; p.yRot = yRot; p.zRot = zRot;
    }

    private static void scale(ModelPart p, float s) {
        p.xScale = p.yScale = p.zScale = s;
    }

    private static float open(AvatarRenderState state) {
        Float f = state.getData(WINGS_OPEN);
        return f == null ? 0f : f;
    }

    /**
     * The dragon's wings, small at first. Feeding them an elytra merges it into them: they
     * grow to an elytra's size, and the elytra itself never shows (WingsLayerMixin).
     *
     * Resting, they hang down the back like a folded elytra: the bone runs down from the
     * shoulder and the skin spreads out to the side, flat against the back, tips splayed a
     * little. In flight they're tipped up and spread, and flap the way the dragon's do.
     */
    private static EntityModel<AvatarRenderState> dragonWingModel(ModelPart left, ModelPart right) {
        ModelPart leftTip = left.getChild("left_wing_tip"), rightTip = right.getChild("right_wing_tip");
        ModelPart leftMount = mount(left), rightMount = mount(right);
        // The lean tips the wings away from the back as they go down, so they clear the body and the arms.
        ModelPart lean = new ModelPart(java.util.List.of(), Map.of("left", leftMount, "right", rightMount));
        ModelPart root = under(PartNames.BODY, Map.of("lean", lean));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                // With the elytra in them, they're an elytra's size: about 20 pixels long and 10 wide.
                float o = open(state);
                float size = Boolean.TRUE.equals(state.getData(FULL_WINGS)) ? 0.18f : 0.1f;
                float idle = Mth.sin(state.ageInTicks * 0.08f) * 0.04f;
                // The wing bone is thick, so the wings sit a little behind the back, clear of the jacket layer.
                place(lean, 0f, 0f, 3.1f + size * 2f, lerp(o, 0.35f, 0f), 0f, 0f);
                // Resting: turned so the bone points down and the skin out to the side (zRot a quarter
                // turn, less a splay like an elytra's). Flying: tipped up so the skin trails behind.
                float tilt = -Mth.HALF_PI + 0.35f;
                float mx = lerp(o, Mth.HALF_PI, tilt);
                float mz = lerp(o, Mth.HALF_PI - 0.26f + idle, 0f);
                place(leftMount, lerp(o, 0.8f, 1f), lerp(o, 0.5f, 2.5f), 0f, mx, 0f, mz);
                place(rightMount, lerp(o, -0.8f, -1f), lerp(o, 0.5f, 2.5f), 0f, mx, 0f, -mz);
                scale(leftMount, size);
                scale(rightMount, size);
                // In flight, the wingbeat from flap(): up and down, a forward sweep on the downstroke,
                // a twist, and the outer half trailing behind. Raised a little at rest, like a glider.
                float up = data(state, FLAP_UP), tip = data(state, FLAP_TIP), sweep = data(state, FLAP_SWEEP), twist = data(state, FLAP_TWIST);
                float yRot = lerp(o, 0f, 0.25f + up * 0.75f);
                float zRot = lerp(o, 0f, sweep);
                float xRot = lerp(o, 0f, twist);
                place(left, 0f, 0f, 0f, xRot, yRot, zRot);
                place(right, 0f, 0f, 0f, xRot, -yRot, -zRot);
                leftTip.yRot = lerp(o, 0f, tip);
                rightTip.yRot = -lerp(o, 0f, tip);
            }
        };
    }

    /** The bat's wings on the back: they beat in flight and fold back along the body otherwise. */
    private static EntityModel<AvatarRenderState> batWingModel(ModelPart left, ModelPart right) {
        ModelPart leftTip = left.getChild("left_wing_tip"), rightTip = right.getChild("right_wing_tip");
        ModelPart leftMount = mount(left), rightMount = mount(right);
        ModelPart root = under(PartNames.BODY, Map.of("left", leftMount, "right", rightMount));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                // Just behind the back, clear of the jacket layer.
                place(leftMount, 1.5f, 3f, 2.7f, 0f, 0f, 0f);
                place(rightMount, -1.5f, 3f, 2.7f, 0f, 0f, 0f);
                scale(leftMount, 1.3f);
                scale(rightMount, 1.3f);
                float o = open(state);
                float idle = Mth.sin(state.ageInTicks * 0.1f) * 0.06f;
                float up = data(state, FLAP_UP), tip = data(state, FLAP_TIP);
                // Right wing: positive turns it back. Folded wings tuck back and down, and twitch a little.
                // In flight the wings sweep forward hard on the downstroke and back on the upstroke.
                float yRot = lerp(o, 1.25f + idle, 0.35f - up * 0.8f);
                float tipRot = lerp(o, 1.1f, -tip);
                place(right, 0f, 0f, 0f, 0f, yRot, lerp(o, 0.25f, 0f));
                place(left, 0f, 0f, 0f, 0f, -yRot, -lerp(o, 0.25f, 0f));
                rightTip.yRot = tipRot;
                leftTip.yRot = -tipRot;
            }
        };
    }

    private static float data(AvatarRenderState state, RenderStateDataKey<Float> key) {
        Float v = state.getData(key);
        return v == null ? 0f : v;
    }

    /** One of the bat's ears, on top of the head. side is 1 for the left one. */
    private static EntityModel<AvatarRenderState> batEarModel(ModelPart ear, int side) {
        ModelPart earMount = mount(ear);
        ModelPart root = under(PartNames.HEAD, Map.of("ear", earMount));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                boolean helmet = Boolean.TRUE.equals(state.getData(HELMET));
                place(earMount, side * 2.2f, helmet ? -9f : -8f, 0f, 0f, 0f, side * 0.2f);
                scale(earMount, 1.6f);
                place(ear, 0f, 0f, 0f, 0f, 0f, 0f);
            }
        };
    }

    /** The blaze's rods, spinning around the body in three rings like BlazeModel, but closer in to fit a player. */
    private static EntityModel<AvatarRenderState> blazeRodModel(ModelPart[] rods) {
        Map<String, ModelPart> children = new HashMap<>();
        for (int i = 0; i < rods.length; i++) children.put("rod" + i, rods[i]);
        ModelPart body = new ModelPart(java.util.List.of(), children);
        ModelPart root = new ModelPart(java.util.List.of(), Map.of(PartNames.BODY, body));
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                float t = state.ageInTicks * (Boolean.TRUE.equals(state.getData(SHIELD)) ? 2.5f : 1f);
                float f = t * Mth.PI * -0.1f;
                for (int i = 0; i < 4; i++) {
                    ring(rods[i], f, 9f, -1f + Mth.cos((i * 2 + state.ageInTicks) * 0.25f));
                    f += Mth.HALF_PI;
                }
                f = Mth.PI / 4f + t * Mth.PI * 0.03f;
                for (int i = 4; i < 8; i++) {
                    ring(rods[i], f, 7.5f, 3.5f + Mth.cos((i * 2 + state.ageInTicks) * 0.25f));
                    f += Mth.HALF_PI;
                }
                f = 0.47123894f + t * Mth.PI * -0.05f;
                for (int i = 8; i < 12; i++) {
                    ring(rods[i], f, 6f, 8f + Mth.cos((i * 1.5f + state.ageInTicks) * 0.5f));
                    f += Mth.HALF_PI;
                }
            }

            private void ring(ModelPart rod, float angle, float radius, float y) {
                // The rod's box starts at its corner, so this centres it on the ring.
                place(rod, Mth.cos(angle) * radius - 0.75f, y, Mth.sin(angle) * radius - 0.75f, 0f, 0f, 0f);
                scale(rod, 0.75f);
            }
        };
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register() {
        // warden.png is 128x128, declared as 64x64 like the helmet tendrils. Left at 58,0, right at 52,32.
        ModelLayerRegistry.registerModelLayer(TENDRIL_LEFT_LAYER, () -> headPiece(64, 64,
                CubeListBuilder.create().texOffs(29, 0).addBox(0f, -6.5f, 0f, 8f, 8f, 0f)));
        ModelLayerRegistry.registerModelLayer(TENDRIL_RIGHT_LAYER, () -> headPiece(64, 64,
                CubeListBuilder.create().texOffs(26, 16).addBox(-8f, -6.5f, 0f, 8f, 8f, 0f)));
        ModelLayerRegistry.registerModelLayer(SWIRL_LAYER, MobLookLayer::swirlMesh);
        ModelLayerRegistry.registerModelLayer(SPIKES_LAYER, MobLookLayer::spikesMesh);
        ModelLayerRegistry.registerModelLayer(GHAST_CLOUD_LAYER, MobLookLayer::ghastCloudMesh);
        ModelLayerRegistry.registerModelLayer(QUIVER_LAYER, MobLookLayer::quiverMesh);
        ModelLayerRegistry.registerModelLayer(QUIVER_ARROW_LAYER, MobLookLayer::quiverArrowMesh);
        // Two glowing red eyes, on a sheet just in front of the face and its hat layer, where most skins
        // have their eyes: row 4, columns 1-2 and 5-6. vex_eyes.png is clear apart from those.
        ModelLayerRegistry.registerModelLayer(VEX_EYES_LAYER, () -> headPiece(16, 8,
                CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -8f, -4.6f, 8f, 8f, 0f)));
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof AvatarRenderer) helper.register(new MobLookLayer((AvatarRenderer) entityRenderer, context));
        });
    }

    // --- Meshes -----------------------------------------------------------------------------------

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(MobCraft.id("look_" + name), "main");
    }

    private static LayerDefinition headPiece(int w, int h, CubeListBuilder cubes) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition head = mesh.getRoot().addOrReplaceChild(PartNames.HEAD, CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild(PIECE, cubes, PartPose.ZERO);
        return LayerDefinition.create(mesh, w, h);
    }

    /** The whole player, a little bigger, for the energy swirl to cover. Part names match the player's so it follows. */
    private static LayerDefinition swirlMesh() {
        CubeDeformation size = new CubeDeformation(0.75f);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(PartNames.HEAD, CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -8f, -4f, 8f, 8f, 8f, size), PartPose.ZERO);
        root.addOrReplaceChild(PartNames.BODY, CubeListBuilder.create().texOffs(16, 16).addBox(-4f, 0f, -2f, 8f, 12f, 4f, size), PartPose.ZERO);
        root.addOrReplaceChild(PartNames.RIGHT_ARM, CubeListBuilder.create().texOffs(40, 16).addBox(-3f, -2f, -2f, 4f, 12f, 4f, size),
                PartPose.offset(-5f, 2f, 0f));
        root.addOrReplaceChild(PartNames.LEFT_ARM, CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1f, -2f, -2f, 4f, 12f, 4f, size),
                PartPose.offset(5f, 2f, 0f));
        root.addOrReplaceChild(PartNames.RIGHT_LEG, CubeListBuilder.create().texOffs(0, 16).addBox(-2f, 0f, -2f, 4f, 12f, 4f, size),
                PartPose.offset(-1.9f, 12f, 0f));
        root.addOrReplaceChild(PartNames.LEFT_LEG, CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2f, 0f, -2f, 4f, 12f, 4f, size),
                PartPose.offset(1.9f, 12f, 0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** The guardian's own spike, from texture 0,0, growing out from its base. */
    private static LayerDefinition spikesMesh() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild(PartNames.BODY, CubeListBuilder.create(), PartPose.ZERO);
        for (int i = 0; i < SPIKE_SPOTS.length; i++) {
            float[] s = SPIKE_SPOTS[i];
            body.addOrReplaceChild("spike" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-1f, -9f, -1f, 2f, 9f, 2f),
                    PartPose.offsetAndRotation(s[0], s[1], s[2], s[3], 0f, s[4]));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A tendril that wiggles like a real warden's: side is 1 for the left one, -1 for the right. */
    private static EntityModel<AvatarRenderState> tendril(ModelPart root, int side) {
        ModelPart piece = root.getChild(PartNames.HEAD).getChild(PIECE);
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            // Runs after the head's pose is copied from the player, so it must not reset the pose.
            public void setupAnim(AvatarRenderState state) {
                boolean helmet = Boolean.TRUE.equals(state.getData(HELMET));
                // Positions are in head pixels: up is -y and +x is the player's left.
                piece.x = side * (helmet ? 5f : 4f);
                piece.y = helmet ? -7f : -6f;
                piece.xScale = piece.yScale = piece.zScale = helmet ? TENDRIL_SCALE : 1f;
                Float shake = state.getData(SHAKE);
                float f = (shake == null ? 0f : shake) * Mth.cos(state.ageInTicks * 2.25f) * Mth.PI * 0.1f;
                piece.xRot = side * f;
            }
        };
    }

    private static EntityModel<AvatarRenderState> spikeModel(ModelPart root) {
        ModelPart body = root.getChild(PartNames.BODY);
        ModelPart[] parts = new ModelPart[SPIKE_SPOTS.length];
        for (int i = 0; i < parts.length; i++) parts[i] = body.getChild("spike" + i);
        return new EntityModel<AvatarRenderState>(root) {
            @Override
            public void setupAnim(AvatarRenderState state) {
                Float e = state.getData(SPIKES);
                float out = e == null ? 0f : e;
                for (int i = 0; i < parts.length; i++) {
                    // Each spike quivers a little, like the guardian's.
                    float wobble = 1f + Mth.cos(state.ageInTicks * 1.5f + i) * 0.04f;
                    parts[i].xScale = parts[i].zScale = SPIKE_SIZE;
                    parts[i].yScale = SPIKE_SIZE * out * wobble;
                }
            }
        };
    }

    // --- Drawing ------------------------------------------------------------------------------------

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        String mob = state.getData(MOB);
        // Invisible players show none of their mob parts. A phasing Vex is drawn see-through on
        // purpose (extract() marks it invisible), so its wings stay, unless it's really invisible too.
        boolean phasing = Boolean.TRUE.equals(state.getData(PHASING));
        boolean hidden = Boolean.TRUE.equals(state.getData(REALLY_INVISIBLE)) || (state.isInvisible && !phasing);
        if (!hidden && !"shulker".equals(mob)) drawShell(poseStack, collector, light, state);
        if (!hidden) drawExplosive(poseStack, collector, light, state);
        Float reading = state.getData(READING);
        if (!hidden && reading != null && reading > 0f) {
            Float pages = state.getData(READING_FLIP), covers = state.getData(READING_COVER);
            TomeRenderer.submitInArms(reading, covers == null ? 0f : covers, pages == null ? 0f : pages,
                    state.getData(READING_BURNS) == null ? 0 : state.getData(READING_BURNS), poseStack, collector, light);
        }
        // The Strongholder's Mantle, written on while the Tome is open.
        if (!hidden) MantleRenderer.submit(poseStack, collector, light, state, getParentModel());
        // Elder Spike on a chestplate: the spikes shoot out of anyone's back, the same as the Elder Guardian's.
        if (!hidden && !"elder_guardian".equals(mob)) {
            Float e = state.getData(SPIKES);
            if (e != null && e > 0.02f) draw(spikes, true, RenderTypes.armorCutoutNoCull(ELDER_TEXTURE), poseStack, collector, light, state);
        }
        if (mob == null || hidden) return;
        switch (mob) {
            case "warden" -> {
                String helmet = state.getData(HelmetUpgradeLayer.UPGRADE);
                boolean left = ArmorUpgrades.TENDRIL_LEFT.equals(helmet) || ArmorUpgrades.TENDRIL_BOTH.equals(helmet);
                boolean right = ArmorUpgrades.TENDRIL_RIGHT.equals(helmet) || ArmorUpgrades.TENDRIL_BOTH.equals(helmet);
                // The tendrils light up as they twitch, pulsing a little, as if lit from inside.
                float glow = data(state, SHAKE) * (0.8f + 0.2f * Mth.sin(state.ageInTicks * 0.6f));
                int lit = glowing(light, glow);
                if (!left) draw(tendrilLeft, true, RenderTypes.armorCutoutNoCull(WARDEN_TEXTURE), poseStack, collector, lit, state);
                if (!right) draw(tendrilRight, true, RenderTypes.armorCutoutNoCull(WARDEN_TEXTURE), poseStack, collector, lit, state);
            }
            case "creeper" -> {
                if (Boolean.TRUE.equals(state.getData(CHARGED))) {
                    float f = state.ageInTicks * 0.01f;
                    draw(swirl, false, RenderTypes.energySwirl(SWIRL_TEXTURE, f % 1f, f % 1f), poseStack, collector, light, state);
                }
            }
            // The dragon's wing skin is one flat face, so both sides of it are drawn.
            case "ender_dragon" -> draw(dragonWings, true, RenderTypes.entityCutout(DRAGON_TEXTURE), poseStack, collector, light, state);
            case "bat" -> {
                // The bat's wings and ears are flat sheets with a different picture on each face, in the same
                // spot. Drawing both at once makes them fight and flicker, so only the face toward you is drawn.
                draw(batWings, true, RenderTypes.entityCutoutCull(BAT_TEXTURE), poseStack, collector, light, state);
                String helmet = state.getData(HelmetUpgradeLayer.UPGRADE);
                boolean left = ArmorUpgrades.EAR_LEFT.equals(helmet) || ArmorUpgrades.EAR_BOTH.equals(helmet);
                boolean right = ArmorUpgrades.EAR_RIGHT.equals(helmet) || ArmorUpgrades.EAR_BOTH.equals(helmet);
                if (!left) draw(batEarLeft, true, RenderTypes.entityCutoutCull(BAT_TEXTURE), poseStack, collector, light, state);
                if (!right) draw(batEarRight, true, RenderTypes.entityCutoutCull(BAT_TEXTURE), poseStack, collector, light, state);
            }
            // Blaze rods are always lit, like the blaze itself.
            case "blaze" -> draw(blazeRods, true, RenderTypes.entityCutout(BLAZE_TEXTURE), poseStack, collector,
                    net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, state);
            case "rabbit" -> {
                Integer fur = state.getData(FUR);
                Identifier tex = RABBIT_TEXTURES[fur == null ? 0 : fur];
                draw(rabbitEars, true, RenderTypes.entityCutout(tex), poseStack, collector, light, state);
                draw(rabbitTail, true, RenderTypes.entityCutout(tex), poseStack, collector, light, state);
            }
            case "vex" -> {
                boolean charging = Boolean.TRUE.equals(state.getData(CHARGING));
                draw(vexWings, true, RenderTypes.entityTranslucent(charging ? VEX_CHARGING_TEXTURE : VEX_TEXTURE), poseStack, collector, light, state);
                if (charging) draw(vexEyes, false, RenderTypes.eyes(VEX_EYES_TEXTURE), poseStack, collector, light, state);
            }
            case "breeze" -> draw(breezeWind, true, RenderTypes.breezeWind(BREEZE_WIND_TEXTURE, (state.ageInTicks * 0.02f) % 1f, 0f),
                    poseStack, collector, light, state);
            case "enderman" -> {
                float g = data(state, GLOW);
                if (g > 0.02f) {
                    float f = state.ageInTicks * 0.03f;
                    // The glow adds light, so a darker color is a fainter glow.
                    int v = Math.round(Mth.clamp(g, 0f, 1f) * 255f);
                    drawTinted(swirl, false, RenderTypes.energySwirl(ENDER_GLOW_TEXTURE, f % 1f, (f * 0.7f) % 1f), poseStack, collector, light, state,
                            0xFF000000 | v << 16 | v << 8 | v);
                }
            }
            // Each gill is a flat sheet with its picture on one face only, so both sides show that face.
            case "axolotl" -> draw(axolotlGills, true, RenderTypes.entityCutout(AXOLOTL_TEXTURE), poseStack, collector, light, state);
            case "shulker" -> {
                drawShell(poseStack, collector, light, state);
            }
            case "piglin" -> draw(piglinEars, true, RenderTypes.entityCutout(
                    Boolean.TRUE.equals(state.getData(CURSED)) ? ZOMBIFIED_PIGLIN_TEXTURE : PIGLIN_TEXTURE), poseStack, collector, light, state);
            case "wither" -> {
                draw(witherHeads, true, RenderTypes.entityCutout(WITHER_TEXTURE), poseStack, collector, light, state);
                if (Boolean.TRUE.equals(state.getData(WITHER_ARMOR))) {
                    // The boss's own armor, with its colors and drift.
                    float t = state.ageInTicks;
                    drawTinted(swirl, false, RenderTypes.energySwirl(WITHER_ARMOR_TEXTURE, (Mth.cos(t * 0.02f) * 3f) % 1f, (t * 0.01f) % 1f),
                            poseStack, collector, light, state, 0xFF808080);
                }
            }
            case "ghast" -> {
                // Lit up a little, like the bright clouds in the sky. The ash cloud's embers glow a bit too.
                boolean ash = Boolean.TRUE.equals(state.getData(ASH));
                if (data(state, CLOUD) > 0.01f) draw(ghastCloud, true, RenderTypes.entityTranslucent(ash ? GHAST_ASH_CLOUD_TEXTURE : GHAST_CLOUD_TEXTURE),
                        poseStack, collector, glowing(light, ash ? 0.5f : 0.75f), state);
            }
            case "skeleton" -> {
                draw(quiver, true, RenderTypes.entityCutout(QUIVER_TEXTURE), poseStack, collector, light, state);
                int[] arrows = state.getData(QUIVER_ARROW_COLORS);
                if (arrows != null && arrows.length > 0) {
                    for (int i = 0; i < QUIVER_ARROWS; i++) {
                        // One arrow per kind; with fewer kinds, the first ones repeat to fill the quiver.
                        int color = 0xFF000000 | arrows[i % arrows.length];
                        drawTinted(quiverShafts[i], true, RenderTypes.entityCutout(QUIVER_TEXTURE), poseStack, collector, light, state, 0xFFFFFFFF);
                        // Each feather is a flat sheet with a face on both sides, so only the side facing you is drawn.
                        drawTinted(quiverFeathers[i], true, RenderTypes.entityCutoutCull(QUIVER_TEXTURE), poseStack, collector, light, state, color);
                    }
                }
            }
            case "silverfish" -> draw(silverfishTail, true, RenderTypes.entityCutout(SILVERFISH_TEXTURE), poseStack, collector, light, state);
            case "elder_guardian" -> {
                Float e = state.getData(SPIKES);
                if (e != null && e > 0.02f) draw(spikes, true, RenderTypes.armorCutoutNoCull(ELDER_TEXTURE), poseStack, collector, light, state);
            }
            default -> { }
        }
    }

    /** `animated` runs the piece's own setupAnim after copying the player's pose, for parts that move by themselves. */
    private void draw(EntityModel<AvatarRenderState> piece, boolean animated, net.minecraft.client.renderer.rendertype.RenderType type,
                      PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state) {
        ArmorRenderer.submitTransformCopyingModel(getParentModel(), state, piece, state, animated, collector.order(1),
                poseStack, type, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
    }

    /** The light for something glowing by `glow` (0 to 1): its own light rises toward full brightness. */
    private static int glowing(int light, float glow) {
        int block = (light >> 4) & 0xF, sky = (light >> 20) & 0xF;
        block = Math.max(block, Math.round(Mth.clamp(glow, 0f, 1f) * 15f));
        sky = Math.max(sky, Math.round(Mth.clamp(glow, 0f, 1f) * 15f));
        return sky << 20 | block << 4;
    }

    /**
     * Creeper Essence, on anyone holding a charge: the Enderman's teleport glow, but orange, swirling over the whole
     * body. It flickers faster and faster the longer the charge is held, like a creeper about to blow, and fades out
     * after the throw.
     */
    private void drawExplosive(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state) {
        float g = data(state, EXPLOSIVE);
        if (g <= 0.02f) return;
        float f = state.ageInTicks * 0.04f;
        Float since = state.getData(EXPLOSIVE_HELD);
        float t = since == null ? 0f : since;
        // a flicker that speeds up over the 10 second charge
        float flicker = 0.75f + 0.25f * Mth.sin(t * (0.25f + t * 0.004f));
        int v = Math.round(Mth.clamp(g, 0f, 1f) * flicker * 255f);
        drawTinted(swirl, false, RenderTypes.energySwirl(EXPLOSIVE_GLOW_TEXTURE, f % 1f, (f * 0.7f) % 1f), poseStack, collector, light, state,
                0xFF000000 | v << 16 | v << 8 | v);
    }

    /** Like draw(), but colored: `color` is ARGB, for fading a glow in and out. */
    private void drawTinted(EntityModel<AvatarRenderState> piece, boolean animated, net.minecraft.client.renderer.rendertype.RenderType type,
                            PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, int color) {
        ModelPart from = getParentModel().root(), to = piece.root();
        for (String name : new String[]{PartNames.HEAD, PartNames.BODY, PartNames.RIGHT_ARM, PartNames.LEFT_ARM, PartNames.RIGHT_LEG, PartNames.LEFT_LEG}) {
            if (to.hasChild(name)) to.getChild(name).loadPose(from.getChild(name).storePose());
        }
        if (animated) piece.setupAnim(state);
        collector.order(1).submitModel(piece, state, poseStack, type, light, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
    }

    // --- From the player to the render state ------------------------------------------------------

    /** When each player's creeper fuse started, and when their spikes or tendrils last changed, by entity id. */
    private static final Map<Integer, Float> FUSE_START = new HashMap<>();
    private static final Map<Integer, float[]> SPIKE_STATE = new HashMap<>();   // {on 0 or 1, changed at, extension then}
    private static final Map<Integer, float[]> SHAKE_STATE = new HashMap<>();
    private static final Map<Integer, float[]> WING_STATE = new HashMap<>();
    private static final Map<Integer, float[]> WHIRL_STATE = new HashMap<>();
    private static final Map<Integer, float[]> GLOW_STATE = new HashMap<>();
    private static final Map<Integer, float[]> EXPLOSIVE_STATE = new HashMap<>();
    private static final Map<Integer, Float> EXPLOSIVE_START = new HashMap<>();
    private static final Map<Integer, Float> SELF_FUSE_START = new HashMap<>();
    private static final Map<Integer, float[]> SHELL_STATE = new HashMap<>();
    private static final Map<Integer, float[]> WET_STATE = new HashMap<>();
    private static final Map<Integer, float[]> CLOUD_STATE = new HashMap<>();
    private static final Map<Integer, float[]> QUIVER_STATE = new HashMap<>();

    /** Per player: ear angle, ear swing speed, and the game time it was last stepped. */
    private static final Map<Integer, float[]> EAR_STATE = new HashMap<>();

    /**
     * A floppy spring for rabbit ears. Rising pulls them back and down, falling throws them up and forward,
     * and the sudden stop on landing makes them bounce a couple of times before they settle.
     */
    private static float earSpring(Avatar entity, float now) {
        float vy = (float) (entity.getY() - entity.yo);
        float[] s = EAR_STATE.computeIfAbsent(entity.getId(), k -> new float[]{0f, 0f, now});
        float steps = Mth.clamp(now - s[2], 0f, 4f);
        s[2] = now;
        float target = Mth.clamp(vy * 3.2f, -0.7f, 0.9f);
        // Stepped in quarter ticks so it's smooth whatever the frame rate.
        for (int i = 0, n = Math.round(steps * 4); i < n; i++) {
            s[1] += (target - s[0]) * 0.09f;
            s[1] *= 0.86f;
            s[0] += s[1];
        }
        if (EAR_STATE.size() > 256) EAR_STATE.clear();
        return s[0];
    }

    /** Called by AvatarHelmetMixin for every player drawn. */
    public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
        String mob = entity.getAttached(MobAttachments.MOB);
        state.setData(MOB, mob);
        SkybreakerClient.extract(entity, state, partialTicks);
        MantleRenderer.extract(entity, state, partialTicks);
        state.setData(HELMET, !entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
        state.setData(REALLY_INVISIBLE, entity.isInvisible());
        // The shulker shell is for anyone: the Shulker's Blocking, or a Shulker Essence on any player.
        Integer shellBits = entity.getAttached(MobAttachments.LOOK);
        int shellLook = shellBits == null ? 0 : shellBits;
        state.setData(SHELL, ease(SHELL_STATE, entity.getId(), (shellLook & MobLook.SHELL) != 0,
                entity.level().getGameTime() + partialTicks, 12f, 10f));
        state.setData(CRACKED, (shellLook & MobLook.SHELL_CRACKED) != 0);
        // The Creeper Essence's orange glow is for anyone too.
        float tick = entity.level().getGameTime() + partialTicks;
        boolean charged = (shellLook & MobLook.EXPLOSIVE) != 0;
        state.setData(EXPLOSIVE, ease(EXPLOSIVE_STATE, entity.getId(), charged, tick, 6f, 12f));
        if (charged) state.setData(EXPLOSIVE_HELD, tick - EXPLOSIVE_START.computeIfAbsent(entity.getId(), k -> tick));
        else EXPLOSIVE_START.remove(entity.getId());
        if (EXPLOSIVE_STATE.size() > 256) EXPLOSIVE_STATE.clear();
        // The Creeper Essence's last stand swells and flashes anyone, like a creeper's fuse.
        float selfFuse = 0f;
        if ((shellLook & MobLook.SELF_FUSE) != 0) {
            float start = SELF_FUSE_START.computeIfAbsent(entity.getId(), k -> tick);
            selfFuse = Mth.clamp((tick - start) / 30f, 0f, 1f);
        } else {
            SELF_FUSE_START.remove(entity.getId());
        }
        state.setData(FUSE, selfFuse);
        // Speed and the ears' jump spring are for anyone: Rabbit Ears on a helmet move the same as a Rabbit's own.
        double sx = entity.getX() - entity.xo, sz = entity.getZ() - entity.zo;
        state.setData(SPEED, (float) Math.sqrt(sx * sx + sz * sz));
        state.setData(EAR_JUMP, earSpring(entity, tick));
        // The Stronghold Tome: how open it is raises the arms (TomeArmsMixin) and shows it held in both hands.
        float reading = 0f, covers = 0f, pages = 0f;
        if (entity instanceof net.minecraft.world.entity.LivingEntity living
                && (TomeRenderer.reading(living) || entity.getMainHandItem().is(com.reality.mobcraft.item.ModItems.STRONGHOLD_TOME)
                || entity.getOffhandItem().is(com.reality.mobcraft.item.ModItems.STRONGHOLD_TOME))) {
            float[] tome = TomeRenderer.step(living, TomeRenderer.reading(living));
            reading = TomeRenderer.lift(tome[0]);
            covers = TomeRenderer.cover(tome[0]);
            pages = tome[1];
            net.minecraft.world.item.ItemStack held = living.getMainHandItem().is(com.reality.mobcraft.item.ModItems.STRONGHOLD_TOME)
                    ? living.getMainHandItem() : living.getOffhandItem();
            state.setData(READING_BURNS, com.reality.mobcraft.item.StrongholdTome.burns(held));
        }
        state.setData(READING, reading);
        state.setData(READING_COVER, covers);
        state.setData(READING_FLIP, pages);
        // Spikes are for anyone: the Elder Guardian's own, or an Elder Spike on a chestplate.
        state.setData(SPIKES, ease(SPIKE_STATE, entity.getId(), (shellLook & MobLook.SPIKES) != 0, tick, 3f, 5f));
        if (mob == null) return;
        Integer bits = entity.getAttached(MobAttachments.LOOK);
        int look = bits == null ? 0 : bits;
        float now = entity.level().getGameTime() + partialTicks;
        int id = entity.getId();

        float fuse = 0f;
        if ((look & MobLook.FUSING) != 0) {
            float start = FUSE_START.computeIfAbsent(id, k -> now);
            fuse = Mth.clamp((now - start) / 30f, 0f, 1f);
        } else {
            FUSE_START.remove(id);
        }
        state.setData(FUSE, Math.max(mob.equals("creeper") ? fuse : 0f, selfFuse));
        state.setData(CHARGED, mob.equals("creeper") && (look & MobLook.CHARGED) != 0);
        // Spikes shoot out fast and go back in a little slower. Tendrils settle down slowly, like a warden's.
        state.setData(SHAKE, ease(SHAKE_STATE, id, (look & MobLook.SENSING) != 0, now, 4f, 20f));
        state.setData(WINGS_OPEN, ease(WING_STATE, id, (look & MobLook.FLYING) != 0, now, 6f, 8f));
        state.setData(FULL_WINGS, Boolean.TRUE.equals(entity.getAttached(MobAttachments.DRAGON_WINGS)));
        state.setData(SHIELD, (look & MobLook.SHIELD) != 0);
        if (WING_STATE.size() > 256) WING_STATE.clear();
        if (mob.equals("ender_dragon") || mob.equals("bat") || mob.equals("vex")) flap(entity, state, id, now, !mob.equals("ender_dragon"));
        Integer curse = entity.getAttached(MobAttachments.CURSE_TICKS);
        state.setData(CURSED, curse != null && curse > 0);
        if (mob.equals("rabbit")) {
            // The rabbit's fur follows its home biomes: white in the snow, gold in the desert.
            var biome = entity.level().getBiome(entity.blockPosition());
            int fur = biome.is(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS) || biome.is(net.minecraft.world.level.biome.Biomes.SNOWY_TAIGA) ? 1
                    : biome.is(net.minecraft.world.level.biome.Biomes.DESERT) ? 2 : 0;
            state.setData(FUR, fur);
        }
        boolean phasing = mob.equals("vex") && (look & MobLook.PHASING) != 0;
        state.setData(PHASING, phasing);
        state.setData(CHARGING, mob.equals("vex") && (look & MobLook.CHARGING) != 0);
        // Phasing shows the vex see-through, the way the game shows an invisible teammate.
        if (phasing && !entity.isInvisible()) {
            state.isInvisible = true;
            state.isInvisibleToPlayer = false;
        }
        state.setData(WHIRL, ease(WHIRL_STATE, id, (look & MobLook.WHIRL) != 0, now, 4f, 12f));
        if (WHIRL_STATE.size() > 256) WHIRL_STATE.clear();
        // The glow builds over the half-second wind-up and fades quickly after landing.
        state.setData(GLOW, ease(GLOW_STATE, id, (look & MobLook.TELEPORTING) != 0, now, 8f, 6f));
        if (mob.equals("axolotl")) {
            boolean cursed = curse != null && curse > 0;
            state.setData(WET, ease(WET_STATE, id, !cursed && entity.isInWaterOrRain(), now, 6f, 30f));
        }
        if (GLOW_STATE.size() > 256) GLOW_STATE.clear();
        if (mob.equals("ghast")) {
            boolean flying = (look & MobLook.FLYING) != 0;
            state.setData(CLOUD, ease(CLOUD_STATE, id, flying, now, 8f, 8f));
            state.setData(ASH, entity.level().dimension() == net.minecraft.world.level.Level.NETHER);
            ghastWisps(entity, id, flying);
        }
        if (CLOUD_STATE.size() > 256) CLOUD_STATE.clear();
        if (mob.equals("skeleton")) {
            java.util.List<Integer> arrows = entity.getAttached(MobAttachments.QUIVER);
            state.setData(QUIVER_ARROW_COLORS, arrows == null ? new int[0] : arrows.stream().mapToInt(Integer::intValue).toArray());
            state.setData(QUIVER_SHUT, ease(QUIVER_STATE, id, entity instanceof net.minecraft.world.entity.LivingEntity l && l.isFallFlying(), now, 6f, 6f));
        }
        if (QUIVER_STATE.size() > 256) QUIVER_STATE.clear();
        if (mob.equals("wither")) {
            witherLook(entity, state, id, now);
            state.setData(WITHER_ARMOR, entity.getHealth() <= entity.getMaxHealth() / 2f);
        }
        if (SHELL_STATE.size() > 256) SHELL_STATE.clear();
        if (WET_STATE.size() > 256) WET_STATE.clear();
        if (FUSE_START.size() > 256) FUSE_START.clear();
        if (SPIKE_STATE.size() > 256) SPIKE_STATE.clear();
        if (SHAKE_STATE.size() > 256) SHAKE_STATE.clear();
    }

    /** The game tick each Ghast player last shed wisps, so it happens once a tick, not once a frame. */
    private static final Map<Integer, Long> WISP_TICK = new HashMap<>();

    /**
     * Wisps of cloud drifting off a Ghast player, seen by everyone nearby. On the ground they
     * come off the body now and then and float up. Flying, they puff off the edge of its cloud
     * and trail behind, more the faster it goes. In the Nether the wisps are smoke, and ash
     * rains from under the cloud.
     */
    private static void ghastWisps(Avatar e, int id, boolean flying) {
        long tick = e.level().getGameTime();
        Long last = WISP_TICK.put(id, tick);
        if (last != null && last == tick) return;
        if (WISP_TICK.size() > 256) WISP_TICK.clear();
        var level = e.level();
        var r = e.getRandom();
        double vx = e.getX() - e.xo, vy = e.getY() - e.yo, vz = e.getZ() - e.zo;
        boolean moving = vx * vx + vy * vy + vz * vz > 0.002;
        int n = flying ? (moving ? 2 : (r.nextFloat() < 0.5f ? 1 : 0)) : (moving ? 2 : 1);
        float s = e.getScale();
        boolean nether = level.dimension() == net.minecraft.world.level.Level.NETHER;
        var wisp = nether ? net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE : net.minecraft.core.particles.ParticleTypes.POOF;
        if (nether && flying) {
            // Ash falling from anywhere under the cloud.
            // Mostly dark ash, some lighter flakes so it shows against dark ground.
            for (int i = 0; i < 4; i++) {
                double angle = r.nextDouble() * Math.PI * 2, radius = Math.sqrt(r.nextDouble()) * 0.85 * s;
                level.addParticle(r.nextFloat() < 0.35f ? net.minecraft.core.particles.ParticleTypes.WHITE_ASH : net.minecraft.core.particles.ParticleTypes.ASH,
                        e.getX() + Math.cos(angle) * radius,
                        e.getY() - 0.3 * s, e.getZ() + Math.sin(angle) * radius, 0, -0.05, 0);
            }
        }
        for (int i = 0; i < n; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double ox = Math.cos(angle), oz = Math.sin(angle);
            double x, y, z;
            if (flying) {
                // Round the rim of the cloud, which reaches about 0.7 of a block out from the feet.
                double radius = (0.6 + r.nextDouble() * 0.35) * s;
                x = e.getX() + ox * radius;
                y = e.getY() - (0.15 + r.nextDouble() * 0.15) * s;
                z = e.getZ() + oz * radius;
            } else {
                double radius = e.getBbWidth() * 0.65;
                x = e.getX() + ox * radius;
                y = e.getY() + (0.1 + r.nextDouble() * 0.85) * e.getBbHeight();
                z = e.getZ() + oz * radius;
            }
            level.addParticle(wisp, x, y, z,
                    ox * 0.02 - vx * 0.4, (flying ? -0.02 : 0.01) - vy * 0.3, oz * 0.02 - vz * 0.4);
        }
    }

    /** Each Wither player's side heads: {left yaw, left pitch, right yaw, right pitch, last time}. */
    private static final Map<Integer, float[]> WITHER_HEADS = new HashMap<>();

    /**
     * Points each side head at someone nearby, like the boss's: the left at the closest player or
     * monster within 20 blocks, the right at the next closest (or the same one). With no one around
     * they look about. They turn smoothly, and only so far round.
     */
    private static void witherLook(Avatar entity, AvatarRenderState state, int id, float now) {
        java.util.List<net.minecraft.world.entity.LivingEntity> near = entity.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, entity.getBoundingBox().inflate(20),
                e -> e != entity && e.isAlive() && !e.isSpectator() && !e.isInvisible()
                        && (e instanceof net.minecraft.world.entity.player.Player || e instanceof net.minecraft.world.entity.monster.Enemy));
        near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(entity)));
        float[] s = WITHER_HEADS.computeIfAbsent(id, k -> new float[]{0f, 0f, 0f, 0f, now});
        float dt = Mth.clamp(now - s[4], 0f, 5f);
        s[4] = now;
        float k = 1f - (float) Math.exp(-dt * 0.25f);
        double eyeY = entity.getY() + entity.getBbHeight() * 0.8;
        for (int side = 0; side < 2; side++) {
            var target = near.isEmpty() ? null : near.get(Math.min(side, near.size() - 1));
            float yaw, pitch;
            if (target != null) {
                double dx = target.getX() - entity.getX(), dz = target.getZ() - entity.getZ();
                double dy = target.getEyeY() - eyeY;
                float worldYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
                yaw = Mth.clamp(Mth.wrapDegrees(worldYaw - state.bodyRot), -110f, 110f);
                pitch = Mth.clamp((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -60f, 60f);
            } else {
                yaw = (side == 0 ? 30f : -30f) + Mth.sin(now * 0.04f + side * 2f) * 25f;
                pitch = Mth.sin(now * 0.03f + side) * 10f;
            }
            s[side * 2] += (yaw - s[side * 2]) * k;
            s[side * 2 + 1] += (pitch - s[side * 2 + 1]) * k;
        }
        state.setData(WITHER_LOOK, new float[]{s[0], s[1], s[2], s[3]});
        if (WITHER_HEADS.size() > 256) WITHER_HEADS.clear();
    }

    /** Each flying player's wingbeat: where in the beat they are, and how hard and fast they're beating. */
    private static final class Flap {
        float phase, last = Float.NaN, amp = 1f, rate = 0.045f;
    }

    private static final Map<Integer, Flap> FLAPS = new HashMap<>();

    /**
     * A wingbeat that follows how the player is flying, like a real flier's:
     *   hovering    big, slow, full strokes
     *   climbing    harder and faster
     *   fast flight shallower strokes
     *   diving      wings held out, gliding, barely beating
     * The downstroke is quicker than the upstroke, the wing sweeps forward as it comes down,
     * and the outer half trails behind the inner half. Changes ease in over about a second.
     */
    private static void flap(Avatar entity, AvatarRenderState state, int id, float now, boolean bat) {
        Flap f = FLAPS.computeIfAbsent(id, k -> new Flap());
        float dt = Float.isNaN(f.last) ? 0f : Mth.clamp(now - f.last, 0f, 5f);
        f.last = now;
        double vx = entity.getX() - entity.xo, vy = entity.getY() - entity.yo, vz = entity.getZ() - entity.zo;
        float fast = Mth.clamp((float) Math.sqrt(vx * vx + vz * vz) / 0.5f, 0f, 1f);
        float climb = Mth.clamp((float) vy / 0.25f, -1f, 1f);
        float wantAmp, wantRate;
        if (climb < -0.15f) {
            wantAmp = 0.15f;                       // gliding down
            wantRate = 0.02f;
        } else {
            wantAmp = lerp(fast, 1f, 0.6f) + Math.max(0f, climb) * 0.4f;
            wantRate = lerp(fast, 0.045f, 0.035f) + Math.max(0f, climb) * 0.03f;
        }
        if (bat) wantRate *= 3.2f;                 // bats beat much faster
        float k = 1f - (float) Math.exp(-dt * 0.12f);
        f.amp += (wantAmp - f.amp) * k;
        f.rate += (wantRate - f.rate) * k;
        f.phase = (f.phase + dt * f.rate) % 1f;
        float v = stroke(f.phase);
        state.setData(FLAP_UP, v * f.amp);
        state.setData(FLAP_TIP, (stroke(f.phase - 0.12f) - v) * 0.7f * f.amp);
        state.setData(FLAP_SWEEP, Mth.sin(f.phase * Mth.TWO_PI) * 0.25f * f.amp);
        state.setData(FLAP_TWIST, v * 0.15f * f.amp);
        if (FLAPS.size() > 256) FLAPS.clear();
    }

    /** One wingbeat: 1 at the top, -1 at the bottom. Down in the first 40% of the beat, back up in the rest. */
    private static float stroke(float p) {
        p -= Mth.floor(p);
        return p < 0.4f ? Mth.cos(Mth.PI * p / 0.4f) : -Mth.cos(Mth.PI * (p - 0.4f) / 0.6f);
    }

    /** Goes from 0 to 1 over `up` ticks after switching on, and back to 0 over `down` ticks after switching off. */
    private static float ease(Map<Integer, float[]> map, int id, boolean on, float now, float up, float down) {
        float[] s = map.get(id);
        if (s == null) {
            if (!on) return 0f;
            s = new float[]{1f, now, 0f};
            map.put(id, s);
        }
        boolean was = s[0] > 0.5f;
        float current = was ? Math.min(1f, s[2] + (now - s[1]) / up) : Math.max(0f, s[2] - (now - s[1]) / down);
        if (on != was) {
            s[0] = on ? 1f : 0f;
            s[1] = now;
            s[2] = current;
        }
        if (!on && current <= 0f) map.remove(id);
        return current;
    }

    /** A real creeper's swell: wider and a bit taller as the fuse burns, with a shiver. */
    public static void swell(AvatarRenderState state, PoseStack poseStack) {
        Float fuse = state.getData(FUSE);
        if (fuse == null || fuse <= 0f) return;
        float f = fuse;
        float g = 1f + Mth.sin(f * 100f) * f * 0.01f;
        f = f * f;
        f = f * f;
        float xz = (1f + f * 0.4f) * g, y = (1f + f * 0.1f) / g;
        poseStack.scale(xz, y, xz);
    }

    /** A real creeper's white flashing, faster as the fuse burns. */
    public static float whiteFlash(AvatarRenderState state) {
        Float fuse = state.getData(FUSE);
        if (fuse == null || fuse <= 0f) return -1f;
        return (int) (fuse * 10f) % 2 == 0 ? 0f : Mth.clamp(fuse, 0.5f, 1f);
    }
}
