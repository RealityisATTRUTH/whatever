"""1.21.6 hand edits on the 1.21.7 copy.
- ItemStackRenderState has the model identity itself (1.21.7 moved it to TrackingItemStackRenderState).
- Fabric API for 1.21.6 has no mouse-drag event for screens, so the Update Log panel follows the
  mouse each frame while its title bar is held."""
import sys, pathlib
root = pathlib.Path(sys.argv[1]) / 'com/reality/mobcraft'

def edit(rel, old, new):
    p = root / rel; s = p.read_text()
    assert old in s, (rel, old[:60])
    p.write_text(s.replace(old, new, 1))

edit('mixin/client/ItemStackRenderStateGlowMixin.java',
     '''        if ((Object) this instanceof net.minecraft.client.renderer.item.TrackingItemStackRenderState tracking)
            tracking.appendModelIdentityElement(CHARGED);''',
     '''        ((net.minecraft.client.renderer.item.ItemStackRenderState) (Object) this).appendModelIdentityElement(CHARGED);''')

edit('client/UpdateLogPanel.java',
     '''            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
                    draw(s, graphics, mouseX, mouseY));''',
     '''            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickProgress) -> {
                // No drag event on this version, so the panel follows the mouse while the bar is held.
                if (dragging) {
                    savedX = (int) Math.round(mouseX - grabX);
                    savedY = (int) Math.round(mouseY - grabY);
                }
                draw(s, graphics, mouseX, mouseY);
            });''')
edit('client/UpdateLogPanel.java',
     '''            ScreenMouseEvents.allowMouseDrag(screen).register((s, mx, my, mb, dx, dy) -> {
                MouseButtonEvent event = new MouseButtonEvent(mx, my, mb);
                if (!dragging) return true;
                savedX = (int) Math.round(event.x() - grabX);
                savedY = (int) Math.round(event.y() - grabY);
                return false;
            });
''', '')

# update log: this version's first build
log = root.parents[3] / 'resources/assets/mobcraft/update_log.txt'; s = log.read_text()
s = s.replace(' for Minecraft 1.21.7\n', ' for Minecraft 1.21.6\n')
h = '# Season 3 Beta 5.14 for Minecraft 1.21.6\n'; assert h in s
s = s.replace(h, h + '- Now made to run on Minecraft 1.21.6 with Fabric API for 1.21.6\n', 1)
log.write_text(s)
print('1.21.6 edits applied')
