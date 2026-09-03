# VOID PVP — Architecture

> Status: **DRAFT for review.** No code exists yet. This document is the contract
> we build against. Figma source of truth:
> `figma.com/design/ks5kpynF3otxC5t1gNKfvg` → page **★ PVP — direction** (`244:2`).

---

## 1. What we are building

A Lunar-style PVP client for Minecraft **1.8.9**, delivered as:

1. **Desktop app** (Tauri + Rust) — the launcher *and* the in-game overlay.
2. **A thin Java mod** (Legacy Fabric) — sensors and a handful of actuators. No UI.

The player launches from the desktop app. The overlay "just works": it appears over
the game window, renders the HUD, and on **Right Shift** takes focus and shows the mod
screens (Figma frames `244:538` → `244:1900`).

### Why this shape

| Alternative | Rejected because |
|---|---|
| All-Java client (draw menus in-game GL — what Lunar and Vape do) | Custom font engine, SDF shaders, hand-drawn widgets; approximates a Figma, never matches it |
| In-game webview (JCEF / Ultralight) | +60 MB, GPL/licensing friction, one more native binary per OS to ship |
| Pure launcher + Modrinth mods | The Figma's 12 "mods" are client features, not community JARs |
| **Overlay + sensor mod (chosen)** | Web UI renders the Figma 1:1; Java stays ~1.5k lines; adding a new MC version means re-targeting a thin mod, not a client |

The Overwolf model. Rust draws, Java senses.

---

## 2. Runtime topology

```
┌──────────────────────────────────────────────────────┐
│  void-pvp desktop  (one Tauri process, two windows)  │
│                                                      │
│   [main]      Launcher UI      frames 244:3 → 431    │
│   [overlay]   Transparent, always-on-top,            │
│               click-through by default               │
│               frames 244:538 → 1900                  │
│                                                      │
│   Rust core:  auth · download · JVM spawn · tray     │
│               WS server · window tracker · loadouts  │
└──────────────┬──────────────────────┬────────────────┘
               │ spawns (PID known)   │ ws://127.0.0.1:<port>
               ▼                      ▼
┌──────────────────────────────────────────────────────┐
│  Minecraft 1.8.9 + Legacy Fabric                     │
│   void-sensor mod:  Mixins → events out,             │
│                     commands in, R-Shift hotkey,     │
│                     borderless enforcement           │
└──────────────────────────────────────────────────────┘
```

Three processes, one owner of truth per concern:

| Concern | Owner |
|---|---|
| Loadout state, persistence | Rust |
| HUD rendering, positions | Overlay webview |
| Game telemetry (fps, keys, ping, coords, armor, potions) | Java → events |
| Gameplay mutation (toggle sprint, fullbright, hitboxes, zoom, crosshair) | Java ← commands |
| Auth, downloads, launch | Rust |
| Where the game window is | Rust (window tracker) |

---

## 3. The 12 mods, classified

The Figma treats these uniformly. The protocol must not.

| Mod | Kind | Runs in | Data |
|---|---|---|---|
| FPS display | HUD | Overlay | `tick.fps` |
| Keystrokes | HUD | Overlay | `keys` (edge-triggered) |
| CPS counter | HUD | Overlay | derived from `keys` in Rust |
| Ping display | HUD | Overlay | `tick.ping` |
| Coordinates | HUD | Overlay | `tick.pos` |
| Armor status | HUD | Overlay | `tick.armor` |
| Potion effects | HUD | Overlay | `tick.fx` |
| Toggle sprint | Gameplay | Java | `cmd` |
| Fullbright | Gameplay | Java | `cmd` |
| Hitboxes | Gameplay | Java | `cmd` |
| Zoom | Gameplay | Java | `cmd` + keybind |
| Crosshair | Gameplay* | Java | `cmd` + style |

\* Crosshair is visually HUD but is drawn in-game: it must sit at the exact GL
center and a 1-frame window-tracking lag would make it visibly drift.

