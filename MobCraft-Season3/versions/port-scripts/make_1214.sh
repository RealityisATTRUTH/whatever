# Builds the 1.21.4 research copy: the 1.21.5 copy, then 1.21.4 edits.
set -e
S=/tmp/claude-0/-home-claude/ba4f4dfa-e2f3-5a6d-bd72-b2683d6d5eab/scratchpad; P=$S/port121; R=$S/src/research-1214
bash $P/make_1215.sh > /dev/null
mkdir -p $R; cd $R; find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name run -exec rm -rf {} +
(cd $S/src/research-1215; tar cf - --exclude=./build --exclude=./.gradle --exclude=./run .) | tar xf -
sed -i "s/^minecraft_version=.*/minecraft_version=1.21.4/; s/^fabric_version=.*/fabric_version=0.119.4+1.21.4/; s/^mod_version=.*/mod_version=5.15.0-beta+1.21.4/; s/^jar_version=.*/jar_version=beta-5.15-mc1.21.4/" gradle.properties
sed -i 's/"minecraft": "1.21.5"/"minecraft": "1.21.4"/' src/main/resources/fabric.mod.json
[ -d $P/files_1214 ] && (cd $P/files_1214 && tar cf - .) | (cd $R/src/main/java && tar xf -)
[ -f $P/manual_1214.py ] && python3 $P/manual_1214.py src/main/java
echo made $R
# Level.getEntity(UUID) and other server calls are ServerLevel-only here; cast them where javac says so.
cd $R; export JAVA_HOME=$(cat $S/javahome)
for i in 1 2; do ./gradlew -q compileJava > /tmp/c1214.txt 2>&1 || true; python3 $P/levelfix.py /tmp/c1214.txt; done
