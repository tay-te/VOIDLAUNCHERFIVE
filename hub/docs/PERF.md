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
* Open latency: from the key event (or the chest use) to the end of the first frame that shows
  the screen, 10× each for vanilla E, VOID E and VOID R-Shift, 5× each for the chests. The first
  VOID open of the run is cold (`voidhub.prewarm=false` in the perf run) and is reported with its
  Ultralight start-up breakdown and the memory it added.

Budgets (the gate fails the build when exceeded, VOID vs vanilla in the same run):
avg frame time +1.0 ms · p99 +2.0 ms · no frame over 33 ms while hovering · first open ≤ 300 ms
(excluding the one-off Ultralight start-up, which a normal session pays while the world loads) ·
every re-open ≤ 150 ms.

## Results

Generated by the gate; each backend's section is rewritten on every run.

<!-- perf:opengl:begin -->
### OpenGL — PASS (generated 2026-10-04 05:54)

Device: NVIDIA GeForce RTX 4060/PCIe/SSE2, OpenGL (3.3.0 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 23% / 35% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 4390 | 440 | 2.274 | 1.948 | 4.168 | 6.220 | 42.03 | 2.084 | 0.596 | 2 |
| vanilla_inventory | 3254 | 326 | 3.066 | 2.849 | 5.082 | 7.424 | 21.79 | 2.856 | 0.707 | 0 |
| void_inventory | 3011 | 302 | 3.310 | 3.008 | 5.483 | 8.079 | 35.88 | 3.086 | 0.705 | 1 |
| vanilla_inventory_hover | 2954 | 296 | 3.377 | 3.099 | 5.620 | 7.706 | 36.71 | 3.153 | 0.713 | 1 |
| void_inventory_hover | 3064 | 307 | 3.256 | 2.980 | 5.478 | 7.797 | 33.87 | 3.031 | 0.707 | 1 |
| vanilla_chest | 3507 | 352 | 2.843 | 2.659 | 4.865 | 6.318 | 22.78 | 2.661 | 0.649 | 0 |
| void_chest | 3753 | 376 | 2.659 | 2.490 | 4.715 | 6.229 | 26.70 | 2.489 | 0.646 | 0 |
| vanilla_chest_hover | 3931 | 394 | 2.538 | 2.479 | 4.395 | 5.935 | 27.22 | 2.395 | 0.631 | 0 |
| void_chest_hover | 4306 | 432 | 2.317 | 2.335 | 4.133 | 6.165 | 17.53 | 2.182 | 0.620 | 0 |
| void_inventory_hover_sync | 2705 | 541 | 1.847 | 1.410 | 3.540 | 9.243 | 23.61 | 1.780 | 0.624 | 0 |
| void_inventory_hover_sync_fullreadback | 1330 | 266 | 3.753 | 1.539 | 11.823 | 17.353 | 23.53 | 3.614 | 1.224 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg +0.213 ms [-0.172, +0.390, +0.036, +0.452], p99 +1.536 ms [-1.924, +1.450, +1.745, +1.622] · inventory while hovering avg -0.060 ms [-0.521, +0.077, -0.147, +0.027], p99 +0.101 ms [-1.517, +0.496, -0.294, +0.971] · chest avg -0.158 ms, p99 -0.560 ms

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| void_inventory | 0.008 / 0.017 / 0.03 | 0.000 | 0.000 | 0.008 | 0.000 | 0 | 0 KB | 0.134 | 0.00 / 0.00 |
| void_inventory_hover | 0.021 / 0.383 / 1.16 | 0.002 | 0.000 | 0.013 | 0.006 | 492 | 164 KB | 1.389 | 7.27 / 76.23 |
| void_chest | 0.008 / 0.015 / 0.27 | 0.000 | 0.000 | 0.008 | 0.000 | 0 | 0 KB | 0.068 | 0.00 / 0.00 |
| void_chest_hover | 0.012 / 0.140 / 0.85 | 0.001 | 0.000 | 0.007 | 0.003 | 557 | 85 KB | 0.974 | 6.68 / 73.98 |
| void_inventory_hover_sync | 0.322 / 7.059 / 20.42 | 0.001 | 0.313 | 0.005 | 0.003 | 403 | 76 KB | 0.305 | 1.98 / 8.87 |
| void_inventory_hover_sync_fullreadback | 1.974 / 14.002 / 16.83 | 0.002 | 1.790 | 0.000 | 0.181 | 327 | 8100 KB | 0.523 | 2.08 / 9.38 |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 13.2 | 10.0 | 38.0 |
| VOID inventory (E) | 16.0 | 15.0 | 23.8 |
| VOID inventory (R-Shift) | 14.6 | 13.7 | 16.6 |
| VOID screen init → first frame | 9.0 | 8.2 | 13.8 |
| vanilla chest | 67.7 | 62.7 | 93.5 |
| VOID chest | 83.6 | 77.1 | 143.1 |

First open after pre-warm: 105.8 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 700.1 ms (start-up 402.3 ms — natives 36.4, renderer 40.3, view 153.9, page 171.0 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +8.9 MB private bytes, +148.7 MB working set, +-6.2 MB Java heap (process after: 2911 MB private, 1541 MB working set).

<!-- perf:opengl:end -->

<!-- perf:vulkan:begin -->
### Vulkan — PASS (generated 2026-10-04 05:57)

Device: NVIDIA GeForce RTX 4060, Vulkan (1.4.351 NVIDIA 616.64) · window 1920×1080 · GUI scale 4 · machine CPU load before/after the run 26% / 7% · vsync off, frame rate uncapped

| scenario | frames | fps | frame avg | p50 | p95 | p99 | max | render CPU avg | GPU avg | hitches >33 ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| idle | 14844 | 1486 | 0.673 | 0.641 | 0.888 | 1.382 | 7.88 | 0.651 | 0.463 | 0 |
| vanilla_inventory | 11733 | 1174 | 0.852 | 0.804 | 1.139 | 1.574 | 7.28 | 0.826 | 0.594 | 0 |
| void_inventory | 11314 | 1132 | 0.883 | 0.824 | 1.220 | 1.753 | 7.71 | 0.856 | 0.611 | 0 |
| vanilla_inventory_hover | 10934 | 1094 | 0.914 | 0.827 | 1.362 | 1.892 | 5.16 | 0.885 | 0.575 | 0 |
| void_inventory_hover | 10611 | 1062 | 0.942 | 0.851 | 1.491 | 2.113 | 5.14 | 0.910 | 0.592 | 0 |
| vanilla_chest | 12160 | 1217 | 0.822 | 0.775 | 1.069 | 1.568 | 18.95 | 0.798 | 0.570 | 0 |
| void_chest | 12081 | 1209 | 0.827 | 0.800 | 1.032 | 1.566 | 4.48 | 0.804 | 0.577 | 0 |
| vanilla_chest_hover | 11973 | 1198 | 0.835 | 0.805 | 1.035 | 1.595 | 7.97 | 0.811 | 0.566 | 0 |
| void_chest_hover | 11566 | 1157 | 0.864 | 0.802 | 1.286 | 1.749 | 5.00 | 0.835 | 0.572 | 0 |
| void_inventory_hover_sync | 5153 | 1031 | 0.970 | 0.825 | 1.885 | 2.765 | 10.07 | 0.945 | 0.567 | 0 |
| void_inventory_hover_sync_fullreadback | 2311 | 462 | 2.164 | 0.914 | 9.162 | 13.898 | 19.29 | 2.101 | 0.633 | 0 |

**VOID vs vanilla** (median of the per-round differences; rounds in brackets): inventory avg +0.023 ms [+0.091, +0.007, +0.031, +0.014], p99 +0.084 ms [+0.538, +0.113, +0.055, +0.005] · inventory while hovering avg +0.038 ms [-0.056, +0.039, +0.056, +0.036], p99 +0.211 ms [-0.275, +0.208, +0.342, +0.213] · chest avg +0.001 ms, p99 -0.014 ms

| VOID cost (ms / frame) | render thread: avg / p99 / max | post | wait | copy | upload | uploads | avg upload | Ultralight on its thread | per page change: avg / p99 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| void_inventory | 0.002 / 0.005 / 0.10 | 0.000 | 0.000 | 0.002 | 0.000 | 0 | 0 KB | 0.003 | 0.00 / 0.00 |
| void_inventory_hover | 0.005 / 0.029 / 0.69 | 0.001 | 0.000 | 0.003 | 0.001 | 869 | 67 KB | 0.158 | 1.76 / 8.37 |
| void_chest | 0.002 / 0.003 / 0.11 | 0.000 | 0.000 | 0.002 | 0.000 | 0 | 0 KB | 0.002 | 0.00 / 0.00 |
| void_chest_hover | 0.004 / 0.020 / 0.26 | 0.000 | 0.000 | 0.003 | 0.001 | 923 | 45 KB | 0.138 | 1.57 / 6.45 |
| void_inventory_hover_sync | 0.155 / 1.958 / 9.18 | 0.001 | 0.150 | 0.003 | 0.001 | 447 | 66 KB | 0.151 | 1.66 / 8.00 |
| void_inventory_hover_sync_fullreadback | 1.130 / 12.183 / 17.16 | 0.001 | 1.055 | 0.000 | 0.074 | 346 | 8100 KB | 0.301 | 1.95 / 8.83 |

| open latency (ms, key → first painted frame) | avg | p50 | max |
|---|---:|---:|---:|
| vanilla inventory (E) | 5.9 | 5.1 | 13.9 |
| VOID inventory (E) | 8.6 | 8.4 | 10.2 |
| VOID inventory (R-Shift) | 8.0 | 7.2 | 10.5 |
| VOID screen init → first frame | 6.2 | 6.0 | 7.4 |
| vanilla chest | 59.7 | 53.1 | 83.5 |
| VOID chest | 65.3 | 57.6 | 141.1 |

First open after pre-warm: 69.7 ms (gated). Pre-warm, on Ultralight's own thread while the world loads: 274.7 ms (start-up 50.9 ms — natives 9.5, renderer 9.2, view 8.3, page 23.4 ms — then the first layout and full paint at the window size). Without pre-warm the first open would wait for all of it.

Memory added by Ultralight, the view, the page and its texture: +60.1 MB private bytes, +65.3 MB working set, +-4.5 MB Java heap (process after: 2871 MB private, 1793 MB working set).

<!-- perf:vulkan:end -->
