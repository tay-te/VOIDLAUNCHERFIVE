# webcap

Frame-exact 4K capture of the VOID launcher's web UI, for compositing in Blender:
PNG sequences of the launcher's own CSS animations, and layered transparent stills
for floating screens apart in 3D.

Plain Node 24 (built-in `fetch`, `WebSocket`, `zlib`) and a local Chrome, driven over
the DevTools protocol. No npm packages.

## Setup

```sh
cd pvp/apps/desktop && npm run dev:web      # serves http://127.0.0.1:5183 (Tauri API mocked)
cd reel/tools/webcap                        # in another shell
node webcap.mjs screens                     # list the screen names
```

Chrome is found at the usual install paths; otherwise pass `--chrome <exe>` or set
`WEBCAP_CHROME`. Every run starts a private headless Chrome with a throwaway profile
and closes it afterwards.

## Commands

```sh
# a 4K still of Home, 2.5 s after it mounted
node webcap.mjs still --screen home --at 2.5 --out out/home.png

# 4 s of Coming soon / Modpacks at 30 fps, "Notify me" clicked at 1.5 s
node webcap.mjs seq --screen modpacks --fps 30 --seconds 4 \
  --actions examples/notify-click.json --out out/modpacks/

# Home with the Friends drawer open, split into layers
node webcap.mjs layers --screen home+friends --at 2.5 \
  --layers examples/home-layers.json --crop --out out/home-layers/

node webcap.mjs composite out/home-layers/      # re-stack layers, diff against full.png
node webcap.mjs compare out/runA/ out/runB/     # determinism: frame by frame
node webcap.mjs diff a.png b.png
```

Common options:

| option | default | |
|---|---|---|
| `--url` | `http://127.0.0.1:5183/` | add `?preview=first` etc. for the mock's preview states |
| `--screen` | (as loaded: home) | screen at t = 0, e.g. `modpacks`, `home+friends` |
| `--nav-via` | `store` | `click` presses the real navbar controls instead |
| `--nav-transitions` | off | let the CSS transitions `--screen` starts (the navbar tab highlight, 300 ms) play from t = 0 instead of ending them |
| `--actions` | none | timeline JSON, see below |
| `--size` / `--scale` | `1920x1080` / `2` | CSS viewport and device pixel ratio: 3840×2160 out |
| `--fps`, `--seconds` or `--frames` | `30`, `4` | `seq` only |
| `--at` | `2.5` | `still` / `layers`: virtual time of the shot |
| `--preroll` | `0` | virtual seconds to run before t = 0 (actions stay relative to t = 0) |
| `--tick` | `60` | rAF rate while stepping up to a still's `--at` or through a preroll |
| `--seed`, `--epoch`, `--tz` | `1`, `2026-10-24T12:00:00Z`, system | `Math.random` seed, what `Date.now()` says at page load, time zone |
| `--trace ".a,.b"` | none | `seq`: per frame computed opacity/transform/filter/box of each selector's first match into `sequence.json` |
| `--transparent` | off | `still`: transparent page background |
| `--crop` | off | `layers`: also write each layer cropped to its bbox |
| `--no-promote`, `--strip-ancestors` | | `layers`: isolation tuning (see Layers) |
| `--gpu` | off | GPU rendering: 2–3× faster, not bit-exact (see Limits) |
| `--keep-banner` | off | keep the "Browser preview" banner (hidden by default) |
| `--headful`, `--chrome-args="--x --y"` | | debugging |
| `--slow <ms>` | `0` | `seq` self-test: really wait between frames; output must not change |

### Why 1920×1080 at scale 2

The launcher's layout is fluid with fixed-size components (designed at 1600×980,
minimum 1280×800). At a native 3840×2160 viewport everything would sit at its 1× size
in a vast empty panel. 1920×1080 CSS px at `deviceScaleFactor` 2 lays out like a 1080p
window and rasterises text, SVG and borders at 4K. `--size 1600x900 --scale 2.4` also
gives 3840×2160 with the UI larger in frame; below 900 px tall the Coming soon stage
applies its own `zoom: 0.8`.

