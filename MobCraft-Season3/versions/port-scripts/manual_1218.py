"""1.21.8 on top of the 1.21.9 copy. Usage: manual_1218.py <src/main/java> <1.21.8|1.21.7>
1.21.8 draws models, shapes and items right away into a MultiBufferSource instead of queueing them, has no shared
player-like model (Avatar), and uses the older mouse and key handling. MobCraft gets small stand-ins for the new
pieces (compat), and each file is adjusted below."""
import sys, re, pathlib
java = pathlib.Path(sys.argv[1]); V = sys.argv[2] if len(sys.argv) > 2 else '1.21.8'
root = java / 'com/reality/mobcraft'; res = java.parent / 'resources'
FILES = pathlib.Path(__file__).parent / 'files_1218'

def write(rel, text):
    p = root / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text)
def read(rel): return (root / rel).read_text()
def edit(rel, a, b, count=1):
    p = root / rel; s = p.read_text()
    assert s.count(a) == count, (rel, a[:80], s.count(a)); p.write_text(s.replace(a, b))
def resub(rel, pat, rep, count=None, flags=0):
    p = root / rel; s = p.read_text(); s2, n = re.subn(pat, rep, s, flags=flags)
    assert n and (count is None or n == count), (rel, pat, n); p.write_text(s2)

# ---- whole files written for 1.21.8 (kept in files_1218, same paths) ----
for f in FILES.rglob('*.java'):
    rel = f.relative_to(FILES); (root / rel).parent.mkdir(parents=True, exist_ok=True); (root / rel).write_text(f.read_text())

# ---- names that changed, across every file ----
REN = [
    (r'\bnet\.minecraft\.client\.renderer\.OrderedSubmitNodeCollector\b', 'com.reality.mobcraft.compat.SubmitNodeCollector'),
    (r'\bnet\.minecraft\.client\.renderer\.SubmitNodeCollector\b', 'com.reality.mobcraft.compat.SubmitNodeCollector'),
    (r'\bOrderedSubmitNodeCollector\b', 'SubmitNodeCollector'),
    (r'\bAvatarRenderState\b', 'PlayerRenderState'),
    (r'\bAvatarRenderer\b', 'PlayerRenderer'),
    (r'import net\.minecraft\.world\.entity\.Avatar;', 'import net.minecraft.world.entity.player.Player;'),
    (r'\bnet\.minecraft\.world\.entity\.Avatar\b', 'net.minecraft.world.entity.player.Player'),
    (r'(?<![\w.])Avatar(?=[\s>)])', 'Player'),
    (r'import net\.minecraft\.world\.entity\.ItemOwner;', 'import net.minecraft.world.entity.LivingEntity;'),
    (r'\bnet\.minecraft\.world\.entity\.ItemOwner\b', 'net.minecraft.world.entity.LivingEntity'),
    (r'(?<![\w.])ItemOwner\b', 'LivingEntity'),
    (r'import net\.minecraft\.client\.input\.MouseButtonEvent;', 'import com.reality.mobcraft.compat.MouseButtonEvent;'),
    (r'import net\.minecraft\.client\.input\.KeyEvent;', 'import com.reality.mobcraft.compat.KeyEvent;'),
    (r'\.getGameProfile\(\)\.name\(\)', '.getGameProfile().getName()'),
    (r'\.submitOutline\(', '.renderOutline('),
]
for f in root.rglob('*.java'):
    s = f.read_text(); s0 = s
    for a, b in REN: s = re.sub(a, b, s)
    if s != s0: f.write_text(s)
print('1.21.8 edits applied')

# ---- 1.21.9's world-render mixin and extraction context aren't used on 1.21.8 (Fabric's own events are) ----
for rel in ['mixin/client/WorldRenderEventsMixin.java', 'compat/world/WorldExtractionContext.java']:
    if (root / rel).exists(): (root / rel).unlink()
mj = res / 'mobcraft.client.mixins.json'; s = mj.read_text()
s = s.replace('    "WorldRenderEventsMixin",\n', ''); mj.write_text(s)

