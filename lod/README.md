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
a fraction of the cost.

* **Real blocks at every distance** (`core/Blocky`). Every tile is made of block columns: a flat top per
  cell and walls between cells, one block a cell next to the chunks, then 2, 4, 8 ... blocks a cell further
  out, so a cell stays a few pixels across wherever it is. Far land has the silhouette, the steps and the
  face shading of Minecraft terrain, not of a smooth surface with blocks painted on: the earlier smooth far
  field (still there, `void.lod.smoothFrom`) read as a 3D model from a few kilometres out, its table
  mountains as streaked curtains and its forests as carpet. The same telephoto view, smooth far field
  above, block columns below:

  ![the far field, smooth and in blocks](docs/far_blocks.jpg)

  What keeps that affordable: from level 2 on, a vertex is 8 bytes instead of 16 (position, face, colour in
  5 bits a channel, occlusion and light, `TileBuilder.COMPACT_BYTES`), since out there a texture and the
  lowest block of a wall are under a pixel; heights are rounded to a quarter of a cell, a step under a
  pixel, so level neighbours merge; and wall bands thinner than a quarter of a cell (a grass rim, a few
  blocks of soil, thin strata) are left out. At 16 km on Ultra that is about 400 to 600 MB of GPU memory and
  1 to 2 ms of GPU time on an RTX 4060; High is half that. Built the way the chunks are:
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
  * The block textures themselves, from the atlas the chunks draw with, so the seam with the chunks
    holds up close. Every face carries what it is made of, an id in the world's material registry (the
    grass top, the grass side, dirt, stone, sand, snow, each band of terracotta, the trees' leaves,
    Expanse's salt and basalt), and a small table on the GPU (`render/MaterialTable`) says where that
    face's sprites are in the atlas. The texture is divided by its own average, so the vertex colour
    stays the truth (the biome's tint, snow, shading, occlusion) and only the pattern is added; a grass
    block's side is its two layers, the dirt and the tinted fringe cut out over it; tops the block model
    turns at random (grass, sand, dirt) turn a quarter per block. Sampled the way vanilla samples
    terrain (crisp texels up close, trilinear mipmaps further off, through vanilla's own sampler), it
    gives way to the old one-block grain as a block shrinks from three pixels to one and a half, where
    the mipmaps have melted it to about that anyway: nothing shimmers, and the tiles further out look
    exactly as they did. A textured pixel reads the table once and the atlas once (twice on a grass
    side); no varying is added, the material rides in a spare component. Faces with no texture
    (water) keep the grain. Just past the chunks through a 30° lens, before and after (the chunks
    begin at the bottom edge):

    ![blocky tiles before and after block textures](docs/seam_textures.jpg)
* **Light from the land itself** (`core/Horizons`, `core/Heights`). Every vertex carries the land's horizon
  along the four axes, from its tile's own samples nearby and the height grids of the coarser tiles further
  out, to 8 km: a sweep per row with an upper convex hull, a few milliseconds a tile. Minecraft's sun crosses
  the sky from east to west straight over the zenith, so the east and west horizons are exactly where the
  sun goes behind the land: the shader casts every mountain's shadow, at any time of day, with nothing
  rebuilt as the sun moves. All four together say how much of the sky a column sees, so valleys and the
  floors of canyons darken and ridges stay open. The chunks have neither, so both come in past them. Late
  in the day, looking toward the sun:

  ![evening light on the far land](docs/evening_light.jpg)
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
  valley or a peak. Dusk and night darken the LOD through vanilla's own lightmap; under water, in lava or
  blind, the LOD is not drawn at all.
* **Haze in the colour of the sky in each direction** (`lod.vsh`, `mixin/FogRendererMixin`). Vanilla gives
  the whole frame one fog colour, pulled toward the sunset's colour as far as the camera faces the sun. The
  LOD works that colour out for each vertex's direction from vanilla's own inputs, scaled so the direction
  the camera faces is exactly vanilla's colour (night, weather and dimension included), and adds the glow
  vanilla paints over the horizon toward the sun. At dusk the land toward the sun sinks into the glow and
  the land away from it into the blue, where the sky behind it is those colours.
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
  generator)        in blocks,      workers,       buffer, indirect
                    light it)       uploads)       draws)
