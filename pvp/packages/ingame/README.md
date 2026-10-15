# `@void/ingame`

The in-game React entry: the **HUD layer** and the **Right-Shift menu layer**, drawn by
Ultralight 1.4 inside the JVM and composited into Minecraft's own GL frame.

One HTML entry, one JS chunk, one CSS file. It is built into
`mod/src/main/resources/assets/void/ui/`, loaded off the JAR classpath as
`assets/void/ui/index.html`, and it never touches the network.

Owner: **ingame** (CONTRACTS.md). Design: **Cartridge** — Figma `3c0Wgc4ZKFSpm98fWJoARS`, page
`559:25183` "★ v2 · Launch refresh", section B `623:20` (tokens `593:2`, menu `594:2`, search
`595:2`, empty/pinned `596:2`, settings bench `597:2`, keybind capture `598:2`, keybinds `643:2`,
loadouts `600:2`, HUD editor `629:2`, fight HUD `630:2`–`633:2`, 720p `606:2`, 1440p `607:2`).
The v1 frames and `pvp/design/quiet-cell-system.md` describe the screens this replaced. Renderer
limits: `pvp/design/ultralight-notes.md`, enforced by `scripts/check-ultralight.mjs`.

---

## The Right-Shift menu — Cartridge

Every mod is a collectible cartridge on a category shelf: Geist and Geist Mono, keycaps with a
2px lip, LEDs, stepped pixel meters and stepped motion. Flat (no gradients, glass or blur in the
chrome), every surface rounded, the category hues (HUD `#9F8BFF`, PvP `#FF9E7A`, Visual
`#7ADFFF`, Utility `#7AE0B0`) only on live values, LEDs and meters.

| | |
|---|---|
| top | the tab island: VOID · `1` Mods · `2` Loadouts · `3` Keybinds · `4` Friends · `5` Cosmetics · the menu key (closes) |
| centre | the active tab's panel, 1180 × 784; with a mod's settings open it moves to the left edge and the **bench** takes the right |
| bottom | the status strip: mods on and the loadout over one stepped meter per shelf, where you are, `HUD layout` (`H`). Hidden in the 720p compact form |
| world | dimmed (`.menu-layer__dim`) |
| ⌘K palette | yes, over any tab |

**Scale.** The menu is laid out once at 1920 × 1080 and the layer is `zoom`ed by `u` =
min(view / 1920×1080), floored at 0.75 (the 720p frame) — `cart/scale.ts`. `zoom` is a layout
scale, so text is set at its final size (rendering-invariants §10a). Below 1000 design px tall
the strip yields its room (`compact`).

**Mods tab** (`cart/ModsTab.tsx`): chips `All · HUD · PvP · Visual · Utility · Pinned` with
counts, the search field (`/`), and four shelves of six — five cartridges and `+N more` when a
shelf is longer; a chip or `View shelf ›` shows one shelf. A cartridge (`cart/Cartridge.tsx`)
carries the mod's key as a keycap, its LED, its pixel sprite, its name, a live value line
(`cart/values.ts`) and the settings button and switch. The body opens the bench; the switch
toggles in place. Grey mods (`hypixel_safe: grey`) read `▲ Grey on Hypixel` on Hypixel — Java
does not block them, so the switch stays live. Search ranks name starts first and also lists
matching **settings** inside mods. `Pinned` is the active loadout's quick pins (`P` pins).

