import os, re, shutil, zipfile, glob
S = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(S, 'pack'); shutil.rmtree(out, ignore_errors=True)
rows = []
jars_info = []
def key(name):
    mc = name.split('-')[0]
    return [int(p) for p in mc.split('.')]
for d in sorted(os.listdir(os.path.join(S, 'versions')), key=key):
    loader = d.split('-')[1]
    jars = [j for j in glob.glob(os.path.join(S, 'versions', d, 'build', 'libs', '*.jar')) if 'sources' not in j and 'dev' not in os.path.basename(j)]
    props = dict(l.strip().split('=', 1) for l in open(os.path.join(S, 'versions', d, 'gradle.properties')) if '=' in l)
    label = props['adob.label'].split('-', 1)[1]
    if not jars:
        rows.append((loader, label, None)); continue
    os.makedirs(os.path.join(out, loader), exist_ok=True)
    name = f"auto-drink-ominous-bottles-{props['adob.label']}.jar"
    shutil.copy(jars[0], os.path.join(out, loader, name))
    rows.append((loader, label, name))
    jars_info.append({'file': name, 'loader': loader, 'label': label, 'range': props['adob.range']})
names = {'fabric': 'Fabric', 'forge': 'Forge', 'neoforge': 'NeoForge'}
lines = ['AUTO-DRINK OMINOUS BOTTLES 2.0.0, EVERY VERSION', '',
 'Drinks an Ominous Bottle for you the moment a raid is won, so you can chain raids.',
 'Press K to turn it on or off (rebind it under Controls, Miscellaneous).', '',
 'HOW TO INSTALL',
 '1. Find your Minecraft version and mod loader below.',
 '2. Put that one jar in your mods folder.',
 '3. Fabric needs Fabric API. Forge and NeoForge need nothing extra.',
 'It only runs on your game, so servers do not need it.', '']
for loader in ['fabric', 'forge', 'neoforge']:
    lines.append(names[loader].upper())
    for l, label, name in rows:
        if l != loader: continue
        lines.append(f"  Minecraft {label:<16} {name or 'not built'}")
    lines.append('')
lines += ['UPLOADING TO MODRINTH', '  upload_modrinth.py uploads every jar here to a Modrinth project with the right tags.', '  Read the top of that file for how to run it.', '']
open(os.path.join(out, 'README.txt'), 'w').write('\n'.join(lines))
ver = open(os.path.join(S, 'gradle.properties')).read().split('mod_version=')[1].split()[0]
import json
json.dump({'version': ver, 'jars': jars_info}, open(os.path.join(out, 'manifest.json'), 'w'), indent=2)
shutil.copy(os.path.join(S, 'upload_modrinth.py'), out)
z = os.path.join(S, 'AutoDrinkOminousBottles-all-versions.zip')
if os.path.exists(z): os.remove(z)
with zipfile.ZipFile(z, 'w', zipfile.ZIP_DEFLATED) as f:
    for root, _, files in os.walk(out):
        for fn in files:
            p = os.path.join(root, fn); f.write(p, os.path.relpath(p, out))
print('\n'.join(lines)); print(z, os.path.getsize(z))
