# VOID — the film, built in After Effects

Companion to `promo-film.md`. That file is the **treatment**: what each shot is and how it
moves. This file is the **build**: project setup, the rigs, the expressions, which effects
are allowed and which are not, the capture spec, and the order to do it in.

Boards: Figma `RShlfbx2TfxrdleKPIry8w`, page **Promo — film boards** (`316:2`), 46 frames at
1920 × 1080. Flat stills of the ten film boards are already exported to
`pvp/design/promo/animatic/` — that is step 1 below.

---

## 0. Two decisions to make before opening AE

### 0.1 Shoot it at 60 fps, not 24

The treatment assumes 24 (board 31 leans on it). I'd overrule that.

- The product's pitch is frame rate. A 24 fps trailer for a client that sells 1 % lows is an
  own goal — the audience for this film is the one audience that will notice.
- At 24 fps a 140 ms arrival is **3.4 frames**. Three frames of movement does not read as
  easing; it reads as a pop. The film's whole thesis is that the motion grammar is felt, and
  at 24 you cannot feel a 140 ms ease-out at all.
- The slow push-ins (3–5 % over 8 s) judder at 24 on a field of a thousand hard-edged cells.
  At 60 they are clean.

**Master: 1920 × 1080, 60 fps, square pixels.** Frame equivalents of the grammar, rounded:

| Grammar | ms | frames @60 |
|---|---|---|
| Arrive / hover in | 140 | 8.4 |
| Leave | 100 | 6 |
| Release drain | 200 | 12 |
| Stagger | 40 | 2.4 |

Two of those are not whole frames, which is exactly why everything below is **driven by
expressions in seconds rather than by keyframes**. Expressions are sampled at frame time, so
140 ms stays 140 ms and the timing is identical if the frame rate ever changes.

### 0.2 Decide now whether this is a product film or a concept film

Boards 03 / 06 / 07 are clones of the **quiet cell system** shells — the new design. The
shipping Electron launcher in `src/` does not look like this yet, and the in-game client is
mid-build. So as boarded, this is a **concept/announce film**, and that is a legitimate thing
to make, but it changes two things:

- You cannot screen-record the shots. Every launcher shot is animated vector, which is more
  AE work but total control. Budget for that.
- Shots 08 and 14 still need **real gameplay capture** underneath. That is the only footage in
  the film and it is the only thing that proves the client exists. Get that capture early —
  it is the one item with a hard external dependency.

If you want a true product film instead, the cut does not change; the shots become screen
captures and the AE work drops by about two thirds. **Say which one this is before building
shot 03**, because the answer decides the whole export pipeline.

---

## 1. Project setup

```
VOID_trailer.aep
├─ 00_CONTROL          _GRAMMAR comp          ← global timing + palette, referenced by all
├─ 01_FOOTAGE          gameplay plates, input recordings
├─ 02_BOARDS           imported Figma assets, one folder per board
├─ 03_RIGS             CELLGRID_master, TRAIL_driver, SHELL_03 …  reusable
├─ 04_SHOTS            SHOT_01 … SHOT_10  (one comp per shot, 1920×1080 @60)
├─ 05_MASTER           MASTER_60s, MASTER_30s, MASTER_vertical
└─ 06_OUT
```

- **Project Settings → Expressions → JavaScript engine.** The expressions below use `const`
  and arrow functions; the legacy ExtendScript engine will throw on all of them.
- **Color: sRGB working space, 16 bpc.** 8 bpc is survivable because the art is flat, but the
  ambient fields sit at 3–22 % alpha over `#0B0B0C` and 8-bit rounding shows up as visible
  stepping across a 1,700-cell field. 16 bpc costs almost nothing here.
- **Fonts: Outfit Light / Regular / Medium installed before first open.** AE substitutes
  silently and you will not notice until the render.
- Turn on **Preferences → General → Allow Keyframes Between Frames** if you ever do keyframe
  something by hand at these durations.

### The `_GRAMMAR` control comp

