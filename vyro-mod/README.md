# VYRO Client mod (Fabric, Minecraft 1.21.11)

The in-game half of VYRO: HUD, keystrokes, crosshair, zoom, toggle sprint, hit sounds and an aura-styled menu.
Targets **Minecraft 1.21.11**, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Mojang mappings, Java 21.
(The build files were copied from FabricMC/fabric-example-mod, branch `1.21.11`.)

## Status: first draft, never compiled against real Minecraft

The code was written against Fabric API 1.21.11 sources, and type-checked against stubs of the APIs it uses, but the
sandbox it was written in cannot download Minecraft, so **expect a few compile errors on first build**. Paste them
back and they get fixed. The pure logic (CPS, combo, compass, clock, colours, drag/resize maths) is tested separately.

## Build

```bash
cd vyro-mod
./gradlew build          # jar ends up in build/libs/vyro-client-mod-0.1.0.jar
./gradlew runClient      # starts a dev client with the mod loaded (good for first testing)
```

To use the jar in VYRO: drop it into the profile's `mods` folder (VYRO → Settings shows the data folder; the profile is
`instances/<profile>/mods`) next to Fabric API. Fabric API is required.

Logic checks that need no Minecraft: `tools/SelfCheck.java`

```bash
javac -d /tmp/sc src/main/java/gg/vyro/core/*.java tools/SelfCheck.java && java -cp /tmp/sc SelfCheck
```

## What is in it

| Feature | Notes |
|---|---|
| HUD elements | FPS, CPS, coordinates, direction, compass strip, ping, clock (real + in-game), speed, biome, memory, server, combo, reach, sprint status, held-item durability, keystrokes, armor status, potion effects, target info |
| HUD editor | Drag to move, corner handle / mouse wheel to resize, snap to centre lines, per-element opacity, text/background colour, panel/shadow/aura toggles, reset, live preview with sample data |
| Presets | Save the current layout + crosshair as a preset, load or delete later (`config/vyro/presets/`) |
| Crosshair | cross, plus, dot, circle, square; size, thickness, gap, colour, outline, opacity, aura colour cycle |
| Zoom | Hold **C** (rebindable). Implemented by temporarily lowering the FOV option, so the minimum is vanilla's 30 and there is no smoothing |
| Toggle sprint / sneak | Sprint: menu toggle or keybind. Sneak: latch keybind |
| Hit sound | ding / arrow / levelup / anvil, volume slider |
| Vanilla HUD tweaks | Scale and offset for hotbar group (hotbar, health, food, XP, ...), scoreboard and boss bar |
| Menu | **Right Shift** (rebindable). Aura glow, animated accent, tabs, scrolling |

Config lives in `.minecraft/config/vyro/config.json` (a broken file is moved aside, never crashes the game).

## Not done yet (needs mixins, which need to be checked against real 1.21.11 code)

Freelook / perspective, hit colour, damage tilt, old/sword-block/bow/rod animations, low fire, no pumpkin overlay,
motion blur, scoreboard/boss-bar *appearance* (only position/scale so far), status-effect icons, item decorations.
Maps (minimap, waypoints, death point, cave map), light-level overlay, chunk borders and structure boxes are better
served by existing mods (Xaero's etc.), which the launcher already installs.

## Fair-play note

Everything here is client-side display or input convenience. Entity radar and similar information advantages were
deliberately left out because many servers ban them.
