# VOID PVP — Architecture

> Status: **DRAFT v2 for review.** No code exists yet. This document is the contract
> we build against. Figma source of truth:
> `figma.com/design/ks5kpynF3otxC5t1gNKfvg` → page **★ PVP — direction** (`244:2`).
>
> v2 supersedes the overlay-window design (v1). In-game UI is now rendered **inside the
> game's GL frame** by Ultralight, the way LabyMod 4 does it. See §1 for why.

---

## 1. What we are building

A Lunar-style PVP client for Minecraft **1.8.9**, delivered as:

1. **Desktop launcher** (Tauri + Rust) — auth, downloads, launch, loadout library,
   Servers, Friends. Figma frames `244:3` → `244:431`.
2. **In-game client mod** (Legacy Fabric + Ultralight) — renders the HUD and the
   Right-Shift menus *inside Minecraft's frame* from the same HTML/CSS the launcher
   uses. Figma frames `244:538` → `244:1900`.

The player launches from the desktop app. In game, the HUD is drawn in the same frame as
the world; Right Shift opens the mods panel over a blurred game. It feels in-game because
it **is** in-game.

### Why this shape

| Alternative | Rejected because |
|---|---|
| Draw menus in-game with raw GL (Lunar, Vape) | Custom font engine, SDF shaders, hand-drawn widgets; approximates a Figma, never matches it |
| Transparent always-on-top Tauri window over the game (v1, the Overwolf / Xbox Game Bar model) | Separate window → 1-frame HUD lag, alt-tab flicker, window tracking per OS, macOS Spaces, OBS Game Capture can't see it, no exclusive fullscreen |
| CEF/Electron offscreen → shared GPU texture → GL (Discord's model) | Ships Chromium (~120 MB), two per-OS GPU-interop layers, JNI to reach CGL on Mac. Right idea, wrong weight |
| JCEF inside the JVM | Chromium competing with the game for RAM; shaky on Java 8 / 1.8.9 |
| **Ultralight inside the JVM (chosen)** | ~10 MB, renders straight into MC's GL context. LabyMod 4 proved the *approach* on 1.8.9. Same React/CSS as the launcher. **We write and own the JNI binding** — see §6.2 and §13 |

One design system, two renderers: the launcher runs it in Tauri's system webview, the
game runs it in Ultralight. Rust stays the owner of state.

---

## 2. Runtime topology

```
┌────────────────────────────────────────────────────────┐
│  void-pvp desktop  (Tauri, one window)                 │
│   Launcher UI  ·  frames 244:3 → 431                   │
│   Rust core:  auth · download · JVM spawn · tray       │
│               WS server · loadout store · updater      │
└──────────────────────┬─────────────────────────────────┘
                       │ spawns JVM · ws://127.0.0.1:<port>
                       ▼
┌────────────────────────────────────────────────────────┐
│  Minecraft 1.8.9 + Legacy Fabric                       │
│  ┌──────────────────────────────────────────────────┐  │
│  │  void-client mod                                  │  │
│  │   sensors (Mixins) ──▶ JS bridge ──▶ Ultralight   │  │
│  │   actuators (Mixins) ◀── JS bridge ◀── (React UI) │  │
│  │   Ultralight view ──▶ GL texture ──▶ drawn in     │  │
│  │                        renderGameOverlay          │  │
│  │   WS client ◀──▶ Rust (loadout sync, telemetry)   │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

| Concern | Owner |
|---|---|
| Loadout persistence, sync, library | Rust (`void-loadout`) |
| **Live** loadout state while in game | Java (authoritative), mirrored to Rust over WS |
| HUD + menu rendering | Ultralight, in the JVM |
| Game telemetry | Java Mixins → JS bridge (in-process, no socket) |
| Gameplay mutation | Java Mixins ← JS bridge |
| Auth, downloads, launch, updater | Rust (`void-core`) |

The WS between Rust and Java carries **state**, not frames. Telemetry never leaves the
JVM to be drawn; it goes Mixin → JS in the same process.

---

## 3. The 12 mods, classified

The Figma treats them uniformly. The code doesn't — but with Ultralight in-process the
split is about *data direction*, not *who draws*. Everything is drawn by the UI.

`kind` is that data-direction split. **`category` is a second, independent axis**: the
tab the Mods panel files a mod under (Figma 244:538). The two do not agree — Crosshair
and Fullbright are `gameplay` but `visual`, Zoom is `gameplay` but `utility` — so both
live in `schema/mods.json` and nothing derives one from the other or re-declares either.
The `Mod` column below is the registry's `label`, which is the panel copy verbatim.

| Mod (`label`) | `kind` | `category` | Data source / effect |
|---|---|---|---|
| FPS display | HUD | hud | `Minecraft.debugFPS` |
| Keystrokes | HUD | hud | `KeyBinding` state, edge-triggered |
| CPS counter | HUD | hud | derived from clicks in JS |
| Ping display | HUD | hud | own `NetworkPlayerInfo.responseTime` |
| Coordinates | HUD | hud | `EntityPlayerSP` pos/yaw |
| Armor status | HUD | hud | `InventoryPlayer.armorInventory` durability |
| Potion effects | HUD | hud | `getActivePotionEffects` |
| Toggle sprint | Gameplay | pvp | `KeyBinding` override in `onLivingUpdate` |
| Fullbright | Gameplay | visual | `gammaSetting` override (client-side, Watchdog-tolerated) |
| Hitboxes | Gameplay | pvp | `RenderManager.debugBoundingBox` |
| Zoom | Gameplay | utility | FOV override while key held |
| Crosshair | Gameplay* | visual | replaces vanilla crosshair pass; drawn in GL at exact center |

\* Crosshair stays GL: it must sit at the exact pixel center and is 20 lines. Everything
else is HTML.

---

## 4. Repository layout (new monorepo: `void-pvp`)

Separate from `VOIDLAUNCHERFIVE`. Different toolchain, cadence and binary. Shares the
design tokens.

```
void-pvp/
├── apps/
│   └── desktop/                Tauri launcher
│       ├── src-tauri/          thin #[tauri::command] wrappers, tray, updater
│       └── src/                launcher React entry
├── packages/
│   ├── ui/                     Shared React components + design tokens (ported from
│   │                           VOID c83b777). Built twice: launcher bundle, in-game bundle
│   ├── ingame/                 In-game React entry (HUD + menus). Output is a static
│   │                           bundle embedded in the mod JAR as resources
│   └── protocol/               TS types generated from schema/
├── crates/
│   ├── void-core/              Auth (MS → Xbox → MC), manifests, Java runtime,
│   │                           JVM args, spawn. No Tauri dependency.
│   ├── void-bridge/            WS server, protocol types (serde)
│   └── void-loadout/           Loadout schema, persistence, diff
├── mod/                        Legacy Fabric mod, Gradle, Java 8 target
│   ├── native/                 Our JNI binding to Ultralight's C API + OpenGL GPUDriver.
│   │                           C++17, CMake. Built in CI for win-x64, mac-x64, mac-arm64.
│   │                           Java API in dev.voidclient.ultralight.
│   │                           (~150 C functions wrapped; 3–4k lines total)
│   └── src/main/java/dev/voidpvp/client/
│       ├── mixin/              sensors + actuators, one per feature
│       ├── ui/                 Ultralight host: view lifecycle, GL upload, input forwarding
│       ├── bridge/             JS ⇄ Java bindings (the `void.*` object)
│       ├── net/                WS client to Rust
│       └── screen/             VoidMenuScreen (mouse release + backdrop blur)
├── schema/
│   ├── protocol.json           Rust ⇄ Java WS messages
│   ├── bridge.json             JS ⇄ Java bridge surface
│   └── loadout.json
└── docs/
```

`void-core` has no Tauri dependency: CLI for free (`void-pvp launch --loadout sword-pvp`),
tests without a webview. Same lesson as VOID's 761-line `main.ts`.

### Java package names: `dev.void.*` does not exist

The names in the tree above are the real ones. `dev.void.client` and `dev.void.ultralight`,
which earlier drafts of this document and of `pvp/CONTRACTS.md` used, **cannot be
compiled**: `void` is a Java keyword and is not a legal identifier, so neither
`package dev.void.client;` nor an `import` of it parses. Both owners renamed the one
illegal segment:

| Was specified | Actual |
|---|---|
| `dev.void.client.*` | **`dev.voidpvp.client.*`** — the mod |
| `dev.void.ultralight.*` | **`dev.voidclient.ultralight.*`** — the JNI binding in `mod/native/java` |

Nothing else moved. The Fabric mod id is still `void`, the JS object is still
`window.void` (a reserved word *is* a legal property name in ES5 and later), the resource
root is still `assets/void/`, and the JAR is still `void-client`. `mod/build.gradle`
compiles `native/java` into the mod JAR, so `dev.voidpvp.client.ui.UltralightWebView`
imports the binding directly — a signature change there is a compile error, not a
runtime surprise.

---

## 5. Desktop launcher (Tauri)

Single frameless window. After a successful spawn it hides to the tray; on `game-closed`
it returns with session stats (played time, avg fps — the numbers on the Loadouts frame).

Tray: Show launcher · Switch loadout ▸ · Quit.

There is **no overlay window**. The launcher has no in-game presence at all beyond the
WS connection.

---

## 6. In-game client mod (`void-client`)

### 6.1 Principles

- **UI is HTML.** No GL widgets except the crosshair. If it's in the Figma, it's React.
- **Java is authoritative for live state.** Toggles apply instantly in-process; Rust is
  told afterwards. If the launcher is closed, the game keeps working.
- **No config files on the Java side.** State arrives from Rust on `hello`, and is
  mirrored back on change. If Rust is unreachable, in-memory state persists for the
  session and is flushed on reconnect.
- **Sensors read, actuators toggle documented client-side options.** No packets touched.

### 6.2 Ultralight host (`ui/`)

- **Binding is ours.** Both public Java bindings are dead: `LabyMod/ultralight-java`
  (targets 1.3 beta, last commit Jul 2021, archived Jun 2024) and
  `Janrupf/ultralight-java-reborn` (0.0.2-SNAPSHOT, last commit Jul 2023). Neither
  targets 1.4. `mod/native/` wraps Ultralight's C API over JNI (Java 8 → no Panama) and
  implements the `GPUDriver` in OpenGL. Ultralight 1.4 (Apr 2025) is the target.
- One `View` sized to the framebuffer, transparent background, GPU renderer via our GL
  driver — it renders directly into MC's GL context. No CPU readback, no
  `glTexSubImage2D` of full frames.
- Rendered at the end of `GuiIngame.renderGameOverlay` (HUD layer) and again in
  `VoidMenuScreen.drawScreen` (menu layer) — same view, the React app decides what's
  visible. Depth test off, **premultiplied**-alpha blend: the binding hands back an RGBA
  texture with premultiplied alpha and a top-left origin, so the quad draws with
  `glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, …)` and `v = 0` at the top.
  Straight alpha — which an earlier draft of this section specified — double-darkens
  every antialiased edge in the UI.
- `View` resize on `Minecraft.resize`. Scale = MC GUI scale × window DPI, passed to
  React as a CSS variable.
- Ticked once per game tick for JS timers; painted once per frame.

### 6.3 Input forwarding

- **HUD mode** (no screen open): Ultralight gets **no** input. Player controls are
  untouched. Keystrokes reach the UI as *data* via the bridge, not as events.
- **Menu mode** (`VoidMenuScreen` open): MC has released the mouse. The screen forwards
  `mouseClicked/Moved/Released`, `keyTyped`, scroll → `view.fireMouseEvent/fireKeyEvent/
  fireScrollEvent`. Escape → close screen (unless React reports a focused text input,
  in which case forward it).
- Right Shift is a normal `KeyBinding`; pressing it opens/closes `VoidMenuScreen`.
  L cycles loadouts. Both rebindable via the UI, persisted as global settings.

### 6.4 `VoidMenuScreen` (backdrop blur + mouse release)

`extends GuiScreen`. Its `drawScreen`:

1. Copy the main framebuffer to a ¼-res FBO
2. Two-pass Gaussian blur (H, V) on the small FBO
3. Draw back full-screen + `rgba(0,0,0,0.45)` tint
4. Paint the Ultralight view (menu layer)

`doesGuiPauseGame()` = `false`. Same technique Vape and Lunar use; we use it for the
backdrop only.

### 6.5 JS ⇄ Java bridge (`bridge/`, `schema/bridge.json`)

A single `window.void` object. Java → JS is push (no polling); JS → Java is calls.

```ts
// Java → JS  (pushed from the render/tick thread, batched per frame)
void.on('keys',    (k: { w:0|1, a, s, d, lmb, rmb, space, shift }) => …)  // edge-triggered
void.on('tick',    (t: { fps, ping, pos:{x,y,z,yaw}, armor:[…], fx:[…] }) => …) // 20 Hz
void.on('server',  (s: { host, connected }) => …)
void.on('loadout', (l: Loadout) => …)         // on init + on switch (from Rust or L key)
void.on('loadouts',(library: Loadout[]) => …) // the whole library, from init.loadouts
void.on('setting', (s: { id, key, value }) => …) // one setting Java changed by itself
void.on('menu',    (open: boolean) => …)