One comp, one null, expression controls. Every other comp reads from it, so a change to the
grammar propagates through the whole film in one edit.

| Control | Type | Value |
|---|---|---|
| `t_in` | Slider | 0.140 |
| `t_out` | Slider | 0.100 |
| `t_drain` | Slider | 0.200 |
| `t_stagger` | Slider | 0.040 |
| `hue_hud` | Color | `#9F8BFF` |
| `hue_pvp` | Color | `#FF9E7A` |
| `hue_visual` | Color | `#7ADFFF` |
| `hue_utility` | Color | `#7AE0B0` |
| `fill_shell / base / card / raised` | Color | `#0B0B0C` `#131315` `#191A1C` `#212225` |

Read it from anywhere with:

```js
const G = comp("_GRAMMAR").layer("GRAMMAR");
const tIn = G.effect("t_in")("Slider");
```

---

## 2. Getting a screen out of Figma and into AE

**Default to converting the board into native AE layers (§2.3), and fall back to flat PNGs
only where conversion is the wrong tool.** That is the opposite of the usual advice for UI
work, and the reason is in §2.3: converters lose exactly the things this design system does
not have.

The format still follows **what the layer has to do in the shot**, so:

| What the layer does in the shot | Bring it in as |
|---|---|
| Anything whose geometry, fill or text animates — knobs, meters, tiles, labels, counters | **Converted to native AE layers** — AEUX, §2.3 |
| Hundreds or thousands of repeating cells | **Rebuilt procedurally** — Repeaters, §3. Never converted, never imported. |
| Purely static plates that only move, scale or get matted — gameplay scrims, the big shell clones | **PNG at 2×**, one per top-level group, §2.1 |
| Anything the camera flies through (11, 13) | **PNG at 4×**, or native shapes if the layer count allows |
| Pixel-grid artwork (17–19, 21, 28) | **PNG at final on-screen size**, 1× or a whole multiple |

Rough split across this film: about two thirds of the boards convert whole, the five
cell-field boards are hybrids, and PNG is reserved for plates and pixel art.

### 2.1 The PNG route, done so you never position anything

The expensive mistake is exporting each group at its own bounds: you get twenty PNGs of
twenty different sizes and you spend an evening nudging them back into alignment, by eye,
wrongly.

**Export every group at the frame's bounds instead.** Then every PNG is 1920 × 1080, every
layer drops in at position `960, 540`, and the board reassembles itself perfectly with zero
alignment work. In Figma the frame is the export unit, so:

- **By hand:** duplicate the board once per group, delete all but one group from each
  duplicate, select all the duplicates, export at 2×. Every file is frame-sized.
- **Scripted (better here):** a ~30-line plugin that walks the frame's top-level children,
  solos each one, and calls `exportAsync` on the *frame* at 2×. You have written plugins for
  this file before — reuse that harness, and mind the traps in the Figma plugin notes
  (collect node references in an array; never re-find nodes by comparing colour floats).

Export settings that matter:

- **2× is the default.** The push-ins are only 3–5 %, so 3840 × 2160 of headroom is plenty.
  Import, scale the layer to 50 %, and you have a supersampled plate you can push to ~200 %
  before softness shows. Use **4×** only for the boards the camera flies through (11, 13).
- **Keep the group numbering.** `01 BG`, `02 FIELD`, `03 MARK` … alphabetical import order is
  z-order, which is why the boards were built that way. **Delete `09 SLATE` on the way out** —
  it is hidden in Figma but a plugin that solos groups will happily export it.
- **Interpret Footage → Alpha: Straight (Unmatted).** Figma writes straight alpha. If AE
  guesses premultiplied, every antialiased edge on this dark UI picks up a pale fringe — it
  reads as a faint glow, which is the one thing the film cannot have.
- Figma exports sRGB, so keep the AE working space sRGB or everything shifts a little darker.

### 2.2 The exception: the shell boards

Boards 03 / 06 / 07 hold shells that deliberately extend past the frame — board 07's is
4400 × 2695 at (−2350, −412). Exporting those at frame bounds throws away the overhang and
kills the whole point, which is that you can reframe and push in AE without a new export.

