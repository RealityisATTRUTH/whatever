"""Hand edits 26.1 -> 1.21.11 that a rename can't express. Every edit must apply exactly once."""
import sys, pathlib
root = pathlib.Path(sys.argv[1]) / 'com/reality/mobcraft'
def edit(rel, old, new, count=1):
    f = root / rel; s = f.read_text()
    n = s.count(old)
    if n != count: raise SystemExit(f'{rel}: expected {count} of {old[:60]!r}, found {n}')
    f.write_text(s.replace(old, new))

# Special item models: 1.21.11's Unbaked isn't generic, submit() also gets the display context,
# and the baking context hands out a MaterialSet instead of 26.1's SpriteGetter.
for rel in ['client/GrubRenderer.java', 'client/LensChain.java', 'client/TomeRenderer.java']:
    edit(rel, 'implements SpecialModelRenderer.Unbaked<Arg> {', 'implements SpecialModelRenderer.Unbaked {')
    edit(rel, 'public SpecialModelRenderer<Arg> bake(', 'public SpecialModelRenderer<?> bake(')
    edit(rel, 'public void submit(Arg arg, PoseStack poseStack,', 'public void submit(Arg arg, net.minecraft.world.item.ItemDisplayContext displayContext, PoseStack poseStack,')
edit('mixin/client/SpecialModelRenderersMixin.java', 'SpecialModelRenderer.Unbaked<?>>>', 'SpecialModelRenderer.Unbaked>>')
edit('client/TomeRenderer.java', 'import net.minecraft.client.resources.model.sprite.SpriteGetter;', 'import net.minecraft.client.resources.model.MaterialSet;\nimport net.minecraft.client.renderer.rendertype.RenderTypes;')
edit('client/TomeRenderer.java', 'context.sprites())', 'context.materials())')
edit('client/TomeRenderer.java', 'private final SpriteGetter sprites;', 'private final MaterialSet sprites;')
edit('client/TomeRenderer.java', 'private TomeRenderer(BookModel model, SpriteGetter sprites) {', 'private TomeRenderer(BookModel model, MaterialSet sprites) {')
# 1.21.11 has no submitModel(texture, sprites) shortcut: do what its enchanting table does.
edit('client/TomeRenderer.java', '''        collector.submitModel(model, state, poseStack, light, OverlayTexture.NO_OVERLAY, -1,
                EnchantTableRenderer.BOOK_TEXTURE, sprites, outlineColor, null);''', '''        collector.submitModel(model, state, poseStack, EnchantTableRenderer.BOOK_TEXTURE.renderType(RenderTypes::entitySolid),
                light, OverlayTexture.NO_OVERLAY, -1, sprites.get(EnchantTableRenderer.BOOK_TEXTURE), outlineColor, null);''')

# 1.21.11's sprite contents can't say up front that a texture is fully opaque, so always build the pixel mask.
edit('client/ExplosiveItemGlow.java', '        if (contents.transparency().isOpaque()) return null;\n', '')

# Recipes: 1.21.11 recipes take a category and a registry lookup, and serializers are interfaces.
edit('item/ArmorUpgradeRecipe.java', 'public static final RecipeSerializer<ArmorUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);', '''public static final RecipeSerializer<ArmorUpgradeRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override public MapCodec<ArmorUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ArmorUpgradeRecipe> streamCodec() { return STREAM_CODEC; }
    };

    private ArmorUpgradeRecipe() {
        super(net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT);
    }''')
edit('item/ArmorUpgradeRecipe.java', 'public ItemStack assemble(CraftingInput input) {', 'public ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {')

# Item particles take a stack in 1.21.11.
edit('item/GearEnchants.java', 'new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, shield.getItem())', 'new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new ItemStack(shield.getItem()))')
edit('item/ShulkerEssence.java', 'new ItemParticleOption(ParticleTypes.ITEM, Items.SHULKER_SHELL)', 'new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SHULKER_SHELL))', count=2)

