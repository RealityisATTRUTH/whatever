"""Finds Minecraft classes the source imports or names in full that don't exist in a target jar, and where a class of
the same simple name lives there. relocate.py SRC_JAVA EXTRACTED_JAR_DIR"""
import re, sys, pathlib, os, collections
src = pathlib.Path(sys.argv[1]); jar = pathlib.Path(sys.argv[2])
have = set(); by_name = collections.defaultdict(list)
for p in jar.rglob('*.class'):
    rel = str(p.relative_to(jar))[:-6].replace('/', '.')
    have.add(rel.replace('$', '.')); by_name[rel.split('.')[-1].split('$')[-1]].append(rel.replace('$', '.'))
refs = collections.Counter()
for f in src.rglob('*.java'):
    for m in re.finditer(r'\b(net\.minecraft\.[a-z0-9_.]+\.[A-Z][A-Za-z0-9_]*(?:\.[A-Z][A-Za-z0-9_]*)*)', f.read_text()):
        refs[m.group(1)] += 1
for r in sorted(refs):
    if r in have: continue
    # a nested reference like a.b.C.D: fine if a.b.C exists
    parts = r.split('.'); outer = '.'.join(p for p in parts if not p[:1].isupper()) + '.' + next(p for p in parts if p[:1].isupper())
    simple = next(p for p in parts if p[:1].isupper())
    if outer in have: 
        if r not in have: print('NESTED?', r)
        continue
    print('MOVED', r, '->', by_name.get(simple, ['?']))
