# VOID Hub — the VOID-drawn inventory and the hub's Map page (prototype)

A client-only Fabric mod for Minecraft 26.3 that draws the inventory and chest screens in the VOID
design (Figma `3c0Wgc4ZKFSpm98fWJoARS`, frames 272:51688 "Hub · Inventory (VOID-drawn)" and
272:52131 "Hub · Inventory · chest open") with **Ultralight** (HTML/CSS), on top of Minecraft's
real menus. It exists to answer one question: is this fast enough, and does it work? The answer and
the numbers are in [docs/PERF.md](docs/PERF.md).

## Keys

| key | does |
|---|---|
| **Right Shift** | opens the hub on the page it was left on — at first the **Map** when VOID Atlas is installed, the Inventory otherwise (closes it again) |
| **Tab / Shift-Tab** | next / previous hub page (Map ⇄ Inventory); clicking a page in the pages island does the same |
| **E** | opens the VOID inventory instead of vanilla's while the swap is on (default on) |
| open a chest | shows the VOID chest (any `ChestMenu`: chest, barrel, ender chest, 1–6 rows; >3 rows scroll) |
| **Esc / E / Right Shift** | close (on the Map page E shows the Inventory, and Esc goes to the map first — see below) |

Everything else is vanilla, because it *is* vanilla: left/right click pick up, place, split; shift-click;
drag-distribute; double-click collect; number keys and F swap; Q / Ctrl+Q drop; middle-click clone in
creative. Armour, offhand and the 2×2 crafting grid with its result are real slots. Chests add
**Move all** both ways and **Sort** (client-side, made of ordinary left clicks the server checks
one by one); the inventory has **Sort** for storage and the **All / Tools / Blocks / Food** filter
chips. Unknown and modded containers, and the creative inventory, keep their vanilla screens.

Switches (system properties, e.g. via `JAVA_TOOL_OPTIONS`):

| property | default | |
|---|---|---|
| `voidhub.replaceInventory` | `true` | `false` keeps vanilla's inventory/chest screens (R-Shift still opens the hub) |
| `voidhub.prewarm` | `true` | start Ultralight while the world loads, not on the first key press |
| `voidhub.hideHud` | `true` | the hub's shell replaces the HUD while it is open |
| `voidhub.upload` | `dirty` | `full` = whole-surface readback every change (the baseline the native dirty copy is measured against) |
| `voidhub.sync` | `false` | `true` = the render thread waits for Ultralight every frame (what a single-threaded integration costs; for measurement) |

## The Map page and VOID Atlas (optional)

The hub's Map page (Figma `375:36239`, section 8, "Hub Map page with the real Atlas") hosts **VOID Atlas**'s
whole map page — segments, search, the map with its readout, layers, zoom and scale, the places list,
cards and the waypoint editor — inside the hub's window, with the hub's islands around it as on every
page: the mark top-left, pages top-centre, close top-right, the world strip bottom-centre.

