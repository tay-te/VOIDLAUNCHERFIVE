# VOID Hub — performance

Is an Ultralight-drawn inventory fast enough for Minecraft 26.3, and does it work? This file has the
verdict, the measured numbers on both graphics backends, and what production would need.

<!-- verdict:begin -->
## Verdict: viable — it costs about what vanilla's inventory costs

Measured 2026-10-04 on an RTX 4060, 1920×1080, vsync off, uncapped (both gates PASS; the cleanest
run is the Vulkan one, machine load 26 % → 7 %):

* **Frame cost vs vanilla's inventory:** Vulkan +0.02 ms avg / +0.08 ms p99 idle, +0.04 ms avg /
  +0.21 ms p99 while the pointer sweeps every slot (~1060 fps vs ~1090). OpenGL +0.21 ms avg idle,
  −0.06 ms while hovering — inside the noise of a shared machine. Budget was +1.0 ms avg / +2.0 ms p99.
* **On the render thread the VOID screen costs ~0.005–0.02 ms a frame** (post messages, copy the
  dirty rectangle, upload it). Idle: nothing is copied or uploaded at all. Hovering: one ~45–160 KB
  dirty rectangle per change.
* **No hitches from the screen.** Zero frames over 33 ms while hovering on Vulkan; on OpenGL the
  one 34 ms frame matched one in vanilla's own hover run (other games were running on the machine).
* **Open:** E / R-Shift → first painted frame 8–16 ms (vanilla 6–13 ms). First open after pre-warm
  70–106 ms. Ultralight's start-up (50 ms on a quiet machine, ~400 ms under load) and the first full
  paint are paid on its own thread when the world is joined.
* **Memory:** +60–115 MB private bytes / working set (WebCore, the 1920×1080 surface and texture,
  fonts); Java heap unchanged.
* **Works:** 16/16 functional checks on both backends — pickup/place, split, shift-click, number-key
  swap, Q drop, 2×2 craft, drag-distribute, double-click collect, armour/offhand, filter chips via the
  page, E/R-Shift/Esc, chest Move all both ways, Sort; client and server agree after every step;
  page wells match Java hit-testing at GUI scales 1–4.

### The Map page with VOID Atlas (2026-10-07, Atlas 792a7cfc+, still backdrop by default)

Measured on the same machine while other game runs were going (CPU load 51–73 %); both gates with
Atlas PASS (`hubPerf -Pbackend=opengl|vulkan -Patlas -Pseconds=20`):

| | OpenGL | Vulkan |
|---|---|---|
| **Map page panning, still backdrop (the default): avg / p99** | **1.45 / 3.34 ms** | **1.15 / 2.60 ms** |
| vs Atlas's own map screen on its still backdrop, panned the same way: avg / p99 | −0.17 / −0.13 ms | −0.34 / −0.53 ms |
| Map page panning, live world (`-Dvoidhub.frozenMap=false`): avg / p99 | 2.47 / 5.08 ms | 2.19 / 4.07 ms |
| vs Atlas's screen with its live backdrop: avg / p99 | −0.27 / −0.37 ms | −0.10 / −0.30 ms |
| Map page idle (live) vs Atlas's screen idle (live): avg / p99 | −0.21 / −0.04 ms | −0.11 / −0.33 ms |
| Saved by the still while panning | 1.02 ms | 1.04 ms |
| Atlas's widget draw on the render thread while panning | 0.54–0.58 ms | 0.58–0.67 ms |
| Ultralight uploads while the map pans (or sits) | 0 | 0 |
| First Map page open, key → first frame (Atlas's shared page made then) | 102.3 ms (23.7 ms) | 94.2 ms (6.7 ms) |
| Map page re-open, worst | 7.1 ms | 7.2 ms |

* **The still keeps the hub's look**: Atlas draws it under the hub's 34 % dim
  (`drawFrozenWorld(graphics, 0.34)`), and the hub draws that same dim natively over the live world in
  the frame or two before Atlas has its still, with the page's own dim off all the while, so the
  backdrop never changes shade or doubles its dim.
* **The hub adds nothing measurable around Atlas's map**, live or still: Atlas draws the same page in
  both; the hub's islands and window frame are one premultiplied quad, already uploaded.
* **Nothing is drawn twice.** The pointer over the map is never sent to Ultralight: zero uploads in
  every Map scenario.

### GUI scale 2 at 1920×1080 (2026-10-19): the page zooms itself

Ultralight 1.4 paints only `(w·s) × (h·s)` CSS px at a device scale `s` under 1, so a layout unit under 1 (GUI 1
and 2 at 1080p) never reaches it as a device scale. bd989d12 rendered those at the CSS viewport's size (2.25× the
pixels at u 0.667) and drew the texture down, LINEAR. Now the view is the framebuffer at device scale 1 and the page
applies the unit as CSS `zoom` on the root (`HubView.pageZoom`, `window.HUBZ`): native-resolution text, pixel art on
whole device px, no extra area, no smoothing. Boxes read in the page stay in CSS px; pointer positions are multiplied by
the zoom on the way in and divided on the way out; `MC.abs`/`MC.size` read exact boxes (offsetLeft rounds under zoom).

