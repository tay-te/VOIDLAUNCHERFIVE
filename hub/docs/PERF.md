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

### The Map page with VOID Atlas (2026-10-07, on Atlas's hub API, 6c4fb205)

Measured on the same machine while other game runs were going (CPU load 25–85 %); all four gates
PASS (`hubPerf -Pbackend=opengl|vulkan`, each with and without `-Patlas`; the Vulkan run with Atlas
at `-Pseconds=20` after two runs at 82–96 % machine load had load spikes in every scenario, Atlas's
own screen included):

| | OpenGL | Vulkan |
|---|---|---|
| Map page panning vs Atlas's own map screen panned the same way, both drawing the live world: avg / p99 | −0.20 / −0.18 ms | −0.45 / −3.75 ms |
| The same, both over Atlas's still backdrop: avg / p99 | −0.09 / +0.14 ms | −0.21 / +0.10 ms |
| Map page idle vs Atlas's screen idle: avg / p99 | +0.08 / +0.36 ms | −0.30 / −1.45 ms |
| Map page panning, live world (the default): avg / p99 / frames over 33 ms | 2.75 / 6.27 ms / 0 | 2.54 / 6.24 ms / 1 (Atlas's screen: 12) |
| Map page panning, still backdrop (`voidhub.frozenMap`): avg / p99 | 1.65 / 4.24 ms | 1.34 / 3.52 ms |
| Atlas's widget draw on the render thread while panning | 0.63 ms | 0.70 ms |
| Ultralight uploads while the map pans (or sits) | 0 | 0 |
| First Map page open, key → first frame (widget made then) | 60.9 ms (widget 3.9 ms) | 149.0 ms (widget 80.2 ms) |
| Map page re-open, worst | 17.5 ms | 36.4 ms |
| Stand-in page without Atlas, idle avg / p99 | 1.27 / 3.25 ms | 0.82 / 2.28 ms |

* **The hub adds nothing measurable around Atlas's map**, live or still: Atlas draws the same page
  in both; the hub's islands and window frame are one premultiplied quad, already uploaded.
* **Nothing is drawn twice.** The pointer over the map is never sent to Ultralight: zero uploads in
  every Map scenario.
* **The still backdrop saves 1.1 ms a frame on OpenGL, 1.2 ms on Vulkan** while panning, but is off
  by default: `drawFrozenWorld` dims the still to Atlas's 76 %, where the hub's design is the live
  world at 34 % (README).
* **The first Map open now makes Atlas's shared map page** (when M has not been opened yet this
  session): 3.9 ms on OpenGL, 80 ms on Vulkan in this run (under load), within the 300 ms gate.

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
### OpenGL, with VOID Atlas — PASS (generated 2026-10-07 03:30)

