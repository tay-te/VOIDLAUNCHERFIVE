# Ultralight 1.4 constraints — what the in-game overlay cannot render

Scope: the six **overlay** frames (`244:538` Mods, `244:834` Mod settings,
`244:1130` Loadouts, `244:1426` Party, `244:1722` HUD layout, `244:1900` Quick
palette) run inside **Ultralight 1.4**, a lightweight WebKit-derived renderer
with its own CPU/GPU rasteriser. It is *not* Chromium and does not inherit
Chromium's CSS surface.

The five **launcher** frames (`244:3`, `244:110`, `244:217`, `244:324`,
`244:431`) run in Electron/Chromium and have none of these limits. Where a
technique is used in both places, the launcher keeps the rich version and only
the overlay takes the fallback — the two are allowed to differ.

Confidence key: **[hard]** = will not render at all; **[risky]** = renders
inconsistently across Ultralight builds / GPU drivers, treat as unavailable.

---

## 1. `backdrop-filter` — **[hard]**

Ultralight has no backdrop/backdrop-filter implementation. The property is
parsed and dropped; you get the element's own background with nothing behind it
blurred. This is the single most load-bearing effect in the design.

**Rule: the overlay never asks the renderer to blur what is behind it.** The
game blur is produced by a GL pass in the host before the Ultralight surface is
composited, so the web layer must paint a *semi-opaque solid* and let the GL
pass supply the softness.

| Occurrence | Frames / nodes | Authored | Fallback |
|---|---|---|---|
| Overlay **Panel** body | `244:597`, `244:893`, `244:1189`, `244:1485` | `background: rgba(10,11,12,0.84)` + `backdrop-filter: blur(15px)` | Drop the filter. Paint `rgba(10,11,12,0.94)` (= `--bg-shell` at 94 %) when the host GL blur pass is running; `rgba(10,11,12,0.97)` if it is not. Keep the 1 px `--border-panel` edge and `--shadow-panel` — both render fine and carry most of the "floating glass" read. |
| **Quick palette** body | `244:1923` | `rgba(10,11,12,0.90)` + `blur(15px)` | `rgba(10,11,12,0.96)`. Same border + shadow. |
| **Quick palette dim** | `244:1922` | `rgba(10,11,12,0.55)` + `blur(3px)` | Single flat layer at `rgba(10,11,12,0.62)`. Do **not** try to fake a 3 px blur; the other overlay frames already use an unblurred dim at `rgba(10,11,12,0.50)` (`--dim-overlay`), so an unblurred dim is on-model. |
| **Live preview** strip | `249:18` (Mod settings) | game still + `rgba(10,11,12,0.58)` | Already a plain fill over a static image — no change needed, but the "game still" must be a texture the host hands in, not a live blurred capture. |
| Launcher Panel / Dock | `252:2`, `246:2`, `247:2`, `248:2`, `244:66` | `blur(15px)` / `blur(12px)` | **Keep as authored.** Chromium supports it. |

**Host contract.** If the GL blur pass is available, expose its state to the page
(e.g. a `data-glblur="on"` attribute on `<html>`) and switch the panel alpha
between 0.84 and 0.94 in CSS. Never branch on `@supports (backdrop-filter: …)` —
Ultralight may claim support and still no-op.

---

## 2. `mix-blend-mode` (and `background-blend-mode`) — **[hard]**

Every surface in this design is overlaid with a 32.64 px tiled noise PNG at
`mix-blend-mode: overlay`. Ultralight does not implement separable blend modes
on composited layers; the noise layer will either paint as an opaque grey wash
over the content or be skipped, and both are worse than no noise.

**Occurrences:** all six overlay frames — the frame root, `Canvas — recessed`,
every Panel, every card/tile/pane, every raised button, every HUD chip, every
switch, every accent CTA. See `--noise-opacity-*` in `tokens.css`.

**Fallback (in priority order):**

1. **Bake it.** Pre-multiply the noise into the flat colour and ship a single
   PNG/solid per surface tier. Practically: skip the runtime noise entirely and
   nudge the base colours ~1 % lighter so the surfaces do not read as flat black
   — `--surface-1` `#191c20` → `#1a1d21`, `--surface-2` `#22262b` → `#23272c`.
2. If grain is essential to the brand read, use a **normal-blend** tiled PNG
   whose pixels are pre-multiplied for a dark ground, at `opacity: 0.04–0.06`,
   `pointer-events: none`, `position: absolute; inset: 0`. Only do this on the
   Panel and the frame root — not on 12 ModTiles, 9 hotbar slots and 6 keycaps,
   which is 30+ extra composited layers per frame.
3. Never use `mix-blend-mode` in the overlay under any circumstances.

The launcher keeps the authored `mix-blend-mode: overlay` noise.

---

## 3. `text-shadow` — **[risky]**

Ultralight's text rasteriser does not reliably apply `text-shadow`; shadows are
commonly dropped, and on some builds they smear the glyph atlas.

