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
| `void.lod.ingest` | `true` | read the chunks the client loads into the far view, and keep them on disk |
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

* **Land on a server nobody has seen.** Without the generator, the far view on a server is what the
  client has seen; a small server-side companion could stream column summaries of the rest.
* **Unseen columns inside a seen tile.** On a server, a tile partly seen marks its unseen columns empty;
  the meshers have to leave those out (shared with the End's empty columns).
* **Shader packs.** These need Iris to know about the LOD's depth and pipeline.
* **Block textures on the blocky tiles.** Their colours are the textures' own averages and a grain stands
  in for the detail; sampling the block atlas itself would make the seam with the chunks vanish
  entirely at close range.
* **A disk cache of built tiles.** The seen land is kept, and Expanse terrain builds faster than a mesh
  would load; the generic generator path could still gain from caching the tiles themselves.
