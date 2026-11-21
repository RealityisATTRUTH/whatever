"""Renames MobCraft's NeoForge shim to Fabric API's and Minecraft's 26.x names. Run from the project folder."""
import os, re, shutil
N = 'src/main/java/com/reality/mobcraft/neo'
F = N + '/fabric/fabric/api'
moves = {  # old shim path -> new shim path (relative to F), and class renames
    'client/rendering/v1/LivingEntityFeatureRendererRegistrationCallback.java': 'client/rendering/v1/LivingEntityRenderLayerRegistrationCallback.java',
    'client/rendering/v1/EntityModelLayerRegistry.java': 'client/rendering/v1/ModelLayerRegistry.java',
    'client/keybinding/v1/KeyBindingHelper.java': 'client/keymapping/v1/KeyMappingHelper.java',
    'itemgroup/v1/FabricItemGroup.java': 'creativetab/v1/FabricCreativeModeTab.java',
    'registry/FabricBrewingRecipeRegistryBuilder.java': 'registry/FabricPotionBrewingBuilder.java',
    'client/rendering/v1/DrawItemStackOverlayCallback.java': 'client/rendering/v1/ExtractItemDecorationsCallback.java',
}
for old, new in moves.items():
    if os.path.exists(f'{F}/{old}'):
        os.makedirs(os.path.dirname(f'{F}/{new}'), exist_ok=True)
        shutil.move(f'{F}/{old}', f'{F}/{new}')
# the 1.21.10 world package is replaced by the level package written separately
shutil.rmtree(f'{F}/client/rendering/v1/world', ignore_errors=True)
subs = [
    (r'\bLivingEntityFeatureRendererRegistrationCallback\b', 'LivingEntityRenderLayerRegistrationCallback'),
    (r'\bEntityModelLayerRegistry\b', 'ModelLayerRegistry'),
    (r'keybinding\.v1\.KeyBindingHelper\b', 'keymapping.v1.KeyMappingHelper'),
    (r'package (\S+)\.keybinding\.v1;', r'package \1.keymapping.v1;'),
    (r'\bKeyBindingHelper\b', 'KeyMappingHelper'), (r'\bregisterKeyBinding\b', 'registerKeyMapping'),
    (r'itemgroup\.v1\.FabricItemGroup\b', 'creativetab.v1.FabricCreativeModeTab'),
    (r'package (\S+)\.itemgroup\.v1;', r'package \1.creativetab.v1;'),
    (r'\bFabricItemGroup\b', 'FabricCreativeModeTab'),
    (r'\bFabricBrewingRecipeRegistryBuilder\b', 'FabricPotionBrewingBuilder'),
    (r'\bDrawItemStackOverlayCallback\b', 'ExtractItemDecorationsCallback'),
    (r'\bonDrawItemStackOverlay\b', 'onExtractItemDecorations'),
    (r'\bplayC2S\(\)', 'serverboundPlay()'), (r'\bplayS2C\(\)', 'clientboundPlay()'),
    (r'\bgetButtons\(', 'getWidgets('), (r'\bgetTextRenderer\(', 'getFont('),
    (r'\bafterRender\(Screen', 'afterExtract(Screen'), (r'\bbeforeRender\(Screen', 'beforeExtract(Screen'),
    (r'ScreenEvents\.afterRender\b', 'ScreenEvents.afterExtract'), (r'ScreenEvents\.beforeRender\b', 'ScreenEvents.beforeExtract'),
    (r'import net\.minecraft\.client\.gui\.GuiGraphics;', 'import net.minecraft.client.gui.GuiGraphicsExtractor;'),
    (r'\bGuiGraphics\b', 'GuiGraphicsExtractor'),
    (r'net\.minecraft\.client\.renderer\.state\.LevelRenderState', 'net.minecraft.client.renderer.state.level.LevelRenderState'),
    (r'BlockEvent\.BreakEvent', 'net.neoforged.neoforge.event.level.block.BreakBlockEvent'),
    (r'import net\.neoforged\.neoforge\.event\.level\.BlockEvent;\n', ''),
    (r'e\.getNewDamage\(\)', 'DamageCompat.dealt(e)'),
]
for root, _, files in os.walk(N):
    for f in files:
        if not f.endswith('.java'): continue
        p = os.path.join(root, f); s = open(p).read(); o = s
        for a, b in subs: s = re.sub(a, b, s)
        if s != o: open(p, 'w').write(s)
# Level render events come from NeoForge's own events on 26.x.
p = N + '/NeoClientHooks.java'; s = open(p).read()
L = 'com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.level'
s = s.replace('        // 1.21.9: MobCraft fires its own world render events from WorldRenderEventsMixin.\n', f'''        bus.addListener((net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent e) ->
                {L}.LevelRenderEvents.END_EXTRACTION.invoker().endExtraction(new {L}.LevelExtractionContext(e.getDeltaTracker(), e.getLevel(), e.getRenderState())));
        bus.addListener((net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent e) -> {{
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            {L}.LevelRenderEvents.COLLECT_SUBMITS.invoker().collectSubmits(new {L}.LevelRenderContext(
                    e.getSubmitNodeCollector(), e.getPoseStack(), mc.getDeltaTracker(), mc.level, e.getLevelRenderState()));
        }});
''')
open(p, 'w').write(s)
# Screen render hooks and HUD elements are "extract" on 26.x.
for rel in ['/fabric/fabric/api/client/screen/v1/ScreenEvents.java', '/NeoClientHooks.java',
            '/fabric/fabric/api/client/rendering/v1/hud/HudElement.java']:
    p = N + rel
    if not os.path.exists(p): continue
    s = open(p).read()
    s = re.sub(r'\bafterRender\b', 'afterExtract', s); s = re.sub(r'\bbeforeRender\b', 'beforeExtract', s)
    s = re.sub(r'\bAfterRender\b', 'AfterExtract', s); s = re.sub(r'\bBeforeRender\b', 'BeforeExtract', s)
    s = re.sub(r'\bAFTER_RENDER\b', 'AFTER_EXTRACT', s); s = re.sub(r'\bBEFORE_RENDER\b', 'BEFORE_EXTRACT', s)
    if rel.endswith('HudElement.java'): s = s.replace('void render(', 'void extractRenderState(')
    if rel.endswith('NeoClientHooks.java'):
        s = s.replace('en.element()::render', 'en.element()::extractRenderState')
        s = s.replace('replacer.apply(original).render(', 'replacer.apply(original).extractRenderState(')
        s = s.replace('Minecraft.getInstance().gui.renderCrosshair(g, t)', 'Minecraft.getInstance().gui.extractCrosshair(g, t)')
    open(p, 'w').write(s)
p = 'src/main/resources/META-INF/accesstransformer.cfg'
if os.path.exists(p):
    s = open(p).read().replace('net.minecraft.client.gui.Gui renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;', 'net.minecraft.client.gui.Gui extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;')
    open(p, 'w').write(s)
# Block breaking goes through BreakHooks, which works with both NeoForge 26.1 event names.
p = N + '/NeoHooks.java'; s = open(p).read()
a = s.find('        bus.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.event.level.block.BreakBlockEvent e) -> {')
if a >= 0:
    b = s.index('        });\n', a) + len('        });\n')
    s = s[:a] + '        BreakHooks.register(bus);\n' + s[b:]
open(p, 'w').write(s)
