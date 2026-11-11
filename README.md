## Screenshots
<img width="2388" height="1522" alt="image" src="https://github.com/user-attachments/assets/ca93e44d-d041-4fb3-b2b2-9738c020f8a1" />
<img width="2302" height="1474" alt="image" src="https://github.com/user-attachments/assets/3cfc3a4f-8196-4dcc-9363-39c2cfb85073" />
<img width="2344" height="1500" alt="image" src="https://github.com/user-attachments/assets/c58a6fa3-2f3a-46f4-aa41-56c5c37b6a18" />


# VYRO CLIENT

A standalone Minecraft: Java Edition launcher (Tauri 2 + Rust + React/TypeScript).
**Status: Stage 3 (first draft) – launcher + Modrinth mod manager + in-game Fabric mod (`vyro-mod/`).** Not an official Minecraft product; not affiliated with Mojang or Microsoft.

## Layout

| Path | What it is |
|---|---|
| `crates/launcher-core` | Pure-Rust engine: version manifests, libraries/natives/assets, Java detection + download, launch arguments, Fabric/Quilt install, Microsoft/Xbox/Minecraft auth, accounts, profiles |
| `src-tauri` | Thin desktop shell: commands, OS keychain storage, events to the UI |
| `src` | React UI: Play, Profiles, Accounts, Settings |
| `public/logo.svg` | VYRO logo |
| `vyro-mod` | Fabric mod for Minecraft 1.21.11: HUD, editor, crosshair, zoom, menu. See `vyro-mod/README.md` – **never compiled against real Minecraft yet** |

Launcher and game stay separated: the launcher only builds a command line and spawns the JVM. In-game features (HUD etc.) will live in a separate Java/Fabric mod (Stage 3).

## What works / how it was verified

| Area | State |
|---|---|
| Modrinth dependency resolver (required deps, incompatibilities, release-over-beta, cycles, pinned versions), mod registry (enable/disable/remove/orphans/conflicts/unsafe filenames/foreign URLs) | **Unit-tested** with a fake Modrinth source |
| Live Modrinth requests (search, versions, update check) and real downloads | **Not tested** – the build sandbox cannot reach api.modrinth.com |
| Rule evaluation, version inheritance (Fabric/Quilt), libraries, legacy + modern natives, asset planning, launch-argument building (modern + legacy), Java parsing/selection, profile + account stores, auth response parsing | **Unit-tested** (`cargo test -p launcher-core`, 49 tests) |
| Installer end-to-end against a pre-seeded cache, process spawn + log streaming | **Tested offline** |
| Tauri shell | **Compiles** (`cargo check`), never run here (no display) |
| React UI | **Type-checks and builds**, never seen rendered here |
| Real downloads from Mojang, Fabric/Quilt meta, Adoptium Java download, Microsoft sign-in, actually starting Minecraft | **Not tested** – the build sandbox cannot reach those servers. Expect small bugs on first real run; please report logs |
| Forge / NeoForge | **Not implemented** (needs installer processing). UI shows them disabled |

## Run it

Prerequisites: Rust stable (recent), Node 20+, and Tauri's platform libraries
(Linux: `libwebkit2gtk-4.1-dev libgtk-3-dev libsoup-3.0-dev librsvg2-dev libssl-dev libdbus-1-dev`; Windows: WebView2 + MSVC build tools; macOS: Xcode CLT).

```bash
npm install
npm run tauri dev        # development
npm run tauri build      # installer
cargo test -p launcher-core
```

## Microsoft sign-in setup (required)

1. Entra admin center → App registrations → New registration → *Personal Microsoft accounts only*.
2. Authentication → enable **Allow public client flows**.
3. Paste the Application (client) ID in VYRO → Settings (or build with `VYRO_MS_CLIENT_ID=<id>`).
4. To let *other players* sign in with your app, Mojang must approve it for the Minecraft services API (see their launcher-access process). Until then only you can sign in.

Passwords are never handled. Refresh/access tokens are stored in the OS credential store (Windows Credential Manager, macOS Keychain, Secret Service).

## Data

Everything lives in the app data folder under `vyro/` (shown in Settings): shared `libraries/`, `assets/`, `versions/`, `runtimes/`, plus one `instances/<profile>/` per profile.
Deleting a profile keeps its game folder on disk so worlds are never lost by accident.

## Roadmap

1. **Stage 1** – auth, install/launch, profiles, main UI.
2. **Stage 2 (this)** – Modrinth mod manager: search/sort, Minecraft-version + loader filtering, one-click and multi-select install with automatic required dependencies, compatibility checks, enable/disable, uninstall, update check, conflict + unused-dependency detection, curated collections. Still to do: Forge/NeoForge, screenshots-only detail view is basic (no Markdown rendering).
3. **Stage 3 (draft written)** – `vyro-mod` (Fabric, Java): in-game HUD, keystrokes/CPS, crosshair; HUD Studio editor in the launcher writing a JSON layout the mod reads.
4. **Stage 4** – hardware detection + presets, Performance Center (frame-time reports from the mod), crash viewer, resource packs/shaders, modpack import/export, auto-update.

Not yet built: news/announcements feed (needs a backend), anything listed in stages 2–4.

## Mod manager notes

- Mods are installed per profile into `instances/<profile>/mods` and tracked in `vyro-mods.json` next to it. Jars you add by hand are listed as "not installed by VYRO" and never touched.
- Downloads are accepted only from `cdn.modrinth.com`, verified against Modrinth's SHA-1, and file names are validated before anything is written.
- Collections use Modrinth slugs resolved at install time; ones that were renamed or don't support the profile's Minecraft version are reported as skipped, not silently dropped.
- Disabling renames `x.jar` to `x.jar.disabled`. Changing mods is blocked while the profile's game is running.

## Getting the VYRO mod into profiles

1. Build and embed the mod (needs Java 25 on PATH for the Gradle build): `npm run sync-mod`
2. Restart `npm run tauri dev` (this recompiles the launcher with the jar embedded).
3. Mods tab -> "Install VYRO mod". It installs Fabric API (version-checked for the profile) and the mod jar, and the
   banner offers an update when the launcher carries a newer mod version.
