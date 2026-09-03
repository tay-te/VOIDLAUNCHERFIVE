# `@void/ingame`

The in-game React entry: the **HUD layer** and the **Right-Shift menu layer**, drawn by
Ultralight 1.4 inside the JVM and composited into Minecraft's own GL frame.

One HTML entry, one JS chunk, one CSS file. It is built into
`mod/src/main/resources/assets/void/ui/`, loaded off the JAR classpath as
`assets/void/ui/index.html`, and it never touches the network.

Owner: **ingame** (CONTRACTS.md). Design source: `pvp/design/README.md` frames `244:538`
(Mods), `244:834` (Mod settings), `244:1130` (Loadouts), `244:1426` (Party), `244:1722`
(HUD layout) and `244:1900` (Quick palette). Renderer limits: `pvp/design/ultralight-notes.md`.

---

## Architecture

```
window.__void_native  ──installVoidShim()──▶  window.void  ──┐
   (Java, in-process)                                        │  on('keys'|'tick'|
                                                             │      'server'|'loadout'|'menu')
                                                             ▼
                                                     src/store/store.ts   (Zustand)
                                                             │
                                    ┌────────────────────────┴────────────────────────┐
                                    ▼                                                 ▼
                          src/hud/HudLayer.tsx                            src/menu/MenuLayer.tsx
                       always mounted · no input                     mounted while `menu` is true
                       7 widgets, placed by hud[]                  Mods · Mod settings · Loadouts
                                                                   Party · HUD editor · ⌘K palette
```

| Path | What lives there |
|---|---|
| `src/bridge/protocol.ts` | The one import of `@void/protocol` — types, `installVoidShim`, `createFakeVoid`, `MOD_REGISTRY` |
| `src/bridge/connect.ts` | Picks the real or the fake bridge, subscribes the five channels, owns `__hasFocus` |
| `src/store/store.ts` | The single Zustand store. `window.void.on(...)` is the only writer of live data |
| `src/store/cps.ts` | Clicks-per-second from the rising edges of `keys.lmb` / `keys.rmb`. Pure |
| `src/store/hud-geometry.ts` | `anchor + dx/dy + scale` ⇄ pixels, snap, clamp, anchor re-pick. Pure |
| `src/hud/` | The seven HUD mods, bound to the store; `HudLayer` places them |
| `src/menu/` | The five overlay screens |
| `src/palette/` | ⌘K: the command set and the fuzzy ranker |
| `src/registry.ts` | The Figma-side view of the registry: filter category, grid order, panel copy |
| `src/styles/overlay.css` | Layer composition and screen layout. Every *component* style is `@void/ui`'s |
| `scripts/check-ultralight.mjs` | CI guard — fails the build on a banned CSS/JS feature |
| `scripts/size.mjs` | CI guard — fails the build over the 400 KB gzip budget |

### The bridge is the only input

`bridge.json` is a closed surface: five push channels and six calls. Because Ultralight
runs inside the JVM, the calls are **synchronous and authoritative** — `setModSetting`
returns the value Java stored after clamping, and that returned value is what the control
binds to. There is no optimistic UI and no `ack` anywhere in this package.

`openKeybindCapture` is the one exception and returns a Promise; the captured key is not
stored by that call, so the UI writes it back with `setModSetting(id, 'key', captured)`.

### Rendering discipline

* **No animation-frame loop.** The 20 Hz `tick` push is the only clock in the bundle, and
  `scripts/check-ultralight.mjs` fails the build if `requestAnimationFrame` appears.
* **Narrow subscriptions.** Every widget selects the single field it draws, so a `tick`
  that moves the ping does not re-render the FPS chip.
* **`keys` is edge-triggered**, and a key change repaints one keycap's class — React
  reconciles a single `className` attribute, nothing else in the widget moves.
* **Placement is one 2D `transform`.** `placementStyle()` turns `anchor + dx/dy + scale`
  into edge offsets plus a `translate(...) scale(...)`, memoised so a keypress never
  re-runs the geometry.