UI thread at 1920×1080 GUI 2 (hubAudit, hubHearth; CPU per paint, worst paint), drawn down → page zoom
(→ keycaps patched in place, 2026-10-19: the draft's hover rebuilt its keycap row with `innerHTML`, which made
Ultralight repaint the whole 1920×1080 view, 8 MB, on every hover at every scale; now it is built once per hand and a
hover restyles one cap, ~27 KB per paint):

| | drawn down (bd989d12) | page zoom |
|---|---|---|
| Mettle draft hover sweep | 20.8 ms, max 25.7 ms | 13.9 ms, max 15.9 ms → **3.5 ms, max 4.9 ms** |
| Mettle draft pick | 10.5 ms, max 34.9 ms | 9.5 ms, max 27.1 ms → 7.5 ms, max 22.4 ms |
| 400-card binder wheel scroll | 4.8 ms (p95 2.57), max 29.9 ms | 2.7 ms (p95 1.66), max 15.4 ms |
| 400-card binder hover sweep | 6.6 ms (p95 4.89), max 11.7 ms | 3.5 ms (p95 3.23), max 10.6 ms |
| Hearth market / talk hover paints | 4.0–5.5 / 3.9–5.2 ms | 1.5–3.0 / 1.4–3.0 ms |
| Idle (draft, binder, every Hearth page) | 0 paints | 0 paints |
| Inventory / chest hover sweep (hubPerf `*_gs2`, gated ≤ 10 ms) | — | 2.9 / 2.9 ms (21 / 18 KB per paint) |

The gates measure the UI thread, not only the render thread: hubAudit's draft hover gates its CPU per paint
(≤ 10 ms, no paint over 30 ms; it was a p95 over every frame, mostly frames that paint nothing), and hubPerf runs the
inventory and chest sweeps again at 1920×1080 GUI 2 and gates Ultralight's CPU per uploaded change (≤ 10 ms;
`uiThreadCpuMsPerPaint` in the JSON, "UI thread CPU per paint" in the tables below).

Spaces: under the zoom Ultralight lays glyphs on whole device px, so a small font's 0.2 em space was 1 px and read as
none ("fromHearth"). `html.hz` (set by `HubView.zoomScript` while zoomed) gives every word space 1.5 device px more
(hub.css); hubAudit (Inventory, Classes, draft at GUI 1–3) and hubHearth (every photographed page at GUI 1–2) fail on a
word space under 1.5 device px (`Spaces`).

### What made it fast (each measured)

1. **Ultralight off the render thread.** With Ultralight's paint on the render thread (`_sync`
   rows) the hover sweep costs the game ~0.15–9 ms per frame and, before the fixes below, 50 ms
   spikes. On its own thread the game never waits for it.
2. **Dirty-rectangle copy in native code** (new JNI `copyDirtyRgba`). The binding's original
   `readPixels()` path (`_fullreadback` rows) moves 8 MB per change: 1.1–2 ms per frame and p99
   12–17 ms on top of the sync cost.
3. **Small repaints only.** Ultralight's CPU renderer repaints the whole 1920×1080 view (~50 ms)
   when one paint covers many scattered changes. Fixed card rows, three-layer "meters" instead of
   per-cell restyling, `opacity` instead of `visibility`, and painting state and card separately took
   per-change paint p99 from ~55 ms to ~8 ms (Vulkan run).
4. **Paced card** (≤ 1 update / 150 ms while sweeping, immediate on rest) and a low-priority UI
   thread so its rasterising doesn't compete with the game.

### What production needs

* **Mac and Linux natives** (only `windows-x64` is built here; the binding's CMake supports
  mac-x64/arm64 and linux-x64) and per-OS jars (~21 MB for Windows).
* **The native change merged** into `pvp/mod/native` (owned by another session) — `copyDirtyRgba`
  and the lock around `viewResize`.
* **Memory**: one full-window CPU surface is ~8 MB at 1080p, ~33 MB at 4K, plus WebCore. A
  window-sized view is simple; a view cropped to the hub's bounding box would halve it.
* **An accelerated path is not needed** for this screen at these numbers. It would only matter for
  heavy animation, and on 26.3 it would need a renderpearl (GL *and* Vulkan) GPU driver, not the
  binding's GL 2.1 one.
