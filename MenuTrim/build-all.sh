#!/bin/bash
# Builds each target on its own so one failure doesn't stop the rest. Retries on Maven rate limits.
cd "$(dirname "$0")"
# Needs Java 25. Set GRADLE to your Gradle 9.6+ command if "gradle" isn't on your PATH.
[ -n "$JAVA_HOME" ] && export PATH=$JAVA_HOME/bin:$PATH
G=${GRADLE:-gradle}
mkdir -p logs
for t in "$@"; do
  for i in 1 2 3 4 5; do
    MENUTRIM_TARGETS=$t timeout 900 $G ":$t:build" --console=plain -x test > "logs/$t.log" 2>&1
    r=$?
    if grep -q " 429 " "logs/$t.log" && [ $r -ne 0 ]; then sleep 90; continue; fi
    break
  done
  if [ $r -eq 0 ]; then echo "OK $t" >> logs/results.txt; else echo "FAIL $t" >> logs/results.txt; fi
done
echo DONE >> logs/results.txt
