#!/usr/bin/env bash
# Prints the real (Mojang-named) method signatures of a few Minecraft classes, so mixins can be
# written against what 1.21.11 actually contains instead of guesses.
# Usage (after `./gradlew build` has run once):  bash tools/dump-signatures.sh
# The output is copied to the clipboard on macOS (paste it into the chat) and saved to mc-signatures.txt.
set -uo pipefail
cd "$(dirname "$0")/.."

JARS=$(find "$HOME/.gradle/caches/fabric-loom" ./.gradle -name '*.jar' ! -name '*sources*' 2>/dev/null | grep -i minecraft || true)
if [ -z "$JARS" ]; then echo "No Minecraft jar found in the Loom cache. Run ./gradlew build first."; exit 1; fi

# class | grep pattern of interesting members
SPECS=(
"net.minecraft.client.MouseHandler|turnPlayer|onMove|accumulated|handle|onPress"
"net.minecraft.client.Camera|setup|setRotation|setPosition|Rot|rotation|detached|alignWith|getEntity|yRot|xRot"
"net.minecraft.client.renderer.ScreenEffectRenderer|."
"net.minecraft.client.gui.Gui|ayer|Overlay|overlay|umpkin|texture|Texture|ignette|Fire|render"
"net.minecraft.world.entity.Entity|turn|setYRot|setXRot|getViewYRot|getViewXRot|getYRot|getXRot"
"net.minecraft.client.renderer.entity.LivingEntityRenderer|verlay|hurt|Hurt|isBodyVisible|getModelAndTexture|render|submit"
"net.minecraft.client.renderer.ItemInHandRenderer|wing|quip|ttack|Block|Bow|use|renderArmWithItem|applyItem|tick"
"net.minecraft.client.renderer.GameRenderer|Fov|fov|hurt|Hurt|bob|ilt|renderLevel|render"
"net.minecraft.client.renderer.entity.state.LivingEntityRenderState|."
"net.minecraft.client.renderer.entity.state.EntityRenderState|."
)

OUT=mc-signatures.txt
: > "$OUT"
for spec in "${SPECS[@]}"; do
  cls="${spec%%|*}"; pat="${spec#*|}"
  found=0
  while read -r jar; do
    [ -z "$jar" ] && continue
    if res=$(javap -p -cp "$jar" "$cls" 2>/dev/null); then
      { echo "== $cls"; echo "$res" | grep -E "class |interface |$pat" | head -45; } >> "$OUT"
      found=1; break
    fi
  done <<< "$JARS"
  [ $found -eq 0 ] && echo "== $cls  (NOT FOUND)" >> "$OUT"
done
wc -l "$OUT"
if command -v pbcopy >/dev/null; then pbcopy < "$OUT"; echo "Copied to clipboard. Paste it into the chat."; else cat "$OUT"; fi
