# `lod/` — VOID LOD

Far terrain for **Minecraft 26.3** (Fabric): the land past your render distance, out to the horizon,
sampled straight from the world generator and drawn in one indirect draw on OpenGL or Vulkan.

```
Fabric Loader ≥ 0.19.5 · Fabric API 0.161.0+26.3 · Java 25 · optional: VOID Expanse
```

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
| Draw calls for the LOD | many | GPU-driven | one indirect draw per 64 MB heap |
| Graphics API | OpenGL (Vulkan via add-on / 26.2+) | OpenGL 4.6 | Mojang's own GPU layer: OpenGL **and** Vulkan |
| Depth | separate framebuffer, composited | separate | vanilla's own reversed-Z depth buffer, no composite |

## How it works

```
 TerrainSource ──▶ TileBuilder ──▶ LodEngine ──▶ LodRenderer ──▶ vanilla main pass
 (Expanse model,   (sample 66²     (quadtree      (TLSF arena,       (after opaque chunks,
  or any noise      columns, mesh   selection,     per-tile instance   same depth buffer)
  generator)        blocky tiles)   workers,       buffer, one
                                    uploads)       indirect draw)
```

* **Screen-space-error quadtree** (`core/LodSelector`). A tile is 64×64 cells. It splits while its
  cells would cover more than `void.lod.detail` pixels on screen. Detail follows what the eye can
  resolve, not fixed rings, so the work per frame stays about the same however far the view reaches.
  While tiles load, the walk falls back to the nearest ready ancestor, so you never see a hole. The
  top two levels are fetched first, so the whole horizon appears within a few tile builds.
* **Prioritised, cancellable workers** (`core/LodEngine`). Workers run at minimum thread priority.
  Every quarter-second the queue is re-sorted by the latest screen error. Any job nobody has asked for
  in 90 frames is dropped unbuilt.
* **Blocky meshing, merged** (`core/TileBuilder`):
  * Each cell becomes a flat top. Tops merge greedily wherever height and colour agree.
  * Walls go wherever a cell stands above its neighbour, merged along runs.
  * Tile borders hang skirts, so a coarser neighbour never opens a crack.
  * Heights snap to a quarter of a cell, so far tops merge into large quads.
  * Forests are a raised, darker blanket, as tall and as dense as the biome's own tree features make
    them.
  * Each vertex is 12 bytes: 16-bit tile-relative position, face, RGBA.
* **One draw** (`render/LodRenderer`):
  * Meshes live in Mojang's TLSF `UberGpuBuffer` heaps, the same allocator the chunk renderer uses.
  * Each frame, the visible tiles (frustum-culled per tile against their real height range) become one
    instance buffer and one `drawIndexedIndirect` command buffer.
  * Tiles that reach into vanilla's area use a dithered-clip shader variant. Everything else uses a
    variant with no `discard`, so the GPU keeps early-Z.
* **Shared depth** (`mixin/CameraMixin`). 26.3 renders reversed-Z into a 32-bit float buffer, so the far
  plane moves out to the LOD's reach for free. The LOD draws right after the opaque chunks, so every
  LOD fragment hidden by nearby terrain is rejected before it is shaded.
* **Fog at the LOD's edge** (`mixin/FogRendererMixin`). Render-distance fog moves out to the LOD's
  radius. Clear-air haze stretches to match. Water, lava and blindness fog are left alone.
* **Colours from the biome itself** (`source/BiomeLooks`):
  * Ground comes from tags, temperature and rainfall.
  * Trees come from the biome's vegetation features. Placement counts give density, trunk placers give
    height, leaf blocks give colour.
  * Modded biomes look right without a table.
  * On Earth worlds, the model adds rivers at their own water level, crater lakes and lava, banded
    canyon walls, white salt flats, and the model's own snow line.

## Settings (system properties, for now)

| Property | Default | |
|---|---|---|
| `void.lod.radius` | `8192` | how far the LOD reaches, in blocks |
| `void.lod.detail` | `5` | the most pixels a cell may cover before its tile splits (lower = sharper, heavier) |
| `void.lod.threads` | half the cores, max 4 | worker threads |
| `void.lod.budget` | `512` | MB of GPU memory before least-recently-used tiles are evicted |
| `void.lod.enabled` | `true` | |

**F8** toggles the LOD in game.

## Numbers

`./gradlew lodBench`: the full horizon around a camera on Earth terrain (seed 8675309), every tile the
selector settles on, sampled and meshed. Run on the 4-core build container, with the forest blanket on:

| Radius | Tiles | Quads | GPU memory | Build, 4 threads |
|---|---|---|---|---|
| 4 km | 436 | 1.65 M | 75 MB | 1.3 s |
| 8 km | 532 | 2.01 M | 92 MB | 1.8 s |
| 16 km | 628 | 2.23 M | 102 MB | 3.6 s |

Doubling the radius costs one more level of about 96 tiles, not four times the work.

## Build and test

```sh
cd lod
./gradlew build                     # the mod, plus the core unit tests
./gradlew lodBench                  # the horizon benchmark above
SDL_VIDEO_FORCE_EGL=1 xvfb-run ./gradlew runClientGameTest   # in-game screenshots, LOD off and on
```

The build includes `../expanse` as a composite build. The LOD compiles against Expanse's terrain
model, but only loads it when Expanse is installed.

## Not yet

* **Multiplayer.** Sampling needs the server's generator and seed, so today the LOD runs in
  singleplayer and on a LAN host. Next steps: ingest the chunks the client receives, and add a small
  server-side companion that streams tile summaries.
* **Edits.** Builds and dug-out terrain beyond the render distance show the generated land. Ingesting
  loaded chunks over the generated tiles fixes this, using the same path as multiplayer.
* **Shader packs.** These need Iris to know about the LOD's depth and pipeline.
* **Disk cache.** Expanse terrain builds faster than it would load, but the generic generator path
  would benefit.