## Timing: how the clock is controlled

Time in the page is virtual. It starts at 0 when the page loads, and only moves when
webcap steps it — exactly `1/fps` per output frame, so a frame can take 50 ms or 5 s to
render and encode and the result is the same.

`lib/shim.js` is installed with `Page.addScriptToEvaluateOnNewDocument`, before any page
script:

- `performance.now()`, `Date.now()`, `new Date()`, `Event#timeStamp` read the virtual clock
  (`Date` = `--epoch` + virtual time).
- `setTimeout` / `setInterval` / `requestIdleCallback` go into a virtual queue; stepping to
  `t` fires each due timer at its own due time, in order. `requestAnimationFrame`
  callbacks run once per stepped frame with the frame's timestamp.
- `Math.random` is a seeded PRNG (the mock's ping jitter would otherwise change the hero).
- Every Web Animation — CSS animations, CSS transitions, `element.animate()`, and the
  view-transition pseudo-elements — is found through `document.getAnimations()`, paused,
  and given `currentTime = now − (the virtual time it first appeared)`. Once past its end
  it is `finish()`ed, so fill modes, `animationend`, `finished` promises and view
  transitions complete normally.
- Between steps webcap *settles* the page at the frozen instant: real macrotasks
  (React's scheduler), real rendering frames (layout, ResizeObserver, view-transition
  capture), font loads, image decodes and network requests all run to quiet, and any
  animation they start is stamped with the current virtual time. A DOM `MutationObserver`
  and the animation list decide when the page is quiet.

Each output frame: fire timers up to `t` → seek → run rAF → settle → seek → screenshot.
Timeline actions at time `a` advance the clock to `a`, seek (so hit-testing sees the
animated positions), dispatch real input, settle; any animation the input starts begins at
exactly `a`. Each input event is settled on its own (move, then press, then release), so
Chrome resolves `:hover` and `:active` between them on every run; settling only after the
whole click let a real frame sometimes fall between the CDP messages and sometimes not,
which changed the Notify pill's hover/press transitions for ~5 frames in 1 run of 3.

Why this and not the alternatives:

- **CDP virtual time + `HeadlessExperimental.beginFrame`** is the textbook answer, but
  begin-frame control lives in `chrome-headless-shell` (old headless). In the installed
  Chrome 156, even with `--enable-begin-frame-control --deterministic-mode`, the call
  answers "'HeadlessExperimental.beginFrame' wasn't found".
- **`Emulation.setVirtualTimePolicy` alone** virtualises JS timers, but without BeginFrame
  control frames (and so CSS animations and transitions) are still produced on real time.
- **The CDP `Animation` domain** (`setPlaybackRate(0)` + `seekAnimations`) only knows
  animations it reported via events, which race the page, and can't stamp an animation
  started by a click with the click's time.
- **Shim + `getAnimations()` seeking** is all in-page, uses public APIs, makes every CSS
  duration/delay/easing/fill land on the exact frame, and needs no special Chrome build.

Rendering is software (`--disable-gpu`) by default: with the GPU, filtered layers (the
Coming soon glow is `filter: blur(84px)`) differ by ±1 level when the real gaps between
frames differ. In software, runs are bit-identical even with `--slow`.

Verified on the Coming soon / Modpacks example above: traced opacity/blur/width of the title words, eyebrow,
line, Notify pill, ripple and burst match `soon.css`'s durations, delays and
`cubic-bezier(.22,1,.36,1)` easing to < 0.0001 at every frame, the pill's pixels follow
the seeked opacity frame for frame, and two runs (one with 500 ms real pauses between
frames) are byte-identical.

## Timeline actions

A JSON array; `t` is virtual seconds from frame 0 (after any preroll). Actions with equal
`t` run in file order.

```json
[
  { "t": 0.4, "do": "hover", "sel": ".navbar .navtab", "text": "Store" },
  { "t": 1.0, "do": "click", "sel": ".navbar .navtab", "text": "Store" },
  { "t": 1.5, "do": "click", "sel": ".soon .notify", "hold": 0.08 },
  { "t": 2.0, "do": "move", "x": 960, "y": 1060 },
  { "t": 2.2, "do": "key", "key": "k", "modifiers": ["ctrl"] },
  { "t": 2.4, "do": "type", "text": "keystrokes" },
  { "t": 2.6, "do": "key", "key": "Escape" },
  { "t": 2.8, "do": "wheel", "sel": ".mods__body", "dy": 400 },
  { "t": 3.0, "do": "nav", "screen": "quests", "via": "click" },
  { "t": 3.5, "do": "eval", "js": "document.body.dataset.x = '1'" }
]
```

- `hover` / `move` / `click` / `down` / `up` / `wheel` dispatch real mouse events
  (`Input.dispatchMouseEvent`) at the centre of the first element matching `sel`
  (optionally containing `text`, or the `nth` match), or at `x`,`y` in CSS px. Real
  events mean `:hover`, `:active` and their transitions behave as for a person. A click
  is move → press → release at one instant; `hold` (s) releases later, so `:active` shows.
  A warning is printed if something else covers the element's centre.
- `key` (`key`, `modifiers`: alt/ctrl/meta/shift) and `type` (`Input.insertText`).
- `nav` — see Navigation. `eval` runs JS in the page.

## Navigation

The launcher has no router: the screen is state in the zustand store `useUi`
(`src/stores/ui.ts`). Under the Vite dev server the page can `import('/src/stores/ui.ts')`
and gets the same module instance the app uses, so `--screen`/`nav` (default
`via: "store"`) set that state directly: instant, no view transition — right for the state
at t = 0. After `--screen`, CSS *transitions* the change started (the navbar tab's 300 ms
highlight) are jumped to their end so the chrome is settled at frame 0, while the new
screen's mount *animations* play from t = 0 (`--nav-transitions` keeps the transitions).
`via: "click"` presses the real controls (navbar tab, friends pill, search box,
profile menu → Settings, PvP → Edit mods) and so plays the app's page transition (a View
Transition: old screen fades 140 ms, new one rises 360 ms after 60 ms).

