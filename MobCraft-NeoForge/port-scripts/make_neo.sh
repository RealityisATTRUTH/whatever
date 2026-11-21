#!/bin/bash
# Makes a NeoForge copy of a Fabric MobCraft tree.
# Usage: make_neo.sh <mc version> <fabric source dir name> <neoforge version>
set -e
S=/tmp/claude-0/-home-claude/ba4f4dfa-e2f3-5a6d-bd72-b2683d6d5eab/scratchpad
V=$1; SRC=$S/src/$2; NEO=$3; BASE=$S/portneo/base; MV=${MV:-5.16}
OUT=$S/src/mobcraft-neo${V//./}
mkdir -p $OUT; cd $OUT
find . -mindepth 1 -maxdepth 1 ! -name .gradle ! -name run ! -name build -exec rm -rf {} +
(cd $SRC; tar cf - --exclude=./build --exclude=./.gradle --exclude='./run*' .) | tar xf -
rm -f src/main/resources/fabric.mod.json
cp $BASE/settings.gradle $BASE/interfaces.json $BASE/build.gradle .
NEXT=$(python3 -c "v='$V'.split('.'); print('.'.join(v[:2]+[str(int(v[2])+1)]) if len(v)==3 else v[0]+'.'+str(int(v[1])+1))")
cat > gradle.properties <<P
# MobCraft Season 3, NeoForge $V test build
org.gradle.jvmargs=-Xmx3G
mod_id=mobcraft
mod_version=$MV.0-beta+neoforge-$V
jar_version=beta-$MV-neoforge-$V
maven_group=com.reality.mobcraft
archives_base_name=mobcraft-season3
minecraft_version=$V
neo_version=$NEO
P
mkdir -p src/main/resources/META-INF src/main/java/com/reality/mobcraft/neo
cp $BASE/META-INF/* src/main/resources/META-INF/
sed -i "s/versionRange = \"\[1.21.4,1.21.5)\"/versionRange = \"[$V,$NEXT)\"/; s/versionRange = \"\[21.4.0,)\"/versionRange = \"[${NEO%.*}.0,)\"/" src/main/resources/META-INF/neoforge.mods.toml
cp $BASE/mobcraft.neo.mixins.json src/main/resources/
mkdir -p src/main/resources/data/mobcraft/neoforge
mkdir -p src/main/resources/data/mobcraft/neoforge/biome_modifier; cp $BASE/biome_modifier/* src/main/resources/data/mobcraft/neoforge/biome_modifier/
(cd $BASE/java; tar cf - .) | (cd src/main/java/com/reality/mobcraft/neo; tar xf -)
for o in $OVER; do
  (cd $S/portneo/over$o/java; tar cf - .) | (cd src/main/java/com/reality/mobcraft/neo; tar xf -)
  [ -f $S/portneo/over$o/client-mixins.txt ] && for mx in $(cat $S/portneo/over$o/client-mixins.txt); do python3 -c "
import json; p='src/main/resources/mobcraft.neo.mixins.json'; d=json.load(open(p))
d['client'].append('$mx') if '$mx' not in d['client'] else None; json.dump(d,open(p,'w'),indent=2)"; done
  [ -f $S/portneo/over$o/META-INF/at-extra.cfg ] && cat $S/portneo/over$o/META-INF/at-extra.cfg >> src/main/resources/META-INF/accesstransformer.cfg
done
if [[ "$V" == 26.* ]]; then
  python3 $S/portneo/to26.py
  (cd $S/portneo/over261/java; tar cf - .) | (cd src/main/java/com/reality/mobcraft/neo; tar xf -)
  rm -f src/main/java/com/reality/mobcraft/neo/mixin/WorldRenderEventsMixin.java
  if [ "$V" != "26.1.2" ]; then python3 $S/portneo/to262.py; (cd $S/portneo/over262/java; tar cf - .) | (cd src/main/java/com/reality/mobcraft/neo; tar xf -); fi
  [ "$V" = "26.3" ] && { python3 $S/portneo/to263.py; (cd $S/portneo/over263/java; tar cf - .) | (cd src/main/java/com/reality/mobcraft/neo; tar xf -); }
  python3 -c "
import json; p='src/main/resources/mobcraft.neo.mixins.json'; d=json.load(open(p))
d['client']=[m for m in d['client'] if m!='WorldRenderEventsMixin']; json.dump(d,open(p,'w'),indent=2)"
fi
grep -rl "net\.fabricmc\." src/main/java/com/reality/mobcraft --include=*.java | grep -v "/neo/" | xargs -r sed -i 's/net\.fabricmc\./com.reality.mobcraft.neo.fabric./g'
# Fabric-only bits
for f in HelmetUpgradeLayer MobLookLayer SkullVisorLayer; do
  [ -f src/main/java/com/reality/mobcraft/client/$f.java ] && ! grep -q "ArmorRenderer\." src/main/java/com/reality/mobcraft/client/$f.java && sed -i '/import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.ArmorRenderer;/d' src/main/java/com/reality/mobcraft/client/$f.java
done
python3 - <<'PY'
import json, os
p='src/main/resources/mobcraft.client.mixins.json'
d=json.load(open(p))
if 'KeyMappingShareMixin' in d.get('client',[]): d['client'].remove('KeyMappingShareMixin')
json.dump(d,open(p,'w'),indent=2)
PY
rm -f src/main/java/com/reality/mobcraft/mixin/client/KeyMappingShareMixin.java
M=src/main/java/com/reality/mobcraft/mixin
sed -i 's/@com.llamalad7.mixinextras.sugar.Local(index = [0-9]*) float damage/@com.llamalad7.mixinextras.sugar.Local(ordinal = 3) float damage/; s/@com.llamalad7.mixinextras.sugar.Local(index = [0-9]*) DamageSource source/@com.llamalad7.mixinextras.sugar.Local DamageSource source/' $M/GloamitePlayerMixin.java
echo "made $OUT"; grep -rn "method_[0-9]\+\|field_[0-9]\+" src/main/java | sed 's/:.*\(method_[0-9]*\|field_[0-9]*\).*/: \1/' || true
# Intermediary lambda/field names in mixins, as Mojang names (check with javap if a version moves them).
sed -i 's/method = "method_17410"/method = "lambda$clickMenuButton$1"/' $M/EnchantDuskveinMixin.java
[ -f $M/client/PurpleTemplateMixin.java ] && sed -i 's/method = "method_52851"/method = "lambda$create$0"/' $M/client/PurpleTemplateMixin.java
[ -f $M/client/ItemLayerGlowMixin.java ] && sed -i 's/field_55345/this$0/g' $M/client/ItemLayerGlowMixin.java
true
# NeoForge moves AnvilMenu.createResult's body into createResultInternal.
[ "$V" != "1.21.4" ] && for f in $(grep -ln "@Mixin(AnvilMenu.class)" $M/*.java); do sed -i 's/method = "createResult"/method = "createResultInternal"/g' $f; done
# Update log heading for the NeoForge release.
python3 $S/portneo/update_log.py "$V" "$MV"
echo "version $MV"
# RtsSelfRenderMixin (expression form) lets the player see himself in RTS view. NeoForge's LevelRenderer
# already draws the local player whenever it isn't the camera entity, and NeoForge 21.6 bundles MixinExtras 0.4,
# which has no expressions, so the mixin is dropped there.
R=$M/client/RtsSelfRenderMixin.java
if [ -f $R ] && grep -q "MIXINEXTRAS:EXPRESSION" $R; then
  rm $R
  python3 -c "
import json; p='src/main/resources/mobcraft.client.mixins.json'; d=json.load(open(p))
d['client']=[m for m in d['client'] if m!='RtsSelfRenderMixin']; json.dump(d,open(p,'w'),indent=2)"
fi
true
# LevelRenderer's main-pass lambda (1.21.9+ WorldRenderEventsMixin).
[ -f $M/client/WorldRenderEventsMixin.java ] && sed -i 's/method = "method_62214"/method = "lambda$addMainPass$1"/' $M/client/WorldRenderEventsMixin.java
echo "left in intermediary:"; grep -rn "method_[0-9]\+\|field_[0-9]\+" src/main/java | sed 's/:.*\(method_[0-9]*\|field_[0-9]*\).*/: \1/' || true
true
# 1.21.11 renamed ResourceLocation to Identifier and moved RenderType.
if [ "$V" = "1.21.11" ] || [[ "$V" == 26.* ]]; then
  N=src/main/java/com/reality/mobcraft/neo
  grep -rl "ResourceLocation" $N | xargs -r sed -i 's/net\.minecraft\.resources\.ResourceLocation/net.minecraft.resources.Identifier/g; s/\bResourceLocation\b/Identifier/g'
  grep -rl "net.minecraft.client.renderer.RenderType;" $N | xargs -r sed -i 's/import net\.minecraft\.client\.renderer\.RenderType;/import net.minecraft.client.renderer.rendertype.RenderType;/'
fi
true
# 26.x: Java 25, and one jar covers every patch of the version (26.1, 26.1.1, 26.1.2).
if [[ "$V" == 26.* ]]; then
  sed -i 's/it.options.release = 21/it.options.release = 25/' build.gradle
  sed -i 's/"compatibilityLevel": "JAVA_21"/"compatibilityLevel": "JAVA_25"/' src/main/resources/*.mixins.json
  MINOR=$(echo $V | cut -d. -f1-2); NEXTM=$(echo $MINOR | awk -F. '{print $1"."$2+1}')
  sed -i "s/versionRange = \"\[$V,[^\"]*\"/versionRange = \"[$MINOR,$NEXTM)\"/; s/versionRange = \"\[${NEO%%.*}\.[0-9]*\.[0-9]*\.0,)\"/versionRange = \"[$MINOR.0,)\"/" src/main/resources/META-INF/neoforge.mods.toml
fi
true