So **export those at their own bounds**, and place them by arithmetic rather than by eye:

```
AE position = ( x + w/2 , y + h/2 )        in frame coordinates
07 shell    = ( −2350 + 2200 , −412 + 1347.5 ) = ( −150 , 935.5 )
```

Split each shell into 3–4 sub-exports (background, content, dock, hero title) so the push can
carry parallax.

### 2.3 Converting a whole screen — the direct route

There are tools that take a Figma selection and rebuild it inside AE as real layers, and for
this design system they work unusually well. The reason is worth stating, because it inverts
the usual advice: **converters lose gradients, blurs, shadows, blend modes and effects — and
the quiet cell system bans every one of those.** What it is made of is flat fills, rounded
rectangles, hairline strokes and text, which is exactly the subset that translates cleanly.
A board here converts better than a normal UI screen does.

**AEUX** (free, `aeux.io`) is the tool. Figma plugin on one side, AE panel on the other.

1. Install the Figma plugin, and install the AE panel's ZXP (the aescripts ZXP Installer
   handles it).
2. In AE, **Preferences → Scripting & Expressions → Allow Scripts to Write Files and Access
   Network** must be on, or the panel cannot receive anything.
3. `Window → AEUX` in AE, select in Figma, run the plugin, then pull it across from the panel.

What comes over intact: layer names and group hierarchy, position and size, flat fills and
strokes, corner radius as a live property, opacity, and **live text** with font, size,
tracking and leading. In other words, most of a board.

What does not: images (export those separately as PNG), component instances (they flatten),
and anything using an effect — which here is nothing.

### 2.3.1 The one thing that will break it: layer count

AEUX will cheerfully attempt whatever you select, and several of these boards are traps:

| Board | Group | Layers it would make |
|---|---|---|
| 34 Hover trail | `02 AMBIENT FIELD` | 1,755 |
| 01 Cold open | `02 FIELD` | 1,277 |
| 13 The mark | `02 FIELD` | 1,017 |
| 22 Frame time | `04 BARS` | 468 |
| 12 Dissolve | `07 DISSOLVE` | 262 |

Those are the fields, and they are also the ones that must never be per-layer anyway (§3).
So the workflow is a hybrid, and it is quick:

> **Hide the cell-field group, convert everything else with AEUX, rebuild the field in AE with
> a Repeater.** Two minutes of setup, and you get a native, fully editable board with a
> one-layer field that previews in real time.

Everything else on these boards is small enough to convert whole — the shells run about 270
nodes, which is fine.

### 2.3.2 Pre-flight, per board

1. **Duplicate the board and convert the copy.** Never point a converter at the master.
2. Delete `09 SLATE` — it is hidden, but a converter will bring it anyway.
3. Hide the cell field per the table above.
4. Keep the numbered group names; they become your AE layer names and your z-order.
5. Convert, then **check three things**: Outfit came across (not substituted), the eyebrow
   tracking survived, and the fills are the exact hexes — `#0B0B0C` `#131315` `#191A1C`
   `#212225`.

**Verify it in ten seconds.** Drop a flat 1× PNG of the same board on top of the converted
comp and set it to **Difference**. Anything that did not convert correctly lights up; a
correct conversion goes pure black. Delete the check layer afterwards — Difference is a blend
mode, and blend modes do not ship in this film.

### 2.3.3 The other routes

- **Overlord** (paid, Battle Axe) — Illustrator → AE. Go Figma → copy as SVG → paste into
  Illustrator → send across. Cleanest path geometry of any route; worth it if you end up doing
  real path work, overkill if you do not.
- **PDF export** — Figma → PDF, import, turn on **Continuously Rasterize**. Crisp at any
  scale, no layer separation. Good for a vector plate you only ever transform.
- **Illustrator `.ai`** — SVG → Illustrator → Release to Layers → save `.ai` → import to AE as
  *Composition – Retain Layer Sizes*. The old reliable when a converter chokes.

