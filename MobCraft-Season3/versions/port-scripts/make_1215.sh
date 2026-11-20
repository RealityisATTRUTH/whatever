# Builds the 1.21.5 research copy: the 1.21.6 copy, then 1.21.5 edits.
set -e
S=/tmp/claude-0/-home-claude/ba4f4dfa-e2f3-5a6d-bd72-b2683d6d5eab/scratchpad; P=$S/port121; R=$S/src/research-1215
bash $P/make_1216.sh > /dev/null
mkdir -p $R; cd $R; find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name run -exec rm -rf {} +
(cd $S/src/research-1216; tar cf - --exclude=./build --exclude=./.gradle --exclude=./run .) | tar xf -
sed -i "s/^minecraft_version=.*/minecraft_version=1.21.5/; s/^fabric_version=.*/fabric_version=0.128.2+1.21.5/; s/^mod_version=.*/mod_version=5.15.0-beta+1.21.5/; s/^jar_version=.*/jar_version=beta-5.15-mc1.21.5/" gradle.properties
sed -i 's/"minecraft": "1.21.6"/"minecraft": "1.21.5"/' src/main/resources/fabric.mod.json
[ -f $P/files_1215 ] || true
[ -d $P/files_1215 ] && (cd $P/files_1215 && tar cf - .) | (cd $R/src/main/java && tar xf -)
[ -f $P/manual_1215.py ] && python3 $P/manual_1215.py src/main/java
python3 $P/models_1215.py src/main/resources $P/block_12110.json
echo made $R
# Many server calls take a ServerLevel that 1.21.6 hands out from level(); cast them where javac says so.
cd $R; export JAVA_HOME=$(cat $S/javahome)
for i in 1 2 3; do ./gradlew -q compileJava > /tmp/c1215.txt 2>&1 || true; python3 $P/levelfix.py /tmp/c1215.txt; done
