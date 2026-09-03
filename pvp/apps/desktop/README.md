# `apps/desktop` — the VOID PVP launcher

Tauri 2 + Rust + React 19. One frameless window, a tray, and thin `#[tauri::command]`
wrappers over `void-core`, `void-bridge` and `void-loadout`.

Figma frames `244:3` → `244:431` (Play · Mods · Cosmetics · Servers · Friends), design
reference in [`../../design/README.md`](../../design/README.md).

```
apps/desktop/
├── src/                    the launcher React entry
│   ├── features/           TopNav · Dock · Menu · CommandPalette · LogDrawer
│   ├── screens/            Play · Mods · Cosmetics · Servers · Friends · Settings
│   ├── stores/             Zustand: session · loadouts · launch · servers · ui
│   ├── local/              the seam: typed invoke/listen, registry, keys, glyphs, CSS
│   ├── dev/backdrops.ts    preview-only canvas art, aliased away in a Tauri build
│   └── mocks/tauri.ts      a mock @tauri-apps/api, so every screen runs in a browser
├── visual-qa/              the Chromium + pixelmatch pass against design/screens
└── src-tauri/              the Rust half
    ├── src/commands/       the launcher's own logic          — no Tauri
    ├── src/adapters/       bridge+JVM orchestration, SLP     — no Tauri
    └── src/{ipc,tray,window,updater}.rs                      — needs Tauri
```

There is no `src/components/`. Every component the design's inventory names comes from
[`@void/ui`](../../packages/ui/README.md) — see *Components* below.

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

### The canvas backdrop

Figma `244:58` is a rendered Minecraft still behind the recessed canvas. There is no
licenced render in this repository and `design/` is read-only reference material, so the
app draws a gradient in the same key — `.canvas__art` in `local/app.css`, carrying the
`TODO(art)` that says what replaces it.

`pnpm dev:web` shows the frame's own canvas rectangle instead, so a review pass is not
looking at a placeholder where the design has art. That rides the same alias that swaps
in the mocked `@tauri-apps/api`: `vite.config.ts` resolves `@dev/backdrops` to
[`src/dev/backdrops.ts`](src/dev/backdrops.ts) when `TAURI_ENV_PLATFORM` is unset and to
`backdrops.none.ts` when it is set, so an installer contains none of it — checked with
`TAURI_ENV_PLATFORM=linux pnpm exec vite build`, which emits zero `Launcher-*.png`
assets. Append `?no-backdrop` to the preview URL to see the placeholder the app ships.

## Components

Every component in `design/README.md`'s inventory comes from
[`@void/ui`](../../packages/ui/README.md). The launcher used to carry a local port of the
set — `src/components/{index,mods,icons}.tsx` and the component half of `local/app.css` —
written while that package was still being built. All of it is gone.

`main.tsx` imports the three stylesheets once, in the order that package's README gives:

```ts
import '@void/ui/tokens.css';   // tokens + the two renderer layers — first
import '@void/ui/fonts.css';    // the three bundled OFL families
import '@void/ui/styles.css';   // every component style
import './local/app.css';       // the four regions below
```

`index.html` carries `data-renderer="webview"`, which is what selects the launcher's
token layer: this bundle runs in a real system webview, so it keeps the `backdrop-filter`
radii and the `.v-noise` grain the in-game bundle has to drop
(`design/ultralight-notes.md` §1–2).

| From the package | Where |
|---|---|
| `TopNav` `NavItem` `SearchBar` `IconButton` `Avatar` `Kbd` | the chrome band |
| `Dock` `PlayerChip` `LoadoutPicker` `VersionPicker` `LaunchButton` `FriendsOnline` `Divider` | the dock |
| `StatusPill` | the Play eyebrow |
| `Panel` `FilterTabs` | every screen but Play |
| `ModGrid` `ModTile` `ModSettingsPanel` `ModSettingsRow` `KeystrokesPreview` `EditPositionButton` `Slider` `Toggle` `KeybindChip` `Swatches` `PositionChips` | Mods |
| `CosmeticCard` `Button` | Cosmetics |
| `ServerRow` `Pane` `StatTile` `Sparkline` `GroupCaption` | Servers |
| `FriendRow` `PartyMemberRow` | Friends |
| `SettingsGroup` `SettingsRow` | Settings |
| `Palette` `PaletteInput` `PaletteSeam` `PaletteSection` `PaletteResult` `PaletteFooter` | ⌘K |
| `Icon` `MOD_ICONS` `resolveLoadoutIcon` `Tag` `StatusDot` `IconWell` | throughout |

### What is still local, and why

**`local/app.css`** — four regions the package has no reason to own, because the overlay
has no window: the shell and its frameless window controls; the recessed canvas, its
backdrop and where the dock sits on it; the Play hero (104 px display type over that
canvas); and the surfaces no frame draws at all — the loadout/version dropdown, the JVM
log drawer, the launch banners and the Settings modal. Nothing in it restates a design
value that a token already carries.

