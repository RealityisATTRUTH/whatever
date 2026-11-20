"""Lists every reference MobCraft makes to Minecraft's own assets and reports the ones missing from a client jar.
vanilla_refs.py SRC_MAIN CLIENT_JAR"""
import re, sys, zipfile, pathlib, json
src = pathlib.Path(sys.argv[1]); jar = zipfile.ZipFile(sys.argv[2])
names = set(jar.namelist())
have = lambda p: ('assets/minecraft/' + p) in names
missing = {}
def note(ref, where):
    missing.setdefault(ref, set()).add(where)
# Java: withDefaultNamespace("textures/...") / ("models/...") and "minecraft:..." strings
for f in (src / 'java').rglob('*.java'):
    s = f.read_text()
    for m in re.finditer(r'withDefaultNamespace\("((?:textures|models|sounds)/[^"]+)"\)', s):
        if not have(m.group(1)): note(m.group(1), f.name)
# Resources: any "minecraft:item/x", "minecraft:block/x", "minecraft:entity/x" style texture/model ids in JSON
for f in (src / 'resources/assets').rglob('*.json'):
    s = f.read_text()
    for m in re.finditer(r'"minecraft:((?:item|block|entity|trims|gui|misc|painting|particle|mob_effect)/[a-z0-9_/.-]+)"', s):
        ref = m.group(1)
        ok = have('textures/' + ref + '.png') or have('models/' + ref + '.json') or have('items/' + ref.split('/')[-1] + '.json')
        if not ok: note(ref, str(f.relative_to(src)))
    for m in re.finditer(r'"(?:parent|model)"\s*:\s*"(?:minecraft:)?((?:item|block|builtin)/[a-z0-9_/.-]+)"', s):
        ref = m.group(1)
        if ref.startswith('builtin/'): continue
        if not have('models/' + ref + '.json'): note('model ' + ref, str(f.relative_to(src)))
for ref, where in sorted(missing.items()):
    print(ref, '<-', ', '.join(sorted(where))[:200])
print('missing:', len(missing))