**Do not import SVG straight into AE.** It rasterises at a fixed size and mangles anything
deeply nested, which Figma's SVG output always is. Go through Illustrator or PDF. Note this
route *does* lose the parametric corner radius — a 30 % rounded cell arrives as a path rather
than a live Roundness — which is one more reason to prefer AEUX or to redraw cells in AE.

### 2.4 Text

Retype it. Every number that counts, every label that restyles between `--text-muted` and
`--text-primary`, every line that sets word by word has to be live text.

Two conversions to get right, because both are silently wrong by default:

```
AE tracking  =  ( Figma letter-spacing in px  /  font size in px ) × 1000
AE tracking  =  Figma letter-spacing in %  × 10
AE leading   =  Figma line-height in px        (uncheck Auto in the Character panel)
```

The eyebrows on these boards are tracked out hard — get that conversion wrong and they read
as ordinary small caps, which is most of what makes the boards look like the product.

---

## 3. The central technique: do not animate cells, animate the light on them

The boards are made of cells, and the cell counts are not small:

| Board | Group | Cells | Cell | Pitch | Origin |
|---|---|---|---|---|---|
| 34 Hover trail | `02 AMBIENT FIELD` | 1,755 (57 × 33) | 8 px | 34 px | −8, −8 |
| 01 Cold open | `02 FIELD` | 1,277 (48 × 27) | 6 px | 40 px | 20, 20 |
| 13 The mark | `02 FIELD` | 1,017 | — | — | — |
| 22 Frame time | `04 BARS` | 468 (64 × 9) | 16 px | 26 × 20 px | 133, 700 |
| 12 Dissolve | `07 DISSOLVE` | 262 | — | — | — |
| 04 The rule | `04 METER` | 16 | 60 px | 84 px | 207, 510 |

Bringing 1,755 cells in as 1,755 layers and driving each with a distance expression is the
obvious approach and it is the wrong one: the per-frame cost is 1,755 expression evaluations
(×24 if you sample pointer history for the age weighting), which puts you at seconds per
frame in the viewer and makes the shot unworkable to iterate on.

**Do this instead.** The cell field is a static texture. Everything that appears to happen to
it is a **matte** driving it:

```
[ CELL GRID ]      one layer, static, all cells at full value   ← never animates
      ↑ luma matte
[ DRIVER ]         a tiny comp where the actual animation lives
```

The driver comp is **one pixel per cell** — for board 34 it is literally a **57 × 33 px comp**.
Animate a soft blob, a ramp, a noise field, a wipe in there, then scale it to 3400 %
(57 × 34 = 1938 px wide) with the layer's **Quality set to Draft** so it scales
nearest-neighbour into hard 34 px blocks that land exactly on the cell pitch. Offset it by
(−8, −8) to match the grid origin. Now every cell reads exactly one driver pixel.

Three things fall out of this for free:

1. **Quantisation is automatic.** A smooth gradient in the driver becomes per-cell constant
   values in the picture, which is precisely the design rule: the grid is the resolution.
2. **The banned effects become legal.** A Gaussian blur, a glow, a gradient ramp, a
   `Fractal Noise` — all forbidden in the picture — are fine *inside the driver comp*, because
   the driver is never rendered. It only decides which cells are lit and how much.
3. **It is fast.** One matte, one grid layer, real-time preview.

**Snapping the values to the system's fill ladder.** The fill steps (1.0 / 0.90 / 0.50 /
0.32 / 0.26) are not evenly spaced, so `Posterize` (which is uniform) will not hit them. Use
**Colorama** on the driver with a custom output ramp built from hard stops at those five
values, or a **Curves** staircase. Either one turns a continuous driver into the exact ladder.

### Building the cell grid layer itself

Do not import 1,755 vector shapes. One shape layer, one rounded rectangle (8 px, 30 % radius
= 2.4 px corner), two nested **Repeaters**: the first with `Copies = 57`, `Position = [34, 0]`,
the second with `Copies = 33`, `Position = [0, 34]`. Continuously-rasterise on. The whole
field is then one lightweight layer whose geometry you can change from two numbers, and the
same rig re-times for board 01 (48 × 27 at 40 px) and board 22 (64 × 9 at 26 × 20).