**HUD mods** are pure functions of the event stream. Toggling one never touches Java.
**Gameplay mods** are toggled by command and Java confirms with an `ack`.

---

## 4. Repository layout (new monorepo: `void-pvp`)

Separate from `VOIDLAUNCHERFIVE`. Different toolchain, different release cadence,
different binary. Shares only the design tokens.

```
void-pvp/
├── apps/
│   └── desktop/                 Tauri app
│       ├── src-tauri/           Rust: thin #[tauri::command] wrappers, window setup
│       └── src/                 Web: two Vite entries
│           ├── main/            Launcher UI
│           ├── overlay/         Overlay UI (strict size budget, see §9)
│           └── shared/          Design tokens (ported from VOID c83b777), components
├── crates/
│   ├── void-core/               Auth (MS → Xbox → MC), version manifest, Java runtime,
│   │                            JVM arg builder, process spawn. No Tauri dependency.
│   ├── void-bridge/             WebSocket server, protocol types (serde), event bus
│   ├── void-overlay/            Game-window tracker (per-OS), overlay geometry math
│   └── void-loadout/            Loadout schema, diff/apply, persistence
├── mod/                         Legacy Fabric mod (Gradle, Java 8 target)
│   └── src/main/java/dev/void/sensor/
│       ├── mixin/               One Mixin per sensor / actuator
│       ├── net/                 WS client (Netty — already on MC's classpath)
│       ├── hotkey/              R-Shift → GuiScreen + event
│       └── window/              Borderless enforcement
├── schema/
│   ├── protocol.json            JSON Schema, single source for Rust + Java types
│   └── loadout.json
└── docs/
```

`void-core` has no Tauri dependency so it gets a CLI for free (`void-pvp launch --loadout sword`)
and tests run without a webview. Same lesson as VOID's 761-line `main.ts`: keep logic
out of the shell.

---

## 5. Desktop app (Tauri)

### 5.1 Two windows, one process

| Window | Decorations | Transparent | Always on top | Cursor events | Lifecycle |
|---|---|---|---|---|---|
| `main` | custom (frameless) | no | no | normal | Shown until launch; minimizes to tray after JVM spawn |
| `overlay` | none | **yes** | **yes** | **ignored** by default | Created on JVM spawn, destroyed on `game-closed` |

The overlay is **click-through** (`set_ignore_cursor_events(true)`) until R-Shift. This
is what makes it "just work": the player never knows a second window exists.

### 5.2 Window tracking (`void-overlay`)

Rust spawned the JVM → it knows the PID → it finds the game window by PID. No title
matching, no guessing. Poll geometry at 30 Hz initially; move to OS events once stable.

**Windows**
- `EnumWindows` filtered by `GetWindowThreadProcessId == pid`
- Geometry: `DwmGetWindowAttribute(DWMWA_EXTENDED_FRAME_BOUNDS)` (correct under DPI scaling; `GetWindowRect` is not)
- Event path: `SetWinEventHook(EVENT_OBJECT_LOCATIONCHANGE)` — replaces polling
- Overlay: `WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOPMOST | WS_EX_NOACTIVATE`
- Minimize/alt-tab: hide overlay when game window is not foreground **or** is iconic