| name | |
|---|---|
| `home`, `store`, `mods`, `modpacks`, `servers`, `quests` | screens (`mods` = Edit mods) |
| `modsetup[:<modId>]` | a mod's setup page (default `keystrokes`) |
| `store-item:<id>` | Store with an item sheet open (e.g. `store-item:void-cape`) |
| `settings`, `friends`, `palette`, `notes`, `log` | overlays, stacked with `+`: `home+friends` |
| `layout:<classic\|stage\|bento\|lobby>`, `material:<flat\|glass>` | exploration switches |

Mock preview states (`?preview=first|title|offline|unreachable|crash|empty|signedout`)
go in `--url`.

## Layers

`--layers spec.json` maps layer name → selectors, bottom to top:

```json
{
  "backdrop": "*rest",
  "content": [".home__top", ".home__divider", ".strip"],
  "friends": [".sheet--friends"],
  "playbar": [".playbar-dock"],
  "topbar": [".navbar"]
}
```

Output: `full.png` (the whole frame), `<name>.png` per layer (full frame size, transparent
background), optional `<name>.crop.png`, and `layers.json`:

```json
{ "width": 3840, "height": 2160, "scale": 2, "time": 2.5, "full": "full.png",
  "layers": [ { "name": "friends", "order": 2, "file": "friends.png",
                "bbox": { "x": 3136, "y": 160, "w": 640, "h": 1936 },
                "domBox": { ... }, "crop": "friends.crop.png" } ] }
```

