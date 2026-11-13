#!/bin/bash
# Runs the logic tests, which need no Minecraft. Needs Java 21 or newer.
cd "$(dirname "$0")"
out=$(mktemp -d)
javac -d "$out" src/core/java/com/shumsic/autodrinkominousbottles/*.java src/test/java/com/shumsic/autodrinkominousbottles/*.java && java -cp "$out" com.shumsic.autodrinkominousbottles.AutoDrinkTest