**The bench** (`cart/Bench.tsx`): the mod's own `ModArt.Preview` over a flat
Day / Night / Nether / Water scene, then every property `modProperties` returns — switches,
segment meters (`CellMeter` restyled), option tiles, swatches, flag chips — and a keybind
capture block that warns before binding over a vanilla default (`cart/keys.ts`, the 1.8.9
defaults: the bridge does not carry the player's own) or another VOID key. Foot: `Move in HUD
layout` (HUD mods), `Reset`, `Pin`.

**Keybinds tab** (`cart/KeybindsTab.tsx`): a keyboard map of `keybindOverview` (menu key, cycle
key, every `keybind`-typed mod setting) over the vanilla defaults, conflicts counted, rebind and
unbind on the selected key, `F` then any key to find it.

**Loadouts tab** (`cart/LoadoutsTab.tsx`): the cartridge case and the focused loadout's rack;
New, Rename, Duplicate, Copy code (through Java's clipboard), Delete in two presses (never the
last), Import a `VOID1.` code; Enter / Insert switches, `L` / `Shift L` step. The frame's
"Auto-insert by server" switch is not drawn: loadouts carry an advisory `server` slug and nothing
switches by server.

**Friends**: the bridge carries no friend or presence data, so Friends says "Not connected yet"
and who is signed in.

**Cosmetics** (`cart/CosmeticsTab.tsx`, `cart/cosmetics-store.ts`, `cart/CrescentWheel.tsx`) —
Wardrobe direction B "Paper doll" (Figma 295:2 section 562:24911) and the Crescent emote wheel
EW-B (588:35340), in Cartridge. The `cosmetics` and `emote` channels feed their own small store;
`equipCosmetic`, `setCosmeticTint`, `setEmoteWheel` and `playEmote` write through it and bind to
what Java returned. The doll is pixel sprites (`doll_front` / `doll_back` plus each worn item's
own sprite where it is worn) — the bridge exposes no GL-rendered preview. Prices are USD from
`price_cents`; a locked item says "Get in launcher Store" and raises a notice — nothing is bought
in game. The Emote wheel view edits the wheel (pick a slot, then an emote) and writes the
`cosmetics` mod's switches; `B` (or "Try it") opens the Crescent wheel over the world from the
menu: point or press 1–8, click to play.

Wiring left for later, because the bridge has no surface for it yet:
* **Hold B with the menu shut.** The wheel is a page overlay; showing it over the HUD layer while
  the player keeps moving needs Java to surface it (a key binding plus a `wheel` open/close event
  and pointer input without a screen). Today it opens from the menu.
* **Outfits** (the frame's "Save look as outfit") — no stored-outfit call exists; not drawn.
* **Get in launcher Store** — no call asks the launcher to open its Store at an item; the button
  raises a notice naming the item and its price instead.

**In a match** (v1 Ship rule 9, unchanged): a hit either way within `COMBAT_WINDOW_MS` of
pressing Right Shift opens the quick panel only — the loadout's pins as numbered cartridges that
`1`–`8` flip, no dim, no palette, closed by the first hit taken.

**Pixel sprites** are one PNG sheet (`cart/sprites.css`, `cart/sprite-map.ts`) generated by
`pnpm sprites` from `scripts/sprites/defs.mjs` at 8× with a transparent gutter — PNG because
inline SVG is [risky] here, 8× because the GL driver samples LINEAR.

In the harness: `?debug` opens the menu, `?debug&ctx=match` the quick panel, and
`?debug&dev=<step>` shows one tour state (`cart/dev.ts`: `search:co`, `settings:keystrokes`,
`capture:toggle_sprint`, `keybinds`, `loadouts`, `hud-editor`, …). The same steps drive the
in-game screenshot tour (`./gradlew runBench -PvoidBench=ui`, mod/README.md).

## Architecture

```
window.__void_native  ──void-shim.js──▶  window.void          ──┐
   (Java, in-process)      (from the JAR)                       │  on('keys'|'tick'|
                                                                │      'server'|'loadout'|
                                                                │      'loadouts'|'setting'|'menu'|
                                                                │      'session'|'settings'|'toast')
                                                                ▼
                                                        src/store/store.ts   (Zustand)
                                                                │
                                    ┌───────────────────────────┴─────────────────────┐
                                    ▼                                                 ▼
                          src/hud/HudLayer.tsx                            src/menu/MenuLayer.tsx
                       always mounted · no input                     mounted while `menu` is true
                 one Cartridge module per HUD mod                    cart/CartMenu: five tabs, bench
                 (src/hud/cart), placed by hud[]                     HUD editor · ⌘K palette
```

| Path | What lives there |
|---|---|
| `src/bridge/protocol.ts` | The one import of `@void/protocol` — types, `installVoidShim`, `createFakeVoid`, `MOD_REGISTRY` |
| `src/bridge/connect.ts` | Picks the real or the fake bridge, subscribes every push channel, owns `__hasFocus` and `__keepsEscape` |
| `src/store/store.ts` | The single Zustand store. `window.void.on(...)` is the only writer of live data |
| `src/store/cps.ts` | Clicks-per-second from the rising edges of `keys.lmb` / `keys.rmb`. Pure |
| `src/store/hud-geometry.ts` | `anchor + dx/dy + scale` ⇄ pixels, snap, clamp, anchor re-pick. Pure |
| `src/hud/` | One widget per HUD mod, bound to the store, drawn by the Cartridge modules in `src/hud/cart/`; `HudLayer` places them |
| `src/toasts/` | `ToastStack`: the `toast` channel and the page's own notices, over both layers |
| `src/cart/` | Cartridge: tokens and primitives, the sprite sheet, the menu shell, the five tabs, the bench, the dev hook |
| `src/menu/` | `MenuLayer` (Escape table), the HUD editor, the property model (`ModSettingsScreen.tsx`), `CellMeter`, previews, keybind overview |
| `src/palette/` | ⌘K: the command set, the fuzzy ranker, and the selection / key routing over `@void/ui`'s palette shell |
| `src/registry.ts` | The overlay's view of the registry: category tags and filter tabs **derived from `mods.json`**, plus the one thing the schema does not carry — the tile grid order |
| `src/styles/overlay.css` | Layer composition (HUD slots, menu layer, editor). The Cartridge styles are `src/cart/*.css` |
| `scripts/check-ultralight.mjs` | CI guard — fails the build on a banned CSS/JS feature |
| `scripts/size.mjs` | CI guard — fails the build over the 400 KB gzip budget |

### The bridge is the only input

`bridge.json` is a closed surface: **eleven** push channels and seventeen calls (the two enums
in `bridge.json` are the list; `VOID_EVENTS` / `VOID_CALLS` and `void-shim.js` mirror them). Because
Ultralight runs inside the JVM, the calls are **synchronous and authoritative** —
`setModSetting` returns the value Java stored after clamping, and that returned value is
what the control binds to. There is no optimistic UI and no `ack` anywhere in this package.

Two of the channels exist because this package needed them:

* **`loadouts`** — the whole library, in full, in library order. `init.loadouts` carries
  complete loadouts, so the Loadouts frame lists and compares every card and the palette
  can offer `Turn on in <other> loadout` without having watched that loadout go past.
  The store treats it as whole-state, and the active loadout's own push wins where both
  describe the same id.
* **`setting`** — one `{id, key, value}` Java changed by itself, such as the
  `keystrokes.keybind` overlay hotkey. Applied exactly as the return value of
  `setModSetting` is, so one key changing does not re-render the world.

`openKeybindCapture` is the one asynchronous call and returns a Promise. Its synchronous
answer is `returns: null` and means *armed*; the key follows on the `__emit` channel as a
call-result envelope. The captured key is not stored by that call, so the UI writes it
back with `setModSetting(id, 'key', captured)`.

### Rendering discipline

* **No animation-frame loop.** The 20 Hz `tick` push is the only clock in the bundle, and
  `scripts/check-ultralight.mjs` fails the build if `requestAnimationFrame` appears.
* **Narrow subscriptions.** Every widget selects the single field it draws, so a `tick`
  that moves the ping does not re-render the FPS chip.
* **`keys` is edge-triggered**, and a key change repaints one keycap's class — React
  reconciles a single `className` attribute, nothing else in the widget moves.
* **Placement is one 2D `transform`, and the scale is not in it.** `placementStyle()` turns
  `anchor + dx/dy` into edge offsets plus a `translate(...)`, memoised so a keypress never
  re-runs the geometry; the size is `zoomStyle()` — a `zoom` — on a box inside it. Scaled text
  is corrupt in this engine (rendering-invariants §10a: `LMB` rendered as `LNB` at 1.3x), and
  `zoom` being a layout scale is what makes the metrics right. Keeping the outer box unzoomed is
  what keeps `dx`/`dy` meaning what Java stores.
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
| `Esc` | Up one level: leaves a focused field, closes the bench, leaves the HUD editor, clears a search, leaves a single shelf, goes from tabs 2–5 to Mods, then closes (MenuLayer; `keepsEscape()` is the same table for Java) |
| `1`–`5` | Jump to a tab (in a match, `1`–`8` flip the quick pins) |
| `← ↑ → ↓` | Walk the cartridges; in the palette, move the selection; in the HUD editor, nudge the selected widget by the grid, or a pixel with `Snap` off |
| `Tab` / `Shift Tab` | Jump a shelf (Mods); cycle the selected widget (HUD editor) |
| `Space` | Toggle the cartridge under the cursor |
| `E` / `Enter` | Open the cursor's settings bench; in the HUD editor `Enter` saves |
| `P` | Pin / unpin the cursor's cartridge (eight at most) |
| `/` | Focus the search field |
| `H` | Open the HUD layout (from the bench, with that mod selected) |
| `L` / `Shift L` | Next / previous loadout on the Loadouts tab; with the menu closed, Java's cycle key |
| `F` | Keybinds tab: the next key pressed is selected |
| `⌘K` / `Ctrl-K` | Toggles the quick palette. **Unverified in game** — whether a keyboard chord carries modifiers could not be settled with synthesised input (rendering-invariants §13a) |

### Loadouts, notices and keybinds

* **Loadouts**: see the Loadouts tab above — the library calls are unchanged (`createLoadout`,
  `renameLoadout`, `duplicateLoadout`, `deleteLoadout`, `exportLoadout`, `importLoadout`,
  clipboard through `void.setClipboard` / `void.getClipboard`).
* **Notices** (`toasts/ToastStack.tsx`): Java's `toast` channel plus the page's own
  (`store.pushToast`), at most three, top right beside the effects module at HUD scale, each with
  a 30-step segment timer and gone after `TOAST_MS` on one `setTimeout`.
* **Keybinds** (`menu/keybind-overview.ts`, drawn by the Keybinds tab): every bound key with
  clashes marked; the cycle key can be rebound or unbound there.

---

## Running it in a browser — `?debug`

There are no devtools in game. The whole bundle runs in an ordinary browser against
`createFakeVoid()` from `@void/protocol`, which plays the part of Java: it owns the
loadout library, clamps what it is given the way `void-loadout` will, drives a 20 Hz
`tick`, turns real key and mouse input into `keys` events, and owns Right Shift.

```bash
pnpm --filter @void/ingame dev
# then open http://localhost:5184/?debug
```

The port is **5184**, not 5183: [`apps/desktop`](../../apps/desktop/README.md)'s
`dev:web` takes 5183 with `--strictPort`, and reviewing the launcher and the in-game
harness side by side is normal. `visual-qa/capture.mjs` and `measure.mjs` default to the
same 5184.

The harness fills the window, like the game's view: at a 1920 × 1080 window the menu lays over
the Cartridge frames 1:1. Behind it is a real in-game still (`design/perf-shots/00-scene.png`).

| Query | Effect |
|---|---|
| `?debug` | Force the fake bridge and open the menu. Implied when `window.__void_native` is absent |
| `?debug&screen=Overlay-Loadouts.png` | Which picture to put behind the UI. Any file in `design/screens/` or `design/perf-shots/` |
| `?debug&dev=search:co` | Show one tour state after mount (`cart/dev.ts`); comma-separate several |
| `?debug&glblur=off` | Drop `data-glblur`, so the panel paints at the denser no-GL-blur alpha |

Once running: Right Shift opens and closes the menu, `⌘K` opens the palette, WASD /
mouse / space drive the keystrokes widget and the CPS counter, and `L` cycles loadouts
with the menu closed. A small **DEBUG** badge marks the fake bridge; it never appears in
game.

The design exports are served by a **dev-server-only** middleware
(`designScreensDevServer` in `vite.config.ts`). Nothing under `design/` is imported at
build time — CONTRACTS.md keeps that directory read-only reference material.

---

## What this package draws, and what `@void/ui` draws

The Cartridge look is this package's own: `src/cart/` (menu, bench, tabs, primitives) and
`src/hud/cart/` (the HUD modules) draw it, scoped under `.cart` so the launcher — which shares
`@void/ui` — never sees a Cartridge value. `@void/ui` still supplies the palette shell, the HUD
editor's selection grips, a few previews' parts (crosshair, keycap colour tables the Cartridge
keys map from) and the base tokens. This package also supplies:

* the **two-layer composition** — where the HUD and menu layers sit, and how a `hud_item`
  becomes a positioned box (`src/styles/overlay.css`, `src/hud/HudLayer.tsx`);
* the **bindings** — which bridge field feeds which prop, and which setting gates it;
* the **behaviour**: drag/snap/scale/undo in the HUD editor, palette selection and key routing,
  the keyboard contract, focus reporting;
* the **data** the schema does not carry: the shelf order (`src/cart/shelves.ts`, derived from
  `MOD_ORDER`), the sprite art (`scripts/sprites`) and the potion colour table
  (`src/hud/format.ts`).

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

The Cartridge redesign is set in **Geist / Geist Mono** (OFL 1.1). In the game those are
served by the native font loader from the mod's own TTFs (`mod/src/main/resources/assets/
void/fonts/geist-*.ttf`), never from the bundle. `src/cart/fonts-dev.css` + `src/cart/fonts/`
carry woff2 copies for the browser harness only and must not be imported by shipped code.
Both are generated by `node scripts/fetch-geist.mjs`.

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

The built `index.html` also gets `<script src="./void-shim.js"></script>` as the first
element of `<head>`, injected by the `injectVoidShim()` plugin. That file is **not** an
asset of this package — the mod commits it at `assets/void/shim/void-shim.js` and Gradle
copies it next to this bundle inside the JAR — which is exactly why the tag is injected
rather than written into `index.html`, where Vite would try to resolve a missing file. The
plugin is build-only: in the harness there is no Java and `createFakeVoid()` installs
`window.void` itself.

`emptyOutDir` is on and safe. The only things in that directory are this bundle's own
outputs; the shim lives one level up in `assets/void/shim/` and Gradle's copy lands in
`build/resources/main/`, never in the source tree, so neither build can delete the other's
work whichever runs first (CONTRACTS.md, "The bridge shim").

The directory is gitignored: it is a build output, and **mod** must never hand-edit it
(CONTRACTS.md).

### Size budget — 400 KB gzipped (§10)

`pnpm size` gzips every emitted file and fails over budget. Current result:

```
  file                                             raw         gzip
  ----------------------------------------  ----------   ----------
  assets/index-*.js                           264.6 KB      84.3 KB
  assets/bricolage-grotesque-800-*.woff2       25.0 KB      25.1 KB
  assets/dm-mono-500-*.woff2                   16.4 KB      16.4 KB
  assets/dm-mono-400-*.woff2                   16.1 KB      16.1 KB
  assets/outfit-600-*.woff2                    15.2 KB      15.1 KB
  assets/outfit-400-*.woff2                    15.1 KB      15.0 KB
  assets/outfit-500-*.woff2                    14.3 KB      14.3 KB
  assets/style-*.css                           55.9 KB       9.4 KB
  index.html                                    0.5 KB       0.3 KB
  ----------------------------------------  ----------   ----------
  TOTAL                                       423.0 KB     195.8 KB   (48.9% of budget)
```

The six bundled faces are 102 KB of that and do not compress further — they are already
woff2. (`fonts-display.css`'s `opsz 96` cut of Bricolage Grotesque is *not* among them:
`@void/ui` deliberately leaves it out of `fonts.css`, and this bundle sets nothing at
display size.) React + React DOM + Zustand are most of the JS. There is ~204 KB of
headroom.

---

## Tests

```bash
pnpm --filter @void/ingame test
pnpm --filter @void/ingame check    # typecheck + Ultralight guard + tests
```

| File | Covers |
|---|---|
| `test/cps.test.ts` | Edge triggering, the half-open window, purity, the bounded ring |
| `test/hud-geometry.test.ts` | anchor → transform, the screen ⇄ placement round-trip through all nine anchors, snap, clamp, anchor re-pick, and that no transform is ever 3D |
| `test/meter.test.tsx` | the numbers `overlay.css` animates the meter by — the marker's `--live` index, the fill's `--d` stagger and the §5.3 `--bleed` strengths. A transition cannot be asserted in jsdom; getting one of these wrong is silent |
| `test/fuzzy.test.ts` | Palette ranking, including the frame's own `fullb` ordering |
| `test/store.test.ts` | Reducers against the real `createFakeVoid()`: CPS through the store, clamped writes, `setHud` round-trip, `__hasFocus` |
| `test/cartridge.test.tsx` | The Cartridge shell, shelves, search, the bench and keybind capture, live values, the dev hook and rule 9's quick panel |
| `test/screens.test.tsx` | HUD layer, the bench's property coverage for every mod, Loadouts/Friends tabs, the HUD editor, the palette, the watermark |
| `test/cosmetics.test.tsx` | The wardrobe against the fake bridge's cosmetics rules: wear/take off, locked → launcher Store in USD, tints, loading, the emote wheel editor and the Crescent wheel |
| `test/library.test.tsx` | The loadout library, notices, quick pins and the keybind overview / Keybinds tab |
| `test/registry.test.ts` | That category, label and filter tabs are *derived* from `mods.json` and not re-transcribed, and that `category` stays distinct from `kind` |

The store and screen tests run against the **real fake bridge** from `@void/protocol`,
not a hand-written mock, so they exercise the actual call and event shapes of
`bridge.json`.

---

## Known gaps

The first four entries here were contract questions for **core**; all four were answered
in `schema/` and the workarounds are gone. They are kept as a record of what moved.

1. ~~**No loadout-library accessor on the bridge.**~~ **Resolved.** `bridge.json` gained a
   `loadouts` event carrying the whole library, and `protocol.json`'s `init.loadouts` now
   carries *full* loadouts rather than summaries. `store.library` is the real library in
   game, not just the loadouts this session happened to see.
2. ~~**Three settings the Mod settings frame draws are not in `mods.json`.**~~
   **Resolved.** `keystrokes.corner_radius` (0–20 px, default 8), `keystrokes.key_color`
   (`shell|raised|pill|sky|teal`, default `shell`) and `keystrokes.pressed_color`
   (`accent|sky|warn|fear|teal`, default `accent`) are in `keystrokes_settings`, with the
   frame's defaults. Java clamps them like any other setting instead of dropping them.
3. ~~**`mods.json` labels disagree with the frames.**~~ **Resolved.** The registry says
   `FPS display`, `CPS counter` and `Ping display`; `modLabel` is a straight lookup and
   the three overrides are deleted.
4. ~~**No category taxonomy in the schema.**~~ **Resolved.** Every registry entry carries
   `category` (`hud | pvp | visual | utility`), matching frame 244:538 tag for tag.
   `MOD_CATEGORY` and `FILTER_TABS` are derived from it; the hand-written map is gone.
5. **Friends is not connected.** No bridge event or call carries party, presence or queue
   state, and §16.2 is still open; the old presentational Party screen is gone and the Friends
   tab says so. Vanilla's own bindings are not on the bridge either, so the keybind conflict
   check compares against the 1.8.9 **defaults** and says "(vanilla default)".
6. ~~**`Outfit` ships at 400/500/600** and the design sets keycap and CTA labels at 700,
   so those weights are synthesised.~~ **Resolved.** `@void/ui`'s `fonts.css` now declares
   an explicit `font-weight: 700` `@font-face` pointing at the 600 instance, so a 700 rule
   resolves to a real bundled face instead of a synthetic bold. Nothing here changes; the
   labels stay at 700 in the CSS.
7. ~~**Icons are inline SVG.**~~ **Resolved.** The bar icons are a PNG sprite
   (`styles/icons.css`) and every Cartridge mark is a pixel sprite from one PNG sheet or a few
   boxes; nothing in the menu draws an SVG stroke.