`bbox` is the pixel bounds of everything the layer paints (alpha > 0) in output pixels,
origin top-left — what you need to size and place a plane from the crop. `domBox` is the
union of the kept elements' boxes (excludes shadows/overflow). `order` is the stacking
order; layers composited "over" in that order reproduce `full.png`.

How a layer is isolated: every element outside the kept subtrees gets
`visibility: hidden !important`, and the page background is transparent (`html, body`
and Chrome's default background). Visibility keeps layout identical, so nothing moves,
and a visible child of a hidden parent still paints. A match that is already invisible
in the full frame stays hidden. `"*rest"` keeps everything not claimed by another layer
(shell, panel, cell field).

Hiding elements changes how Chrome splits the page into compositor layers, and content
rasterised into a different layer comes out slightly differently. On Home, hiding the
Friends sheet stops the navbar and play bar from being composited, and their antialiased
edges then change by up to 14 levels. So every claimed root gets
`will-change: opacity` in `full.png` and in every layer, which gives it its own
compositor layer whatever else is hidden (`--no-promote` turns this off). The side
effect is that `full.png` can differ from a plain `still` by a few antialiased pixels.
`--strip-ancestors` is a second, optional guard: the kept elements' ancestors stay
visible and only lose their own paint (background, border, shadow, outline,
`::before/::after`, direct text).

Measured on Home + Friends (5 layers, default settings): composite vs `full.png` — no
pixel differs by more than 2 levels per channel; 0.62 % differ by 1–2 (8-bit straight-alpha
rounding at antialiased edges). With `--no-promote`: 204 pixels over 2 levels, max 43.

All frames carry the viewport's transparent rounded corners (the shell's 20 px radius)
in layers and `full.png`; `still` and `seq` render the page background unless
`--transparent` (still only).

## Output layout

- `seq`: `frame_00000.png` … plus `sequence.json` (fps, size, actions, Chrome version,
  per-frame `t`, `sha256`, and `trace` if asked). Blender: Image Sequence, start frame 1.
- `still`: one PNG. `layers`: see above.

## Known limits

- Virtual time covers the page's main thread. Workers, worklets, `<video>`/`<audio>`,
  animated GIF/WebP, SVG SMIL and the text caret keep real time (the caret is hidden; the
  launcher has none of the others). `document.timeline.currentTime` and
  `Animation.startTime` stay real.
- CSS `animation-play-state: paused` is overridden — webcap owns every animation's time.
  Focus is emulated, so the Coming soon screens' blur/hidden pause never triggers.
- Scroll-driven animations are left alone (they follow scroll, not time).
- `setInterval` and deeply nested `setTimeout` are clamped to ≥ 4 ms as in browsers.
- A layer that uses `mix-blend-mode` or `backdrop-filter` against content in another layer
  blends against transparency once separated (the Coming soon glow is
  `mix-blend-mode: screen`; the `glass` material blurs what is behind it). Keep such an
  element in the same layer as what it blends with.
- Layer order is the spec's order; it must match the page's paint order where layers
  overlap.
- If the mouse rests on an element during `layers`, hiding things can drop `:hover` from
  ancestors; webcap holds any style change this starts at its old value, but move the
  mouse away (`move`) before a layered still to be safe.
- Store navigation needs the Vite dev server (it imports `/src/stores/ui.ts`); against a
  production build use `--nav-via click`.
- `--gpu`: about 0.23 s/frame at 4K instead of ~0.5 s (this machine), but filtered/blurred regions can
  differ by 1 level between runs when frame timing differs. Software and GPU renders differ
  slightly from each other (antialiasing, blur), so don't mix them in one shot.
- Layers are full-frame straight-alpha PNGs; re-compositing 8-bit straight alpha rounds
  antialiased edges by 1–2 levels.
- Each run is a fresh profile: localStorage starts empty, so the mock's preview seed
  applies and "Notify me" starts idle every time.
