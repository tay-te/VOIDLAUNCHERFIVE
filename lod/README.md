# `lod/` — VOID LOD

Far terrain for **Minecraft 26.3** (Fabric): the land past your render distance, out to the horizon,
sampled straight from the world generator, shaded to look like the blocks it stands for, and drawn in a
handful of indirect draws on OpenGL or Vulkan.

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

### The End, conifers and rain

The End's outer islands, vanilla and with the LOD, and from 140 blocks above them: islands to the
horizon, slabs over the void, sinking into the dark with distance.

| Vanilla | VOID LOD |
|---|---|
| ![End islands, vanilla](docs/end0_vanilla.jpg) | ![End islands, VOID LOD](docs/end0_after.jpg) |

![End islands from above](docs/end1_after.jpg)

A conifer forest on a cold coast, from 140 blocks above it: cones close in, a dark, spiky canopy further
out, the crowns' tops white with snow and their flanks not.

![conifer forest under snow](docs/forest0_after.jpg)

Rain from the peak, before and now: the far land goes in a downpour as it does in vanilla.

| Before | Now |
|---|---|
| ![rain, before](docs/rain0_before.jpg) | ![rain, now](docs/rain0_after.jpg) |

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

## Settings (system properties, for now)

| Property | Default | |
|---|---|---|
| `void.lod.radius` | `8192` | how far the LOD reaches, in blocks |
| `void.lod.detail` | `5` | the most pixels a cell may cover before its tile splits (lower = sharper, heavier) |
| `void.lod.farDetail` | same as `detail` | the same for the smooth far field (3 sharpens far silhouettes for a third more quads) |
| `void.lod.haze` | 3/4 of the radius | how far the air carries at sea level (the haze is about 2/3 there) |
| `void.lod.smoothFrom` | `2` | the first level drawn smooth; below it, blocky |
| `void.lod.threads` | half the cores, max 4 | worker threads |
| `void.lod.budget` | `512` | MB of GPU memory before least-recently-used tiles are evicted |
| `void.lod.enabled` | `true` | |

**F8** toggles the LOD in game.

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

The build includes `../expanse` as a composite build. The LOD compiles against Expanse's terrain
model, but only loads it when Expanse is installed.

## Not yet

* **Multiplayer.** Sampling needs the server's generator and seed, so today the LOD runs in
  singleplayer and on a LAN host. Next steps: ingest the chunks the client receives, and add a small
  server-side companion that streams tile summaries.
* **Edits.** Builds and dug-out terrain beyond the render distance show the generated land. Ingesting
  loaded chunks over the generated tiles fixes this, using the same path as multiplayer.
* **Shader packs.** These need Iris to know about the LOD's depth and pipeline.
* **Block textures on the blocky tiles.** Their colours are the textures' own averages and a grain stands
  in for the detail; sampling the block atlas itself would make the seam with the chunks vanish
  entirely at close range.
* **Disk cache.** Expanse terrain builds faster than it would load, but the generic generator path
  would benefit.
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