Keep `CELLGRID_master` in `03_RIGS` and duplicate per board.

---

## 4. Effects: the allowed list, the banned list, the exemption

**The rule that makes this decidable:** the contract governs *what VOID renders*. It has
nothing to say about what happens inside a matte that is never seen. So —

> **Banned in the picture. Unrestricted inside a driver comp.**

### Use these

| Effect | What it's for |
|---|---|
| **Fill** | the hue arriving on a live value — the single most-used effect in the film |
| **Tint / Colorama** | stepping mono values between fill steps; quantising a driver |
| **Set Matte**, luma/alpha track mattes | the entire grid-lighting architecture above |
| **Echo** (or CC Wide Time) | motion history — board 34's trail, board 31's easing ghosts |
| **Mosaic** | quantising a full-size driver to the cell pitch when a tiny driver comp is awkward |
| **Linear Wipe**, 0 feather | column and row wipes — always as a matte, never on the art |
| **Fractal / Turbulent Noise** | driver-only: board 12's dissolve order, board 13's field |
| **Posterize Time** | forcing a sub-rate on something that should tick, not glide |
| **3D layers + one camera** | boards 11, 13, 19 only |

### Do not use these in the picture

Glow · Gaussian / Camera Lens Blur · Drop Shadow · Gradient Ramp as visible art · every blend
mode · Depth of Field on the camera · lens flare, light leaks, chromatic aberration, film
burn. The product cannot draw any of them, so the film drawing them is the film contradicting
the thing it is selling. This is not a style preference; it is the one rule that makes the
film legible as an argument.

### Two judgement calls

**Motion blur.** Off for UI arrivals — they are fill steps and 8 px moves, and blur on them
just makes them mushy. **On for the camera**: boards 11 and 13 fly through a thousand
hard-edged cells, and without motion blur that field strobes badly. Shutter angle 180°.

**Aliasing on push-ins.** A 3 % push across a field of 6 px cells shimmers. Fix it by
**never moving the field** — lock `02 FIELD` / `02 AMBIENT FIELD` to the frame and get the
parallax from the product planes only. Where the camera genuinely must move through cells
(11, 13), render that shot at 2× and downsample.

**Grain, at the master only.** This is a film of large flat near-black areas, which is the
single worst case for H.264 — you get banding and blocking in the shell colour. One adjustment
layer at the top of `MASTER`, `Add Grain` or a 1–2 % monochrome noise, kills it. It is a
mastering fix applied after the picture, not an effect on the product.

---

## 5. The expression library

Put these in `03_RIGS` as animation presets once and reuse them.

### 5.1 Arrival — a fill step with no keyframes

Start time comes from a **layer marker**, so retiming a beat is dragging a marker.

```js
const G  = comp("_GRAMMAR").layer("GRAMMAR");
const t0 = marker.numKeys > 0 ? marker.key(1).time : 0;
const A  = 0;      // from
const B  = 100;    // to
easeOut(time, t0, t0 + G.effect("t_in")("Slider"), A, B)
```

`easeOut()` decelerates into the end of the range, which is what CSS `ease-out` means. AE's
naming trips everyone — if a move looks inverted, swap to `easeIn()` and look again.

For the exact CSS curve rather than AE's built-in Hermite:

```js
function cb(t, p1x, p1y, p2x, p2y) {
  const bx = u => 3*(1-u)*(1-u)*u*p1x + 3*(1-u)*u*u*p2x + u*u*u;
  const by = u => 3*(1-u)*(1-u)*u*p1y + 3*(1-u)*u*u*p2y + u*u*u;
  let lo = 0, hi = 1, u = 0.5;
  for (let i = 0; i < 18; i++) { u = (lo + hi) / 2; if (bx(u) < t) lo = u; else hi = u; }
  return by(u);
}
const G = comp("_GRAMMAR").layer("GRAMMAR");
const t0 = marker.key(1).time, d = G.effect("t_in")("Slider");
const p = clamp((time - t0) / d, 0, 1);
linear(cb(p, 0, 0, 0.58, 1), 0, 1, 0, 100)   // cubic-bezier(0,0,.58,1) = ease-out
```