# ---- older mouse and key handling: each handler takes the values and builds the event it used to get ----
INPUT = [
    (r'public boolean mouseClicked\(MouseButtonEvent event, boolean doubleClick\) \{',
     'public boolean mouseClicked(double mobcraft$x, double mobcraft$y, int mobcraft$button) {\n'
     '        MouseButtonEvent event = new MouseButtonEvent(mobcraft$x, mobcraft$y, mobcraft$button); boolean doubleClick = false;'),
    (r'super\.mouseClicked\(event, doubleClick\)', 'super.mouseClicked(event.x(), event.y(), event.button())'),
    (r'public boolean mouseReleased\(MouseButtonEvent event\) \{',
     'public boolean mouseReleased(double mobcraft$x, double mobcraft$y, int mobcraft$button) {\n'
     '        MouseButtonEvent event = new MouseButtonEvent(mobcraft$x, mobcraft$y, mobcraft$button);'),
    (r'super\.mouseReleased\(event\)', 'super.mouseReleased(event.x(), event.y(), event.button())'),
    (r'public boolean (keyPressed|keyReleased)\(KeyEvent event\) \{',
     r'public boolean \1(int mobcraft$key, int mobcraft$scan, int mobcraft$mods) {\n'
     '        KeyEvent event = new KeyEvent(mobcraft$key, mobcraft$scan, mobcraft$mods);'),
    (r'super\.(keyPressed|keyReleased)\(event\)', r'super.\1(event.key(), event.scancode(), event.modifiers())'),
    (r'\.matches\(event\)', '.matches(event.key(), event.scancode())'),
    (r'public void onClick\(MouseButtonEvent event, boolean doubleClick\) \{',
     'public void onClick(double mobcraft$x, double mobcraft$y) {\n'
     '        MouseButtonEvent event = new MouseButtonEvent(mobcraft$x, mobcraft$y, 0); boolean doubleClick = false;'),
    (r'public void onRelease\(MouseButtonEvent event\) \{',
     'public void onRelease(double mobcraft$x, double mobcraft$y) {\n'
     '        MouseButtonEvent event = new MouseButtonEvent(mobcraft$x, mobcraft$y, 0);'),
    (r'\b(?:this\.minecraft|minecraft|Minecraft\.getInstance\(\)|client)\.has(Shift|Control|Alt)Down\(\)',
     r'net.minecraft.client.gui.screens.Screen.has\1Down()'),
    (r'\.getWindow\(\)\.handle\(\)', '.getWindow().getWindow()'),
    (r'getProfile\(\)\.name\(\)', 'getProfile().getName()'),
    (r'\b(\w+)\.asLivingEntity\(\)', r'\1'),
]
for f in root.rglob('*.java'):
    s = f.read_text(); s0 = s
    for a, b in INPUT: s = re.sub(a, b, s)
    if s != s0: f.write_text(s)

edit('client/UpdateLogPanel.java', 'ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> click(event));',
     'ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, mb) -> click(new MouseButtonEvent(mx, my, mb)));')
edit('client/UpdateLogPanel.java', 'ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> {',
     'ScreenMouseEvents.allowMouseDrag(screen).register((s, mx, my, mb, dx, dy) -> {\n                MouseButtonEvent event = new MouseButtonEvent(mx, my, mb);')
edit('client/UpdateLogPanel.java', 'ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {',
     'ScreenMouseEvents.allowMouseRelease(screen).register((s, mx, my, mb) -> {')

# Key categories are plain translation keys before 1.21.9.
edit('client/AbilityKeys.java', 'private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MobCraft.id("abilities"));',
     'private static final String CATEGORY = "key.category.mobcraft.abilities";')

# Server side.
edit('mob/kit/KitUtil.java', 'level.getChunkSource().sendToTrackingPlayersAndSelf(entity, new ClientboundSetEntityMotionPacket(entity));',
     'level.getChunkSource().broadcastAndSend(entity, new ClientboundSetEntityMotionPacket(entity));')
edit('item/GhastWisp.java', 'ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_SMALL, WeightedList.of(), SoundEvents.WIND_CHARGE_BURST);',
     'ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_SMALL, SoundEvents.WIND_CHARGE_BURST);')
edit('mixin/ShulkerBulletAimMixin.java', 'Entity current = EntityReference.getEntity(finalTarget, self.level());',
     'Entity current = finalTarget == null ? null : finalTarget.getEntity(self.level(), Entity.class);')
edit('mixin/ShulkerBulletAimMixin.java', 'finalTarget = EntityReference.of(next);', 'finalTarget = new EntityReference<>(next);')

# ---- drawing: each renderer keeps its submit code, and the game's render call hands it a collector that draws now ----
LAYER = (r'@Override\n(\s+)public void submit\(PoseStack poseStack, SubmitNodeCollector collector, int light, PlayerRenderState state,\s*float yRot, float xRot\) \{',
         r'@Override\n\1public void render(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {\n'
         r'\1    submit(poseStack, new SubmitNodeCollector(buffers), light, state, yRot, xRot);\n\1}\n\n'
         r'\1public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, PlayerRenderState state, float yRot, float xRot) {')
