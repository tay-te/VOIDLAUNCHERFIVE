# `lod/` — VOID LOD

Far terrain for **Minecraft 26.3** (Fabric): the land past your render distance, out to the horizon,
sampled straight from the world generator, with everything you have seen (builds, dug-out land,
villages) read from the chunks themselves and kept on disk, shaded to look like the blocks it stands for,
and drawn in a handful of indirect draws on OpenGL or Vulkan.

```
Fabric Loader ≥ 0.19.5 · Fabric API 0.161.0+26.3 · Java 25 · optional: VOID Expanse
```

## In game

An Expanse Earth world (seed 8675309, render distance 8, 1600×900), from a 250-block peak, from a
valley floor two kilometres away, and from 420 blocks above the peak. Vanilla, then the first version of
VOID LOD, then this one:

| Vanilla | VOID LOD before | VOID LOD now |
|---|---|---|
| ![peak, vanilla](docs/peak1_vanilla.jpg) | ![peak, before](docs/peak1_before.jpg) | ![peak, now](docs/peak1_after.jpg) |
| ![peak, vanilla](docs/peak3_vanilla.jpg) | ![peak, before](docs/peak3_before.jpg) | ![peak, now](docs/peak3_after.jpg) |
| ![valley, vanilla](docs/low0_vanilla.jpg) | ![valley, before](docs/low0_before.jpg) | ![valley, now](docs/low0_after.jpg) |
| ![air, vanilla](docs/air_vanilla.jpg) | ![air, before](docs/air_before.jpg) | ![air, now](docs/air_after.jpg) |

Through a 30° lens from the peak, the far field up close:

![far field through a 30-degree lens](docs/far0_after.jpg)

What changed, in short: far land is drawn as columns of blocks, the way Minecraft looks from a long way
off, instead of 64-block stair-steps or a smooth surface; the air hazes with distance and thins with
height; walls are made of what the ground is made of; forests are trees close in and a canopy far out;
snow lies where the chunks put it; colours come from the loaded textures; tiles cross-fade instead of
popping.

Measured in that run at 1600×900 (RTX 4060, Ryzen 7 5800XT shared with other work), view settled:

* The LOD's work on the render thread is **0.2–0.4 ms per frame** (selection, fades, culling, draw
  buffers); about 1 ms while tiles stream in, uploads capped at 4 MB a frame.
* Its draws take **0.36–0.5 ms of GPU time** for **650 k–1 M quads** (timestamp queries); about 0.8 ms
  through the 30° lens. The game runs at 1100–1200 fps with it on.
* About **600 tiles** cached in **90–140 MB** of GPU memory.
* A tile builds in **about 35 ms** on a worker, 95 % of it the terrain source (the Earth model and the
  biome lookup). Moving two kilometres, the new view completes in **3.5 s**.

The first version, on the same machine and views, never settled at these frame rates: it counted its
cache timers in frames, so at 1500 fps it evicted and rebuilt its coarsest tiles in a loop (2,088 builds
for 378 tiles, 150–185 jobs always queued) and spent **1.9–3.3 ms** of render thread per frame. Timers
are in seconds now, and a split tile stays resident as its children's fallback.

### The End, conifers, rain and the sea at dusk

The End's outer islands, vanilla and with the LOD, and from 140 blocks above them: islands to the
horizon, slabs over the void, sinking into the dark with distance.

| Vanilla | VOID LOD |
|---|---|
| ![End islands, vanilla](docs/end0_vanilla.jpg) | ![End islands, VOID LOD](docs/end0_after.jpg) |

![End islands from above](docs/end1_after.jpg)

A conifer forest on a cold coast, from 140 blocks above it: cones close in, a dark, spiky canopy further
out, the crowns' tops white with snow and their flanks not.

![conifer forest under snow](docs/forest0_after.jpg)

Rain from the peak, and dusk from just above the open sea, before and now: the far land goes in a
downpour as it does in vanilla, and the far sea meets vanilla's water without a dark band.

| Before | Now |
|---|---|
| ![rain, before](docs/rain0_before.jpg) | ![rain, now](docs/rain0_after.jpg) |
| ![dusk at sea, before](docs/sea0_before.jpg) | ![dusk at sea, now](docs/sea0_after.jpg) |

## Why it is built this way

