#!/usr/bin/env bash
# Second round: classes needed for hit colour and the old-style item animations.
# Usage (after ./gradlew build has run once):  bash tools/dump-signatures2.sh
set -uo pipefail
cd "$(dirname "$0")/.."
JARS=$(find "$HOME/.gradle/caches/fabric-loom" ./.gradle -name '*.jar' ! -name '*sources*' 2>/dev/null | grep -i minecraft || true)
if [ -z "$JARS" ]; then echo "No Minecraft jar found in the Loom cache. Run ./gradlew build first."; exit 1; fi
SPECS=(
"net.minecraft.client.renderer.texture.OverlayTexture|."
"net.minecraft.client.renderer.ItemInHandRenderer|."
"net.minecraft.world.item.ItemUseAnimation|."
"net.minecraft.client.Options|ameraType|CameraType|fov|Fov"
"net.minecraft.client.CameraType|."
"net.minecraft.client.MouseHandler|croll|ensitiv"
)
OUT=mc-signatures2.txt
: > "$OUT"
for spec in "${SPECS[@]}"; do
  cls="${spec%%|*}"; pat="${spec#*|}"
  found=0
  while read -r jar; do
    [ -z "$jar" ] && continue
    if res=$(javap -p -cp "$jar" "$cls" 2>/dev/null); then
      { echo "== $cls"; echo "$res" | grep -E "class |interface |enum |$pat" | head -70; } >> "$OUT"
      found=1; break
    fi
  done <<< "$JARS"
  [ $found -eq 0 ] && echo "== $cls  (NOT FOUND)" >> "$OUT"
done
wc -l "$OUT"
if command -v pbcopy >/dev/null; then pbcopy < "$OUT"; echo "Copied to clipboard. Paste it into the chat."; else cat "$OUT"; fi
