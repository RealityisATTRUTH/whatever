# Builds the 1.21.6 research copy: the 1.21.7 copy, then 1.21.6 edits.
set -e
S=/tmp/claude-0/-home-claude/ba4f4dfa-e2f3-5a6d-bd72-b2683d6d5eab/scratchpad; P=$S/port121; R=$S/src/research-1216
bash $P/make_1218.sh 1.21.7 > /dev/null
mkdir -p $R; cd $R; find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name run -exec rm -rf {} +
(cd $S/src/research-1217; tar cf - --exclude=./build --exclude=./.gradle --exclude=./run .) | tar xf -
sed -i "s/^minecraft_version=.*/minecraft_version=1.21.6/; s/^fabric_version=.*/fabric_version=0.128.2+1.21.6/; s/^mod_version=.*/mod_version=5.15.0-beta+1.21.6/; s/^jar_version=.*/jar_version=beta-5.15-mc1.21.6/" gradle.properties
sed -i 's/"minecraft": "1.21.7"/"minecraft": "1.21.6"/' src/main/resources/fabric.mod.json
[ -f $P/manual_1216.py ] && python3 $P/manual_1216.py src/main/java
echo made $R
