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

What changed, in short: far terrain is a smooth surface shaded the way a stair of blocks averages out,
not 64-block stair-steps; the air hazes with distance and thins with height; walls are made of what the
ground is made of; forests are trees close in and a canopy far out; snow lies where the chunks put it;
colours come from the loaded textures; tiles cross-fade instead of popping.

Measured in that run (RTX 4060, Ryzen 7 5800XT shared with other work), with the view settled:

* The LOD's work on the render thread is **0.23–0.58 ms per frame** (selection, fades, culling, draw
  buffers); **0.9–1.3 ms** while tiles are still streaming in, uploads capped at 4 MB a frame.
* Its draws take **0.18–0.25 ms of GPU time** for **790–870 k quads** (timestamp queries), and the game
  runs at 1000–1600 fps with it on.
* **400–600 tiles** cached in **80–130 MB** of GPU memory.
* A tile builds in **35–45 ms** on a worker; 95 % of that is the terrain source (the Earth model and the
  biome lookup), 2 ms is meshing. The first two levels, which cover the whole horizon, are built first.

The first version, on the same machine and views, never settled at these frame rates: it counted its
cache timers in frames, so at 1500 fps it evicted and rebuilt its coarsest tiles in a loop (2,088 builds
for 378 tiles, 150–185 jobs always queued) and spent **1.9–3.3 ms** of render thread per frame. Timers
are in seconds now, and a split tile stays resident as its children's fallback.

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
    height and shade, cones for tall narrow trees, domes for broad ones, darker under the canopy.
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
  * Forests are a canopy raised as far as they are dense; valleys are slightly darker and ridges
    slightly lighter; colours are softened across biome borders and mottled cell by cell; rivers
    narrower than a cell are a thread of their water's colour.
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
  shallows show their sand; past the chunks, water at a low angle mirrors the sky.
* **Aerial haze** (`lod.vsh`, `mixin/FogRendererMixin`): exponential haze that thins with height, dense in
  the valleys and thin over the peaks, so far ranges stand clear above the haze in front of them.
  Vanilla's chunks get the same haze in linear form, matched at the camera's height, so the seam
  disappears; rain and misty biomes (Expanse's cloud forest, fjords) thicken it; the land melts into the
  sky at the LOD's edge.
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
| `void.lod.farDetail` | `3` | the same for the smooth far field |
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
`LodShowcase`) and logs, per view, what the LOD drew and what each part of a frame cost.

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