**Nothing in the 11 frames authors a `text-shadow`** — the design already solves
HUD legibility structurally, and that decision must be preserved rather than
"improved" later:

- Every in-game HUD readout sits on its own chip with a solid-ish background
  (`--hud-chip-bg` `rgba(10,11,12,0.55)` or `--hud-chip-bg-strong`
  `rgba(10,11,12,0.62)`) plus a `--border-dock` edge and `--inset-hud` bevel.
  `FpsChip`, `PingChip`, `CoordsChip`, `PotionList`, `ArmorList`, `CpsChip` all
  follow this.
- The `KeystrokesWidget` and `Hotbar` put their labels on opaque keycaps
  (`--key-bg` `rgba(34,38,43,0.96)`), never on bare game pixels.
- The only unbacked marks over live game are the `Crosshair` (a solid shape, not
  text) and the HUD-editor selection label, which sits on a solid `--accent` pill.

**Rule for new HUD elements:** if text must sit over the game, give it a chip.
If a chip is genuinely impossible, use a 1 px solid outline drawn as four
offset copies — do **not** reach for `text-shadow`, and do not use
`-webkit-text-stroke` (also unreliable in Ultralight).

---

## 4. 3D transforms / `perspective` — **[hard]**

Ultralight supports 2D transforms only. `transform: rotateX/rotateY/rotateZ` in
3D space, `translateZ`, `perspective`, `transform-style: preserve-3d` and
`backface-visibility` do not render.

| Occurrence | Where | Fallback |
|---|---|---|
| `rotate(90deg)` on the Launch play glyph | `244:94` (launcher) | 2D rotation — **supported**, keep. Same for any 2D `rotate`/`scale`/`translate`. |
| Character stage skin render | `246:16–18` (launcher Cosmetics) | Launcher-only; Chromium can do a real 3D viewer. If this view is ever mirrored into the overlay, ship pre-rendered turntable frames as PNGs instead. |
| Cape "hanging" perspective | `246:22–59` (launcher) | Launcher-only. In the overlay, use the flat 62 × 98 swatch + coloured glow exactly as drawn — that is already a 2D approximation. |

**Panel enter/exit motion** must therefore be 2D: `opacity` + `translateY(8px)`
+ `scale(0.98)` over `--duration-slow` (220 ms) with `--ease-out`. No flip, no
depth, no `perspective`.

---

## 5. WebGL / `<canvas>` 3D — **[hard]**

Ultralight exposes no WebGL context. 2D canvas is present but slow and should be
avoided in a per-frame overlay.

| Occurrence | Where | Fallback |
|---|---|---|
| Cosmetics character stage | `246:16` (launcher) | Launcher-only; leave as-is. |
| Ping sparkline | `247:91–103` (launcher Servers) | Already built from 12 plain `div` bars (10 px wide, `gap 4`, r 2) — **no canvas needed**. Reuse this pattern for every chart in the overlay. |
| Any future in-game graph | — | Build it out of absolutely-positioned divs, as the sparkline does. Anything the game itself must render in 3D (skins, capes, the world) belongs to the host GL layer, composited *behind* the Ultralight surface. |

---

## 6. `<video>` and animated media — **[hard]**

Ultralight ships no media pipeline: `<video>`, `<audio>`, and animated WebP are
not decoded. Animated GIF support is unreliable.

| Occurrence | Where | Fallback |
|---|---|---|
| `Canvas — recessed` backdrop in the overlay frames | `244:592`, `244:888`, `244:1184`, `244:1480` | This is the **live game render**, supplied by the host GL layer beneath the Ultralight surface — not an HTML element. Never try to draw it in the page. |
| Launcher hero backdrop | `244:58` | A static image in the design. If this is ever upgraded to a looping video, keep it launcher-only. |
| `Preview in lobby` (Cosmetics) | `246:64` | Launcher-only action that hands off to the game client; no in-page media. |

Anything that needs to move in the overlay must be CSS-animatable (`opacity`,
2D `transform`, `width`/`left` on a slider fill) or a CSS sprite sheet stepped
with `animation-timing-function: steps(n)`.

---

## 7. Secondary risks worth designing around