for rel in ['client/MobLookLayer.java', 'client/HelmetUpgradeLayer.java', 'client/SkullVisorLayer.java']:
    resub(rel, LAYER[0], LAYER[1], 1)
SPECIAL = (r'@Override\n(\s+)public void submit\(Arg arg, net\.minecraft\.world\.item\.ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil,\s*int outlineColor\) \{',
           r'@Override\n\1public void render(Arg arg, net.minecraft.world.item.ItemDisplayContext displayContext, PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay, boolean foil) {\n'
           r'\1    submit(arg, displayContext, poseStack, new SubmitNodeCollector(buffers), light, overlay, foil, 0);\n\1}\n\n'
           r'\1public void submit(Arg arg, net.minecraft.world.item.ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outlineColor) {')
for rel in ['client/GrubRenderer.java', 'client/LensChain.java', 'client/TomeRenderer.java']:
    resub(rel, SPECIAL[0], SPECIAL[1], 1)
    resub(rel, r'public SpecialModelRenderer<\?> bake\(SpecialModelRenderer\.BakingContext context\) \{',
          'public SpecialModelRenderer<?> bake(net.minecraft.client.model.geom.EntityModelSet context) {', 1)
for f in root.rglob('*.java'):
    s = f.read_text(); s2 = s.replace('state.outlineColor', '0')
    if s2 != s: f.write_text(s2)

# The Tome: 1.21.8's book model is posed with four numbers, and its texture comes from the block atlas directly.
edit('client/TomeRenderer.java', 'return new TomeRenderer(new BookModel(context.entityModelSet().bakeLayer(ModelLayers.BOOK)), context.materials());',
     'return new TomeRenderer(new BookModel(context.bakeLayer(ModelLayers.BOOK)));')
edit('client/TomeRenderer.java', '''    private final BookModel model;
    private final MaterialSet sprites;

    private TomeRenderer(BookModel model, MaterialSet sprites) {
        this.model = model;
        this.sprites = sprites;''', '''    private final BookModel model;

    private TomeRenderer(BookModel model) {
        this.model = model;''')
resub('client/TomeRenderer.java', r'import net\.minecraft\.client\.resources\.model\.MaterialSet;\n', '', 1)
edit('client/TomeRenderer.java', '''        BookModel.State state = new BookModel.State(arg.time(), flip1, flip2, ease);
        collector.submitModel(model, state, poseStack, EnchantTableRenderer.BOOK_LOCATION.renderType(RenderType::entitySolid),
                light, OverlayTexture.NO_OVERLAY, -1, sprites.get(EnchantTableRenderer.BOOK_LOCATION), outlineColor, null);''',
'''        model.setupAnim(arg.time(), flip1, flip2, ease);
        model.renderToBuffer(poseStack, EnchantTableRenderer.BOOK_LOCATION.buffer(collector.buffers(), RenderType::entitySolid),
                light, OverlayTexture.NO_OVERLAY);''')
edit('client/TomeRenderer.java', '''            collector.order(1).submitModel(model, state, poseStack,
                    net.minecraft.client.renderer.RenderType.entityCutout(BURN[Math.min(arg.burns(), 4) - 1]),
                    light, OverlayTexture.NO_OVERLAY, outlineColor, null);''',
'''            model.renderToBuffer(poseStack, collector.buffers().getBuffer(
                    net.minecraft.client.renderer.RenderType.entityCutout(BURN[Math.min(arg.burns(), 4) - 1])),
                    light, OverlayTexture.NO_OVERLAY);''')

# The Grub's texture: 1.21.8 hands out the block atlas by name.
resub('client/GrubRenderer.java',
      r'net\.minecraft\.client\.Minecraft\.getInstance\(\)\.getAtlasManager\(\)\s*\.getAtlasOrThrow\(net\.minecraft\.data\.AtlasIds\.BLOCKS\)\.getSprite\(([^;]*)\);',
      r'net.minecraft.client.Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(\1);', 1)

# The thrown Ghast Wisp draws itself with the game's render call.
edit('client/GhastWispRenderer.java', '''    public void submit(ThrownItemRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {''',
     '''    public void render(ThrownItemRenderState state, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {''')
edit('client/GhastWispRenderer.java', '''        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        super.submit(state, pose, collector, camera);''', '''        state.item.render(pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(state, pose, buffers, light);''')
resub('client/GhastWispRenderer.java', r'import net\.minecraft\.client\.renderer\.state\.CameraRenderState;\n', '', 1)
resub('client/GhastWispRenderer.java', r'import com\.reality\.mobcraft\.compat\.SubmitNodeCollector;\n', '')