Device: NVIDIA GeForce RTX 4060/PCIe/SSE2, OpenGL (3.3.0 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 81% / 65% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 5321 | 533 | 1.876 | 1.650 | 3.220 | 5.131 | 15.62 | 1.774 | 0.823 | 0 |
| vanilla_inventory | 4275 | 428 | 2.334 | 2.114 | 4.098 | 6.158 | 27.14 | 2.204 | 0.853 | 0 |
| void_inventory | 5109 | 512 | 1.954 | 1.726 | 3.115 | 4.964 | 11.11 | 1.858 | 0.912 | 0 |
| vanilla_inventory_hover | 4516 | 453 | 2.210 | 2.019 | 3.583 | 5.417 | 13.68 | 2.098 | 0.940 | 0 |
| void_inventory_hover | 4373 | 438 | 2.283 | 2.063 | 4.075 | 5.547 | 13.53 | 2.165 | 0.937 | 0 |
| vanilla_chest | 4784 | 479 | 2.086 | 1.855 | 3.634 | 5.589 | 13.69 | 1.976 | 0.913 | 0 |
| void_chest | 4974 | 499 | 2.006 | 1.718 | 3.406 | 5.546 | 12.85 | 1.899 | 0.935 | 0 |
| vanilla_chest_hover | 5092 | 510 | 1.961 | 1.639 | 3.317 | 5.574 | 14.19 | 1.864 | 0.961 | 0 |
| void_chest_hover | 4597 | 460 | 2.172 | 1.951 | 4.018 | 5.928 | 16.30 | 2.060 | 0.851 | 0 |
| void_inventory_hover_sync | 1498 | 300 | 3.335 | 2.726 | 6.496 | 12.947 | 15.73 | 3.167 | 0.860 | 0 |
| void_inventory_hover_sync_fullreadback | 763 | 153 | 6.554 | 2.828 | 16.082 | 22.203 | 26.06 | 6.264 | 2.022 | 0 |
| atlas_map | 3145 | 315 | 3.172 | 2.882 | 5.073 | 6.923 | 14.69 | 3.039 | 0.977 | 0 |
| void_map | 3057 | 307 | 3.262 | 3.086 | 5.299 | 7.300 | 15.37 | 3.113 | 0.926 | 0 |
| atlas_map_pan | 3344 | 335 | 2.981 | 2.677 | 4.718 | 6.439 | 13.30 | 2.852 | 0.970 | 0 |
| void_map_pan | 3631 | 364 | 2.749 | 2.380 | 4.345 | 6.268 | 13.35 | 2.632 | 0.958 | 0 |
| atlas_map_pan_frozen | 5660 | 567 | 1.764 | 1.551 | 2.698 | 4.594 | 20.05 | 1.683 | 0.518 | 0 |
| void_map_pan_frozen | 6037 | 604 | 1.654 | 1.552 | 2.465 | 4.237 | 10.51 | 1.577 | 0.504 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg -0.437 ms [-0.624, -0.249, -0.800, -0.115], p99 -1.014 ms [-0.934, -1.093, -2.200, -0.309] · inventory while hovering avg +0.089 ms [+0.189, +0.133, +0.045, -0.114], p99 +0.193 ms [+0.104, +0.281, +0.617, -0.352] · chest avg -0.201 ms, p99 -0.209 ms

**Map page vs Atlas's own map screen** (median of the per-round differences), both drawing the live world: idle avg +0.084 ms [+0.019, -0.210, +0.267, +0.149], p99 +0.357 ms [+3.538, +0.018, +0.695, -0.184] · panning avg -0.199 ms [+0.013, -0.226, -0.172, -0.391], p99 -0.176 ms [-0.244, -0.111, -0.108, -0.242] · both over Atlas's still backdrop, panning avg -0.088 ms [-0.400, -0.104, +0.015, -0.072], p99 +0.140 ms [-2.230, +0.531, -0.049, +0.329]

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.005 / 0.011 / 0.09 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.012 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.015 / 0.192 / 2.38 | 0.002 | 0.000 | 0.008 | 0.006 | 740 | 107 KB | 0.474 | 2.56 / 12.11 | — |
| void_chest | 0.005 / 0.012 / 0.38 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.026 | 0.00 / 0.00 | — |
| void_chest_hover | 0.013 / 0.117 / 0.85 | 0.001 | 0.000 | 0.007 | 0.004 | 773 | 63 KB | 0.448 | 2.51 / 11.61 | — |
| void_inventory_hover_sync | 0.675 / 9.482 / 12.43 | 0.002 | 0.654 | 0.010 | 0.009 | 370 | 84 KB | 0.621 | 2.42 / 11.17 | — |
| void_inventory_hover_sync_fullreadback | 3.872 / 17.521 / 20.66 | 0.003 | 3.495 | 0.000 | 0.373 | 310 | 8100 KB | 1.053 | 2.54 / 11.11 | — |
| void_map | 0.007 / 0.014 / 0.12 | 0.000 | 0.000 | 0.007 | 0.000 | 0 | 0 KB | 0.034 | 0.00 / 0.00 | 0.776 / 1.368 |
| void_map_pan | 0.005 / 0.012 / 0.11 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.027 | 0.00 / 0.00 | 0.633 / 1.143 |
| void_map_pan_frozen | 0.004 / 0.010 / 0.12 | 0.000 | 0.000 | 0.004 | 0.000 | 0 | 0 KB | 0.012 | 0.00 / 0.00 | 0.623 / 1.017 |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 10.1 | 8.7 | 20.8 |
| VOID inventory (E) | 13.8 | 13.7 | 16.4 |
| VOID inventory (R-Shift) | 12.9 | 12.5 | 15.2 |
| VOID screen init → first frame | 7.7 | 7.3 | 9.9 |
| vanilla chest | 67.0 | 63.3 | 91.0 |
| VOID chest | 67.5 | 63.8 | 168.2 |
| Map page (R-Shift), re-open | 8.6 | 7.5 | 17.5 |
| Atlas's own map screen | 7.3 | 7.1 | 8.4 |

First Map page open: 60.9 ms key → first painted frame (gated), of which making Atlas's widget 3.86 ms (lazily, on the first Map frame; kept for every later open).

First open after pre-warm: 112.8 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 335.0 ms (start-up 74.8 ms — natives 14.0, renderer 14.0, view 12.0, page 34.2 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +-65.9 MB private bytes, +-71.4 MB working set, +0.6 MB Java heap (process after: 3048 MB private, 1599 MB working set).

<!-- perf:opengl-atlas:end -->

<!-- perf:vulkan-atlas:begin -->
### Vulkan, with VOID Atlas — PASS (generated 2026-10-07 03:40)

Device: NVIDIA GeForce RTX 4060, Vulkan (1.4.351 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 82% / 74% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 13862 | 692 | 1.445 | 1.332 | 2.326 | 4.297 | 86.82 | 1.378 | 0.463 | 3 |
| vanilla_inventory | 15580 | 780 | 1.283 | 1.127 | 2.094 | 3.486 | 26.12 | 1.231 | 0.599 | 0 |
| void_inventory | 15275 | 762 | 1.312 | 1.106 | 2.198 | 3.679 | 79.14 | 1.259 | 0.640 | 2 |
| vanilla_inventory_hover | 15527 | 777 | 1.287 | 1.101 | 2.129 | 3.546 | 91.35 | 1.236 | 0.601 | 3 |
| void_inventory_hover | 14716 | 736 | 1.358 | 1.192 | 2.347 | 3.774 | 49.48 | 1.303 | 0.644 | 2 |
| vanilla_chest | 13731 | 687 | 1.456 | 1.313 | 2.369 | 4.062 | 46.09 | 1.395 | 0.505 | 3 |
| void_chest | 10904 | 527 | 1.896 | 1.569 | 3.165 | 6.255 | 333.89 | 1.797 | 0.779 | 10 |
| vanilla_chest_hover | 12822 | 641 | 1.560 | 1.452 | 2.580 | 6.658 | 94.24 | 1.450 | 0.496 | 1 |
| void_chest_hover | 10705 | 521 | 1.921 | 1.585 | 3.265 | 8.308 | 159.88 | 1.777 | 0.791 | 22 |
| void_inventory_hover_sync | 902 | 156 | 6.420 | 2.664 | 19.098 | 72.236 | 290.25 | 5.714 | 1.319 | 23 |
| void_inventory_hover_sync_fullreadback | 776 | 155 | 6.438 | 2.327 | 17.681 | 22.135 | 31.39 | 6.150 | 0.866 | 0 |
| atlas_map | 7487 | 373 | 2.683 | 2.450 | 4.066 | 7.331 | 127.24 | 2.550 | 0.953 | 10 |
| void_map | 7988 | 400 | 2.502 | 2.293 | 3.794 | 7.087 | 67.21 | 2.401 | 0.925 | 10 |
| atlas_map_pan | 7357 | 363 | 2.757 | 2.448 | 4.242 | 8.949 | 124.17 | 2.607 | 1.018 | 12 |
| void_map_pan | 7871 | 394 | 2.539 | 2.328 | 3.944 | 6.237 | 51.99 | 2.450 | 0.956 | 1 |
| atlas_map_pan_frozen | 13210 | 658 | 1.519 | 1.438 | 2.238 | 3.758 | 93.06 | 1.464 | 0.242 | 2 |
| void_map_pan_frozen | 14997 | 748 | 1.336 | 1.250 | 2.062 | 3.521 | 57.50 | 1.288 | 0.289 | 3 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg +0.046 ms [-0.170, +0.118, +0.286, -0.027], p99 +0.145 ms [-0.666, +0.609, +2.594, -0.319] · inventory while hovering avg +0.054 ms [+0.105, +0.090, -0.060, +0.018], p99 +0.234 ms [+0.499, +0.574, -0.032, -0.523] · chest avg +0.296 ms, p99 +0.666 ms

**Map page vs Atlas's own map screen** (median of the per-round differences), both drawing the live world: idle avg -0.300 ms [-0.139, -0.460, -0.704, +0.546], p99 -1.446 ms [-4.117, -1.345, -1.548, +4.396] · panning avg -0.454 ms [-0.830, +0.542, -0.094, -0.813], p99 -3.747 ms [-7.587, +2.599, +0.094, -7.823] · both over Atlas's still backdrop, panning avg -0.209 ms [-0.381, -0.444, -0.037, +0.014], p99 +0.100 ms [-0.289, -2.369, +0.489, +1.011]

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.005 / 0.011 / 1.17 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.013 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.009 / 0.079 / 1.17 | 0.001 | 0.000 | 0.006 | 0.002 | 1433 | 88 KB | 0.340 | 3.15 / 20.83 | — |
| void_chest | 0.007 / 0.015 / 3.87 | 0.000 | 0.000 | 0.007 | 0.000 | 0 | 0 KB | 0.076 | 0.00 / 0.00 | — |
| void_chest_hover | 0.011 / 0.115 / 0.99 | 0.001 | 0.000 | 0.007 | 0.003 | 1052 | 86 KB | 0.713 | 6.25 / 84.66 | — |
| void_inventory_hover_sync | 3.011 / 43.613 / 238.07 | 0.002 | 2.962 | 0.025 | 0.021 | 262 | 190 KB | 1.654 | 5.52 / 33.35 | — |
| void_inventory_hover_sync_fullreadback | 4.137 / 18.093 / 26.90 | 0.004 | 3.783 | 0.001 | 0.349 | 312 | 8100 KB | 1.632 | 3.59 / 14.41 | — |
| void_map | 0.007 / 0.015 / 0.21 | 0.000 | 0.000 | 0.007 | 0.000 | 0 | 0 KB | 0.037 | 0.00 / 0.00 | 0.722 / 1.398 |
| void_map_pan | 0.007 / 0.018 / 0.74 | 0.000 | 0.000 | 0.007 | 0.000 | 0 | 0 KB | 0.044 | 0.00 / 0.00 | 0.698 / 1.528 |
| void_map_pan_frozen | 0.004 / 0.011 / 0.99 | 0.000 | 0.000 | 0.004 | 0.000 | 0 | 0 KB | 0.015 | 0.00 / 0.00 | 0.613 / 1.014 |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 8.8 | 6.0 | 23.8 |
| VOID inventory (E) | 12.2 | 10.8 | 20.6 |
| VOID inventory (R-Shift) | 10.8 | 10.2 | 13.1 |
| VOID screen init → first frame | 8.7 | 7.7 | 17.1 |
| vanilla chest | 33.8 | 7.1 | 93.2 |
| VOID chest | 78.7 | 60.8 | 150.8 |
| Map page (R-Shift), re-open | 9.8 | 6.1 | 36.4 |
| Atlas's own map screen | 6.8 | 6.0 | 12.9 |

First Map page open: 149.0 ms key → first painted frame (gated), of which making Atlas's widget 80.18 ms (lazily, on the first Map frame; kept for every later open).

First open after pre-warm: 144.9 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 877.4 ms (start-up 505.6 ms — natives 76.0, renderer 56.0, view 138.5, page 234.4 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +65.8 MB private bytes, +-13.1 MB working set, +-8.0 MB Java heap (process after: 2989 MB private, 1703 MB working set).

<!-- perf:vulkan-atlas:end -->
