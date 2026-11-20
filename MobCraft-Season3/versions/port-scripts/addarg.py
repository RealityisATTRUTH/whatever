"""Rename a call and add an argument at the end: addarg.py ROOT OLD NEW EXTRA (balanced parentheses, strings skipped)."""
import sys, pathlib
root, old, new, extra = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
def fix(s):
    out = []; i = 0; key = '.' + old + '('
    while True:
        j = s.find(key, i)
        if j < 0: out.append(s[i:]); return ''.join(out)
        out.append(s[i:j]); out.append('.' + new + '(')
        k = j + len(key); depth = 1; q = None
        while depth:
            c = s[k]
            if q:
                if c == '\\': k += 1
                elif c == q: q = None
            elif c in '"\'': q = c
            elif c == '(': depth += 1
            elif c == ')': depth -= 1
            k += 1
        out.append(s[j + len(key):k - 1] + ', ' + extra + ')'); i = k
n = 0
for f in pathlib.Path(root).rglob('*.java'):
    s = f.read_text()
    if '.' + old + '(' in s: f.write_text(fix(s)); n += 1
print(old, '->', new, 'files', n)