* **Optional.** Atlas is never required. The hub compiles against Atlas's API `compileOnly` (nothing of
  Atlas ships in the hub jar), and only `map/atlas/AtlasMapHost` names an Atlas type; `map/HubMap`
  reaches it through one static call, made only once `FabricLoader.isModLoaded("atlas")` said yes, so
  without Atlas no Atlas class is ever loaded. The rest of the hub talks to the hub-owned
  `map/MapHost` interface. Without Atlas the Map page shows a quiet stand-in ("Install VOID Atlas for
  the map") in the same window, the strip's "Waypoint here" is dimmed and inert, and R-Shift opens on
  the Inventory rather than on a stand-in.
* **Atlas's page at 1280 × 744.** The window is Atlas's shipped page at exactly its design size, at
  the largest of Atlas's own scales (0.75 … 3, the ones its fonts are rasterised at) that fits between
  the islands and is no larger than the islands' unit (asked of Atlas: `MapWidget.unitFor`); at
  1920×1080 that is Atlas at 1:1 in a 1280×744 window. Atlas measures the rectangle and lays its page
  out at that same scale, unstretched. (Without Atlas the stand-in's window uses a copy of Atlas's rule.)
* **One map with M.** The Map page and Atlas's own map screen are two views of Atlas's one map page
  for the session: the same view, zoom, layer, depth, selection and panel tab. **M** on the Map page
  closes the hub (`AtlasApi.isMapKey`), as it closes Atlas's own map.
* **Drawn natively.** Ultralight draws only the hub's chrome: the dim, the islands and the window's
  frame (Atlas's page window: `#0B0B0C`, 7 % hairline, radius 32). Atlas's
  `MapWidget.render` draws everything inside it through Minecraft's GUI pipeline, in the same frame,
  after the chrome quad. Nothing is drawn twice and neither side draws a full-screen layer of its own
  by default. The window rectangle is on
  the GUI-pixel grid, so the frame the page draws and the rectangle Atlas lays out in are the same.
  While the pointer is over the map the page is told once that it left and then not at all, so
  panning costs Ultralight nothing (no messages, no hit-testing, no repaints).
* **Still backdrop, optional** (`-Dvoidhub.frozenMap=true`): Atlas's still of the world behind the Map
  page (`MapWidget.freezeWorld` / `drawFrozenWorld`), the world itself skipped, released when the page
  stops showing. It saves 0.5–1.5 ms a frame (docs/PERF.md) but is **off by default**: Atlas dims the
  still to its own map's 76 %, where the hub's design is the live world at 34 % (the hub's dim steps
  aside over the still, so it is not dimmed twice).
* **Lazy and kept.** The widget is made on the first frame the Map page draws (not at start-up or when
  the hub opens), then kept: every later open, and every switch back from the Inventory, shows the
  same map where it was left (`MapWidget.shown()` each time it shows: the map reads the player's
  dimension and depth, as M does; `closed()` when it stops: the map saves its settings). Joining or
  leaving a world drops it.
* **Input.** On the Map page the window is the map's: clicks, drags (captured from the press, wherever
  the pointer goes), the wheel and keys go to the widget; the islands stay the page's. No click there is
  a vanilla container click, so a carried stack is never dropped from the map. The hub keeps
  **R-Shift** (close), **Tab / Shift-Tab** (pages) and **Esc**: Esc goes to the map first, so it closes the
  map's card, menu or search, and closes the hub only when the map has nothing to close. While the map
  has a text field (its search, the waypoint editor) keys are text: Tab moves between Atlas's search
  groups and R-Shift types capitals, and only an Esc the map does not use closes the hub. **E** on the
  Map page shows the Inventory.
* **"Waypoint here"** (the world strip) adds an Atlas waypoint where the player stands, named like
  Atlas's own quick-add ("Waypoint 4"), through `AtlasApi.addWaypoint`; on the Map page it is selected
  (`MapWidget.selectWaypoint`), so the map centres on it and shows its card. The pill says "Waypoint added" for a moment.

Building with Atlas: the hub compiles against `dev.voidmc:void-atlas:0.1.0` from Maven local
(`./gradlew publishToMavenLocal` in `atlas/`; Atlas 6c4fb205 or later). Without it, `./gradlew syncAtlas`
stages `libs/void-atlas.jar` from a `git archive` snapshot of the `claude/atlas` branch under
`build/atlas-snapshot/` (`-PatlasRef=<ref>`, `-PatlasJar=<file>`; `-PatlasFrom=libs` prefers it over
Maven local). Then `-Patlas` puts Atlas in the dev runs: `./gradlew runClient -Patlas`,
`hubFunctional -Patlas`, `hubPerf -Pbackend=vulkan -Patlas`. With neither, the hub compiles against
`src/atlasApi/`, a copy of the API signatures it calls (excluded from the jar). An Atlas older than
6c4fb205 is refused at the first Map frame (logged; the stand-in shows).

**Asked of Atlas and landed in 6c4fb205** (2026-10-07): one shared map page per session for M and
`createMapWidget`; `MapWidget.view()`, `selectWaypoint`, `closed()`, `shown()`, `unitFor(w, h)`,
`freezeWorld` / `drawFrozenWorld`; `AtlasApi.markerId` and `isMapKey`; a solid Track glyph and solid
22 % hairline tracking lines; maven-publish. The hub uses all of them and no longer reaches into Atlas.
Still open: `drawFrozenWorld` with the host's dim (or none), so the still backdrop could keep the
hub's 34 % look and be on by default.

## Architecture

```
            ┌─────────────── one shared layout (HubLayout, physical px) ───────────────┐
            │                                                                           │
  page  ◄───┤ layout message (CSS px = physical / u)        hit-testing + item rects   ├───► Java
            └───────────────────────────────────────────────────────────────────────────┘

  "VOID Hub UI" thread (Ultralight)              Minecraft render thread, every frame
    hub.apply(json) ◄── posted messages ─────────  HubScreen.extractRenderState
    update → render (CPU view, incremental)          1. state diff → post hub.apply(json) (only on change)
    paints under the surface lock                    2. copy the dirty rect (native try-lock, BGRA→RGBA)
    page → Java messages ──► inbox ────────────────►    → CommandEncoder.writeToTexture (GL or Vulkan)
                                                     3. one quad: the chrome texture, premultiplied, 1:1
                                                     4. Minecraft: stacks, counts, durability bars,
                                                        player model, carried stack — native
```

* **Vanilla logic, VOID drawing.** `HubScreen` is an `AbstractContainerScreen` over the real
  `InventoryMenu` / `ChestMenu`. Mouse events reach vanilla's own click code; the only thing VOID
  changes is *where* they are: `PointerMap` moves each event onto the centre of the slot the VOID
  layout says is under the pointer (or onto a point on no slot for VOID chrome, or far outside the
  panel when the pointer is off every VOID surface, which drops a carried stack exactly like
  clicking outside vanilla's panel). Every click is therefore vanilla's click — server
  authoritative, no desync, bundles and cloning included. `Gui.setScreen` swaps vanilla's
  `InventoryScreen` / `ContainerScreen` for the VOID ones over the *same menu*.
* **Hybrid drawing.** Ultralight draws the chrome only — shell islands, window, wells, hover and
  held rings, chips, the hovered-item card (name, rarity, durability cells, enchantments), purse,
  chest card. Minecraft draws the item stacks natively at the exact well rectangles, so there is
  no item-icon atlas to build or keep in sync.
* **One layout.** `HubLayout` computes every well in physical pixels from the Figma coordinates;
  the page is told those rects and places the wells there, and Java hit-tests and draws items
  against the same integers. The design unit `u` follows the GUI scale (scale 3 = the design 1:1)
  and shrinks to fit small windows.
* **Why a CPU view.** 26.3 renders through renderpearl on OpenGL *or Vulkan*, so the binding's
  OpenGL accelerated path cannot be used. The CPU view rasterises incrementally; only its dirty
  rectangle is copied (natively, swizzled to RGBA in one pass) and uploaded through renderpearl,
  which both backends implement. An unchanged page costs no copy and no upload.
* **Ultralight off the render thread.** JavaScript, style, layout and rasterisation (a few ms
  whenever the page changes — the hovered-item card is the big one) run on a dedicated "VOID Hub UI"
  thread. The render thread posts messages and, once a frame, copies what the UI thread finished
  painting under a *try*-lock: if a paint is in progress it uploads next frame rather than wait.
  The one deliberate wait is when a screen opens, so its first frame is not the previous screen's
  pixels.
* **Idle cost ~0.** The page has no animations or transitions; Java only talks to it when hover,
  held slot, contents, filter or the hovered stack actually change, and an unchanged surface is
  neither copied nor uploaded.
* **Small repaints only.** Ultralight's CPU renderer repaints the *whole view* (~50 ms at
  1920×1080) when one paint has to cover many separate changes — restyling a row of cells one by
  one, rebuilding a list, toggling `visibility` on a subtree, or the hover ring moving while the
  card changes. So the card has fixed rows filled in place, every row of cells is a three-layer
  "meter" (off cells, on cells clipped to a width, one live cell) updated by resizing one box, hiding
  uses `opacity`, and the UI thread paints each message (state, then card) on its own. Measured:
  per-change paint p99 went from ~55 ms to ~10 ms.
* **Paced card.** The hover ring follows the pointer every frame; the hovered-item card (a 5–10 ms
  paint) updates at once when the pointer rests and at most every 150 ms while it sweeps across the
  grid — nobody reads 30 cards a second, and it keeps Ultralight's rasteriser from competing with
  the game for CPU.
* **Priorities.** Ultralight's thread runs at the lowest priority, raised only while a screen is
  opening and the render thread is waiting for that first paint.

Source map:

| | |
|---|---|
| `layout/HubLayout`, `layout/PointerMap` | the shared layout; pointer → vanilla geometry |
| `model/*` | tooltip model, click planner (Sort / Move all), JS bridge codec, JSON — pure Java |
| `screen/HubScreen`, `HubInventoryScreen`, `HubChestScreen` | the screens |
| `ui/HubView`, `HubTexture`, `HubStats` | Ultralight view, renderpearl texture, per-frame timings |
| `mixin/GuiMixin` | screen swap; HUD hidden under the hub |
| `map/MapHost`, `map/HubMap`, `map/atlas/AtlasMapHost` | the Map page's map: the hub-side interface, the optional-Atlas gate, the only class naming Atlas types |
| `src/atlasApi/java` | the Atlas API signatures the hub compiles against when no Atlas jar is staged (not packaged) |
| `src/main/resources/assets/voidhub/ui/` | the page: `index.html`, `hub.css`, `hub.js`, Outfit fonts |

## Building and running

Java 25 and the Ultralight binding (built by `pvp/mod/native`, see its README) are needed:

```bash
export JAVA_HOME=<repo>/.tools/jdk-25
./gradlew syncUltralight          # stage libs/voidultralight-api.jar + libs/natives/windows-x64 from ../pvp/mod/native/build
./gradlew build                   # build/libs/void-hub-0.1.0.jar (binding shaded, Windows natives inside, ~21 MB)
./gradlew runClient               # dev client
```

**Native change (needs coordinating with the pvp/ owner before merge).** The binding gained one JNI
entry point, `View.copyDirtyRgba(ByteBuffer dst, boolean full, boolean tryLock, int[] rectOut)`
(`pvp/mod/native/src/jni_api.cpp`, `Native.java`, `View.java`): copy the CPU surface's dirty
rectangle into a direct buffer as RGBA (swizzled in the same pass), clear the damage, report the
rect and the surface size, and with `tryLock` return at once (-2) instead of waiting for a paint in
progress on the UI thread. `viewResize` now takes the surface lock, so a resize on the UI thread
cannot race that copy. Without the new call the only CPU readback is `readPixels()` — the whole
surface into a new `byte[]` every changed frame (≈17 ms at 1920×1080 with the Java swizzle,
measured). PERF.md has the in-game difference.

## Tests

| command | what | needs the game |
|---|---|---|
| `./gradlew test` | JUnit 5: shared layout (page rects ↔ hit-testing at GUI scales 1–4, 8 window sizes, every chest size/scroll), pointer → vanilla slot mapping against vanilla's real slot positions, tooltip model → JSON, JS-bridge decode/encode, Sort / Move-all click plans replayed on a model of vanilla's PICKUP rules | no |
| `./gradlew hubFunctional [-Patlas]` | in-game: real clicks through `MouseHandler` / `KeyboardHandler`, asserting the real menu and that client and server agree — pickup/place, right-click split, shift-click, number-key swap, Q drop, 2×2 craft (log → 4 planks), drag-distribute, double-click collect, armour/offhand, filter chips via the page, E / R-Shift / Esc, chest Move all both ways + Sort, page wells vs layout at GUI scales 1–4; then the Map page (`MapTest`): without Atlas the stand-in, Tab / island page switches, the inert pill, R-Shift re-opening on the last page; with `-Patlas` R-Shift opening on Atlas's map, a drag panning it, the wheel zooming it, "Waypoint here" adding a waypoint, Esc reaching the map first, Tab / Shift-Tab / island / E, the widget kept between opens with its view, typing in Atlas's search keeping Tab and R-Shift, the window matching the layout at GUI scales 1–4. Screenshots in `build/run/hubFunctionalGame-<backend>[-atlas]/screenshots/` | yes |
| `./gradlew hubPerf -Pbackend=opengl` (or `vulkan`) `[-Patlas]` | the perf regression gate (below) | yes |
| `./gradlew runClientGameTest` | functional + perf in one run | yes |

Optional: `-Pseconds=20` (time per scenario, default 10, split over 2 interleaved rounds).

### The perf gate

`hubPerf` runs a client game test with vsync off and the frame rate uncapped, at 1920×1080 (GUI
scale auto = 4), in a survival world with a realistic inventory and a 27-stack chest. It measures,
interleaving vanilla and VOID rounds so background load lands on both sides:

* world idle; vanilla inventory; VOID inventory idle; vanilla and VOID inventory with the pointer
  sweeping every slot (~30 slots/s, so vanilla draws its highlight and tooltips and VOID repaints
  wells and the card); vanilla chest; VOID chest idle and hovered;
* two baselines: Ultralight on the render thread (`sync`), and that plus the binding's original
  whole-surface `readPixels()` instead of the new dirty-rectangle copy;
* without Atlas, the Map page's stand-in (`void_map_standin`); with `-Patlas`, the Map page against
  Atlas's own map screen (M), each idle (`void_map` / `atlas_map`) and panned by the same drag
  (`void_map_pan` / `atlas_map_pan`: the left button held while the pointer circles over the map
  every frame), interleaved round by round, plus the first Map open (the widget is made then), Map
  re-opens and Atlas's own screen's opens. The with-Atlas run writes `build/perf/<backend>-atlas.json`
  and its own section of `docs/PERF.md`.

For each: fps, frame time avg/p50/p95/p99/max, CPU time in `renderFrame`, GPU time (renderpearl
timestamp queries), hitches, and the VOID cost split into render-thread work (post, wait, copy,
upload) and Ultralight's own thread (update, paint, JS). Plus open latency (key → first painted
frame) for E, R-Shift and chests; Ultralight's start-up and pre-warm cost; memory (private bytes,
working set, heap) added by Ultralight.