**`local/glyphs.tsx`** — six SVG glyphs, and not a second icon set. Minimise, maximise
and window-close exist because a frameless Tauri window draws its own buttons; `terminal`
because there is no JVM log inside the JVM; `trash` for removing a favourite; and the
Cosmetics and Servers nav marks, because the overlay has neither screen so those two
never reached the shared set. They follow the same drawing contract (one 24 × 24 grid,
1.6 px stroke, round caps) so a sprite-sheet renderer can swap them the same way.

**`features/Menu.tsx`** — the dropdown the dock's two pills open. `@void/ui` ships the
pill and not a popover, because in game the same picker opens the Loadouts panel instead
of a list. The pill is the package's; only the list below it is here.

**`local/keys.ts`** — `KeybindChip` takes the applied key from a promise, because in game
that promise is `void.openKeybindCapture(modId)` and Java is authoritative for the key it
actually saw. There is no game here, so `captureKey()` resolves the same promise from the
browser's own `KeyboardEvent.code`.

**One shim, with a `TODO(ui)`.** `@void/ui`'s reset includes
`.v-app button { background: none }` at specificity (0,1,1), and every component
background in the package is a single class at (0,1,0) — so putting `v-app` on the tree
makes **every button in it transparent** (measured:
`getComputedStyle('.v-launch').backgroundColor` is `rgba(0,0,0,0)`). Until that reset is
scoped upstream — `.v-app :where(button, input)` keeps its intent at zero specificity —
the root does not carry `v-app` and `local/app.css` carries the same reset itself, with
`:where()` so it cannot outrank a component. The block at the top of that file is the
whole of it, and no design value is duplicated.

## Visual QA

[`visual-qa/`](visual-qa/) drives `pnpm dev:web` in a real Chromium at the frames' own
1300 × 820 and diffs the result against `design/screens/Launcher-*.png` with `pixelmatch`.
[`visual-qa/report.md`](visual-qa/report.md) has the numbers, the side-by-side sheets and
a measured account of every difference that is left.

```sh
pnpm dev:web                                   # terminal 1
cd visual-qa
node capture.mjs after                         # eight shots: 5 screens + Settings + ⌘K + the launch progress
node capture.mjs after-plain --no-backdrop     # the same eight on the shipped gradient placeholder
node compare.mjs after && node compare.mjs after-plain
```

`playwright`, `pixelmatch` and `pngjs` are **not** dependencies of `@void/desktop`: they
are a review tool, not something the launcher ships, and adding them would put a browser
download into every `pnpm install` in the workspace. `visual-qa/lib.mjs` resolves them at
run time from `visual-qa/node_modules`, `NODE_PATH`, or the global root — so either

```sh
npm i -g playwright pixelmatch pngjs        # and once: npx playwright install chromium
npm --prefix visual-qa i --no-save playwright pixelmatch pngjs
```

works. Set `PLAYWRIGHT_BROWSERS_PATH` if the browsers live somewhere unusual, and
`VQA_URL` if the dev server is not on port 5183.

Sheets are written to `visual-qa/<screen>.png` (design | render | diff, half scale) and
are the only thing in that folder besides the scripts and `report.md` that is not
gitignored; the full-resolution captures and per-pixel diffs land in `visual-qa/out/`.

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
- **The screens.** All five, plus Settings, the ⌘K palette and the dock mid-launch, are
  rendered in a real headless Chromium at 1300 × 820 and diffed against the frames — see
  *Visual QA* above and [`visual-qa/report.md`](visual-qa/report.md).

**Not verified here, and not verifiable here:**

- **The window itself.** The screens render, but they render in a browser. The custom
  drag region, the frameless chrome, tray behaviour, hide-to-tray, the real system
  webview's `backdrop-filter`, and the ⌘↵ shortcut reaching the app when the window is
  not focused all need a desktop session.
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
- **Two findings that belong to `packages/ui`, not here.** Its reset makes every button
  in the package paint transparent (see *What is still local* above), and its bundled
  Bricolage Grotesque is pinned at `opsz 14`, which renders the 104 px hero title 12 %
  wider than the frame — 26 px titles agree exactly, so it only shows on Play. Both are
  measured in [`visual-qa/report.md`](visual-qa/report.md). The launcher works around the
  first and lives with the second rather than distorting type to chase a diff.
- **`local/protocol.ts` re-exports, it does not transcribe.** Everything the schemas
  define comes from `@void/protocol` — including the three `JavaToRust` messages the
  bridge forwarder republishes to the window, which are aliased there rather than
  rewritten. What that file *declares* is only the Rust ⇄ webview DTOs of
  `src-tauri/src/models.rs`, which no schema describes.
- **The three-way settings split** (§8.3) is deliberate and easy to undo by accident:
  `menu_key`/`cycle_loadout_key`/`theme`/`ui_scale`/`hud_editor_grid` are
  `void-loadout`'s `GlobalSettings` and cross to the mod in `init`; `java_path`/`ram_mb`/
  `mod_jar` are `void-core`'s `config.json` and never leave Rust; `hide_to_tray_on_launch`
  and `update_channel` ride in `GlobalSettings.extra`, which the schema allows. If it
  changes how the game *plays*, it belongs in the loadout, not in any of the three.