### 5.2 Stagger — derive the delay from position, not index

Index-based staggers break the moment you reorder layers. Grid staggers should read the
layer's own x:

```js
const G    = comp("_GRAMMAR").layer("GRAMMAR");
const stg  = G.effect("t_stagger")("Slider");
const col  = Math.round((transform.position[0] - 160) / 276);   // tile pitch on board 06
const t0   = thisComp.layer("CUE").marker.key(1).time + col * stg;
easeOut(time, t0, t0 + G.effect("t_in")("Slider"), 0, 100)
```

Swap `col` for a row calculation on board 07, or for `Math.round(hypot(...)/pitch)` if you
ever want a radial build (board 01's mark build is clockwise, so that one is hand-ordered —
see 5.1).

### 5.3 Count-up

```js
const t0 = marker.key(1).time, d = 0.9, target = 142;
Math.round(easeOut(time, t0, t0 + d, 0, target)).toString()
```

**Outfit is proportional**, so a counting number reflows its own width every frame and the
figure appears to wobble. Fix it by setting the text layer's paragraph alignment to **right**
and anchoring on the right edge, so the last digit is pinned and the growth happens leftward.
On board 09's hero figure, where the number is 400 px tall, this is very visible if you skip it.

### 5.4 The hover trail (board 34) — driver comp, not per-cell expressions

In a **57 × 33 px** comp named `TRAIL_driver`:

1. A ~6 px white soft blob, parented to a null that runs the pointer path (the same path in
   the shot comp, scaled down by 34).
2. **Echo** on the blob: `Number of Echoes 24`, `Starting Intensity 1`, `Decay 0.86`,
   `Echo Time -(1/60)`, `Operator: Maximum`. That is your age weighting — decay is
   exponential where the treatment asked for `age^2.5`; visually it is the same read, and
   the knob is one number instead of a loop over 24 `valueAtTime` samples per cell.
3. **Levels** to set the floor at 3 % and the ceiling at 22 %.
4. **Colorama** with the fill-step ladder as hard stops.

Then in the shot comp: `TRAIL_driver` scaled 3400 %, Draft quality, offset (−8, −8), set as
the luma matte on `CELLGRID`, with a `Fill` of `hue_visual` on a duplicate above for the
coloured portion.

**The build note from the treatment still binds:** falloff must be **linear in distance**.
The blob's own profile is that falloff — if you use a heavily soft radial blob you have
squared it by accident and the trail disappears. Use a near-hard blob with a two-pixel edge.

If you insist on per-cell layers, this is the honest expression, and the reason not to:

```js
// ~1,755 layers × 24 samples = ~42,000 evaluations per frame. Do not ship this.
const P = thisComp.layer("POINTER");
let a = 0.03;
for (let i = 0; i < 24; i++) {
  const age = i / 24;
  const d = length(P.toComp([0,0], time - i*thisComp.frameDuration), toComp(anchorPoint));
  a = Math.max(a, (1 - clamp(d/420, 0, 1)) * Math.pow(1 - age, 2.5) * 0.22);
}
a * 100
```

### 5.5 The histogram (board 22)

Columns arrive discretely — never slide the graph smoothly.

```js
const pitch = 26, rate = 6;                 // 6 columns per second
const n = Math.floor((time - inPoint) * rate);
[value[0] - n * pitch, value[1]]
```

Then a `Fill` in the hue on a duplicate of the bars layer, masked to a single 26 px-wide
rectangle sitting at the newest column's x. The hue never animates — the mask is static and
the bars move underneath it, so a column is in the hue for exactly as long as it is newest.
That is §1 of the contract enforced by the rig itself rather than by keyframing.

### 5.6 Pixel-integer lock (boards 17–19, 21, 28)

