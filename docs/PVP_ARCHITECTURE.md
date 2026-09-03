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
| **Ultralight inside the JVM (chosen)** | ~10 MB, renders straight into MC's GL context, proven on 1.8.9 by LabyMod 4 via `ultralight-java`. Same React/CSS as the launcher |

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

| Mod | Kind | Data source / effect |
|---|---|---|
| FPS display | HUD | `Minecraft.debugFPS` |
| Keystrokes | HUD | `KeyBinding` state, edge-triggered |
| CPS counter | HUD | derived from clicks in JS |
| Ping display | HUD | own `NetworkPlayerInfo.responseTime` |
| Coordinates | HUD | `EntityPlayerSP` pos/yaw |
| Armor status | HUD | `InventoryPlayer.armorInventory` durability |
| Potion effects | HUD | `getActivePotionEffects` |
| Toggle sprint | Gameplay | `KeyBinding` override in `onLivingUpdate` |
| Fullbright | Gameplay | `gammaSetting` override (client-side, Watchdog-tolerated) |
| Hitboxes | Gameplay | `RenderManager.debugBoundingBox` |
| Zoom | Gameplay | FOV override while key held |
| Crosshair | Gameplay* | replaces vanilla crosshair pass; drawn in GL at exact center |

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
│   └── src/main/java/dev/void/client/
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

`void-core` has no Tauri dependency: CLI for free (`void-pvp launch --loadout sword`),
tests without a webview. Same lesson as VOID's 761-line `main.ts`.

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

- One `View` sized to the framebuffer, transparent background, GPU renderer via
  `ultralight-java`'s GL driver — it renders directly into MC's GL context. No CPU
  readback, no `glTexSubImage2D` of full frames.
- Rendered at the end of `GuiIngame.renderGameOverlay` (HUD layer) and again in
  `VoidMenuScreen.drawScreen` (menu layer) — same view, the React app decides what's
  visible. Depth test off, straight-alpha blend.
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
void.on('keys',   (k: { w:0|1, a, s, d, lmb, rmb, space, shift }) => …)   // edge-triggered
void.on('tick',   (t: { fps, ping, pos:{x,y,z,yaw}, armor:[…], fx:[…] }) => …) // 20 Hz
void.on('server', (s: { host, connected }) => …)
void.on('loadout',(l: Loadout) => …)          // on hello + on switch (from Rust or L key)
void.on('menu',   (open: boolean) => …)

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
{ "t":"hello",   "v":1, "mc":"1.8.9", "mod":"0.1.0", "token":"…" }
{ "t":"state",   "loadout":"sword-pvp", "patch":{ "mods.fullbright.on":true } }   // on change
{ "t":"hud",     "loadout":"sword-pvp", "items":[ {id, anchor, dx, dy, scale} ] }  // on drop
{ "t":"session", "fps_avg":142, "played_ms":812000, "server":"mc.hypixel.net" }  // every 60 s + on exit
{ "t":"server",  "host":"mc.hypixel.net", "connected":true }

// Rust → Java
{ "t":"init",    "v":1, "loadout":{…}, "loadouts":[…summaries], "settings":{…} }
{ "t":"loadout", "loadout":{…} }             // launcher/tray switched it
{ "t":"settings","settings":{…} }
```

Rules: unknown `t`/fields ignored (forward compatible). `v` mismatch → launcher refuses
to launch and prompts update; mod and launcher ship together.

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
  - ✅ flexbox, grid, `border-radius`, `box-shadow`, gradients, transforms, transitions,
    `@font-face`, custom properties
  - ❌ `backdrop-filter` — the panel backdrop blur is GL (§6.4); *glass inside cards* is
    the fidelity risk and is what M1 tests
  - JS: JavaScriptCore, ES2019-ish. Babel target accordingly. No Chrome devtools — we
    ship a `?debug` mode that renders the in-game bundle in a normal browser with a
    fake `window.void`.
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
3. Download `void-client` JAR (which embeds the UI bundle + Ultralight natives for the
   host OS) from our release channel; verify signature.
4. Java 8 runtime: detect or fetch Adoptium.
5. JVM args cached by manifest hash; spawn; stdout → ring buffer for the log view.

---

## 13. Known limitations (accepted)

- **Ultralight license**: free while company revenue < $100k/yr; paid above. Budget it.
- **CSS subset**: no `backdrop-filter`; a few effects may need solid fallbacks.
- **No Chrome devtools** in-game; debug via the browser `?debug` harness.
- **Native binaries in the JAR**: Ultralight ships per-OS natives (Win x64, macOS
  x64/arm64) — JAR is ~25 MB. Linux out of scope.
- **macOS OpenGL is deprecated** but present; 1.8.9 uses GL 2.1 via LWJGL2 and Ultralight's
  GL driver targets 3.2 core. **Verify in M1** on Apple Silicon.

## 14. Milestones

| # | Goal | Proves |
|---|---|---|
| **M0** | Monorepo, CI, `void-core` launches vanilla 1.8.9 + Legacy Fabric from CLI | Auth + launch |
| **M1 (gate)** | Ultralight view painting in-game on Win + Mac; one Figma card (Keystrokes tile) rendered from the shared components; paint-cost measured | **Fidelity + cost + macOS GL** |
| **M2** | `VoidMenuScreen` (blur + mouse release), input forwarding, full Mods panel, Keystrokes settings, HUD editor | Input model |
| **M3** | All 7 HUD + 5 gameplay mods over the bridge | Full bridge |
| **M4** | Loadouts: create / switch / L-cycle / stats; Rust sync; tray switch | Loadout model |
| **M5** | Launcher screens: Play, Mods, Cosmetics, Servers, Friends; updater; signing | Ship |

M1 is the gate. If Ultralight can't hold the card design or the Mac GL path fails, we
learn it with a few hundred lines written. Fallback if it fails: v1 overlay design (kept
in git history).

---

## 15. Decisions log

| Decision | Choice | Why |
|---|---|---|
| In-game rendering | **Ultralight in the JVM** | Figma is the contract; in-frame; LabyMod-proven on 1.8.9 |
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
6. **Ultralight licensing** — confirm current terms before M1.
