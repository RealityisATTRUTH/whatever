"""Rename layer 26.1 -> 1.21.11 for MobCraft. Each rule was checked against the 1.21.11 game jar or Fabric API 0.141.6+1.21.11."""
import re, sys, pathlib
RULES = [
    # Fabric API: 26.1 moved Fabric's own names to Mojang's
    (r'PayloadTypeRegistry\.serverboundPlay\(\)', 'PayloadTypeRegistry.playC2S()'),
    (r'PayloadTypeRegistry\.clientboundPlay\(\)', 'PayloadTypeRegistry.playS2C()'),
    (r'net\.fabricmc\.fabric\.api\.client\.keymapping\.v1\.KeyMappingHelper', 'net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper'),
    (r'\bKeyMappingHelper\.registerKeyMapping\b', 'KeyBindingHelper.registerKeyBinding'),
    (r'\bKeyMappingHelper\b', 'KeyBindingHelper'),
    (r'\bModelLayerRegistry\b', 'EntityModelLayerRegistry'),
    (r'\bLivingEntityRenderLayerRegistrationCallback\b', 'LivingEntityFeatureRendererRegistrationCallback'),
    (r'net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.level\.LevelRenderEvents', 'net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents'),
    (r'net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.level\.LevelRenderContext', 'net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext'),
    (r'net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.level\.LevelExtractionContext', 'net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext'),
    (r'\bLevelRenderEvents\b', 'WorldRenderEvents'),
    (r'\bLevelRenderContext\b', 'WorldRenderContext'),
    (r'\bLevelExtractionContext\b', 'WorldExtractionContext'),
    (r'net\.fabricmc\.fabric\.api\.creativetab\.v1\.FabricCreativeModeTab', 'net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup'),
    (r'\bFabricCreativeModeTab\b', 'FabricItemGroup'),
    (r'\bFabricPotionBrewingBuilder\b', 'FabricBrewingRecipeRegistryBuilder'),
    (r'net\.fabricmc\.fabric\.api\.client\.rendering\.v1\.ExtractItemDecorationsCallback', 'net.fabricmc.fabric.api.client.rendering.v1.DrawItemStackOverlayCallback'),
    (r'\bExtractItemDecorationsCallback\b', 'DrawItemStackOverlayCallback'),
    (r'ScreenEvents\.afterExtract\(', 'ScreenEvents.afterRender('),
    (r'Screens\.getWidgets\(', 'Screens.getButtons('),
    (r'Screens\.getFont\(', 'Screens.getTextRenderer('),
    # Screens: 26.1 renamed GuiGraphics and the render methods
    (r'\bGuiGraphicsExtractor\b', 'GuiGraphics'),
    (r'\b((?:graphics|g|gfx|guiGraphics))\.centeredText\(', r'\1.drawCenteredString('),
    (r'\b((?:graphics|g|gfx|guiGraphics))\.text\(', r'\1.drawString('),
    (r'\b((?:graphics|g|gfx|guiGraphics))\.outline\(', r'\1.renderOutline('),
    # Only screen and widget methods (their first parameter is the GuiGraphics). Entity renderers keep extractRenderState.
    (r'void extractRenderState\((final )?GuiGraphics', r'void render(\1GuiGraphics'),
    (r'super\.extractRenderState\((graphics|g|gfx|guiGraphics),', r'super.render(\1,'),
    (r'void extractBackground\((final )?GuiGraphics', r'void renderBackground(\1GuiGraphics'),
    (r'super\.extractBackground\((graphics|g|gfx|guiGraphics),', r'super.renderBackground(\1,'),
    (r'void extractWidgetRenderState\((final )?GuiGraphics', r'void renderWidget(\1GuiGraphics'),
    (r'original\.extractRenderState\((graphics|g|gfx|guiGraphics),', r'original.render(\1,'),
]
root = pathlib.Path(sys.argv[1])
n = 0
for f in root.rglob('*.java'):
    s = f.read_text(); o = s
    for a, b in RULES: s = re.sub(a, b, s)
    if s != o: f.write_text(s); n += 1
print('files changed', n)
RULES2 = [
    # Fabric world rendering context: 26.1 names -> 1.21.11 names
    (r'\bcontext\.submitNodeCollector\(\)', 'context.commandQueue()'),
    (r'\bcontext\.poseStack\(\)', 'context.matrices()'),
    (r'\bcontext\.levelState\(\)', 'context.worldState()'),
    (r'\bcontext\.deltaTracker\(\)', 'context.tickCounter()'),
    (r'WorldRenderEvents\.COLLECT_SUBMITS', 'WorldRenderEvents.BEFORE_ENTITIES'),
    # Players and entities
    (r'\.sendOverlayMessage\(', '.mobcraft$overlay('),
    (r'\.entityTags\(\)', '.getTags()'),
    (r'\bContainerInput\b', 'ClickType'),
    (r'net\.minecraft\.world\.inventory\.ClickType\b', 'net.minecraft.world.inventory.ClickType'),
    (r'\.nonEmptyItemCopyStream\(\)', '.nonEmptyStream()'),  # read-only use, so no copy is needed
    # Rendering
    (r'RenderTypes\.entityCutoutCull\(', 'RenderTypes.entityCutout('),
    (r'net\.minecraft\.client\.resources\.model\.geometry\.BakedQuad', 'net.minecraft.client.renderer.block.model.BakedQuad'),
    (r'\.materialInfo\(\)\.sprite\(\)', '.sprite()'),
    # 26.1 moved the level render states into state.level; in 1.21.11 they sit in state.
    (r'net\.minecraft\.client\.renderer\.state\.level\.', 'net.minecraft.client.renderer.state.'),
    (r'net/minecraft/client/renderer/state/level/', 'net/minecraft/client/renderer/state/'),
    (r'net\.minecraft\.util\.LightCoordsUtil', 'net.minecraft.client.renderer.LightTexture'),
    (r'\bLightCoordsUtil\b', 'LightTexture'),
    (r'BookModel\.State\.forAnimation\(', 'new BookModel.State('),
]
if __name__ == '__main__' and len(sys.argv) > 2 and sys.argv[2] == '2':
    n = 0
    for f in root.rglob('*.java'):
        s = f.read_text(); o = s
        for a, b in RULES2: s = re.sub(a, b, s)
        if s != o: f.write_text(s); n += 1
    print('pass 2 files changed', n)