Whole-number scaling, no rotation, and no subpixel positions:

```js
[Math.round(value[0]), Math.round(value[1])]
```

Scale only at 200 / 300 / 400 %, set layer **Quality → Draft** (AE's nearest-neighbour path;
Best/Bicubic will resample the grid and turn the sprites to mush), and prefer re-exporting the
sprite at final size from Figma over scaling in AE at all.

---

## 6. Shot-by-shot, where the AE method is not obvious

The motion is specified in `promo-film.md` §2. These are the build notes only.

### 6.1 Shot 01 — the mark builds out of the field
The 16 mark cells are a separate group (`03 MARK`) from the 1,277-cell field, so bring the
mark in as 16 real layers — small enough to hand-order clockwise — and leave the field on the
driver rig, its density ramp animated as a radial gradient in the 48 × 27 driver comp over the
full 5 s. Wordmark wipes with a `Linear Wipe` matte, 0 feather.

### 6.2 Shots 03 / 06 / 07 — the shell trio
These three are the same rig three times: build it once, duplicate twice.

The shells are large clones that extend well past the frame — export and place them per §2.2,
split into 3–4 sub-groups (background, content, dock, hero title) so the push-in can carry
parallax; give the dock roughly 1.6× the hero's travel.

Push-in is on a camera, not on layer scale, and it is **linear** — no ease on a push, ever.
The Launch press at 0:16 is a 100 ms `Fill` step, no scale bounce, no glow.

### 6.3 Shot 04 — the thesis shot
Only 16 meter cells, so this one is genuinely per-layer. Drive the hue with a `Fill` on a
duplicate of each cell and an opacity expression keyed off the live-cell index, so
"which cell is live" is a single slider you can animate to drag the value:

```js
const live = thisComp.layer("VALUE").effect("live_index")("Slider");
const i = index - thisComp.layer("CELL 01").index;
const dist = Math.abs(i - live);
const amt = dist === 0 ? 100 : dist === 1 ? 40 : dist === 2 ? 16 : 0;
easeOut(time, marker.key(1).time, marker.key(1).time + 0.140, 0, amt)
```

The ±1 at 40 % / ±2 at 16 % bleed then follows the live cell automatically when you drag it
two steps right, and the 40 ms lag on the neighbours is one added `* stg * dist` on the start
time. The ambient field behind is the same driver rig as 34, ramped 3 % → 22 % over 140 ms
and drained over 200 ms.

### 6.4 Shots 08 / 14 — the only real footage
Composite the HUD in AE over a **clean plate**; do not record gameplay with the HUD already
on. See §7.

Board 14 is a **match cut** from 08: same plate, same HUD positions, `03 HUD REMNANT` never
moves or fades. Build 14 as a duplicate of the 08 comp with the menu groups added, so the two
are guaranteed to line up frame for frame.

### 6.5 Boards 11 / 13 / 19 — the 3D shots
Zero the fake 2D offsets, enable 3D on the `PLANE` groups, set real Z (0, −400, −800, −1200,
−1600), one camera, **DOF off**, motion blur on, orbit ±12°. Because every plane shares one
local coordinate system, animating Z → 0 collapses them into one correct launcher screen —
that collapse is the shot.

Board 19's block is a true 2 : 1 dimetric projection, so a real camera will line up with the
drawn faces exactly when it reaches dimetric. Worth doing properly; it is the one place where
the drawn and the rendered projection can be made to agree on camera.

---

## 7. The gameplay plate

The one hard external dependency. Get it before you need it.

- **1920 × 1080 at 60 fps minimum**; 1440p60 downsampled to 1080 is noticeably crisper if the
  machine can hold it. OBS, NVENC/ProRes or lossless — not a 6 Mbps stream preset.
- **Clean plate: capture with VOID's HUD disabled.** Every HUD element in the film is boarded
  vector, and compositing it lets you retime the count-ups, fix positions and keep the type
  crisp. A HUD baked into the capture cannot be fixed later.