```

* **Screen-space-error quadtree** (`core/LodSelector`). A tile is 64×64 cells. It splits while its
  cells would cover more than `void.lod.detail` pixels on screen. Detail follows what the eye can resolve, not fixed
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
    Next to the chunks a vertex is 16 bytes: tile-relative 16-bit position and flags, with the light in
    their spare high bytes, then two RGBA8 colours (its colour and its material id). Further out it is 8
    bytes, in heaps of its own.
  * Each frame, the visible tiles (frustum-culled per tile against their real height range) become one
    instance buffer and one `drawIndexedIndirect` per heap and kind of tile.
  * Only tiles in the seam with the chunks, or fading, use the shader variant with `discard`; the rest
    keep early-Z.
  * GPU time is measured with timestamp queries and logged with the other statistics.
* **Shared depth** (`mixin/CameraMixin`). 26.3 renders reversed-Z into a 32-bit float buffer, so the far
  plane moves out to the LOD's reach for free. The LOD draws right after the opaque chunks, so every
  LOD fragment hidden by nearby terrain is rejected before it is shaded.

## Building the far land fast

The far land is never generated as chunks. Distant Horizons runs the world generator for every far chunk (all of
its noise, caves, structures and light, to keep the top layer); the LOD asks the terrain model for the surface
alone, a column at a time (`source/ExpanseSource`), so a 64 x 64-column tile costs what one chunk costs it, and a
far tile covers thousands of blocks a cell. What is left is made cheap three ways:

* **Every plate built once.** Expanse's Earth is drawn on tectonic plates about 4 km across, each worked out
  (rivers, erosion, uplift) the first time something samples it. The model kept 40; a 32 km horizon samples about
  313, so each was built 38 times over as the workers pushed each other's out. The LOD now asks the model to keep
  as many as its reach covers, half as many again for slack, up to a sixth of the heap, and lets them go once the
  horizon is built (`TerrainModel.reservePlates`). On 16 threads a 32 km horizon builds in 0.64 s instead of 3.9,
  a 64 km one in 5.7 s instead of 28.7 (`./gradlew lodBench -Plod.bench.radii=32768,65536`).
* **The generator's answers kept** (`core/GeneratorCache`). Every sample a tile asks of the generator goes into a
  page per tile, saved in the world's folder (`void_lod/<dimension>/generated`), and read back the next time
  that tile is built: joining a world again, its horizon comes off the disk and the generator is not asked at all.
  Pages are stamped with what the generator is (the seed, the game's, VOID LOD's and Expanse's versions, the
  resource packs that colour the land) and dropped when that changes; the folder is trimmed to 256 MB, least
  recently used first. It costs nothing on a first visit (two runs each way, 32.3 and 29.7 s without it, 32.1 and
  29.9 s with it), and on a rejoin the 32 km horizon was whole in 1.7 s instead of 6.9 s, every sample from the
  disk (`./gradlew cacheBench`; `-Dvoid.lod.generatorCache=false` turns it off).
* **Built ahead.** Moving faster than 8 blocks a second, the engine also selects the horizon three seconds ahead,
  every eighth frame, and queues its missing tiles after everything the view needs now, so a fast flight finds
  its land built when it gets there.

* **Fewer faces where nobody can count them.** Trees are about half of a horizon's quads (7.6 million against
  3.9 without them at 8 km, `./gradlew faceBench`), nearly all of it the flanks of crowns one cell wide. Where
  cells are 4 blocks or more, a crown's leafy flank and the shade under it, a pixel or two each, are one face whose
  colour runs from the leaves down into the shade: 14% fewer quads, no difference to see, through a shader pack
  either.
* **More workers when the machine is idle.** With a long queue and the CPU under 65% busy, up to twice the preset's
  workers build (never more than all but two cores), back to the preset's once the queue is short; the game's own
  threads keep what they need either way.

The log says how long each world's horizon took: `VOID LOD: horizon built in 1.7 s, 455 tiles, radius 32768`.

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
  256 ints, 256 bytes of light and a short palette, about 1.5 KB in memory: ten thousand chunks explored,
  15 MB.
* **Lit at night.** Each column also keeps the block light over its surface (on its ground, or over the
  highest thing standing there: a roof under a lantern, a torch on a wall). Light is summed like everything
  else, so a cell glows by how much of it is lit, and it rides in the vertex (3 bits up close, 2 in the
  compact far vertices, Distant Horizons' block-light nibble through a pack). The shader lights it through
  vanilla's lightmap exactly as it lights a block by a torch, so a town the player has been to stays lit
  on the horizon after dark, and lava always glows.
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

At midnight, glowstone streets laid on a deck in loaded chunks and seen from 480 blocks away, past the
chunks (`-Plod.show.town=true`), in the LOD's own renderer and through Complementary Reimagined:

| LOD | Through a shader pack |
|---|---|
| ![town at night](docs/town_night.jpg) | ![town at night, Complementary](docs/town_night_pack.jpg) |

## Sodium and Iris

* **Sodium** (0.9.2 tested) replaces the chunk renderer, but its chunks still draw inside vanilla's main
  pass, so the LOD draws right after them exactly as without it. Nothing to configure.
* **Iris without a shader pack** renders like vanilla, and so does the LOD.
* **Iris with a shader pack** renders the world into the pack's own buffers and lights it in the pack's
  own passes. Most packs carry programs for Distant Horizons' far terrain (`dh_terrain`, `dh_water`), and
  the LOD draws through those (`compat/iris`), so the pack lights the far land as it lights everything else:
  its sun, sky light, fog, water, reflections, screen-space shadows and colour grading, with no seam where
  the chunks end. Distant Horizons does not need to be installed. Mixins on Iris's Distant Horizons facade
  (`DHCompat`) answer for it: the pack is compiled with `DISTANT_HORIZONS`, and its `dhRenderDistance`,
  `dhProjection` and `dhDepthTex` are the LOD's. Tiles are then built in Distant Horizons' vertex layout
  (position, colour, a material such as leaves, grass, stone or water, a face normal and the sky light
  from the land's own occlusion), drawn into the pack's buffers before the chunks, which cover them
  wherever they stand. The water goes after the world's translucent pass, through `dh_water`, over a bed
  drawn under it, so the sea shows its floor in the shallows as the chunks do; past 12 blocks down the bed
  steps down in 8-block levels instead of following every bump, still dark as deep water should be, in a
  fraction of the faces (the sea view: 269k quads instead of 601k). The pack's draws are timed on the GPU
  for F3 and auto-tune, which counts half of it: a pack's programs cost about twice the LOD's own. Depth is reversed, as
  everywhere in 26.3: Iris patches the pack's programs to write it reversed and to read every depth texture
  back the usual way round, so the LOD's depth clears to 0 and tests greater-or-equal.

  A pack without Distant Horizons programs gets the fallback: the LOD waits until the pack has written its
  final picture and draws on top of it, tested against the world's depth so it covers only sky, its haze
  fading into a copy of the pack's picture so the far land melts into the pack's sky. The pack's lighting
  stays on the chunks there, and its border fog fades the chunks' edge just in front of the LOD. With
  Distant Horizons installed too, Iris keeps its own integration and the LOD takes the fallback.

  Packs with a `dh_shadow` program (of those tested, only Bliss) can take the LOD into their shadow map too
  (`-Dvoid.lod.packShadows=true`, through Iris's shadow render callback). It is off by default: a pack's
  shadow map reaches a few hundred blocks at most, about where the chunks end, so in the showcase views the
  far land changed no visible shadow, while drawing it into the map is GPU work every frame for nothing seen. The
  packs that shade distant land do it in screen space instead (Complementary).

  Tested with Complementary Reimagined r5.9.3, BSL v10.1.8, Photon v1.3b and Bliss v2.1.2 at the High
  preset (8 km), on the showcase views. None of them reads the block texture atlas Iris offers Distant
  Horizons terrain (`dhBlockAtlas`), so the LOD gives them none; they shade the far land from its colours.
  Complementary at dusk:

  ![dusk over the badlands, the far land lit by Complementary Reimagined](docs/shader_pack_dusk.jpg)

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
| `smoothFrom` | `void.lod.smoothFrom` | `10` (none) | the first level drawn as the smooth far field instead of blocks (read at start) |
| | `void.lod.ingest` | `true` | read the chunks the client loads into the far view, and keep them on disk |
| | `void.lod.generatorCache` | `true` | keep the generator's answers in the world's folder (`false`: sample it every time) |
| | `void.lod.packShadows` | `false` | put the LOD into the shadow map of packs with a `dh_shadow` program (Bliss) |
| | `void.lod.overPack` | `true` | with a shader pack that has no Distant Horizons programs, draw over its picture (`false`: inside its buffers, unlit, to compare) |

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
* **Far shadows through packs that keep a short shadow map.** BSL and Photon do not shade distant land at
  all, and a pack's shadow map ends near the chunks; long shadows over the far land would need the LOD's
  own horizon lighting fed to the pack, which no pack reads today.
* **Block textures from the real blocks.** The textures follow what the generator puts on the surface;
  loaded chunks could put their own blocks' ids on the faces through the same registry. A grass
  block's fringe takes the default grass tint against the biome's (the face's colour is the biome's).
* **Faster first visits in a brand-new world.** The first horizon of a fresh world builds while the game is
  still generating its spawn area on every core, and the terrain model costs about three times what it does in
  the benchmark there (71 against 27 ms a tile); the workers run at minimum priority so the game comes first.
* **Faster void worlds.** The End goes through the generic generator path, which sets up a noise chunk
  for every column it reads, so an End tile takes several times as long to build as an Earth tile (150
  to 260 ms against 40 to 70 in the showcase runs; the horizon fills in about 40 s). Reading a tile's
  columns through one noise chunk would fix it.
