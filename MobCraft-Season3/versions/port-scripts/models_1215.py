"""1.21.5 item models: an element may only turn by -45, -22.5, 0, 22.5 or 45 degrees.
1.21.6 takes any angle from -45 to 45. Run after models_12110.py; rewrites every model that uses other angles, exactly:
  - an element turned by any other angle goes to its own part model, with the turn folded into the display transforms;
  - models only used as a special model's base (their elements are never drawn) just round the angle.
The part that moves to the model becomes its own model file, with the turn folded into every display transform,
and the item definitions draw the parts together (minecraft:composite). Models only used as a special model's base
(their elements are never drawn) just keep the remainders.
Usage: models_1215.py <resources dir> <block.json from the 1.21.10 jar>"""
import json, math, sys, pathlib
import numpy as np

res = pathlib.Path(sys.argv[1]) / 'assets/mobcraft'
BLOCK = json.load(open(sys.argv[2]))
CTX = ['none', 'thirdperson_lefthand', 'thirdperson_righthand', 'firstperson_lefthand', 'firstperson_righthand',
       'head', 'gui', 'ground', 'fixed', 'on_shelf']
LEFT = {'thirdperson_lefthand', 'firstperson_lefthand'}
M = np.diag([-1.0, 1, 1])


def rx(a): c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def ry(a): c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def rz(a): c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
AX = {'x': rx, 'y': ry, 'z': rz}


def euler_xyz(R):
    """Degrees (x, y, z) with R = Rx Ry Rz, the order a display rotation uses."""
    b = math.asin(max(-1, min(1, R[0, 2])))
    if abs(R[0, 2]) < 0.999999:
        a = math.atan2(-R[1, 2], R[2, 2]); c = math.atan2(-R[0, 1], R[0, 0])
    else:
        a = math.atan2(R[2, 1], R[1, 1]); c = 0.0
    out = [round(math.degrees(t), 4) for t in (a, b, c)]
    assert np.allclose(rx(math.radians(out[0])) @ ry(math.radians(out[1])) @ rz(math.radians(out[2])), R, atol=1e-4)
    return out


def display(ctx, model):
    """A model's transform for one context, after the left-hand fallback and the parent (block/block)."""
    for d in (model.get('display', {}), BLOCK['display']):
        if ctx in d: return d[ctx]
        if ctx == 'thirdperson_lefthand' and 'thirdperson_righthand' in d: return d['thirdperson_righthand']
        if ctx == 'firstperson_lefthand' and 'firstperson_righthand' in d: return d['firstperson_righthand']
    return None


def fold(model, Re, o):
    """Every display transform with the element turn Re (about o, in pixels) done first."""
    out = {}
    oc = (np.array(o) / 16.0) - 0.5
    for ctx in CTX:
        t = display(ctx, model) or {}
        T = np.array(t.get('translation', [0, 0, 0]), float) / 16.0
        r = t.get('rotation', [0, 0, 0]); s = t.get('scale', [1, 1, 1])
        assert s[0] == s[1] == s[2], 'only an even scale folds exactly'
        R = rx(math.radians(r[0])) @ ry(math.radians(r[1])) @ rz(math.radians(r[2]))
        if ctx in LEFT: T, R = M @ T, M @ R @ M       # what the game really does in the left hand
        T2 = T + s[0] * R @ (np.eye(3) - Re) @ oc
        R2 = R @ Re
        if ctx in LEFT: T2, R2 = M @ T2, M @ R2 @ M
        tr = [round(float(v) * 16, 4) for v in T2]
        assert all(abs(v) <= 80 for v in tr)
        out[ctx] = {'rotation': euler_xyz(R2), 'translation': tr, 'scale': s}
    return out


def split(rot):
    """(rotation 1.21.5 accepts or None, the turn left over as (key, matrix, origin) or None)."""
    o = rot['origin']
    assert 'axis' in rot, 'models_12110.py runs first'
    assert not rot.get('rescale'), 'rescale does not split'
    a, ang = rot['axis'], rot['angle']
    if ang % 22.5 == 0:
        return rot, None
    return None, ((a, ang, tuple(o)), AX[a](math.radians(ang)), o)


def special_bases():
    out = set()
    def walk(n):
        if isinstance(n, dict):
            if n.get('type') == 'minecraft:special': out.add(n['base'])
            if n.get('type') == 'minecraft:model': drawn.add(n['model'])
            for v in n.values(): walk(v)
        elif isinstance(n, list):
            for v in n: walk(v)
    drawn = set()
    for f in (res / 'items').glob('*.json'): walk(json.load(open(f)))
    return out - drawn


bases = special_bases()
parts = {}   # model id -> [part model ids]
for f in sorted((res / 'models/item').glob('*.json')):
    model = json.load(open(f))
    els = model.get('elements')
    if not els or not any('rotation' in e for e in els): continue
    mid = 'mobcraft:item/' + f.stem
    groups = {}
    kept = []
    changed = False
    for e in els:
        if 'rotation' not in e:
            kept.append(e); continue
        old = e['rotation']
        keep, moved = split(old)
        e = dict(e)
        if keep: e['rotation'] = keep
        else: e.pop('rotation')
        if keep != old: changed = True
        if moved and mid in bases:
            e['rotation'] = dict(old, angle=round(old['angle'] / 22.5) * 22.5)
            kept.append(e); continue
        if moved and mid not in bases:
            key, Re, o = moved
            groups.setdefault(key, (Re, o, []))[2].append(e)
        else:
            kept.append(e)
    if not changed: continue
    model['elements'] = kept
    names = []
    for i, (key, (Re, o, ge)) in enumerate(groups.items()):
        pm = {k: v for k, v in model.items() if k not in ('elements', 'display')}
        pm['display'] = fold(model, Re, o)
        pm['elements'] = ge
        name = f'{f.stem}_part{i + 1}'
        (f.parent / f'{name}.json').write_text(json.dumps(pm, indent=1))
        names.append('mobcraft:item/' + name)
    f.write_text(json.dumps(model, indent=1))
    if names: parts[mid] = names
    print(f'{f.stem}: {len(kept)} elements stay, {len(groups)} parts')


def compose(n):
    if isinstance(n, list): return [compose(v) for v in n]
    if not isinstance(n, dict): return n
    n = {k: compose(v) for k, v in n.items()}
    if n.get('type') == 'minecraft:model' and n.get('model') in parts:
        return {'type': 'minecraft:composite', 'models': [n] + [dict(n, model=p) for p in parts[n['model']]]}
    return n


for f in sorted((res / 'items').glob('*.json')):
    d = json.load(open(f))
    d2 = compose(d)
    if d2 != d:
        f.write_text(json.dumps(d2, indent=1))
        print('items/' + f.name, 'now draws the parts')
