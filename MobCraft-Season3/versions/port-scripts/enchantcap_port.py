"""Copies the enchant cap (Beta 5.12) from the 26.3 source into an older-version source tree and bumps it to 5.12.
Usage: enchantcap_port.py <target project dir> <minecraft version label>"""
import sys, shutil, pathlib
S = pathlib.Path('/tmp/claude-0/-home-claude/ba4f4dfa-e2f3-5a6d-bd72-b2683d6d5eab/scratchpad/src/mobcraft-s3')
d = pathlib.Path(sys.argv[1]); V = sys.argv[2]
J = 'src/main/java/com/reality/mobcraft/'
for rel in ['item/EnchantCap.java', 'mixin/EnchantCapAnvilMixin.java']:
    shutil.copy(S / J / rel, d / J / rel)
def edit(rel, a, b):
    f = d / rel; s = f.read_text()
    if b in s: return
    assert s.count(a) == 1, (rel, a[:60]); f.write_text(s.replace(a, b))
edit('src/main/resources/mobcraft.mixins.json', '    "AnvilKeepLevelsMixin",\n', '    "AnvilKeepLevelsMixin",\n    "EnchantCapAnvilMixin",\n')
edit(J + 'item/MaceEnchants.java', "        if (!result.is(ItemTags.MACE_ENCHANTABLE) || random.nextFloat() >= AWAKEN_CHANCE) return false;",
     "        if (!result.is(ItemTags.MACE_ENCHANTABLE) || EnchantCap.full(result) || random.nextFloat() >= AWAKEN_CHANCE) return false;")
edit(J + 'item/GearEnchants.java', "        if (result.is(ItemTags.MACE_ENCHANTABLE) || random.nextFloat() >= 0.3f) return;",
     "        if (result.is(ItemTags.MACE_ENCHANTABLE) || EnchantCap.full(result) || random.nextFloat() >= 0.3f) return;")
src = (S / J / 'client/MobCraftClient.java').read_text()
b = src.index('        // Gear with MobCraft enchants shows'); e = src.index('        // Itchy armor')
edit(J + 'client/MobCraftClient.java', '        // Itchy armor', src[b:e] + '        // Itchy armor')
p = d / 'gradle.properties'; s = p.read_text()
s = s.replace(f'mod_version=5.11.0-beta+{V}', f'mod_version=5.12.0-beta+{V}').replace(f'jar_version=beta-5.11-mc{V}', f'jar_version=beta-5.12-mc{V}')
assert '5.12' in s; p.write_text(s)
log = (S / 'src/main/resources/assets/mobcraft/update_log.txt').read_text()
b = log.index('# Season 3 Beta 5.12\n'); e = log.index('# Season 3 Beta 5.10\n')
entry = log[b:e].replace('# Season 3 Beta 5.12\n', f'# Season 3 Beta 5.12 for Minecraft {V}\n')
entry = '\n'.join(l for l in entry.split('\n') if not l.startswith('- Same number on every')) 
edit('src/main/resources/assets/mobcraft/update_log.txt', f'# Season 3 Beta 5.11 for Minecraft {V}\n', entry + f'# Season 3 Beta 5.11 for Minecraft {V}\n')
edit('README.md', '(beta 5.11)', '(beta 5.12)')
print('ported to', d)
