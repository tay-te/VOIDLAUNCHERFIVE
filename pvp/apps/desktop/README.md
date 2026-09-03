# `apps/desktop` — the VOID PVP launcher

Tauri 2 + Rust + React 19. One frameless window, a tray, and thin `#[tauri::command]`
wrappers over `void-core`, `void-bridge` and `void-loadout`.

Figma frames `244:3` → `244:431` (Play · Mods · Cosmetics · Servers · Friends), design
reference in [`../../design/README.md`](../../design/README.md).

```
apps/desktop/
├── src/                    the launcher React entry
│   ├── components/         local port of the shared component set  (→ @void/ui)
│   ├── features/           TopNav · Dock · CommandPalette · LogDrawer
│   ├── screens/            Play · Mods · Cosmetics · Servers · Friends · Settings
│   ├── stores/             Zustand: session · loadouts · launch · servers · ui
│   ├── local/              the seam: typed invoke/listen, registry, tokens
│   └── mocks/tauri.ts      a mock @tauri-apps/api, so every screen runs in a browser
└── src-tauri/              the Rust half
    ├── src/commands/       the launcher's own logic          — no Tauri
    ├── src/adapters/       bridge+JVM orchestration, SLP     — no Tauri
    └── src/{ipc,tray,window,updater}.rs                      — needs Tauri
```

## Prerequisites

Everywhere: **Rust 1.77+**, **Node 22**, **pnpm 10**.