# Player look data: 1.21.8 has only players, drawn by PlayerRenderer from an AbstractClientPlayer.
edit('mixin/client/AvatarHelmetMixin.java', 'public abstract class AvatarHelmetMixin<AvatarlikeEntity extends Player & ClientAvatarEntity> {',
     'public abstract class AvatarHelmetMixin {')
edit('mixin/client/AvatarHelmetMixin.java', 'extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V',
     'extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V')
edit('mixin/client/AvatarHelmetMixin.java', 'private void mobcraft$helmetUpgrade(AvatarlikeEntity entity,',
     'private void mobcraft$helmetUpgrade(net.minecraft.client.player.AbstractClientPlayer entity,')
resub('mixin/client/AvatarHelmetMixin.java', r'import net\.minecraft\.client\.entity\.ClientAvatarEntity;\n', '', 1)

# Rabbit disguise: swapped where each entity is drawn (RabbitDisguiseRenderMixin), not in a gathered list.
edit('client/RabbitDisguise.java', '''        // Once the world's been gathered up to draw, swap each disguised player for their rabbit.
        WorldRenderEvents.END_EXTRACTION.register(RabbitDisguise::swap);
    }''', '''        // Each disguised player is drawn as their rabbit by RabbitDisguiseRenderMixin.
    }''')
s = read('client/RabbitDisguise.java'); a = s.index('    private static void swap('); b = s.index('    /** The rabbit to draw instead')
write('client/RabbitDisguise.java', s[:a] + s[b:])
resub('client/RabbitDisguise.java', r'import com\.reality\.mobcraft\.compat\.world\.(WorldExtractionContext|WorldRenderEvents);\n', '', 2)
cj = res / 'mobcraft.client.mixins.json'; s = cj.read_text(); anchor = '  "client": [\n'
cj.write_text(s.replace(anchor, anchor + '    "RabbitDisguiseRenderMixin",\n', 1))

# Showing yourself in RTS view: 1.21.8 decides who to draw in collectVisibleEntities.
edit('mixin/client/RtsSelfRenderMixin.java',
     'method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/LevelRenderState;)V",',
     'method = "collectVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/List;)Z",')

# ---- mixins on the game's drawing: 1.21.8 calls render(..., MultiBufferSource, ...) where 1.21.9 calls submit ----
# Backwards tier, held by others: ItemInHandLayer draws the item with ItemStackRenderState.render.
edit('mixin/client/BackwardsItemMixin.java', '''    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void mobcraft$backwards(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline,
                                    Operation<Void> original) {''', '''    @WrapOperation(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void mobcraft$backwards(ItemStackRenderState item, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay,
                                    Operation<Void> original) {''')
edit('mixin/client/BackwardsItemMixin.java', 'original.call(item, pose, collector, light, overlay, outline);', 'original.call(item, pose, buffers, light, overlay);', 2)

# Backwards tier in first person: ItemInHandRenderer.renderItem hands the item to ItemRenderer.renderStatic.
edit('mixin/client/BackwardsFirstPersonMixin.java', '''    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void mobcraft$backwards(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline,
                                    Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {''', '''    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V"))
    private void mobcraft$backwards(net.minecraft.client.renderer.entity.ItemRenderer renderer, net.minecraft.world.entity.LivingEntity entity,
                                    ItemStack stack, net.minecraft.world.item.ItemDisplayContext context, PoseStack pose,
                                    net.minecraft.client.renderer.MultiBufferSource buffers, net.minecraft.world.level.Level level,
                                    int light, int overlay, int seed, Operation<Void> original) {''')
edit('mixin/client/BackwardsFirstPersonMixin.java', 'original.call(item, pose, collector, light, overlay, outline);',
     'original.call(renderer, entity, stack, context, pose, buffers, level, light, overlay, seed);', 2)

# Dragon wings hide the elytra: WingsLayer.render.
edit('mixin/client/WingsLayerMixin.java', '''    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",''',
     '''    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",''')
edit('mixin/client/WingsLayerMixin.java', 'private void mobcraft$dragonWings(PoseStack poseStack, SubmitNodeCollector collector,',
     'private void mobcraft$dragonWings(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers,')

# Charged glow: drawn at the end of each item layer's render, into the same buffers.
edit('mixin/client/ItemLayerGlowMixin.java', '''    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void mobcraft$glow(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        if (mobcraft$explosive && ExplosiveItemGlow.shows(((ExplosiveGlowState) (Object) field_55345).mobcraft$displayContext()))
            ExplosiveItemGlow.submit(poseStack, collector, quads);''', '''    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void mobcraft$glow(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (mobcraft$explosive && ExplosiveItemGlow.shows(((ExplosiveGlowState) (Object) field_55345).mobcraft$displayContext()))
            ExplosiveItemGlow.submit(poseStack, new SubmitNodeCollector(buffers), quads);''')

