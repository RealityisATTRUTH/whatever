"""26.2 changes on top of to26.py. Run from the project folder."""
import os, re
N = 'src/main/java/com/reality/mobcraft/neo'
p = N + '/fabric/fabric/api/client/rendering/v1/WorldRenderEvents.java'
if os.path.exists(p): os.remove(p)
p = N + '/NeoClientHooks.java'; s = open(p).read()
s = s.replace('Minecraft.getInstance().gui.extractCrosshair(g, t)', 'Minecraft.getInstance().gui.hud.extractCrosshair(g, t)')
s = s.replace('cb.registerRenderers(EntityType.PLAYER,', 'cb.registerRenderers(net.minecraft.world.entity.EntityTypes.PLAYER,')
open(p, 'w').write(s)
p = 'src/main/resources/META-INF/accesstransformer.cfg'
s = open(p).read().replace('public net.minecraft.client.gui.Gui extractCrosshair(', 'public net.minecraft.client.gui.Hud extractCrosshair(')
open(p, 'w').write(s)