| OS | Also needed |
|---|---|
| **Windows** | [Visual Studio Build Tools](https://visualstudio.microsoft.com/visual-cpp-build-tools/) with the *Desktop development with C++* workload, and **WebView2** (present on Windows 11 and up-to-date Windows 10; otherwise install the Evergreen Runtime) |
| **macOS** | Xcode Command Line Tools — `xcode-select --install` |
| **Linux** (dev only; not a shipping target, §13) | `libwebkit2gtk-4.1-dev libgtk-3-dev libayatana-appindicator3-dev librsvg2-dev libssl-dev` |

Then, from the repo root:

```sh
pnpm install
```

### Microsoft sign-in needs an Azure application id

`void-core` deliberately compiles no client id in: an Azure application is
per-publisher, has to be registered with the device-code flow enabled and approved for
the Minecraft API, and would be a credential in the repository. Set one before using
**Sign in**:

```sh
export VOID_MS_CLIENT_ID=<your azure application (client) id>
```

…or put `"ms_client_id"` in `~/.void-pvp/config.json`. Without it, **Play offline** still
works end to end — it needs no network at all.

## Running

```sh
pnpm dev:web        # browser preview at http://127.0.0.1:5183 — no Rust, no webview
pnpm tauri dev      # the real launcher
pnpm tauri build    # a signed-shaped installer for the host OS
```

`pnpm dev:web` is the one to reach for when reviewing screens. `vite.config.ts` aliases
`@tauri-apps/api/{core,event}` to [`src/mocks/tauri.ts`](src/mocks/tauri.ts) whenever
`TAURI_ENV_PLATFORM` is unset, so every screen, every launch phase and every error state
renders with fixture data and no Rust toolchain. There are no `if (isTauri)` branches in
the app; the alias is the whole mechanism, which also means the mock cannot survive into
a shipped bundle. A banner on the canvas says when you are looking at the preview.

Other scripts: `pnpm typecheck`, `pnpm test`, `pnpm build` (typecheck + Vite), and
inside `src-tauri/`, `cargo test` and `cargo check --no-default-features`.

## First-run flow

1. **Sign in.** The gear → *Account*. Microsoft is a device-code flow: the launcher shows
   a short code and a URL immediately and pushes `auth:status` events until the token
   chain (Microsoft → Xbox Live → XSTS → Minecraft services) completes. The refresh token
   goes to the OS keychain, so the next start signs in silently. *Play offline* takes a
   name and works on offline-mode servers.
2. **Pick a loadout.** The library is seeded on first run — `~/.void-pvp/loadouts/*.json`.
   Version is 1.8.9 and the picker's other entries are disabled: it is the only version
   this client targets (§15).
3. **Launch** (or `⌘↵` / `Ctrl+↵`). `prepare` resolves the 1.8.9 and Legacy Fabric
   manifests, downloads libraries, natives, the client jar and ~3,000 asset objects with
   SHA-1 verification, then finds or fetches Temurin 8. Progress streams as
   `prepare:progress`; the button becomes a bar with a step label and a MB/s readout.
   `prepare` is idempotent — everything is verified against the manifest and skipped if
   already present — so pressing Launch again on a warm install costs a manifest fetch.
4. **The bridge and the JVM.** `void-bridge` binds `127.0.0.1:0`, mints a per-spawn
   token, and both are passed to the JVM as `-Dvoid.port` / `-Dvoid.token` (§7). The mod
   connects, sends `hello`, and gets the whole world of state back in `init`.
5. **Hide to tray.** After a successful spawn the window hides (configurable in
   Settings). The tray offers *Show launcher · Switch loadout ▸ · Quit*; switching there
   hot-swaps the loadout in the running game rather than only affecting the next launch.
6. **Back with stats.** `game:closed` brings the window back and shows played time and
   average fps, folded into the loadout's own totals from the mod's `session` reports.

Everything lives under `~/.void-pvp` (or `$VOID_PVP_HOME`): `loadouts/`, `settings.json`,
`config.json`, `versions/`, `libraries/`, `assets/`, `java/`, `game/`. Settings → *Data
folder* → **Open** reveals it.

## What is verified here, and what needs a real machine

Verified on this Linux container (Rust 1.94, Node 22, pnpm 10, webkit2gtk 2.52):

- `cargo check --all-targets` and `cargo check --no-default-features` — both feature
  configurations compile, so a CI runner with no webkit2gtk still type-checks the whole
  command layer.
- `cargo test` — 44 tests over the command layer, the launch orchestration, the progress
  translation and the SLP pinger.
- `pnpm typecheck`, `pnpm test` (32 store/mock tests), `pnpm build`.

**Not verified here, and not verifiable here:**

- **The window.** There is no display in this container, so nothing was ever rendered.
  Layout, the custom drag region, the frameless chrome, tray behaviour, hide-to-tray and
  the ⌘↵ shortcut all need a desktop session.
- **A real launch.** No Minecraft was downloaded and no JVM was spawned: that needs
  network access to Mojang and Adoptium, an account, and several hundred megabytes.
  The launch path is exercised up to its guards (unknown loadout, not signed in, wrong
  Java version) and no further.
- **Microsoft sign-in.** Needs an Azure application id (see above).
- **`server_ping`.** The wire format is unit-tested (varints, the status JSON, MOTD
  flattening, host splitting) and failure against an unroutable address is tested, but no
  live server was reached.
- **Bundling.** `pnpm tauri build` was not run. `src-tauri/icons/` holds PNGs only;
  Windows and macOS bundles want `icon.ico` and `icon.icns` — run `pnpm tauri icon
  <source.png>` on a real machine to generate the full set.
- **The updater.** Wired, but `tauri.conf.json` points at `https://updates.void.invalid/…`
  — `.invalid` is reserved by RFC 2606 and can never resolve, so a development build
  cannot be talked into installing anything. Replace the endpoint and the `pubkey`
  placeholder when release signing exists (§16.5).

## Notes for whoever picks this up

- **This crate is not in the root Cargo workspace, on purpose.** `pvp/Cargo.toml` does
  not list `apps/desktop/src-tauri`, and `src-tauri/Cargo.toml` opts out with an empty
  `[workspace]` table. Tauri needs webkit2gtk to resolve its build scripts on Linux, and
  keeping this package out means `cargo check` at the repo root still compiles the three
  core crates on a bare runner. The core crates are consumed as path dependencies.
- **The launcher is the rich renderer.** `design/ultralight-notes.md` §1–2: the launcher
  runs in a real system webview, so `backdrop-filter` and the noise blend are allowed
  *here* and forbidden in the in-game bundle. `index.html` carries
  `data-renderer="webview"`, and every effect the overlay cannot have is scoped under
  that attribute so one stylesheet can serve both bundles.
- **The three-way settings split** (§8.3) is deliberate and easy to undo by accident:
  `menu_key`/`cycle_loadout_key`/`theme`/`ui_scale`/`hud_editor_grid` are
  `void-loadout`'s `GlobalSettings` and cross to the mod in `init`; `java_path`/`ram_mb`/
  `mod_jar` are `void-core`'s `config.json` and never leave Rust; `hide_to_tray_on_launch`
  and `update_channel` ride in `GlobalSettings.extra`, which the schema allows. If it
  changes how the game *plays*, it belongs in the loadout, not in any of the three.