Distant Horizons and Voxy both get their far terrain from **chunks**: Voxy only from chunks someone
has loaded, Distant Horizons by running the full world generator on them. Either way, a 16 km view is
about a million chunks of work.

VOID LOD asks the generator about **columns** instead. A world's terrain is a function of position, so
the LOD samples it once per LOD cell (one block close in, 512 blocks at the horizon) without building a
chunk at all. On Expanse's Earth worlds the source is the terrain model itself, which answers a column
in about two microseconds. The whole horizon is there the moment the world loads.

| | Distant Horizons | Voxy | VOID LOD |
|---|---|---|---|
| Terrain nobody has visited | runs the full generator | nothing until visited | sampled per column |
| Builds and edits past the render distance | yes | yes | yes: the loaded chunks, read in and kept on disk |
| Draw calls for the LOD | many | GPU-driven | an indirect draw per 64 MB heap and kind of tile |
| Graphics API | OpenGL (Vulkan via add-on / 26.2+) | OpenGL 4.6 | Mojang's own GPU layer: OpenGL **and** Vulkan |
| Depth | separate framebuffer, composited | separate | vanilla's own reversed-Z depth buffer, no composite |

## How it looks like Minecraft from far away

The LOD's job is to draw what the chunks would look like if the render distance reached the horizon, at
a fraction of the cost. Two kinds of tile do it, chosen by level (`core/TileBuilder`):

* **Blocky tiles, next to the chunks** (`core/Blocky`, levels 0 and 1, where a block is still a few
  pixels). A flat top per cell and walls between cells, built the way the chunks are:
  * Walls are made of what the ground is made of: the surface block's side for the top block (grass
    side, snowy grass, sand), then soil, then rock, or the world's own badlands strata, read from its
    `MaterialSystem`, so canyon walls are striped exactly as the chunks stripe them.
  * Vanilla's ambient occlusion: each top corner darkens with the neighbours above it, quads split
    along the brighter diagonal, and every wall darkens along its lowest block.
  * Trees, one crown each (`core/Trees`): a jittered grid with the biome's density, each tree its own
    height and shade, darker under the canopy. Conifers are cones and broadleaf trees domes, in the
    biome's own mix: `BiomeLooks` reads each tree feature's foliage placer (spruce, pine, mega pine) or,
    for a modded tree, the names of its placers (Expanse's redwoods shape their crowns in the trunk
    placer). In snowy forests the crowns' tops are white with the snow on the boughs, their flanks not.
  * A one-block grain in the shader, as the block textures give the chunks, fading out before it
    could shimmer.
* **Smooth tiles, everywhere else** (`core/Smooth`, level 2 and up, where a block is under a pixel or
  two). A blocky mesh with 64-block cells is giant stair-steps; these tiles are a continuous heightfield
  instead, and each vertex says what a stair of blocks *averages* to there:
  * the colour of the tops, the colour of the risers (the surface block's side on gentle ground, soil
    and then rock as the slope steepens and each step gets taller), and the slope;
  * the shader weights tops and risers by how much of each faces the camera, with Minecraft's face
    shading. From above, a hillside is its grass; from across the valley it is mostly risers; above
    the eye there are no tops at all. That is what makes far terrain read as Minecraft and not as a
    smooth CG landscape.
  * The fragment shader lays this out as **columns of blocks**, about three pixels across at every
    distance: one block close in, then 2, 4, 8... blocks further out, blended between sizes and anchored
    to the world's block grid. Each column shows its top, and a strip of riser on each downhill edge only
    where the land crosses a whole block between it and its neighbour, sized so the risers average out
    to what the camera sees. On gentle ground the steps follow the contours every few columns, on steep
    ground every column has its own; each column is a shade of its own. Far land gets the crunch of
    blocks that Distant Horizons gets from real block columns, with none of the geometry, box-filtered
    so it never shimmers.
  * Past the first kilometre or so, the sun models the relief: slopes toward it a little brighter,
    slopes away a little darker, so a far range has shape instead of flat light.
  * Forests are a canopy raised as far as they are dense: conifer forests darker, their tops showing
    their spires' shaded flanks and a stronger mottle, broadleaf forests rounder and lighter; a cold
    forest's tops partly white with snow, its flanks not. Valleys are slightly darker and ridges slightly
    lighter; colours are softened across biome borders and mottled cell by cell; rivers narrower than a
    cell are a thread of their water's colour.
  * Every smooth tile has the same topology, so they all share one index buffer: 70 KB a tile.
