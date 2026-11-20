"""Beta 5.13: a helmet's head piece (warden tendrils, bat ears, rabbit ears) and the mob's own head pieces never show
together. Usage: headpieces.py <project dir> <version label or 26.3>"""
import sys, re, pathlib
d = pathlib.Path(sys.argv[1]); V = sys.argv[2]
f = d / 'src/main/java/com/reality/mobcraft/client/MobLookLayer.java'
s = f.read_text()
def sub(a, b, count=1):
    global s
    assert s.count(a) == count, (a[:70], s.count(a)); s = s.replace(a, b)

sub("""                boolean right = ArmorUpgrades.TENDRIL_RIGHT.equals(helmet) || ArmorUpgrades.TENDRIL_BOTH.equals(helmet);
""", """                boolean right = ArmorUpgrades.TENDRIL_RIGHT.equals(helmet) || ArmorUpgrades.TENDRIL_BOTH.equals(helmet);
                // Bat ears or rabbit ears on the helmet: the warden's own tendrils step aside.
                if (helmetHeadPiece(helmet) && !tendrils(helmet)) left = right = true;
""")
sub("""                boolean right = ArmorUpgrades.EAR_RIGHT.equals(helmet) || ArmorUpgrades.EAR_BOTH.equals(helmet);
""", """                boolean right = ArmorUpgrades.EAR_RIGHT.equals(helmet) || ArmorUpgrades.EAR_BOTH.equals(helmet);
                // Tendrils or rabbit ears on the helmet: the bat's own ears step aside.
                if (helmetHeadPiece(helmet) && !batEars(helmet)) left = right = true;
""")
m = re.search(r'\n(\s*)draw\(rabbitEars, ', s); assert m
s = s[:m.start()] + '\n' + m.group(1) + '// A head piece on the helmet: the rabbit\'s own ears step aside.\n' + m.group(1) \
    + 'if (!helmetHeadPiece(state.getData(HelmetUpgradeLayer.UPGRADE))) draw(rabbitEars, ' + s[m.end():]
for name, model in [('piglin', 'piglinEars'), ('axolotl', 'axolotlGills')]:
    m = re.search(r'case "' + name + r'" -> (draw\(' + model + r', .*?state\);)', s, re.S); assert m, name
    s = s[:m.start()] + f'case "{name}" -> {{\n                if (!helmetHeadPiece(state.getData(HelmetUpgradeLayer.UPGRADE))) {m.group(1)}\n            }}' + s[m.end():]
i = s.rstrip().rindex('}')
s = s[:i] + '''
    /** Warden tendrils, bat ears or rabbit ears on the helmet: the mob's own ears, tendrils or gills don't show with them. */
    static boolean helmetHeadPiece(String helmet) {
        return tendrils(helmet) || batEars(helmet) || ArmorUpgrades.LUCKY_PAW.equals(helmet);
    }

    private static boolean tendrils(String helmet) {
        return ArmorUpgrades.TENDRIL_LEFT.equals(helmet) || ArmorUpgrades.TENDRIL_RIGHT.equals(helmet) || ArmorUpgrades.TENDRIL_BOTH.equals(helmet);
    }

    private static boolean batEars(String helmet) {
        return ArmorUpgrades.EAR_LEFT.equals(helmet) || ArmorUpgrades.EAR_RIGHT.equals(helmet) || ArmorUpgrades.EAR_BOTH.equals(helmet);
    }
}
'''
f.write_text(s)

# version 5.13 and its update-log heading
p = d / 'gradle.properties'; g = p.read_text()
g2 = g.replace('5.12.0-beta', '5.13.0-beta').replace('beta-5.12', 'beta-5.13'); assert g2 != g; p.write_text(g2)
log = d / 'src/main/resources/assets/mobcraft/update_log.txt'; t = log.read_text()
old = '# Season 3 Beta 5.12\n' if V == '26.3' else f'# Season 3 Beta 5.12 for Minecraft {V}\n'
new = '# Season 3 Beta 5.13\n' if V == '26.3' else f'# Season 3 Beta 5.13 for Minecraft {V}\n'
assert old in t
t = t.replace(old, new + '''- A helmet's warden tendrils, bat ears or rabbit ears never show together with your mob's own ears, tendrils or gills. The helmet's piece shows and your mob's steps aside, like a Rabbit's ears under a tendril helmet, or a Warden's tendrils under a rabbit-ears helmet
- One tendril or one bat ear on the helmet still pairs with the Warden's or Bat's own on the other side
''' + old, 1)
log.write_text(t)
r = d / 'README.md'; rt = r.read_text(); r.write_text(rt.replace('(beta 5.12)', '(beta 5.13)', 1))
print('5.13 head pieces in', d)