- **Pick the scene for the HUD, not for the action.** The HUD clusters sit top-left,
  bottom-left, bottom-right and centre. Shoot somewhere with quiet, dark-ish pixels under
  those regions — the contract's own chrome/world split exists because legibility over
  arbitrary game pixels is a real problem, and the film should not have to fight it.
- **Record the keyboard separately** (an input-display source on a second scene) so shot 08's
  keystroke caps can be driven by real input timing rather than hand-keyed guesses.
- Grade: keep the game in colour — it is the contrast that makes the monochrome UI read — but
  expect to pull saturation ~15 % and exposure down a touch under the HUD clusters.
- Board 14's `02 SCRIM` is designed at 74 %. Do not "improve" it with a blur.

---

## 8. Mastering and delivery

- **Master out: ProRes 4444, 1920 × 1080, 60 fps**, via Media Encoder. Keep this as the
  archival file; every other deliverable is a transcode of it.
- **H.264 for upload: ~40–60 Mbps, VBR 2-pass, high profile.** This film is mostly flat
  near-black; at typical 12–16 Mbps presets the shell colour bands and the ambient fields
  blotch. The grain layer from §4 plus the high bitrate is what keeps it clean.
- **Upload a 2160p version even though it is a 1080p film.** YouTube assigns a higher bitrate
  tier and a better codec to 4K uploads; a nearest-neighbour 2× of a flat vector film is
  lossless in practice and the dark flats survive the re-encode far better.
- Thumbnail: board 44 (product hero) — it is the only frame in the set built to survive at
  thumbnail size.

### Cutdowns

Cut the 30 for social **first** and treat the 60 as the long form, not the other way round.
Vertical is a reframe, not a crop: the 16 : 9 product shots do not survive 9 : 16. The boards
that do are the centred bands — 04, 05, 18, 31, 35 — plus the end card. Six frames is a
vertical cut; do not try to force 03 / 06 / 07 into it.

---

## 9. Where to start — in order

1. **Build the animatic tonight.** The eleven stills in `pvp/design/promo/animatic/` on a
   62-second timeline at the treatment's in/out points, plus room tone. No animation. This
   takes half an hour and it is the only thing that tells you whether the cut holds — and my
   read is that it will come back long, which is worth knowing before anything is built.
2. **Fix the cut from what the animatic tells you.** My recommendation up front: put
   **board 14 (the overlay) into the main cut**, straight after 08. The film as boarded shows
   the HUD in play but never shows the menu opening over a live match, which is the single
   thing that separates this from a launcher with a mod list. Drop 05 Loadout to pay for it —
   it is the least load-bearing shot in the ten.
3. **Order the gameplay capture.** §7. It gates two shots and nothing else can unblock it.
4. **Build shot 04 first, not shot 01.** It is the thesis shot, it is only 16 cells, and the
   ambient-field rig it forces you to build is the same rig that shots 01, 13, 31 and 34 all
   reuse. If 04 does not land, the film's idea does not work and you want to know on day one.
5. **Then the shell trio (03 / 06 / 07)** as one rig built once and duplicated twice.
6. **Then 01 and 10**, which are nearly free once the field rig exists.
7. **08 and 14 last**, when the capture is in hand.
8. **Replace every number before anything goes public.** See `promo-film.md` §2h — 142 fps,
   1 % low 118, 9.7 ms, 48 MB, 11 s and the rest are all invented placeholders. A performance
   claim in a trailer is a promise, and this is the audience that will test it.

### Open risks, carried forward

- **Placeholder numbers** — as above. Three groups are marked in Figma; the caution covers all.
- **Board 27's promises** (no store, no pass, no ads, no background service, no telemetry, no
  paid tier) ship only if all six are true and intended to stay true.
- **`Hypixel`** appears on board 24 and in the Play hero's own copy. It is a real third-party
  mark that came from the app, not from the film. If it has to go, the launcher copy changes
  with it.
- **`NOT AFFILIATED WITH MOJANG OR MICROSOFT`** is on board 43. It belongs on the end card
  footer too if the film ships standalone.
