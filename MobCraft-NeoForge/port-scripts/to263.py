"""26.3 changes on top of to26.py and to262.py. Run from the project folder."""
import os, re
N = 'src/main/java/com/reality/mobcraft/neo'
# Brewing recipes are data files in 26.3, so the brewing hook and its shim go.
p = N + '/fabric/fabric/api/registry/FabricPotionBrewingBuilder.java'
if os.path.exists(p): os.remove(p)
p = N + '/NeoHooks.java'; s = open(p).read()
s = s.replace('import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;\n', '')
s = re.sub(r'import com\.reality\.mobcraft\.neo\.fabric\.fabric\.api\.registry\.FabricPotionBrewingBuilder;\n', '', s)
s = s.replace('        bus.addListener((RegisterBrewingRecipesEvent e) -> FabricPotionBrewingBuilder.BUILD.invoker().build(e.getBuilder()));\n', '')
# LootTableLoadEvent hands out a HolderGetter.Provider now; MobCraft's loot code wants the full registry access.
s = s.replace('LootTableSource.VANILLA, e.getRegistries()));', 'LootTableSource.VANILLA, lootRegistries(e)));')
s = s.replace('    private static void cancelUnlessPass(', '''    private static net.minecraft.core.HolderLookup.Provider lootRegistries(LootTableLoadEvent e) {
        if (e.getRegistries() instanceof net.minecraft.core.HolderLookup.Provider p) return p;
        return new GetterLookup(e.getRegistries());
    }

    private static void cancelUnlessPass(''')
open(p, 'w').write(s)
# Fabric 26.3's ArmorRenderer has no crumbling overlay argument.
p = N + '/fabric/fabric/api/client/rendering/v1/ArmorRenderer.java'; s = open(p).read()
s = s.replace('RenderType type, int light, int overlay, int color, ModelFeatureRenderer.CrumblingOverlay crumbling) {', 'RenderType type, int light, int overlay, int color) {')
s = s.replace('pose, type, light, overlay, color, crumbling);', 'pose, type, light, overlay, color);')
open(p, 'w').write(s)