* `armor` and `fx` are replaced only on the ticks that carry them — `bridge.json` says an
  absent field means unchanged.

### Focus reporting

`window.void.__hasFocus()` is overridden by this package to answer *"is a text field
eating the keyboard right now?"*, read straight off `document.activeElement` so it can
never be a render behind. `VoidMenuScreen` asks it before acting on Escape (§6.3):
with the search field focused, Escape leaves the field; a second Escape closes the menu.
A focused checkbox or button is **not** text focus, so Escape still closes.

### Keyboard

| Key | Effect |
|---|---|
| **Right Shift** | Opens / closes the screen. Handled in Java as a `KeyBinding`; this package **never binds it** (the fake bridge does, because in the harness it plays Java) |
| `Esc` | Leaves a focused text field, else exits the HUD editor, else closes the menu |
| `⌘K` / `Ctrl-K` | Toggles the quick palette over whatever screen is up |
| `← ↑ → ↓` | Walks the mod grid (3 columns); in the palette, moves the selection |
| `Enter` | Toggles the highlighted mod; in the palette, runs the highlighted action |
| `⌘↵` / `Ctrl-↵` | In the palette, opens the action's settings instead of running it |
| `L` | Cycles loadouts. Java's key, not ours |

Screens other than Mods are reached from the palette (`Loadouts`, `Party`,
`Edit HUD layout`), from a tile's settings pane (`Edit position` → HUD editor), or by
double-clicking a tile (→ that mod's settings screen). The frames draw no screen
switcher, so none was invented.

---

## Running it in a browser — `?debug`

There are no devtools in game. The whole bundle runs in an ordinary browser against
`createFakeVoid()` from `@void/protocol`, which plays the part of Java: it owns the
loadout library, clamps what it is given the way `void-loadout` will, drives a 20 Hz
`tick`, turns real key and mouse input into `keys` events, and owns Right Shift.

```bash
pnpm --filter @void/ingame dev
# then open http://localhost:5183/?debug
```

The harness renders at the authored **1300 × 820** frame size and puts the matching Figma
export behind the UI, so the result can be compared 1:1 with `design/screens/*.png`.

| Query | Effect |
|---|---|
| `?debug` | Force the fake bridge and open the menu. Implied when `window.__void_native` is absent |
| `?debug&screen=Overlay-Loadouts.png` | Which export to put behind the UI. Any file in `design/screens/` |
| `?debug&glblur=off` | Drop `data-glblur`, so the panel paints at the denser no-GL-blur alpha |

Once running: Right Shift opens and closes the menu, `⌘K` opens the palette, WASD /
mouse / space drive the keystrokes widget and the CPS counter, and `L` cycles loadouts
with the menu closed. A small **DEBUG** badge marks the fake bridge; it never appears in
game.

The design exports are served by a **dev-server-only** middleware
(`designScreensDevServer` in `vite.config.ts`). Nothing under `design/` is imported at
build time — CONTRACTS.md keeps that directory read-only reference material.

---

## Consuming `@void/ui` and `@void/protocol`

Both are `workspace:*` dependencies, imported by their real package names. They are
resolved to **source** rather than to `dist/` by aliases in `vite.config.ts`,
`vitest.config.ts` and `tsconfig.json`:

| Specifier | Resolves to |
|---|---|
| `@void/ui` | `../ui/src/index.ts` |
| `@void/ui/tokens.css`, `/fonts.css` | `../ui/src/…` |
| `@void/ui/styles.css` | `src/styles/void-ui.css`, which `@import`s `../ui/src/styles/0*.css` in order |
| `@void/protocol` | `../protocol/src/index.ts` |

Both packages are written by sibling owners in this monorepo, and their `exports` maps
point at build output that may not have been produced yet. Aliasing to source means this
bundle always compiles against what they have actually written and never needs another
package's build to have been run first. Nothing in either package is edited from here.

Two consequences worth knowing:

* `tsc` also sees their files, so `noUnusedLocals` / `noUnusedParameters` are off here —
  unused-symbol diagnostics in a sibling package are that package's CI signal, not this
  one's.
* `pnpm build` for the JAR does **not** require `pnpm --filter @void/ui build`.

`@void/ui` also supplies the three OFL faces (Bricolage Grotesque ExtraBold, Outfit
400/500/600, DM Mono 400/500). Vite fingerprints and copies them into `assets/`; nothing
is fetched at runtime.

---

## Ultralight constraints, and how they are enforced

`scripts/check-ultralight.mjs` runs on every build and greps **both** `src/` and the
emitted bundle — a rule broken inside a dependency has to fail too. 22 rules, from
`design/ultralight-notes.md`:

| § | Banned | Why |
|---|---|---|
| §1 | `backdrop-filter` | Parsed and dropped; nothing behind is blurred |
| §2 | `mix-blend-mode`, `background-blend-mode` | Separable blend modes are not implemented |
| §3 | `text-shadow`, `-webkit-text-stroke` | Dropped, or they smear the glyph atlas |
| §4 | `perspective`, `preserve-3d`, `backface-visibility`, `*3d()`, `translateZ`, `rotateX/Y` | 2D transforms only |
| §5 | `getContext('webgl')` | No WebGL context exists |
| §6 | `<video>`, `<audio>` | No media pipeline |
| §7 | `font-variation-settings`, `display: grid`, `grid-template-*`, `position: sticky` | Unreliable or unsupported |
| — | `fetch()`, `XMLHttpRequest`, `WebSocket` | The bundle runs off the JAR classpath with no network; the WS link is Java ⇄ Rust, never the page |
| §9 | `requestAnimationFrame` | The 20 Hz `tick` push is the only clock |

One allowance, and it is deliberate: `@void/ui` writes
`backdrop-filter: blur(var(--blur-panel))` and lets the **token** decide. Under
`data-renderer="ultralight"` every `--blur-*` resolves to `0px`, so the property is inert
even on a build that claims to honour it. That is exactly the discipline §1 asks for —
never branch on `@supports`, read the radius through a token — so a token-driven or
explicitly-zero blur passes the guard and a hard-coded radius does not.

The design decisions that follow from those rules, all visible in the output:

* **Panels are semi-opaque solids.** The game behind them is blurred by a GL pass in the
  host before the Ultralight surface is composited. `<html data-glblur="on">` tells the
  page that pass is running, and the panel sits at `rgba(10,11,12,0.94)`; without it,
  `0.97`. The quick-palette dim is one flat layer, never a 3 px blur.
* **No noise.** The authored 32.64 px `mix-blend-mode: overlay` grain is dropped and the
  base surfaces are nudged ~1 % lighter instead, which is fallback 1 of §2.
* **Every HUD readout sits on a chip.** That is how this design solves legibility over
  live game pixels, and it is why no `text-shadow` is needed anywhere.
* **The selection frame is a solid border**, not a dashed one: dash phase on rounded
  corners is inconsistent in Ultralight. The `--accent-tint-faint` fill and the four grips
  carry the selection read.
* **Panel enter motion is `opacity` + `translateY(8px)` + `scale(0.98)`** — 2D only.
* **The type ramp is snapped to whole pixels** by `@void/ui`'s ultralight token layer.
* **The grid, the sparkline and the crosshair are plain boxes.** No canvas anywhere.
* **The crosshair is GL, not HTML** (§3 of the architecture): the HTML one renders only
  under the fake bridge, so the harness matches the frames without double-drawing in game.

---

## Build

```bash
pnpm --filter @void/ingame build
```

`vite build` → `mod/src/main/resources/assets/void/ui/`, then the two guards. The output
is `index.html` + hashed `assets/`, **all URLs relative** (`base: './'`) because there is
no origin behind a classpath loader — and the `crossorigin` attribute Vite emits is
stripped for the same reason. Target is **ES2022** with real ES modules, which Ultralight
1.4's JavaScriptCore supports.

The directory is gitignored: it is a build output, and **mod** must never hand-edit it
(CONTRACTS.md).

### Size budget — 400 KB gzipped (§10)

`pnpm size` gzips every emitted file and fails over budget. Current result:

```
  file                                             raw         gzip
  ----------------------------------------  ----------   ----------
  assets/index-*.js                           261.7 KB      83.6 KB
  assets/bricolage-grotesque-800-*.woff2       25.1 KB      25.1 KB
  assets/dm-mono-500-*.woff2                   16.3 KB      16.3 KB
  assets/dm-mono-400-*.woff2                   16.1 KB      16.1 KB
  assets/outfit-600-*.woff2                    15.2 KB      15.1 KB
  assets/outfit-400-*.woff2                    15.1 KB      14.9 KB
  assets/outfit-500-*.woff2                    14.3 KB      14.3 KB
  assets/style-*.css                           51.2 KB       8.6 KB
  index.html                                    0.4 KB       0.3 KB
  ----------------------------------------  ----------   ----------
  TOTAL                                       415.4 KB     194.3 KB   (48.6% of budget)
```

The three font families are 86 KB of that and do not compress further — they are already
woff2. React + React DOM + Zustand are most of the JS. There is ~205 KB of headroom.

---

## Tests

```bash
pnpm --filter @void/ingame test     # 84 tests
pnpm --filter @void/ingame check    # typecheck + Ultralight guard + tests
```

| File | Covers |
|---|---|
| `test/cps.test.ts` | Edge triggering, the half-open window, purity, the bounded ring |
| `test/hud-geometry.test.ts` | anchor → transform, the screen ⇄ placement round-trip through all nine anchors, snap, clamp, anchor re-pick, and that no transform is ever 3D |
| `test/fuzzy.test.ts` | Palette ranking, including the frame's own `fullb` ordering |
| `test/store.test.ts` | Reducers against the real `createFakeVoid()`: CPS through the store, clamped writes, `setHud` round-trip, `__hasFocus` |
| `test/screens.test.tsx` | A render smoke test per screen, asserting the frames' verbatim copy — footer hints included |

The store and screen tests run against the **real fake bridge** from `@void/protocol`,
not a hand-written mock, so they exercise the actual call and event shapes of
`bridge.json`.

---

## Known gaps, raised rather than worked around

These are contract questions for **core** (`schema/` is written by core and read by
everyone; a change there is a contract change).

1. **No loadout-library accessor on the bridge.** Rust sends `init.loadouts` to Java, but
   `bridge.json` gives JS no way to read the list — and the Loadouts frame lists every
   loadout. The store's `library` is filled from the fake bridge's `getLoadouts()` in the
   harness and otherwise grows from the `loadout` events actually seen, so in game it
   starts as just the active loadout.
2. **Three settings the Mod settings frame draws are not in `mods.json`:**
   `keystrokes.corner_radius`, `keystrokes.key_color`, `keystrokes.pressed_color`. They
   are written through `setModSetting` under those keys (Java clamps rather than throws)
   and flagged here for reconciliation.
3. **`mods.json` labels disagree with the frames** for three mods: the registry says
   `FPS`, `CPS`, `Ping`; the panels read `FPS display`, `CPS counter`, `Ping display`.
   `src/registry.ts` overrides the three, since this is panel copy.
4. **No category taxonomy in the schema.** The Mods panel filters across
   All / HUD / PvP / Visual / Utility, which is a product split; `mods.json` only carries
   `kind` (hud | gameplay), a data-direction split. The mapping is in `src/registry.ts`,
   read off frame 244:538 tile by tile.
5. **Party is presentational.** No bridge event or call carries party, presence or queue
   state, and §16.2 is still open. Nothing on that screen is wired, and no bridge call was
   invented for it.
6. **`Outfit` ships at 400/500/600**, and the design sets keycap labels and CTA labels at
   700. Those weights are synthesised. If Ultralight's synthesis reads badly, `@void/ui`
   should add the 700 instance.
7. **Icons are inline SVG.** `ultralight-notes.md` §7 rates that **[risky]** and asks for
   a 2×/3× PNG sprite. `@void/ui`'s `Icon` already resolves through `setIconRenderer`, so
   the swap is one call at boot once the sprite exists — no call site changes.
