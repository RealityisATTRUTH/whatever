# levelfix.py ERRS : on each line where javac says Level should be ServerLevel (or Level lacks a server method),
# cast the .level() call there to ServerLevel.
import re, sys, collections
t = open(sys.argv[1]).read().split('\n')
todo = collections.defaultdict(set)
for i, l in enumerate(t):
    m = re.match(r'\s*(/\S+\.java):(\d+): error: (.*)', l)
    if not m: continue
    f, n, msg = m.group(1), int(m.group(2)), m.group(3)
    nxt = '\n'.join(t[i+1:i+5])
    if 'Level cannot be converted to ServerLevel' in msg or 'UUID cannot be converted to int' in msg or ('cannot find symbol' in msg and 'location: class Level' in nxt) \
       or ('cannot find symbol' in msg and 'location: variable level of type Level' in nxt):
        todo[f].add(n)
pat = re.compile(r'((?:\b[A-Za-z_][\w]*(?:\(\))?\.)*)level\(\)')
for f, lines in todo.items():
    src = open(f).read().split('\n'); c = 0
    for n in lines:
        s = src[n-1]
        if 'ServerLevel) ' in s and '.level())' in s: continue
        new = re.sub(r'(?<![\w.])((?:[A-Za-z_]\w*(?:\(\))?\.)+)level\(\)', r'((net.minecraft.server.level.ServerLevel) \1level())', s)
        if new != s: src[n-1] = new; c += 1
        else: print('NOFIX', f.split('/mobcraft/')[-1], n, s.strip())
    open(f, 'w').write('\n'.join(src))
print('files', len(todo))