// JS → Java
void.setGameplay('fullbright', true)           // returns applied state (sync, in-process)
void.setHud(id, { anchor, dx, dy, scale })     // persists via Rust
void.setModSetting(id, key, value)
void.switchLoadout(id)
void.closeMenu()
void.openKeybindCapture(modId) → Promise<key>
```

Because this is in-process, `setGameplay` is synchronous and the toggle in the UI shows
real state — no optimistic UI, no `ack`.

`loadouts` exists because the Loadouts frame lists the whole library and the quick
palette offers "turn on in *other* loadout": without it JS would only know the loadouts
it happened to watch go by. `setting` exists because an in-game hotkey can change one
setting, and replacing the whole loadout to say so re-renders the world for one boolean.

**`openKeybindCapture` is the one asynchronous call, and its shape is easy to get
wrong.** The hop is still synchronous and still answers — with `returns: null`, meaning
*armed*. The captured key arrives later on the push channel as a call-result envelope,
`__emit({ c:'openKeybindCapture', returns:'V' })`, or `returns: null` again when the
player cancelled with Escape. Null therefore travels on both channels meaning two
different things, and a shim must tell them apart **by channel, never by value**.

### 6.6 Sensors (Mixins)

| Sensor | Injection point | Pushes |
|---|---|---|
| Keys | `KeyBinding.setKeyBindState` | `keys` on change |
| FPS | `Minecraft.debugFPS` per tick | `tick` |
| Ping | own `NetworkPlayerInfo` | `tick` |
| Position | `EntityPlayerSP` per tick | `tick` |
| Armor | `armorInventory` durability, on change | `tick` |
| Potions | `getActivePotionEffects`, on change | `tick` |
| Server | `connect` / `disconnect` | `server` |

`tick` is coalesced to one push per game tick (20 Hz). `keys` is edge-triggered.

### 6.7 Actuators (Mixins)

Toggle sprint, Fullbright, Hitboxes, Zoom, Crosshair — as in §3. Each is a `boolean` +
settings struct read by its Mixin every frame; `setGameplay` just writes the field.

### 6.8 Fullscreen

Exclusive fullscreen **works** — we're in the frame. Nothing to enforce.

### 6.9 Transport to Rust (`net/`)

Netty WS client (Netty ships with MC). Port + session token via
`-Dvoid.port=… -Dvoid.token=…`. Reconnect with backoff. On `hello`, Rust sends the
active loadout and global settings; Java pushes `state` deltas on every change.

---

## 7. Rust ⇄ Java protocol (`schema/protocol.json`)

Carries state and telemetry summaries, **not** per-frame data. JSON, versioned by `v`.

```jsonc
// Java → Rust
{ "t":"hello",   "v":2, "mc":"1.8.9", "mod":"0.1.0", "token":"…" }
{ "t":"state",   "loadout":"sword-pvp", "patch":{ "mods.fullbright.on":true } }   // on change
{ "t":"hud",     "loadout":"sword-pvp", "items":[ {id, anchor, dx, dy, scale} ] }  // on drop
{ "t":"session", "fps_avg":142, "played_ms":812000, "server":"mc.hypixel.net" }  // every 60 s + on exit
{ "t":"server",  "host":"mc.hypixel.net", "connected":true }
{ "t":"hotkey",  "id":"loadout.next" }       // or "overlay" — already applied in game

