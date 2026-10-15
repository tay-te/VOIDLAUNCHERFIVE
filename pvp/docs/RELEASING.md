# Releasing the VOID PvP client

Everything a player installs comes from one GitHub Release workflow,
[`.github/workflows/pvp-release.yml`](../../.github/workflows/pvp-release.yml), triggered by a
`pvp-v*` tag on `origin` (`https://github.com/tay-te/VOIDLAUNCHERFIVE`). The older Electron
launcher keeps `v*` tags and `build.yml`; the two never overlap.

## What a release contains

| Asset | Where it is read |
|---|---|
| `VOID PVP_<v>_x64-setup.exe` (+ `.sig`) | Windows installer; the `.sig` is for the Tauri updater |
| the universal `.dmg`, and `.app.tar.gz` (+ `.sig`) for the updater | macOS (Apple Silicon + Intel) |
| `latest.json` | the launcher's updater, via the channel releases below |
| `void-client-<mod>-<platform>.jar` | the mod, one per platform (`windows-x64`, `macos-x64`, `macos-arm64`, `linux-x64`) |
| `mod-manifest.json` | the signed list of those JARs (URL, SHA-256, size), protocol version and minimum launcher version |

Two **channel releases** never move: `pvp-stable` and `pvp-beta`. Each carries only the current
`latest.json` and `mod-manifest.json`, replaced on every release:

```
https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download/pvp-stable/latest.json
https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download/pvp-stable/mod-manifest.json
https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download/pvp-beta/…
```

A stable tag (`pvp-v0.2.0`) updates both channels; a prerelease tag (`pvp-v0.2.0-beta.1`) only
`pvp-beta`. The launcher reads the channel from Settings → *Update channel*. Nothing is ever marked
GitHub's "latest" release: `/releases/latest` belongs to the Electron launcher. **The repository has
to be public** (or the assets hosted elsewhere) for unauthenticated launchers to download them.

## Cutting a release

```sh
node pvp/scripts/release/bump-version.mjs 0.2.0        # tauri.conf.json, Cargo.toml, package.json
# bump mod/gradle.properties mod_version too when the mod changed
cd pvp/apps/desktop/src-tauri && cargo check && cd -   # refresh Cargo.lock
git commit -am "pvp 0.2.0" && git tag pvp-v0.2.0 && git push origin HEAD pvp-v0.2.0
```

The workflow refuses a tag whose version differs from `tauri.conf.json`. It runs all of
`pvp-ci.yml` first (schemas, Rust, web, mod, Ultralight natives, per-OS JARs), then builds the
launcher on Windows and macOS, signs the mod manifest, publishes `pvp-v0.2.0`, and points the channel
releases at it.

`min_launcher_version` in the manifest is the version being released: the mod and the launcher ship
together (`schema/protocol.json`). An older launcher keeps running the last mod it can drive (from its
cache) and shows "restart to update"; the updater prompt does the rest.

## Keys

