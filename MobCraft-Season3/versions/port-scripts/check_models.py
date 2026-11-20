"""Checks the rewritten 1.21.10 models put every corner where 1.21.11 puts it, in every display context."""
import json, math, sys, pathlib, itertools
import numpy as np
sys.argv = [sys.argv[0], 'x', str(pathlib.Path(__file__).parent / 'block_12110.json')] + sys.argv[1:]
old_root, new_root = pathlib.Path(sys.argv[3]), pathlib.Path(sys.argv[4])
exec(open(pathlib.Path(__file__).parent / 'models_12110.py').read().split('def special_bases')[0].replace("res = pathlib.Path(sys.argv[1]) / 'assets/mobcraft'", 'res=None'))

def elrot(rot):
    if not rot: return np.eye(3), np.zeros(3)
    o = np.array(rot['origin']) / 16
    if 'axis' in rot: return AX[rot['axis']](math.radians(rot['angle'])), o
    return rz(math.radians(rot.get('z', 0))) @ ry(math.radians(rot.get('y', 0))) @ rx(math.radians(rot.get('x', 0))), o

def disp(ctx, model):
    t = display(ctx, model)
    if t is None: return lambda p: p - 0.5
    T = np.array(t.get('translation', [0, 0, 0])) / 16; r = t.get('rotation', [0, 0, 0]); s = np.array(t.get('scale', [1, 1, 1]))
    R = rx(math.radians(r[0])) @ ry(math.radians(r[1])) @ rz(math.radians(r[2]))
    if ctx in LEFT: T, R = M @ T, M @ R @ M
    return lambda p: T + R @ (s * (p - 0.5))

def corners(model):
    out = []
    for e in model.get('elements', []):
        R, o = elrot(e.get('rotation'))
        a, b = np.array(e['from']) / 16, np.array(e['to']) / 16
        out.append([R @ (np.array(c) - o) + o for c in itertools.product(*zip(a, b))])
    return out

def world(mid, root, items):
    """Every drawn corner of the model and its parts, per context."""
    names = [mid]
    for f in (root / 'items').glob('*.json'):
        s = f.read_text()
        names += sorted(set(n for n in __import__('re').findall(r'mobcraft:item/[a-z0-9_]+', s) if n.startswith(mid + '_part')))
    out = {}
    for ctx in CTX:
        pts = []
        for n in names:
            m = json.load(open(root / 'models/item' / (n.split('/')[1] + '.json')))
            d = disp(ctx, m)
            pts += [d(c) for el in corners(m) for c in el]
        out[ctx] = np.array(sorted(map(tuple, np.round(pts, 4))))
    return out

for f in sorted((new_root / 'models/item').glob('*.json')):
    if '_part' in f.stem: continue
    old = json.load(open(old_root / 'models/item' / f.name))
    if old == json.load(open(f)): continue
    mid = 'mobcraft:item/' + f.stem
    a, b = world(mid, old_root, None), world(mid, new_root, None)
    worst = max(max(float(np.linalg.norm(b[c] - x, axis=1).min()) for x in a[c]) if a[c].shape == b[c].shape else 99 for c in CTX)
    bad = [e.get('rotation') for e in json.load(open(f)).get('elements', []) for p in [f] if e.get('rotation') and ('axis' not in e['rotation'] or abs(e['rotation']['angle']) > 45)]
    for p in (new_root / 'models/item').glob(f.stem + '_part*.json'):
        bad += [e['rotation'] for e in json.load(open(p))['elements'] if e.get('rotation') and ('axis' not in e['rotation'] or abs(e['rotation']['angle']) > 45)]
    print(f'{f.stem:24s} worst corner miss {worst:.5f} blocks; rotations 1.21.10 refuses: {len(bad)}')