# Rabbit disguise: 26.1 rabbits hop and tilt their heads with keyframe animations. 1.21.11 rabbits show their hop
# from the jump timer this disguise already drives, and have no head tilt, so there's nothing extra to start.
f = root / 'client/RabbitDisguise.java'; s = f.read_text()
a = s.index('    private static void animate(Rabbit rabbit, Player player, boolean hopping) {')
b = s.index('\n    }\n', a) + len('\n    }\n')
f.write_text(s[:a] + '''    private static void animate(Rabbit rabbit, Player player, boolean hopping) {
        // 1.21.11: the hop comes from the jump timer (see tick). There's no head tilt before 26.1.
    }
''' + s[b:])
edit('client/RabbitDisguise.java', 'context.level().getEntity(', 'context.world().getEntity(')

# Messages to a Player (not a ServerPlayer): 1.21.11 only has displayClientMessage there. false = chat, not the action bar.
for rel, n in [('client/rts/RtsView.java', 1), ('item/Duskvein.java', 1), ('item/GearEnchants.java', 1),
               ('item/Gloamite.java', 1), ('item/MaceEnchants.java', 1)]:
    edit(rel, 'player.sendSystemMessage(', 'player.mobcraft$chat(', count=n)

# ---- Mixin hooks (checked against the 1.21.11 game code) ----
# Melee: 1.21.11 has no separate attack packet. Attacks come through handleInteract, which runs the same
# getBoundingBox() reach check.
edit('mixin/ServerAttackMixin.java', '@WrapOperation(method = "handleAttack"', '@WrapOperation(method = "handleInteract"', count=2)
# Lambdas and inner-class fields have no Mojang names in 1.21.11, so they keep Fabric's intermediary names.
edit('mixin/EnchantDuskveinMixin.java', 'method = "lambda$clickMenuButton$0"', 'method = "method_17410"')
edit('mixin/client/PurpleTemplateMixin.java', 'method = "lambda$create$0"', 'method = "method_52851"')
edit('mixin/client/ItemLayerGlowMixin.java', 'ItemStackRenderState this$0;', 'ItemStackRenderState field_55345;')
edit('mixin/client/ItemLayerGlowMixin.java', '(Object) this$0)', '(Object) field_55345)')
# Camera: 26.1's update(DeltaTracker) is 1.21.11's setup(...). The shake only needs the end of it.
edit('mixin/client/CameraShakeMixin.java', '@Inject(method = "update", at = @At("TAIL"))\n    private void mobcraft$shake(DeltaTracker delta, CallbackInfo ci) {',
     '@Inject(method = "setup", at = @At("TAIL"))\n    private void mobcraft$shake(CallbackInfo ci) {')
# Skull Visor: 1.21.11's Mob doesn't override canAttack, so hook LivingEntity's and check it's a Mob.
edit('mixin/SkullVisorTargetMixin.java', '''    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void mobcraft$visorCanAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (SkullVisor.ignores((Mob) (Object) this, target) || com.reality.mobcraft.item.RabbitEars.hidden(target)) cir.setReturnValue(false);
    }

''', '')
(root / 'mixin/SkullVisorCanAttackMixin.java').write_text('''package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.SkullVisor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skull Visor and the rabbit disguise: mobs won't attack you. 1.21.11 keeps canAttack on LivingEntity, not Mob. */
@Mixin(LivingEntity.class)
public abstract class SkullVisorCanAttackMixin {
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mobcraft$visorCanAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Mob mob)) return;
        if (SkullVisor.ignores(mob, target) || com.reality.mobcraft.item.RabbitEars.hidden(target)) cir.setReturnValue(false);
    }
}
''')
f = root.parent.parent.parent.parent / 'resources/mobcraft.mixins.json'; s = f.read_text()
assert s.count('"SkullVisorTargetMixin",') == 1
f.write_text(s.replace('"SkullVisorTargetMixin",', '"SkullVisorTargetMixin",\n    "SkullVisorCanAttackMixin",'))
# Duskvein shift-click: 1.21.11's enchanting table checks for lapis with is(Item), not 26.1's generic is(T).
edit('mixin/EnchantShiftClickMixin.java', 'target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"', 'target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"')
edit('mixin/EnchantShiftClickMixin.java', 'ItemStack stack, Object item, Operation<Boolean> original)', 'ItemStack stack, net.minecraft.world.item.Item item, Operation<Boolean> original)')
# Held Enderman (RTS): the two-argument teleports only exist from 26.3. Before that it's randomTeleport(DDDZ)Z.
edit('mixin/HeldEndermanMixin.java', '"randomTeleport(DDDZLjava/util/function/Predicate;)Z"', '"randomTeleport(DDDZLjava/util/function/Predicate;)Z",\n            "randomTeleport(DDDZ)Z"')
# The mod itself: Minecraft 1.21.11 and Java 21.
f = root.parent.parent.parent.parent / 'resources/fabric.mod.json'; s = f.read_text()
assert '"minecraft": ">=26.1 <26.2"' in s and '"java": ">=25"' in s
f.write_text(s.replace('"minecraft": ">=26.1 <26.2"', '"minecraft": "1.21.11"').replace('"java": ">=25"', '"java": ">=21"'))

