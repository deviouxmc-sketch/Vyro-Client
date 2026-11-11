#!/usr/bin/env bash
# Builds the VYRO in-game mod and copies it where the launcher embeds it from.
# Needs Java 25 on PATH (Fabric Loom requirement). Run from anywhere: npm run sync-mod
set -euo pipefail
cd "$(dirname "$0")/../vyro-mod"
./gradlew build
JAR=$(ls build/libs/vyro-client-mod-*.jar | grep -v -- '-sources' | head -1)
mkdir -p ../src-tauri/resources
cp "$JAR" ../src-tauri/resources/vyro-client-mod.jar
grep '^mod_version' gradle.properties | cut -d= -f2 | tr -d ' \r' > ../src-tauri/resources/vyro-client-mod.version
echo "Synced $(basename "$JAR") (version $(cat ../src-tauri/resources/vyro-client-mod.version)). Restart 'npm run tauri dev' to embed it."