It writes `build/perf/<backend>.json`, rewrites that backend's section of `docs/PERF.md`, prints
the numbers, and **fails** if the VOID inventory exceeds vanilla's by more than: avg frame time
+1.0 ms or p99 +2.0 ms (idle, and again while hovering), more frames over 33 ms while hovering than
vanilla had hovering the same way, first open (after pre-warm) over 300 ms, any re-open over
150 ms. The frame-time deltas are the median of the per-round differences (VOID round minus the
vanilla round next to it), so a load spike from another process lands in one round, not in the
verdict, while a real regression shows in every round. With Atlas it also fails if the Map page
exceeds Atlas's own map screen by more than avg +1.0 ms (idle and panning) or p99 +2.0 ms (panning),
has more frames over 33 ms while panning than Atlas's screen had, a first Map open over 300 ms or a
Map re-open over 150 ms.

The gate's game tasks end the JVM themselves once the results are written
(`voidhub.test.halt`): closing the test world occasionally deadlocks under the client game test,
which would otherwise hold the run until a timeout.

The client game test drives this machine's GPU in a real window. On a machine shared with other
game runs, take numbers when it is otherwise idle; interleaving reduces, but does not remove, the
effect of other load.

## Licence notice

Ultralight is used under its free licence. As its LICENSE §4.4 requires, the credit line is shown
in the mod's credits (`fabric.mod.json` → `custom.credits`), logged at start-up and available as
`Ultralight.licenceNotice()`:

> Ultralight (c) 2024 Ultralight, Inc. All rights reserved. Ultralight is a trademark of
> Ultralight, Inc.

Third-party notices ship with the natives (`natives/windows-x64/resources/NOTICES.md`). Outfit is
SIL Open Font License 1.1 (`assets/voidhub/ui/fonts/OFL.txt`).