# ---- Minecraft's own textures and models ----
# 26.1 moved some entity textures into folders and renamed the rabbit ones. Checked file by file against the
# 1.21.11 client jar (vanilla_refs.py); all are pixel-identical except the rabbit's, which 26.1 redrew.
TEX = {'textures/entity/bat/bat.png': 'textures/entity/bat.png',
       'textures/entity/blaze/blaze.png': 'textures/entity/blaze.png',
       'textures/entity/guardian/guardian_elder.png': 'textures/entity/guardian_elder.png',
       'textures/entity/silverfish/silverfish.png': 'textures/entity/silverfish.png',
       'textures/entity/rabbit/rabbit_brown.png': 'textures/entity/rabbit/brown.png',
       'textures/entity/rabbit/rabbit_gold.png': 'textures/entity/rabbit/gold.png',
       'textures/entity/rabbit/rabbit_white.png': 'textures/entity/rabbit/white.png'}
for f in root.rglob('*.java'):
    s = f.read_text(); o = s
    for a, b in TEX.items(): s = s.replace('"' + a + '"', '"' + b + '"')
    if s != o: f.write_text(s)
# 1.21.11's rabbit model keeps the head (with the ears) and the tail on the root, not inside the body.
edit('client/HelmetUpgradeLayer.java', 'context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.RABBIT).getChild("body").getChild("head");',
     'context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.RABBIT).getChild("head");')
edit('client/MobLookLayer.java', '''        ModelPart rabbitBody = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.RABBIT).getChild("body");
        ModelPart rabbitHead = rabbitBody.getChild("head");''', '''        ModelPart rabbitBody = context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.RABBIT);
        ModelPart rabbitHead = rabbitBody.getChild("head");''')

# Silverfish Grub: 1.21.11 draws a hand-built shape in the first-person view badly (it comes out streaked), while it
# draws item quads right everywhere. So the grub hands its faces to the game as ordinary item quads, textured from
# the item atlas (the grub texture is already there because its base model uses it).
edit('client/GrubRenderer.java', """        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE),
                (pose, buffer) -> emit(pose.pose(), pose.normal(), buffer, data, count, light, overlay));""",
"""        collector.submitItem(poseStack, displayContext, light, overlay, outlineColor, new int[0], quads(data, count),
                net.minecraft.client.renderer.Sheets.translucentItemSheet(), net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.NONE);""")
edit('client/GrubRenderer.java', """    private static void emit(""", """    /** The collected faces as item quads: positions in model pixels, UVs mapped into the grub's spot in the item atlas. */
    private static java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads(float[] d, int count) {
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(net.minecraft.data.AtlasIds.ITEMS).getSprite(MobCraft.id("item/silverfish_grub_3d"));
        java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> out = new java.util.ArrayList<>(count / 4);
        for (int q = 0; q + 3 < count; q += 4) {
            Vector3f[] p = new Vector3f[4];
            long[] uv = new long[4];
            for (int k = 0; k < 4; k++) {
                int o = (q + k) * 8;
                p[k] = new Vector3f(d[o], d[o + 1], d[o + 2]);
                uv[k] = net.minecraft.client.model.geom.builders.UVPair.pack(sprite.getU(d[o + 3]), sprite.getV(d[o + 4]));
            }
            int o = q * 8;
            net.minecraft.core.Direction face = net.minecraft.core.Direction.getApproximateNearest(d[o + 5], d[o + 6], d[o + 7]);
            out.add(new net.minecraft.client.renderer.block.model.BakedQuad(p[3], p[2], p[1], p[0], uv[3], uv[2], uv[1], uv[0],
                    -1, face, sprite, true, 0));
        }
        return out;
    }

    private static void emit(""")
