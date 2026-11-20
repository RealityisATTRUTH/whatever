# Builds the 1.21.8 research copy: the 1.21.9 copy, then 1.21.8 edits. Pass 1.21.8 or 1.21.7.
set -e
V=${1:-1.21.8}; T=$(echo $V | tr -d .)
S=/tmp/claude-0/-home-claude/ba4f4dfa-e2f3-5a6d-bd72-b2683d6d5eab/scratchpad; P=$S/port121; R=$S/src/research-$T
bash $P/make_12110.sh 1.21.9 > /dev/null
mkdir -p $R; cd $R; find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name run -exec rm -rf {} +
(cd $S/src/research-1219; tar cf - --exclude=./build --exclude=./.gradle --exclude=./run .) | tar xf -
FAPI=$( [ $V = 1.21.7 ] && echo 0.129.0+1.21.7 || echo 0.136.1+1.21.8 )
sed -i "s/^minecraft_version=.*/minecraft_version=$V/; s/^fabric_version=.*/fabric_version=$FAPI/; s/^mod_version=.*/mod_version=5.15.0-beta+$V/; s/^jar_version=.*/jar_version=beta-5.15-mc$V/" gradle.properties
sed -i "s/\"minecraft\": \"1.21.9\"/\"minecraft\": \"$V\"/" src/main/resources/fabric.mod.json
[ -f $P/manual_1218.py ] && python3 $P/manual_1218.py src/main/java $V
echo made $R