# Reading the Tome in first person: the map hands and the book draw into the hand's buffers.
edit('mixin/client/TomeHandsMixin.java', 'abstract void mobcraft$mapHand(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidArm arm);',
     'abstract void mobcraft$mapHand(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, HumanoidArm arm);')
edit('mixin/client/TomeHandsMixin.java', 'PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {',
     'PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {')
edit('mixin/client/TomeHandsMixin.java', '''            mobcraft$mapHand(poseStack, collector, light, HumanoidArm.RIGHT);
            mobcraft$mapHand(poseStack, collector, light, HumanoidArm.LEFT);''', '''            mobcraft$mapHand(poseStack, buffers, light, HumanoidArm.RIGHT);
            mobcraft$mapHand(poseStack, buffers, light, HumanoidArm.LEFT);''')
edit('mixin/client/TomeHandsMixin.java', 'TomeRenderer.submitHeld(me, poseStack, collector, light);',
     'TomeRenderer.submitHeld(me, poseStack, new SubmitNodeCollector(buffers), light);')

# ---- update log: this version's first build, so 1.21.9's 5.12 and 5.13 notes go under one 5.13 heading ----
log = res / 'assets/mobcraft/update_log.txt'; s = log.read_text()
h13, h12 = '# Season 3 Beta 5.13 for Minecraft 1.21.9\n', '# Season 3 Beta 5.12 for Minecraft 1.21.9\n'
a = s.index(h13); b = s.index(h12); e = s.index('\n# ', b + 1) + 1
notes13 = ''.join(l for l in s[a + len(h13):b].splitlines(True) if not l.startswith("- Rabbit ears (the Rabbit's own"))
notes12 = s[b + len(h12):e].replace('1.21.9', V)
keys = '- Ability 2 (G) works even though this Minecraft version also puts Quick Actions on G\n'
s = s[:a] + f'# Season 3 Beta 5.13 for Minecraft {V}\n' + notes12 + keys + notes13 + s[e:]
s = s.replace('# Season 3 Beta 5.14 for Minecraft 1.21.9\n', f'# Season 3 Beta 5.14 for Minecraft {V}\n', 1)
s = s.replace('# Season 3 Beta 5.15 for Minecraft 1.21.9\n', f'# Season 3 Beta 5.15 for Minecraft {V}\n', 1)
log.write_text(s)

# Binds sharing a key all get it, as from 1.21.9 (KeyMappingShareMixin): Ability 2 shares G with Quick Actions.
cj = res / 'mobcraft.client.mixins.json'; s = cj.read_text(); anchor = '  "client": [\n'
cj.write_text(s.replace(anchor, anchor + '    "KeyMappingShareMixin",\n', 1))

# ---- 1.21.7 ----
if V == '1.21.7':
    F17 = pathlib.Path(__file__).parent / 'files_1217'
    for f in F17.rglob('*.java'):
        rel = f.relative_to(F17); (root / rel).parent.mkdir(parents=True, exist_ok=True); (root / rel).write_text(f.read_text())
    # Fabric API for 1.21.7 has no render-state data, so MobCraft carries its own (compat.RenderStateDataKey).
    for f in root.rglob('*.java'):
        s = f.read_text(); s0 = s
        s = s.replace('net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey', 'com.reality.mobcraft.compat.RenderStateDataKey')
        s = re.sub(r'\b(state|avatar)\.getData\(', r'com.reality.mobcraft.compat.RenderStateDataKey.get(\1, ', s)
        s = re.sub(r'\b(state|avatar)\.setData\(', r'com.reality.mobcraft.compat.RenderStateDataKey.set(\1, ', s)
        if s != s0: f.write_text(s)
    cj = res / 'mobcraft.client.mixins.json'; s = cj.read_text(); anchor = '  "client": [\n'
    cj.write_text(s.replace(anchor, anchor + '    "RenderStateDataMixin",\n', 1))
    # EnchantmentHelper.getComponentType is private in 1.21.7: books keep stored enchants, everything else its own.
    s = read('item/Duskvein.java')
    s2 = re.sub(r'EnchantmentHelper\.getComponentType\((\w+)\)',
                r'(\1.is(net.minecraft.world.item.Items.ENCHANTED_BOOK) ? net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS : net.minecraft.core.component.DataComponents.ENCHANTMENTS)', s)
    assert s2 != s; write('item/Duskvein.java', s2)