Two keypairs, generated once (2026-10-15). **The private halves live only in
`C:\Users\bitto\.void-release-keys\` on the release machine and in GitHub secrets — never in the
repository.** Back that folder up somewhere safe: losing the updater key means installed launchers
can never auto-update again; losing the manifest key means a launcher release with a new public key.

| File | What it is | Public half |
|---|---|---|
| `tauri-updater.key` + `tauri-updater.key.password` | minisign key the Tauri updater checks installers against | `plugins.updater.pubkey` in `apps/desktop/src-tauri/tauri.conf.json` (also `tauri-updater.key.pub`) |
| `mod-manifest-ed25519.pem` | ed25519 key that signs `mod-manifest.json` | `crates/void-core/keys/mod-manifest.pub`, compiled into the launcher |

To build signed updater artifacts locally (as the release workflow does):

```sh
export TAURI_SIGNING_PRIVATE_KEY="$(cat ~/.void-release-keys/tauri-updater.key)"
export TAURI_SIGNING_PRIVATE_KEY_PASSWORD="$(cat ~/.void-release-keys/tauri-updater.key.password)"
cd pvp/apps/desktop && pnpm tauri build      # → src-tauri/target/release/bundle/nsis/*.exe + .sig
```

Without `TAURI_SIGNING_PRIVATE_KEY` a local `pnpm tauri build` fails at the end (the config asks for
updater artifacts); pass `--config '{"bundle":{"createUpdaterArtifacts":false}}'` for an unsigned
build. To sign or check a manifest by hand: `node pvp/scripts/release/mod-manifest.mjs sign|verify`.

## GitHub secrets

Settings → Secrets and variables → Actions on `tay-te/VOIDLAUNCHERFIVE`:

| Secret | Value | Without it |
|---|---|---|
| `VOID_MOD_MANIFEST_KEY` | contents of `mod-manifest-ed25519.pem` | **the release fails** — an unsigned mod is useless |
| `TAURI_SIGNING_PRIVATE_KEY` | contents of `tauri-updater.key` | no `latest.json`; installed launchers never see the release |
| `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` | contents of `tauri-updater.key.password` | — |
| `WINDOWS_CERTIFICATE` | base64 of an Authenticode `.pfx` (`base64 -w0 cert.pfx`) | unsigned installer; SmartScreen warns |
| `WINDOWS_CERTIFICATE_PASSWORD` | its password | — |
| `APPLE_CERTIFICATE` | base64 of a *Developer ID Application* `.p12` | unsigned app; Gatekeeper blocks it by default |
| `APPLE_CERTIFICATE_PASSWORD` | its password | — |
| `APPLE_SIGNING_IDENTITY` | e.g. `Developer ID Application: Your Name (TEAMID)` | — |
| `APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID` | Apple ID, an app-specific password, team id — for notarization | signed but not notarized |
| `VOID_MS_CLIENT_ID` | the Azure application (client) id, below | Microsoft sign-in needs `ms_client_id` in each player's `config.json` |
| `VOID_DISCORD_APP_ID` | the Discord application id, below | Discord Rich Presence is off |

Windows certificates: an OV certificate still accumulates SmartScreen reputation slowly; an EV
certificate (usually on a hardware token or a cloud HSM, which needs a `signCommand` instead of
`certificateThumbprint`) is trusted immediately.

## Microsoft sign-in: Azure app and Minecraft API approval

The launcher signs in with Microsoft's device-code flow and then exchanges the token through Xbox
Live for a Minecraft services token. That needs an Azure application that Mojang has approved.

1. **Register the app.** [portal.azure.com](https://portal.azure.com) → *Microsoft Entra ID* →
   *App registrations* → *New registration*.
   - Name: `VOID PvP`.
   - Supported account types: **Personal Microsoft accounts only** (the launcher uses the
     `consumers` tenant).
   - Redirect URI: none needed for device code. (Optionally add *Mobile and desktop applications*
     → `https://login.microsoftonline.com/common/oauth2/nativeclient`.)
2. **Allow public client flows.** The app → *Authentication* → *Advanced settings* → **Allow public
   client flows: Yes**. Without it the device-code request fails with `AADSTS7000218`.
3. **API permissions.** The launcher asks for `XboxLive.signin offline_access`; nothing else has to be
   added in the portal for personal accounts.
4. **Copy the Application (client) ID** (a GUID) from *Overview*.
5. **Apply for Minecraft API access.** New Azure apps get `403 Forbidden` (*"Invalid app
   registration"*) from `api.minecraftservices.com/authentication/login_with_xbox` until Mojang
   approves them. Fill in Mojang's form for third-party launchers — linked from the Minecraft help
   article *"Microsoft authentication for third-party launchers"* (`aka.ms/mce-reviewappid`) — with the
   client id, the app name, and what the launcher does. Approval takes days to weeks; Mojang emails
   the result.
6. **Ship it.** Set the `VOID_MS_CLIENT_ID` secret; the release build compiles it in
   (`option_env!` in `crates/void-core/src/config.rs`). Precedence at run time:
   `$VOID_MS_CLIENT_ID` → `ms_client_id` in `~/.void-pvp/config.json` → the compiled-in id.

The client id is not a secret (every installed copy carries it), but it is per-publisher, so it is
injected rather than committed. Until approval, **Play offline** works end to end.

## Discord Rich Presence

1. [discord.com/developers/applications](https://discord.com/developers/applications) → *New
   Application* → name it **VOID PvP** (the name Discord shows as "Playing VOID PvP").
2. *Rich Presence* → *Art Assets* → upload the VOID mark with the key **`void`**.
3. Copy the *Application ID* into the `VOID_DISCORD_APP_ID` secret (or `$VOID_DISCORD_APP_ID` when
   running a dev build).

The launcher shows *In the launcher*, *Launching Minecraft 1.8.9*, and *Playing on
mc.hypixel.net* / *In the menus* with the loadout name and elapsed time. Settings → *Discord status*
turns it off.

## Developer overrides

| Override | Effect |
|---|---|
| `VOID_MOD_JAR=<path>` or `"mod_jar"` in `config.json` | install that JAR instead of the release channel's (Settings shows it as *Developer client JAR*); `scripts/launcher.ps1 -Build` sets it |
| `VOID_MOD_MANIFEST_URL=<url>` | read the mod manifest from elsewhere — still signature-checked, so only VOID-signed manifests pass |
| `void-pvp mod [--channel beta]` | fetch and verify the channel's JAR without downloading the game |