| Feature | Status | Occurrence | Fallback |
|---|---|---|---|
| `filter: drop-shadow(...)` | **[risky]** | `244:122` — the inactive `Tab / Play` in Launcher — Mods carries a Figma layer shadow that exports as `drop-shadow`. | Launcher-only. In the overlay use `box-shadow` (well supported) — every other shadow in the design is already a `box-shadow`, including all the `--inset-*` bevels. |
| Inline `<svg>` icons | **[risky]** | Every `ico / *` node across all six overlay frames (lucide-style 13–22 px glyphs). | Ultralight's SVG support is partial — strokes, `stroke-linejoin` and non-scaling strokes are the usual casualties. Ship the overlay icon set as a **single PNG sprite sheet at 2× and 3×** with `background-position` offsets, or as pre-rendered PNGs per size (13/14/16/22 px). Keep the SVGs for the launcher. |
| `font-variation-settings` | **[risky]** | `"opsz" 14, "wdth" 100` on every Bricolage Grotesque title; `"CTGR" 0, "wdth" 100` on DM Mono kbd glyphs. | Ultralight loads static faces reliably, variable axes much less so. Bundle **static instances**: Bricolage Grotesque ExtraBold (opsz 14, wdth 100) and DM Mono Regular + Medium, as `@font-face` with local `.ttf`/`.otf` files. Drop `font-variation-settings` from the overlay CSS. |
| CSS Grid | **[risky]** | Not used — the design is flex + absolute positioning throughout. | Keep it that way. The 3 × 4 ModTile grid (`252:26`, `244:621`) is authored as nested flex rows with `gap 10`; implement it that way, not with `grid-template-columns`. |
| `border-style: dashed` on a rounded box | **[risky]** | `244:1858` — HUD-editor selection box, 1.5 px dashed `--accent`, r 12. | Dash phase on rounded corners is inconsistent. Fallback: a solid 1.5 px `--accent` border plus the `--accent-tint-faint` fill, which already distinguishes selection; or draw the dashes as a repeating-linear-gradient border-image on the four edges only. |
| `position: sticky` | **[risky]** | Not used. | Panels are fixed-height with no scroll in the design. If a list ever needs to scroll, use `overflow: auto` on a fixed-height container with a normally-positioned header above it. |
| Sub-pixel type sizes | **[risky]** | The design is full of `8.5px`, `10.5px`, `12.5px`, `13.5px`, `56.5px`. | Ultralight rounds inconsistently across DPI scales. Snap the overlay type ramp to whole pixels at 1× (`8.5→9`, `10.5→10`, `12.5→13`, `13.5→14`) *or* render the overlay at a fixed 2× internal scale and downsample — but pick one and apply it globally so the vertical rhythm stays consistent. |
| `gap` in flexbox | **[risky]** on older builds | Used everywhere. | Verify on the target Ultralight build. If `gap` is ignored, fall back to `margin-right`/`margin-bottom` on all-but-last children. Do not mix the two. |
| Long shadow lists | fine but costly | `--shadow-cta` is two shadows; several nodes stack a drop shadow *and* two inset shadows. | Fine at these counts. Avoid adding more — Ultralight rasterises shadows on the CPU on some configurations. |

---

## 8. What *is* safe in the overlay

Confirmed-good and used heavily by this design, so lean on them:

- Flexbox (row/column, `gap`, `flex: 1 0 0`, `justify-content`, `align-items`)
- Absolute positioning inside a `position: relative` parent
- `border-radius`, including `overflow: hidden` clipping to the radius
- `box-shadow`, **including `inset`** — this is what carries the whole bevelled
  look (`--inset-raised`, `--inset-card`, `--inset-key`, `--inset-accent`,
  `--inset-switch`, `--inset-hud`, `--inset-canvas`)
- `linear-gradient` backgrounds (`--scrim-launcher`, `--scrim-vignette`)
- `rgba()` fills and borders at any alpha
- `opacity` on an element (the HUD chips render at `opacity: 0.7` when a panel
  is open — keep this)
- 2D `transform` and CSS transitions/animations on `opacity`, `transform`,
  colour and size
- Custom fonts via `@font-face` with bundled static files
- `letter-spacing`, `text-transform`, `white-space: nowrap`

---

## 9. Per-frame checklist

| Frame | Must change for Ultralight |
|---|---|
| `244:538` Overlay — Mods | Panel `backdrop-filter` → solid `rgba(10,11,12,0.94)`. Remove noise blend on the frame, panel, 12 tiles, pane, search, tabs, switches, `Edit position`. Icons → PNG sprite. |
| `244:834` Overlay — Mod settings | Same panel + noise changes. Live-preview strip already unblurred. Icons (move, rotate-ccw, X) → sprite. Preview keycap set is pure boxes — safe. |
| `244:1130` Overlay — Loadouts | Same panel + noise changes. `--shadow-card-active` (two shadows) is fine. Sword/box/heart icons → sprite. |
| `244:1426` Overlay — Party | Same panel + noise changes. Avatar images are PNGs — safe. |
| `244:1722` Overlay — HUD layout | No panel here, so no `backdrop-filter`; the `--dim-hud-editor` scrim is already a flat fill. Remove noise from the toolbar, 7 HUD widgets and 6 keycaps. Dashed selection border → solid + tint. Armour/ping bars are plain divs — safe. |
| `244:1900` Overlay — Quick palette | Palette `backdrop-filter` → `rgba(10,11,12,0.96)`; dim `blur(3px)` → flat `rgba(10,11,12,0.62)`. Remove noise from the palette, 5 result rows, 9 hotbar slots. Search/sun/settings/layers/monitor/eye/sword icons → sprite. |
| `244:3` / `244:110` / `244:217` / `244:324` / `244:431` Launcher | **No changes** — Chromium. Keep `backdrop-filter`, `mix-blend-mode` noise, SVG icons, variable-font axes and the 3D cosmetics stage. |
