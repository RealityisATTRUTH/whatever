import sys
v, mv = sys.argv[1], sys.argv[2]
p = 'src/main/resources/assets/mobcraft/update_log.txt'
lines = open(p).read().split('\n')
i = next(n for n, l in enumerate(lines) if l.startswith('# '))
lines[i:i] = [f'# Season 3 Beta {mv} for Minecraft {v} on NeoForge',
              '- MobCraft now runs on NeoForge as well as Fabric. It needs NeoForge, not Fabric API',
              '- Everything from Beta 5.15 is here: the same mobs, abilities, items, RTS mode and commands']
open(p, 'w').write('\n'.join(lines))