# Neighbouring grub segments can have side faces on exactly the same plane (once the body is at full thickness), and
# 1.21.11 shows that as banding. Each segment is a hair (0.3%) fatter than the one before, so they never tie.
edit('client/GrubRenderer.java', """            q.box(c, u, v, w, len / 2, th / 2, depth * 0.36f, side, back, belly, end);
            q.box(c, u, v, w, len / 2, th * 0.36f, depth * 0.54f, side, back, belly, end);""",
"""            float apart = 1 + i * 0.003f;
            q.box(c, u, v, w, len / 2, th / 2 * apart, depth * 0.36f * apart, side, back, belly, end);
            q.box(c, u, v, w, len / 2, th * 0.36f * apart, depth * 0.54f * apart, side, back, belly, end);""")

# Charged-item glow pipeline: 1.21.11 sets blend and depth on the builder itself, and its entity format is NEW_ENTITY.
edit('client/ExplosiveItemGlow.java', '.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE))',
     '.withBlend(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE)')
edit('client/ExplosiveItemGlow.java', 'com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY,', 'com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,')
edit('client/ExplosiveItemGlow.java', '.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, false))',
     '.withDepthWrite(false)')
# A 1.21.11 quad doesn't know its render type. Items are drawn with these sheets, so those are the ones to draw first.
edit('client/ExplosiveItemGlow.java', '''                    for (BakedQuad q : all) {
                        var t2 = q.materialInfo().itemRenderType();
                        if (t2 != null && done.add(t2)) source.endBatch(t2);
                    }''', '''                    for (var t2 : java.util.List.of(net.minecraft.client.renderer.Sheets.translucentItemSheet(),
                            net.minecraft.client.renderer.Sheets.translucentBlockItemSheet(), net.minecraft.client.renderer.Sheets.cutoutBlockSheet(),
                            net.minecraft.client.renderer.Sheets.solidBlockSheet()))
                        if (done.add(t2)) source.endBatch(t2);''')

# Rabbit ears: before 26.1 each ear's box starts 4 pixels above its pivot (26.1 redrew the rabbit with the box at the
# pivot), so ears placed for 26.x float above the head. MobCraft builds its own ears with 26.x's box, using the same
# texture spots as 1.21.x's ears (left at 58,0, right at 52,0 on the 64x32 rabbit texture).
edit('client/MobLookLayer.java', '        rabbitEars = rabbitEarModel(rabbitHead.getChild("left_ear"), rabbitHead.getChild("right_ear"));',
     '        rabbitEars = rabbitEarModel(rabbitEar(58), rabbitEar(52));')
edit('client/HelmetUpgradeLayer.java', '        rabbitEars = MobLookLayer.rabbitEarModel(rabbitHead.getChild("left_ear"), rabbitHead.getChild("right_ear"));',
     '        rabbitEars = MobLookLayer.rabbitEarModel(MobLookLayer.rabbitEar(58), MobLookLayer.rabbitEar(52));')
edit('client/MobLookLayer.java', '''    static EntityModel<AvatarRenderState> rabbitEarModel(ModelPart left, ModelPart right) {''',
'''    /** One rabbit ear shaped like 26.x's (its box sits on the pivot), textured from the spot at u,0. */
    static ModelPart rabbitEar(int u) {
        net.minecraft.client.model.geom.builders.MeshDefinition mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
        mesh.getRoot().addOrReplaceChild("ear", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(u, 0)
                .addBox(-1f, -4.2929f, -0.1213f, 2f, 5f, 1f), net.minecraft.client.model.geom.PartPose.ZERO);
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("ear");
    }

    static EntityModel<AvatarRenderState> rabbitEarModel(ModelPart left, ModelPart right) {''')