// Rust → Java
{ "t":"init",    "v":2, "loadout":{…}, "loadouts":[…full loadouts], "settings":{…} }
{ "t":"loadout", "loadout":{…} }             // launcher/tray switched it
{ "t":"settings","settings":{…} }
```

Rules: unknown `t`/fields ignored (forward compatible). `v` mismatch → launcher refuses
to launch and prompts update; mod and launcher ship together.

`hotkey` is a notification, never a request: by the time it arrives Java has already
cycled the loadout or toggled the overlay. It exists so the launcher's own active-loadout
pointer, and the tray, follow the running game instead of contradicting it. It is dropped
rather than queued when the link is down — the *state* the key press produced travels in
its own `state` message, which is queued, so replaying a keystroke from minutes ago would
be noise.

`init.loadouts` carries **whole loadouts, not summaries**. A loadout is around a kilobyte
and the library is capped at 128, so the whole library is a few hundred KB sent once per
launch. In exchange `switchLoadout` and the L-key cycle apply any loadout in under a frame
with no round trip (§8.2), and the in-game Loadouts screen has something to list — which
is why there is deliberately **no** `request_loadout` message. `loadout_summary` still
exists in `schema/loadout.json`, but only for the launcher's own tray and IPC.

---

## 8. Loadouts (`void-loadout`)

> "A loadout is which mods are on, their settings and HUD layout." — Figma 244:1130

A loadout is a **template**: a named snapshot of mod state. Fully hot-swappable.

```jsonc
{
  "id":"sword-pvp", "name":"Sword PvP", "icon":"sword", "server":"hypixel", "mc":"1.8.9",
  "mods": {
    "keystrokes":    { "on":true, "scale":1.0, "opacity":0.85 },
    "cps":           { "on":true },
    "toggle_sprint": { "on":true },
    "fullbright":    { "on":false },
    "zoom":          { "on":true, "key":"C" },
    …
  },
  "hud": [
    { "id":"keystrokes", "anchor":"bottom-left", "dx":32,  "dy":-40, "scale":1.0 },
    { "id":"fps",        "anchor":"top-left",    "dx":20,  "dy":20 },
    { "id":"armor",      "anchor":"right",       "dx":-20, "dy":0 }
  ],
  "stats": { "played_ms":15600000, "fps_avg":142 }
}
```

### 8.1 HUD positions are anchor + offset, never absolute pixels

Figma shows `x 32 · y 580`. We store `anchor + dx/dy + scale`. Survives GUI-scale changes,
resolution changes, fullscreen toggles.

### 8.2 Hot-swap

In-process: `switchLoadout(id)` writes every actuator field and re-renders the HUD. Sub-
frame. Then `state` to Rust. From the launcher/tray: Rust sends `loadout`, Java does the
same thing. **L** in game cycles.

### 8.3 Global vs per-loadout

Per-loadout: everything above. Global: account, Java path, RAM, hotkeys (R-Shift, L),
theme. If it changes how the game *plays*, it's in the loadout.

---

## 9. Frontend

- **One React codebase, two bundles.** `packages/ui` holds components + tokens (ported
  from VOID `c83b777`). `apps/desktop` bundles the launcher; `packages/ingame` bundles
  the HUD + menus into static files embedded in the mod JAR.
- **Ultralight constraints** (WebKit-derived, older CSS surface):
  - ✅ flexbox, `border-radius`, `box-shadow`, gradients, 2D transforms, transitions,
    `@font-face`, custom properties
  - ✅ (1.4) CSS `filter`, `@font-face`, ES2022 + modules
  - ❌ `backdrop-filter` (slated for 1.4.1, **not shipped** as of Sep 2026), `text-shadow`,
    3D transforms, WebGL, video, **CSS Grid**, `position: sticky`.
    **On grid specifically**: earlier drafts of this list had "grid + subgrid" under
    1.4, read off the wiki. That claim is for **1.4.1**, and the SDK we pin and ship is
    **1.4.0b (`081c48b`)** — so grid is not available to us. The design is flex plus
    absolute positioning throughout, `packages/ui` ships no `display: grid`, and
    `packages/ingame/scripts/check-ultralight.mjs` fails the build on it. **Keep the
    guard**; revisit only if we ever move to 1.4.1. The panel backdrop blur is GL (§6.4); *glass inside
    cards* is the fidelity risk and is what M1 tests. Design tokens must not rely on
    `text-shadow`.
  - JS: JavaScriptCore, ES2022. No Chrome devtools — we ship a `?debug` mode that
    renders the in-game bundle in a normal browser with a fake `window.void`.
- **State**: a small store (Zustand); `window.void.on(...)` is the only writer of live
  data. No MobX — the HUD wants cheap frequent updates.
- **Rendering discipline**: `transform` for positions, repaint on state change only,
  `keys` touches only the changed key's node.
- **HUD editor** (frame `244:1722`): menu mode with the panel hidden; drag/snap/scale,
  writes `anchor/dx/dy/scale` on drop via `void.setHud`.

---

## 10. Performance budgets

| Metric | Budget | How we measure |
|---|---|---|
| Ultralight paint cost per frame | ≤ 0.5 ms at 1080p, HUD only | GL timer queries around the paint |
| Frame cost, menu open | ≤ 2 ms (blur + UI) | same; menu-open fps is not a PVP concern |
| Key press → pixel | same frame | by construction (in-process) |
| Loadout hot-swap | < 1 frame | by construction |
| JVM heap for Ultralight + bundle | ≤ 60 MB | `-Xmx` comparison with mod on/off |
| Warm launch (assets cached) | ≤ 3 s to MC window | `void-core` telemetry |
| In-game bundle size | ≤ 400 KB gz | CI check |

If the paint cost budget fails, first lever is painting the HUD layer only on state change
(Ultralight repaints dirty regions; a static HUD costs ~0).

---

## 11. Anti-cheat posture

- Reads game state; toggles five well-known client-side options. No packet changes, no
  movement changes, no injection into other processes, no third-party plugin system.
- Each mod has `hypixel_safe: safe | grey`. Keystrokes/CPS/Toggle sprint/FPS/Coords/
  Armor/Potions → safe. Fullbright, Hitboxes → grey. "HYPIXEL-READY" badge = every
  enabled mod is `safe`.

---

## 12. Auth & launch (`void-core`)

Port of VOID's `main.ts`, minus Node:

1. Microsoft OAuth → Xbox Live → XSTS → MC token. Refresh token in OS keychain.
2. Resolve 1.8.9 + Legacy Fabric manifests. Parallel downloads, SHA-1 verify, hash cache.
3. Download the **per-OS** `void-client` JAR — `void-client-<version>-<os>-<arch>.jar`,
   embedding the UI bundle plus the Ultralight natives for *that* platform — from our
   release channel; verify signature. `void_core::install::ModPlatform` picks it at
   `prepare` time from the OS the JVM will run as, and `void-pvp prepare --platform`
   overrides that for cross-preparing another machine's install. Note "the OS the JVM
   will run as": on Apple Silicon the game runs on an x64 JVM under Rosetta, so an arm64
   Mac takes the **mac-x64** JAR (§13).
4. Java 8 runtime: detect or fetch Adoptium.
5. JVM args cached by manifest hash; spawn; stdout → ring buffer for the log view.

---

## 13. Known limitations (accepted)

- **Ultralight license (verified Sep 2026)**. Free tier: **$0** while *both* last-fiscal-
  year turnover **and** total funding raised are **< $100k**; on crossing, 30 days to buy
  Pro (**$3,000/yr per application**, < $10M revenue) or distribution rights terminate.
  A credit line from their `NOTICES.txt` must appear in an About/credits screen. "Limited
  performance / feature-set" on the pricing page is not defined in the license; the free
  SDK is the full SDK, minus the `NetworkListener` API (Pro) and custom allocator
  (Enterprise). "PC platforms" = Windows/macOS/Linux.
- **We own the binding.** No maintained Java binding exists (§6.2). ~2 focused weeks up
  front; ongoing cost is tracking Ultralight releases (two in the last three years).
- **CSS subset**: no `backdrop-filter`, no `text-shadow`; some effects need solid fallbacks.
- **No Chrome devtools** in-game; debug via the browser `?debug` harness.
- **Native binaries in the JAR**: Ultralight natives + our JNI lib per OS/arch. The
  ~25 MB in earlier drafts of this line was wrong. Measured, deflated as a JAR stores
  them (`mod/native/README.md`): **20.8 MB** for `windows-x64` alone, **50.3 MB** with
  `macos-x64`, **77.4 MB** with `macos-arm64` too. `WebCore` is 45–80 MB uncompressed
  per platform and is nearly all of it.

  **Decision: one JAR per platform**, not one fat JAR and not a separate natives
  download. `mod/build.gradle`'s `platformJars` task repackages the remapped JAR once per
  staged `mod/native/build*/natives/<os>-<arch>/` tree into
  `void-client-<version>-<os>-<arch>.jar` — a repackage, not a second Loom remap, because
  the classes are byte-identical across platforms and only the natives differ. The base
  `void-client-<version>.jar` carries **no** natives at all (324 KB), which is what keeps
  `./gradlew build` and the test loop fast; CI runs `platformJars` after fetching the
  three natives trees. `void-core` selects one at prepare time (§12.3). A separate
  hash-verified natives download was the alternative and was rejected: it adds a second
  artifact, a second version to keep in step, and a second way for a half-installed
  machine to fail, to save nothing the per-OS split does not already save.
  Linux out of scope for shipping; `linux-x64` is built for the binding's own tests.
- **macOS**: 1.8.9 runs on an x64 JVM under Rosetta on Apple Silicon (LWJGL 2 has no
  official arm64 natives), so mac-x64 Ultralight matches the JVM; arm64 is a bonus.
  macOS OpenGL is deprecated but present; MC uses GL 2.1 via LWJGL2, our GPUDriver must
  work in that context. **Verify in M1.**

## 14. Milestones

| # | Goal | Proves |
|---|---|---|
| **M0** | Monorepo, CI, `void-core` launches vanilla 1.8.9 + Legacy Fabric from CLI | Auth + launch |
| **M1 (gate)** | `mod/native/` JNI binding + GL GPUDriver against Ultralight 1.4; view painting in-game on Win + Mac; one Figma card (Keystrokes tile) rendered from the shared components; paint-cost measured | **Binding feasibility + fidelity + cost + macOS GL** |
| **M2** | `VoidMenuScreen` (blur + mouse release), input forwarding, full Mods panel, Keystrokes settings, HUD editor | Input model |
| **M3** | All 7 HUD + 5 gameplay mods over the bridge | Full bridge |
| **M4** | Loadouts: create / switch / L-cycle / stats; Rust sync; tray switch | Loadout model |
| **M5** | Launcher screens: Play, Mods, Cosmetics, Servers, Friends; updater; signing | Ship |

M1 is the gate, and it is bigger than in v1 of this doc because the binding is ours.
Budget ~2 weeks. If the binding stalls, Ultralight can't hold the card design, or the Mac
GL path fails, we learn it before any product code exists. Fallback: v1 overlay design
(kept in git history).

---

## 15. Decisions log

| Decision | Choice | Why |
|---|---|---|
| In-game rendering | **Ultralight 1.4 in the JVM** | Figma is the contract; in-frame; LabyMod proved the approach on 1.8.9 |
| Ultralight binding | **Write our own** (JNI, C API, GL GPUDriver) | Both public Java bindings are dead and target 1.3 |
| Ultralight licence | Free tier now; Pro ($3k/yr/app) budgeted for when revenue or funding crosses $100k | Verified Sep 2026 |
| Platforms | Windows + macOS | Linux out of scope |
| MC version | 1.8.9 | Hypixel PVP meta |
| Mod loader | Legacy Fabric (Fabric has no 1.8.9) | Mixins; thin re-target to mainline Fabric for 1.21.x later |
| Live state owner | Java, mirrored to Rust | Game keeps working if launcher dies |
| Loadouts | Templates, fully hot-swappable, in-process | Sub-frame switch |
| Hotkeys | Java `KeyBinding`s (R-Shift, L) | Already has the keyboard |
| Transport | localhost WS, Rust server, JSON, state-only | Netty is free in MC; no per-frame data crosses processes |
| HUD positions | anchor + offset + scale | Survives GUI scale / resolution |
| Repo | New monorepo `void-pvp` | Different toolchain and cadence |
| Frontend | One React codebase, two bundles | One design system, two renderers |
| Crosshair | GL, not HTML | Exact center, 20 lines |

## 16. Still open

1. **Cosmetics** (frame `244:217`) — render Mixin + asset pipeline. Own doc.
2. **Friends / Party** (`244:431`, `244:1426`) — reuse VOID's Supabase or start clean?
   Party implies presence + invites.
3. **Servers** (`244:324`) — Rust-side ping; server-bound default loadouts?
4. **"Ask VOID anything" ⌘K** — command palette or LLM?
5. **Signing** — Apple notarization + Authenticode for launcher; the JAR's natives should
   be signed too or Gatekeeper will complain on first load.
6. **Ultralight 1.4.1** — `backdrop-filter`, GPU-accelerated filters and CSS Grid are
   slated for it; unreleased, and we ship 1.4.0b. Don't design around it; adopt if it
   lands.
