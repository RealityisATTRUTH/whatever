# Builds the 1.21.11 research copy from the 26.1.2 source: build setup, renames, hand edits.
set -e
S=/tmp/claude-0/-home-claude/ba4f4dfa-e2f3-5a6d-bd72-b2683d6d5eab/scratchpad; P=$S/port121; R=$S/src/research-12111
mkdir -p $R; cd $R; find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name run -exec rm -rf {} +
(cd $S/src/mobcraft-261x; tar cf - --exclude=./build --exclude=./.gradle --exclude=./run --exclude=./run2 .) | tar xf -
python3 - <<'PY'
import re
s=open('build.gradle').read()
s=s.replace("id 'net.fabricmc.fabric-loom' version \"${loom_version}\"","id 'net.fabricmc.fabric-loom-remap' version \"${loom_version}\"")
s=s.replace('''    minecraft "com.mojang:minecraft:${minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"''','''    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings loom.officialMojangMappings()
    modImplementation "net.fabricmc:fabric-loader:${loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"''')
s=s.replace('it.options.release = 25','it.options.release = 21').replace('VERSION_25','VERSION_21')
s=s.replace("tasks.withType(JavaCompile).configureEach {","tasks.withType(JavaCompile).configureEach {\n    it.options.compilerArgs += ['-Xmaxerrs', '10000']")
open('build.gradle','w').write(s)
p=open('gradle.properties').read()
for k,v in {'minecraft_version':'1.21.11','fabric_version':'0.141.6+1.21.11','mod_version':'5.15.0-beta+1.21.11','jar_version':'beta-5.15-mc1.21.11'}.items():
    p=re.sub(k+r'=.*',k+'='+v,p)
open('gradle.properties','w').write(p)
PY
python3 $P/rename_12111.py src/main/java
python3 $P/rename_12111.py src/main/java 2
python3 $P/addarg.py src/main/java 'mobcraft$overlay' displayClientMessage true
python3 $P/manual_12111.py src/main/java
python3 $P/addarg.py src/main/java 'mobcraft$chat' displayClientMessage false
python3 - <<'PY'
# Players on 1.21.x run Java 21, so the mixin configs must ask for Java 21, not 25.
for f in ['src/main/resources/mobcraft.mixins.json','src/main/resources/mobcraft.client.mixins.json']:
    t=open(f).read(); assert '"JAVA_25"' in t; open(f,'w').write(t.replace('"JAVA_25"','"JAVA_21"'))
f='src/main/resources/assets/mobcraft/update_log.txt'; s=open(f).read()
a='# Season 3 Beta 5.11 for Minecraft 26.1.2\n'; assert a in s
s=s.replace(a,'''# Season 3 Beta 5.11 for Minecraft 1.21.11
- The same mod as Beta 5.11, made to run on Minecraft 1.21.11 with Fabric API for 1.21.11, the version the original Paper plugin was on
'''+a,1)
for n in ['5.15','5.14','5.13','5.12']:
    b=f'# Season 3 Beta {n} for Minecraft 26.1.2\n'; assert b in s
    s=s.replace(b,f'# Season 3 Beta {n} for Minecraft 1.21.11\n',1)
h='# Season 3 Beta 5.13 for Minecraft 1.21.11\n'
s=s.replace(h,h+'- Rabbit ears (the Rabbit\'s own and the helmet\'s) sit on the head instead of floating above it\n',1)
open(f,'w').write(s)
PY
echo made
