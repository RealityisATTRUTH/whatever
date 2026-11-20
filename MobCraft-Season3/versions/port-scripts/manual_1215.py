"""1.21.5 hand edits on the 1.21.6 copy (after files_1215 is copied in).
1.21.6 rebuilt the GUI (deferred drawing, 2D pose, render pipelines in blit) and Fabric's HUD API;
1.21.5 still has the layered HUD, PoseStack in GuiGraphics and RenderType functions in blit."""
import sys, re, pathlib
root = pathlib.Path(sys.argv[1]) / 'com/reality/mobcraft'
res = root.parents[3] / 'resources'

def edit(rel, old, new, count=1):
    p = root / rel; s = p.read_text()
    assert old in s, (rel, old[:70])
    p.write_text(s.replace(old, new, count if count else -1))

def sub_all(pattern, repl):
    for p in root.rglob('*.java'):
        s = p.read_text(); t = re.sub(pattern, repl, s)
        if t != s: p.write_text(t)

# HUD: the newer Fabric registry, rebuilt on the layered HUD (compat/hud)
sub_all(r'net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.hud\.', 'com.reality.mobcraft.compat.hud.')
# GUI pictures take a RenderType function, not a pipeline
sub_all(r'\.blit\(RenderPipelines\.GUI_TEXTURED,', '.blit(net.minecraft.client.renderer.RenderType::guiTextured,')
# The camera's position is getPosition()
sub_all(r'getMainCamera\(\)\.position\(\)', 'getMainCamera().getPosition()')
edit('client/LensChain.java', 'Vec3 cam = camera.position();', 'Vec3 cam = camera.getPosition();')
edit('client/WorldToScreen.java', 'Vec3 eye = camera.position();', 'Vec3 eye = camera.getPosition();')
# GUI pose is a PoseStack
edit('client/SenseMarkers.java', '''        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().rotate(angle);''', '''        graphics.pose().pushPose();
        graphics.pose().translate((float) x, (float) y, 0f);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle));''')
edit('client/SenseMarkers.java', 'graphics.pose().popMatrix();', 'graphics.pose().popPose();')
# Armor upgrade icons: drawn by ItemDecorationsMixin
edit('client/ArmorIconOverlay.java', 'import net.fabricmc.fabric.api.client.rendering.v1.DrawItemStackOverlayCallback;\n', '')
edit('client/ArmorIconOverlay.java', '''        DrawItemStackOverlayCallback.EVENT.register((graphics, font, stack, x, y) -> draw(graphics, stack, x, y));''',
     '''        // Drawn by ItemDecorationsMixin on this version.''')
edit('client/ArmorIconOverlay.java', '    private static void draw(GuiGraphics graphics', '    public static void draw(GuiGraphics graphics')
# Sounds: play returns nothing
edit('mixin/client/HushSoundMixin.java', '''    private void mobcraft$hushPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (mobcraft$silenced()) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);''', '''    private void mobcraft$hushPlay(SoundInstance sound, CallbackInfo cir) {
        if (mobcraft$silenced()) cir.cancel();''')
# No icon caching or animated flag for item icons on this version
edit('mixin/client/ItemStackRenderStateGlowMixin.java',
     '        ((net.minecraft.client.renderer.item.ItemStackRenderState) (Object) this).appendModelIdentityElement(CHARGED);\n', '')
edit('mixin/client/ItemStackRenderStateGlowMixin.java', '        setAnimated();\n', '')
edit('mixin/client/ItemStackRenderStateGlowMixin.java', '    @Shadow public abstract void setAnimated();\n', '')
edit('mixin/client/ItemStackRenderStateGlowMixin.java',
     '        // 26.1 caches inventory icons by model, so a charged item would reuse the plain item\'s icon. Mark it apart.\n', '')
edit('mixin/client/ItemStackRenderStateGlowMixin.java', ', and keeps the item redrawn every frame so its swirl moves', '')
edit('client/LensChain.java', '        state.setAnimated();\n', '')
# Special item models have no extents
for f in ['client/GrubRenderer.java', 'client/LensChain.java', 'client/TomeRenderer.java']:
    edit(f, '    @Override\n    public void getExtents(', '    public void getExtents(')
# The charged glow's pipeline: vanilla's entity base, like its energy swirl
edit('client/ExplosiveItemGlow.java',
     'com.mojang.blaze3d.pipeline.RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_SNIPPET)',
     'com.mojang.blaze3d.pipeline.RenderPipeline.builder(com.reality.mobcraft.mixin.client.RenderPipelinesAccessor.mobcraft$entitySnippet())')
edit('client/ExplosiveItemGlow.java', '''                    .withVertexShader("core/entity")
                    .withFragmentShader("core/entity")
''', '')
edit('client/ExplosiveItemGlow.java', '                    .withSampler("Sampler0")\n', '')
edit('client/ExplosiveItemGlow.java', 'new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(TEXTURE, false)',
     'new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(TEXTURE, net.minecraft.util.TriState.FALSE, false)')
# On this version a player's level() is a plain Level, so server-only calls go through ServerLevel
edit('mob/kit/IllusionerKit.java', '        var level = player.level();\n', '        var level = (net.minecraft.server.level.ServerLevel) player.level();\n')
# Mobs and items that came later
edit('mob/kit/GhastKit.java', 'Set.of(EntityType.GHAST, EntityType.HAPPY_GHAST)', 'Set.of(EntityType.GHAST)')
edit('item/Tiers.java', 'b.add(e.attribute(), e.modifier(), e.slot(), e.display());', 'b.add(e.attribute(), e.modifier(), e.slot());')
for f, v in [('mob/kit/WardenKit.java', 'warden'), ('item/EvokerEssence.java', 'player')]:
    edit(f, f'for (ServerPlayer other : {v}.level().players())',
         f'for (ServerPlayer other : ((net.minecraft.server.level.ServerLevel) {v}.level()).players())')
# Mixins
(root / 'mixin/client/PipGlowFlagMixin.java').unlink()
cj = res / 'mobcraft.client.mixins.json'; s = cj.read_text()
assert '    "PipGlowFlagMixin",\n' in s
s = s.replace('    "PipGlowFlagMixin",\n', '    "RenderPipelinesAccessor",\n    "ItemDecorationsMixin",\n')
cj.write_text(s)
# Update log
log = res / 'assets/mobcraft/update_log.txt'; s = log.read_text()
s = s.replace(' for Minecraft 1.21.6\n', ' for Minecraft 1.21.5\n')
s = s.replace('- Now made to run on Minecraft 1.21.6 with Fabric API for 1.21.6\n',
              '- Now made to run on Minecraft 1.21.5 with Fabric API for 1.21.5\n'
              '- The Happy Ghast came in 1.21.6, so on 1.21.5 the Ghast only counts regular ghasts as friends\n', 1)
log.write_text(s)
print('1.21.5 edits applied')
