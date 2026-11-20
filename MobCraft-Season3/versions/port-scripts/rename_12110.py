"""Rename layer 1.21.11 -> 1.21.10, on top of the 1.21.11 source. Checked against the 1.21.10 game jar and
Fabric API 0.138.4+1.21.10."""
import re, sys, pathlib
RULES = [
    # 1.21.11 renamed ResourceLocation to Identifier
    (r'net\.minecraft\.resources\.Identifier\b', 'net.minecraft.resources.ResourceLocation'),
    (r'\bIdentifier\b', 'ResourceLocation'),
    (r'\.identifier\(\)', '.location()'),
    # 1.21.11 split RenderType's factories into RenderTypes and moved both into rendertype
    (r'import net\.minecraft\.client\.renderer\.rendertype\.RenderTypes;', 'import net.minecraft.client.renderer.RenderType;'),
    (r'net\.minecraft\.client\.renderer\.rendertype\.RenderType\b', 'net.minecraft.client.renderer.RenderType'),
    (r'net\.minecraft\.client\.renderer\.rendertype\.RenderTypes\b', 'net.minecraft.client.renderer.RenderType'),
    (r'\bRenderTypes(\.|::)', r'RenderType\1'),
    # Classes 1.21.11 moved into new packages (relocate.py)
    (r'net\.minecraft\.client\.model\.object\.book\.BookModel', 'net.minecraft.client.model.BookModel'),
    (r'net\.minecraft\.client\.model\.object\.projectile\.WindChargeModel', 'net.minecraft.client.model.WindChargeModel'),
    (r'net\.minecraft\.client\.model\.player\.PlayerModel', 'net.minecraft.client.model.PlayerModel'),
    (r'net\.minecraft\.world\.entity\.animal\.rabbit\.Rabbit', 'net.minecraft.world.entity.animal.Rabbit'),
    (r'net\.minecraft\.world\.entity\.projectile\.arrow\.', 'net.minecraft.world.entity.projectile.'),
    (r'net\.minecraft\.world\.entity\.projectile\.hurtingprojectile\.windcharge\.', 'net.minecraft.world.entity.projectile.windcharge.'),
    (r'net\.minecraft\.world\.entity\.projectile\.hurtingprojectile\.', 'net.minecraft.world.entity.projectile.'),
    (r'net\.minecraft\.world\.entity\.projectile\.throwableitemprojectile\.', 'net.minecraft.world.entity.projectile.'),
    # Camera and entities
    (r'\.upVector\(\)', '.getUpVector()'),
    (r'\.leftVector\(\)', '.getLeftVector()'),
    (r'\.needsSync = true', '.hurtMarked = true'),   # 1.21.10 sends the new motion to the player through hurtMarked
    (r'EnchantTableRenderer\.BOOK_TEXTURE', 'EnchantTableRenderer.BOOK_LOCATION'),
    # Screens
    (r'\b((?:graphics|g|gfx|guiGraphics))\.renderOutline\(', r'\1.submitOutline('),
    # Permissions: 1.21.11's COMMANDS_MODERATOR is the moderator level, level 1 in the older number system
    (r'(\w+(?:\(\))?)\.permissions\(\)\.hasPermission\((?:net\.minecraft\.server\.permissions\.)?Permissions\.COMMANDS_MODERATOR\)', r'\1.hasPermission(1)'),
    (r'(\w+\.createCommandSourceStack\(\))\.permissions\(\)\.hasPermission\(Permissions\.COMMANDS_MODERATOR\)', r'\1.hasPermission(1)'),
    (r'import net\.minecraft\.server\.permissions\.Permissions;\n', ''),
]
root = pathlib.Path(sys.argv[1]); n = 0
for f in root.rglob('*.java'):
    s = f.read_text(); o = s
    for a, b in RULES: s = re.sub(a, b, s)
    if s != o: f.write_text(s); n += 1
print('files changed', n)