**macOS**
- `CGWindowListCopyWindowInfo` filtered by `kCGWindowOwnerPID`. Bounds do **not** require Screen Recording permission (names do — don't read them)
- Overlay `NSWindow`: `level = .screenSaver`-ish (above game, below system UI), `ignoresMouseEvents = true`, `collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]`
- **Fullscreen Spaces**: when MC enters native fullscreen it gets its own Space; `.fullScreenAuxiliary` is what lets the overlay follow it. Test this on day one.
- Retina: all geometry in points, convert once

**Both**
- Exclusive fullscreen breaks overlays everywhere. The Java mod forces borderless
  windowed (§6.4) and reports `window.fullscreen`; if a player re-enables it we show a
  one-line toast, not a modal.

### 5.3 R-Shift focus handoff

Java owns the hotkey (it already has the keyboard; no OS-level hook needed).

```
Player presses R-Shift
  Java:  open VoidBackdropScreen         → MC releases mouse, stops reading WASD,
                                           and the screen renders the game blurred + tinted (§6.5)
  Java:  send { t:"hotkey", id:"overlay" }
  Rust:  overlay.set_ignore_cursor_events(false)
  Rust:  overlay.set_focus()
  Web:   show mods panel (frame 244:538)

Player presses R-Shift again / Esc / clicks "×"
  Web:   emit close
  Rust:  overlay.set_ignore_cursor_events(true)
  Rust:  send { t:"cmd", id:"gui.close" }
  Java:  close VoidBackdropScreen         → MC recaptures mouse, blur gone
```

Both sides must flip on the same keypress or the player walks while dragging HUD tiles.
Java is the single trigger; Rust follows.

**Why the blur lives in Java.** The Figma overlay frames show the game blurred behind
the panel. A Tauri window's `backdrop-filter` only blurs its *own* content — the game is
a different window. So the layering is: Java blurs the game underneath, the overlay
draws the panel on a fully transparent background on top. One composited image, and the
blur toggles with the same `GuiScreen` that releases the mouse — no extra state to sync.
(OS-compositor blur via `window-vibrancy` was considered and rejected: it blurs the whole
overlay region, not just behind the panel, and Win10 acrylic lags on movement.)

### 5.4 Tray & lifecycle

After spawn, `main` hides to tray. Tray menu: Show launcher · Switch loadout ▸ · Quit.
On `game-closed`, overlay is destroyed, `main` returns with session stats (played time,
avg fps — the numbers on the Loadouts frame).

---

## 6. Java mod (`void-sensor`, Legacy Fabric 1.8.9)

### 6.1 Principles

- **No UI.** Not one GuiScreen with widgets. The only screen it opens is
  `VoidBackdropScreen` — no widgets, it just releases the mouse and blurs the frame (§6.5).
- **No config files.** Everything arrives from Rust in the `hello` message.
- **No persistence.** Restart-safe by design; Rust re-sends state on reconnect.
- **Read-only where possible.** Sensors never write game state. Only the five gameplay
  actuators mutate anything, and only via documented client-side toggles.

### 6.2 Sensors (Mixins → events)

| Sensor | Injection point | Emits |
|---|---|---|
| Keys | `KeyBinding.setKeyBindState` / `onTick` | `keys` on change only |
| FPS | `Minecraft.debugFPS` read each tick | into `tick` |
| Ping | `NetHandlerPlayClient` player info for self | into `tick` |
| Position | `EntityPlayerSP` pos/yaw each tick | into `tick` |
| Armor | `InventoryPlayer.armorInventory` durability | into `tick` (on change) |
| Potions | `EntityPlayer.getActivePotionEffects` | into `tick` (on change) |
| Window | `Minecraft.toggleFullscreen`, `resize` | `window` |
| Server | `connect` / `disconnect` | `server` |

`tick` is coalesced to **20 Hz** (one per game tick). `keys` is **edge-triggered** —
8 keys polled at 200 Hz is pure noise on the socket.

### 6.3 Actuators (commands → Mixins)

| Actuator | Mechanism |
|---|---|
| Toggle sprint | `KeyBinding` state override in `EntityPlayerSP.onLivingUpdate` |
| Fullbright | Override `gammaSetting` (client-side, Watchdog-tolerated) |
| Hitboxes | `RenderManager.debugBoundingBox` |
| Zoom | FOV override while keybind held |
| Crosshair | Replace `GuiIngame.renderGameOverlay` crosshair pass with configured style |

Each command gets an `ack` so the overlay toggle can show real state, not optimistic state.

### 6.4 Borderless enforcement

On startup and on `toggleFullscreen`: if fullscreen, switch to borderless windowed at
monitor size, emit `window`. This is exactly what Lunar/Badlion do and why their overlays
work.

### 6.5 Backdrop screen (the one GuiScreen)

`VoidBackdropScreen extends GuiScreen`. Opened on R-Shift, closed on `cmd:gui.close`.
Its `drawScreen`:

1. Copy the main framebuffer to a ¼-resolution FBO
2. Two-pass Gaussian blur shader (horizontal, vertical) on the small FBO — cheap at ¼ res
3. Draw it back full-screen, then a `rgba(0,0,0,0.45)` tint quad

~80 lines of Java plus two ~20-line GLSL shaders. Same technique Vape and Lunar use for
their menus; we use it only for the backdrop and let the overlay draw everything else.
It **does not** draw any widgets, text, or handle any clicks — those go to the overlay.

`doesGuiPauseGame()` returns `false` (multiplayer anyway). Escape closes it and emits
`hotkey:"overlay"` so Rust re-enables click-through.

### 6.6 Transport

Netty WS client (Netty ships with MC). Port and session token come in via JVM system
properties set by Rust: `-Dvoid.port=… -Dvoid.token=…`. Reconnect with backoff; Rust
re-sends the full loadout on every `hello`.

---

## 7. Protocol (`schema/protocol.json`)

One WebSocket. Rust is the server (starts before the JVM). JSON now; MessagePack only if
profiling demands it. Every message has `t` (type) and `v` (protocol version, integer).

### Java → Rust

```jsonc
{ "t":"hello",  "v":1, "mc":"1.8.9", "mod":"0.1.0", "token":"…" }
{ "t":"keys",   "w":1,"a":0,"s":0,"d":1,"lmb":1,"rmb":0,"space":0,"shift":0 }
{ "t":"tick",   "fps":142, "ping":42,
                "pos":{"x":118,"y":64,"z":-212,"yaw":135.2},
                "armor":[{"slot":"helmet","cur":231,"max":363}, …],
                "fx":[{"id":"speed","amp":2,"ms":84000}, …] }
{ "t":"window", "fullscreen":false, "w":1920, "h":1080 }
{ "t":"server", "host":"mc.hypixel.net", "connected":true }
{ "t":"hotkey", "id":"overlay" }               // also "loadout.next" for L-cycle
{ "t":"ack",    "cmd":"fullbright", "on":true }
```

### Rust → Java

```jsonc
{ "t":"loadout", "v":1, "gameplay":{ "toggle_sprint":true, "fullbright":false,
                                     "hitboxes":false, "zoom":{"on":true,"key":"C"},
                                     "crosshair":{"on":true,"style":"dot"} } }
{ "t":"cmd", "id":"fullbright", "on":true }
{ "t":"cmd", "id":"gui.close" }
```

Rules:
- Rust never sends HUD config to Java. Java never sees HUD positions. Clean split.
- Unknown `t` is ignored, unknown fields are ignored — forward compatible.
- `v` mismatch: Rust shows "update client" and refuses launch. Both halves ship together.

---

## 8. Loadouts (`void-loadout`)

> "A loadout is which mods are on, their settings and HUD layout." — Figma 244:1130

A loadout is a **template**: a named snapshot of mod state. Hot-swappable in full.

```jsonc
{
  "id": "sword-pvp", "name": "Sword PvP", "icon": "sword",
  "server": "hypixel", "mc": "1.8.9",
  "mods": {
    "keystrokes":    { "on":true,  "scale":1.0, "opacity":0.85, "keybind":"R_SHIFT" },
    "cps":           { "on":true },
    "toggle_sprint": { "on":true },
    "fullbright":    { "on":false },
    …
  },
  "hud": [
    { "id":"keystrokes", "anchor":"bottom-left",  "dx":32,  "dy":-40, "scale":1.0 },
    { "id":"fps",        "anchor":"top-left",     "dx":20,  "dy":20 },
    { "id":"armor",      "anchor":"right",        "dx":-20, "dy":0 }
  ],
  "stats": { "played_ms": 15600000, "fps_avg": 142 }
}
```

### 8.1 HUD positions are anchor + offset, never absolute pixels

Figma shows `x 32 · y 580`. We store `anchor:"bottom-left", dx:32, dy:-40`. Survives
resolution change, DPI scaling, and dragging the game to another monitor. Absolute
pixels break the first time someone alt-tabs on a laptop docked to a 4K display.

### 8.2 Hot-swap = diff + apply

```
switch(from, to):
  gameplay_diff = diff(from.mods ∩ gameplay, to.mods ∩ gameplay)
  for each changed → send cmd, await ack
  overlay.replace(to.hud, to.mods ∩ hud)          // instant, pure re-render
  persist active_loadout_id
```

In-game **L** cycles loadouts (Java emits `hotkey:"loadout.next"`). Nothing requires a
relaunch. Whether a mod is "on" in the launcher Mods page vs the overlay Mods page is the
same field — the two screens are two views of one store.

### 8.3 Global vs per-loadout

Per-loadout: everything above. Global: account, Java path, RAM, overlay hotkey, theme.
If it changes how the game *plays*, it belongs to the loadout.

---

## 9. Frontend

- **One Vite project, two HTML entries**: `main.html` and `overlay.html`. Shared token
  layer ported from VOID `c83b777` (Tailwind 4 + CSS custom properties).
- **Framework**: React for both, to reuse VOID components and muscle memory. Overlay bundle
  gets a **hard budget: ≤150 KB gz, ≤300 DOM nodes at rest**. If React blows it, the
  overlay entry alone moves to Preact (same JSX, ~3 KB).
- **State**: a single store (Zustand or plain signals — not MobX; the overlay needs
  cheap, frequent updates). Rust pushes deltas via Tauri events; the store is the only
  writer.
- **Rendering discipline for the overlay**: position with `transform: translate()`,
  no layout thrash, no `requestAnimationFrame` loop — repaint only on store change.
  `keys` arrives edge-triggered so the DOM touches only the key that changed.
- **HUD editor** (frame 244:1722) is the overlay in edit mode: snap-to-grid, drag,
  scale handle, writes back `anchor/dx/dy/scale` to the store on drop, not on move.

---

## 10. Performance budgets

| Metric | Budget | How we measure |
|---|---|---|
| Overlay frame cost on the game | ≤ 3% fps (142 → ≥ 138) | PresentMon (Win) / Instruments (Mac) with overlay on/off |
| Key press → HUD paint | ≤ 16 ms | Timestamp in `keys`, `performance.now()` in overlay |
| Loadout hot-swap | ≤ 100 ms to full HUD | Stopwatch around `switch()` |
| Warm launch (assets cached) | ≤ 3 s to MC window | `void-core` telemetry |
| Overlay idle CPU | < 1% | Task Manager / Activity Monitor |
| Mod tick overhead | < 0.1 ms/tick | Java profiler, `tick` build + send |

Measure in M1 before building any of the pretty parts. A transparent WebView over a 3D
game is a real compositing cost; if the budget is blown we need to know before there are
11 screens depending on it.

---

## 11. Anti-cheat posture

- The mod reads game state and flips five well-known client-side toggles. No packets
  modified, no movement changed, no injection into other processes.
- Each mod carries `hypixel_safe: true|false|grey` in its definition; the overlay shows it.
  Keystrokes/CPS/Toggle sprint/FPS/Coords/Armor/Potions → safe. Fullbright → grey.
  Hitboxes → grey. "HYPIXEL-READY" badge on the Play frame = all enabled mods are `safe`.
- We do not ship anything Watchdog bans today, and we don't build a plugin system for
  third parties to add it.

---

## 12. Auth & launch (`void-core`)

Direct port of what VOID does in `main.ts` today, minus Node:

1. Microsoft device-code / OAuth → Xbox Live → XSTS → Minecraft token. Store refresh
   token in OS keychain (`keyring` crate), never on disk in plaintext.
2. Resolve 1.8.9 manifest + Legacy Fabric loader manifest. Download libs/assets in
   parallel (`tokio` + bounded concurrency), SHA-1 verify, cache by hash.
3. Download our mod JAR from our release channel, verify signature.
4. Java 8 runtime: detect or fetch Adoptium.
5. Build JVM args (cache by manifest hash — this is a chunk of the 3 s budget), spawn,
   stream stdout to a ring buffer for the log view.

---

## 13. Known limitations (accepted)

- **OBS Game Capture won't record the overlay.** It hooks MC's GL context; the HUD isn't
  in it. Users need Window/Display capture. Overwolf lives with this. Settings-page note.
- **Exclusive fullscreen is unsupported.** Borderless is enforced; the player can fight
  it but we won't render.
- **Linux** is out of scope (Wayland has no window-positioning API by design).
- **Screenshots via F2** won't include the HUD, same reason as OBS.

---

## 14. Milestones

| # | Goal | Proves |
|---|---|---|
| **M0** | Monorepo, CI, `void-core` launches vanilla 1.8.9 + Legacy Fabric from CLI | Auth + launch pipeline |
| **M1** | Overlay window tracks game on Win + Mac; mod sends `keys`; overlay paints them | Window tracking, transport, **frame budget** |
| **M2** | R-Shift handoff both ways; Keystrokes settings panel; HUD editor drag | Input model |
| **M3** | All 7 HUD mods + 5 gameplay mods + `ack` | Full protocol |
| **M4** | Loadouts: create, switch (hot), L-cycle, stats | Loadout model |
| **M5** | Launcher screens: Play, Mods, Cosmetics, Servers, Friends; tray; updater | Ship |

M1 is the gate. If the compositing cost or macOS fullscreen Spaces don't work, we find
out with ~500 lines written, not 15k.

---

## 15. Decisions log

| Decision | Choice | Why |
|---|---|---|
| Rendering | Tauri overlay, not in-game GL | Figma renders 1:1; mod stays thin |
| Platforms | Windows + macOS | Wayland can't do overlays |
| MC version | 1.8.9 | Hypixel PVP meta; board is pinned to it |
| Mod loader | **Legacy Fabric** (Fabric has no 1.8.9 target) | Mixins; upgrade path to mainline Fabric for 1.21.x is a re-target of a thin mod |
| Hotkey owner | Java | Already has the keyboard; no OS hook |
| Loadouts | Templates, fully hot-swappable | Nothing needs relaunch; diff + apply |
| Transport | localhost WS, Rust server, JSON | Netty is free in MC; JSON until profiled |
| HUD positions | anchor + offset + scale | Survives DPI/monitor changes |
| Repo | New monorepo `void-pvp` | Different toolchain and cadence from VOID |
| Frontend | React, overlay budgeted, Preact fallback | Reuse VOID tokens/components |
| Menu rendering | Overlay (web), **not** in-game GL | Figma is the contract; GL would mean a font engine + SDF shaders + hand-drawn widgets to approximate it |
| Blur behind panel | Java `VoidBackdropScreen`, framebuffer blur | Overlay can't blur another window; ties blur to mouse-release, one trigger |

## 16. Still open

1. **Cosmetics** (frame `244:217`) — capes/hats need a render Mixin in Java *and* an asset
   pipeline. Out of M0–M4; needs its own doc.
2. **Friends / Party** (frames `244:431`, `244:1426`) — reuse VOID's Supabase schema or
   start clean? Party implies presence + invites, which VOID doesn't have.
3. **Servers** (frame `244:324`) — "12 ms to Hypixel" is a Rust-side ping. Do we also want
   server-specific default loadouts (join Hypixel → auto-switch to Sword PvP)?
4. **"Ask VOID anything" (⌘K)** — command palette or LLM? Changes the dependency story.
5. **Signing** — Mac notarization + Windows Authenticode are required for an app that
   sits on top of a game; SmartScreen will flag an unsigned overlay hard.
