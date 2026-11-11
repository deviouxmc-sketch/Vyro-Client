# VYRO Client – Privacy and security

VYRO Client is a desktop launcher for Minecraft: Java Edition. This page describes exactly what it does with your
account and your data. The source code in this repository is the authority; if the code and this page ever disagree,
please open an issue.

## Sign-in

- VYRO uses Microsoft's official **OAuth device-code flow** (scope `XboxLive.signin offline_access`).
  You sign in on Microsoft's own page in your browser. **VYRO never sees or stores your Microsoft password.**
- After sign-in VYRO performs the standard exchange: Microsoft → Xbox Live → XSTS → Minecraft services
  (`api.minecraftservices.com`), then reads your Minecraft profile (name and UUID) and checks that the account owns
  Minecraft: Java Edition.

## What is stored, and where

| Data | Where | Notes |
|---|---|---|
| Microsoft refresh token | Your operating system's credential store (macOS Keychain, Windows Credential Manager, Secret Service on Linux) | Never written to a plain file |
| Minecraft access token (short-lived) | Same credential store | Stored separately from the refresh token |
| Username and UUID | `accounts.json` in VYRO's data folder | Not secret |
| Profiles, settings, mod lists | JSON files in VYRO's data folder | Local only |

Removing an account in VYRO deletes its tokens from the credential store.

## What VYRO sends, and to whom

VYRO has **no backend server and no telemetry**. It does not collect, log or transmit your tokens, your account details
or your usage anywhere except the services needed to do its job:

- Microsoft, Xbox Live and Mojang/Minecraft services: sign-in and profile (`login.microsoftonline.com`,
  `*.xboxlive.com`, `api.minecraftservices.com`)
- Mojang's download servers: game files (`piston-meta.mojang.com`, `libraries.minecraft.net`,
  `resources.download.minecraft.net`)
- Fabric / Quilt metadata servers: mod loader profiles
- Adoptium (`api.adoptium.net`): a Java runtime, only if none is installed
- Modrinth (`api.modrinth.com`, `cdn.modrinth.com`): mod search and downloads. Downloaded files are checked against
  Modrinth's SHA-1 and are only accepted from Modrinth's CDN.
- `mc-heads.net`: player head images shown next to your account. This sends your public Minecraft UUID to that
  third-party image service. (This will become optional.)

## When the game starts

Minecraft receives your access token as a launch argument, exactly as with the official launcher. Other programs
running as your user on the same computer could see it in the process list while the game runs. VYRO masks the token in
its own game log.

## Your control

- Sign out of an account at any time (Accounts → Sign out).
- Delete VYRO's data folder (shown in Settings) to remove everything it stored on disk.
- Deleting a profile never deletes its game folder, so your worlds are not lost by accident.

## Reporting problems

Security issues: please open a private security advisory on this repository, or an issue if it is not sensitive.

*VYRO Client is not an official Minecraft product and is not approved by or associated with Mojang or Microsoft.*
