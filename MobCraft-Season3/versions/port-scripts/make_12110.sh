# Builds the 1.21.10 research copy: the 1.21.11 copy, then the 1.21.10 renames and hand edits. Pass the version (1.21.10 or 1.21.9).
set -e
V=${1:-1.21.10}; T=$(echo $V | tr -d .)
S=/tmp/claude-0/-home-claude/ba4f4dfa-e2f3-5a6d-bd72-b2683d6d5eab/scratchpad; P=$S/port121; R=$S/src/research-$T
bash $P/make_12111.sh > /dev/null
mkdir -p $R; cd $R; find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name run -exec rm -rf {} +
(cd $S/src/research-12111; tar cf - --exclude=./build --exclude=./.gradle --exclude=./run .) | tar xf -
FAPI=$( [ $V = 1.21.9 ] && echo 0.134.1+1.21.9 || echo 0.138.4+1.21.10 )
sed -i "s/^minecraft_version=.*/minecraft_version=$V/; s/^fabric_version=.*/fabric_version=$FAPI/; s/^mod_version=.*/mod_version=5.15.0-beta+$V/; s/^jar_version=.*/jar_version=beta-5.15-mc$V/" gradle.properties
sed -i "s/\"minecraft\": \"1.21.11\"/\"minecraft\": \"$V\"/" src/main/resources/fabric.mod.json
python3 $P/rename_12110.py src/main/java
python3 $P/manual_12110.py src/main/java
python3 $P/models_12110.py src/main/resources $P/block_12110.json
python3 - $V <<'PY'
import sys
V=sys.argv[1]
f='src/main/resources/assets/mobcraft/update_log.txt'; s=open(f).read()
a='# Season 3 Beta 5.11 for Minecraft 1.21.11\n'; assert a in s
b=s.index(a); e=s.index('\n# ', b+1)+1
s=s[:b]+f'''# Season 3 Beta 5.11 for Minecraft {V}
- The same mod as Beta 5.11, made to run on Minecraft {V} with Fabric API for {V}
- Spears and the Parched came in 1.21.11, so on {V} there are none for MobCraft to check for
'''+s[e:]
for n in ['5.15','5.14','5.13','5.12']: s=s.replace(f'# Season 3 Beta {n} for Minecraft 1.21.11\n',f'# Season 3 Beta {n} for Minecraft {V}\n',1)
open(f,'w').write(s)
PY
[ $V = 1.21.9 ] && python3 $P/manual_1219.py src/main/java
echo made $R