* Keep the rules that keep repaints small (see README "Small repaints only") — a page that
  restyles dozens of elements per frame will hit the 50 ms full repaint again.
* Hearth coins, search, Classes and the world strip's action are visual stand-ins in this prototype.
<!-- verdict:end -->

## How it was measured

`./gradlew hubPerf -Pbackend=opengl|vulkan` (a client game test, `src/gametest`):

* Window 1920×1080 (GUI scale auto = 4), **vsync off, frame rate uncapped**, render distance 5,
  survival world at seed 8675309, noon, clear weather, mobs off. The player stands on a stone
  platform facing a chest.
* Realistic inventory: diamond sword (Sharpness IV, Looting II, Unbreaking III, worn), bow,
  enchanted iron pickaxe, shovel, torches, bread, cobblestone, planks, 18 storage stacks, full iron
  armour (enchanted helmet), shield; the chest holds 27 stacks.
* Scenarios, each ≥ 10 s split over two interleaved rounds (vanilla, VOID, vanilla, VOID…), so
  load from anything else on the machine lands on both sides:
  `idle` (no screen) · `vanilla_inventory` · `void_inventory` (pointer parked on the window) ·
  `void_inventory_hover` (the pointer sweeps every well, ~30 wells/s, every frame — continuous
  repaints and card changes) · `vanilla_chest` · `void_chest` · `void_chest_hover`, then two
  baselines: `void_inventory_hover_sync` (render thread waits for Ultralight every frame — what a
  single-threaded integration costs) and `void_inventory_hover_sync_fullreadback` (that plus the
  binding's original whole-surface `readPixels()` path instead of the new dirty-rectangle copy).
* Per frame: frame interval (what fps is made of), CPU time in `renderFrame`, GPU time
  (renderpearl timestamp queries around the frame), whether a client tick ran (the client game
  test runs ticks in lock-step with the test thread and the integrated server, so tick frames carry
  a sync wait; the `frameMsNoTick` series excludes them), and the VOID screen's own costs on the
  render thread (post, wait, copy, upload) and on Ultralight's thread (update, render, JS).
* The Map page: without Atlas its stand-in (`void_map_standin`); with `-Patlas` the Map page against
  Atlas's own map screen (M), idle (`void_map` / `atlas_map`) and panned by the same drag
  (`void_map_pan` / `atlas_map_pan`: the left button held while the pointer circles over the map,
  every frame), interleaved. Atlas's screen runs with its live backdrop on for the comparison (the
  world drawn behind the map every frame, as the hub draws it); `atlas_map_pan_frozen` is Atlas's
  default still backdrop, reported, not gated. Plus the first Map open (when the widget is made),
  10 Map re-opens and 10 opens of Atlas's own screen. Results go to `build/perf/<backend>-atlas.json`
  and the "with VOID Atlas" sections below.
* Open latency: from the key event (or the chest use) to the end of the first frame that shows
  the screen, 10× each for vanilla E, VOID E and VOID R-Shift, 5× each for the chests. The first
  VOID open of the run is cold (`voidhub.prewarm=false` in the perf run) and is reported with its
  Ultralight start-up breakdown and the memory it added.

Budgets (the gate fails the build when exceeded, VOID vs vanilla in the same run):
avg frame time +1.0 ms · p99 +2.0 ms · no frame over 33 ms while hovering · first open ≤ 300 ms
(excluding the one-off Ultralight start-up, which a normal session pays while the world loads) ·
every re-open ≤ 150 ms. With Atlas, the Map page vs Atlas's own map screen: avg +1.0 ms (idle and
panning) · p99 +2.0 ms panning · no more frames over 33 ms while panning than Atlas's screen ·
first Map open ≤ 300 ms · every Map re-open ≤ 150 ms.

## Results

Generated by the gate; each backend's section is rewritten on every run.

<!-- perf:opengl:begin -->
### OpenGL, without VOID Atlas — PASS (generated 2026-10-07 02:36)

