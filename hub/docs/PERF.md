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

### The Map page with VOID Atlas (2026-10-07)

Measured on the same machine while other game runs were going (CPU load 24–91 %); all four gates
PASS (`hubPerf -Pbackend=opengl|vulkan`, each with and without `-Patlas`):

| | OpenGL | Vulkan |
|---|---|---|
| Map page panning vs Atlas's own map screen panned the same way, both drawing the live world: avg / p99 | −0.22 / +0.18 ms | +0.01 / +0.06 ms |
| Map page idle vs Atlas's screen idle: avg / p99 | −0.06 / −0.45 ms | −0.32 / −0.61 ms |
| Map page panning, absolute: avg / p99 / worst (frames over 33 ms) | 3.34 / 6.65 / 13.2 ms (0) | 1.96 / 3.91 / 10.8 ms (0) |
| Atlas's widget draw on the render thread while panning | 0.72 ms | 0.64 ms |
| Ultralight uploads while the map pans (or sits) | 0 | 0 |
| First Map page open, key → first frame (widget made then: lazily) | 53.0 ms (widget 1.7 ms) | 41.9 ms (widget 1.4 ms) |
| Map page re-open avg / worst (Atlas's own screen) | 7.4 / 9.7 ms (8.7 / 14.4) | 4.3 / 6.4 ms (5.6 / 17.9) |
| Stand-in page without Atlas, idle avg / p99 | 1.86 / 4.77 ms | 0.89 / 2.20 ms |

* **The hub adds nothing measurable around Atlas's map.** Atlas draws the same page in both; the
  hub's part — the islands and window frame (one premultiplied quad, already uploaded), its input
  routing — costs no more than Atlas's own screen's frame and dim.
* **Nothing is drawn twice.** The pointer over the map is never sent to Ultralight, so panning
  causes no page work at all: zero uploads in every Map scenario.
* **Atlas's still backdrop is worth ~1.5 ms on OpenGL, ~0.7 ms on Vulkan** (`atlas_map_pan_frozen`:
  Atlas's default, which draws the world once and then skips it). The hub draws the live world behind
  every page, as the design asks (live match, dimmed); a still behind the Map page would need
  Atlas's backdrop as API (see the README).

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
### OpenGL, without VOID Atlas — PASS (generated 2026-10-07 00:48)

Device: NVIDIA GeForce RTX 4060/PCIe/SSE2, OpenGL (3.3.0 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 60% / 47% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 4759 | 477 | 2.094 | 1.986 | 3.588 | 5.627 | 26.57 | 1.963 | 0.981 | 0 |
| vanilla_inventory | 3948 | 395 | 2.529 | 2.384 | 4.039 | 6.109 | 15.20 | 2.392 | 1.144 | 0 |
| void_inventory | 4455 | 446 | 2.240 | 2.145 | 3.515 | 5.387 | 11.18 | 2.125 | 1.200 | 0 |
| vanilla_inventory_hover | 3933 | 394 | 2.537 | 2.359 | 4.218 | 5.797 | 9.96 | 2.406 | 1.237 | 0 |
| void_inventory_hover | 4000 | 401 | 2.494 | 2.287 | 4.298 | 6.072 | 26.34 | 2.358 | 1.196 | 0 |
| vanilla_chest | 4196 | 420 | 2.379 | 2.274 | 3.855 | 5.678 | 16.46 | 2.246 | 0.995 | 0 |
| void_chest | 4659 | 467 | 2.142 | 2.068 | 3.430 | 5.220 | 20.66 | 2.032 | 0.993 | 0 |
| vanilla_chest_hover | 3987 | 400 | 2.503 | 2.391 | 4.261 | 5.793 | 20.09 | 2.372 | 1.000 | 0 |
| void_chest_hover | 3827 | 383 | 2.610 | 2.416 | 4.718 | 6.419 | 14.17 | 2.466 | 1.025 | 0 |
| void_map_standin | 5358 | 537 | 1.863 | 1.775 | 2.837 | 4.774 | 28.43 | 1.768 | 0.983 | 0 |
| void_inventory_hover_sync | 1665 | 334 | 2.996 | 2.418 | 5.969 | 12.840 | 32.55 | 2.860 | 0.780 | 0 |
| void_inventory_hover_sync_fullreadback | 792 | 159 | 6.299 | 2.509 | 16.365 | 21.143 | 25.38 | 6.059 | 1.859 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg -0.187 ms [-1.070, -0.296, -0.037, -0.078], p99 -0.213 ms [-1.019, -0.454, +0.029, +0.282] · inventory while hovering avg +0.119 ms [+0.089, +0.150, +0.204, -0.727], p99 +0.189 ms [+0.464, -0.087, +0.868, -0.147] · chest avg -0.276 ms, p99 -0.361 ms

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.005 / 0.011 / 0.15 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.019 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.016 / 0.198 / 0.77 | 0.002 | 0.000 | 0.009 | 0.005 | 750 | 99 KB | 0.528 | 2.62 / 12.62 | — |
| void_chest | 0.005 / 0.011 / 0.12 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.033 | 0.00 / 0.00 | — |
| void_chest_hover | 0.016 / 0.151 / 6.19 | 0.001 | 0.000 | 0.009 | 0.006 | 677 | 80 KB | 0.683 | 3.53 / 25.25 | — |
| void_map_standin | 0.005 / 0.010 / 0.21 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.013 | 0.00 / 0.00 | — |
| void_inventory_hover_sync | 0.602 / 9.251 / 12.17 | 0.002 | 0.585 | 0.009 | 0.007 | 369 | 89 KB | 0.536 | 2.34 / 10.22 | — |
| void_inventory_hover_sync_fullreadback | 3.771 / 17.376 / 19.23 | 0.003 | 3.436 | 0.000 | 0.331 | 313 | 8100 KB | 1.007 | 2.47 / 10.50 | — |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 10.4 | 8.0 | 22.6 |
| VOID inventory (E) | 12.6 | 12.6 | 16.6 |
| VOID inventory (R-Shift) | 11.5 | 10.6 | 17.3 |
| VOID screen init → first frame | 6.9 | 6.8 | 8.3 |
| vanilla chest | 73.1 | 61.0 | 122.5 |
| VOID chest | 84.4 | 67.3 | 203.9 |

First open after pre-warm: 83.9 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 356.8 ms (start-up 80.5 ms — natives 13.6, renderer 16.3, view 13.8, page 36.1 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +85.9 MB private bytes, +91.5 MB working set, +-26.5 MB Java heap (process after: 2877 MB private, 1519 MB working set).

<!-- perf:opengl:end -->

<!-- perf:vulkan:begin -->
### Vulkan, without VOID Atlas — PASS (generated 2026-10-07 00:56)

Device: NVIDIA GeForce RTX 4060, Vulkan (1.4.351 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 24% / 61% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 12123 | 1213 | 0.824 | 0.778 | 1.132 | 2.030 | 7.89 | 0.795 | 0.604 | 0 |
| vanilla_inventory | 9520 | 953 | 1.049 | 1.002 | 1.471 | 2.696 | 6.84 | 1.013 | 0.584 | 0 |
| void_inventory | 9403 | 941 | 1.062 | 1.001 | 1.505 | 2.706 | 13.90 | 1.026 | 0.601 | 0 |
| vanilla_inventory_hover | 9802 | 981 | 1.019 | 0.959 | 1.393 | 2.613 | 8.90 | 0.984 | 0.589 | 0 |
| void_inventory_hover | 8983 | 899 | 1.112 | 1.026 | 1.809 | 2.911 | 18.01 | 1.070 | 0.597 | 0 |
| vanilla_chest | 9959 | 997 | 1.003 | 0.944 | 1.388 | 2.518 | 25.00 | 0.971 | 0.565 | 0 |
| void_chest | 9808 | 982 | 1.018 | 0.983 | 1.382 | 2.495 | 8.24 | 0.986 | 0.573 | 0 |
| vanilla_chest_hover | 9391 | 940 | 1.064 | 1.010 | 1.482 | 2.713 | 9.22 | 1.028 | 0.547 | 0 |
| void_chest_hover | 9238 | 925 | 1.081 | 0.996 | 1.728 | 2.888 | 22.47 | 1.041 | 0.582 | 0 |
| void_map_standin | 11245 | 1126 | 0.888 | 0.835 | 1.183 | 2.203 | 17.05 | 0.857 | 0.621 | 0 |
| void_inventory_hover_sync | 4204 | 842 | 1.188 | 0.938 | 2.404 | 5.163 | 21.30 | 1.154 | 0.627 | 0 |
| void_inventory_hover_sync_fullreadback | 1826 | 365 | 2.737 | 1.091 | 10.555 | 15.727 | 22.66 | 2.653 | 0.737 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg +0.000 ms [-0.049, +0.049, -0.048, +0.082], p99 -0.075 ms [-0.282, +0.131, -0.334, +0.484] · inventory while hovering avg +0.089 ms [+0.154, +0.043, +0.100, +0.079], p99 +0.210 ms [+0.600, +0.096, +0.303, +0.117] · chest avg +0.026 ms, p99 +0.034 ms

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.003 / 0.007 / 0.06 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.006 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.006 / 0.048 / 0.90 | 0.001 | 0.000 | 0.004 | 0.002 | 848 | 68 KB | 0.201 | 1.93 / 9.25 | — |
| void_chest | 0.003 / 0.007 / 1.16 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.006 | 0.00 / 0.00 | — |
| void_chest_hover | 0.006 / 0.041 / 0.44 | 0.001 | 0.000 | 0.004 | 0.001 | 884 | 48 KB | 0.202 | 1.95 / 7.72 | — |
| void_map_standin | 0.003 / 0.006 / 0.06 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.005 | 0.00 / 0.00 | — |
| void_inventory_hover_sync | 0.215 / 2.649 / 18.32 | 0.001 | 0.208 | 0.004 | 0.002 | 425 | 73 KB | 0.198 | 1.87 / 9.34 | — |
| void_inventory_hover_sync_fullreadback | 1.464 / 12.884 / 18.93 | 0.002 | 1.344 | 0.000 | 0.119 | 340 | 8100 KB | 0.432 | 2.24 / 10.77 | — |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 5.8 | 4.8 | 15.6 |
| VOID inventory (E) | 8.8 | 8.4 | 10.3 |
| VOID inventory (R-Shift) | 8.5 | 7.9 | 11.6 |
| VOID screen init → first frame | 6.3 | 5.9 | 7.8 |
| vanilla chest | 60.5 | 54.9 | 83.8 |
| VOID chest | 80.7 | 66.5 | 141.8 |

First open after pre-warm: 69.7 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 291.7 ms (start-up 55.7 ms — natives 9.3, renderer 10.2, view 9.9, page 25.3 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +83.7 MB private bytes, +101.6 MB working set, +-17.2 MB Java heap (process after: 2835 MB private, 1751 MB working set).

<!-- perf:vulkan:end -->

<!-- perf:opengl-atlas:begin -->
### OpenGL, with VOID Atlas — PASS (generated 2026-10-07 01:06)

Device: NVIDIA GeForce RTX 4060/PCIe/SSE2, OpenGL (3.3.0 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 28% / 52% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 6129 | 614 | 1.630 | 1.383 | 2.944 | 4.896 | 20.28 | 1.544 | 0.529 | 0 |
| vanilla_inventory | 4753 | 476 | 2.100 | 1.886 | 3.563 | 5.531 | 23.73 | 1.991 | 0.630 | 0 |
| void_inventory | 5718 | 573 | 1.746 | 1.515 | 2.935 | 4.488 | 10.63 | 1.666 | 0.605 | 0 |
| vanilla_inventory_hover | 5714 | 573 | 1.746 | 1.539 | 2.805 | 4.404 | 10.05 | 1.669 | 0.602 | 0 |
| void_inventory_hover | 5710 | 572 | 1.749 | 1.479 | 3.165 | 4.731 | 10.10 | 1.669 | 0.607 | 0 |
| vanilla_chest | 5631 | 564 | 1.774 | 1.531 | 2.968 | 4.904 | 10.96 | 1.690 | 0.579 | 0 |
| void_chest | 6025 | 603 | 1.657 | 1.409 | 2.810 | 4.706 | 9.17 | 1.581 | 0.569 | 0 |
| vanilla_chest_hover | 5705 | 572 | 1.750 | 1.482 | 2.972 | 4.637 | 8.05 | 1.673 | 0.581 | 0 |
| void_chest_hover | 5094 | 510 | 1.960 | 1.773 | 3.517 | 5.334 | 10.01 | 1.861 | 0.593 | 0 |
| void_inventory_hover_sync | 1243 | 249 | 4.020 | 3.181 | 7.647 | 13.710 | 20.27 | 3.803 | 0.657 | 0 |
| void_inventory_hover_sync_fullreadback | 437 | 87 | 11.504 | 13.109 | 22.425 | 31.611 | 114.64 | 10.786 | 2.764 | 3 |
| atlas_map | 2600 | 261 | 3.834 | 3.884 | 5.922 | 7.297 | 12.46 | 3.657 | 0.803 | 0 |
| void_map | 2757 | 277 | 3.616 | 3.644 | 5.622 | 6.538 | 11.35 | 3.449 | 0.779 | 0 |
| atlas_map_pan | 2854 | 286 | 3.495 | 3.503 | 5.432 | 6.663 | 11.48 | 3.334 | 0.784 | 0 |
| void_map_pan | 2984 | 299 | 3.343 | 3.296 | 5.097 | 6.647 | 13.21 | 3.181 | 0.770 | 0 |
| atlas_map_pan_frozen | 5213 | 522 | 1.916 | 1.786 | 3.000 | 4.914 | 14.43 | 1.821 | 0.403 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg -0.433 ms [-0.660, -0.109, -0.471, -0.394], p99 -0.871 ms [-0.930, -0.812, -0.766, -1.053] · inventory while hovering avg +0.086 ms [+0.080, +0.129, +0.092, -0.311], p99 +0.259 ms [+0.317, +0.843, +0.200, -0.438] · chest avg -0.162 ms, p99 -0.208 ms

**Map page vs Atlas's own map screen**, both drawing the live world behind the map (median of the per-round differences; `atlas_map_pan_frozen` is Atlas's default still backdrop, not gated): idle avg -0.060 ms [-0.269, -1.079, +0.278, +0.149], p99 -0.448 ms [-0.437, -1.722, -0.026, -0.459] · panning avg -0.220 ms [-0.587, +0.452, +0.147, -0.674], p99 +0.182 ms [-0.314, +0.678, +0.773, -0.970]

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.004 / 0.010 / 0.09 | 0.000 | 0.000 | 0.004 | 0.000 | 0 | 0 KB | 0.009 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.010 / 0.114 / 0.65 | 0.001 | 0.000 | 0.006 | 0.004 | 811 | 95 KB | 0.309 | 2.00 / 9.39 | — |
| void_chest | 0.004 / 0.010 / 0.51 | 0.000 | 0.000 | 0.004 | 0.000 | 0 | 0 KB | 0.012 | 0.00 / 0.00 | — |
| void_chest_hover | 0.011 / 0.100 / 0.48 | 0.001 | 0.000 | 0.006 | 0.004 | 818 | 64 KB | 0.400 | 2.33 / 12.39 | — |
| void_inventory_hover_sync | 0.911 / 10.082 / 13.10 | 0.002 | 0.883 | 0.013 | 0.013 | 357 | 85 KB | 0.740 | 2.49 / 11.00 | — |
| void_inventory_hover_sync_fullreadback | 7.478 / 22.422 / 101.00 | 0.004 | 6.796 | 0.001 | 0.677 | 268 | 8100 KB | 3.065 | 4.53 / 23.85 | — |
| void_map | 0.007 / 0.014 / 0.03 | 0.000 | 0.000 | 0.007 | 0.000 | 0 | 0 KB | 0.038 | 0.00 / 0.00 | 0.813 / 1.262 |
| void_map_pan | 0.007 / 0.015 / 0.13 | 0.000 | 0.000 | 0.007 | 0.000 | 0 | 0 KB | 0.030 | 0.00 / 0.00 | 0.719 / 1.074 |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 8.5 | 7.4 | 15.7 |
| VOID inventory (E) | 11.5 | 11.5 | 13.8 |
| VOID inventory (R-Shift) | 10.1 | 9.4 | 13.6 |
| VOID screen init → first frame | 6.5 | 6.7 | 7.3 |
| vanilla chest | 63.1 | 55.7 | 90.9 |
| VOID chest | 79.8 | 62.3 | 150.3 |
| Map page (R-Shift), re-open | 7.4 | 7.3 | 9.7 |
| Atlas's own map screen | 8.7 | 7.7 | 14.4 |

First Map page open: 53.0 ms key → first painted frame (gated), of which making Atlas's widget 1.73 ms (lazily, on the first Map frame; kept for every later open).

First open after pre-warm: 95.0 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 261.2 ms (start-up 53.5 ms — natives 10.4, renderer 10.0, view 8.1, page 24.4 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +35.4 MB private bytes, +64.6 MB working set, +7.5 MB Java heap (process after: 3186 MB private, 1743 MB working set).

<!-- perf:opengl-atlas:end -->

<!-- perf:vulkan-atlas:begin -->
### Vulkan, with VOID Atlas — PASS (generated 2026-10-07 01:00)

Device: NVIDIA GeForce RTX 4060, Vulkan (1.4.351 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 46% / 91% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 10999 | 1101 | 0.908 | 0.803 | 1.351 | 2.295 | 7.53 | 0.874 | 0.312 | 0 |
| vanilla_inventory | 10755 | 1076 | 0.929 | 0.828 | 1.354 | 2.295 | 8.66 | 0.895 | 0.362 | 0 |
| void_inventory | 11069 | 1108 | 0.903 | 0.793 | 1.375 | 2.153 | 16.31 | 0.871 | 0.377 | 0 |
| vanilla_inventory_hover | 10750 | 1076 | 0.929 | 0.827 | 1.344 | 2.238 | 7.31 | 0.897 | 0.363 | 0 |
| void_inventory_hover | 9674 | 968 | 1.033 | 0.919 | 1.778 | 2.698 | 8.89 | 0.992 | 0.380 | 0 |
| vanilla_chest | 10014 | 1003 | 0.997 | 0.916 | 1.562 | 2.659 | 8.27 | 0.961 | 0.341 | 0 |
| void_chest | 10679 | 1069 | 0.936 | 0.842 | 1.367 | 2.274 | 7.04 | 0.903 | 0.356 | 0 |
| vanilla_chest_hover | 10144 | 1015 | 0.985 | 0.873 | 1.490 | 2.542 | 15.01 | 0.950 | 0.341 | 0 |
| void_chest_hover | 9562 | 957 | 1.045 | 0.933 | 1.795 | 2.863 | 8.23 | 1.004 | 0.357 | 0 |
| void_inventory_hover_sync | 4247 | 850 | 1.177 | 0.883 | 2.570 | 5.147 | 12.86 | 1.138 | 0.380 | 0 |
| void_inventory_hover_sync_fullreadback | 1060 | 212 | 4.716 | 1.575 | 14.283 | 20.253 | 31.75 | 4.560 | 0.610 | 0 |
| atlas_map | 4775 | 478 | 2.091 | 1.899 | 3.142 | 3.989 | 7.61 | 2.034 | 0.408 | 0 |
| void_map | 5705 | 571 | 1.751 | 1.560 | 2.664 | 3.366 | 7.08 | 1.704 | 0.387 | 0 |
| atlas_map_pan | 4693 | 470 | 2.127 | 1.951 | 3.082 | 4.095 | 6.66 | 2.065 | 0.417 | 0 |
| void_map_pan | 5099 | 511 | 1.958 | 1.816 | 2.970 | 3.911 | 10.80 | 1.897 | 0.394 | 0 |
| atlas_map_pan_frozen | 7691 | 770 | 1.299 | 1.160 | 1.881 | 2.603 | 5.86 | 1.259 | 0.200 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg -0.019 ms [+0.052, -0.021, -0.018, -0.104], p99 -0.052 ms [-0.064, -0.015, -0.039, -0.376] · inventory while hovering avg +0.087 ms [+0.203, +0.108, +0.065, +0.043], p99 +0.542 ms [+0.751, +0.887, +0.333, +0.050] · chest avg -0.076 ms, p99 -0.284 ms

**Map page vs Atlas's own map screen**, both drawing the live world behind the map (median of the per-round differences; `atlas_map_pan_frozen` is Atlas's default still backdrop, not gated): idle avg -0.324 ms [-0.458, -0.599, -0.072, -0.189], p99 -0.610 ms [-0.802, -1.152, -0.166, -0.417] · panning avg +0.011 ms [+0.089, -0.010, -0.661, +0.033], p99 +0.061 ms [+0.528, +0.138, -1.505, -0.015]

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 | Atlas widget draw avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| void_inventory | 0.003 / 0.006 / 0.03 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.005 | 0.00 / 0.00 | — |
| void_inventory_hover | 0.006 / 0.037 / 0.78 | 0.001 | 0.000 | 0.004 | 0.001 | 834 | 80 KB | 0.189 | 1.98 / 9.73 | — |
| void_chest | 0.003 / 0.007 / 0.05 | 0.000 | 0.000 | 0.003 | 0.000 | 0 | 0 KB | 0.005 | 0.00 / 0.00 | — |
| void_chest_hover | 0.006 / 0.039 / 0.44 | 0.001 | 0.000 | 0.004 | 0.001 | 863 | 54 KB | 0.200 | 2.01 / 8.81 | — |
| void_inventory_hover_sync | 0.202 / 2.647 / 10.55 | 0.001 | 0.196 | 0.004 | 0.002 | 403 | 82 KB | 0.195 | 1.97 / 8.94 | — |
| void_inventory_hover_sync_fullreadback | 3.008 / 17.650 / 28.13 | 0.002 | 2.776 | 0.000 | 0.230 | 321 | 8100 KB | 0.970 | 3.02 / 14.46 | — |
| void_map | 0.004 / 0.010 / 0.06 | 0.000 | 0.000 | 0.004 | 0.000 | 0 | 0 KB | 0.008 | 0.00 / 0.00 | 0.620 / 0.988 |
| void_map_pan | 0.005 / 0.011 / 0.16 | 0.000 | 0.000 | 0.005 | 0.000 | 0 | 0 KB | 0.012 | 0.00 / 0.00 | 0.638 / 1.037 |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 6.9 | 5.8 | 18.6 |
| VOID inventory (E) | 10.4 | 9.8 | 13.9 |
| VOID inventory (R-Shift) | 10.1 | 9.8 | 13.4 |
| VOID screen init → first frame | 7.2 | 6.6 | 9.8 |
| vanilla chest | 60.0 | 55.9 | 78.4 |
| VOID chest | 79.1 | 63.3 | 147.4 |
| Map page (R-Shift), re-open | 4.3 | 3.8 | 6.4 |
| Atlas's own map screen | 5.6 | 3.9 | 17.9 |

First Map page open: 41.9 ms key → first painted frame (gated), of which making Atlas's widget 1.37 ms (lazily, on the first Map frame; kept for every later open).

First open after pre-warm: 115.0 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 307.6 ms (start-up 65.3 ms — natives 12.3, renderer 13.4, view 10.8, page 28.1 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +65.9 MB private bytes, +74.3 MB working set, +-9.3 MB Java heap (process after: 3055 MB private, 1858 MB working set).

<!-- perf:vulkan-atlas:end -->