* **Colours from the textures actually loaded** (`source/TextureColors`, `source/BiomeLooks`): each block
  face is the average of the sprites its model draws, in linear light like a mipmap, layered (grass side
  = dirt with its tinted fringe), with the block's own tint for the biome through vanilla's
  `BlockTintSource`. Resource packs, Expanse's leaves and any modded block colour the far terrain the way
  they colour the chunks.
* **Natural variation** (`core/Samples`): forests thin into clearings and thicken again, tones drift over
  a few hundred blocks, and detail noise in the shader keeps every octave that is still two pixels across.
* **Snow where the chunks put it**: vanilla's own temperature test block by block near the chunks, and
  its snow line (temperature falling 0.05 per 40 blocks) as a soft band further out.
* **Water by depth**: the water texture's tint over a bed that darkens a light level per block, so
  shallows show their sand; water at a low angle mirrors the sky, right up to the chunks, whose own
  water there shows the sky through it.
* **Aerial haze** (`lod.vsh`, `mixin/FogRendererMixin`): exponential haze that thins with height, dense in
  the valleys and thin over the peaks, so far ranges stand clear above the haze in front of them.
  Vanilla's chunks get the same haze in linear form, as thick as the LOD's where they meet it, so the
  seam disappears; misty biomes (Expanse's cloud forest, fjords) thicken it; the land melts into the sky at
  the LOD's edge. Rain hangs a curtain over it that does not thin with height, as dense as vanilla's
  rain fog (two thirds by 660 blocks), so in a downpour the far land goes as it does in vanilla, from a
  valley or a peak. Dusk and night darken the LOD through vanilla's own lightmap and haze it toward
  vanilla's fog colour, as the chunks are; under water, in lava or blind, the LOD is not drawn at all.
* **The End and other floating land**: where the generator fills with air instead of water, a column
  is read whole for its top and the underside of its island, and a column with no ground is empty.
  Close in, islands are slabs: walls hang from the rim to the underside, and the underside is drawn
  step by step for when you are below it. Far out, the shared grid stays whole: vertices over the void
  are flagged, those beside land hang to its underside as the island's flank, and the shader cuts out
  every triangle with no land in it, only in tiles that have void (the rest keep early-Z). End stone
  comes from the textures like every other block; there is no sea, and the haze is even and thicker,
  so far islands sink into the dark rather than ending in a line at the LOD's edge.
* **No popping** (`core/Fades`): when the selection swaps a tile for its children or back, the two
  cross-fade over 0.45 s through complementary dither, so every pixel shows exactly one of them.

## How it works

```
 TerrainSource ──▶ TileBuilder ──▶ LodEngine ──▶ LodRenderer ──▶ vanilla main pass
 (Expanse model,   (sample the     (quadtree      (TLSF arena, fades,  (after opaque chunks,
  or any noise      grid, mesh      selection,     per-tile instance    same depth buffer)
  generator)        blocky or       workers,       buffer, indirect
                    smooth)         uploads)       draws)
```

* **Screen-space-error quadtree** (`core/LodSelector`). A tile is 64×64 cells. It splits while its
  cells would cover more than `void.lod.detail` pixels on screen (`void.lod.farDetail` for the smooth
  far field, which is cheap enough to keep finer). Detail follows what the eye can resolve, not fixed
  rings, so the work per frame stays about the same however far the view reaches. Zoomed in (a
  spyglass, a narrow field of view), only the land in view refines; the rest keeps the detail a normal
  field of view gives it.
  While tiles load, the walk falls back to the nearest ready ancestor, and a split tile stays resident
  as its children's fallback, so you never see a hole. The top two levels are fetched first, so the
  whole horizon appears within a few tile builds.
* **Prioritised, cancellable workers** (`core/LodEngine`). Workers run at minimum thread priority.
  Four times a second the queue is re-sorted by the latest screen error; a job the last three
  selections did not ask for is dropped unbuilt. Uploads are capped at 4 MB a frame. Everything else
  is timed in seconds, not frames, so it behaves the same at 30 fps and at 2000.
* **Few draws** (`render/LodRenderer`):
  * Meshes live in Mojang's TLSF `UberGpuBuffer` heaps, the same allocator the chunk renderer uses.
    Vertices are 16 bytes: tile-relative 16-bit position, flags, two RGBA8 colours.
  * Each frame, the visible tiles (frustum-culled per tile against their real height range) become one
    instance buffer and one `drawIndexedIndirect` per heap and kind of tile.
  * Only tiles in the seam with the chunks, or fading, use the shader variant with `discard`; the rest
    keep early-Z.
  * GPU time is measured with timestamp queries and logged with the other statistics.
* **Shared depth** (`mixin/CameraMixin`). 26.3 renders reversed-Z into a 32-bit float buffer, so the far
  plane moves out to the LOD's reach for free. The LOD draws right after the opaque chunks, so every
  LOD fragment hidden by nearby terrain is rejected before it is shaded.

## Real chunks, kept

The generator only knows the world as it was generated. Everything players do to it, and everything placed
after the terrain (villages, structures), is in the chunks. So every chunk the client loads is also read
into the LOD (`source/ChunkIngest`), and the far view shows the world as it is:

* **Per column, a few ints.** Each column is followed down from its highest block: past plants, torches
  and thin snow (which only colours the ground), through leaves (where the canopy's top is) and the trunks
  under them, through water or lava (its surface), to the first solid block; then a few blocks further for
  the soil and the rock. The column keeps its ground height, water depth, canopy height and an index into
  the chunk's palette of *surfaces*: what its faces look like (the same texture colours and biome tints as
  the generator path) and their material ids, worked out once per kind of column ever. A chunk is
  256 ints and a short palette, about 1.3 KB in memory: ten thousand chunks explored, 13 MB.
* **Cheap on the client thread.** Chunks queue as they arrive and are read within 0.3 ms a frame. A chunk
  costs about 0.13 ms on forested Earth terrain (leaves, trunks and water to walk through) and 0.04 ms on
  plain land. A changed chunk is read again once its block updates have been quiet for 0.75 s, so an
  explosion or a big `/fill` is one read; a change nothing far off could see (a cave dug, a chest opened,
  the grass under a placed block turning to dirt) changes nothing downstream. Leaves are one look whatever
  their distance to a log, or every leaf update after a chunk generates would read as a change.
* **Seen land wins, the generator fills in** (`core/IngestSource`, `core/ColumnStore`). A cell of `c`
  blocks stands for the `c`-wide square around its sample, which is always four aligned squares half as
  wide: a few columns added up, a chunk's own summary, or a cell of a per-region pyramid of summaries
  (32 to 256 blocks) kept up to date as chunks come in. Summaries are sums, so every tile and level agrees
  on what a square averages to: heights by kind (water where most of it is wet, otherwise land with the
  water's colour mixed in by share, so a river narrower than a cell is a thread), colours, the share under
  leaves, the commonest materials. Up close (cells of one or two blocks) a crown is leaf blocks, as the
  chunks show it; far out it is a canopy statistic, as the smooth tiles draw it. Where a cell is only
  partly seen, the seen part and the generator blend by share, so the explored land fades into the
  generated land instead of stepping. Workers read without locks: chunks and summaries are immutable and
  swapped in whole. Tiles over seen land build faster than over the generator: 1–5 ms for most levels
  (12–17 ms at level 4, where a sample adds up 256 columns), against 40–55 ms from the Earth model and biome lookups.
* **Rebuilt behind what is drawn** (`LodEngine.invalidate`). A change marks the tiles over it at every
  level; a marked tile on screen is rebuilt in the background once the changes have settled (a second of
  quiet, a tile at most every 2 s, coarse tiles less often, one rebuild per worker, behind every missing
  tile) and swaps in only when its mesh is on the GPU, so nothing flickers or opens. A change smaller than
  one of a tile's cells (a chunk under 512-block cells) waits for more, up to 30 s. Nearly every change
  happens where vanilla's chunks are drawn (grass spreading, water flowing, the player building), where the
  LOD shows nothing: those wait until the camera has moved far enough for the LOD to show them.
* **Kept on disk** (`core/ColumnFiles`), in the world's save (`void_lod/<dimension>/r.<x>.<z>.vlod`) or,
  on a server, under `void_lod/servers/<address>/`: one deflated file per 32 × 32 chunks, materials by
  name so a file outlives the session. All file work is one background thread at minimum priority. On
  joining, the saved land around the player is read before the first tile is built (a few hundred ms at
  most, then it builds anyway); regions come in as they come within reach and are saved and forgotten once
  far out of it, so memory follows the view, not the whole explored world. About 250 bytes a chunk on disk;
  the showcase's 683 chunks are 157 KB, read back in 0.2 s.
* **Multiplayer.** With no integrated server there is no generator: the LOD draws what the client has
  seen (this session and every earlier one on that server), and a tile over land nobody has seen is not
  built at all. Nothing in this path touches server classes.

In the showcase world: a red concrete deck pierced by a gold tower and a trench cut across the land, built
in loaded chunks, then seen from 480 blocks away (render distance 8: the chunks end at 128 blocks), and
again after leaving and rejoining, when its chunks never load:

| Before | Built, LOD off | Built, LOD on | Rejoined |
|---|---|---|---|
| ![before](docs/ingest_before.jpg) | ![built, vanilla](docs/ingest_after_vanilla.jpg) | ![built, LOD](docs/ingest_after.jpg) | ![rejoined](docs/ingest_rejoin.jpg) |

`./gradlew runClientGameTest -Plod.show.only=none -Plod.show.ingest=true` takes these;
`-Plod.show.mp=true` does the same on a dedicated server.

## Sodium and Iris

* **Sodium** (0.9.2 tested) replaces the chunk renderer, but its chunks still draw inside vanilla's main
  pass, so the LOD draws right after them exactly as without it. Nothing to configure.
* **Iris without a shader pack** renders like vanilla, and so does the LOD.
* **Iris with a shader pack** renders the world into the pack's own buffers and lights them in the pack's
  passes, which know nothing of the LOD: drawn in there, the LOD came out unlit and dark. So while a pack
  is on (`compat/Shaders`), the LOD waits until the pack has written its final picture, then draws on top
  of it in one more pass, tested against the depth the world left, so it covers only sky. Its haze fades
  into a copy of the pack's picture rather than vanilla's fog colour, so the far land melts into the
  pack's own sky, whatever colour that is. Costs: one screen-sized copy a frame, nothing else.

  What that leaves out: the pack's lighting and shadows stay on the chunks (the LOD keeps its own sun
  and relief), rain the pack draws in front of the far land is covered, and a pack's border fog still
  fades the chunks' edge into sky just in front of the LOD; turn it off in the pack's settings if it has
  the option, as for Distant Horizons.

The game test runs with them: `-Psodium` adds Sodium, `-Piris` Sodium and Iris (from Modrinth's Maven,
for dev runs only; nothing of either ships). Put a pack in `build/run/clientGameTest/shaderpacks` and
switch it on in `config/iris.properties` to test one.

## Settings

In game: **Options → VOID LOD Settings…**, under the other buttons (it stays there with mods that replace
Video Settings, such as Sodium); the same button at the end of **Video Settings**; or a key (unbound by
default; Controls → Key Binds → VOID LOD). **F8** toggles the LOD.

![the settings screen](docs/settings.jpg)

Every change applies at once, and none needs a tile rebuilt: the reach, detail and haze from the next frame
(the selection picks other levels of the same tiles), worker threads by starting or stopping workers (one
told to stop finishes the tile in hand), the memory budget at the next sweep. Under the title the screen
shows what the LOD draws and costs right now.

The settings live in `config/void_lod.json`, written with the defaults on first start and again when the
screen closes after a change. Each value is read on its own: a missing, misspelt or out-of-range one falls
back to its default or is clamped, and the rest of the file still counts. `void.lod.*` system properties
override the file for the session without being written back (dev runs and the game tests pass them:
`./gradlew runClient -Pvoid.lod.detail=4`).

| In the file | Property | Default | |
|---|---|---|---|
| `preset` | `void.lod.preset` | `high` | `low`, `medium`, `high`, `ultra` or `custom`: sets the next five together, and in the file wins over them unless it is `custom` |
| `radius` | `void.lod.radius` | `8192` | how far the LOD reaches, in blocks (1024–32768) |
| `detail` | `void.lod.detail` | `5` | the most pixels a cell may cover before its tile splits (lower = sharper, heavier) |
| `farDetail` | `void.lod.farDetail` | same as `detail` | the same for the smooth far field (3 sharpens far silhouettes for a third more quads) |
| `threads` | `void.lod.threads` | half the cores, max 4 | worker threads |
| `budget` | `void.lod.budget` | `512` | MB of GPU memory before least-recently-used tiles are evicted |
| `haze` | `void.lod.haze` | `0`: 3/4 of the reach | how far the air carries at sea level, in blocks (the haze is about 2/3 there) |
| `enabled` | `void.lod.enabled` | `true` | |
| `autoTune` | `void.lod.autoTune` | `true` (`false` in the game tests) | see below |
| `gpuTarget`, `prepareTarget` | | `1.5`, `0.5` | auto-tune's targets, in ms a frame |
| `smoothFrom` | `void.lod.smoothFrom` | `2` | the first level drawn smooth; below it, blocky (read at start) |
| | `void.lod.ingest` | `true` | read the chunks the client loads into the far view, and keep them on disk |
| | `void.lod.overPack` | `true` | with an Iris shader pack, draw over the pack's picture (`false`: inside its buffers, to compare) |

### Quality presets

What sets the GPU cost is the detail budget: a tile's cell count goes with its square, so 10 px per cell
draws a quarter of the cells 5 px does. The reach costs little past a few kilometres (each doubling adds
one coarse level of about 96 tiles) but memory and build time grow with it. Threads are capped at half the
cores.

| Preset | Meant for | Reach | Detail, near / far | Threads | GPU memory |
|---|---|---|---|---|---|
| Low | integrated graphics, older laptops | 4 km | 10 / 10 px | 1 | 192 MB |
| Medium | laptop GPUs | 8 km | 7 / 7 px | 2 | 320 MB |
| **High** (default) | desktop GPUs | 8 km | 5 / 5 px | up to 4 | 512 MB |
| Ultra | strong desktop GPUs | 16 km | 4 / 3 px | up to 6 | 1 GB |

Each preset from the 250-block peak above (1600×900, RTX 4060), settled, auto-tune off:

| Preset | Tiles drawn | Quads | GPU time | Render thread |
|---|---|---|---|---|
| Low | 50 | 285 k | 0.25 ms | 0.24 ms |
| Medium | 89 | 478 k | 0.31 ms | 0.24 ms |
| High | 125 | 652 k | 0.37 ms | 0.32 ms |
| Ultra | 253 | 1.3 M | 0.65 ms | 0.46 ms |

The GPU time falls more slowly than the quads: part of it is shading the pixels the LOD covers, which no
detail setting changes. Integrated graphics take several times as long for each; auto-tune walks down from
whichever preset is chosen.

### Auto-tune

On by default (`core/AutoTune`). It keeps the LOD's GPU time (timestamp queries around its draws) under
1.5 ms and its render-thread time under 0.5 ms a frame by coarsening the detail budget a step at a time,
×1.2 up to ×3; only once the detail is three times coarser does it shorten the reach, to 3/4 and then 1/2.
When there is room it refines again, step by step, back to the settings. It is fed every frame and decides
twice a second, and is built not to hunt:

* a step down needs a second over the target; a step up three seconds under it and five since the last
  change, so finer tiles have streamed in and shown their cost;
* a step up is taken only if its predicted cost (cell count, the square of the detail) lands under 80% of
  the target, so the step up is never the one that goes over;
* a step up undone within 20 s bars that step for 30 s, twice as long each time, up to eight minutes: a
  view right at the edge settles a step down instead of flickering (in the unit tests, a machine where
  finer detail costs far more than the model predicts sees 15 changes in 30 minutes, over the target
  0.5% of the time);
* render-thread time is the median of frames that streamed no tiles, so uploads (capped on their own) and
  the odd hitch do not count;
* while the game holds its frame cap (vsync or a frame limit) the LOD is not what holds it back, so
  nothing is coarsened, however long a GPU idling at low clocks takes over the draws.

Part of the GPU time is shading the pixels the LOD covers, which no detail budget changes, so each step
down saves less than the one before; on a GPU where that part alone is over the target, auto-tune ends at
its coarsest step. A new world, or a new reach, detail or preset, starts it over from the settings. Its
state shows on the F3 screen and in the settings screen's status line.

### F3

Three lines on the F3 screen: tiles drawn and cached, quads, GPU memory and queued builds; GPU and
render-thread time and workers; preset, reach, detail and auto-tune. They come with vanilla's built-in debug
profiles, and are `void_lod` in the debug options (F3 + F6) to move or hide.

## Numbers

`./gradlew lodBench`: the full horizon around a camera on Earth terrain (seed 8675309), every tile the
selector settles on, sampled and meshed, on a Ryzen 7 5800XT (16 threads). The source here is the terrain
model alone; in game the biome lookup and colours come on top.

| Radius | Tiles | GPU memory | Build, 1 thread | Build, 16 threads | Before: memory, 16 threads |
|---|---|---|---|---|---|
| 4 km | 436 | 70 MB | 3.5 s | 0.44 s | 75 MB, 0.34 s |
| 8 km | 532 | 77 MB | 4.2 s | 0.48 s | 92 MB, 0.41 s |
| 16 km | 628 | 83 MB | 6.6 s | 1.46 s | 102 MB, 1.45 s |

Doubling the radius costs one more level of about 96 tiles, not four times the work. The smooth tiles'
shared index buffer more than pays for the 16-byte vertices: the horizon takes less memory than before,
for about 15% more build time spent on occlusion, materials, trees and colour.

## Build and test

```sh
cd lod
./gradlew build                     # the mod, plus the core unit tests
./gradlew lodBench                  # the horizon benchmark above
./gradlew runClientGameTest         # in-game screenshots: a peak, a valley and the air, LOD off and on
                                    # (headless on Linux: SDL_VIDEO_FORCE_EGL=1 xvfb-run ./gradlew ...)
```

The game test takes `-Plod.show.*` properties (seed, render distance, views, screenshot size; see
`LodShowcase`) and logs, per view, what the LOD drew and what each part of a frame cost. Besides the
peak, valley, air and long-lens views it photographs dusk (`dusk0`-`dusk2`), night (`night0`, `night1`),
rain and thunder (`rain0`, `rain1`, `thunder0`), the open sea at dusk and night and under water (`sea0`,
`sea1`, `water0`), a temperate and a snowy conifer forest (`forest0`, `forest1`) and the End's outer
islands (`end0`-`end2`, the last from the void below them); `-Plod.show.only=end0,end1 -Plod.show.views=40`
picks views by name.
`-Plod.show.settings=true` also goes through Video Settings to the settings screen, chooses Low on it and
photographs the view at each preset, then the F3 lines. Auto-tune is off in the game tests unless
`-Pvoid.lod.autoTune=true`.

The build includes `../expanse` as a composite build. The LOD compiles against Expanse's terrain
model, but only loads it when Expanse is installed.

## Not yet

* **Land on a server nobody has seen.** Without the generator, the far view on a server is what the
  client has seen; a small server-side companion could stream column summaries of the rest.
* **Shader-pack lighting on the LOD.** With a pack on, the LOD draws over the pack's picture in its own
  style. Lighting it the pack's way needs a program in the pack for the LOD's vertices, as packs carry
  for Distant Horizons.
* **Block textures on the blocky tiles.** Their colours are the textures' own averages and a grain stands
  in for the detail; sampling the block atlas itself would make the seam with the chunks vanish
  entirely at close range.
* **A disk cache of built tiles.** The seen land is kept, and Expanse terrain builds faster than a mesh
  would load; the generic generator path could still gain from caching the tiles themselves.
* **Faster void worlds.** The End goes through the generic generator path, which sets up a noise chunk
  for every column it reads, so an End tile takes several times as long to build as an Earth tile (150
  to 260 ms against 40 to 70 in the showcase runs; the horizon fills in about 40 s). Reading a tile's
  columns through one noise chunk would fix it.
* **Stepped skylines.** Far ridges meet the sky in smooth curves, where Distant Horizons' real block
  columns step. Tried in the shader: just in front of where a convex ridge turns away from the eye, cut
  the uphill half of each block column above its top, so the skyline steps column by column (cutting
  anywhere else would show the inside of the hill). It cost nothing measurable, even with `discard` in
  every smooth tile, but changed only 70 to 200 scattered pixels of a 1600×900 frame: the shader's
  columns are about three pixels across, so their steps are one or two pixels tall and read as aliasing,
  not as steps. Steps that show need geometry at the cell scale (pairs of grid vertices collapsed into
  flat-topped columns with walls between, which the shared index buffer allows), at the price of the
  smooth far field's detail, so it was set aside.