Device: NVIDIA GeForce RTX 4060/PCIe/SSE2, OpenGL (3.3.0 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 53% / 25% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 7125 | 713 | 1.402 | 1.183 | 2.373 | 4.130 | 23.09 | 1.335 | 0.633 | 0 |
| vanilla_inventory | 5491 | 550 | 1.819 | 1.589 | 2.909 | 4.606 | 8.09 | 1.744 | 0.726 | 0 |
| void_inventory | 5002 | 501 | 1.996 | 1.784 | 3.154 | 5.196 | 16.19 | 1.909 | 0.721 | 0 |
| vanilla_inventory_hover | 5004 | 501 | 1.996 | 1.795 | 3.146 | 4.717 | 10.72 | 1.913 | 0.724 | 0 |
| void_inventory_hover | 4660 | 467 | 2.143 | 1.917 | 3.822 | 5.389 | 11.21 | 2.047 | 0.736 | 0 |
| vanilla_chest | 5448 | 546 | 1.833 | 1.578 | 3.047 | 4.817 | 8.76 | 1.756 | 0.707 | 0 |
| void_chest | 6254 | 626 | 1.597 | 1.400 | 2.519 | 3.887 | 10.36 | 1.533 | 0.707 | 0 |
| vanilla_chest_hover | 5743 | 575 | 1.738 | 1.518 | 2.818 | 4.397 | 10.61 | 1.670 | 0.704 | 0 |
| void_chest_hover | 5371 | 538 | 1.858 | 1.630 | 3.331 | 4.944 | 8.91 | 1.777 | 0.709 | 0 |
| void_map_standin | 7831 | 784 | 1.275 | 1.120 | 1.927 | 3.248 | 8.30 | 1.225 | 0.669 | 0 |
| void_inventory_hover_sync | 1814 | 363 | 2.754 | 2.292 | 5.319 | 11.718 | 17.74 | 2.634 | 0.775 | 0 |
| void_inventory_hover_sync_fullreadback | 1013 | 203 | 4.928 | 1.975 | 13.681 | 18.719 | 26.20 | 4.747 | 1.651 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg +0.185 ms [+0.305, +0.069, +0.080, +0.290], p99 +0.767 ms [+0.949, +0.050, +0.668, +0.865] · inventory while hovering avg +0.149 ms [+0.468, +0.004, -0.045, +0.295], p99 +0.613 ms [+1.628, +0.269, -0.556, +0.958] · chest avg -0.209 ms, p99 -0.499 ms

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.004 / 0.010 / 0.05 | 0.000 | 0.000 | 0.004 | 0.000 | 0 | 0 KB | 0.012 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.013 / 0.137 / 0.97 | 0.001 | 0.000 | 0.007 | 0.004 | 800 | 102 KB | 0.404 | 2.20 / 11.36 | — |
| void_chest | 0.003 / 0.008 / 0.06 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.008 | 0.00 / 0.00 | — |
| void_chest_hover | 0.009 / 0.084 / 0.42 | 0.001 | 0.000 | 0.005 | 0.003 | 843 | 59 KB | 0.340 | 2.04 / 9.11 | — |
| void_map_standin | 0.003 / 0.007 / 0.18 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.006 | 0.00 / 0.00 | — |
| void_inventory_hover_sync | 0.512 / 8.680 / 10.84 | 0.002 | 0.497 | 0.007 | 0.006 | 379 | 76 KB | 0.486 | 2.25 / 9.99 | — |
| void_inventory_hover_sync_fullreadback | 2.746 / 15.817 / 21.12 | 0.002 | 2.482 | 0.000 | 0.261 | 322 | 8100 KB | 0.734 | 2.26 / 10.05 | — |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 9.3 | 8.0 | 17.5 |
| VOID inventory (E) | 11.8 | 11.3 | 13.8 |
| VOID inventory (R-Shift) | 10.2 | 10.0 | 11.4 |
| VOID screen init → first frame | 6.7 | 6.4 | 8.5 |
| vanilla chest | 64.8 | 58.1 | 90.7 |
| VOID chest | 74.0 | 66.0 | 102.9 |

First open after pre-warm: 79.2 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 382.7 ms (start-up 99.9 ms — natives 22.4, renderer 17.3, view 18.9, page 40.6 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +59.3 MB private bytes, +-27.4 MB working set, +6.8 MB Java heap (process after: 2988 MB private, 1565 MB working set).

<!-- perf:opengl:end -->

<!-- perf:vulkan:begin -->
### Vulkan, without VOID Atlas — PASS (generated 2026-10-07 02:45)

Device: NVIDIA GeForce RTX 4060, Vulkan (1.4.351 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 74% / 37% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 12257 | 1227 | 0.815 | 0.712 | 1.274 | 2.547 | 23.73 | 0.779 | 0.392 | 0 |
| vanilla_inventory | 9816 | 983 | 1.017 | 0.961 | 1.515 | 2.873 | 7.95 | 0.978 | 0.407 | 0 |
| void_inventory | 9558 | 957 | 1.045 | 0.976 | 1.473 | 2.794 | 27.85 | 1.004 | 0.434 | 0 |
| vanilla_inventory_hover | 8848 | 886 | 1.129 | 1.051 | 1.648 | 3.072 | 37.42 | 1.085 | 0.410 | 1 |
| void_inventory_hover | 8264 | 827 | 1.209 | 1.063 | 2.006 | 3.414 | 31.45 | 1.155 | 0.441 | 0 |
| vanilla_chest | 9449 | 946 | 1.057 | 0.974 | 1.520 | 2.919 | 25.84 | 1.016 | 0.402 | 0 |
| void_chest | 9047 | 906 | 1.104 | 0.993 | 1.697 | 3.252 | 38.13 | 1.061 | 0.459 | 1 |
| vanilla_chest_hover | 9384 | 940 | 1.064 | 0.980 | 1.572 | 3.086 | 40.02 | 1.025 | 0.404 | 1 |
| void_chest_hover | 9433 | 944 | 1.059 | 0.948 | 1.691 | 3.095 | 31.37 | 1.017 | 0.408 | 0 |
| void_map_standin | 12366 | 1226 | 0.816 | 0.703 | 1.218 | 2.284 | 114.01 | 0.786 | 0.460 | 3 |
| void_inventory_hover_sync | 3540 | 708 | 1.412 | 0.986 | 3.226 | 10.041 | 41.29 | 1.363 | 0.459 | 3 |
| void_inventory_hover_sync_fullreadback | 1562 | 313 | 3.200 | 1.020 | 12.087 | 19.289 | 45.52 | 3.099 | 0.616 | 1 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg +0.030 ms [+0.050, +0.011, +0.101, -0.124], p99 -0.051 ms [+0.005, -0.107, +0.336, -0.479] · inventory while hovering avg +0.084 ms [+0.090, +0.177, +0.077, -0.009], p99 +0.279 ms [+0.309, +0.553, +0.250, +0.189] · chest avg +0.009 ms, p99 +0.116 ms

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.003 / 0.008 / 0.12 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.006 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.007 / 0.048 / 0.78 | 0.001 | 0.000 | 0.005 | 0.001 | 651 | 85 KB | 0.406 | 4.71 / 22.52 | — |
| void_chest | 0.004 / 0.009 / 0.23 | 0.000 | 0.000 | 0.004 | 0.000 | 0 | 0 KB | 0.013 | 0.00 / 0.00 | — |
| void_chest_hover | 0.006 / 0.034 / 0.36 | 0.001 | 0.000 | 0.004 | 0.001 | 778 | 51 KB | 0.247 | 2.76 / 11.52 | — |
| void_map_standin | 0.003 / 0.007 / 0.15 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.006 | 0.00 / 0.00 | — |
| void_inventory_hover_sync | 0.322 / 6.386 / 24.25 | 0.001 | 0.313 | 0.005 | 0.002 | 396 | 78 KB | 0.264 | 2.26 / 10.34 | — |
| void_inventory_hover_sync_fullreadback | 1.983 / 16.333 / 40.97 | 0.002 | 1.832 | 0.000 | 0.149 | 319 | 8100 KB | 0.586 | 2.73 / 13.43 | — |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 8.9 | 6.9 | 20.1 |
| VOID inventory (E) | 11.6 | 11.3 | 16.4 |
| VOID inventory (R-Shift) | 10.7 | 10.1 | 13.5 |
| VOID screen init → first frame | 8.1 | 7.9 | 10.6 |
| vanilla chest | 66.3 | 56.3 | 109.4 |
| VOID chest | 93.8 | 73.5 | 185.2 |

First open after pre-warm: 117.0 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 652.2 ms (start-up 293.8 ms — natives 66.8, renderer 26.4, view 13.7, page 166.0 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +117.2 MB private bytes, +105.2 MB working set, +-4.4 MB Java heap (process after: 2760 MB private, 1671 MB working set).

<!-- perf:vulkan:end -->

<!-- perf:opengl-atlas:begin -->
### OpenGL, with VOID Atlas — PASS (generated 2026-10-21 16:30)

Device: NVIDIA GeForce RTX 4060/PCIe/SSE2, OpenGL (3.3.0 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 7% / 1% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 5768 | 578 | 1.731 | 1.616 | 2.312 | 3.498 | 8.67 | 1.669 | 1.075 | 0 |
| vanilla_inventory | 5275 | 528 | 1.893 | 1.741 | 3.030 | 3.738 | 9.91 | 1.827 | 1.350 | 0 |
| void_inventory | 5550 | 556 | 1.799 | 1.696 | 2.199 | 3.365 | 7.77 | 1.741 | 1.269 | 0 |
| vanilla_inventory_hover | 5282 | 529 | 1.890 | 1.769 | 2.755 | 3.635 | 14.04 | 1.826 | 1.361 | 0 |
| void_inventory_hover | 5049 | 506 | 1.977 | 1.719 | 3.355 | 4.402 | 8.50 | 1.905 | 1.393 | 0 |
| vanilla_chest | 5796 | 581 | 1.722 | 1.622 | 2.193 | 3.253 | 7.74 | 1.667 | 1.189 | 0 |
| void_chest | 5991 | 600 | 1.666 | 1.577 | 1.977 | 3.196 | 6.29 | 1.612 | 1.103 | 0 |
| vanilla_chest_hover | 5657 | 567 | 1.765 | 1.666 | 2.428 | 3.244 | 6.47 | 1.709 | 1.228 | 0 |
| void_chest_hover | 5389 | 540 | 1.852 | 1.588 | 3.205 | 4.101 | 10.52 | 1.787 | 1.242 | 0 |
| void_inventory_hover_sync | 2144 | 429 | 2.331 | 1.741 | 4.130 | 11.362 | 24.77 | 2.262 | 1.309 | 0 |
| void_inventory_hover_sync_fullreadback | 989 | 198 | 5.051 | 2.146 | 12.936 | 25.604 | 34.74 | 4.878 | 1.886 | 2 |
| void_inventory_hover_gs2 | 2604 | 521 | 1.918 | 1.689 | 3.114 | 4.126 | 8.80 | 1.855 | 1.357 | 0 |
| void_chest_hover_gs2 | 2646 | 530 | 1.888 | 1.606 | 3.342 | 4.496 | 11.19 | 1.827 | 1.266 | 0 |
| atlas_map | 3952 | 396 | 2.526 | 2.396 | 3.604 | 4.345 | 8.49 | 2.455 | 1.350 | 0 |
| void_map | 4150 | 416 | 2.404 | 2.282 | 3.474 | 4.146 | 7.54 | 2.336 | 1.258 | 0 |
| atlas_map_pan | 4051 | 406 | 2.464 | 2.351 | 3.549 | 4.131 | 9.97 | 2.393 | 1.336 | 0 |
| void_map_pan | 4219 | 423 | 2.366 | 2.255 | 3.429 | 3.949 | 6.81 | 2.297 | 1.277 | 0 |
| atlas_map_pan_frozen | 9203 | 921 | 1.085 | 1.032 | 1.279 | 2.329 | 6.17 | 1.049 | 0.297 | 0 |
| void_map_pan_frozen | 10261 | 1027 | 0.973 | 0.922 | 1.111 | 2.251 | 4.44 | 0.939 | 0.294 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg -0.088 ms [-0.153, -0.017, -0.196, -0.023], p99 -0.292 ms [-0.446, +0.103, -0.559, -0.138] · inventory while hovering avg +0.105 ms [+0.117, +0.108, +0.022, +0.103], p99 +0.968 ms [+0.946, +0.990, +0.520, +1.091] · chest avg -0.057 ms, p99 -0.045 ms

**Map page vs Atlas's own map screen** (median of the per-round differences), both drawing the live world: idle avg -0.124 ms [-0.137, -0.151, -0.110, -0.091], p99 -0.065 ms [-0.452, -0.018, -0.113, +0.302] · panning avg -0.109 ms [-0.100, -0.127, -0.046, -0.118], p99 -0.226 ms [-0.237, -0.258, -0.116, -0.216] · both over Atlas's still backdrop, panning avg -0.112 ms [-0.124, -0.134, -0.094, -0.100], p99 -0.106 ms [-0.121, -0.124, -0.091, -0.085]

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | UI thread CPU per paint | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| void_inventory | 0.002 / 0.004 / 0.03 | 0.000 | 0.000 | 0.002 | 0.000 | 0 | 0 KB | 0.003 | 0.00 / 0.00 | 0.00 ms | — |
| void_inventory_hover | 0.007 / 0.052 / 0.41 | 0.001 | 0.000 | 0.004 | 0.002 | 787 | 70 KB | 0.389 | 2.35 / 19.84 | 2.82 ms | — |
| void_chest | 0.002 / 0.004 / 0.02 | 0.000 | 0.000 | 0.002 | 0.000 | 0 | 0 KB | 0.003 | 0.00 / 0.00 | 0.00 ms | — |
| void_chest_hover | 0.006 / 0.055 / 0.52 | 0.001 | 0.000 | 0.003 | 0.002 | 810 | 62 KB | 0.430 | 2.72 / 21.73 | 3.36 ms | — |
| void_inventory_hover_sync | 0.452 / 9.169 / 22.01 | 0.001 | 0.444 | 0.004 | 0.003 | 398 | 74 KB | 0.442 | 2.36 / 21.43 | 2.75 ms | — |
| void_inventory_hover_sync_fullreadback | 2.607 / 20.501 / 30.95 | 0.002 | 2.425 | 0.000 | 0.179 | 323 | 8100 KB | 0.932 | 2.82 / 21.51 | 7.21 ms | — |
| void_inventory_hover_gs2 | 0.005 / 0.034 / 0.20 | 0.001 | 0.000 | 0.003 | 0.002 | 393 | 21 KB | 0.343 | 2.09 / 15.97 | 2.50 ms | — |
| void_chest_hover_gs2 | 0.005 / 0.035 / 0.17 | 0.001 | 0.000 | 0.003 | 0.002 | 371 | 18 KB | 0.448 | 2.94 / 24.96 | 3.58 ms | — |
| void_map | 0.002 / 0.004 / 0.03 | 0.000 | 0.000 | 0.002 | 0.000 | 0 | 0 KB | 0.005 | 0.00 / 0.00 | 0.00 ms | 0.455 / 0.669 |
| void_map_pan | 0.002 / 0.004 / 0.02 | 0.000 | 0.000 | 0.002 | 0.000 | 0 | 0 KB | 0.005 | 0.00 / 0.00 | 0.00 ms | 0.422 / 0.628 |
| void_map_pan_frozen | 0.002 / 0.003 / 0.01 | 0.000 | 0.000 | 0.002 | 0.000 | 0 | 0 KB | 0.002 | 0.00 / 0.00 | 0.00 ms | 0.394 / 0.533 |

`*_gs2` rows: 1920×1080 at GUI scale 2 (layout unit under 1: the page's own CSS zoom), there for the UI thread's cost (Ultralight's CPU update, script and paint per uploaded change, gated at 10 ms); their frame times are not compared with vanilla.

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 7.0 | 6.0 | 13.6 |
| VOID inventory (E) | 11.7 | 11.6 | 12.5 |
| VOID inventory (R-Shift) | 10.6 | 10.3 | 12.7 |
| VOID screen init → first frame | 6.8 | 6.6 | 7.6 |
| vanilla chest | 62.0 | 56.6 | 84.9 |
| VOID chest | 76.6 | 62.2 | 136.5 |
| Map page (R-Shift), re-open | 6.2 | 6.1 | 6.7 |
| Atlas's own map screen | 4.7 | 4.4 | 6.0 |

First Map page open: 44.0 ms key → first painted frame (gated), of which making Atlas's widget 2.88 ms (lazily, on the first Map frame; kept for every later open).

First open after pre-warm: 105.6 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 234.7 ms (start-up 61.4 ms — natives 9.1, renderer 8.3, view 6.9, page 36.6 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +96.3 MB private bytes, +88.0 MB working set, +8.5 MB Java heap (process after: 3281 MB private, 1808 MB working set).

<!-- perf:opengl-atlas:end -->

<!-- perf:vulkan-atlas:begin -->
### Vulkan, with VOID Atlas — PASS (generated 2026-10-07 05:19)

Device: NVIDIA GeForce RTX 4060, Vulkan (1.4.351 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 53% / 66% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 16074 | 804 | 1.243 | 1.089 | 2.051 | 3.561 | 44.68 | 1.192 | 0.639 | 3 |
| vanilla_inventory | 14802 | 741 | 1.350 | 1.185 | 2.171 | 3.733 | 29.13 | 1.299 | 0.768 | 0 |
| void_inventory | 14922 | 747 | 1.339 | 1.223 | 2.105 | 3.445 | 29.21 | 1.288 | 0.679 | 0 |
| vanilla_inventory_hover | 15248 | 763 | 1.311 | 1.220 | 2.027 | 3.272 | 43.24 | 1.262 | 0.698 | 1 |
| void_inventory_hover | 12929 | 647 | 1.546 | 1.422 | 2.644 | 4.039 | 19.64 | 1.478 | 0.665 | 0 |
| vanilla_chest | 16330 | 817 | 1.224 | 1.112 | 1.961 | 3.564 | 23.73 | 1.179 | 0.645 | 0 |
| void_chest | 16285 | 815 | 1.227 | 1.143 | 1.790 | 3.122 | 35.15 | 1.184 | 0.646 | 1 |
| vanilla_chest_hover | 14434 | 722 | 1.385 | 1.276 | 2.131 | 3.517 | 45.96 | 1.334 | 0.747 | 3 |
| void_chest_hover | 14486 | 725 | 1.380 | 1.198 | 2.369 | 4.280 | 50.82 | 1.322 | 0.646 | 5 |
| void_inventory_hover_sync | 2670 | 529 | 1.891 | 1.303 | 4.174 | 11.319 | 60.51 | 1.824 | 0.735 | 2 |
| void_inventory_hover_sync_fullreadback | 573 | 114 | 8.801 | 10.870 | 20.544 | 27.603 | 96.18 | 8.365 | 2.094 | 3 |
| atlas_map | 7895 | 395 | 2.531 | 2.461 | 3.792 | 5.182 | 23.71 | 2.457 | 0.893 | 0 |
| void_map | 8533 | 427 | 2.343 | 2.301 | 3.358 | 4.804 | 38.03 | 2.275 | 0.929 | 1 |
| atlas_map_pan | 8586 | 430 | 2.328 | 2.284 | 3.391 | 4.603 | 33.85 | 2.260 | 0.894 | 1 |
| void_map_pan | 9121 | 456 | 2.191 | 2.169 | 3.051 | 4.074 | 10.93 | 2.125 | 0.910 | 0 |
| atlas_map_pan_frozen | 13450 | 673 | 1.486 | 1.479 | 1.991 | 3.150 | 17.84 | 1.438 | 0.206 | 0 |
| void_map_pan_frozen | 17313 | 867 | 1.154 | 1.028 | 1.571 | 2.596 | 58.72 | 1.119 | 0.240 | 4 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg +0.136 ms [+0.158, +0.180, -0.697, +0.114], p99 +0.121 ms [+0.046, +0.584, -2.468, +0.196] · inventory while hovering avg +0.204 ms [-0.044, +0.025, +0.562, +0.383], p99 +0.809 ms [+0.430, +0.427, +1.188, +1.661] · chest avg +0.009 ms, p99 -0.332 ms

**Map page vs Atlas's own map screen** (median of the per-round differences), both drawing the live world: idle avg -0.107 ms [-0.927, -0.274, +0.069, +0.061], p99 -0.327 ms [-3.419, -0.497, -0.158, +0.433] · panning avg -0.098 ms [-0.534, -0.206, +0.009, +0.052], p99 -0.298 ms [-1.207, -0.537, -0.059, +0.360] · both over Atlas's still backdrop, panning avg -0.340 ms [-0.339, -0.426, -0.192, -0.341], p99 -0.525 ms [-0.617, -1.527, +0.087, -0.434]

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.004 / 0.011 / 1.73 | 0.000 | 0.000 | 0.004 | 0.000 | 0 | 0 KB | 0.010 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.010 / 0.098 / 2.41 | 0.001 | 0.000 | 0.007 | 0.002 | 1304 | 102 KB | 0.491 | 4.34 / 41.27 | — |
| void_chest | 0.003 / 0.009 / 0.16 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.011 | 0.00 / 0.00 | — |
| void_chest_hover | 0.007 / 0.065 / 0.66 | 0.001 | 0.000 | 0.005 | 0.001 | 1300 | 72 KB | 0.434 | 4.40 / 61.23 | — |
| void_inventory_hover_sync | 0.418 / 8.339 / 58.30 | 0.001 | 0.409 | 0.006 | 0.003 | 392 | 74 KB | 0.368 | 2.41 / 9.93 | — |
| void_inventory_hover_sync_fullreadback | 5.661 / 21.022 / 90.54 | 0.004 | 5.253 | 0.001 | 0.403 | 282 | 8100 KB | 2.679 | 4.65 / 16.57 | — |
| void_map | 0.006 / 0.012 / 0.14 | 0.000 | 0.000 | 0.006 | 0.000 | 0 | 0 KB | 0.015 | 0.00 / 0.00 | 0.752 / 1.194 |
| void_map_pan | 0.005 / 0.011 / 0.12 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.016 | 0.00 / 0.00 | 0.672 / 1.036 |
| void_map_pan_frozen | 0.003 / 0.008 / 0.20 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.006 | 0.00 / 0.00 | 0.585 / 0.923 |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 6.5 | 5.1 | 17.1 |
| VOID inventory (E) | 9.1 | 8.6 | 11.7 |
| VOID inventory (R-Shift) | 9.4 | 8.7 | 11.7 |
| VOID screen init → first frame | 6.2 | 5.9 | 8.4 |
| vanilla chest | 60.6 | 56.2 | 80.6 |
| VOID chest | 79.7 | 61.3 | 154.9 |
| Map page (R-Shift), re-open | 5.4 | 5.4 | 7.2 |
| Atlas's own map screen | 5.4 | 4.9 | 10.1 |

First Map page open: 94.2 ms key → first painted frame (gated), of which making Atlas's widget 6.73 ms (lazily, on the first Map frame; kept for every later open).

First open after pre-warm: 96.4 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 295.4 ms (start-up 59.8 ms — natives 11.0, renderer 9.6, view 8.6, page 30.0 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +15.1 MB private bytes, +175.4 MB working set, +19.0 MB Java heap (process after: 3046 MB private, 1815 MB working set).

<!-- perf:vulkan-atlas:end -->
