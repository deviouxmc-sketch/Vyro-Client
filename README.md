# VYRO CLIENT

A standalone Minecraft: Java Edition launcher (Tauri 2 + Rust + React/TypeScript).
**Status: Stage 1 – launcher foundation.** Not an official Minecraft product; not affiliated with Mojang or Microsoft.

## Layout

| Path | What it is |
|---|---|
| `crates/launcher-core` | Pure-Rust engine: version manifests, libraries/natives/assets, Java detection + download, launch arguments, Fabric/Quilt install, Microsoft/Xbox/Minecraft auth, accounts, profiles |
| `src-tauri` | Thin desktop shell: commands, OS keychain storage, events to the UI |
| `src` | React UI: Play, Profiles, Accounts, Settings |
| `public/logo.svg` | VYRO logo |

Launcher and game stay separated: the launcher only builds a command line and spawns the JVM. In-game features (HUD etc.) will live in a separate Java/Fabric mod (Stage 3).

## What works / how it was verified

| Area | State |
|---|---|
| Rule evaluation, version inheritance (Fabric/Quilt), libraries, legacy + modern natives, asset planning, launch-argument building (modern + legacy), Java parsing/selection, profile + account stores, auth response parsing | **Unit-tested** (`cargo test -p launcher-core`, 34 tests) |
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

1. **Stage 1 (this)** – auth, install/launch, profiles, main UI.
2. **Stage 2** – Modrinth mod manager (search, filters, dependency resolution, compatibility checks, enable/disable, updates), mod collections, Forge/NeoForge.
3. **Stage 3** – `vyro-mod` (Fabric, Java): in-game HUD, keystrokes/CPS, crosshair; HUD Studio editor in the launcher writing a JSON layout the mod reads.
4. **Stage 4** – hardware detection + presets, Performance Center (frame-time reports from the mod), crash viewer, resource packs/shaders, modpack import/export, auto-update.

Not yet built: news/announcements feed (needs a backend), anything listed in stages 2–4.
